package com.inmc.fishing.tournament

import java.util.UUID

/** 대회가 무엇으로 순위를 매기는가. */
enum class TournamentType {
    /** 등급이 높은 물고기를 많이. */
    GRADE,

    /** 잡은 물고기 크기의 합. */
    SIZE,

    /** 마릿수. */
    COUNT;

    companion object {
        fun parse(raw: String?): TournamentType =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: COUNT
    }
}

/** 대회 참가자 한 명의 기록. */
class Standing(val playerId: UUID, var playerName: String) {

    var score: Long = 0L
        private set

    /** 이 사람이 잡은 것 중 가장 큰 것. SIZE 대회의 동점 판정에 쓴다. */
    var bestSize: Double = 0.0
        private set

    /** 크기 합. SIZE 대회의 점수 재료다. */
    var totalSize: Double = 0.0
        private set

    var catchCount: Int = 0
        private set

    /** 마지막으로 점수가 오른 시각. 동점일 때 **먼저 도달한 사람이 이긴다.** */
    var lastScoredAt: Long = 0L
        private set

    /**
     * 스스로 나갔는가. **기록은 지우지 않는다** — 2세대와 같다. 나간 사람은 점수·보상·순위에서
     * 빠지고, 다시 들어오면 하던 기록이 이어진다. 결과 기록(`history/`)에는 남는다.
     */
    var left: Boolean = false
        internal set

    /** `active.yml` 에서 되살린다. 재시작 뒤에도 대회가 이어져야 한다. */
    internal fun restore(score: Long, bestSize: Double, totalSize: Double, catchCount: Int, lastScoredAt: Long, left: Boolean) {
        this.score = score
        this.bestSize = bestSize
        this.totalSize = totalSize
        this.catchCount = catchCount
        this.lastScoredAt = lastScoredAt
        this.left = left
    }

    fun addGradeScore(amount: Long, now: Long) {
        score += amount
        lastScoredAt = now
    }

    fun addSize(size: Double, now: Long) {
        totalSize += size
        if (size > bestSize) bestSize = size
        // 소수점을 정수 점수로 옮긴다. 0.1cm 까지 순위에 반영된다.
        score = (totalSize * 10).toLong()
        lastScoredAt = now
    }

    fun addCatch(now: Long) {
        catchCount++
        score = catchCount.toLong()
        lastScoredAt = now
    }
}

/**
 * 대회 한 판의 순위표.
 *
 * **순수 계산이다.** 누가 무엇을 낚았는지만 넣으면 순위가 나온다 — Bukkit 도 시간도 모른다.
 * 2세대는 이 계산이 `TournamentManager` 의 이벤트 핸들러 안에 있어서, 동점 처리나 등급 필터
 * 같은 규칙을 확인하려면 대회를 실제로 열어야 했다.
 */
class Scoreboard(
    val type: TournamentType,
    /** GRADE 대회에서 점수로 칠 등급. null 이나 "all" 이면 전부. */
    private val targetGrade: String? = null,
    /** 등급 id → 기본 점수. 높은 등급일수록 큰 값. */
    private val gradeScores: Map<String, Int> = DEFAULT_GRADE_SCORES,
) {

    private val standings = LinkedHashMap<UUID, Standing>()

    /** 들어온다. 나갔던 사람이면 **하던 기록을 이어서** 다시 참가한다. */
    fun join(playerId: UUID, name: String): Standing {
        val standing = standings.getOrPut(playerId) { Standing(playerId, name) }
        standing.left = false
        standing.playerName = name
        return standing
    }

    /** 나간다. 기록은 남는다. 참가 중이 아니었으면 false. */
    fun leave(playerId: UUID): Boolean {
        val standing = standings[playerId] ?: return false
        if (standing.left) return false
        standing.left = true
        return true
    }

    fun of(playerId: UUID): Standing? = standings[playerId]

    /** 지금 참가 중인가. 나간 사람은 아니다. */
    fun isIn(playerId: UUID): Boolean = standings[playerId]?.left == false

    /** 지금 참가 중인 인원. 정원·최소 인원 판정에 쓴다. 나간 사람은 세지 않는다. */
    val size: Int get() = standings.values.count { !it.left }

    /**
     * 낚은 것을 반영한다. **참가자가 아니면 아무 일도 없다.**
     *
     * @return 점수에 반영됐으면 true. GRADE 대회에서 대상 등급이 아니면 false 다.
     */
    fun record(
        playerId: UUID,
        playerName: String,
        gradeId: String,
        fishWeight: Int,
        size: Double,
        now: Long,
    ): Boolean {
        // 나간 사람은 점수가 오르지 않는다.
        val standing = standings[playerId]?.takeIf { !it.left } ?: return false
        // 지금 낚고 있으니 확실히 접속 중이다. 이름을 최신으로 갱신한다.
        standing.playerName = playerName

        return when (type) {
            TournamentType.GRADE -> {
                if (!matchesGrade(gradeId)) return false
                // 등급 기본 점수 × 100 + 물고기 가중치. 가중치는 같은 등급 안의 순서를 가른다.
                val base = gradeScores[gradeId.lowercase()] ?: 0
                standing.addGradeScore(base * 100L + fishWeight, now)
                true
            }

            TournamentType.SIZE -> {
                // 크기가 없는 것(쓰레기)은 점수가 안 된다.
                if (size <= 0.0) return false
                standing.addSize(size, now)
                true
            }

            TournamentType.COUNT -> {
                standing.addCatch(now)
                true
            }
        }
    }

    private fun matchesGrade(gradeId: String): Boolean {
        val target = targetGrade
        return target == null || target.equals("all", ignoreCase = true) ||
            target.equals(gradeId, ignoreCase = true)
    }

    /**
     * 순위. 점수가 높은 순, **동점이면 먼저 도달한 사람이 앞**이다.
     *
     * 동점 규칙이 없으면 순위가 맵 순서에 따라 흔들린다 — 같은 대회를 두 번 집계하면 1등이
     * 바뀔 수 있고, 그건 보상이 걸린 자리에서 일어나면 안 된다.
     */
    fun ranked(): List<Standing> =
        standings.values.sortedWith(
            compareByDescending<Standing> { it.score }
                .thenBy { if (it.lastScoredAt == 0L) Long.MAX_VALUE else it.lastScoredAt },
        )

    /** 점수를 한 번이라도 낸, 나가지 않은 사람만. 보상과 결과 공지는 이 목록을 따른다. */
    fun rankedActive(): List<Standing> = ranked().filter { it.score > 0L && !it.left }

    /** 지금 참가 중인 사람 전부. 참가상을 받는 사람들이다. */
    fun participants(): List<Standing> = standings.values.filter { !it.left }

    /** 나간 사람까지 전부. 저장과 결과 기록에 쓴다. */
    fun all(): Collection<Standing> = standings.values

    companion object {

        /**
         * 등급 id → 기본 점수. **2세대 `getGradeBaseScore` 그대로 — F=1 … S=7.**
         *
         * 5세대가 처음에 1·2·3·5·8·13·21 을 "2세대 값"이라고 적어 두었는데 사실이 아니었다.
         * 그대로면 같은 대회에서 S 한 마리가 세 배 무거워져 2세대에서 옮겨 온 서버의 순위가 바뀐다.
         */
        val DEFAULT_GRADE_SCORES: Map<String, Int> = mapOf(
            "f" to 1, "e" to 2, "d" to 3, "c" to 4, "b" to 5, "a" to 6, "s" to 7,
        )
    }
}
