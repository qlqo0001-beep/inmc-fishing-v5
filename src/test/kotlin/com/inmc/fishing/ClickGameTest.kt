package com.inmc.fishing

import com.inmc.fishing.minigame.Click
import com.inmc.fishing.minigame.ClickGame
import com.inmc.fishing.minigame.Progress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 미니게임 상태 기계를 못박는다.
 *
 * 2세대는 이 판단이 스케줄러 콜백 안에 섞여 있어 서버 없이는 한 줄도 확인할 수 없었다.
 * 경계(마지막 입력과 제한 시간이 겹치는 순간, 끝난 게임에 또 입력)가 특히 그랬다.
 */
class ClickGameTest {

    private val start = 1_000_000L
    private val limit = 5_000L

    private fun game(vararg clicks: Click) = ClickGame(clicks.toList(), limit, start)

    @Test
    fun `순서대로 다 누르면 성공이다`() {
        val g = game(Click.LEFT, Click.RIGHT, Click.LEFT)

        assertEquals(Progress.HIT, g.press(Click.LEFT, start))
        assertEquals(Progress.HIT, g.press(Click.RIGHT, start + 100))
        assertEquals(Progress.DONE, g.press(Click.LEFT, start + 200))
        assertTrue(g.finished)
        assertEquals(3, g.hits)
    }

    @Test
    fun `틀리면 그 자리에서 실패다`() {
        val g = game(Click.LEFT, Click.RIGHT)

        assertEquals(Progress.MISS, g.press(Click.RIGHT, start))
        assertTrue(g.finished)
        assertEquals(0, g.hits, "틀린 입력은 진행으로 세지 않는다")
    }

    @Test
    fun `중간에 틀려도 이미 맞힌 것은 남는다`() {
        val g = game(Click.LEFT, Click.RIGHT, Click.LEFT)

        g.press(Click.LEFT, start)
        assertEquals(Progress.MISS, g.press(Click.LEFT, start + 100))
        assertEquals(1, g.hits)
    }

    @Test
    fun `끝난 게임에 또 누르면 아무 일도 없다`() {
        // 연타하던 손가락이 성공 직후에 한 번 더 들어온다. 그게 다음 입질을 잡아먹으면 안 된다.
        val g = game(Click.LEFT)
        g.press(Click.LEFT, start)

        assertEquals(Progress.OVER, g.press(Click.LEFT, start + 10))
        assertEquals(Progress.OVER, g.press(Click.RIGHT, start + 20))
        assertEquals(1, g.hits)
    }

    // --- 시간 ---------------------------------------------------------------------

    @Test
    fun `제한 시간과 같은 순간이면 실패다`() {
        // 관대하게 만들면 "시간이 지났는데 성공했다"가 된다. 진행 바를 보던 사람에게
        // 설명할 수 없는 동작이라 엄격한 쪽을 골랐다.
        val g = game(Click.LEFT)

        assertEquals(Progress.MISS, g.press(Click.LEFT, start + limit))
        assertTrue(g.finished)
    }

    @Test
    fun `제한 시간 직전은 성공이다`() {
        val g = game(Click.LEFT)

        assertEquals(Progress.DONE, g.press(Click.LEFT, start + limit - 1))
    }

    @Test
    fun `시간이 지나면 틱커가 닫는다`() {
        val g = game(Click.LEFT, Click.RIGHT)

        assertFalse(g.expireIfDue(start + limit - 1))
        assertTrue(g.expireIfDue(start + limit))
        assertTrue(g.finished)
        assertFalse(g.expireIfDue(start + limit + 1), "이미 닫힌 것을 또 닫지 않는다")
    }

    @Test
    fun `남은 시간 비율이 선형으로 줄어든다`() {
        val g = game(Click.LEFT)

        assertEquals(1.0, g.timeLeftRatio(start))
        assertEquals(0.5, g.timeLeftRatio(start + limit / 2))
        assertEquals(0.0, g.timeLeftRatio(start + limit))
        assertEquals(0.0, g.timeLeftRatio(start + limit * 10), "음수로 내려가지 않는다")
    }

    @Test
    fun `제한 시간이 0 이면 시간 제한이 없는 것으로 본다`() {
        // 설정 실수다. 여기서 0 을 그대로 쓰면 진행 바가 0 에 굳고 모든 입질이 즉시 실패한다.
        val g = ClickGame(listOf(Click.LEFT), 0L, start)

        assertEquals(1.0, g.timeLeftRatio(start + 999_999))
        assertFalse(g.isTimedOut(start + 999_999))
        assertEquals(Progress.DONE, g.press(Click.LEFT, start + 999_999))
    }

    // --- 표시 ---------------------------------------------------------------------

    @Test
    fun `진행을 글자로 그린다 — 맞힌 것 회색 · 지금 누를 것 굵은 금색 · 남은 것 흰색`() {
        val g = game(Click.LEFT, Click.RIGHT, Click.LEFT)
        g.press(Click.LEFT, start)

        assertEquals(listOf("<dark_gray>L", "<gold><bold>R", "<white>L"), g.pieces())
        assertEquals("<dark_gray>L <gold><bold>R <white>L", g.render())
    }

    @Test
    fun `설정한 글자와 색으로 그린다 — config minigame letters colors`() {
        val g = game(Click.LEFT, Click.RIGHT, Click.LEFT)
        g.press(Click.LEFT, start)
        // 그림 글자(리소스팩 글꼴)도 같은 길이다 — 글자 칸에 <font:…> 태그나 글리프 문자를 적는다.
        val icon = "<font:customfishing:icons>뀁</font>"
        val display = com.inmc.fishing.minigame.ClickDisplay(icon, "우", pressed = "&7", current = "<white>", waiting = "")

        assertEquals(listOf("&7$icon", "<white>우", icon), g.pieces(display))
    }

    @Test
    fun `다음에 눌러야 할 것을 알려준다`() {
        val g = game(Click.LEFT, Click.RIGHT)

        assertEquals(Click.LEFT, g.expected())
        g.press(Click.LEFT, start)
        assertEquals(Click.RIGHT, g.expected())
        g.press(Click.RIGHT, start)
        assertNull(g.expected())
    }

    // --- 생성 ---------------------------------------------------------------------

    @Test
    fun `0_5 미만이면 좌클릭이다`() {
        val rolls = listOf(0.0, 0.49, 0.5, 0.99).iterator()
        val g = ClickGame.generate(4, limit, start) { rolls.next() }

        assertEquals(listOf(Click.LEFT, Click.LEFT, Click.RIGHT, Click.RIGHT), g.sequence)
    }

    @Test
    fun `길이가 0 이어도 최소 하나는 만든다`() {
        // 설정에 input-count 0 이 들어오면 누를 것이 없어 영원히 끝나지 않는다.
        val g = ClickGame.generate(0, limit, start) { 0.0 }

        assertEquals(1, g.total)
    }
}
