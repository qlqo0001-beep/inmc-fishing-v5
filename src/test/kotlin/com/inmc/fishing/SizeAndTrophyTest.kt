package com.inmc.fishing

import com.inmc.fishing.fish.SizeRange
import com.inmc.fishing.fish.Trophy
import com.inmc.fishing.fish.TrophyRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 크기 추첨과 트로피 판정을 못박는다.
 *
 * 트로피는 물고기당 한 번뿐이고 서버 전체에 공지가 나간다. 판정이 한쪽으로 치우치면
 * "트로피가 안 나온다" 또는 "트로피가 흔하다"가 되는데, 둘 다 확률이라 뒤늦게 발견된다.
 */
class SizeRangeTest {

    /** 배포 기본 대구: 10~80cm, 평균 35. 폭 70 이므로 표준편차는 70/6 ≈ 11.67. */
    private val cod = SizeRange(min = 10.0, max = 80.0, avg = 35.0)

    @Test
    fun `가우시안 0 이면 평균이 나온다`() {
        assertEquals(35.0, cod.roll(0.0))
    }

    @Test
    fun `표준편차 한 칸이 폭의 6분의 1이다`() {
        // 70 / 6 = 11.666… → 35 + 11.67 = 46.7
        assertEquals(46.7, cod.roll(1.0))
        assertEquals(23.3, cod.roll(-1.0))
    }

    @Test
    fun `범위를 벗어나면 잘린다`() {
        // 정규분포는 꼬리가 무한하다. 자르지 않으면 음수 크기나 말도 안 되는 대어가 나온다.
        assertEquals(80.0, cod.roll(10.0))
        assertEquals(10.0, cod.roll(-10.0))
    }

    @Test
    fun `소수점 한 자리로 반올림한다`() {
        val rolled = cod.roll(0.37)

        assertEquals(rolled, (rolled * 10).toInt() / 10.0, 1e-9)
    }

    @Test
    fun `크기가 없는 것은 0 이다`() {
        // 장화·석탄 같은 쓰레기. 여기서 숫자가 나오면 트로피 판정에 걸린다.
        assertEquals(0.0, SizeRange.NONE.roll(2.0))
        assertTrue(!SizeRange.NONE.hasSize)
    }

    @Test
    fun `min 과 max 가 같으면 크기가 없는 것으로 본다`() {
        // 설정 실수다. 이걸 크기로 인정하면 표준편차가 0 이 되어 항상 같은 값이 나오고,
        // 그 값이 곧 max 라 매번 레어 트로피가 된다.
        assertTrue(!SizeRange(5.0, 5.0, 5.0).hasSize)
        assertEquals(0.0, SizeRange(5.0, 5.0, 5.0).roll(0.0))
    }
}

class TrophyRuleTest {

    private val cod = SizeRange(min = 10.0, max = 80.0, avg = 35.0)
    private val rule = TrophyRule.DEFAULT // 일반 평균×1.45 = 50.75 / 레어 최대×0.9 = 72.0

    @Test
    fun `평범한 크기는 트로피가 아니다`() {
        assertEquals(Trophy.NONE, rule.judge(cod, 35.0))
        assertEquals(Trophy.NONE, rule.judge(cod, 50.7))
    }

    @Test
    fun `평균의 배수를 넘으면 일반 트로피다`() {
        assertEquals(Trophy.NORMAL, rule.judge(cod, 50.75))
        assertEquals(Trophy.NORMAL, rule.judge(cod, 71.9))
    }

    @Test
    fun `최대의 배수를 넘으면 레어 트로피다`() {
        assertEquals(Trophy.RARE, rule.judge(cod, 72.0))
        assertEquals(Trophy.RARE, rule.judge(cod, 80.0))
    }

    @Test
    fun `레어를 먼저 판정한다`() {
        // 레어 조건(72)은 일반 조건(50.75)도 만족한다. 순서가 뒤집히면 레어가 영영 안 나온다.
        assertEquals(Trophy.RARE, rule.judge(cod, 75.0))
    }

    @Test
    fun `크기가 없으면 트로피가 아니다`() {
        assertEquals(Trophy.NONE, rule.judge(SizeRange.NONE, 100.0))
        assertEquals(Trophy.NONE, rule.judge(cod, 0.0))
    }

    @Test
    fun `두 기준이 서로 다른 값을 본다`() {
        // 폭이 좁은 물고기: 최대 20, 평균 18. 레어 기준 18.0, 일반 기준 26.1 -> 일반이 더 높다.
        // 이 경우 레어가 먼저 걸리는 것이 맞다 - 판정 순서가 그래서 중요하다.
        val narrow = SizeRange(min = 15.0, max = 20.0, avg = 18.0)

        assertEquals(Trophy.RARE, rule.judge(narrow, 19.0))
    }

    @Test
    fun `설정으로 기준을 바꿀 수 있다`() {
        val strict = TrophyRule(normalMultiplier = 2.0, rareMultiplier = 0.99)

        // 기본 기준(평균×1.45 = 50.75)으로는 69cm 가 이미 트로피다.
        assertEquals(Trophy.NORMAL, rule.judge(cod, 69.0))
        // 엄격한 기준(평균×2 = 70)으로는 아직 아니다.
        assertEquals(Trophy.NONE, strict.judge(cod, 69.0))
        assertEquals(Trophy.NORMAL, strict.judge(cod, 70.0))
        assertEquals(Trophy.RARE, strict.judge(cod, 79.2), "최대×0.99")
    }
}
