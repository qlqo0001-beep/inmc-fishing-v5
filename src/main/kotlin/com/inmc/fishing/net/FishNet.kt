package com.inmc.fishing.net

import com.inmc.fishing.fish.FishStamp
import com.inmc.fishing.fish.Trophy

/** 어망을 무엇으로 줄 세울지. */
enum class NetSort(val display: String) {
    /** 최근에 잡은 것부터. */
    RECENT("최근순"),

    /** 큰 것부터. */
    SIZE("크기순"),

    /** 등급이 높은 것부터. */
    GRADE("등급순"),

    /** 종류별로 묶어서. */
    KIND("종류순");

    fun next(): NetSort = entries[(ordinal + 1) % entries.size]
}

/**
 * 어망 — 낚은 물고기를 모아두는 보관함.
 *
 * **인벤토리가 아니라 목록이다.** 물고기마다 크기·트로피·잡은 시각이 붙어 있어 같은 종류라도
 * 하나하나가 다르고, 바닐라 인벤토리에 넣으면 스택이 합쳐지면서 그 정보가 뭉개진다.
 *
 * 정렬은 화면을 그릴 때만 한다 — 저장 순서는 넣은 순서 그대로다. 그래야 정렬을 바꿔도
 * "몇 번째 칸"이 흔들리지 않는다.
 */
class FishNet(
    /** 최대 마릿수. `config.yml` 의 `net.max-size`. */
    val capacity: Int,
    private val items: MutableList<FishStamp> = ArrayList(),
) {

    val size: Int get() = items.size

    val isFull: Boolean get() = capacity > 0 && items.size >= capacity

    fun all(): List<FishStamp> = items.toList()

    /**
     * 한 마리 넣는다.
     *
     * @return 넣었으면 true. 가득 차 있으면 false 이고, 그때 아이템을 소모하면 안 된다.
     */
    fun add(stamp: FishStamp): Boolean {
        if (isFull) return false
        items.add(stamp)
        return true
    }

    /** 목록의 [index] 번째를 꺼낸다. 범위를 벗어나면 null. */
    fun removeAt(index: Int): FishStamp? =
        if (index in items.indices) items.removeAt(index) else null

    /** 같은 것을 하나 뺀다. 화면에서 고른 항목을 꺼낼 때 쓴다. */
    fun remove(stamp: FishStamp): Boolean = items.remove(stamp)

    fun clear() {
        items.clear()
    }

    /**
     * 화면에 그릴 순서.
     *
     * 등급 순서는 [gradeOrder] 가 정한다 — 등급은 설정값이라 코드가 순서를 알 수 없다.
     * 목록에 없는 등급은 맨 뒤로 보낸다.
     */
    fun sorted(sort: NetSort, gradeOrder: List<String>): List<FishStamp> {
        val rank = gradeOrder.withIndex().associate { (index, id) -> id.lowercase() to index }
        fun gradeRank(stamp: FishStamp) = rank[stamp.gradeId.lowercase()] ?: Int.MAX_VALUE

        return when (sort) {
            NetSort.RECENT -> items.sortedByDescending { it.caughtAt }
            NetSort.SIZE -> items.sortedByDescending { it.size }
            // 같은 등급 안에서는 큰 것부터. 등급만 보면 순서가 불안정하다.
            NetSort.GRADE -> items.sortedWith(compareBy(::gradeRank).thenByDescending { it.size })
            NetSort.KIND -> items.sortedWith(compareBy<FishStamp> { it.fishId }.thenByDescending { it.size })
        }
    }

    /** 트로피만. 자랑용 화면이 쓴다. */
    fun trophies(): List<FishStamp> = items.filter { it.trophy != Trophy.NONE }

    /** 이 물고기 중 가장 큰 것. */
    fun largestOf(fishId: String): FishStamp? =
        items.filter { it.fishId.equals(fishId, ignoreCase = true) }.maxByOrNull { it.size }
}
