package com.inmc.fishing.collection

import com.inmc.fishing.fish.Trophy
import org.bukkit.configuration.ConfigurationSection

/**
 * 도감 한 칸 — 물고기 하나에 대한 그 사람의 기록.
 *
 * 세 단계가 있다. **발견**(한 번이라도 낚음) → **등록**(아이템을 소모해 칸을 채움) →
 * **퍼펙트**(칸을 다 채움). 발견과 등록이 나뉘어 있는 것이 핵심이다 — 낚기만 해서는 도감이
 * 차지 않고, 잡은 물고기를 바쳐야 한다.
 */
class CollectionEntry(
    val fishId: String,
    val gradeId: String,
    /** 다 채우면 퍼펙트가 되는 칸 수. */
    val maxSlots: Int,
) {

    /** 한 번이라도 낚았는지. 등록은 안 했어도 도감에 이름이 뜬다. */
    var discovered: Boolean = false
        private set

    /** 지금까지 채운 칸. */
    var registeredSlots: Int = 0
        private set

    /** 도감 등록과 무관하게 총 몇 마리 낚았는지. */
    var totalCaught: Int = 0
        private set

    var firstCaughtAt: Long = 0L
        private set

    /** 가장 크게 낚은 것. 사이즈 랭킹의 재료다. */
    var largestSize: Double = 0.0
        private set

    /** 가장 작게 낚은 것. 0 은 기록 없음이다. */
    var smallestSize: Double = 0.0
        private set

    var trophyCount: Int = 0
        private set

    var rareTrophyCount: Int = 0
        private set

    /** 이미 받은 보상. 같은 보상을 두 번 주지 않기 위한 기록이다. */
    private val claimed = HashSet<String>()

    /** 등록한 물고기의 크기, 넣은 순서대로. 회수할 때 **그 크기 그대로** 돌려주려고 둔다(2세대와 같다). */
    private val registeredSizes = ArrayList<Double>()

    val isPerfect: Boolean get() = registeredSlots >= maxSlots

    val hasTrophy: Boolean get() = trophyCount > 0 || rareTrophyCount > 0

    /** 한 칸이라도 채웠는지. 등급 올등록 판정의 기준이다. */
    val isRegistered: Boolean get() = registeredSlots > 0

    /**
     * 한 마리 낚았다고 기록한다. **도감 칸은 차지 않는다** — 등록은 따로다.
     *
     * @return 이번에 새로 발견한 것이면 true.
     */
    fun recordCatch(size: Double, trophy: Trophy, now: Long): Boolean {
        val firstTime = !discovered
        discovered = true
        totalCaught++
        if (firstCaughtAt == 0L) firstCaughtAt = now

        if (size > 0.0) {
            if (size > largestSize) largestSize = size
            // 0 은 "기록 없음"이라 첫 기록은 무조건 받는다.
            if (smallestSize == 0.0 || size < smallestSize) smallestSize = size
        }

        when (trophy) {
            Trophy.NORMAL -> trophyCount++
            Trophy.RARE -> rareTrophyCount++
            Trophy.NONE -> Unit
        }
        return firstTime
    }

    /**
     * 아이템을 소모해 도감 칸을 하나 채운다.
     *
     * @return 실제로 채웠으면 true. 이미 가득 찼으면 false 다 — 그때는 아이템을 소모하면
     *         안 되므로 부르는 쪽이 이 값을 봐야 한다.
     */
    fun register(size: Double, now: Long): Boolean {
        if (isPerfect) return false
        registeredSlots++
        registeredSizes.add(size.coerceAtLeast(0.0))
        // 등록도 발견으로 친다. 남이 준 물고기를 바로 등록하는 경우가 있다.
        if (!discovered) {
            discovered = true
            if (firstCaughtAt == 0L) firstCaughtAt = now
        }
        if (size > 0.0 && size > largestSize) largestSize = size
        return true
    }

    /**
     * 다음 [unregister] 가 되돌릴 물고기의 크기 — 마지막에 넣은 것. 등록된 것이 없으면 null.
     * 회수는 이 크기로 아이템을 **먼저 만든 뒤에** 칸을 비운다. 만들 수 없을 때 칸만 비우면 물고기가 사라진다.
     */
    fun lastRegisteredSize(): Double? =
        if (registeredSlots > 0) registeredSizes.lastOrNull() ?: 0.0 else null

    /** 등록을 하나 되돌린다. 잘못 넣은 것을 빼는 용도다. 물고기는 부르는 쪽이 [lastRegisteredSize] 로 돌려준다. */
    fun unregister(): Boolean {
        if (registeredSlots <= 0) return false
        registeredSlots--
        registeredSizes.removeLastOrNull()
        return true
    }

    // --- 보상 수령 기록 -------------------------------------------------------------

    fun hasClaimed(key: String): Boolean = key in claimed

    /** @return 이번에 처음 받는 것이면 true. */
    fun claim(key: String): Boolean = claimed.add(key)

    fun save(section: ConfigurationSection) {
        section.set("discovered", discovered)
        section.set("slots", registeredSlots)
        if (registeredSizes.isNotEmpty()) section.set("sizes", registeredSizes.toList())
        section.set("caught", totalCaught)
        if (firstCaughtAt > 0L) section.set("first-caught", firstCaughtAt)
        if (largestSize > 0.0) section.set("largest", largestSize)
        if (smallestSize > 0.0) section.set("smallest", smallestSize)
        if (trophyCount > 0) section.set("trophy", trophyCount)
        if (rareTrophyCount > 0) section.set("rare-trophy", rareTrophyCount)
        if (claimed.isNotEmpty()) section.set("claimed", claimed.toList())
    }

    companion object {

        fun load(fishId: String, gradeId: String, maxSlots: Int, section: ConfigurationSection): CollectionEntry {
            val entry = CollectionEntry(fishId, gradeId, maxSlots)
            entry.discovered = section.getBoolean("discovered", false)
            // 설정이나 파일이 망가져 칸 수가 최대를 넘는 경우를 막는다.
            entry.registeredSlots = section.getInt("slots", 0).coerceIn(0, maxSlots)
            entry.registeredSizes.addAll(section.getDoubleList("sizes").take(entry.registeredSlots))
            entry.totalCaught = section.getInt("caught", 0).coerceAtLeast(0)
            entry.firstCaughtAt = section.getLong("first-caught", 0L)
            entry.largestSize = section.getDouble("largest", 0.0).coerceAtLeast(0.0)
            entry.smallestSize = section.getDouble("smallest", 0.0).coerceAtLeast(0.0)
            entry.trophyCount = section.getInt("trophy", 0).coerceAtLeast(0)
            entry.rareTrophyCount = section.getInt("rare-trophy", 0).coerceAtLeast(0)
            entry.claimed.addAll(section.getStringList("claimed"))
            return entry
        }
    }
}

/**
 * 한 사람의 도감 전체.
 *
 * 점수 계산이 여기 있다 — 랭킹이 이 값으로 줄을 세운다.
 */
class Collection(private val entries: MutableMap<String, CollectionEntry> = LinkedHashMap()) {

    fun of(fishId: String): CollectionEntry? = entries[fishId.lowercase()]

    fun all(): kotlin.collections.Collection<CollectionEntry> = entries.values

    fun put(entry: CollectionEntry) {
        entries[entry.fishId.lowercase()] = entry
    }

    fun getOrCreate(fishId: String, gradeId: String, maxSlots: Int): CollectionEntry =
        entries.getOrPut(fishId.lowercase()) { CollectionEntry(fishId.lowercase(), gradeId, maxSlots) }

    val totalRegisteredSlots: Int get() = entries.values.sumOf { it.registeredSlots }
    val perfectCount: Int get() = entries.values.count { it.isPerfect }
    val trophyCount: Int get() = entries.values.sumOf { it.trophyCount }
    val rareTrophyCount: Int get() = entries.values.sumOf { it.rareTrophyCount }
    val discoveredCount: Int get() = entries.values.count { it.discovered }

    /**
     * 도감 점수. 랭킹이 이 값으로 줄을 세운다.
     *
     * 가중치가 다른 이유가 있다 — 칸을 채우는 것은 꾸준함, 퍼펙트는 한 물고기를 파고든 것,
     * 트로피는 운과 실력이다. 전부 1점이면 흔한 물고기를 잔뜩 등록한 사람이 이긴다.
     */
    fun score(weights: ScoreWeights = ScoreWeights.DEFAULT): Long =
        totalRegisteredSlots * weights.slot +
            perfectCount * weights.perfect +
            trophyCount * weights.trophy +
            rareTrophyCount * weights.rareTrophy

    /** 이 등급의 모든 물고기를 한 칸 이상 등록했는지. */
    fun isGradeAllRegistered(gradeId: String, fishIds: List<String>): Boolean =
        fishIds.isNotEmpty() && fishIds.all { of(it)?.isRegistered == true }

    /** 이 등급의 모든 물고기가 퍼펙트인지. */
    fun isGradePerfect(gradeId: String, fishIds: List<String>): Boolean =
        fishIds.isNotEmpty() && fishIds.all { of(it)?.isPerfect == true }

    /** 전체 물고기가 퍼펙트인지. */
    fun isComplete(allFishIds: List<String>): Boolean =
        allFishIds.isNotEmpty() && allFishIds.all { of(it)?.isPerfect == true }
}

/** 도감 점수의 가중치. `collections.yml` 에서 조절한다. */
data class ScoreWeights(
    val slot: Long,
    val perfect: Long,
    val trophy: Long,
    val rareTrophy: Long,
) {
    companion object {
        /** 2세대 값 그대로. */
        val DEFAULT = ScoreWeights(slot = 1, perfect = 5, trophy = 10, rareTrophy = 25)

        fun load(section: ConfigurationSection?): ScoreWeights {
            if (section == null) return DEFAULT
            return ScoreWeights(
                slot = section.getLong("slot", DEFAULT.slot),
                perfect = section.getLong("perfect", DEFAULT.perfect),
                trophy = section.getLong("trophy", DEFAULT.trophy),
                rareTrophy = section.getLong("rare-trophy", DEFAULT.rareTrophy),
            )
        }
    }
}
