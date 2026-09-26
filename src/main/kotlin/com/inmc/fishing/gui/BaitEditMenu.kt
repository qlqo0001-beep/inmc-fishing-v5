package com.inmc.fishing.gui

import com.inmc.fishing.Fishing
import com.inmc.fishing.bait.Bait
import kr.inmc.core.gui.Editors
import kr.inmc.core.gui.Icon
import kr.inmc.core.gui.Paging
import kr.inmc.core.item.StorageMode
import kr.inmc.core.util.Text
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent

/**
 * 미끼 하나의 설정.
 *
 * 크기 보정 두 칸은 **순서가 있다** — 퍼센트를 곱한 뒤 고정값을 더한다. 반대면 결과가 달라져서
 * 같은 숫자를 넣어도 다른 물고기가 나온다. 화면에 그 순서를 적어둔다.
 */
class BaitEditMenu(
    fishing: Fishing,
    private val viewer: Player,
    private val id: String,
) : Menu(fishing, SIZE, Text.renderFlat("<dark_gray>미끼 — " + id + "</dark_gray>")) {

    private fun bait(): Bait? = fishing.baits.get(id)

    override fun draw() {
        clear()
        val bait = bait() ?: run {
            BaitListMenu(fishing, viewer).open(viewer)
            return
        }
        fillEmpty(Icon.EDGE)

        set(
            SLOT_BONUS,
            Icon.of(
                Material.ENCHANTED_BOOK,
                "<yellow>등급 보너스</yellow>",
                bait.describe(fishing.grades.ordered.map { it.id })
                    .ifEmpty { listOf("<dark_gray>없음</dark_gray>") } +
                    listOf("", "<yellow>▶ 클릭: 등급별로 설정</yellow>"),
            ),
        ) {
            GradeBonusMenu(
                fishing,
                viewer,
                "<dark_gray>미끼 보너스 — " + id + "</dark_gray>",
                current = { bait()?.gradeBonus },
                onChange = { next -> mutate { it.copy(gradeBonus = next) } },
                back = { open(viewer) },
            ).open(viewer)
        }

        set(
            SLOT_SIZE_PERCENT,
            Editors.numberIcon(
                Material.SLIME_BALL,
                "<yellow>크기 보정 (퍼센트)</yellow>",
                bait.sizePercent,
                unit = "%",
                extra = listOf(
                    "<gray>뽑힌 크기에 곱합니다. <white>먼저</white> 적용됩니다.</gray>",
                    "<gray>키운 크기는 최대 크기를 넘을 수 있습니다.</gray>",
                ),
            ),
        ) { event ->
            nudge(event, "크기 보정 퍼센트", bait.sizePercent, -100.0, MAX_PERCENT) { v, b -> b.copy(sizePercent = v) }
        }

        set(
            SLOT_SIZE_FIXED,
            Editors.numberIcon(
                Material.GLOWSTONE_DUST,
                "<yellow>크기 보정 (고정값)</yellow>",
                bait.sizeFixed,
                unit = "cm",
                extra = listOf("<gray>퍼센트를 적용한 <white>뒤에</white> 더합니다.</gray>"),
            ),
        ) { event ->
            nudge(event, "크기 보정 고정값", bait.sizeFixed, -MAX_FIXED, MAX_FIXED) { v, b -> b.copy(sizeFixed = v) }
        }

        set(SLOT_MODE, modeIcon(bait)) { mutate { it.copy(item = it.item.withMode(it.item.mode.toggle())) } }

        set(SLOT_GIVE, giveIcon()) { event ->
            val stack = fishing.baits.stack(bait, if (event.isShiftClick) 64 else 1)
            if (stack == null) {
                viewer.sendMessage(Text.render("<red>아이템을 만들 수 없습니다 - 연동 플러그인이 꺼져 있나요?</red>"))
                return@set
            }
            for (left in viewer.inventory.addItem(stack).values) {
                viewer.world.dropItem(viewer.location, left)
            }
        }

        set(Paging.SLOT_BACK, Icon.back()) { BaitListMenu(fishing, viewer).open(viewer) }
        set(Paging.SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }

    private fun modeIcon(bait: Bait) = Icon.of(
        Material.CHEST,
        "<yellow>저장 방식: <white>" +
            (if (bait.item.mode == StorageMode.SNAPSHOT) "스냅샷(고정)" else "동적(참조)") +
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

    private fun mutate(reopen: Boolean = true, change: (Bait) -> Bait) {
        val current = bait() ?: return
        fishing.baits.put(change(current))
        if (reopen) refresh() else open(viewer)
    }

    private fun nudge(
        event: InventoryClickEvent,
        label: String,
        current: Double,
        min: Double,
        max: Double,
        apply: (Double, Bait) -> Bait,
    ) {
        if (Editors.isPrompt(event)) {
            Editors.promptDouble(fishing.prompts, viewer, label, min, max, reopen = { open(viewer) }) { value ->
                mutate(reopen = false) { apply(value, it) }
            }
            return
        }
        mutate { apply((current + Editors.step(event, 1.0)).coerceIn(min, max), it) }
    }

    companion object {
        const val SIZE = 54
        const val MAX_PERCENT = 10_000.0
        const val MAX_FIXED = 100_000.0

        const val SLOT_BONUS = 11
        const val SLOT_SIZE_PERCENT = 13
        const val SLOT_SIZE_FIXED = 15
        const val SLOT_MODE = 22
        const val SLOT_GIVE = 49
    }
}
