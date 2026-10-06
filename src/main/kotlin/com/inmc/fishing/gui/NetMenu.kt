package com.inmc.fishing.gui

import com.inmc.fishing.Fishing
import com.inmc.fishing.fish.FishLore
import com.inmc.fishing.fish.FishStamp
import com.inmc.fishing.fish.Trophy
import com.inmc.fishing.net.NetSort
import com.inmc.fishing.player.Angler
import com.inmc.fishing.util.Ph
import kr.inmc.core.gui.Icon
import kr.inmc.core.gui.Paging
import kr.inmc.core.util.Text
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * 어망.
 *
 * **인벤토리가 아니라 목록이다.** 물고기마다 크기·트로피·잡은 시각이 붙어 있어 같은 종류라도
 * 하나하나가 다르고, 바닐라 인벤토리에 넣으면 스택이 합쳐지면서 그 정보가 뭉개진다.
 *
 * 정렬은 **그릴 때만** 한다. 저장 순서는 넣은 순서 그대로라, 정렬을 바꿔도 "몇 번째 칸"이
 * 흔들리지 않는다 — 흔들리면 정렬을 누르는 순간 다른 물고기를 꺼내게 된다.
 */
class NetMenu(
    fishing: Fishing,
    private val viewer: Player,
    private val angler: Angler,
) : Menu(fishing, SIZE, Text.renderFlat("<dark_gray>낚시 — 어망</dark_gray>")) {

    private var page = 0
    private var sort = NetSort.entries.first()

    override fun draw() {
        clear()

        val gradeOrder = fishing.grades.ordered.map { it.id }
        val shown = angler.net.sorted(sort, gradeOrder)
        page = Paging.clamp(page, shown.size)

        for ((index, stamp) in Paging.slice(shown, page).withIndex()) {
            set(index, tile(stamp)) { take(stamp) }
        }

        if (page > 0) {
            set(Paging.SLOT_PREV, Icon.prevPage()) {
                page--
                refresh()
            }
        }
        if (page < Paging.pageCount(shown.size) - 1) {
            set(Paging.SLOT_NEXT, Icon.nextPage()) {
                page++
                refresh()
            }
        }

        set(SLOT_SORT, sortButton()) {
            sort = sort.next()
            page = 0
            refresh()
        }
        set(SLOT_STORE, storeButton()) { store() }
        set(SLOT_STORE_ALL, storeAllButton()) { storeAll() }
        set(SLOT_INFO, info(shown.size))
        mainButton(SLOT_MAIN, viewer)
        set(Paging.SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }

    private fun tile(stamp: FishStamp): ItemStack {
        val fish = fishing.fish.get(stamp.fishId)
        val grade = fishing.grades[stamp.gradeId]
        val icon = fish?.let { fishing.itemResolver.icon(it.item).stack.clone() }
            ?: ItemStack(Material.COD)

        return Icon.annotate(
            icon,
            "<white>" + (fish?.label() ?: stamp.fishId) + "</white>",
            buildList {
                add("<gray>등급: <white>" + (grade?.tag() ?: stamp.gradeId.uppercase()) + "</white></gray>")
                if (stamp.size > 0.0) add("<gray>크기: <white>" + stamp.size + "cm</white></gray>")
                when (stamp.trophy) {
                    Trophy.NORMAL -> add("<gold>★ 트로피</gold>")
                    Trophy.RARE -> add("<light_purple>★★ 레어 트로피</light_purple>")
                    Trophy.NONE -> Unit
                }
                add("")
                add("<yellow>▶ 클릭: 꺼내기</yellow>")
            },
        )
    }

    /** 어망에서 꺼낸다. **인벤토리에 못 넣으면 어망에 그대로 둔다** — 꺼내다 잃으면 안 된다. */
    private fun take(stamp: FishStamp) {
        val fish = fishing.fish.get(stamp.fishId)
        if (fish == null) {
            viewer.sendMessage(Text.render("<red>이 물고기는 더 이상 등록되어 있지 않습니다.</red>"))
            return
        }
        if (viewer.inventory.firstEmpty() < 0) {
            fishing.messages.send(viewer, "inventory-full")
            return
        }

        val stack = fishing.fish.stack(fish, 1, FishLore.of(stamp, fishing.grades[stamp.gradeId]?.tag().orEmpty()))
            ?: return
        stamp.applyTo(stack)
        if (!angler.net.remove(stamp)) return

        viewer.inventory.addItem(stack)
        fishing.anglers.markDirty(angler)
        fishing.messages.send(viewer, "net-taken", Ph.of().fish(fish.label()))
        refresh()
    }

    /** 가방에서 Shift+클릭한 물고기를 어망에 넣는다. 물고기가 아니면 조용히 넘긴다. */
    override fun handleClick(event: org.bukkit.event.inventory.InventoryClickEvent) {
        if (event.rawSlot >= size && event.isShiftClick) {
            event.isCancelled = true
            val stack = event.currentItem
            val moved = storeStack(stack)
            if (moved > 0 && stack != null) {
                if (stack.amount - moved <= 0) event.currentItem = null
                else stack.amount = stack.amount - moved
                fishing.anglers.markDirty(angler)
                fishing.messages.send(viewer, "net-stored", Ph.of().fish(
                    fishing.fish.get(FishStamp.read(stack)?.fishId ?: "")?.label() ?: "물고기",
                ))
                refresh()
            }
            return
        }
        super.handleClick(event)
    }

    /** 손에 든 물고기를 전부 어망에 넣는다. 가득 차면 넣은 만큼만 소모한다. */
    private fun store() {
        val hand = viewer.inventory.itemInMainHand
        val stamp = FishStamp.read(hand)
        val moved = storeStack(hand)
        if (moved == 0) {
            if (stamp == null) {
                fishing.messages.send(viewer, "collection-need-item", Ph.of().fish("물고기"))
            } else {
                fishing.messages.send(viewer, "net-full", Ph.of().amount(angler.net.capacity))
            }
            return
        }
        hand.amount = hand.amount - moved
        fishing.anglers.markDirty(angler)
        fishing.messages.send(
            viewer,
            "net-stored",
            Ph.of().fish(fishing.fish.get(stamp?.fishId ?: "")?.label() ?: "물고기"),
        )
        refresh()
    }

    /**
     * 스택 하나를 어망에 넣는다. 손·인벤토리 Shift+클릭이 같은 길이다.
     *
     * @return 넣은 마릿수. 물고기가 아니면 0.
     */
    private fun storeStack(stack: ItemStack?): Int {
        val stamp = FishStamp.read(stack) ?: return 0
        var moved = 0
        while (moved < (stack?.amount ?: 0) && angler.net.add(stamp)) moved++
        return moved
    }

    /** 가방의 물고기를 어망이 찰 때까지 넣는다. */
    private fun storeAll() {
        var moved = 0
        val inv = viewer.inventory
        for (i in 0 until inv.storageContents.size) {
            if (angler.net.isFull) break
            val stack = inv.getItem(i) ?: continue
            val n = storeStack(stack)
            if (n > 0) {
                moved += n
                if (stack.amount - n <= 0) inv.setItem(i, null)
                else stack.amount = stack.amount - n
            }
        }
        if (!angler.net.isFull) {
            val off = inv.itemInOffHand
            val n = storeStack(off)
            if (n > 0) {
                moved += n
                if (off.amount - n <= 0) inv.setItemInOffHand(null) else off.amount = off.amount - n
            }
        }
        if (moved == 0) {
            fishing.messages.send(viewer, "collection-need-item", Ph.of().fish("물고기"))
            return
        }
        fishing.anglers.markDirty(angler)
        fishing.messages.send(viewer, "net-stored-many", Ph.of().count(moved))
        refresh()
    }

    private fun sortButton() = Icon.of(
        Material.COMPARATOR,
        "<yellow>정렬: <white>" + sort.display + "</white></yellow>",
        listOf("<yellow>▶ 클릭: 다음 정렬</yellow>"),
    )

    private fun storeButton(): ItemStack {
        val hand = viewer.inventory.itemInMainHand
        val stamp = FishStamp.read(hand)
        if (stamp == null) {
            return Icon.of(
                Material.BUCKET,
                "<green>어망에 넣기</green>",
                listOf(
                    "<gray>가방에서 물고기를 Shift+클릭하세요.</gray>",
                    "<gray>낚은 물고기만 들어갑니다.</gray>",
                ),
            )
        }
        return Icon.of(
            Material.BUCKET,
            "<green>손에 든 것 넣기</green>",
            listOf("<gray>대상: <white>" + (fishing.fish.get(stamp.fishId)?.label() ?: stamp.fishId) + "</white></gray>"),
        )
    }

    private fun storeAllButton(): ItemStack = Icon.of(
        Material.WATER_BUCKET,
        "<green>가방 물고기 모두 넣기</green>",
        listOf(
            "<gray>가방·왼손에 든 물고기를 어망이 찰 때까지 넣습니다.</gray>",
            "",
            "<yellow>▶ 클릭</yellow>",
        ),
    )

    private fun info(total: Int) = Icon.of(
        Material.PAPER,
        "<aqua>어망</aqua>",
        listOf(
            "<gray>보관: <white>" + angler.net.size + "</white> / " +
                (if (angler.net.capacity > 0) angler.net.capacity.toString() else "무제한") + "</gray>",
            "<gray>트로피: <gold>" + angler.net.trophies().size + "</gold></gray>",
            "",
            "<dark_gray>" + (page + 1) + "/" + Paging.pageCount(total) + " 쪽</dark_gray>",
        ),
    )

    companion object {
        const val SIZE = 54
        const val SLOT_MAIN = 52
        const val SLOT_SORT = 45
        const val SLOT_STORE = 48
        /** 가방 물고기 모두 넣기 — 넣기(48) 옆 빈자리. 항목 칸(0~44)·이전·다음과 무충돌. */
        const val SLOT_STORE_ALL = 50
        const val SLOT_INFO = 49
    }
}
