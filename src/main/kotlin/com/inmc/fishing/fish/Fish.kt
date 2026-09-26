package com.inmc.fishing.fish

import kr.inmc.core.item.StoredItem
import org.bukkit.configuration.ConfigurationSection
import kotlin.math.roundToInt

/**
 * 낚을 수 있는 것 하나. 물고기일 수도, 쓰레기(장화·석탄)일 수도 있다.
 *
 * 아이템 정체는 core 의 [StoredItem] 이 들고 있다. 2세대는 `use-type: vanilla|mmoitems` 로
 * 갈라 여섯 개 필드를 손으로 적게 했고, 그래서 ItemsAdder 아이템이나 직접 만든 아이템은
 * 아예 등록할 수 없었다. 지금은 **손에 든 것을 그대로 등록**할 수 있고 네 종류가 같은 길로
 * 들어온다 — 인벤키퍼와 같은 방식이다.
 */
data class Fish(
    val id: String,
    val gradeId: String,
    val item: StoredItem,
    /** 같은 등급 안에서의 추첨 가중치. */
    val weight: Int,
    val size: SizeRange,
    /** 더블 보상 대상인지. 쓰레기는 보통 끈다. */
    val doubleEnabled: Boolean,
    /** 낚였을 때 콘솔에서 실행할 명령어. `{player}` 가 치환된다. */
    val commands: List<String>,
    /** 이 아이템이 회복시키는 피로도. 0 이면 회복 물약이 아니다. */
    val fatigueRecovery: Int,
    /** 도감 등록 칸 수. 가득 채우면 퍼펙트다. */
    val maxSlots: Int,
    val decor: com.inmc.fishing.registry.Decor = com.inmc.fishing.registry.Decor.NONE,
) {

    /** 크기가 있는 물고기인지. 쓰레기·광물은 크기가 없다. */
    val hasSize: Boolean get() = size.hasSize

    val isFatiguePotion: Boolean get() = fatigueRecovery > 0

    fun label(): String = item.label().ifBlank { id }

    fun save(section: ConfigurationSection) {
        item.save(section)
        decor.save(section)
        section.set("grade", gradeId)
        section.set("weight", weight)
        size.save(section)
        section.set("double-enabled", doubleEnabled)
        section.set("commands", commands)
        section.set("fatigue-recovery", fatigueRecovery)
        section.set("max-slots", maxSlots)
    }

    companion object {

        fun load(key: String, gradeId: String, section: ConfigurationSection, defaultSlots: Int): Fish? {
            val id = section.getString("id").orEmpty().ifBlank { key }.lowercase()
            if (id.isBlank()) return null
            val item = StoredItem.load(section) ?: return null

            return Fish(
                id = id,
                gradeId = gradeId.lowercase(),
                item = item,
                weight = section.getInt("weight", 1).coerceAtLeast(0),
                size = SizeRange.load(section),
                doubleEnabled = section.getBoolean("double-enabled", true),
                commands = section.getStringList("commands"),
                fatigueRecovery = section.getInt("fatigue-recovery", 0).coerceAtLeast(0),
                maxSlots = section.getInt("max-slots", defaultSlots).coerceAtLeast(1),
                decor = com.inmc.fishing.registry.Decor.load(section),
            )
        }
    }
}

/**
 * 물고기의 크기 범위. 단위는 cm.
 *
 * 세 값이 다 있어야 의미가 있다 — [min] 과 [max] 가 폭을, [avg] 가 중심을 정한다. 크기가
 * 없는 것(쓰레기)은 [NONE] 이다.
 */
data class SizeRange(val min: Double, val max: Double, val avg: Double) {

    val hasSize: Boolean get() = max > 0.0 && max > min

    /**
     * 크기를 하나 뽑는다.
     *
     * **정규분포다.** 균등분포로 하면 평균 크기가 대어와 똑같이 흔해져 트로피가 무의미해진다.
     * 표준편차는 폭의 1/6 인데, 그래야 99.7% 가 [min]~[max] 안에 들어온다 (±3σ).
     * 밖으로 나간 나머지는 잘라낸다.
     *
     * @param gaussian 평균 0 · 표준편차 1 의 표본. 테스트가 결과를 정할 수 있게 밖에서 받는다.
     */
    fun roll(gaussian: Double): Double {
        if (!hasSize) return 0.0
        val stddev = (max - min) / 6.0
        val raw = avg + gaussian * stddev
        return round(raw.coerceIn(min, max))
    }

    /**
     * **트로피가 되는 크기**를 뽑는다. 대어가 걸렸을 때 쓴다.
     *
     * `[하한, max]` 구간에서 고르게 뽑는다. 정규분포를 쓰지 않는 이유는 이 구간이 이미
     * "특별한 개체"의 영역이라 그 안에서 또 평균에 몰릴 이유가 없기 때문이다. 고르게 뽑으면
     * **레어 트로피가 나올 확률이 구간의 길이 비로 자연스럽게 정해진다** —
     * `(max - 레어하한) / (max - 하한)`.
     *
     * @param floor 트로피 하한. 이 값이 [max] 보다 크면 이 물고기는 트로피가 될 수 없다.
     * @param roll 0.0 이상 1.0 미만.
     * @return 트로피 크기. 불가능하면 0.0.
     */
    fun rollAtLeast(floor: Double, roll: Double): Double {
        if (!hasSize) return 0.0
        if (floor > max) return 0.0
        val low = floor.coerceAtLeast(min)
        return round(low + (max - low) * roll.coerceIn(0.0, 1.0))
    }

    /** 소수점 한 자리. 크기는 표시용이라 그 이상은 의미가 없다. */
    /**
     * ì¸ ê°ì ì ëë¤. [max] ê° 0 ë³´ë¤ í¬ë©´ [hasSize] ê° ìëì´ë ì ëë¤ â ê´ë¦¬ìê° GUI ìì
     * ìµìë¥¼ ìµëë³´ë¤ í¬ê² ìëª» ë£ìì ë ê·¸ ê°ì´ ì¡°ì©í ì¬ë¼ì§ë©´ ë¬´ìì ê³ ì³ì¼ í ì§ ëª¨ë¥¸ë¤.
     */
    fun save(section: ConfigurationSection) {
        if (max <= 0.0) return
        section.set("min-size", min)
        section.set("max-size", max)
        section.set("avg-size", avg)
    }

    private fun round(value: Double): Double = (value * 10.0).roundToInt() / 10.0

    companion object {

        val NONE = SizeRange(0.0, 0.0, 0.0)

        fun load(section: ConfigurationSection): SizeRange {
            val min = section.getDouble("min-size", 0.0)
            val max = section.getDouble("max-size", 0.0)
            // 평균을 안 적으면 가운데로 둔다. 2세대의 FishLoader 와 같은 보정이다.
            val avg = section.getDouble("avg-size", 0.0).takeIf { it > 0.0 } ?: ((min + max) / 2.0)
            return SizeRange(min, max, avg)
        }
    }
}

/** 낚은 것이 트로피인지. */
enum class Trophy {
    NONE, NORMAL, RARE;

    val isTrophy: Boolean get() = this != NONE
}

/**
 * 크기로 트로피를 판정한다.
 *
 * 두 기준이 **다른 값을 기준으로 잡는다** — 일반 트로피는 평균의 배수, 레어 트로피는 최대의
 * 배수다. 평균 기준만 쓰면 폭이 좁은 물고기에서 레어가 너무 쉽고, 최대 기준만 쓰면 폭이 넓은
 * 물고기에서 일반 트로피조차 안 나온다.
 */
class TrophyRule(
    /** 평균 × 이 값 이상이면 일반 트로피. */
    val normalMultiplier: Double,
    /** 최대 × 이 값 이상이면 레어 트로피. */
    val rareMultiplier: Double,
) {

    fun judge(size: SizeRange, rolled: Double): Trophy {
        if (!size.hasSize || rolled <= 0.0) return Trophy.NONE
        // 레어를 먼저 본다. 레어 조건은 일반 조건도 만족하는 경우가 많다.
        if (rolled >= rareFloor(size)) return Trophy.RARE
        if (rolled >= normalFloor(size)) return Trophy.NORMAL
        return Trophy.NONE
    }

    /** 일반 트로피가 되는 최소 크기. */
    fun normalFloor(size: SizeRange): Double = size.avg * normalMultiplier

    /** 레어 트로피가 되는 최소 크기. */
    fun rareFloor(size: SizeRange): Double = size.max * rareMultiplier

    /**
     * 이 물고기가 **트로피가 될 수 있는지**.
     *
     * 평균이 최대에 가까운 물고기는 `평균 × 1.45` 가 최대를 넘어 트로피가 애초에 불가능하다.
     * 대어가 그런 물고기에 걸리면 "대어인데 트로피가 아닌" 모순이 생기므로, 대어 판정에서
     * 미리 걸러야 한다.
     */
    fun canBeTrophy(size: SizeRange): Boolean =
        size.hasSize && minOf(normalFloor(size), rareFloor(size)) <= size.max

    companion object {
        /** 2세대 기본값. */
        val DEFAULT = TrophyRule(normalMultiplier = 1.45, rareMultiplier = 0.9)

        fun load(section: ConfigurationSection?): TrophyRule {
            if (section == null) return DEFAULT
            return TrophyRule(
                normalMultiplier = section.getDouble("trophy-threshold", DEFAULT.normalMultiplier),
                rareMultiplier = section.getDouble("rare-trophy-threshold", DEFAULT.rareMultiplier),
            )
        }
    }
}
