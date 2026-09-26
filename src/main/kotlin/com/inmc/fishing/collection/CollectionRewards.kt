package com.inmc.fishing.collection

import com.inmc.fishing.Fishing
import com.inmc.fishing.player.Angler
import kr.inmc.core.store.YamlFileStore
import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player

/**
 * 도감 보상 다섯 갈래. `collections.yml` 에서 온다.
 *
 * | 갈래 | 언제 |
 * |---|---|
 * | 최초 등록 | 그 물고기를 처음 도감에 넣었을 때 |
 * | 마일스톤 | 그 물고기를 N칸 채웠을 때 |
 * | 퍼펙트 | 그 물고기를 끝까지 채웠을 때 |
 * | 등급 올등록 | 그 등급 전부를 한 칸 이상 |
 * | 등급 퍼펙트 | 그 등급 전부를 끝까지 |
 *
 * **한 번 받은 것은 다시 안 준다.** 받았다는 표시는 [CollectionEntry.claimed] 에 남는다 —
 * 칸을 비웠다가 다시 채워도 재지급되지 않는다. 안 그러면 넣었다 뺐다를 반복하는 것이
 * 무한 보상이 된다.
 *
 * 등급 단위 보상은 **그 등급 물고기 중 아무거나 하나**의 기록에 표시를 남긴다. 등급마다 따로
 * 저장소를 두지 않으려는 것이고, 표시 키에 등급 이름이 들어가므로 섞이지 않는다.
 */
class CollectionRewards(private val fishing: Fishing) : YamlFileStore(
    io = fishing.io,
    path = listOf("collections.yml"),
    header = "",
    what = "도감 보상",
) {

    private var enabled = true
    private var firstRegister = emptyMap<String, List<String>>()
    private var milestones = emptyMap<String, Map<Int, List<String>>>()
    private var perfect = emptyMap<String, List<String>>()
    private var gradeAllRegistered = emptyMap<String, List<String>>()
    private var gradeAllPerfect = emptyMap<String, List<String>>()

    /**
     * 한 칸 등록한 뒤에 부른다. 받을 것이 있으면 준다.
     *
     * 순서는 좁은 것에서 넓은 것으로 — 최초 → 마일스톤 → 퍼펙트 → 등급. 퍼펙트를 채우는
     * 그 한 칸이 등급 퍼펙트까지 완성시키는 경우가 있고, 그때 둘 다 받아야 한다.
     */
    fun onRegistered(player: Player, angler: Angler, entry: CollectionEntry) {
        // 도감 등록은 **낚는 것과 다른 사건이다.** 등록은 도감 화면에서 손으로 넣는 행위고,
        // 낚기만 해서는 일어나지 않는다. 그래서 신호도 따로 쏜다.
        kr.inmc.core.event.InmcSignalEvent.fire(
            source = "fishing",
            type = "register",
            playerId = player.uniqueId,
            subject = entry.fishId,
            player = player,
        ) {
            mapOf(
                "fish" to entry.fishId,
                "grade" to entry.gradeId,
                "perfect" to entry.isPerfect.toString(),
                "slots" to entry.registeredSlots.toString(),
            )
        }

        if (!enabled) return

        if (entry.registeredSlots == 1) grant(player, entry, "first", firstRegister[entry.fishId])

        milestones[entry.fishId]?.get(entry.registeredSlots)
            ?.let { grant(player, entry, "milestone-" + entry.registeredSlots, it) }

        if (entry.isPerfect) grant(player, entry, "perfect", perfect[entry.fishId])

        val gradeId = entry.gradeId
        val idsInGrade = fishing.fish.ofGrade(gradeId).map { it.id }
        if (idsInGrade.isEmpty()) return

        if (angler.collection.isGradeAllRegistered(gradeId, idsInGrade)) {
            grant(player, entry, "grade-all-" + gradeId, gradeAllRegistered[gradeId])
        }
        if (angler.collection.isGradePerfect(gradeId, idsInGrade)) {
            grant(player, entry, "grade-perfect-" + gradeId, gradeAllPerfect[gradeId])
        }
    }

    private fun grant(player: Player, entry: CollectionEntry, key: String, commands: List<String>?) {
        if (commands.isNullOrEmpty()) return
        // 표시를 먼저 남긴다. 명령어가 중간에 터져도 두 번 주지는 않는다.
        if (!entry.claim(key)) return

        val console = Bukkit.getConsoleSender()
        for (raw in commands) {
            val line = raw.replace("{player}", player.name).replace("{플레이어}", player.name)
            runCatching { Bukkit.dispatchCommand(console, line) }
                .onFailure { fishing.logger.warning("도감 보상 명령어 실패: " + line + " - " + it.message) }
        }
    }

    // --- 영속화 ---------------------------------------------------------------------

    override fun read(config: YamlConfiguration) {
        enabled = config.getBoolean("settings.enabled", true)

        val rewards = config.getConfigurationSection("rewards")
        firstRegister = readLists(rewards?.getConfigurationSection("first-register"))
        perfect = readLists(rewards?.getConfigurationSection("slot-completion"))
        gradeAllRegistered = readLists(rewards?.getConfigurationSection("grade-all-registered"))
        gradeAllPerfect = readLists(rewards?.getConfigurationSection("grade-all-perfect"))

        val byFish = HashMap<String, Map<Int, List<String>>>()
        rewards?.getConfigurationSection("milestones")?.let { node ->
            for (fishId in node.getKeys(false)) {
                val counts = node.getConfigurationSection(fishId) ?: continue
                val steps = HashMap<Int, List<String>>()
                for (countKey in counts.getKeys(false)) {
                    val count = countKey.toIntOrNull() ?: continue
                    steps[count] = counts.getStringList(countKey)
                }
                if (steps.isNotEmpty()) byFish[fishId.lowercase()] = steps
            }
        }
        milestones = byFish
    }

    /** 정의는 파일에서만 온다. 보상이 콘솔 명령어라 화면으로 안전하게 편집할 물건이 아니다. */
    override fun write(config: YamlConfiguration) = Unit

    private fun readLists(section: org.bukkit.configuration.ConfigurationSection?): Map<String, List<String>> {
        if (section == null) return emptyMap()
        val result = HashMap<String, List<String>>()
        for (key in section.getKeys(false)) {
            val list = section.getStringList(key)
            if (list.isNotEmpty()) result[key.lowercase()] = list
        }
        return result
    }
}
