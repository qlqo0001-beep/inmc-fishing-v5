package com.inmc.fishing.gui

import com.inmc.fishing.Fishing
import com.inmc.fishing.fillet.FilletSlot
import com.inmc.fishing.fish.FishLore
import com.inmc.fishing.fish.FishStamp
import com.inmc.fishing.fish.Trophy
import com.inmc.fishing.player.Angler
import kr.inmc.core.gui.Icon
import kr.inmc.core.gui.Paging
import kr.inmc.core.util.Text
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * 손질대.
 *
 * 슬롯마다 하나씩 걸고 시간이 지나면 생선살이 나온다. **다 된 것은 틱커가 알아서 준다** —
 * 이 화면을 열어두고 기다릴 필요가 없다. 화면은 무엇이 얼마나 남았는지를 보여줄 뿐이다.
 */
class FilletMenu(
    fishing: Fishing,
    private val viewer: Player,
    private val angler: Angler,
) : Menu(fishing, SIZE, Text.renderFlat("<dark_gray>낚시 — 손질대</dark_gray>")) {

    override fun draw() {
        clear()
        fillEmpty(Icon.EDGE)

        val now = System.currentTimeMillis()
        for (index in 0 until angler.bench.slotCount) {
            if (index >= SLOT_POSITIONS.size) break
            val position = SLOT_POSITIONS[index]
            val slot = angler.bench.slotAt(index)

            if (slot == null) {
                set(position, emptySlot()) { submit() }
            } else {
                set(position, workingSlot(slot, now)) { cancel(index) }
            }
        }
        // 칸이 더 있을 수 있다는 것을 보여준다 (2세대 `locked-slot`). 관리자가 늘려 준다.
        SLOT_POSITIONS.getOrNull(angler.bench.slotCount)?.let {
            set(it, Icon.of(Material.BARRIER, "<dark_gray>잠긴 칸</dark_gray>", listOf("<gray>관리자가 칸을 늘려 줄 수 있습니다.</gray>")))
        }

        set(SLOT_SUBMIT, submitButton()) { submit() }
        mainButton(SLOT_MAIN, viewer)
        set(Paging.SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }

    private fun emptySlot() = Icon.of(
        Material.LIGHT_GRAY_STAINED_GLASS_PANE,
        "<gray>빈 자리</gray>",
        listOf("<yellow>▶ 클릭: 손에 든 물고기 걸기</yellow>"),
    )

    private fun workingSlot(slot: FilletSlot, now: Long): ItemStack {
        val fish = fishing.fish.get(slot.fishId)
        val left = slot.remainingSeconds(now)
        val icon = fish?.let { fishing.itemResolver.icon(it.item).stack.clone() } ?: ItemStack(Material.COD)

        return Icon.annotate(
            icon,
            "<white>" + (fish?.label() ?: slot.fishId) + "</white>",
            buildList {
                add("<gray>수량: <white>" + slot.count + "</white></gray>")
                when (slot.trophy) {
                    Trophy.NORMAL -> add("<gold>★ 트로피</gold>")
                    Trophy.RARE -> add("<light_purple>★★ 레어 트로피</light_purple>")
                    Trophy.NONE -> Unit
                }
                add(bar(slot.progress(now)))
                add("<gray>남은 시간: <white>" + (left / 60) + "분 " + (left % 60) + "초</white></gray>")
                add("")
                add("<red>▶ 클릭: 취소 (재료를 돌려받습니다)</red>")
            },
        )
    }

    private fun bar(ratio: Double): String {
        val filled = (ratio.coerceIn(0.0, 1.0) * BAR_WIDTH).toInt()
        return "<green>" + "|".repeat(filled) + "<dark_gray>" + "|".repeat(BAR_WIDTH - filled)
    }

    private fun submitButton(): ItemStack {
        val hand = viewer.inventory.itemInMainHand
        val stamp = FishStamp.read(hand)
        if (stamp == null) {
            return Icon.of(
                Material.BARRIER,
                "<red>손질하려면 물고기를 손에 드세요</red>",
                listOf("<gray>낚은 물고기만 손질할 수 있습니다.</gray>"),
            )
        }
        val seconds = fishing.config.fillet.secondsFor(stamp.gradeId, stamp.trophy, hand.amount)
        return Icon.of(
            Material.SHEARS,
            "<green>손에 든 것 전부 손질</green>",
            listOf(
                "<gray>대상: <white>" + (fishing.fish.get(stamp.fishId)?.label() ?: stamp.fishId) +
                    "</white> x" + hand.amount + "</gray>",
                "<gray>예상 시간: <white>" + seconds + "초</white></gray>",
                "<gray>나오는 개수: <white>" +
                    fishing.config.fillet.yieldFor(stamp.trophy, hand.amount) + "</white></gray>",
            ),
        )
    }

    private fun submit() {
        val hand = viewer.inventory.itemInMainHand
        if (FishStamp.read(hand) == null) return
        fishing.fillet.submit(viewer, angler, hand, hand.amount)
        refresh()
    }

    /**
     * 취소하면 **재료를 돌려준다.**
     *
     * 돌려주지 않으면 잘못 건 한 번이 물고기를 통째로 삼킨다. 시간을 이미 쓴 것은 맞지만
     * 그 대가로 재료까지 가져갈 이유는 없다.
     */
    private fun cancel(index: Int) {
        val slot = angler.bench.cancel(index) ?: return
        val fish = fishing.fish.get(slot.fishId)

        if (fish != null) {
            val stamp = FishStamp(slot.fishId, slot.gradeId, slot.size, slot.trophy, System.currentTimeMillis())
            val stack = fishing.fish.stack(
                fish,
                slot.count,
                FishLore.of(stamp, fishing.grades[slot.gradeId]?.tag().orEmpty()),
            )
            if (stack != null) {
                stamp.applyTo(stack)
                for (left in viewer.inventory.addItem(stack).values) {
                    viewer.world.dropItem(viewer.location, left)
                }
            }
        }

        fishing.anglers.markDirty(angler)
        refresh()
    }

    private companion object {
        /** 6줄 — 사람마다 칸 수가 다르고(최대 35) 2세대와 같은 배치를 쓴다. */
        const val SIZE = 54
        const val SLOT_SUBMIT = 49
        const val SLOT_MAIN = 52
        const val BAR_WIDTH = 10

        /** 2세대 `FilletGui.SLOT_POS` 그대로. 줄마다 양 끝을 비워 테두리처럼 보이게 한다. */
        val SLOT_POSITIONS = listOf(
            1, 2, 3, 4, 5, 6, 7, 10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43,
        )
    }
}
