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
