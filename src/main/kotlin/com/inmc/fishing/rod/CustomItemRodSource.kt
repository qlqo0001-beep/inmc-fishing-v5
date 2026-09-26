package com.inmc.fishing.rod

import com.inmc.fishing.Fishing
import kr.inmc.core.item.ItemRef
import kr.inmc.core.item.StorageMode
import kr.inmc.core.item.StoredItem
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

/**
 * 커스텀아이템 플러그인에서 정의한 낚싯대.
 *
 * **이 파일이 [RodSource] 를 만든 이유다.** 낚싯대를 아이템 플러그인에서 만들 수 있게 하되,
 * 낚시가 그 플러그인을 컴파일 시점에 알지 않도록 하는 것.
 *
 * 어떻게 되는가:
 * 1. 관리자가 커스텀아이템에서 아이템을 만들고 `fishing.rod: 1` 을 적는다
 * 2. 그 플러그인이 core 의 [kr.inmc.core.integration.CustomItemHook] 에 공급처로 등록된다
 * 3. 여기서 core 에게 "이 참조에 딸린 값들"을 물어 [Rod] 로 옮긴다
 *
 * **낚시는 그 플러그인이 있는지 모른다.** core 의 `data(ref)` 만 부르고, 아무도 안 꽂혀
 * 있으면 빈 맵이 와서 낚싯대가 하나도 없는 것과 같아진다.
 *
 * `rods.yml` 을 대체하지 않는다 — [com.inmc.fishing.Fishing.rods] 가 어느 쪽을 볼지 정하고,
 * 둘 다 보고 싶으면 [Combined] 를 쓴다.
 */
class CustomItemRodSource(private val fishing: Fishing) : RodSource {

    /**
     * 이 아이템이 낚싯대라고 선언하는 열쇠.
     *
     * 값이 있기만 하면 된다 — `1`, `true`, 아무거나. **없으면 낚싯대가 아니다.** 이 표시가
     * 없으면 서버의 모든 커스텀아이템이 낚싯대 후보가 되고, 그중 하나를 들고 낚시를 하면
     * 보너스 0 짜리 낚싯대로 인식된다.
     */
    private fun isRod(data: Map<String, String>): Boolean = RodData.isRod(data)

    override fun all(): List<Rod> {
        // 커스텀아이템 쪽 목록을 통째로 가져올 길이 core 에 없다 — 있어야 할 이유도 없다.
        // 목록이 필요한 자리(GUI·명령어 추천)는 `rods.yml` 쪽이 채운다.
        return emptyList()
    }

    override fun byId(id: String?): Rod? {
        val key = id?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
        val ref = ItemRef.Namespaced(NAMESPACE, key)
        val data = fishing.customItems.data(ref)
        if (!isRod(data)) return null
        return build(key, ref, data)
    }

    /**
     * 손에 든 것이 커스텀아이템이면 그 id 로 되묻는다.
     *
     * **핫 패스다** — 던질 때마다 불린다. core 의 `identify` 가 PDC 한 번 읽기로 끝나므로
     * 여기서 목록을 훑지 않는다.
     */
    override fun identify(stack: ItemStack?): Rod? {
        if (stack == null || stack.type.isAir) return null
        val ref = fishing.customItems.identify(stack) ?: return null
        if (!ref.namespace.equals(NAMESPACE, ignoreCase = true)) return null
        val data = fishing.customItems.data(ref)
        if (!isRod(data)) return null
        return build(ref.id, ref, data)
    }

    /**
     * 연동 값을 [Rod] 로 옮긴다.
     *
     * **없는 값은 0 이다.** 관리자가 `fishing.rod: 1` 만 적고 나머지를 안 적었으면 보너스
     * 없는 평범한 낚싯대가 된다 — 그게 "낚싯대로는 인정하되 특별할 것은 없다"는 뜻이고,
     * 읽기 실패로 다뤄 낚시를 막는 것보다 낫다.
     */
    private fun build(id: String, ref: ItemRef.Namespaced, data: Map<String, String>): Rod =
        RodData.toRod(id, ref, data)

    private companion object {
        const val NAMESPACE = "inmc"
    }
}

/**
 * 연동 값을 [Rod] 로 옮긴다. **그게 전부다.**
 *
 * 따로 뺀 이유는 검증하기 위해서다. 열쇠 이름 하나를 오타 내면 **오류 없이 0짜리
 * 낚싯대가 나온다** — 만든 사람은 보너스가 안 먹는다고 느낄 뿐 이유를 모른다.
 *
 * 열쇠 이름은 **커스텀아이템 플러그인의 문서·화면·예시와 글자 단위로 같아야** 한다.
 * 거기는 이 플러그인을 알지 못하며, 그게 이 설계의 요점이다.
 */
object RodData {

    const val ROD = "fishing.rod"
    const val GRADE_BONUS = "fishing.grade-bonus"
    const val BIG_FISH_CHANCE = "fishing.big-fish-chance"
    const val DOUBLE_CHANCE = "fishing.double-chance"
    const val MAX_FATIGUE = "fishing.max-fatigue"
    const val FATIGUE_RECOVERY = "fishing.fatigue-recovery"
    const val REEL_POWER = "fishing.reel-power"
    const val LINE_STRENGTH = "fishing.line-strength"
    const val REEL_DURABILITY = "fishing.reel-durability"
    const val MINIGAME_TIME = "fishing.minigame-time"
    const val BAIT_SAVE = "fishing.bait-save"
    const val SIZE_BONUS = "fishing.size-bonus"
    const val FATIGUE_SAVE = "fishing.fatigue-save"
    const val AUTO_CATCH_REDUCE = "fishing.auto-catch-reduce"

    /**
     * 이 아이템이 낚싯대라고 선언했는지.
     *
     * 값이 있기만 하면 된다. **없으면 낚싯대가 아니다** — 이 표시가 없으면
     * 서버의 모든 커스텀아이템이 낚싯대 후보가 된다.
     */
    fun isRod(data: Map<String, String>): Boolean = data.containsKey(ROD)

    fun toRod(id: String, ref: ItemRef.Namespaced, data: Map<String, String>): Rod =
        plus(
            Rod(
                id = id,
                item = StoredItem(
                    ref = ref,
                    // 재질을 모른다. 알아볼 때는 PDC 를 쓰고 만들 때는 core 가 참조를 푸므로
                    // 이 값은 화면 대체 아이콘으로만 쓰인다.
                    material = Material.FISHING_ROD,
                    mode = StorageMode.REFERENCE,
                ),
            ),
            data,
        )

    /**
     * 낚싯대에 연동 값을 **더한다**. 커스텀아이템 낚싯대를 만들 때와, 낚싯대에 붙은 커스텀 인첸트
     * (core `CustomEnchantHook.data`)를 얹을 때 같은 길을 쓴다 — 열쇠 해석이 두 벌이면 한쪽만 고쳐진다.
     */
    fun plus(rod: Rod, data: Map<String, String>): Rod {
        if (data.isEmpty()) return rod
        val bonus = HashMap(rod.gradeBonus)
        for ((key, value) in data) {
            if (!key.startsWith(GRADE_BONUS)) continue
            val grade = key.removePrefix(GRADE_BONUS).trim('.', ' ').lowercase()
            if (grade.isBlank()) continue
            value.trim().toDoubleOrNull()?.let { bonus[grade] = (bonus[grade] ?: 0.0) + it }
        }
        return rod.copy(
            gradeBonus = bonus,
            bigFishChance = rod.bigFishChance + num(data, BIG_FISH_CHANCE),
            doubleChance = rod.doubleChance + num(data, DOUBLE_CHANCE),
            maxFatigue = rod.maxFatigue + num(data, MAX_FATIGUE).toInt(),
            fatigueRecovery = rod.fatigueRecovery + num(data, FATIGUE_RECOVERY).toInt(),
            reelPower = rod.reelPower + num(data, REEL_POWER),
            lineStrength = rod.lineStrength + num(data, LINE_STRENGTH),
            reelDurability = rod.reelDurability + num(data, REEL_DURABILITY),
            minigameSeconds = rod.minigameSeconds + num(data, MINIGAME_TIME),
            baitSaveChance = rod.baitSaveChance + num(data, BAIT_SAVE),
            sizePercent = rod.sizePercent + num(data, SIZE_BONUS),
            fatigueSaveChance = rod.fatigueSaveChance + num(data, FATIGUE_SAVE),
            autoCatchReduce = rod.autoCatchReduce + num(data, AUTO_CATCH_REDUCE),
        )
    }

    /** 이 값들 중 낚시가 읽는 열쇠가 하나라도 있는가. */
    fun hasFishingKeys(data: Map<String, String>): Boolean = data.keys.any { it.startsWith("fishing.") }

    private fun num(data: Map<String, String>, key: String): Double =
        data[key]?.trim()?.toDoubleOrNull() ?: 0.0
}

/**
 * 두 공급처를 겹쳐 쓴다. **커스텀아이템이 먼저다.**
 *
 * 아이템 플러그인으로 옮겨가는 중인 서버가 실제 상황이다 — 예전 `rods.yml` 낚싯대가 아직
 * 사람들 손에 있는데 새것부터 저쪽에서 만들기 시작한다. 둘 중 하나만 되면 그 이행 기간에
 * 한쪽이 통째로 죽는다.
 *
 * 편집은 `rods.yml` 쪽만 열어둔다([editable]). 커스텀아이템 정의를 낚시 화면에서 고치면
 * 같은 아이템을 두 곳에서 고치게 되고, 어느 쪽이 이기는지가 순서에 달리게 된다.
 */
class CombinedRodSource(
    private val primary: RodSource,
    private val fallback: RodSource,
) : RodSource {

    override fun all(): List<Rod> = primary.all() + fallback.all()

    override fun byId(id: String?): Rod? = primary.byId(id) ?: fallback.byId(id)

    override fun identify(stack: ItemStack?): Rod? =
        primary.identify(stack) ?: fallback.identify(stack)

    override val editable: Boolean get() = fallback.editable

    override fun register(rod: Rod) = fallback.register(rod)

    override fun unregister(id: String): Boolean = fallback.unregister(id)
}
