package com.inmc.fishing.fillet

import com.inmc.fishing.fish.Trophy
import org.bukkit.configuration.ConfigurationSection

/**
 * 손질(필렛) 설정. `config.yml` 의 `fillet` 에서 온다.
 *
 * 낚은 물고기를 시간을 들여 가공물로 바꾸는 장치다. 등급이 높고 트로피일수록 오래 걸리고,
 * 그만큼 많이 나온다.
 */
data class FilletConfig(
    /** 한 마리에서 기본으로 나오는 개수. */
    val baseYield: Int = 3,
    /** 동시에 돌릴 수 있는 슬롯 수. */
    val defaultSlots: Int = 3,
    /** 여러 슬롯이 동시에 돌아가는지. false 면 한 번에 하나씩 순서대로. */
    val concurrent: Boolean = true,
    /** 등급별 기본 소요 시간(초). */
    val gradeSeconds: Map<String, Int> = emptyMap(),
    val trophyMultiplier: Double = 1.5,
    val rareTrophyMultiplier: Double = 2.0,
    /** 희귀할수록 시간이 는다. 끄면 등급 시간만 쓴다. */
    val useRarityFactor: Boolean = true,
    /** 투입한 물고기 한 마리당 더해지는 시간(초). */
    val secondsPerItem: Int = 5,
) {

    /**
     * 한 묶음을 손질하는 데 걸리는 시간(초).
     *
     * ```
     * (등급 시간 × 트로피 배수) + (마릿수 × 마리당 시간)
     * ```
     *
     * 트로피 배수를 **등급 시간에만** 곱하는 것이 의도다. 마리당 시간까지 곱하면 트로피
     * 여러 마리를 한 번에 넣었을 때 시간이 폭발한다.
     */
    fun secondsFor(gradeId: String, trophy: Trophy, count: Int): Int {
        val base = gradeSeconds[gradeId.lowercase()] ?: 0
        val scaled = if (useRarityFactor) base * multiplierFor(trophy) else base.toDouble()
        val perItem = count.coerceAtLeast(1) * secondsPerItem
        return (scaled + perItem).toInt().coerceAtLeast(1)
    }

    /** 한 묶음에서 나오는 개수. 트로피일수록 많이 나온다. */
    fun yieldFor(trophy: Trophy, count: Int): Int =
        (baseYield * multiplierFor(trophy) * count.coerceAtLeast(1)).toInt().coerceAtLeast(1)

    private fun multiplierFor(trophy: Trophy): Double = when (trophy) {
        Trophy.RARE -> rareTrophyMultiplier
        Trophy.NORMAL -> trophyMultiplier
        Trophy.NONE -> 1.0
    }

    companion object {

        fun load(section: ConfigurationSection?): FilletConfig {
            if (section == null) return FilletConfig()
            val times = HashMap<String, Int>()
            section.getConfigurationSection("grade-times")?.let { node ->
                for (key in node.getKeys(false)) times[key.lowercase()] = node.getInt(key, 0)
            }
            return FilletConfig(
                baseYield = section.getInt("base-yield", 3).coerceAtLeast(1),
                defaultSlots = section.getInt("default-slots", 3).coerceAtLeast(1),
                concurrent = section.getBoolean("concurrent-mode", true),
                gradeSeconds = times,
                trophyMultiplier = section.getDouble("trophy-multiplier", 1.5),
                rareTrophyMultiplier = section.getDouble("rare-trophy-multiplier", 2.0),
                useRarityFactor = section.getBoolean("use-rarity-factor", true),
                secondsPerItem = section.getInt("time-per-item", 5).coerceAtLeast(0),
            )
        }
    }
}

/** 손질 슬롯 하나. */
class FilletSlot(
    val fishId: String,
    val gradeId: String,
    val trophy: Trophy,
    val count: Int,
    val totalSeconds: Int,
    val startedAt: Long,
    /** 건 물고기의 크기. 취소하면 이 크기 그대로 돌려준다(2세대와 같다). 0 은 크기 없는 것. */
    val size: Double = 0.0,
) {

    fun isDone(now: Long): Boolean = now >= startedAt + totalSeconds * 1000L

    fun remainingSeconds(now: Long): Int =
        ((startedAt + totalSeconds * 1000L - now).coerceAtLeast(0L) / 1000L).toInt()

    fun progress(now: Long): Double {
        if (totalSeconds <= 0) return 1.0
        val elapsed = (now - startedAt).coerceAtLeast(0L)
        return (elapsed.toDouble() / (totalSeconds * 1000L)).coerceIn(0.0, 1.0)
    }
}

/**
 * 한 사람의 손질대.
 *
 * 슬롯이 **동시에** 도는지 **차례로** 도는지가 설정이다 ([FilletConfig.concurrent]).
 * 차례로 돌면 앞 슬롯이 끝나야 다음이 시작하므로, 시작 시각을 넣을 때 앞의 것을 봐야 한다.
 */
class FilletBench(private val config: FilletConfig, size: Int = config.defaultSlots) {

    private val slots = ArrayList<FilletSlot?>()

    init {
        repeat(size.coerceIn(1, MAX_SLOTS)) { slots.add(null) }
    }

    val slotCount: Int get() = slots.size

    /**
     * 칸 수를 바꾼다 — 2세대 `/fishing fillet setSlots`. **돌고 있는 칸은 지우지 않는다** — 줄일 때는
     * 뒤쪽의 빈 칸만 걷어낸다. 남의 생선을 사라지게 하는 것은 칸을 줄이는 것과 다른 일이다.
     *
     * @return 바뀐 뒤의 칸 수.
     */
    fun resize(target: Int): Int {
        val wanted = target.coerceIn(1, MAX_SLOTS)
        while (slots.size < wanted) slots.add(null)
        while (slots.size > wanted && slots.last() == null) slots.removeAt(slots.size - 1)
        return slots.size
    }

    fun slotAt(index: Int): FilletSlot? = slots.getOrNull(index)

    fun used(): Int = slots.count { it != null }

    fun hasRoom(): Boolean = slots.any { it == null }

    /**
     * 빈 슬롯에 넣는다.
     *
     * @return 넣은 슬롯 번호. 자리가 없으면 -1.
     */
    fun submit(fishId: String, gradeId: String, trophy: Trophy, count: Int, now: Long, size: Double = 0.0): Int {
        val index = slots.indexOfFirst { it == null }
        if (index < 0) return -1

        val seconds = config.secondsFor(gradeId, trophy, count)
        // 차례로 도는 설정이면 앞에 걸린 것들이 다 끝난 뒤에 시작한다.
        val startAt = if (config.concurrent) now else latestFinish(now)

        slots[index] = FilletSlot(fishId, gradeId, trophy, count, seconds, startAt, size)
        return index
    }

    /** 지금 돌고 있는 것들이 전부 끝나는 시각. 차례 모드의 시작 시각이 된다. */
    private fun latestFinish(now: Long): Long =
        slots.filterNotNull().maxOfOrNull { it.startedAt + it.totalSeconds * 1000L } ?: now

    /**
     * 다 된 슬롯을 거둔다.
     *
     * @return 거둔 슬롯들. 비어 있으면 아직 아무것도 안 됐다.
     */
    fun collect(now: Long): List<FilletSlot> {
        val done = ArrayList<FilletSlot>()
        for (index in slots.indices) {
            val slot = slots[index] ?: continue
            if (!slot.isDone(now)) continue
            done.add(slot)
            slots[index] = null
        }
        return done
    }

    /** 돌고 있는 것을 취소한다. 부르는 쪽이 원재료를 돌려줄지 정한다. */
    fun cancel(index: Int): FilletSlot? {
        val slot = slots.getOrNull(index) ?: return null
        slots[index] = null
        return slot
    }

    /**
     * 저장된 슬롯을 그대로 되돌린다. 디스크에서 읽을 때만 쓴다.
     *
     * [submit] 을 쓰면 시작 시각이 지금으로 다시 찍혀서 **재시작이 손질 시간을 늘리는
     * 꼼수**가 된다. 그래서 되돌리는 길을 따로 둔다.
     */
    fun restore(index: Int, slot: FilletSlot) {
        while (slots.size <= index) slots.add(null)
        slots[index] = slot
    }

    companion object {
        /** 칸 수 상한. 2세대 `setSlots` 의 상한과 같다. */
        const val MAX_SLOTS = 35
    }
}

/**
 * [FilletSlot] 을 설정 파일에 적고 되읽는다.
 *
 * 시작 시각을 **절대 시각으로** 적는다. 남은 시간을 적으면 서버가 꺼져 있던 동안 손질이
 * 멈춘 셈이 되고, 그러면 재시작이 손질 시간을 늘리는 꼼수가 된다.
 */
object FilletSlotYaml {

    fun save(slot: FilletSlot, section: ConfigurationSection) {
        section.set("fish", slot.fishId)
        section.set("grade", slot.gradeId)
        section.set("trophy", slot.trophy.name)
        section.set("count", slot.count)
        section.set("seconds", slot.totalSeconds)
        section.set("started-at", slot.startedAt)
        if (slot.size > 0.0) section.set("size", slot.size)
    }

    fun load(section: ConfigurationSection): FilletSlot? {
        val fishId = section.getString("fish")?.takeIf { it.isNotBlank() } ?: return null
        return FilletSlot(
            fishId = fishId.lowercase(),
            gradeId = section.getString("grade").orEmpty().lowercase(),
            trophy = runCatching { Trophy.valueOf(section.getString("trophy").orEmpty()) }
                .getOrDefault(Trophy.NONE),
            count = section.getInt("count", 1).coerceAtLeast(1),
            totalSeconds = section.getInt("seconds", 0).coerceAtLeast(0),
            startedAt = section.getLong("started-at", 0L),
            size = section.getDouble("size", 0.0).coerceAtLeast(0.0),
        )
    }
}
