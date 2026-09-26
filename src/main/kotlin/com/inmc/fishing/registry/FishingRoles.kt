package com.inmc.fishing.registry

import com.inmc.fishing.Fishing
import kr.inmc.core.integration.ItemRoles
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration

/**
 * 낚시가 커스텀아이템에 내놓는 역할(core [ItemRoles]) — 낚싯대 · 미끼 · 피로회복 물약 · 생선살 · 물고기 겉모습.
 *
 * 커스텀아이템이 있으면 앞의 넷은 **통째로** 커스텀아이템으로 가고(파일은 `.migrated`), 물고기는 **겉모습만** 간다 — 확률·크기·등급은
 * `fish.yml` 에 남는다(사용자 결정 2026-09-25). 값은 정의의 YAML 을 납작하게 편 것([flatten])이라 잃는 것이 없고, 낚시 관리 화면이
 * 그대로 고칠 수 있다. 커스텀아이템 화면에는 자주 고치는 칸만 내놓는다.
 */
object FishingRoles {

    const val OWNER = "낚시"

    val ROD = "fishing.rod"
    val BAIT = "fishing.bait"
    val POTION = "fishing.potion"
    val FILLET = "fishing.fillet"
    val FISH = "fishing.fish"

    val ALL = listOf(ROD, BAIT, POTION, FILLET, FISH)

    private fun grades(fishing: Fishing): () -> List<Pair<String, String>> = {
        runCatching { fishing.grades.ordered.map { it.id to it.id.uppercase() } }.getOrDefault(emptyList())
    }

    fun roles(fishing: Fishing): List<ItemRoles.Role> = listOf(
        ItemRoles.Role(
            ROD, OWNER, "낚싯대", Material.FISHING_ROD,
            listOf("낚시의 낚싯대. 등급 보너스는 낚시 관리 화면에서."),
            listOf(
                ItemRoles.Number("options/max-fatigue", "최대 피로도 보너스", 0.0, 100000.0, 100.0, "0"),
                ItemRoles.Number("options/fatigue-recovery", "피로 회복 보너스", 0.0, 10000.0, 1.0, "0"),
                ItemRoles.Number("options/reel-power", "릴 힘", 0.0, 1000.0, 1.0, "0", integer = false),
                ItemRoles.Number("options/line-strength", "줄 세기", 0.0, 1000.0, 1.0, "0", integer = false),
                ItemRoles.Number("options/reel-durability", "릴 내구", 0.0, 1000.0, 1.0, "0", integer = false),
                ItemRoles.Number("options/minigame-time", "미니게임 시간 보너스(초)", 0.0, 60.0, 0.5, "0", integer = false),
                ItemRoles.Number("options/bait-save", "미끼 아낌(%)", 0.0, 100.0, 1.0, "0", integer = false),
                ItemRoles.Number("options/size-bonus", "크기 보너스(%)", 0.0, 1000.0, 1.0, "0", integer = false),
                ItemRoles.Number("options/fatigue-save", "피로 아낌(%)", 0.0, 100.0, 1.0, "0", integer = false),
                ItemRoles.Number("options/auto-catch-reduce", "자동 낚시 단축", 0.0, 100.0, 1.0, "0", integer = false),
                ItemRoles.Number("big-fish-chance-bonus", "대어 확률 보너스(%)", 0.0, 100.0, 1.0, "0", integer = false),
                ItemRoles.Number("double-chance-bonus", "더블 확률 보너스(%)", 0.0, 100.0, 1.0, "0", integer = false),
            ),
        ),
        ItemRoles.Role(
            BAIT, OWNER, "미끼", Material.WHEAT_SEEDS,
            listOf("낚싯대와 함께 가방에 있으면 쓰입니다. 등급 보너스는 낚시 관리 화면에서."),
            listOf(
                ItemRoles.Number("size-bonus-percent", "크기 보너스(%)", 0.0, 1000.0, 1.0, "0", integer = false),
                ItemRoles.Number("size-bonus-fixed", "크기 보너스(고정)", 0.0, 1000.0, 1.0, "0", integer = false),
            ),
        ),
        ItemRoles.Role(
            POTION, OWNER, "피로회복 물약", Material.POTION,
            listOf("마시면 낚시 피로도를 되돌립니다."),
            listOf(
                ItemRoles.Number("amount", "되돌리는 피로도", 0.0, 100000.0, 10.0, "100"),
                ItemRoles.Choice("grade", "등급", grades(fishing)),
            ),
        ),
        ItemRoles.Role(
            FILLET, OWNER, "생선살", Material.COD,
            listOf("손질대에서 물고기를 손질하면 나옵니다."),
            listOf(
                ItemRoles.Choice("grade", "등급", grades(fishing)),
                ItemRoles.Choice("trophy-type", "트로피", { listOf("none" to "트로피 아님", "trophy" to "트로피", "rare" to "레어 트로피") }, "none"),
            ),
        ),
        ItemRoles.Role(
            FISH, OWNER, "물고기 겉모습", Material.TROPICAL_FISH,
            listOf("이 아이템이 그 물고기의 모습이 됩니다.", "확률·크기·등급은 낚시의 fish.yml 에 그대로 있습니다."),
            listOf(ItemRoles.Choice("fish", "물고기", { fishing.fish.all().map { it.id to it.label() } })),
        ),
    )

    // --- 정의 YAML ↔ 역할 값 ----------------------------------------------------------------

    /** 아이템 정체는 역할을 맡은 아이템이 정하므로 값에 넣지 않는다. 겉모습(이름·설명)도 커스텀아이템의 것이다. */
    private val SKIP = setOf("item", "mode", "material", "name", "snapshot", Decor.KEY_NAME, "lore")

    private const val LIST = "yaml-list:"

    /** 정의 YAML → 납작한 값. 경로의 점은 `/` 로(값 열쇠가 YAML 경로로 읽히지 않게). 목록은 YAML 글자로. */
    fun flatten(section: ConfigurationSection): Map<String, String> = buildMap {
        for (key in section.getKeys(true)) {
            if (section.isConfigurationSection(key) || key in SKIP) continue
            val value = section.get(key) ?: continue
            put(key.replace('.', '/'), if (value is List<*>) LIST + YamlConfiguration().also { it.set("v", value) }.saveToString() else value.toString())
        }
    }

    /** [flatten] 의 역. 글자를 원래 타입(참·거짓·정수·실수)으로 되돌린다 — YAML 읽기가 글자 "30" 을 숫자로 안 본다. */
    fun unflatten(values: Map<String, String>): YamlConfiguration {
        val yaml = YamlConfiguration()
        for ((key, raw) in values) {
            if (key == ItemRoles.LEGACY) continue
            yaml.set(key.replace('/', '.'), when {
                raw.startsWith(LIST) -> YamlConfiguration().also { it.loadFromString(raw.removePrefix(LIST)) }.getList("v")
                raw == "true" || raw == "false" -> raw.toBoolean()
                raw.toIntOrNull() != null -> raw.toInt()
                raw.toLongOrNull() != null -> raw.toLong()
                raw.toDoubleOrNull() != null -> raw.toDouble()
                else -> raw
            })
        }
        return yaml
    }
}
