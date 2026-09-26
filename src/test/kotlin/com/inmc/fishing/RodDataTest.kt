package com.inmc.fishing

import com.inmc.fishing.rod.RodData
import kr.inmc.core.item.ItemRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 커스텀아이템에서 만든 낚싯대가 낚시에 제대로 넘어오는지.
 *
 * **열쇠 이름 하나를 오타 내면 오류 없이 0짜리 낚싯대가 나온다.** 만든 사람은 보너스가
 * 안 먹는다고 느낄 뿐 이유를 모르고, 로그에도 아무것도 안 남는다.
 *
 * 열쇠 이름은 **커스텀아이템 플러그인의 문서·화면·예시와 글자 단위로 같아야** 한다.
 * 거기는 이 플러그인을 알지 못하고, 그게 이 설계의 요점이다 — 그래서 여기가 유일한
 * 안전장치다.
 */
class RodDataTest {

    private val ref = ItemRef.Namespaced("inmc", "example_rod")

    /** 커스텀아이템의 배포 예시(`example_rod`)와 **같은 값**이다. */
    private val example = mapOf(
        "fishing.rod" to "1",
        "fishing.reel-power" to "15",
        "fishing.line-strength" to "30",
        "fishing.reel-durability" to "15",
        "fishing.big-fish-chance" to "2",
        "fishing.double-chance" to "3",
        "fishing.max-fatigue" to "500",
        "fishing.fatigue-recovery" to "50",
        "fishing.grade-bonus.a" to "3",
        "fishing.grade-bonus.s" to "2",
    )

    @Test
    fun `배포 예시가 값을 하나도 잃지 않고 낚싯대가 된다`() {
        // 이 테스트가 깨지면 커스텀아이템의 items.yml 예시가 낚싯대로 안 읽힌다는 뜻이다.
        val rod = RodData.toRod("example_rod", ref, example)

        assertEquals(15.0, rod.reelPower)
        assertEquals(30.0, rod.lineStrength)
        assertEquals(15.0, rod.reelDurability)
        assertEquals(2.0, rod.bigFishChance)
        assertEquals(3.0, rod.doubleChance)
        assertEquals(500, rod.maxFatigue)
        assertEquals(50, rod.fatigueRecovery)
        assertEquals(mapOf("a" to 3.0, "s" to 2.0), rod.gradeBonus)
    }

    @Test
    fun `열쇠 이름이 커스텀아이템 쪽과 같다`() {
        // 상수가 바뀌면 여기가 먼저 빨개진다. 그때 저쪽 문서·화면·예시도 같이 고쳐야 한다.
        assertEquals("fishing.rod", RodData.ROD)
        assertEquals("fishing.grade-bonus", RodData.GRADE_BONUS)
        assertEquals("fishing.big-fish-chance", RodData.BIG_FISH_CHANCE)
        assertEquals("fishing.double-chance", RodData.DOUBLE_CHANCE)
        assertEquals("fishing.max-fatigue", RodData.MAX_FATIGUE)
        assertEquals("fishing.fatigue-recovery", RodData.FATIGUE_RECOVERY)
        assertEquals("fishing.reel-power", RodData.REEL_POWER)
        assertEquals("fishing.line-strength", RodData.LINE_STRENGTH)
        assertEquals("fishing.reel-durability", RodData.REEL_DURABILITY)
        assertEquals("fishing.minigame-time", RodData.MINIGAME_TIME)
        assertEquals("fishing.bait-save", RodData.BAIT_SAVE)
        assertEquals("fishing.size-bonus", RodData.SIZE_BONUS)
        assertEquals("fishing.fatigue-save", RodData.FATIGUE_SAVE)
        assertEquals("fishing.auto-catch-reduce", RodData.AUTO_CATCH_REDUCE)
    }

    @Test
    fun `표시가 없으면 낚싯대가 아니다`() {
        // 이게 없으면 서버의 **모든** 커스텀아이템이 낚싯대 후보가 되고, 아무 아이템이나
        // 들고 낚시하면 보너스 0 짜리 낚싯대로 인식된다.
        assertTrue(RodData.isRod(mapOf("fishing.rod" to "1")))
        assertTrue(RodData.isRod(mapOf("fishing.rod" to "아무거나")))
        assertTrue(!RodData.isRod(mapOf("fishing.reel-power" to "15")))
        assertTrue(!RodData.isRod(emptyMap()))
    }

    @Test
    fun `표시만 적으면 보너스 없는 평범한 낚싯대가 된다`() {
        // 읽기 실패로 다뤄 낚시를 막는 것보다 낫다 — "낚싯대로는 인정하되 특별할 것은 없다".
        val rod = RodData.toRod("plain", ref, mapOf("fishing.rod" to "1"))

        assertEquals(0.0, rod.reelPower)
        assertEquals(0, rod.maxFatigue)
        assertEquals(emptyMap(), rod.gradeBonus)
    }

    @Test
    fun `숫자가 아닌 값은 0 으로 떨어진다`() {
        // 관리자가 "15cm" 라고 적어도 아이템 전체가 죽으면 안 된다.
        val rod = RodData.toRod(
            "typo",
            ref,
            mapOf("fishing.rod" to "1", "fishing.reel-power" to "열다섯"),
        )

        assertEquals(0.0, rod.reelPower)
    }

    @Test
    fun `등급 보너스 열쇠에서 등급만 떼어낸다`() {
        val rod = RodData.toRod(
            "x",
            ref,
            mapOf(
                "fishing.rod" to "1",
                "fishing.grade-bonus.S" to "5",
                "fishing.grade-bonus" to "9",
            ),
        )

        assertEquals(mapOf("s" to 5.0), rod.gradeBonus, "등급 없는 열쇠는 무시한다")
    }

    @Test
    fun `편의 열쇠 다섯도 읽는다`() {
        val rod = RodData.toRod(
            "x", ref,
            mapOf(
                "fishing.rod" to "1", "fishing.minigame-time" to "2", "fishing.bait-save" to "25",
                "fishing.size-bonus" to "10", "fishing.fatigue-save" to "15", "fishing.auto-catch-reduce" to "1.5",
            ),
        )
        assertEquals(2.0, rod.minigameSeconds)
        assertEquals(25.0, rod.baitSaveChance)
        assertEquals(10.0, rod.sizePercent)
        assertEquals(15.0, rod.fatigueSaveChance)
        assertEquals(1.5, rod.autoCatchReduce)
    }

    @Test
    fun `인첸트 값은 낚싯대 값에 더해진다 - 덮지 않는다`() {
        // 낚싯대에 붙은 커스텀 인첸트(core CustomEnchantHook.data)가 이 길로 들어온다.
        val rod = RodData.toRod("example_rod", ref, example)
        val enchanted = RodData.plus(rod, mapOf("fishing.reel-power" to "5", "fishing.grade-bonus.s" to "1", "fishing.size-bonus" to "8"))

        assertEquals(20.0, enchanted.reelPower)
        assertEquals(30.0, enchanted.lineStrength)
        assertEquals(mapOf("a" to 3.0, "s" to 3.0), enchanted.gradeBonus)
        assertEquals(8.0, enchanted.sizePercent)
        assertEquals(rod.id, enchanted.id)
    }

    @Test
    fun `낚시 열쇠가 없는 인첸트 값은 낚싯대를 바꾸지 않는다`() {
        assertTrue(!RodData.hasFishingKeys(mapOf("combat.power" to "3")))
        assertTrue(RodData.hasFishingKeys(mapOf("fishing.bait-save" to "3")))
    }

    @Test
    fun `참조가 그대로 남는다`() {
        // 참조를 잃으면 낚싯대를 만들 수 없다 — 지급도 화면 아이콘도 이걸로 만든다.
        val rod = RodData.toRod("example_rod", ref, example)

        assertEquals(ref, rod.item.ref)
        assertEquals(kr.inmc.core.item.StorageMode.REFERENCE, rod.item.mode)
    }

    @Test
    fun `추첨 보정으로 그대로 넘어간다`() {
        // 등급 보너스가 **덧셈 축**으로 가야 한다. 곱셈 축으로 가면 가중치 1 인 S 에
        // 아무 효과가 없다.
        val bonuses = RodData.toRod("example_rod", ref, example).bonuses()

        assertEquals(mapOf("a" to 3.0, "s" to 2.0), bonuses.gradeFlatBonus)
        assertEquals(emptyMap(), bonuses.gradeMultipliers)
        assertEquals(2.0, bonuses.bigFishChance)
    }
}
