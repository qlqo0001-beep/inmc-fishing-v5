package com.inmc.fishing.gui

import com.inmc.fishing.Fishing
import com.inmc.fishing.fillet.FilletItem
import com.inmc.fishing.fish.Trophy
import kr.inmc.core.gui.Editors
import kr.inmc.core.gui.Icon
import kr.inmc.core.gui.Paging
import kr.inmc.core.item.StorageMode
import kr.inmc.core.util.Text
import org.bukkit.Material
import org.bukkit.entity.Player

/**
 * 손질 결과물 하나의 설정.
 *
 * 고칠 것이 **등급과 대상 둘뿐**이다. 나머지(몇 개가 나오는지, 얼마나 걸리는지)는 물고기가
 * 아니라 손질 설정에서 오고, 그건 `config.yml` 의 `fillet` 절이다.
 */
class FilletItemEditMenu(
    fishing: Fishing,
    private val viewer: Player,
    private val id: String,
) : Menu(fishing, SIZE, Text.renderFlat("<dark_gray>생선살 — " + id + "</dark_gray>")) {

    private fun fillet(): FilletItem? = fishing.fillets.get(id)

    override fun draw() {
        clear()
        val fillet = fillet() ?: run {
            FilletItemListMenu(fishing, viewer).open(viewer)
            return
        }
        fillEmpty(Icon.EDGE)

        val gradeIds = fishing.grades.ordered.map { it.id }
        set(
            SLOT_GRADE,
            Icon.of(
                Material.NAME_TAG,
                "<yellow>등급: <white>" + gradeLabel(fillet.gradeId) + "</white></yellow>",
                Editors.pickHint,
            ),
        ) {
            if (gradeIds.isEmpty()) return@set
            // 등급은 일곱 안팎 — 고르는 화면으로(2026-10-08).
            kr.inmc.core.gui.PickMenu(
                fishing, viewer, "등급 고르기", gradeIds,
                icon = { Icon.of(Material.NAME_TAG, (if (it == fillet.gradeId) "<green>▶ " else "<yellow>") + gradeLabel(it) + "</yellow>") },
                back = { open(viewer) },
            ) { picked -> mutate(reopen = false) { it.copy(gradeId = picked) } }.show()
        }

        val trophies = Trophy.entries.toList()
        set(
            SLOT_TROPHY,
            Icon.of(
                Material.NAUTILUS_SHELL,
                "<yellow>대상: <white>" + trophyLabel(fillet.trophy) + "</white></yellow>",
                Editors.optionList(trophies, fillet.trophy) { trophyLabel(it) } +
                    listOf(
                        "",
                        "<gray>그 조합이 없으면 <white>한 단계 아래</white> 결과물이 나옵니다.</gray>",
                        "<gray>보통 칸이 비면 그 등급은 아무것도 안 나옵니다.</gray>",
                    ) + Editors.cycleHint,
            ),
        ) { event -> mutate { it.copy(trophy = Editors.cycle(event, trophies, it.trophy)) } }

        set(SLOT_MODE, modeIcon(fillet)) { mutate { it.copy(item = it.item.withMode(it.item.mode.toggle())) } }

        set(SLOT_GIVE, giveIcon()) { event ->
            val stack = fishing.fillets.stack(fillet, if (event.isShiftClick) 64 else 1)
            if (stack == null) {
                viewer.sendMessage(Text.render("<red>아이템을 만들 수 없습니다 - 연동 플러그인이 꺼져 있나요?</red>"))
                return@set
            }
            for (left in viewer.inventory.addItem(stack).values) {
                viewer.world.dropItem(viewer.location, left)
            }
        }

        set(Paging.SLOT_BACK, Icon.back()) { FilletItemListMenu(fishing, viewer).open(viewer) }
        set(Paging.SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }

    private fun gradeLabel(id: String): String = fishing.grades[id]?.displayName ?: id.uppercase()

    private fun trophyLabel(trophy: Trophy): String = when (trophy) {
        Trophy.NONE -> "보통"
        Trophy.NORMAL -> "트로피"
        Trophy.RARE -> "레어 트로피"
    }

    private fun modeIcon(fillet: FilletItem) = Icon.of(
        Material.CHEST,
        "<yellow>저장 방식: <white>" +
            (if (fillet.item.mode == StorageMode.SNAPSHOT) "스냅샷(고정)" else "동적(참조)") +
            "</white></yellow>",
        listOf(
            "<gray><white>동적</white> - 원본 플러그인에서 매번 다시 만듭니다.</gray>",
            "<gray><white>스냅샷</white> - 등록한 순간의 모습으로 고정합니다.</gray>",
            "",
            "<yellow>▶ 클릭: 전환</yellow>",
        ),
    )

    private fun giveIcon() = Icon.of(
        Material.HOPPER,
        "<green>나에게 지급</green>",
        listOf("<yellow>▶ 좌클릭: 1개  /  Shift: 64개</yellow>"),
    )

    private fun mutate(reopen: Boolean = true, change: (FilletItem) -> FilletItem) {
        val current = fillet() ?: return
        fishing.fillets.put(change(current))
        if (reopen) refresh() else open(viewer)
    }

    companion object {
        const val SIZE = 54

        const val SLOT_GRADE = 11
        const val SLOT_TROPHY = 13
        const val SLOT_MODE = 15
        const val SLOT_GIVE = 49
    }
}
