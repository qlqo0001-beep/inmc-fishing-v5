package com.inmc.fishing.fight

/**
 * 힘겨루기의 W(감기)·S(풀기) — 키 상태가 바뀐 순간 중 **누른 순간**(클릭 하나와 같다)을 가려낸다.
 *
 * 한쪽만 눌린 상태로 **새로 들어갈 때**가 누른 순간이다. "그 키가 새로 눌렸는가" 로 보면 W 를 쥔 채 S 를 눌렀다 떼는 경우
 * (손가락을 굴려 방향을 바꾸는 경우) W 로 돌아가지 못해 쥐고 있는 W 가 아무것도 안 한다. 둘 다 누르면 아무 쪽도 아니다.
 * 판이 열리기 전부터 쥐고 있던 키는 누른 순간이 아니다 — 부르는 쪽이 시작 때의 키 상태를 이전 값으로 넘긴다.
 */
object FightKeys {

    enum class Press { REEL, RELEASE }

    /**
     * 한쪽 키를 누르고 있는 한 틱(사용자 요청 2026-10-01 — W 를 꾹 누르면 콤보가 쌓이며 감겨야 한다). [repeatMillis] 마다 **한 번 더
     * 누른 것**으로 쳐서 연타처럼 콤보를 올리고, 그 사이는 누르고 있음만 늘린다. 판이 열리기 전부터 쥐고 있던 키([pressedAt] 이 null)는
     * 되풀이하지 않는다 — 한 번 떼고 눌러야 감긴다.
     * @return 다음 되풀이를 잴 시각(되풀이하지 않았으면 [pressedAt] 그대로)
     */
    fun hold(fight: Fight, reel: Boolean, pressedAt: Long?, now: Long, repeatMillis: Long): Long? {
        if (pressedAt != null && repeatMillis > 0 && now - pressedAt >= repeatMillis) {
            if (reel) fight.registerReel(now) else fight.registerRelease(now)
            return now
        }
        if (reel) fight.holdReel(now) else fight.holdRelease(now)
        return pressedAt
    }

    fun pressed(wasForward: Boolean, wasBackward: Boolean, forward: Boolean, backward: Boolean): Press? {
        val wasReel = wasForward && !wasBackward
        val wasRelease = wasBackward && !wasForward
        return when {
            forward && !backward && !wasReel -> Press.REEL
            backward && !forward && !wasRelease -> Press.RELEASE
            else -> null
        }
    }
}
