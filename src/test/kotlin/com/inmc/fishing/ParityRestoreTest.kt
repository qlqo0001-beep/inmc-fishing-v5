package com.inmc.fishing

import com.inmc.fishing.catching.RandomRolls
import com.inmc.fishing.fight.Fight
import com.inmc.fishing.fight.FightKeys
import com.inmc.fishing.fight.FightOutcome
import com.inmc.fishing.fight.FightSettings
import com.inmc.fishing.fish.RollEngine
import com.inmc.fishing.fish.Simulation
import com.inmc.fishing.fish.TrophyRule
import com.inmc.fishing.grade.GradeTable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 2세대에 있었는데 5세대가 처음에 빠뜨렸던 것들 중 서버 없이 확인할 수 있는 것.
 */
class ParityRestoreTest {

    // --- 실패 유예 (2세대 fight.yml fail-grace) -------------------------------------------

    /** 줄 강도가 거의 0 이라 한 번 감으면 한계에 닿는다. */
    private fun snapping(graceMillis: Long) = Fight(
        FightSettings(
            stats = FightSettings.Stats(defaultLineStrength = 0.0001),
            general = FightSettings.General(lineSnappedGraceMillis = graceMillis),
        ),
        "d", rare = false, startedAt = 0L, random = java.util.Random(1),
    )

    private fun fresh() = Fight(FightSettings(), "d", rare = false, startedAt = 0L, random = java.util.Random(1))

    private fun Fight.reelAt(now: Long): FightOutcome {
        registerReel(now)
        tick(now)
        return outcome
    }

    @Test
    fun `한계에 닿아도 유예 시간 안에는 끝나지 않는다`() {
        val fight = snapping(300L)
        assertEquals(FightOutcome.RUNNING, fight.reelAt(1_000L))
        assertTrue(fight.inGrace(FightOutcome.LINE_SNAPPED), "유예 중이라고 알려야 HUD 가 3 을 띄운다")
        assertEquals(FightOutcome.RUNNING, fight.reelAt(1_250L))
    }

    @Test
    fun `유예 시간을 넘기면 그 이유로 끝난다`() {
        val fight = snapping(300L)
        fight.reelAt(1_000L)
        assertEquals(FightOutcome.LINE_SNAPPED, fight.reelAt(1_300L))
    }

    @Test
    fun `유예가 0 이면 닿는 순간 끝난다`() {
        assertEquals(FightOutcome.LINE_SNAPPED, snapping(0L).reelAt(1_000L))
    }

    @Test
    fun `한계를 벗어나면 유예가 풀린다`() {
        val fight = fresh()
        fight.tick(1_000L)
        assertTrue(FightOutcome.entries.none { fight.inGrace(it) })
    }

    // --- 입력 (2세대 FightSession) ---------------------------------------------------------

    @Test
    fun `클릭 하나는 유예 시간 동안 누르고 있는 것으로 친다`() {
        val fight = fresh()
        assertTrue(!fight.isReeling(1_000L), "누른 적이 없으면 감는 중이 아니다")
        fight.registerReel(1_000L)
        assertTrue(fight.isReeling(1_300L))
        assertTrue(!fight.isReeling(1_301L), "300ms 가 지나면 손을 뗀 것")
    }

    @Test
    fun `감기와 풀기는 서로를 끄고 콤보는 창 안에서만 오른다`() {
        val fight = fresh()
        fight.registerRelease(1_000L)
        fight.registerRelease(1_300L)
        assertEquals(2, fight.releaseCombo)
        fight.registerRelease(1_601L)
        assertEquals(1, fight.releaseCombo, "창(300ms)을 넘기면 다시 1")
        fight.registerReel(1_650L)
        assertTrue(fight.isReeling(1_650L) && !fight.isReleasing(1_650L))
        assertEquals(0, fight.releaseCombo)
        repeat(20) { fight.registerReel(1_700L + it) }
        assertEquals(10, fight.reelCombo, "상한 10")
    }

    // --- W·S 키 (5세대에서 더함) ---------------------------------------------------------------

    @Test
    fun `W 를 누르고 있으면 유예를 넘겨도 계속 감고 콤보는 오르지 않는다`() {
        val fight = fresh()
        fight.registerReel(1_000L) // 누른 순간
        for (t in 1_050L..3_000L step 50L) fight.holdReel(t) // 누르고 있는 동안 틱마다
        assertTrue(fight.isReeling(3_300L))
        assertEquals(1, fight.reelCombo, "누르고 있는 것은 연타가 아니다")
        assertTrue(!fight.isReeling(3_301L), "떼면 클릭과 같은 유예 뒤에 멈춘다")
    }

    @Test
    fun `누르고 있어도 누른 순간이 없으면 감지 않고 가장 최근 입력이 이긴다`() {
        val fight = fresh()
        fight.holdReel(1_000L)
        assertTrue(!fight.isReeling(1_000L), "판 전부터 누르고 있던 W 는 감기가 아니다")

        fight.registerReel(1_000L)
        fight.registerRelease(1_100L) // W 를 누른 채 우클릭
        fight.holdReel(1_150L)
        assertTrue(fight.isReleasing(1_150L) && !fight.isReeling(1_150L), "우클릭이 방향을 바꾸면 누르고 있는 W 가 되돌리지 않는다")

        fight.holdRelease(1_400L)
        assertTrue(fight.isReleasing(1_700L), "S 를 누르고 있으면 풀기가 이어진다")
    }

    @Test
    fun `W·S 를 누른 순간은 한쪽만 눌린 상태로 새로 들어갈 때다`() {
        fun press(was: Pair<Boolean, Boolean>, now: Pair<Boolean, Boolean>) = FightKeys.pressed(was.first, was.second, now.first, now.second)
        val none = false to false
        val w = true to false
        val s = false to true
        val both = true to true

        assertEquals(FightKeys.Press.REEL, press(none, w), "W 누름 = 좌클릭 하나")
        assertEquals(FightKeys.Press.RELEASE, press(none, s), "S 누름 = 우클릭 하나")
        assertNull(press(w, w), "쥐고 있는 것은 누른 순간이 아니다")
        assertNull(press(w, both), "둘 다 누르면 아무 쪽도 아니다")
        assertNull(press(w, none), "떼는 것은 누른 순간이 아니다")
        // 손가락을 굴려 방향을 바꾼다 — W 를 쥔 채 S 를 눌렀다 떼면 다시 감긴다(전에는 W 를 떼고 다시 눌러야 했다)
        assertEquals(FightKeys.Press.REEL, press(both, w))
        assertEquals(FightKeys.Press.RELEASE, press(both, s))
        assertEquals(FightKeys.Press.RELEASE, press(w, s))
    }

    @Test
    fun `W 를 꾹 누르면 연타처럼 콤보가 쌓이며 감긴다 — 판 전부터 쥔 키는 되풀이하지 않고 S 도 같다`() {
        val input = FightSettings().input
        // 0ms 에 W 를 누르고 1.5초 쥐고 있다(20Hz 틱). 사용자 요청 2026-10-01 — 전에는 콤보가 1 에서 멈췄다.
        val fight = fresh()
        fight.registerReel(0L)
        var pressedAt: Long? = 0L
        for (tick in 1..30) {
            val now = tick * 50L
            pressedAt = FightKeys.hold(fight, reel = true, pressedAt, now, input.holdRepeatMillis)
            assertTrue(fight.isReeling(now), "쥐고 있는 동안은 감는 중이어야 한다($now ms)")
        }
        assertEquals(input.maxReelCombo, fight.reelCombo, "1.5초 쥐면 콤보가 끝까지 쌓인다")

        // 판이 열리기 전부터 쥐고 있던 키(누른 시각 없음)는 되풀이하지 않는다 — 한 번 떼고 눌러야 감긴다.
        val early = fresh()
        var never: Long? = null
        for (tick in 1..30) never = FightKeys.hold(early, reel = true, never, tick * 50L, input.holdRepeatMillis)
        assertEquals(0, early.reelCombo)
        assertTrue(!early.isReeling(1_500L))

        // S 도 같다.
        val loose = fresh()
        loose.registerRelease(0L)
        var at: Long? = 0L
        for (tick in 1..30) at = FightKeys.hold(loose, reel = false, at, tick * 50L, input.holdRepeatMillis)
        assertEquals(input.maxReleaseCombo, loose.releaseCombo)
        assertTrue(input.holdRepeatMillis < input.reelComboWindowMillis, "되풀이 간격이 콤보 간격보다 길면 쌓이지 않는다")
    }

    // --- 시뮬레이션 (2세대 /fishing simulate) -------------------------------------------

    @Test
    fun `물고기가 없으면 아무것도 세지 않는다`() {
        val engine = RollEngine(GradeTable(emptyList()), emptyMap(), TrophyRule.DEFAULT, 0.0, 0.0)
        val result = Simulation.run(engine, 100, RandomRolls())
        assertEquals(0L, result.total)
        assertTrue(result.byGrade.isEmpty())
    }

    @Test
    fun `백분율은 2세대처럼 소수 둘째 자리이고 0 으로 나누지 않는다`() {
        assertEquals(String.format("%.2f", 25.0), Simulation.percent(1, 4))
        assertEquals(String.format("%.2f", 0.0), Simulation.percent(0, 0))
    }
}
