package com.inmc.fishing.minigame

/** 미니게임에서 받는 입력. */
enum class Click {
    LEFT, RIGHT;

    /** 화면에 찍는 글자. */
    val letter: String get() = if (this == LEFT) "L" else "R"
}

/** 입력 하나를 넣은 결과. */
enum class Progress {
    /** 맞았고 아직 남았다. */
    HIT,

    /** 맞았고 이걸로 끝이다. */
    DONE,

    /** 틀렸다. */
    MISS,

    /** 이미 끝난 게임이다. 아무 일도 일어나지 않았다. */
    OVER,
}

/**
 * 입질 뒤의 L/R 클릭 게임.
 *
 * **순수 상태 기계다.** Bukkit 도, 스케줄러도, 플레이어도 모른다 — 그래서 "다섯 번째에서
 * 틀리면 어떻게 되는가", "제한 시간과 마지막 입력이 같은 순간이면 어느 쪽이 이기는가" 같은
 * 것을 서버 없이 전부 확인할 수 있다. 2세대는 이 판단이 스케줄러 콜백 안에 섞여 있었다.
 *
 * 시간은 밖에서 받는다([startedAt] · [now]). 틱에 의존하면 테스트가 실시간을 기다려야 한다.
 */
class ClickGame(
    /** 눌러야 하는 순서. 길이가 곧 등급의 `input-count` 다. */
    val sequence: List<Click>,
    val timeLimitMillis: Long,
    private val startedAt: Long,
) {

    /** 지금까지 맞힌 개수. */
    var hits: Int = 0
        private set

    var finished: Boolean = false
        private set

    val total: Int get() = sequence.size

    /** 다음에 눌러야 하는 것. 끝났으면 null. */
    fun expected(): Click? = sequence.getOrNull(hits)

    /**
     * 남은 시간 비율 0.0~1.0. 진행 바를 그리는 데 쓴다.
     *
     * 제한 시간이 0 이하면 1.0 을 돌려준다 — 설정 실수로 진행 바가 0 으로 굳는 것보다 낫다.
     */
    fun timeLeftRatio(now: Long): Double {
        if (timeLimitMillis <= 0L) return 1.0
        val left = (startedAt + timeLimitMillis - now).coerceAtLeast(0L)
        return (left.toDouble() / timeLimitMillis).coerceIn(0.0, 1.0)
    }

    fun isTimedOut(now: Long): Boolean = timeLimitMillis > 0L && now >= startedAt + timeLimitMillis

    /**
     * 입력 하나를 넣는다.
     *
     * **시간 초과를 먼저 본다.** 마지막 입력과 제한 시간이 같은 순간에 걸리면 실패다 —
     * 관대하게 만들면 "시간이 지났는데 성공했다"가 되고, 그건 진행 바를 보고 있던 사람에게
     * 설명할 수 없는 동작이다.
     */
    fun press(click: Click, now: Long): Progress {
        if (finished) return Progress.OVER
        if (isTimedOut(now)) {
            finished = true
            return Progress.MISS
        }

        if (click != expected()) {
            finished = true
            return Progress.MISS
        }

        hits++
        if (hits >= total) {
            finished = true
            return Progress.DONE
        }
        return Progress.HIT
    }

    /** 시간이 다 됐으면 실패로 닫는다. 틱커가 부른다. */
    fun expireIfDue(now: Long): Boolean {
        if (finished || !isTimedOut(now)) return false
        finished = true
        return true
    }

    /** 지금까지의 진행 — 타이틀에 찍는다(2세대 `TimeBarMiniGame`): 맞힌 것 회색, 지금 누를 것 굵은 금색, 남은 것 흰색. */
    fun render(): String =
        sequence.mapIndexed { index, click ->
            when {
                index < hits -> "<dark_gray>${click.letter}</dark_gray>"
                index == hits -> "<gold><bold>${click.letter}</bold></gold>"
                else -> "<white>${click.letter}</white>"
            }
        }.joinToString(" ")

    companion object {

        /**
         * 순서를 만든다.
         *
         * @param rolls 0.0~1.0 값을 [count] 번 돌려주는 것. 0.5 미만이면 좌클릭이다.
         */
        fun generate(count: Int, timeLimitMillis: Long, startedAt: Long, rolls: () -> Double): ClickGame {
            val sequence = List(count.coerceAtLeast(1)) { if (rolls() < 0.5) Click.LEFT else Click.RIGHT }
            return ClickGame(sequence, timeLimitMillis, startedAt)
        }
    }
}
