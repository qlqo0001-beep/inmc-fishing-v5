package com.inmc.fishing.modifier

import org.bukkit.configuration.ConfigurationSection

/**
 * 낚시하는 상황이 등급 확률에 주는 보정.
 *
 * 다섯 축이 있다 — 월드 · 바이옴 · 날씨 · 시간 · 권한. **전부 곱연산으로 누적된다.** 네더에서
 * 비 오는 밤에 VIP 가 낚으면 `2.0 × 1.5 × 1.2 × 1.1` 이 S 등급 가중치에 곱해진다.
 *
 * 곱연산인 것이 중요하다. 더하기였다면 축을 하나 더할 때마다 기존 균형이 전부 어긋난다.
 * 곱하기는 "보정 없음"이 1.0 이라, 설정하지 않은 축이 결과에 아무 영향을 주지 않는다.
 *
 * [Bukkit] 타입을 받지 않는다 — 상황을 [Situation] 이라는 값으로 받아 서버 없이 테스트한다.
 */
class Modifiers(
    private val world: Map<String, Map<String, Double>>,
    private val biome: Map<String, Map<String, Double>>,
    private val weather: Map<String, Map<String, Double>>,
    private val time: Map<String, Map<String, Double>>,
    private val permission: Map<String, Map<String, Double>>,
) {

    /**
     * 낚시가 일어나는 상황.
     *
     * 키는 전부 **대문자로 정규화해서** 담는다. 설정 파일에는 `OCEAN`·`ocean` 이 섞여 들어오고,
     * Bukkit 의 enum 이름은 대문자다.
     */
    data class Situation(
        val world: String,
        val biome: String,
        val weather: String,
        val time: String,
        /** 이 플레이어가 가진, 설정에 등장하는 권한들. */
        val permissions: Set<String> = emptySet(),
    )

    /**
     * 등급 id → 곱할 배수.
     *
     * 보정이 없는 등급은 아예 담기지 않는다. [com.inmc.fishing.grade.GradeTable.draw] 가
     * 없는 키를 1.0 으로 읽는다.
     */
    fun multipliers(at: Situation): Map<String, Double> {
        val result = HashMap<String, Double>()

        merge(result, world[at.world.uppercase()])
        merge(result, biome[at.biome.uppercase()])
        merge(result, weather[at.weather.uppercase()])
        merge(result, time[at.time.uppercase()])
        for (node in at.permissions) merge(result, permission[node.lowercase()])

        return result
    }

    /** 설정에 등장하는 권한 노드 전부. 상황을 만들 때 어떤 권한을 물어볼지 정한다. */
    fun permissionNodes(): Set<String> = permission.keys

    private fun merge(into: HashMap<String, Double>, from: Map<String, Double>?) {
        if (from == null) return
        for ((grade, factor) in from) {
            into[grade] = (into[grade] ?: 1.0) * factor
        }
    }

    companion object {

        val NONE = Modifiers(emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyMap())

        fun load(section: ConfigurationSection?): Modifiers {
            if (section == null) return NONE
            return Modifiers(
                world = readAxis(section.getConfigurationSection("world"), upper = true),
                biome = readAxis(section.getConfigurationSection("biome"), upper = true),
                weather = readAxis(section.getConfigurationSection("weather"), upper = true),
                time = readAxis(section.getConfigurationSection("time"), upper = true),
                permission = readPermissions(section.getConfigurationSection("permission")),
            )
        }

        /**
         * 권한 축은 따로 읽는다.
         *
         * **권한 노드에는 점이 들어간다** (`fishing.vip`). `YamlConfiguration` 은 점을 경로
         * 구분자로 다뤄서, `getKeys(false)` 를 부르면 `fishing` 이라는 중간 섹션 하나만 나오고
         * `fishing.vip` 는 그 아래로 들어간다. 그대로 두면 **권한 보정이 통째로 사라진다** —
         * 설정 파일은 멀쩡해 보이고 오류도 안 나므로 아무도 모른다.
         *
         * 그래서 깊은 키를 전부 훑어 `grade-weight-multiplier` 를 가진 자리를 찾고, 그 앞부분을
         * 권한 노드 이름으로 되돌린다.
         */
        private fun readPermissions(section: ConfigurationSection?): Map<String, Map<String, Double>> {
            if (section == null) return emptyMap()
            val axis = HashMap<String, Map<String, Double>>()

            for (path in section.getKeys(true)) {
                if (!path.endsWith(MULTIPLIER)) continue
                val node = path.removeSuffix(".$MULTIPLIER").removeSuffix(MULTIPLIER)
                if (node.isBlank()) continue

                val weights = section.getConfigurationSection(path) ?: continue
                val byGrade = HashMap<String, Double>()
                for (grade in weights.getKeys(false)) {
                    byGrade[grade.lowercase()] = weights.getDouble(grade, 1.0)
                }
                if (byGrade.isNotEmpty()) axis[node.lowercase()] = byGrade
            }
            return axis
        }

        private const val MULTIPLIER = "grade-weight-multiplier"

        private fun readAxis(
            section: ConfigurationSection?,
            upper: Boolean,
        ): Map<String, Map<String, Double>> {
            if (section == null) return emptyMap()
            val axis = HashMap<String, Map<String, Double>>()
            for (key in section.getKeys(false)) {
                val entry = section.getConfigurationSection(key) ?: continue
                val weights = entry.getConfigurationSection(MULTIPLIER) ?: continue

                val byGrade = HashMap<String, Double>()
                for (grade in weights.getKeys(false)) {
                    // 등급 id 는 소문자가 표준이다. 설정에는 `S` 로 적히지만 GradeTable 은 `s` 로 찾는다.
                    byGrade[grade.lowercase()] = weights.getDouble(grade, 1.0)
                }
                if (byGrade.isEmpty()) continue
                axis[if (upper) key.uppercase() else key.lowercase()] = byGrade
            }
            return axis
        }
    }
}
