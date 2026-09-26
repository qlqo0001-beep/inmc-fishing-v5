package com.inmc.fishing.gui

import com.inmc.fishing.Fishing
import com.inmc.fishing.bait.Bait
import kr.inmc.core.item.StoredItem
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/** 미끼 목록. */
class BaitListMenu(
    fishing: Fishing,
    viewer: Player,
) : DefinitionListMenu<Bait>(fishing, viewer, "<dark_gray>낚시 — 미끼</dark_gray>") {

    override val what: String get() = "미끼"

    override fun listAll(): List<Bait> = fishing.baits.all()

    override fun exists(id: String): Boolean = fishing.baits.exists(id)

    override fun delete(id: String) {
        fishing.baits.remove(id)
    }

    override fun idOf(value: Bait): String = value.id

    override fun itemOf(value: Bait): StoredItem = value.item

    override fun summary(value: Bait): List<String> =
        value.describe(fishing.grades.ordered.map { it.id })
            .ifEmpty { listOf("<dark_gray>효과 없음</dark_gray>") }

    override fun openEdit(value: Bait) = BaitEditMenu(fishing, viewer, value.id).open(viewer)

    override fun create(id: String, stack: ItemStack): Bait {
        val bait = Bait(id = id, item = fishing.itemResolver.capture(stack))
        fishing.baits.put(bait)
        return bait
    }
}
