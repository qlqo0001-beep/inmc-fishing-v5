package com.inmc.fishing.gui

import com.inmc.fishing.Fishing
import com.inmc.fishing.potion.Potion
import kr.inmc.core.item.StoredItem
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/** 피로회복 물약 목록. */
class PotionListMenu(
    fishing: Fishing,
    viewer: Player,
) : DefinitionListMenu<Potion>(fishing, viewer, "<dark_gray>낚시 — 피로회복 물약</dark_gray>") {

    override val what: String get() = "물약"

    override fun listAll(): List<Potion> = fishing.potions.all()

    override fun exists(id: String): Boolean = fishing.potions.exists(id)

    override fun delete(id: String) {
        fishing.potions.remove(id)
    }

    override fun idOf(value: Potion): String = value.id

    override fun itemOf(value: Potion): StoredItem = value.item

    override fun summary(value: Potion): List<String> = buildList {
        add("<gray>회복량: <white>" + value.amount + "</white></gray>")
        if (value.gradeId.isNotBlank()) {
            add("<gray>등급: <white>" + (fishing.grades[value.gradeId]?.displayName ?: value.gradeId.uppercase()) + "</white></gray>")
        } else {
            add("<dark_gray>등급 없음 (지급 명령으로 못 찾음)</dark_gray>")
        }
        if (value.amount <= 0) add("<red>회복량이 0 이라 마셔도 효과가 없습니다.</red>")
    }

    override fun openEdit(value: Potion) = PotionEditMenu(fishing, viewer, value.id).open(viewer)

    override fun create(id: String, stack: ItemStack): Potion {
        val potion = Potion(id = id, item = fishing.itemResolver.capture(stack))
        fishing.potions.put(potion)
        return potion
    }
}
