package com.inmc.fishing.bait

import com.inmc.fishing.fish.Bonuses
import kr.inmc.core.item.StoredItem
import org.bukkit.configuration.ConfigurationSection

/**
 * 미끼 하나. **왼손(오프핸드)에 들고**, 입질이 오면 한 개 소모된다.
 *
 * 낚싯대와 겹치는 축(등급 보너스)은 **더해진다** — 좋은 낚싯대에 좋은 미끼를 더하면 그만큼
 * 더해지는 것이 자연스럽고, 곱하면 두 개의 조합이 기하급수로 벌어진다.
 *
 * 미끼만 가진 축이 크기 보정이다. **퍼센트를 곱한 뒤 고정값을 더한다** — 순서가 반대면
 * 결과가 달라진다. 그리고 이 보정은 **트로피 판정보다 먼저** 걸리므로, 미끼로 키운 크기가
 * 관리자가 정한 `max-size` 를 넘을 수 있다. 2세대부터 의도된 동작이다.
 */
data class Bait(
    val id: String,
    val item: StoredItem,
    /** 등급 id → 가중치에 **더할** 값. */
    val gradeBonus: Map<String, Double> = emptyMap(),
    /** 크기에 곱할 퍼센트. 10.0 이면 +10%. */
    val sizePercent: Double = 0.0,
    /** 크기에 더할 고정값(cm). 퍼센트 **뒤에** 더해진다. */
    val sizeFixed: Double = 0.0,
    val decor: com.inmc.fishing.registry.Decor = com.inmc.fishing.registry.Decor.NONE,
) {

    fun bonuses(): Bonuses = Bonuses(
        gradeFlatBonus = gradeBonus,
        sizePercent = sizePercent,
        sizeFixed = sizeFixed,
    )

    /** 로어에 찍을 능력 줄. 0 인 것은 적지 않는다. */
    fun describe(gradeOrder: List<String>): List<String> = buildList {
        val bonuses = gradeOrder.mapNotNull { id ->
            val value = gradeBonus[id.lowercase()] ?: return@mapNotNull null
            if (value == 0.0) null else id.uppercase() to value
        }
        if (bonuses.isNotEmpty()) {
            add("<gray>등급 보너스</gray>")
            for ((grade, value) in bonuses) {
                val sign = if (value > 0) "+" else ""
                add("  <dark_gray>▸</dark_gray> <white>$grade</white> <yellow>$sign$value</yellow>")
            }
        }
        if (sizePercent != 0.0) add("<gray>크기 <yellow>+$sizePercent%</yellow></gray>")
        if (sizeFixed != 0.0) add("<gray>크기 <yellow>+${sizeFixed}cm</yellow></gray>")
    }

    fun save(section: ConfigurationSection) {
        item.save(section)
        decor.save(section)
        if (gradeBonus.isNotEmpty()) {
            val node = section.createSection("grade-bonus")
            for ((grade, value) in gradeBonus) node.set(grade, value)
        }
        section.set("size-bonus-percent", sizePercent)
        section.set("size-bonus-fixed", sizeFixed)
    }

    companion object {

        fun load(key: String, section: ConfigurationSection): Bait? {
            val id = section.getString("id").orEmpty().ifBlank { key }.lowercase()
            if (id.isBlank()) return null
            val item = StoredItem.load(section) ?: return null

            val bonus = HashMap<String, Double>()
            section.getConfigurationSection("grade-bonus")?.let { node ->
                for (grade in node.getKeys(false)) bonus[grade.lowercase()] = node.getDouble(grade, 0.0)
            }

            return Bait(
                id = id,
                item = item,
                gradeBonus = bonus,
                sizePercent = section.getDouble("size-bonus-percent", 0.0),
                sizeFixed = section.getDouble("size-bonus-fixed", 0.0),
                decor = com.inmc.fishing.registry.Decor.load(section),
            )
        }
    }
}
