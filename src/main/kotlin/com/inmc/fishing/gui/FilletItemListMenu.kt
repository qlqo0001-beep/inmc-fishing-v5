package com.inmc.fishing.gui

import com.inmc.fishing.Fishing
import com.inmc.fishing.fillet.FilletItem
import com.inmc.fishing.fish.Trophy
import kr.inmc.core.gui.Editors
import kr.inmc.core.gui.Icon
import kr.inmc.core.item.StoredItem
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * 손질 결과물 목록.
 *
 * 등급 × 트로피 조합이라 금방 스물한 칸이 된다. 그래서 목록에 **어느 조합이 비어 있는지**를
 * 같이 보여준다 — 비어 있으면 손질했을 때 한 단계 아래 결과물이 나오고, 그것도 없으면
 * 아무것도 안 나온다.
 */
class FilletItemListMenu(
    fishing: Fishing,
    viewer: Player,
) : DefinitionListMenu<FilletItem>(fishing, viewer, "<dark_gray>낚시 — 생선살</dark_gray>") {

    /** null 이면 전체. */
    private var gradeFilter: String? = null

    override val what: String get() = "생선살"

    override fun listAll(): List<FilletItem> = fishing.fillets.all()

    override fun exists(id: String): Boolean = fishing.fillets.exists(id)

    override fun delete(id: String) {
        fishing.fillets.remove(id)
    }

    override fun idOf(value: FilletItem): String = value.id

    override fun itemOf(value: FilletItem): StoredItem = value.item

    override fun visible(): List<FilletItem> =
        gradeFilter?.let { grade -> fishing.fillets.all().filter { it.gradeId == grade } }
            ?: fishing.fillets.all()

    override fun summary(value: FilletItem): List<String> = buildList {
        add("<gray>등급: <white>" + gradeLabel(value.gradeId) + "</white></gray>")
        add("<gray>대상: <white>" + trophyLabel(value.trophy) + "</white></gray>")
        if (fishing.grades[value.gradeId] == null) {
            add("<red>등급표에 없는 등급입니다 - 이 결과물은 안 나옵니다.</red>")
        }
    }

    override fun openEdit(value: FilletItem) = FilletItemEditMenu(fishing, viewer, value.id).open(viewer)

    override fun create(id: String, stack: ItemStack): FilletItem {
        val fillet = FilletItem(
            id = id,
            item = fishing.itemResolver.capture(stack),
            gradeId = gradeFilter ?: fishing.grades.ordered.firstOrNull()?.id.orEmpty(),
            trophy = Trophy.NONE,
        )
        fishing.fillets.put(fillet)
        return fillet
    }

    override fun decorate() {
        val options = listOf<String?>(null) + fishing.grades.ordered.map { it.id }
        set(
            SLOT_FILTER,
            Icon.of(
                Material.HOPPER,
                "<yellow>등급 필터: <white>" + (gradeFilter?.let { gradeLabel(it) } ?: "전체") + "</white></yellow>",
                Editors.optionList(options, gradeFilter) { it?.let { id -> gradeLabel(id) } ?: "전체" } +
                    missingReport() + Editors.cycleHint,
            ),
        ) { event ->
            gradeFilter = Editors.cycle(event, options, gradeFilter)
            page = 0
            refresh()
        }
    }

    /** 비어 있는 조합. 손질했는데 아무것도 안 나오는 자리를 미리 보여준다. */
    private fun missingReport(): List<String> {
        val missing = fishing.grades.ordered.flatMap { grade ->
            Trophy.entries.filter { trophy ->
                fishing.fillets.all().none { it.gradeId == grade.id && it.trophy == trophy }
            }.map { grade.displayName + " " + trophyLabel(it) }
        }
        if (missing.isEmpty()) return listOf("", "<green>모든 조합이 채워져 있습니다.</green>")
        return listOf("", "<yellow>비어 있는 조합 " + missing.size + "개</yellow>") +
            missing.take(6).map { "<dark_gray>- " + it + "</dark_gray>" } +
            if (missing.size > 6) listOf("<dark_gray>...</dark_gray>") else emptyList()
    }

    private fun gradeLabel(id: String): String = fishing.grades[id]?.displayName ?: id.uppercase()

    private fun trophyLabel(trophy: Trophy): String = when (trophy) {
        Trophy.NONE -> "보통"
        Trophy.NORMAL -> "트로피"
        Trophy.RARE -> "레어 트로피"
    }
}
