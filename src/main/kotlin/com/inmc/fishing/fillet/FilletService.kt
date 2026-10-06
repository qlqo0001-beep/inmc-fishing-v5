package com.inmc.fishing.fillet

import com.inmc.fishing.Fishing
import com.inmc.fishing.fish.FishStamp
import com.inmc.fishing.player.Angler
import com.inmc.fishing.util.Ph
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * 손질을 걸고 결과를 내준다.
 *
 * **결과물은 등급과 트로피 여부로만 찾는다** ([FilletItemRegistry.find]). 물고기마다 결과물을
 * 만들게 하면 관리자가 물고기를 하나 추가할 때마다 결과물도 만들어야 하고, 빠뜨리면 손질했는데
 * 아무것도 안 나온다.
 */
class FilletService(private val fishing: Fishing) {

    /**
     * 손에 든 물고기를 손질대에 건다.
     *
     * **아이템을 먼저 없앤 뒤에 건다.** 반대로 하면 슬롯이 가득 찼을 때 물고기가 사라진다.
     *
     * @return 걸었으면 슬롯 번호. 못 걸었으면 -1.
     */
    fun submit(player: Player, angler: Angler, stack: ItemStack, amount: Int, quiet: Boolean = false): Int {
        val stamp = FishStamp.read(stack) ?: return -1
        val count = amount.coerceIn(1, stack.amount)

        if (!angler.bench.hasRoom()) {
            if (!quiet) fishing.messages.send(player, "fillet-no-room")
            return -1
        }

        val index = angler.bench.submit(
            fishId = stamp.fishId,
            gradeId = stamp.gradeId,
            trophy = stamp.trophy,
            count = count,
            now = System.currentTimeMillis(),
            size = stamp.size,
        )
        if (index < 0) {
            if (!quiet) fishing.messages.send(player, "fillet-no-room")
            return -1
        }

        stack.amount = stack.amount - count
        fishing.anglers.markDirty(angler)

        if (!quiet) {
            val slot = angler.bench.slotAt(index)
            fishing.messages.send(
                player,
                "fillet-started",
                Ph.of()
                    .fish(fishing.fish.get(stamp.fishId)?.label() ?: stamp.fishId)
                    .seconds((slot?.totalSeconds ?: 0).toLong()),
            )
        }
        return index
    }

    /**
     * 다 된 슬롯의 결과물을 준다.
     *
     * 인벤토리가 가득 차면 **바닥에 떨어뜨린다.** 여기서 되돌릴 곳이 없다 — 슬롯은 이미
     * 비워졌고, 손질은 시간이 걸리는 작업이라 "나중에 다시 받으세요"가 성립하지 않는다.
     */
    fun deliver(player: Player, slot: FilletSlot) {
        val definition = fishing.fillets.find(slot.gradeId, slot.trophy)
        val amount = fishing.config.fillet.yieldFor(slot.trophy, slot.count)

        if (definition == null) {
            fishing.logger.warning(
                "손질 결과물이 없습니다: 등급 " + slot.gradeId + " · " +
                    FilletItem.token(slot.trophy) + " — fillet-items.yml 을 확인하세요",
            )
            return
        }

        val stack = fishing.fillets.stack(definition, amount) ?: return
        for (left in player.inventory.addItem(stack).values) {
            player.world.dropItem(player.location, left)
        }

        fishing.messages.send(
            player,
            "fillet-done",
            Ph.of().fish(definition.item.label()).amount(amount),
        )
    }
}
