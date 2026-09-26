package com.inmc.fishing.grade

import org.bukkit.configuration.ConfigurationSection

/**
 * 물고기 등급 하나. `grades.yml` 에서 온다.
 *
 * 등급은 **두 가지를 동시에** 정한다 - 나올 확률([weight])과 미니게임 난이도([inputCount] ·
 * [timeSeconds]). 하나만 고치면 균형이 어긋나므로 한 곳에 모여 있다.
 *
 * 2세대에는 `next-grade` 가 있었다. 대어가 한 등급 위로 승급시키는 장치였는데, 대어가
 * **트로피 보장**으로 바뀌면서 쓰이지 않게 되어 걷어냈다.
 *
 * 등급은 **고정 목록이 아니다.** 관리자가 `grades.yml` 에 등급을 더하거나 뺄 수 있어야 해서
 * enum 이 아니라 설정에서 읽는 값이다.
 */
data class Grade(
    val id: String,
    val displayName: String,
    /** 추첨 가중치. 전체 합에 대한 비율로 뽑힌다. */
    val weight: Double,
    /** 미니게임에서 눌러야 하는 횟수. */
    val inputCount: Int,
    /** 그 횟수를 눌러야 하는 제한 시간. */
    val timeSeconds: Double,
    /** MiniMessage 색. 메시지와 로어에 붙는다. */
    val color: String,
) {

    /**
     * `&d[S]` 같은 표시. 낚은 메시지와 어망 아이콘에 쓴다.
     *
     * 색을 닫지 않는다. core 의 [kr.inmc.core.util.Text] 가 레거시 색코드(`&d`)를
     * MiniMessage 로 옮기면서 열린 채로 두고, 그래야 뒤에 오는 물고기 이름까지 같은 색이 된다.
     * 관리자가 `grades.yml` 에 MiniMessage 표기(`<light_purple>`)를 적어도 똑같이 동작한다.
     */
    fun tag(): String = "$color[$displayName]"

    companion object {

        fun load(key: String, section: ConfigurationSection): Grade? {
            val id = section.getString("id").orEmpty().ifBlank { key }.lowercase()
            if (id.isBlank()) return null
            return Grade(
                id = id,
                displayName = key.uppercase(),
                weight = section.getDouble("weight", 0.0).coerceAtLeast(0.0),
                inputCount = section.getInt("input-count", 3).coerceAtLeast(1),
                timeSeconds = section.getDouble("time-seconds", 5.0).coerceAtLeast(0.1),
                color = section.getString("color").orEmpty().ifBlank { "<white>" },
            )
        }
    }
}

/**
 * 등급 전부와 그 사이의 관계.
 *
 * 추첨과 조회가 전부 여기를 지난다. 설정을 다시 읽으면 통째로 교체되는 불변 스냅샷이다.
 */
class GradeTable(grades: List<Grade>) {

    private val byId: Map<String, Grade> = grades.associateBy { it.id }

    /** 설정에 적힌 순서. 목록·GUI 가 이 순서를 따른다. */
    val ordered: List<Grade> = grades

    val isEmpty: Boolean get() = byId.isEmpty()

    operator fun get(id: String?): Grade? = id?.let { byId[it.lowercase()] }

    /**
     * 가중치 추첨.
     *
     * **환경 보정은 곱하고, 장비 보너스는 더한다. 순서가 그 반대면 안 된다.**
     *
     * ```
     * 최종 가중치 = 기본 가중치 × 환경 배수 + 장비 보너스
     * ```
     *
     * 환경(월드·바이옴·날씨·시간·권한)은 [com.inmc.fishing.modifier.Modifiers] 가 곱연산으로
     * 쌓아 온다 — 축을 더해도 기존 균형이 어긋나지 않는다. 반면 낚싯대와 미끼는 **덧셈**이다.
     * 곱셈이면 가중치가 0 에 가까운 최상위 등급에서 아무 효과가 없다 — S 가중치 1 에 2배를
     * 곱해봐야 1 이 늘 뿐이지만, +5 를 더하면 실제로 확률이 여섯 배가 된다. 좋은 낚싯대가
     * "희귀한 것을 낚게 해준다"는 약속이 성립하려면 덧셈이어야 한다.
     *
     * @param roll 0.0 이상 1.0 미만. 테스트가 결과를 정할 수 있게 밖에서 받는다.
     */
    fun draw(
        bonus: Map<String, Double> = emptyMap(),
        roll: Double,
        flatBonus: Map<String, Double> = emptyMap(),
    ): Grade? {
        if (byId.isEmpty()) return null

        val weights = ordered.map { grade ->
            val scaled = grade.weight * (bonus[grade.id] ?: 1.0) + (flatBonus[grade.id] ?: 0.0)
            grade to scaled.coerceAtLeast(0.0)
        }
        val total = weights.sumOf { it.second }
        // 전부 0 이면 비율을 만들 수 없다. 첫 등급으로 떨어뜨린다 - 입질이 사라지는 것보다 낫다.
        if (total <= 0.0) return ordered.firstOrNull()

        var cursor = roll.coerceIn(0.0, 0.999999) * total
        for ((grade, weight) in weights) {
            cursor -= weight
            if (cursor < 0.0) return grade
        }
        // 부동소수 오차로 끝까지 갔을 때. 마지막 등급이 맞다.
        return weights.lastOrNull { it.second > 0.0 }?.first
    }

    companion object {

        fun load(section: ConfigurationSection?): GradeTable {
            if (section == null) return GradeTable(emptyList())
            val grades = section.getKeys(false).mapNotNull { key ->
                section.getConfigurationSection(key)?.let { Grade.load(key, it) }
            }
            return GradeTable(grades)
        }
    }
}
