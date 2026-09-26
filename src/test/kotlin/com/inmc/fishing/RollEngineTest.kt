package com.inmc.fishing

import com.inmc.fishing.fish.Bonuses
import com.inmc.fishing.fish.Fish
import com.inmc.fishing.fish.RollEngine
import com.inmc.fishing.fish.Rolls
import com.inmc.fishing.fish.SizeRange
import com.inmc.fishing.fish.Trophy
import com.inmc.fishing.fish.TrophyRule
import com.inmc.fishing.grade.Grade
import com.inmc.fishing.grade.GradeTable
import kr.inmc.core.item.ItemRef
import kr.inmc.core.item.StoredItem
import org.bukkit.Material
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 낚시 한 번의 추첨 전체를 못박는다.
 *
 * **순서가 곧 사양이다.** 순서가 뒤집혀도 플러그인은 멀쩡히 돌기 때문에 테스트 말고는 잡을
 * 방법이 없다. 특히 **대어 판정이 물고기 추첨보다 뒤**여야 한다 — 그 물고기의 크기 범위를
 * 알아야 트로피 구간을 정할 수 있다.
 *
 * 추첨 순서(`Scripted` 에 넣는 값의 순서): 등급 · 물고기 · 대어 · 더블 · (대어면) 크기
 */
class RollEngineTest {

    /** 미리 정한 값을 순서대로 돌려주는 난수. 다 쓰면 마지막 값을 반복한다. */
    private class Scripted(private val values: List<Double>, private val gaussian: Double = 0.0) : Rolls {
        private var index = 0
        override fun next(): Double = values.getOrElse(index++) { values.lastOrNull() ?: 0.0 }
        override fun gaussian(): Double = gaussian
    }

    private fun grade(id: String) = Grade(id, id.uppercase(), 50.0, 3, 5.0, "<white>")

    private fun fish(id: String, gradeId: String, weight: Int, size: SizeRange = SizeRange(10.0, 80.0, 35.0)) =
        Fish(
            id = id,
            gradeId = gradeId,
            item = StoredItem(ItemRef.Vanilla(Material.COD), Material.COD, displayName = id),
            weight = weight,
            size = size,
            doubleEnabled = true,
            commands = emptyList(),
            fatigueRecovery = 0,
            maxSlots = 10,
        )

    private val grades = GradeTable(listOf(grade("f"), grade("e")))

    private val fishByGrade = mapOf(
        "f" to listOf(fish("cod", "f", 70), fish("salmon", "f", 30)),
        "e" to listOf(fish("tuna", "e", 100)),
    )

    private fun engine(bigFish: Double = 0.0, double: Double = 0.0) = RollEngine(
        grades = grades,
        fishByGrade = fishByGrade,
        trophyRule = TrophyRule.DEFAULT,
        bigFishChance = bigFish,
        doubleChance = double,
    )

    // --- 기본 흐름 ------------------------------------------------------------------

    @Test
    fun `가장 단순한 한 번`() {
        val result = engine().roll(Scripted(listOf(0.0)))

        assertNotNull(result)
        assertEquals("f", result.grade.id)
        assertEquals("cod", result.fish.id)
        assertFalse(result.bigFish)
        assertFalse(result.double)
        assertEquals(35.0, result.size, "가우시안 0 이면 평균")
        assertEquals(Trophy.NONE, result.trophy)
    }

    @Test
    fun `등급이 없으면 아무것도 안 나온다`() {
        val empty = RollEngine(GradeTable(emptyList()), emptyMap(), TrophyRule.DEFAULT, 0.0, 0.0)

        assertNull(empty.roll(Scripted(listOf(0.5))))
    }

    @Test
    fun `그 등급에 물고기가 없으면 아무것도 안 나온다`() {
        val engine = RollEngine(grades, emptyMap(), TrophyRule.DEFAULT, 0.0, 0.0)

        assertNull(engine.roll(Scripted(listOf(0.0))))
    }

    // --- 대어 = 트로피 보장 -----------------------------------------------------------

    @Test
    fun `대어는 반드시 트로피가 된다`() {
        // 이 시스템의 전제다. 대어인데 트로피가 아니면 이름이 거짓말이 된다.
        val result = engine(bigFish = 100.0).roll(Scripted(listOf(0.0)))

        assertNotNull(result)
        assertTrue(result.bigFish)
        assertNotEquals(Trophy.NONE, result.trophy)
    }

    @Test
    fun `대어는 등급을 올리지 않는다`() {
        // 2세대는 승급이었다. 지금은 같은 등급의 큰 개체다 - 그게 "대어"라는 말에 맞다.
        val result = engine(bigFish = 100.0).roll(Scripted(listOf(0.0)))

        assertEquals("f", result!!.grade.id)
        assertEquals("cod", result.fish.id)
    }

    @Test
    fun `대어 크기는 트로피 하한 이상이다`() {
        // cod 평균 35 × 1.45 = 50.75 가 하한. 마지막 roll 0.0 이면 구간의 맨 아래다.
        val result = engine(bigFish = 100.0).roll(Scripted(listOf(0.0)))

        assertTrue(result!!.size >= 50.75, "크기 ${result.size} 가 하한보다 작다")
        assertEquals(Trophy.NORMAL, result.trophy)
    }

    @Test
    fun `대어 구간의 맨 위는 최대 크기이고 레어가 된다`() {
        // 등급 · 물고기 · 대어 · 더블 · 크기 순서. 마지막을 1.0 으로 준다.
        val result = engine(bigFish = 100.0).roll(Scripted(listOf(0.0, 0.0, 0.0, 0.9, 1.0)))

        assertEquals(80.0, result!!.size)
        assertEquals(Trophy.RARE, result.trophy)
    }

    @Test
    fun `크기 없는 물고기에는 대어가 걸리지 않는다`() {
        // 장화가 트로피가 될 수는 없다. 확률이 100% 여도 성립하지 않아야 한다.
        val trash = RollEngine(
            grades,
            mapOf("f" to listOf(fish("boot", "f", 100, SizeRange.NONE))),
            TrophyRule.DEFAULT,
            100.0,
            0.0,
        )

        val result = trash.roll(Scripted(listOf(0.0)))

        assertNotNull(result)
        assertFalse(result.bigFish)
        assertEquals(Trophy.NONE, result.trophy)
    }

    @Test
    fun `트로피 하한이 최대를 넘는 물고기에는 대어가 안 걸린다`() {
        // 평균이 최대에 가까우면 평균×1.45 도 최대×1.5 도 최대를 넘어 트로피가 불가능하다.
        // 그런 물고기에 대어가 걸리면 "대어인데 트로피가 아닌" 모순이 생긴다.
        val narrow = RollEngine(
            grades,
            mapOf("f" to listOf(fish("tight", "f", 100, SizeRange(90.0, 100.0, 99.0)))),
            TrophyRule(normalMultiplier = 1.45, rareMultiplier = 1.5),
            100.0,
            0.0,
        )

        assertFalse(narrow.roll(Scripted(listOf(0.0)))!!.bigFish)
    }

    @Test
    fun `낚싯대 대어 보너스는 덧셈이다`() {
        val result = engine(bigFish = 0.0)
            .roll(Scripted(listOf(0.0)), Bonuses(bigFishChance = 100.0))

        assertNotNull(result)
        assertTrue(result.bigFish)
        assertNotEquals(Trophy.NONE, result.trophy)
    }

    @Test
    fun `대어가 아니면 크기는 정규분포를 따른다`() {
        // 대어 판정이 크기 추첨 방식을 갈라놓는다. 섞이면 평범한 물고기가 전부 커진다.
        val result = engine(bigFish = 0.0).roll(Scripted(listOf(0.0), gaussian = 0.0))

        assertEquals(35.0, result!!.size)
    }

    // --- 물고기 추첨 ----------------------------------------------------------------

    @Test
    fun `물고기는 가중치로 뽑힌다`() {
        // roll 1 등급 / roll 2 물고기. cod 70 · salmon 30 -> 누적 0~70 / 70~100.
        assertEquals("cod", engine().roll(Scripted(listOf(0.0, 0.69, 0.5)))?.fish?.id)
        assertEquals("salmon", engine().roll(Scripted(listOf(0.0, 0.70, 0.5)))?.fish?.id)
        assertEquals("salmon", engine().roll(Scripted(listOf(0.0, 0.999, 0.5)))?.fish?.id)
    }

    @Test
    fun `가중치가 전부 0 이어도 물고기가 나온다`() {
        val zeroed = RollEngine(
            grades,
            mapOf("f" to listOf(fish("cod", "f", 0))),
            TrophyRule.DEFAULT,
            0.0,
            0.0,
        )

        assertEquals("cod", zeroed.roll(Scripted(listOf(0.0)))?.fish?.id)
    }

    // --- 더블 ---------------------------------------------------------------------

    @Test
    fun `더블 확률에 걸리면 두 개다`() {
        val result = engine(double = 100.0).roll(Scripted(listOf(0.0)))

        assertNotNull(result)
        assertTrue(result.double)
    }

    @Test
    fun `더블이 꺼진 물고기는 확률과 무관하게 안 걸린다`() {
        // 쓰레기 아이템을 두 개 주면 안 된다.
        val noDouble = RollEngine(
            grades,
            mapOf("f" to listOf(fish("boot", "f", 100).copy(doubleEnabled = false))),
            TrophyRule.DEFAULT,
            0.0,
            100.0,
        )

        assertFalse(noDouble.roll(Scripted(listOf(0.0)))!!.double)
    }

    // --- 크기와 미끼 ----------------------------------------------------------------

    @Test
    fun `미끼 퍼센트 보정이 크기에 곱해진다`() {
        val result = engine().roll(Scripted(listOf(0.0)), Bonuses(sizePercent = 50.0))

        assertEquals(52.5, result!!.size, 1e-9)
    }

    @Test
    fun `미끼 고정 보정이 퍼센트 뒤에 더해진다`() {
        // (35 × 1.5) + 10 = 62.5. 순서가 뒤집히면 (35 + 10) × 1.5 = 67.5 다.
        val result = engine().roll(Scripted(listOf(0.0)), Bonuses(sizePercent = 50.0, sizeFixed = 10.0))

        assertEquals(62.5, result!!.size, 1e-9)
    }

    @Test
    fun `미끼로 키운 크기는 최대치를 넘을 수 있다`() {
        // 미끼를 쓴 보람이 있어야 하고, 그래서 트로피 판정보다 먼저 적용된다.
        val result = engine().roll(Scripted(listOf(0.0)), Bonuses(sizeFixed = 100.0))

        assertEquals(135.0, result!!.size, 1e-9)
        assertEquals(Trophy.RARE, result.trophy, "미끼로 키운 크기도 트로피가 된다")
    }

    @Test
    fun `크기가 없는 것에는 미끼 보정이 붙지 않는다`() {
        val trash = RollEngine(
            grades,
            mapOf("f" to listOf(fish("boot", "f", 100, SizeRange.NONE))),
            TrophyRule.DEFAULT,
            0.0,
            0.0,
        )

        val result = trash.roll(Scripted(listOf(0.0)), Bonuses(sizePercent = 200.0, sizeFixed = 50.0))

        assertEquals(0.0, result!!.size)
        assertEquals(Trophy.NONE, result.trophy)
    }

    @Test
    fun `큰 가우시안이 트로피를 만든다`() {
        // 대어가 아니어도 운이 좋으면 트로피가 된다. 대어는 그 확률을 보장으로 바꿀 뿐이다.
        // gaussian 3.0 -> 35 + 3 × 11.67 = 70 -> 일반 트로피(50.75 이상)
        val result = engine().roll(Scripted(listOf(0.0), gaussian = 3.0))

        assertEquals(70.0, result!!.size)
        assertEquals(Trophy.NORMAL, result.trophy)
    }

    // --- 환경 보정 -----------------------------------------------------------------

    @Test
    fun `등급 보정이 추첨에 반영된다`() {
        // f 50 · e 50 이 기본. e 를 3배로 올리면 f 50 / e 150, 총 200.
        // roll 0.3 -> 60 -> f 구간(0~50) 을 넘어 e 다.
        val result = engine().roll(
            Scripted(listOf(0.3, 0.5, 0.5, 0.5)),
            Bonuses(gradeMultipliers = mapOf("e" to 3.0)),
        )

        assertEquals("e", result!!.grade.id)
    }
}

/**
 * 대어 크기 추첨 자체.
 *
 * 트로피 구간에서 **고르게** 뽑는다 — 이미 "특별한 개체"의 영역이라 그 안에서 또 평균에 몰릴
 * 이유가 없고, 고르게 뽑으면 레어가 나올 확률이 구간 길이의 비로 자연스럽게 정해진다.
 */
class TrophySizeRollTest {

    private val cod = SizeRange(min = 10.0, max = 80.0, avg = 35.0)
    private val floor = 50.75 // 평균 35 × 1.45

    @Test
    fun `0 이면 하한이고 1 이면 최대다`() {
        assertEquals(50.8, cod.rollAtLeast(floor, 0.0), "소수점 한 자리로 반올림된다")
        assertEquals(80.0, cod.rollAtLeast(floor, 1.0))
    }

    @Test
    fun `구간 안에서 고르게 나온다`() {
        // 0.5 면 하한과 최대의 한가운데.
        assertEquals(65.4, cod.rollAtLeast(floor, 0.5))
    }

    @Test
    fun `하한이 최소보다 낮으면 최소로 올라간다`() {
        // 평균이 아주 작은 물고기에서 하한이 min 밑으로 내려갈 수 있다.
        assertEquals(10.0, cod.rollAtLeast(0.0, 0.0))
    }

    @Test
    fun `하한이 최대를 넘으면 0 이다`() {
        // 트로피가 불가능한 물고기. 부르는 쪽이 대어 판정에서 이미 걸러야 하지만,
        // 여기서도 안전하게 막는다.
        assertEquals(0.0, cod.rollAtLeast(200.0, 0.5))
    }

    @Test
    fun `크기가 없으면 0 이다`() {
        assertEquals(0.0, SizeRange.NONE.rollAtLeast(1.0, 0.5))
    }

    @Test
    fun `레어가 나올 확률이 구간 길이의 비다`() {
        // 하한 50.75 ~ 최대 80 구간에서 레어 하한은 72 (최대 × 0.9).
        // 레어 비율 = (80 - 72) / (80 - 50.75) ≈ 27.4%
        val rule = TrophyRule.DEFAULT
        val rare = (0 until 1000).count { i ->
            rule.judge(cod, cod.rollAtLeast(floor, i / 1000.0)) == Trophy.RARE
        }

        assertTrue(rare in 260..290, "레어 비율이 ${rare / 10.0}% 로 예상(27%)과 다르다")
    }
}
