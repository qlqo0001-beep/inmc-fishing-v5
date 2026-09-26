package com.inmc.fishing.rod

import com.inmc.fishing.grade.Grade
import org.bukkit.configuration.ConfigurationSection

/**
 * 지급하는 낚싯대에 붙는 **능력 로어의 서식** — `item-format.yml`. 2세대와 같은 파일·같은 키다.
 *
 * 등급 보너스 · 낚시 옵션(더블/대어) · 트로피 파이트 옵션(릴 파워/줄 강도/릴 내구도) · 피로도
 * 네 섹션을 구분선 사이에 쌓는다. **0 인 값은 적지 않는다**(등급 보너스는 `show-zero` 로 켤 수 있다).
 *
 * 5세대가 처음에 이 서식을 빠뜨려서 지급한 낚싯대에 능력 줄이 아예 없었다 — 들고 있는 낚싯대가
 * 무엇을 해주는지 볼 길이 없었다. Bukkit 을 모르는 순수 계산이라 서버 없이 검사한다.
 */
data class RodLoreFormat(
    val divider: String = "&8━━━━━━━━━━━━━━━━━━━━",
    val sectionGap: Boolean = true,
    val footerDivider: Boolean = true,
    val gradeBonus: GradeBonusSection = GradeBonusSection(),
    val fishingOptions: Section = Section("&b◈ 낚시 옵션", listOf("더블 확률", "대어 확률")),
    val fightOptions: Section = Section("&d◈ 트로피 파이트 옵션", listOf("릴 파워", "줄 강도", "릴 내구도")),
    val fatigue: Section = Section("&c◈ 피로도", listOf("최대 피로도", "피로 회복")),
) {

    data class GradeBonusSection(
        val enabled: Boolean = true,
        val title: String = "&6✧ 등급 보너스",
        val showZero: Boolean = false,
        val linePrefix: String = "  &8▸ &7",
        val valueSeparator: String = "   ",
        val positiveColor: String = "&a",
        val negativeColor: String = "&c",
        /** 양수 값을 등급 고유 색으로. */
        val useGradeColorPositive: Boolean = true,
    )

    data class Section(
        val title: String,
        /** `label-1`, `label-2` … 순서. */
        val labels: List<String>,
        val enabled: Boolean = true,
        val linePrefix: String = "  &8▸ &7",
        val valueSeparator: String = "   ",
        val positiveColor: String = "&a",
    ) {
        fun line(index: Int, value: String): String =
            linePrefix + labels.getOrElse(index) { "" } + valueSeparator + positiveColor + "+" + value
    }

    /** 이 낚싯대의 능력 줄. 적을 것이 하나도 없으면 빈 목록 — 구분선만 덩그러니 남지 않게. */
    fun lines(rod: Rod, grades: List<Grade>): List<String> {
        val sections = ArrayList<List<String>>()

        if (gradeBonus.enabled) {
            val body = grades.mapNotNull { grade ->
                val value = rod.gradeBonus[grade.id] ?: 0.0
                if (value == 0.0 && !gradeBonus.showZero) return@mapNotNull null
                val color = when {
                    value > 0 && gradeBonus.useGradeColorPositive -> grade.color
                    value > 0 -> gradeBonus.positiveColor
                    else -> gradeBonus.negativeColor
                }
                gradeBonus.linePrefix + grade.id.uppercase() + " 등급" + gradeBonus.valueSeparator +
                    color + (if (value > 0) "+" else "") + trim(value)
            }
            if (body.isNotEmpty()) sections += listOf(gradeBonus.title) + body
        }

        section(sections, fishingOptions, listOf(rod.doubleChance, rod.bigFishChance)) { trim(it) + "%" }
        section(sections, fightOptions, listOf(rod.reelPower, rod.lineStrength, rod.reelDurability)) { trim(it) }
        section(sections, fatigue, listOf(rod.maxFatigue.toDouble(), rod.fatigueRecovery.toDouble())) { trim(it) }

        if (sections.isEmpty()) return emptyList()
        return buildList {
            add(divider)
            for ((index, section) in sections.withIndex()) {
                if (index > 0 && sectionGap) add("")
                addAll(section)
            }
            if (footerDivider) {
                add("")
                add(divider)
            }
        }
    }

    private fun section(into: MutableList<List<String>>, format: Section, values: List<Double>, show: (Double) -> String) {
        if (!format.enabled) return
        val body = values.mapIndexedNotNull { index, value -> if (value > 0.0) format.line(index, show(value)) else null }
        if (body.isNotEmpty()) into += listOf(format.title) + body
    }

    companion object {

        /** 정수면 소수점을 떼고, 아니면 한 자리. 2세대 `trimNumber`. */
        fun trim(value: Double): String =
            if (value == Math.floor(value) && !value.isInfinite()) value.toLong().toString() else String.format("%.1f", value)

        /** `item-format.yml` 의 `rod` 절. 빠진 키는 전부 2세대 기본값이다. */
        fun load(section: ConfigurationSection?): RodLoreFormat {
            val d = RodLoreFormat()
            if (section == null) return d
            val gb = section.getConfigurationSection("grade-bonus")
            return RodLoreFormat(
                divider = section.getString("divider", d.divider)!!,
                sectionGap = section.getBoolean("section-gap", d.sectionGap),
                footerDivider = section.getBoolean("footer-divider", d.footerDivider),
                gradeBonus = if (gb == null) d.gradeBonus else GradeBonusSection(
                    enabled = gb.getBoolean("enabled", true),
                    title = gb.getString("title", d.gradeBonus.title)!!,
                    showZero = gb.getBoolean("show-zero", false),
                    linePrefix = gb.getString("line-prefix", d.gradeBonus.linePrefix)!!,
                    valueSeparator = gb.getString("value-separator", d.gradeBonus.valueSeparator)!!,
                    positiveColor = gb.getString("positive-color", d.gradeBonus.positiveColor)!!,
                    negativeColor = gb.getString("negative-color", d.gradeBonus.negativeColor)!!,
                    useGradeColorPositive = gb.getBoolean("use-grade-color-positive", true),
                ),
                fishingOptions = loadSection(section.getConfigurationSection("fishing-options"), d.fishingOptions),
                fightOptions = loadSection(section.getConfigurationSection("fight-options"), d.fightOptions),
                fatigue = loadSection(section.getConfigurationSection("fatigue"), d.fatigue),
            )
        }

        private fun loadSection(section: ConfigurationSection?, d: Section): Section {
            if (section == null) return d
            return Section(
                title = section.getString("title", d.title)!!,
                labels = d.labels.indices.map { section.getString("label-" + (it + 1), d.labels[it])!! },
                enabled = section.getBoolean("enabled", true),
                linePrefix = section.getString("line-prefix", d.linePrefix)!!,
                valueSeparator = section.getString("value-separator", d.valueSeparator)!!,
                positiveColor = section.getString("positive-color", d.positiveColor)!!,
            )
        }
    }
}
