package com.inmc.fishing.gui

import com.inmc.fishing.Fishing
import com.inmc.fishing.rod.Rod
import kr.inmc.core.item.StoredItem
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * 낚싯대 목록.
 *
 * **[com.inmc.fishing.rod.RodSource] 만 본다.** 앞으로 커스텀아이템 플러그인이 낚싯대를
 * 공급하게 되면 이 화면은 그대로 두고 공급처만 갈아끼우면 된다 — 그때는 공급처가
 * `editable = false` 라고 말하므로 만들기·지우기 버튼이 저절로 사라진다.
 */
class RodListMenu(
    fishing: Fishing,
    viewer: Player,
) : DefinitionListMenu<Rod>(fishing, viewer, "<dark_gray>낚시 — 낚싯대</dark_gray>") {

    override val what: String get() = "낚싯대"

    override val editable: Boolean get() = fishing.rods.editable

    override fun listAll(): List<Rod> = fishing.rods.all()

    override fun exists(id: String): Boolean = fishing.rods.byId(id) != null

    override fun delete(id: String) {
        fishing.rods.unregister(id)
    }

    override fun idOf(value: Rod): String = value.id

    override fun itemOf(value: Rod): StoredItem = value.item

    override fun summary(value: Rod): List<String> {
        val lines = value.describe(fishing.grades.ordered.map { it.id })
        return lines.ifEmpty { listOf("<dark_gray>보너스 없음</dark_gray>") }
    }

    override fun openEdit(value: Rod) = RodEditMenu(fishing, viewer, value.id).open(viewer)

    override fun create(id: String, stack: ItemStack): Rod {
        // 아이템 정체만 잡아두고 값은 전부 0 이다. 이어지는 편집 화면에서 채운다.
        val rod = Rod(id = id, item = fishing.itemResolver.capture(stack))
        fishing.rods.register(rod)
        return rod
    }
}
