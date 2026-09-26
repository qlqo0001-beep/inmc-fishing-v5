package com.inmc.fishing.rod

import com.inmc.fishing.fish.Bonuses
import kr.inmc.core.item.StoredItem
import org.bukkit.configuration.ConfigurationSection

/**
 * 낚싯대 하나.
 *
 * 물고기와 같은 이유로 core 의 [StoredItem] 을 쓴다 — 손에 든 것을 그대로 등록할 수 있고,
 * MMOItems·ItemsAdder·바닐라·수제 낚싯대가 전부 같은 길로 들어온다.
 *
 * **등급 보너스는 덧셈이다.** 곱셈이면 가중치 1 인 S 에 배수를 줘봐야 1 이 늘 뿐이라, 좋은
 * 낚싯대가 희귀 등급을 열어준다는 약속이 성립하지 않는다.
 */
data class Rod(
    val id: String,
    val item: StoredItem,
    /** 등급 id → 가중치에 **더할** 값. */
    val gradeBonus: Map<String, Double> = emptyMap(),
    /** 대어 확률에 더할 퍼센트 포인트. */
    val bigFishChance: Double = 0.0,
    /** 더블 확률에 더할 퍼센트 포인트. */
    val doubleChance: Double = 0.0,
    // --- 피로도. 둘 다 **더해지는** 값이다 ---
    /**
     * 이걸 들고 있는 동안 **자연 회복 한계**를 이만큼 올린다.
     *
     * 절대 상한(`fatigue.max`)을 넘지는 못한다 — 그건 물약과 관리자 명령만 닿는 영역이다.
     */
    val maxFatigue: Int = 0,
    /** 자연 회복 1회당 이만큼 **더** 회복한다. */
    val fatigueRecovery: Int = 0,
    // --- 힘겨루기 스탯. 기본값에 **더해지는** 추가값이다 ---
    val reelPower: Double = 0.0,
    val lineStrength: Double = 0.0,
    val reelDurability: Double = 0.0,
    // --- 편의. 인첸트가 주로 채운다 ---
    /** 미니게임 제한 시간에 더할 초. */
    val minigameSeconds: Double = 0.0,
    /** 입질 때 미끼를 쓰지 않을 확률(%). */
    val baitSaveChance: Double = 0.0,
    /** 물고기 크기에 더할 퍼센트. 미끼의 크기 보정과 더해진다. */
    val sizePercent: Double = 0.0,
    /** 자동 낚시로 낚을 때 피로도를 쓰지 않을 확률(%). */
    val fatigueSaveChance: Double = 0.0,
    /** 자동 낚시 대기에서 뺄 초. */
    val autoCatchReduce: Double = 0.0,
    val decor: com.inmc.fishing.registry.Decor = com.inmc.fishing.registry.Decor.NONE,
) {

    /** 추첨에 넘길 보정으로 바꾼다. */
    fun bonuses(): Bonuses = Bonuses(
        gradeFlatBonus = gradeBonus,
        bigFishChance = bigFishChance,
        doubleChance = doubleChance,
    )

    /** 로어에 찍을 능력 줄. **0 인 것은 적지 않는다** — 전부 적으면 읽을 수 없다. */
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
        if (bigFishChance != 0.0) add("<gray>대어 확률 <yellow>+$bigFishChance%</yellow></gray>")
        if (doubleChance != 0.0) add("<gray>더블 확률 <yellow>+$doubleChance%</yellow></gray>")
        if (maxFatigue != 0) add("<gray>최대 피로도 <yellow>+$maxFatigue</yellow></gray>")
        if (fatigueRecovery != 0) add("<gray>피로 회복 <yellow>+$fatigueRecovery</yellow></gray>")
        if (reelPower != 0.0) add("<gray>릴 파워 <yellow>+$reelPower</yellow></gray>")
        if (lineStrength != 0.0) add("<gray>줄 강도 <yellow>+$lineStrength</yellow></gray>")
        if (reelDurability != 0.0) add("<gray>릴 내구도 <yellow>+$reelDurability</yellow></gray>")
        if (minigameSeconds != 0.0) add("<gray>미니게임 시간 <yellow>+${minigameSeconds}초</yellow></gray>")
        if (baitSaveChance != 0.0) add("<gray>미끼 절약 <yellow>$baitSaveChance%</yellow></gray>")
        if (sizePercent != 0.0) add("<gray>크기 <yellow>+$sizePercent%</yellow></gray>")
        if (fatigueSaveChance != 0.0) add("<gray>피로 절약 <yellow>$fatigueSaveChance%</yellow></gray>")
        if (autoCatchReduce != 0.0) add("<gray>자동 낚시 대기 <yellow>-${autoCatchReduce}초</yellow></gray>")
    }

    fun save(section: ConfigurationSection) {
        item.save(section)
        decor.save(section)
        if (gradeBonus.isNotEmpty()) {
            val node = section.createSection("bonus")
            for ((grade, value) in gradeBonus) node.set(grade, value)
        }
        section.set("big-fish-chance-bonus", bigFishChance)
        section.set("double-chance-bonus", doubleChance)
        section.set("options.max-fatigue", maxFatigue)
        section.set("options.fatigue-recovery", fatigueRecovery)
        section.set("options.reel-power", reelPower)
        section.set("options.line-strength", lineStrength)
        section.set("options.reel-durability", reelDurability)
        section.set("options.minigame-time", minigameSeconds)
        section.set("options.bait-save", baitSaveChance)
        section.set("options.size-bonus", sizePercent)
        section.set("options.fatigue-save", fatigueSaveChance)
        section.set("options.auto-catch-reduce", autoCatchReduce)
    }

    companion object {

        fun load(key: String, section: ConfigurationSection): Rod? {
            val id = section.getString("id").orEmpty().ifBlank { key }.lowercase()
            if (id.isBlank()) return null
            val item = StoredItem.load(section) ?: return null

            val bonus = HashMap<String, Double>()
            section.getConfigurationSection("bonus")?.let { node ->
                for (grade in node.getKeys(false)) bonus[grade.lowercase()] = node.getDouble(grade, 0.0)
            }

            return Rod(
                id = id,
                item = item,
                gradeBonus = bonus,
                bigFishChance = section.getDouble("big-fish-chance-bonus", 0.0),
                doubleChance = section.getDouble("double-chance-bonus", 0.0),
                maxFatigue = section.getInt("options.max-fatigue", 0).coerceAtLeast(0),
                fatigueRecovery = section.getInt("options.fatigue-recovery", 0).coerceAtLeast(0),
                reelPower = section.getDouble("options.reel-power", 0.0),
                lineStrength = section.getDouble("options.line-strength", 0.0),
                reelDurability = section.getDouble("options.reel-durability", 0.0),
                minigameSeconds = section.getDouble("options.minigame-time", 0.0),
                baitSaveChance = section.getDouble("options.bait-save", 0.0),
                sizePercent = section.getDouble("options.size-bonus", 0.0),
                fatigueSaveChance = section.getDouble("options.fatigue-save", 0.0),
                autoCatchReduce = section.getDouble("options.auto-catch-reduce", 0.0),
                decor = com.inmc.fishing.registry.Decor.load(section),
            )
        }
    }
}
