package com.inmc.fishing.region

import org.bukkit.configuration.ConfigurationSection
import java.util.Locale

/**
 * `worldguard.yml` — WorldGuard 보호 구역별 낚시 금지와 등급 확률 배수. 2세대와 같은 형식이다.
 *
 * **WorldGuard 플래그를 쓰지 않는다.** 규칙이 (월드, 구역 이름) 쌍이라 필요한 것은 "지금 어느
 * 구역 안에 있나" 뿐이다. 커스텀 플래그를 등록하면 관리자가 WorldGuard 쪽에서도 따로 설정해야
 * 해서 설정의 출처가 둘로 갈라진다.
 *
 * Bukkit 도 WorldGuard 도 모른다 — 구역 이름 목록을 받아 답한다. 서버 없이 테스트된다.
 */
class RegionRules(private val rules: Map<String, Rule>) {

    /** @property gradeMultipliers 등급 id(소문자) → 배수. 없는 등급은 1.0 */
    data class Rule(val fishingEnabled: Boolean, val gradeMultipliers: Map<String, Double>)

    /** 비어 있으면 WorldGuard 에 묻지도 않는다. 입질마다 도는 경로다. */
    val isEmpty: Boolean get() = rules.isEmpty()

    val size: Int get() = rules.size

    /**
     * 겹친 구역 중 **하나라도** 금지면 금지다(거부 우선).
     *
     * 우선순위로 가르면 관리자가 WorldGuard 쪽 priority 까지 맞춰야 하고, 금지 구역이 뚫리는
     * 실수가 허용 구역이 막히는 실수보다 훨씬 위험하다.
     */
    fun isFishingEnabled(world: String, regions: Collection<String>): Boolean =
        regions.none { rules[key(world, it)]?.fishingEnabled == false }

    /**
     * 겹친 구역의 배수는 **전부 곱한다.** `modifiers.yml` 의 다섯 축과 같은 방식이라 관리자가
     * 새로 익힐 규칙이 없다.
     */
    fun multipliers(world: String, regions: Collection<String>): Map<String, Double> {
        if (regions.isEmpty()) return emptyMap()
        val result = HashMap<String, Double>()
        for (region in regions) {
            val rule = rules[key(world, region)] ?: continue
            for ((grade, factor) in rule.gradeMultipliers) result[grade] = (result[grade] ?: 1.0) * factor
        }
        return result
    }

    companion object {

        val NONE = RegionRules(emptyMap())

        /**
         * `regions` 절을 읽는다. 항목 이름(`spawn_lake`)은 사람이 읽으라고 있는 것이고 실제 열쇠는
         * `world` + `region` 이다.
         *
         * @param warn 둘 중 하나가 비었거나 같은 구역이 두 번 나오면. 조용히 넘기면 그 규칙이
         *   왜 안 먹는지 아무도 모른다.
         */
        fun load(section: ConfigurationSection?, warn: (String) -> Unit = {}): RegionRules {
            if (section == null) return NONE
            val loaded = LinkedHashMap<String, Rule>()
            for (label in section.getKeys(false)) {
                val entry = section.getConfigurationSection(label) ?: continue
                val world = entry.getString("world")
                val region = entry.getString("region")
                if (world.isNullOrBlank() || region.isNullOrBlank()) {
                    warn("worldguard.yml: regions.$label 에 world 또는 region 이 없어 무시합니다")
                    continue
                }

                val multipliers = HashMap<String, Double>()
                entry.getConfigurationSection("grade-weight-multiplier")?.let { grades ->
                    for (grade in grades.getKeys(false)) {
                        multipliers[grade.lowercase(Locale.ROOT)] = grades.getDouble(grade, 1.0)
                    }
                }

                val rule = Rule(entry.getBoolean("fishing-enabled", true), multipliers)
                if (loaded.putIfAbsent(key(world, region), rule) != null) {
                    warn("worldguard.yml: $world/$region 규칙이 중복입니다. regions.$label 은 무시합니다")
                }
            }
            return RegionRules(loaded)
        }

        /** WorldGuard 는 구역 id 를 소문자로 저장한다. 조회 쪽도 맞춘다. */
        private fun key(world: String, region: String): String =
            world.lowercase(Locale.ROOT) + "|" + region.lowercase(Locale.ROOT)
    }
}
