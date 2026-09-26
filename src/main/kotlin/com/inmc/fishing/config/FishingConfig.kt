package com.inmc.fishing.config

import com.inmc.fishing.fatigue.FatigueConfig
import com.inmc.fishing.fillet.FilletConfig
import com.inmc.fishing.fish.TrophyRule
import kr.inmc.core.reward.RewardSettings
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration

/**
 * `config.yml` 한 벌의 **불변 스냅샷**.
 *
 * 리로드는 이 객체를 통째로 새로 만들어 바꿔 끼운다. 2세대의 `ConfigManager` 는 값을 필드로
 * 들고 있으면서 `reload()` 가 하나씩 덮어써서, 리로드 도중에 읽는 쪽이 반쯤 바뀐 설정을 보는
 * 구간이 있었다.
 *
 * [RewardSettings] 를 구현한다 — core 의 보상·우편함이 요구하는 다섯 값이다.
 */
class FishingConfig(
    val enabled: Boolean,
    /** 낚시가 허용된 월드. 비어 있으면 전부 허용이다. */
    private val allowedWorlds: Set<String>,
    /** 인벤토리에 빈 칸이 없으면 낚이지 않는다. */
    val requireEmptySlot: Boolean,
    /** 넘치는 보상을 바닥에 흘릴지. */
    val dropOverflow: Boolean,
    val debug: Boolean,
    /** 등록되지 않은 바닐라 낚싯대로도 낚을 수 있는지. */
    val allowUnregisteredRod: Boolean,

    // --- 미니게임 끄기(자동 낚시) ---
    val allowMinigameToggle: Boolean,
    /** 자동 낚시일 때 등급별 대기 시간(초). */
    val autoCatchDelay: Map<String, Int>,
    /** 자동 낚시로 나올 수 있는 등급. */
    val autoCatchGrades: Set<String>,

    // --- 확률 ---
    val doubleChance: Double,
    val bigFishChance: Double,
    val trophyRule: TrophyRule,

    // --- 하위 설정 ---
    /** 물고기에 max-slots 를 안 적었을 때 쓰는 도감 칸 수. */
    val defaultMaxSlots: Int,
    val netMaxSize: Int,
    val fillet: FilletConfig,
    val fatigue: FatigueConfig,
    val resultTitles: ResultTitles,

    // --- core 보상 ---
    override val mailboxLimit: Int,
    override val mailboxExpireSeconds: Long,
    override val dropWhenInventoryFull: Boolean,
    override val broadcastRewards: Boolean,
    override val seasonArchiveLimit: Int,
) : RewardSettings {

    /** 비어 있으면 전부 허용이다 — 월드를 하나씩 적게 만들면 새 월드마다 문의가 온다. */
    fun isWorldAllowed(worldName: String?): Boolean =
        allowedWorlds.isEmpty() || (worldName != null && worldName.lowercase() in allowedWorlds)

    fun autoCatchDelayFor(gradeId: String): Int = autoCatchDelay[gradeId.lowercase()] ?: 0

    fun isAutoCatchable(gradeId: String): Boolean =
        autoCatchGrades.isEmpty() || gradeId.lowercase() in autoCatchGrades

    companion object {

        fun from(config: YamlConfiguration): FishingConfig {
            val settings = config.getConfigurationSection("settings")
            val minigameOff = config.getConfigurationSection("minigame-off")
            val rates = config.getConfigurationSection("rates")
            val rewards = config.getConfigurationSection("rewards")

            return FishingConfig(
                enabled = settings?.getBoolean("enabled", true) ?: true,
                allowedWorlds = settings?.getStringList("allowed-worlds")
                    .orEmpty().map { it.lowercase() }.toSet(),
                requireEmptySlot = settings?.getBoolean("require-empty-slot", true) ?: true,
                dropOverflow = settings?.getBoolean("drop-overflow-items", true) ?: true,
                debug = settings?.getBoolean("debug", false) ?: false,
                allowUnregisteredRod = settings?.getBoolean("allow-unregistered-vanilla-rod", true) ?: true,

                allowMinigameToggle = minigameOff?.getBoolean("allow-player-toggle", true) ?: true,
                autoCatchDelay = readInts(minigameOff?.getConfigurationSection("catch-delay-seconds")),
                autoCatchGrades = minigameOff?.getStringList("allowed-grades")
                    .orEmpty().map { it.lowercase() }.toSet(),

                doubleChance = rates?.getDouble("double-chance", 7.0) ?: 7.0,
                bigFishChance = rates?.getDouble("big-fish-chance", 1.0) ?: 1.0,
                trophyRule = TrophyRule.load(rates),

                defaultMaxSlots = config.getInt("collection.default-max-slots", 10).coerceAtLeast(1),
                netMaxSize = config.getInt("net.max-size", 100),
                fillet = FilletConfig.load(config.getConfigurationSection("fillet")),
                fatigue = FatigueConfig.load(config.getConfigurationSection("fatigue")),
                resultTitles = ResultTitles.load(config.getConfigurationSection("titles")),

                mailboxLimit = rewards?.getInt("mailbox-limit", 54) ?: 54,
                mailboxExpireSeconds = rewards?.getLong("mailbox-expire-seconds", 604800L) ?: 604800L,
                dropWhenInventoryFull = rewards?.getBoolean("drop-when-full", true) ?: true,
                broadcastRewards = rewards?.getBoolean("broadcast", true) ?: true,
                seasonArchiveLimit = rewards?.getInt("season-archive-limit", 10) ?: 10,
            )
        }

        private fun readInts(section: ConfigurationSection?): Map<String, Int> {
            if (section == null) return emptyMap()
            val result = HashMap<String, Int>()
            for (key in section.getKeys(false)) result[key.lowercase()] = section.getInt(key, 0)
            return result
        }
    }
}
