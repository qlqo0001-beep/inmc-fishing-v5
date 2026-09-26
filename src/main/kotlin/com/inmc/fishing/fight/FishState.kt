package com.inmc.fishing.fight

/**
 * 힘겨루기 중 물고기가 오가는 상태.
 *
 * 상태는 **"지금 어느 클릭을 써야 하는가"** 라는 축으로 나뉜다. 이름이 분위기를 말하는 게
 * 아니라, 화면을 본 사람이 **무엇을 눌러야 하는지** 알 수 있어야 게임이 성립한다.
 */
enum class FishState(
    val displayName: String,
    /** 이 상태에서 무엇을 하는 게 이로운가. 화면 지시에 쓴다. */
    val advice: Advice,
) {

    /** 힘을 빼고 쉰다. 감아 들일 최적 타이밍. */
    REST("휴식", Advice.REEL),

    /** 천천히 움직인다. 감으면 많이 딸려온다. */
    SLOW_MOVE("천천히 이동", Advice.REEL),

    /** 보통 속도. 무난하게 감는다. */
    NORMAL_MOVE("이동 중", Advice.REEL),

    /** 방향을 바꾼다. 장력이 더 오르니 좌우를 섞는다. */
    TURN("방향 전환", Advice.BALANCE),

    /** 배 주위를 돈다. 변화가 완만한 소강 구간. */
    CIRCLE("원형 유영", Advice.BALANCE),

    /** 강하게 도망간다. 감으면 장력이 치솟는다. */
    CHARGE("강하게 돌진!", Advice.RELEASE),

    /** 아래로 파고든다. 거리는 안 변하는데 당기면 장력만 오른다. */
    DIVE("잠수!", Advice.RELEASE),

    /** 마지막 저항. 가장 위험한 구간. */
    FINAL_STRUGGLE("마지막 발악!!", Advice.RELEASE),

    /** 발악 뒤에 반드시 오는 보상 구간. 몰아칠 때다. */
    EXHAUSTED("탈진!", Advice.REEL),

    /** 수면 위로 튀어오른다. 무엇을 해도 효과가 약하다. */
    JUMP("점프!", Advice.WAIT),

    /** 줄이 엉켰다. 풀기 전에는 감아도 소용없다. */
    LINE_TANGLE("줄 엉킴!", Advice.RELEASE),

    /** 체력이 0. 더는 저항하지 않는다. 거리만 0 으로 만들면 끝. */
    STUNNED("기절!", Advice.REEL);

    /** 이 상태에서 행동력이 모두 회복되는지. 쉬거나 줄이 엉킨 동안은 손이 비는 셈이다. */
    val recoversActionPower: Boolean
        get() = this == REST || this == LINE_TANGLE

    /** 물고기가 더 이상 스스로 상태를 바꾸지 않는 끝 상태. */
    val isTerminal: Boolean get() = this == STUNNED

    /**
     * 지금 뭘 눌러야 하는가. 화면에 그대로 찍힌다.
     */
    enum class Advice(val hint: String) {
        REEL("감아라"),
        RELEASE("풀어라"),
        BALANCE("번갈아"),
        WAIT("기다려"),
    }
}
