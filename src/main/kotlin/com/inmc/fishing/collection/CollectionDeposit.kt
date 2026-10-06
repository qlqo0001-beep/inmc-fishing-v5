package com.inmc.fishing.collection

import com.inmc.fishing.fish.FishStamp
import com.inmc.fishing.fish.Trophy
import com.inmc.fishing.player.Angler
import kr.inmc.core.integration.CarriedStorage
import org.bukkit.entity.Player

/**
 * 도감 등록용 물고기 찾기 — 손·인벤·배낭·어망 전부.
 *
 * 예전에는 주 손에 든 것만 등록됐고, 그게 "왜 인벤으로 바로 안 들어오지" 다음으로
 * 많이 나온 불만이었다. 이제 도감 타일을 누르면 가진 곳을 전부 훑어 1마리씩 꺼낸다.
 *
 * 순서는 **작은 것·일반부터, 트로피·레어는 나중**이다. 크기가 기록되므로 아무거나
 * 먹으면 큰 물고기나 트로피를 등록에 날리게 된다. 위치는 인벤 먼저, 그다음 배낭,
 * 마지막 어망 — 눈에 보이는 것부터 쓰고, 꺼내기 번거로운 어망은 나중에 쓴다.
 *
 * 배낭이 "열어둔 채 등록 불가"처럼 보이는 것은 클라이언트가 top 인벤토리를 하나만
 * 열어서다. 도감이 열린 순간 배낭은 닫혀(`BackpackMenu.onClose` 가 commit+release)
 * 있으므로 [CarriedStorage] 에는 정상으로 보인다. 따로 처리할 것이 없다.
 */
object CollectionDeposit {

    /**
     * 등록 후보 정렬 — 서버 없이 돈다. 테스트가 여기를 본다.
     *
     * 트로피 없는 것 먼저, 그다음 일반 트로피, 레어는 맨 나중. 같은 무리 안에서는
     * 작은 크기부터. 크기가 0 이면(기록 없음) 맨 앞에 둔다.
     */
    fun sortForDeposit(stamps: List<FishStamp>): List<FishStamp> =
        stamps.sortedWith(
            compareBy<FishStamp> { trophyRank(it.trophy) }
                .thenBy { if (it.size <= 0.0) Double.MIN_VALUE else it.size },
        )

    private fun trophyRank(trophy: Trophy): Int =
        when (trophy) {
            Trophy.NONE -> 0
            Trophy.NORMAL -> 1
            Trophy.RARE -> 2
        }

    /** 타일 로어에 보여줄 보유 수 — 인벤·배낭·어망. */
    data class Holdings(val inventory: Int, val carried: Int, val net: Int) {
        val total: Int get() = inventory + carried + net
    }

    fun count(player: Player, angler: Angler, fishId: String): Holdings {
        var inv = 0
        for (stack in player.inventory.storageContents) {
            val stamp = FishStamp.read(stack)
            if (stamp != null && stamp.fishId.equals(fishId, ignoreCase = true)) inv += stack?.amount ?: 0
        }
        val off = FishStamp.read(player.inventory.itemInOffHand)
        if (off != null && off.fishId.equals(fishId, ignoreCase = true)) inv += player.inventory.itemInOffHand.amount
        val carried = CarriedStorage.count(player) { stack ->
            FishStamp.read(stack)?.fishId.equals(fishId, ignoreCase = true)
        }
        val net = angler.net.all().count { it.fishId.equals(fishId, ignoreCase = true) }
        return Holdings(inv, carried, net)
    }

    /**
     * 1마리 꺼낸다. 어디서 꺼냈든 스탬프를 돌려준다 — 크기를 도감에 기록해야 하므로.
     * 없으면 null. 꺼낸 자리는 그 자리에서 1개 차감되고 곧바로 저장된다.
     */
    fun takeOne(player: Player, angler: Angler, fishId: String): FishStamp? {
        // --- 인벤 (저장칸 + 왼손) : 가장 작은·일반부터 ---
        val invCandidates = ArrayList<Pair<Int, FishStamp>>()
        val storage = player.inventory.storageContents
        for (i in storage.indices) {
            val stamp = FishStamp.read(storage[i])
            if (stamp != null && stamp.fishId.equals(fishId, ignoreCase = true)) invCandidates += i to stamp
        }
        val offStamp = FishStamp.read(player.inventory.itemInOffHand)
        if (offStamp != null && offStamp.fishId.equals(fishId, ignoreCase = true)) {
            invCandidates += OFFHAND to offStamp
        }
        val bestInv = invCandidates.minWithOrNull(
            compareBy<Pair<Int, FishStamp>> { trophyRank(it.second.trophy) }
                .thenBy { if (it.second.size <= 0.0) Double.MIN_VALUE else it.second.size },
        )
        if (bestInv != null) {
            return if (bestInv.first == OFFHAND) {
                val stack = player.inventory.itemInOffHand
                val stamp = FishStamp.read(stack) ?: return null
                if (stack.amount <= 1) player.inventory.setItemInOffHand(null)
                else stack.amount = stack.amount - 1
                stamp
            } else {
                val stack = player.inventory.getItem(bestInv.first) ?: return null
                val stamp = FishStamp.read(stack) ?: return null
                if (!stamp.fishId.equals(fishId, ignoreCase = true)) return null
                if (stack.amount <= 1) player.inventory.setItem(bestInv.first, null)
                else {
                    stack.amount = stack.amount - 1
                    player.inventory.setItem(bestInv.first, stack)
                }
                stamp
            }
        }

        // --- 배낭 (CarriedStorage) : 같은 순서로 가장 아까운 것 빼고 하나 ---
        val bestCarried = bestCarriedStamp(player, fishId)
        if (bestCarried != null) {
            val taken = ArrayList<org.bukkit.inventory.ItemStack>(1)
            val stamp = FishStamp.read(bestCarried)
            val removed = CarriedStorage.take(player, 1, taken) { stack ->
                FishStamp.read(stack)?.fishId.equals(fishId, ignoreCase = true)
            }
            // take 는 앞 칸부터 빼므로 "가장 작은 것"이 아닐 수 있다 — 그래도 같은
            // 물고기 1마리이므로 등록에는 문제없다. 크기만 그 빼낸 것의 것으로 기록한다.
            if (removed == 1 && stamp != null) {
                val actual = taken.firstOrNull()?.let { FishStamp.read(it) } ?: stamp
                return actual
            }
            if (removed == 1) return stamp
        }

        // --- 어망 : 작은·일반부터 하나 꺼낸다 ---
        val netBest = sortForDeposit(
            angler.net.all().filter { it.fishId.equals(fishId, ignoreCase = true) },
        ).firstOrNull() ?: return null
        if (!angler.net.remove(netBest)) return null
        return netBest
    }

    private fun bestCarriedStamp(player: Player, fishId: String): org.bukkit.inventory.ItemStack? {
        var best: org.bukkit.inventory.ItemStack? = null
        for (container in CarriedStorage.containers(player)) {
            for (stack in container.contents()) {
                val stamp = FishStamp.read(stack)
                if (stamp == null || !stamp.fishId.equals(fishId, ignoreCase = true)) continue
                val current = best?.let { FishStamp.read(it) }
                if (current == null || compareStamps(stamp, current) < 0) best = stack
            }
        }
        return best
    }

    private fun compareStamps(a: FishStamp, b: FishStamp): Int {
        val rank = trophyRank(a.trophy).compareTo(trophyRank(b.trophy))
        if (rank != 0) return rank
        val aSize = if (a.size <= 0.0) Double.MIN_VALUE else a.size
        val bSize = if (b.size <= 0.0) Double.MIN_VALUE else b.size
        return aSize.compareTo(bSize)
    }

    private const val OFFHAND = -40
}
