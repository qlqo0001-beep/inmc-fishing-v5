package com.inmc.fishing.player

import com.inmc.fishing.collection.Collection
import com.inmc.fishing.collection.CollectionEntry
import com.inmc.fishing.config.FishingConfig
import com.inmc.fishing.fatigue.PlayerFatigue
import com.inmc.fishing.fillet.FilletBench
import com.inmc.fishing.fillet.FilletSlotYaml
import com.inmc.fishing.fish.FishRegistry
import com.inmc.fishing.fish.FishStampYaml
import com.inmc.fishing.net.FishNet
import org.bukkit.configuration.file.YamlConfiguration
import java.util.UUID

/**
 * 한 사람의 낚시 기록 전부. `players/<uuid>.yml` 한 파일이다.
 *
 * **저장되는 것과 안 되는 것이 나뉜다.** 도감·어망·피로도·손질대는 남고, 진행 중인
 * 미니게임과 힘겨루기는 남지 않는다 — 접속이 끊긴 순간 그 판은 끝난 것이다. 남겨두면
 * 재접속했을 때 언제 시작했는지 모르는 판이 되살아난다.
 *
 * 설정이 바뀌면 [rebind] 로 갈아끼운다. 어망 용량·손질 슬롯 수·피로도 기준이 전부 설정에서
 * 오는데, 그 객체들은 만들 때 설정을 받아 들고 있기 때문이다.
 */
class Angler(val id: UUID, var name: String, config: FishingConfig) {

    val collection = Collection()

    var net: FishNet = FishNet(config.netMaxSize)
        private set

    var fatigue: PlayerFatigue = PlayerFatigue(config.fatigue)
        private set

    var bench: FilletBench = FilletBench(config.fillet)
        private set

    /**
     * 이 사람만의 손질대 칸 수(2세대 `fillet_slots.max_slots`). null 이면 설정의 기본값을 따른다.
     * 관리자가 `/낚시 손질 칸 <플레이어> <설정|추가> <수>` 로 바꾼다. 저장된다.
     */
    var benchSlots: Int? = null
        private set

    /** 칸 수를 정한다. 돌고 있는 칸은 지우지 않는다. @return 바뀐 뒤의 칸 수. */
    fun setBenchSlots(count: Int): Int {
        benchSlots = count.coerceIn(1, FilletBench.MAX_SLOTS)
        return bench.resize(count)
    }

    /** 미니게임을 켜고 낚는지. 끄면 자동으로 낚이는 대신 피로도를 쓴다. */
    var minigameEnabled: Boolean = true

    /**
     * 힘겨루기 **연습 모드**(2세대 `trophy_practice_mode`). 켜면 미니게임에 성공한 모든 물고기가
     * 힘겨루기로 넘어가고, **이겨도 보상이 없다** — 트로피가 걸렸어도 그렇다. 저장된다.
     */
    var practice: Boolean = false

    /**
     * 관리자 테스트 모드(2세대 `testfightmode`). 입질이 오면 미니게임을 건너뛰고 곧바로
     * 힘겨루기로 간다. 보상은 정상이다. **저장하지 않는다** — 켜 둔 채 잊으면 곤란하다.
     */
    var testFight: Boolean = false

    // --- 저장되지 않는 진행 상태 -------------------------------------------------------

    /** 입질이 와서 미니게임을 하는 중. */
    var bite: BiteSession? = null

    /** 힘겨루기 시작 연출(!!! → 3·2·1 → START!!) 중. */
    var intro: IntroSession? = null

    /** 힘겨루기 중. */
    var fight: FightSession? = null

    /**
     * 물에 떠서 입질을 기다리는 찌(2세대 `hookInWater`). 찌가 물에 닿는 순간(BOBBING) 들고, 감아올리거나 판이 끝나 찌가 사라지면 놓는다.
     * 이게 있는 동안 1초마다 "물고기를 기다리는 중" 액션바를 다시 보낸다 — 판이 도는 동안은 쉰다.
     */
    var waitingHook: org.bukkit.entity.FishHook? = null

    val isBusy: Boolean get() = bite != null || intro != null || fight != null

    /** 판을 전부 접는다. 접속 종료·리로드·월드 이동에서 부른다. */
    fun clearSessions() {
        bite = null
        intro = null
        fight = null
    }

    /**
     * 새 설정으로 갈아끼운다. **값은 그대로 옮긴다.**
     *
     * 어망이 줄어들면 넘치는 만큼은 버리지 않고 그대로 둔다 — 용량을 줄인 것은 관리자의
     * 결정이지만, 그 때문에 남의 물고기를 지우는 것은 다른 문제다. 가득 찬 상태로 두면
     * 더 넣지 못할 뿐이다.
     */
    fun rebind(config: FishingConfig) {
        val kept = net.all()
        net = FishNet(config.netMaxSize)
        for (stamp in kept) if (!net.add(stamp)) break

        val value = fatigue.value
        val locked = fatigue.locked
        val recoveredAt = fatigue.lastRecoveredAt
        fatigue = PlayerFatigue(config.fatigue, value)
        fatigue.restoreState(value, locked, recoveredAt)

        val slots = (0 until bench.slotCount).map { bench.slotAt(it) }
        bench = FilletBench(config.fillet, benchSlots ?: config.fillet.defaultSlots)
        for ((index, slot) in slots.withIndex()) if (slot != null) bench.restore(index, slot)
    }

    // --- 영속화 (둘 다 메인 스레드) -----------------------------------------------------

    fun save(config: YamlConfiguration) {
        config.set("name", name)
        config.set("minigame", minigameEnabled)
        config.set("practice", practice)

        config.set("fatigue.value", fatigue.value)
        config.set("fatigue.locked", fatigue.locked)
        fatigue.lastRecoveredAt?.let { config.set("fatigue.recovered-at", it) }

        val entries = config.createSection("collection")
        for (entry in collection.all()) {
            val section = entries.createSection(entry.fishId)
            section.set("grade", entry.gradeId)
            entry.save(section)
        }

        val netSection = config.createSection("net")
        for ((index, stamp) in net.all().withIndex()) {
            FishStampYaml.save(stamp, netSection.createSection(index.toString()))
        }

        benchSlots?.let { config.set("bench-slots", it) }
        val benchSection = config.createSection("bench")
        for (index in 0 until bench.slotCount) {
            val slot = bench.slotAt(index) ?: continue
            FilletSlotYaml.save(slot, benchSection.createSection(index.toString()))
        }
    }

    companion object {

        /**
         * 저장된 파일을 읽는다. **워커 스레드에서 부르지 말 것** — [FishRegistry] 를 본다.
         *
         * 도감 칸 수는 저장하지 않고 **지금 등록된 물고기에서 다시 가져온다.** 관리자가
         * 칸 수를 늘리면 이미 퍼펙트였던 사람도 다시 채울 자리가 생겨야 한다.
         */
        fun load(
            id: UUID,
            config: YamlConfiguration,
            settings: FishingConfig,
            fish: FishRegistry,
        ): Angler {
            val angler = Angler(id, config.getString("name").orEmpty(), settings)
            angler.minigameEnabled = config.getBoolean("minigame", true)
            angler.practice = config.getBoolean("practice", false)

            angler.fatigue.restoreState(
                amount = config.getInt("fatigue.value", settings.fatigue.default),
                wasLocked = config.getBoolean("fatigue.locked", false),
                recoveredAt = if (config.contains("fatigue.recovered-at")) {
                    config.getLong("fatigue.recovered-at")
                } else {
                    null
                },
            )

            config.getConfigurationSection("collection")?.let { entries ->
                for (fishId in entries.getKeys(false)) {
                    val section = entries.getConfigurationSection(fishId) ?: continue
                    val definition = fish.get(fishId)
                    val gradeId = definition?.gradeId ?: section.getString("grade").orEmpty()
                    val maxSlots = definition?.maxSlots ?: settings.defaultMaxSlots
                    angler.collection.put(CollectionEntry.load(fishId, gradeId, maxSlots, section))
                }
            }

            config.getConfigurationSection("net")?.let { netSection ->
                for (key in netSection.getKeys(false).sortedBy { it.toIntOrNull() ?: 0 }) {
                    val section = netSection.getConfigurationSection(key) ?: continue
                    FishStampYaml.load(section)?.let { angler.net.add(it) }
                }
            }

            if (config.isInt("bench-slots")) angler.setBenchSlots(config.getInt("bench-slots"))
            config.getConfigurationSection("bench")?.let { benchSection ->
                for (key in benchSection.getKeys(false)) {
                    val index = key.toIntOrNull() ?: continue
                    val section = benchSection.getConfigurationSection(key) ?: continue
                    FilletSlotYaml.load(section)?.let { angler.bench.restore(index, it) }
                }
            }

            return angler
        }
    }
}
