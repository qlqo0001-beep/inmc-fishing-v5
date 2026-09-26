package com.inmc.fishing.fish

/**
 * 확률 검증 — 2세대 `/fishing simulate <n>`. 추첨기를 [count] 번 돌려 등급·물고기별로 센다.
 *
 * 환경 보정·장비 보너스 없이([Bonuses.NONE]) 돌린다 — 2세대도 플레이어 없이 돌렸다. 설정만의
 * 확률을 보는 도구다.
 */
object Simulation {

    class Result(
        val total: Long,
        val bigFish: Long,
        val double: Long,
        /** 등급 id → 횟수. 등급표 순서. */
        val byGrade: Map<String, Long>,
        /** 물고기 id → 횟수. 많은 순. */
        val byFish: Map<String, Long>,
    )

    fun run(engine: RollEngine, count: Int, rolls: Rolls): Result {
        var total = 0L
        var big = 0L
        var double = 0L
        val grades = LinkedHashMap<String, Long>()
        val fish = HashMap<String, Long>()
        repeat(count) {
            val result = engine.roll(rolls) ?: return@repeat
            total++
            if (result.bigFish) big++
            if (result.double) double++
            grades.merge(result.grade.id, 1L, Long::plus)
            fish.merge(result.fish.id, 1L, Long::plus)
        }
        return Result(
            total = total,
            bigFish = big,
            double = double,
            byGrade = grades,
            byFish = fish.entries.sortedByDescending { it.value }.associate { it.key to it.value },
        )
    }

    /** 2세대와 같은 `%.2f` 백분율. */
    fun percent(count: Long, total: Long): String = String.format("%.2f", count * 100.0 / total.coerceAtLeast(1L))
}
