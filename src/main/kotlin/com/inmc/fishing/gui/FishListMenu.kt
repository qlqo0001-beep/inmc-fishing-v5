package com.inmc.fishing.gui

import com.inmc.fishing.Fishing
import com.inmc.fishing.fish.Fish
import kr.inmc.core.gui.Editors
import kr.inmc.core.gui.Icon
import kr.inmc.core.item.StoredItem
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * 물고기 목록.
 *
 * 등급이 **필드**라 여기서 필터로 좁힌다. 2세대는 등급마다 파일이 따로였고, 그래서 물고기의
 * 등급을 바꾸는 것이 파일 사이로 옮기는 일이었다 — 화면으로는 할 수 없었다.
 */
class FishListMenu(
    fishing: Fishing,
    viewer: Player,
) : DefinitionListMenu<Fish>(fishing, viewer, "<dark_gray>낚시 — 물고기</dark_gray>") {

    /** null 이면 전체. */
    private var gradeFilter: String? = null

    override val what: String get() = "물고기"

    override fun listAll(): List<Fish> = fishing.fish.all()

    override fun exists(id: String): Boolean = fishing.fish.exists(id)

    override fun delete(id: String) {
        fishing.fish.remove(id)
    }

    override fun idOf(value: Fish): String = value.id

    override fun itemOf(value: Fish): StoredItem = value.item

    override fun visible(): List<Fish> =
        gradeFilter?.let { grade -> fishing.fish.ofGrade(grade) } ?: fishing.fish.all()

    /**
     * 등급표가 비어 있으면 등록을 막는다.
     *
     * 등급 없는 물고기는 어느 묶음에도 들어가지 않아 **영원히 추첨되지 않는다.** 등록만 되고
     * 안 나오는 것이 제일 찾기 어려운 종류의 고장이다.
     */
    override fun blockedReason(): String? =
        if (fishing.grades.isEmpty) "grades.yml 에 등급이 하나도 없습니다" else null

    override fun summary(value: Fish): List<String> {
        val grade = fishing.grades[value.gradeId]
        return buildList {
            add("<gray>등급: <white>" + (grade?.tag() ?: value.gradeId) + "</white></gray>")
            add("<gray>가중치: <white>" + value.weight + "</white></gray>")
            if (value.hasSize) {
                add(
                    "<gray>크기: <white>" + value.size.min + " ~ " + value.size.max +
                        "cm</white> (평균 " + value.size.avg + ")</gray>",
                )
            } else {
                add("<dark_gray>크기 없음 (쓰레기)</dark_gray>")
            }
            if (value.isFatiguePotion) {
                add("<gray>피로 회복: <white>" + value.fatigueRecovery + "</white></gray>")
            }
            if (grade == null) {
                add("<red>등급표에 없는 등급입니다 - 이 물고기는 안 나옵니다.</red>")
            }
        }
    }

    override fun openEdit(value: Fish) = FishEditMenu(fishing, viewer, value.id).open(viewer)

    override fun create(id: String, stack: ItemStack): Fish {
        // 필터로 등급을 좁혀놓고 등록하면 그 등급으로 들어간다. 한 등급을 몰아서 만들 때 편하다.
        val grade = gradeFilter ?: fishing.grades.ordered.firstOrNull()?.id.orEmpty()
        return fishing.fish.register(id, grade, stack)
    }

    override fun decorate() {
        val options = listOf<String?>(null) + fishing.grades.ordered.map { it.id }
        set(
            SLOT_FILTER,
            Icon.of(
                Material.HOPPER,
                "<yellow>등급 필터: <white>" + label(gradeFilter) + "</white></yellow>",
                Editors.optionList(options, gradeFilter) { label(it) } +
                    listOf("", "<gray>보이는 항목 <white>" + visible().size + "</white>개</gray>") +
                    Editors.cycleHint,
            ),
        ) { event ->
            gradeFilter = Editors.cycle(event, options, gradeFilter)
            page = 0
            refresh()
        }
    }

    private fun label(id: String?): String =
        if (id == null) "전체" else fishing.grades[id]?.displayName ?: id.uppercase()
}
