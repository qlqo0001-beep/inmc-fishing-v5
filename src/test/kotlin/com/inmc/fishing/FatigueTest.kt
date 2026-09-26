package com.inmc.fishing

import com.inmc.fishing.fatigue.FatigueConfig
import com.inmc.fishing.fatigue.PlayerFatigue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 피로도와 자동 낚시 잠금을 못박는다.
 *
 * **잠금이 이력 현상(hysteresis)** 이라는 것이 핵심이다. 잠기는 값과 풀리는 값이 같으면 경계에서
 * 껐다 켜졌다를 반복하고, 자동 낚시가 한 마리 잡고 멈추기를 영원히 되풀이한다.
 */
class FatigueTest {

    /** 배포 기본값. F 10 · S 1600 소모, 300초마다 100 회복. */
    private val config = FatigueConfig(
        default = 2000,
        max = 20000,
        recoveryAmount = 100,
        recoveryIntervalSeconds = 300,
        lockThreshold = 0,
        unlockThreshold = 100,
        consume = mapOf("f" to 10, "s" to 1600),
    )

    private fun fatigue(start: Int = 2000) = PlayerFatigue(config, start)

    /** 한 날. 회복 주기가 충분히 많이 돌았다는 뜻이다. */
    private val DAY_MILLIS = 86_400_000L

    // --- 소모 -------------------------------------------------------------------

    @Test
    fun `등급마다 소모량이 다르다`() {
        val f = fatigue()

        assertEquals(10, f.consume("f"))
        assertEquals(1600, f.consume("s"))
        assertEquals(2000 - 10 - 1600, f.value)
    }

    @Test
    fun `설정에 없는 등급은 소모하지 않는다`() {
        val f = fatigue()

        assertEquals(0, f.consume("새등급"))
        assertEquals(2000, f.value)
    }

    @Test
    fun `모자라면 남은 만큼만 쓴다`() {
        // 음수가 되면 회복 계산이 이상해지고 게이지도 깨진다.
        val f = fatigue(start = 500)

        assertEquals(500, f.consume("s"), "1600 이 필요하지만 500 밖에 없다")
        assertEquals(0, f.value)
    }

    @Test
    fun `0 이 되면 자동 낚시가 잠긴다`() {
        val f = fatigue(start = 10)

        assertFalse(f.locked)
        f.consume("f")
        assertTrue(f.locked)
    }

    // --- 이력 현상 -----------------------------------------------------------------

    @Test
    fun `잠금 임계값을 넘겨도 해제 임계값 전에는 안 풀린다`() {
        // 이게 이력 현상이다. 없으면 1 만 회복해도 풀렸다가 한 마리 잡고 다시 잠긴다.
        val f = fatigue(start = 0)
        f.consume("f")
        assertTrue(f.locked)

        f.restore(50)
        assertTrue(f.locked, "50 은 해제 기준 100 에 못 미친다")

        f.restore(50)
        assertFalse(f.locked, "100 이 되면 풀린다")
    }

    @Test
    fun `회복으로도 풀린다`() {
        val f = fatigue(start = 0)
        f.consume("f")

        f.recover(0L)
        f.recover(300_000L)

        assertEquals(100, f.value)
        assertFalse(f.locked)
    }

    // --- 회복 -------------------------------------------------------------------

    @Test
    fun `첫 호출은 기준 시각만 잡는다`() {
        // 기준이 없으면 1970년부터 지난 시간만큼 회복해 즉시 최대가 된다.
        val f = fatigue(start = 100)

        assertEquals(0, f.recover(1_700_000_000_000L))
        assertEquals(100, f.value)
    }

    @Test
    fun `주기가 지나야 회복한다`() {
        val f = fatigue(start = 100)
        f.recover(0L)

        assertEquals(0, f.recover(299_999L))
        assertEquals(100, f.recover(300_000L))
    }

    @Test
    fun `오래 비워두면 한꺼번에 회복한다`() {
        // 접속을 끊었던 동안도 쉰 것이다. 피로도는 접속 여부와 무관해야 한다.
        val f = fatigue(start = 0)
        f.recover(0L)

        // 10주기 = 3000초
        assertEquals(1000, f.recover(3_000_000L))
        assertEquals(1000, f.value)
    }

    @Test
    fun `남은 시간이 다음 회복으로 이월된다`() {
        // 매번 now 로 기준을 맞추면 주기가 조금씩 밀려 회복이 느려진다.
        val f = fatigue(start = 0)
        f.recover(0L)

        f.recover(450_000L) // 1.5 주기 -> 1회 회복, 150초가 남는다
        assertEquals(100, f.value)

        f.recover(600_000L) // 처음부터 2주기째. 이월된 150초 덕에 여기서 2회차가 된다
        assertEquals(200, f.value)
    }

    @Test
    fun `자연 회복은 절대 상한이 아니라 기본 한계까지만 올린다`() {
        // `default` 와 `max` 는 역할이 다르다. 쉬어서 차오르는 것은 `default` 까지고, 그 위는
        // 회복 물약과 관리자 명령만 닿는다. 둘을 구분하지 않으면 밤새 접속만 해두어도 상한까지
        // 차서 자동 낚시에 사실상 제한이 없어진다.
        val f = fatigue(start = 0)
        f.recover(0L)

        f.recover(DAY_MILLIS)

        assertEquals(config.default, f.value)
        assertTrue(config.default < config.max, "두 값이 같으면 이 검사에 의미가 없다")
    }

    @Test
    fun `물약으로 한계를 넘겨둔 값을 자연 회복이 깎지 않는다`() {
        // 한계로 잘라버리면 물약을 먹은 사람이 5분 뒤에 그 효과를 잃는다.
        val f = fatigue(start = 5000)
        f.recover(0L)

        assertEquals(0, f.recover(DAY_MILLIS))
        assertEquals(5000, f.value)
    }

    @Test
    fun `낚싯대가 자연 회복 한계를 올려준다`() {
        val f = fatigue(start = 0)
        f.recover(0L)

        f.recover(DAY_MILLIS, maxBonus = 1000)

        assertEquals(config.default + 1000, f.value)
    }

    @Test
    fun `낚싯대 보너스도 절대 상한은 넘지 못한다`() {
        val f = fatigue(start = 0)
        f.recover(0L)

        f.recover(DAY_MILLIS, maxBonus = 999_999)

        assertEquals(config.max, f.value)
    }

    @Test
    fun `낚싯대가 1회 회복량을 더한다`() {
        val f = fatigue(start = 0)
        f.recover(0L)

        // 1주기(300초)에 기본 100 + 보너스 50.
        assertEquals(150, f.recover(300_000L, amountBonus = 50))
    }

    @Test
    fun `수동 회복은 절대 상한까지 닿는다`() {
        val f = fatigue(start = 0)

        f.restore(999_999)

        assertEquals(config.max, f.value)
    }

    @Test
    fun `회복량이 0 이면 아무 일도 없다`() {
        val noRecovery = FatigueConfig(recoveryAmount = 0)
        val f = PlayerFatigue(noRecovery, 100)

        f.recover(0L)
        assertEquals(0, f.recover(999_999_999L))
    }

    // --- 직접 설정 -----------------------------------------------------------------

    @Test
    fun `관리자가 값을 세우면 잠금도 따라 바뀐다`() {
        val f = fatigue(start = 0)
        f.consume("f")
        assertTrue(f.locked)

        f.set(5000)
        assertFalse(f.locked)

        f.set(0)
        assertTrue(f.locked)
    }

    @Test
    fun `값은 범위 안으로 잘린다`() {
        val f = fatigue()

        f.set(-100)
        assertEquals(0, f.value)

        f.set(config.max * 10)
        assertEquals(config.max, f.value)
    }

    @Test
    fun `게이지 비율이 0 과 1 사이다`() {
        val f = fatigue(start = config.max / 2)

        assertEquals(0.5, f.ratio(), 1e-9)
        f.set(0)
        assertEquals(0.0, f.ratio())
    }
}
