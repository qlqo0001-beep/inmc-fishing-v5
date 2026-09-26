package com.inmc.fishing.fish

import com.inmc.fishing.Fishing
import com.inmc.fishing.registry.DefinitionStore
import kr.inmc.core.item.StoredItem
import org.bukkit.configuration.ConfigurationSection

/**
 * 낚을 수 있는 것 전부. `fish.yml` 한 파일에 담긴다.
 *
 * **2세대는 등급마다 파일이 따로였다** (`items/f-grade.yml` … `s-grade.yml`). 그러면 물고기의
 * 등급을 바꾸는 것이 "파일 사이로 옮기기"가 되어 GUI 로 할 수 없는 일이 된다. 여기서는 등급이
 * 물고기의 **필드**라 한 칸만 고치면 된다.
 *
 * [grouped] 는 추첨기가 쓰는 등급별 묶음이다. 매번 만들면 낚을 때마다 전체를 훑게 되므로
 * 목록이 바뀔 때만 다시 만든다.
 */
class FishRegistry(fishing: Fishing) : DefinitionStore<Fish>(
    fishing = fishing,
    fileName = "fish.yml",
    header = "낚을 수 있는 것 전부. /낚시 관리 에서 손에 든 것을 그대로 등록할 수 있습니다.\n",
    what = "물고기",
    rootKey = "fish",
) {

    @Volatile
    private var byGrade: Map<String, List<Fish>> = emptyMap()

    /** 등급 id → 그 등급의 물고기. [RollEngine] 이 그대로 받는다. */
    fun grouped(): Map<String, List<Fish>> = byGrade

    fun ofGrade(gradeId: String?): List<Fish> = byGrade[gradeId?.lowercase()].orEmpty()

    /** 크기 기록이 있는 것만. 랭킹 보드와 도감의 크기 칸이 쓴다. */
    fun sized(): List<Fish> = all().filter { it.hasSize }

    override val appearanceRole: String? get() = com.inmc.fishing.registry.FishingRoles.FISH

    override fun withItem(value: Fish, item: kr.inmc.core.item.StoredItem): Fish = value.copy(item = item)

    override fun idOf(value: Fish): String = value.id

    override fun itemOf(value: Fish): StoredItem = value.item

    override fun decorOf(value: Fish) = value.decor

    override fun read(key: String, section: ConfigurationSection): Fish? {
        val gradeId = section.getString("grade").orEmpty().ifBlank { return null }
        return Fish.load(key, gradeId, section, defaultSlots = fishing.config.defaultMaxSlots)
    }

    override fun write(value: Fish, section: ConfigurationSection) = value.save(section)

    /**
     * 등급별 묶음을 다시 만들고 **추첨기까지 다시 세운다.**
     *
     * 추첨기는 이 묶음의 스냅샷이라, 목록이 바뀌면 같이 바뀌어야 한다. 부르는 쪽에 맡기면
     * 언젠가 한 곳에서 빠지고 — 그러면 GUI 로 만든 물고기가 등록은 됐는데 **아무리 던져도
     * 안 나오는** 상태가 된다. 오류는 나지 않는다. 그래서 여기서 같이 한다.
     */
    override fun reindex() {
        byGrade = all().groupBy { it.gradeId }
        fishing.rebuildEngine()
    }

    /** 손에 든 것을 그대로 등록한다. 같은 id 가 있으면 덮어쓴다. */
    fun register(id: String, gradeId: String, stack: org.bukkit.inventory.ItemStack): Fish {
        val fish = Fish(
            id = id.lowercase(),
            gradeId = gradeId.lowercase(),
            item = fishing.itemResolver.capture(stack),
            weight = 1,
            size = SizeRange.NONE,
            doubleEnabled = true,
            commands = emptyList(),
            fatigueRecovery = 0,
            maxSlots = fishing.config.defaultMaxSlots,
        )
        put(fish)
        return fish
    }
}
