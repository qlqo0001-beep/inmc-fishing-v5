package com.inmc.fishing.gui

import com.inmc.fishing.Fishing
import com.inmc.fishing.potion.Potion
import kr.inmc.core.gui.Editors
import kr.inmc.core.gui.Icon
import kr.inmc.core.gui.Paging
import kr.inmc.core.item.StorageMode
import kr.inmc.core.util.Text
import org.bukkit.Material
import org.bukkit.entity.Player

/**
 * 피로회복 물약 하나의 설정.
 *
 * 회복량의 기준을 화면에 같이 적는다 — `2000` 이 큰 값인지 작은 값인지는 기본 최대 피로도와
 * 등급별 소모량을 봐야 알 수 있고, 그 둘은 다른 파일에 있다.
 */
class PotionEditMenu(
    fishing: Fishing,
    private val viewer: Player,
    private val id: String,
) : Menu(fishing, SIZE, Text.renderFlat("<dark_gray>물약 — " + id + "</dark_gray>")) {

    private fun potion(): Potion? = fishing.potions.get(id)

    override fun draw() {
        clear()
        val potion = potion() ?: run {
            PotionListMenu(fishing, viewer).open(viewer)
            return
        }
        fillEmpty(Icon.EDGE)

        val fatigue = fishing.config.fatigue
        set(
            SLOT_AMOUNT,
            Editors.intIcon(
                Material.POTION,
                "<yellow>회복량</yellow>",
                potion.amount,
                extra = listOf(
                    "<gray>자연 회복 한계 <white>" + fatigue.default + "</white> · 절대 상한 <white>" +
                        fatigue.max + "</white></gray>",
                    "<gray>물약은 <white>절대 상한</white>까지 채울 수 있습니다.</gray>",
                ),
                stepLabel = "100",
            ),
        ) { event ->
            if (Editors.isPrompt(event)) {
                Editors.promptInt(
                    fishing.prompts, viewer, "회복량", 0, fatigue.max,
                    reopen = { open(viewer) },
                ) { value -> mutate(reopen = false) { it.copy(amount = value) } }
                return@set
            }
            val next = (potion.amount + Editors.step(event, 100)).coerceIn(0, fatigue.max)
            mutate { it.copy(amount = next) }
        }

        val gradeIds = listOf("") + fishing.grades.ordered.map { it.id }
        set(
            SLOT_GRADE,
            Icon.of(
                Material.NAME_TAG,
                "<yellow>연관 등급: <white>" + gradeLabel(potion.gradeId) + "</white></yellow>",
                Editors.optionList(gradeIds, potion.gradeId) { gradeLabel(it) } +
                    listOf("", "<gray>지급 명령이 등급으로 이 물약을 찾습니다.</gray>") + Editors.cycleHint,
            ),
        ) { event -> mutate { it.copy(gradeId = Editors.cycle(event, gradeIds, it.gradeId)) } }

        set(SLOT_MODE, modeIcon(potion)) { mutate { it.copy(item = it.item.withMode(it.item.mode.toggle())) } }

        set(SLOT_GIVE, giveIcon()) { event ->
            val stack = fishing.potions.stack(potion, if (event.isShiftClick) 64 else 1)
            if (stack == null) {
                viewer.sendMessage(Text.render("<red>아이템을 만들 수 없습니다 - 연동 플러그인이 꺼져 있나요?</red>"))
                return@set
            }
            for (left in viewer.inventory.addItem(stack).values) {
                viewer.world.dropItem(viewer.location, left)
            }
        }

        set(Paging.SLOT_BACK, Icon.back()) { PotionListMenu(fishing, viewer).open(viewer) }
        set(Paging.SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }

    private fun gradeLabel(id: String): String = when {
        id.isBlank() -> "없음"
        else -> fishing.grades[id]?.displayName ?: id.uppercase()
    }

    private fun modeIcon(potion: Potion) = Icon.of(
        Material.CHEST,
        "<yellow>저장 방식: <white>" +
            (if (potion.item.mode == StorageMode.SNAPSHOT) "스냅샷(고정)" else "동적(참조)") +
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

    private fun mutate(reopen: Boolean = true, change: (Potion) -> Potion) {
        val current = potion() ?: return
        fishing.potions.put(change(current))
        if (reopen) refresh() else open(viewer)
    }

    companion object {
        const val SIZE = 54

        const val SLOT_AMOUNT = 11
        const val SLOT_GRADE = 13
        const val SLOT_MODE = 15
        const val SLOT_GIVE = 49
    }
}
