package com.inmc.fishing.hook

import com.inmc.fishing.Fishing
import com.inmc.fishing.catching.RandomRolls
import com.inmc.fishing.command.FishingTools
import com.inmc.fishing.fish.Fish
import com.inmc.fishing.fish.FishLore
import com.inmc.fishing.fish.FishStamp
import com.inmc.fishing.fish.Rolls
import com.inmc.fishing.fish.TrophyRule
import kr.inmc.core.integration.CustomItemHook
import kr.inmc.core.item.ItemRef
import org.bukkit.inventory.ItemStack

/**
 * **만들기 전용** 공급처 `inmcfish:<물고기>` · `inmcfish:<물고기>:trophy` (2026-10-10).
 *
 * 진짜 물고기는 `inmc:<id>` 참조로 만들 수 없다 — 커스텀아이템의 `fishing.fish` 역할은 겉모습뿐이라
 * [FishStamp] 이 없고, 도장이 없으면 도감에 등록되지 않는다. 이 공급처를 꽂으면 essentials `/지급`·킷·urb 보상이
 * 같은 참조로 도장 찍힌 물고기를 만든다.
 *
 * **[identify] 는 언제나 null 이다.** 알아보기 시작하면 모든 플러그인의 보상 캡처(`ItemResolver.identifyAll`)가 손에 든
 * 물고기를 이 참조로 저장하고, 받을 때마다 크기를 다시 굴리게 된다 — 조용한 동작 변화.
 *
 * 네임스페이스가 `fish` 가 아닌 것은 ItemsAdder 네임스페이스와 겹치지 않게 하려는 것이다(`register` 는 같은
 * 이름을 **갈아 끼운다**).
 */
class FishItemProvider(private val fishing: Fishing) : CustomItemHook.Provider {

    private val rolls = RandomRolls()

    override val namespace: String get() = NAMESPACE

    /** 만들기 전용 — 찾기·지우기 제안에서 빠지고 "꽂혔나" 에 세지 않는다(core `makeOnly`). */
    override val makeOnly: Boolean get() = true

    override fun identify(stack: ItemStack): String? = null

    override fun create(ref: ItemRef.Namespaced): ItemStack? {
        if (!ref.namespace.equals(NAMESPACE, ignoreCase = true)) return null
        val (fishId, trophy) = parse(ref.id) ?: return null
        val fish = fishing.fish.get(fishId) ?: return null
        if (trophy) return FishingTools(fishing).trophyStack(fish, 1)

        val config = fishing.config
        val stamp = rollStamp(fish, config.trophyRule, config.bigFishChance, rolls, System.currentTimeMillis())
        val stack = fishing.fish.stack(fish, 1, FishLore.of(stamp, fishing.grades[fish.gradeId]?.tag().orEmpty())) ?: return null
        stamp.applyTo(stack)
        return stack
    }

    /** 물고기 id 전부, 크기가 있는 것은 `:trophy` 도. 정의를 다 읽기 전에는 빈 목록. */
    override fun ids(): Collection<String> = fishing.fish.all().flatMap { fish ->
        if (fish.hasSize) listOf(fish.id, fish.id + TROPHY_SUFFIX) else listOf(fish.id)
    }

    companion object {
        const val NAMESPACE = "inmcfish"
        private const val TROPHY_SUFFIX = ":trophy"

        /** `<물고기>` → (id, false), `<물고기>:trophy` → (id, true). 그 밖의 꼴은 null. id 는 소문자로(정의 저장소가 소문자). */
        fun parse(id: String): Pair<String, Boolean>? {
            val parts = id.trim().lowercase().split(':')
            return when {
                parts.size == 1 && parts[0].isNotBlank() -> parts[0] to false
                parts.size == 2 && parts[0].isNotBlank() && parts[1] == "trophy" -> parts[0] to true
                else -> null
            }
        }

        /**
         * 장비 보너스 없이 **진짜로 낚은 것처럼** 크기를 굴린다 — `RollEngine.roll` 의 3·5·6 단계(대어 → 크기 → 트로피)와
         * 같은 순서·같은 식이다. 더블은 개수의 일이라 여기서 보지 않는다. 등급은 물고기의 등급 그대로.
         */
        fun rollStamp(fish: Fish, rule: TrophyRule, bigFishChance: Double, rolls: Rolls, now: Long): FishStamp {
            val bigFish = rule.canBeTrophy(fish.size) && rolls.next() * 100.0 < bigFishChance
            val size = if (bigFish) fish.size.rollAtLeast(rule.normalFloor(fish.size), rolls.next()) else fish.size.roll(rolls.gaussian())
            return FishStamp(fish.id, fish.gradeId, size, rule.judge(fish.size, size), now)
        }
    }
}
