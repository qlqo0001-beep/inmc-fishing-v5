package com.inmc.fishing

import com.inmc.fishing.grade.Grade
import com.inmc.fishing.grade.GradeTable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 등급 추첨을 못박는다.
 *
 * 이 계산이 모든 낚시의 첫 단계다. 여기가 틀리면 S등급이 쏟아지거나 영영 안 나오는데, 둘 다
 * 확률이라 **한참 뒤에야 이상하다고 느낀다.** `roll` 을 밖에서 받게 만들어 전부 단정으로 잡는다.
 */
class GradeTableTest {

    private fun grade(id: String, weight: Double) =
        Grade(id, id.uppercase(), weight, 3, 5.0, "<white>")

    /** 배포 기본값과 같은 비율: F 45 · E 25 · D 15 · C 8 · B 4 · A 2 · S 1 (합 100). */
    private val table = GradeTable(
        listOf(
            grade("f", 45.0),
            grade("e", 25.0),
            grade("d", 15.0),
            grade("c", 8.0),
            grade("b", 4.0),
            grade("a", 2.0),
            grade("s", 1.0),
        ),
    )

    @Test
    fun `roll 이 구간 경계를 정확히 가른다`() {
        // 누적: f 0~45, e 45~70, d 70~85, c 85~93, b 93~97, a 97~99, s 99~100
        assertEquals("f", table.draw(roll = 0.0)?.id)
        assertEquals("f", table.draw(roll = 0.449)?.id)
        assertEquals("e", table.draw(roll = 0.45)?.id)
        assertEquals("e", table.draw(roll = 0.699)?.id)
        assertEquals("d", table.draw(roll = 0.70)?.id)
        assertEquals("s", table.draw(roll = 0.99)?.id)
        assertEquals("s", table.draw(roll = 0.9999)?.id)
    }

    @Test
    fun `roll 이 1 이어도 범위를 벗어나지 않는다`() {
        // 난수 구현이 1.0 을 돌려주는 경우가 있다. 그때 null 이 나오면 입질이 사라진다.
        assertEquals("s", table.draw(roll = 1.0)?.id)
        assertEquals("f", table.draw(roll = -0.5)?.id, "음수도 안전하게 잘린다")
    }

    @Test
    fun `보너스는 곱연산이다`() {
        // S 확률 2배 -> 가중치 1 이 2 가 되고 총합은 101. S 구간은 99~101 이다.
        val bonus = mapOf("s" to 2.0)

        assertEquals("a", table.draw(bonus, roll = 98.0 / 101.0)?.id)
        assertEquals("s", table.draw(bonus, roll = 99.5 / 101.0)?.id)
    }

    @Test
    fun `보너스 0 이면 그 등급은 안 나온다`() {
        val noF = table.draw(mapOf("f" to 0.0), roll = 0.0)

        assertEquals("e", noF?.id, "F 가 빠지면 첫 구간은 E 다")
    }

    @Test
    fun `음수 보너스는 0 으로 잘린다`() {
        // 설정 실수로 음수가 들어오면 총합이 줄어 엉뚱한 등급이 나온다.
        assertEquals("e", table.draw(mapOf("f" to -3.0), roll = 0.0)?.id)
    }

    @Test
    fun `가중치가 전부 0 이면 첫 등급으로 떨어진다`() {
        // 입질이 사라지는 것보다 낫다.
        val zeroed = GradeTable(listOf(grade("f", 0.0), grade("e", 0.0)))

        assertEquals("f", zeroed.draw(roll = 0.5)?.id)
    }

    @Test
    fun `등급이 하나도 없으면 null 이다`() {
        assertNull(GradeTable(emptyList()).draw(roll = 0.5))
    }

    @Test
    fun `등급 조회는 대소문자를 가리지 않는다`() {
        assertEquals("s", table["S"]?.id)
        assertEquals("s", table["s"]?.id)
        assertNull(table["zzz"])
        assertNull(table[null])
    }

    @Test
    fun `분포가 가중치를 따라간다`() {
        // 경계 단정만으로는 누적 계산이 뒤집혀도 통과할 수 있다. 균등한 roll 을 촘촘히 넣어
        // 실제 비율이 설정과 맞는지 본다.
        val counts = HashMap<String, Int>()
        val samples = 10_000
        for (i in 0 until samples) {
            val id = table.draw(roll = i.toDouble() / samples)?.id ?: continue
            counts[id] = (counts[id] ?: 0) + 1
        }

        assertEquals(4500, counts["f"], "F 는 45% 여야 한다")
        assertEquals(2500, counts["e"])
        assertEquals(100, counts["s"], "S 는 1% 여야 한다")
        assertTrue(counts.values.sum() == samples)
    }
}

/**
 * 낚싯대·미끼의 **덧셈** 보너스.
 *
 * 곱셈과 섞이는 자리라 따로 묶었다. 순서가 `기본 × 배수 + 덧셈` 이어야 하고, 그 반대면
 * 환경 보정이 낚싯대 보너스까지 부풀린다.
 */
class GradeFlatBonusTest {

    private fun grade(id: String, weight: Double) =
        Grade(id, id.uppercase(), weight, 3, 5.0, "<white>")

    /** F 99 · S 1. 최상위가 아주 희귀한 상황. */
    private val table = GradeTable(listOf(grade("f", 99.0), grade("s", 1.0)))

    @Test
    fun `덧셈 보너스가 희귀 등급을 실제로 열어준다`() {
        // S 가중치 1 에 +9 -> 10. 총합 109 이므로 S 구간은 99~109 (약 9%).
        val bonus = mapOf("s" to 9.0)

        assertEquals("f", table.draw(roll = 98.0 / 109.0, flatBonus = bonus)?.id)
        assertEquals("s", table.draw(roll = 104.0 / 109.0, flatBonus = bonus)?.id)
    }

    @Test
    fun `가중치가 작을수록 곱셈은 절대량을 못 늘린다`() {
        // 낚싯대 보너스가 덧셈인 이유 그 자체. S 가중치 1 을 두 배로 만들어도 1 이 늘 뿐이다.
        // 총합 99+2 = 101 이라 S 구간은 99~101 (약 2%).
        val doubled = table.draw(mapOf("s" to 2.0), roll = 98.5 / 101.0)
        assertEquals("f", doubled?.id, "2배로는 98.5 가 아직 F 구간이다")

        // 같은 자리에서 +9 를 더하면 S 가중치가 10, 총합 109 라 S 구간은 99~109 (약 9%) 다.
        assertEquals("f", table.draw(roll = 98.5 / 109.0, flatBonus = mapOf("s" to 9.0))?.id)
        assertEquals("s", table.draw(roll = 103.0 / 109.0, flatBonus = mapOf("s" to 9.0))?.id)
    }

    @Test
    fun `곱셈이 먼저고 덧셈이 나중이다`() {
        // 순서를 실제로 구분하려면 두 계산의 구간이 갈라지는 값이어야 한다.
        //   f 10 (보정 없음) · s 1 (배수 3 · 덧셈 +30)
        //   올바른 순서: s = 1×3 + 30 = 33, 총합 43 -> f 구간 0~10 (약 23%)
        //   뒤바뀐 순서: s = (1+30)×3 = 93, 총합 103 -> f 구간 0~10 (약 10%)
        val split = GradeTable(listOf(grade("f", 10.0), grade("s", 1.0)))

        val result = split.draw(
            bonus = mapOf("s" to 3.0),
            roll = 0.15,
            flatBonus = mapOf("s" to 30.0),
        )

        // 0.15 × 43 = 6.45 -> F 구간 안. 순서가 뒤바뀌었다면 0.15 × 103 = 15.45 로 S 가 나온다.
        assertEquals("f", result?.id, "곱셈을 먼저 해야 한다")
    }

    @Test
    fun `음수 덧셈으로 가중치가 0 밑으로 가지 않는다`() {
        // 설정 실수로 -100 이 들어와도 F 가 음수 가중치가 되면 안 된다.
        val result = table.draw(roll = 0.0, flatBonus = mapOf("f" to -1000.0))

        assertEquals("s", result?.id, "F 가 0 이 되면 S 만 남는다")
    }

    @Test
    fun `덧셈 보너스가 없으면 기본과 같다`() {
        assertEquals(table.draw(roll = 0.5)?.id, table.draw(roll = 0.5, flatBonus = emptyMap())?.id)
    }
}
