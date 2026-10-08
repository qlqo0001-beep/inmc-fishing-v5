package com.inmc.fishing.gui

import com.inmc.fishing.Fishing
import com.inmc.fishing.fish.Fish
import kr.inmc.core.gui.Editors
import kr.inmc.core.gui.Icon
import kr.inmc.core.gui.Paging
import kr.inmc.core.item.StorageMode
import kr.inmc.core.util.Text
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent

/**
 * 물고기 한 마리의 설정.
 *
 * **정의가 아니라 id 를 들고 있다.** [Fish] 는 불변이라 한 칸을 고칠 때마다 새 객체가 되는데,
 * 화면이 옛 객체를 붙들고 있으면 두 번째 편집이 첫 번째를 지운다. 매번 레지스트리에서 다시
 * 찾으면 그 문제가 생기지 않는다.
 */
class FishEditMenu(
    fishing: Fishing,
    private val viewer: Player,
    private val id: String,
) : Menu(fishing, SIZE, Text.renderFlat("<dark_gray>물고기 — " + id + "</dark_gray>")) {

    private fun fish(): Fish? = fishing.fish.get(id)

    override fun draw() {
        clear()
        val fish = fish() ?: run {
            // 다른 관리자가 지웠다. 빈 화면을 보여주느니 목록으로 돌린다.
            FishListMenu(fishing, viewer).open(viewer)
            return
        }
        fillEmpty(Icon.EDGE)

        val gradeIds = fishing.grades.ordered.map { it.id }
        set(
            SLOT_GRADE,
            Icon.of(
                Material.NAME_TAG,
                "<yellow>등급: <white>" + gradeLabel(fish.gradeId) + "</white></yellow>",
                Editors.pickHint,
            ),
        ) {
            if (gradeIds.isEmpty()) return@set
            // 등급은 일곱 안팎 — 고르는 화면으로(2026-10-08).
            kr.inmc.core.gui.PickMenu(
                fishing, viewer, "등급 고르기", gradeIds,
                icon = { Icon.of(Material.NAME_TAG, (if (it == fish.gradeId) "<green>▶ " else "<yellow>") + gradeLabel(it) + "</yellow>") },
                back = { open(viewer) },
            ) { picked -> mutate(reopen = false) { it.copy(gradeId = picked) } }.show()
        }

        set(
            SLOT_WEIGHT,
            Editors.intIcon(
                Material.STRING,
                "<yellow>추첨 가중치</yellow>",
                fish.weight,
                extra = listOf(
                    "<gray>같은 등급 안에서의 비율입니다.</gray>",
                    "<gray>0 이면 뽑히지 않습니다.</gray>",
                ),
            ),
        ) { event -> nudgeInt(event, "추첨 가중치", fish.weight, 0, 100_000) { v, f -> f.copy(weight = v) } }

        set(
            SLOT_MIN,
            Editors.numberIcon(
                Material.LIGHT_GRAY_DYE,
                "<yellow>최소 크기</yellow>",
                fish.size.min,
                unit = "cm",
                extra = listOf("<gray>셋 다 0 이면 크기 없는 것(쓰레기)이 됩니다.</gray>"),
            ),
        ) { event -> nudgeSize(event, "최소 크기", fish.size.min) { v, f -> f.copy(size = f.size.copy(min = v)) } }

        set(
            SLOT_MAX,
            Editors.numberIcon(Material.YELLOW_DYE, "<yellow>최대 크기</yellow>", fish.size.max, unit = "cm"),
        ) { event -> nudgeSize(event, "최대 크기", fish.size.max) { v, f -> f.copy(size = f.size.copy(max = v)) } }

        set(
            SLOT_AVG,
            Editors.numberIcon(
                Material.ORANGE_DYE,
                "<yellow>평균 크기</yellow>",
                fish.size.avg,
                unit = "cm",
                extra = sizeWarning(fish),
            ),
        ) { event -> nudgeSize(event, "평균 크기", fish.size.avg) { v, f -> f.copy(size = f.size.copy(avg = v)) } }

        set(
            SLOT_DOUBLE,
            Icon.of(
                Icon.toggleMaterial(fish.doubleEnabled),
                "<yellow>더블 보상: " + Icon.toggle(fish.doubleEnabled) + "</yellow>",
                listOf(
                    "<gray>확률에 걸리면 두 개를 줍니다.</gray>",
                    "<gray>쓰레기는 꺼두는 편이 좋습니다.</gray>",
                ),
            ),
        ) { mutate { it.copy(doubleEnabled = !it.doubleEnabled) } }

        set(
            SLOT_FATIGUE,
            Editors.intIcon(
                Material.POTION,
                "<yellow>피로 회복량</yellow>",
                fish.fatigueRecovery,
                extra = listOf("<gray>0 보다 크면 회복 물약으로 쓰입니다.</gray>"),
                stepLabel = "10",
            ),
        ) { event ->
            nudgeInt(event, "피로 회복량", fish.fatigueRecovery, 0, 1_000_000, step = 10) { v, f ->
                f.copy(fatigueRecovery = v)
            }
        }

        set(
            SLOT_SLOTS,
            Editors.intIcon(
                Material.BOOK,
                "<yellow>도감 칸 수</yellow>",
                fish.maxSlots,
                extra = listOf("<gray>다 채우면 퍼펙트가 됩니다.</gray>"),
            ),
        ) { event -> nudgeInt(event, "도감 칸 수", fish.maxSlots, 1, 64) { v, f -> f.copy(maxSlots = v) } }

        set(SLOT_MODE, modeIcon(fish)) { mutate { it.copy(item = it.item.withMode(it.item.mode.toggle())) } }

        set(SLOT_COMMANDS, commandIcon(fish)) { event ->
            if (event.isRightClick) {
                mutate { it.copy(commands = emptyList()) }
                return@set
            }
            Editors.promptText(
                fishing.prompts,
                viewer,
                "실행할 명령어",
                listOf("<gray>앞에 슬래시를 붙이지 마세요. <white>{player}</white> 가 치환됩니다.</gray>"),
                reopen = { open(viewer) },
            ) { raw ->
                val line = raw.trim().removePrefix("/")
                if (line.isNotBlank()) mutate(reopen = false) { it.copy(commands = it.commands + line) }
            }
        }

        set(SLOT_GIVE, giveIcon()) { event ->
            val stack = fishing.fish.stack(fish, if (event.isShiftClick) 64 else 1)
            if (stack == null) {
                viewer.sendMessage(Text.render("<red>아이템을 만들 수 없습니다 - 연동 플러그인이 꺼져 있나요?</red>"))
                return@set
            }
            for (left in viewer.inventory.addItem(stack).values) {
                viewer.world.dropItem(viewer.location, left)
            }
        }

        set(Paging.SLOT_BACK, Icon.back()) { FishListMenu(fishing, viewer).open(viewer) }
        set(Paging.SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }

    private fun gradeLabel(id: String): String =
        fishing.grades[id]?.displayName ?: id.uppercase()

    /**
     * 평균이 최대에 너무 가까우면 **이 물고기는 트로피가 될 수 없다** — 대어가 걸려도 성립하지
     * 않아 조용히 보통 물고기가 된다. 값을 고치는 자리에서 알려준다.
     */
    private fun sizeWarning(fish: Fish): List<String> {
        if (!fish.hasSize) return emptyList()
        val rule = fishing.config.trophyRule
        if (rule.canBeTrophy(fish.size)) return emptyList()
        return listOf(
            "",
            "<red>이 크기 범위로는 트로피가 나오지 않습니다.</red>",
            "<gray>평균 x " + rule.normalMultiplier + " 가 최대를 넘습니다.</gray>",
        )
    }

    private fun modeIcon(fish: Fish) = Icon.of(
        Material.CHEST,
        "<yellow>저장 방식: <white>" +
            (if (fish.item.mode == StorageMode.SNAPSHOT) "스냅샷(고정)" else "동적(참조)") +
            "</white></yellow>",
        listOf(
            "<gray><white>동적</white> - 원본 플러그인에서 매번 다시 만듭니다.</gray>",
            "<gray>  원본이 바뀌면 같이 바뀝니다.</gray>",
            "<gray><white>스냅샷</white> - 등록한 순간의 모습으로 고정합니다.</gray>",
            "<gray>  원본이 사라져도 계속 나옵니다.</gray>",
            "",
            "<yellow>▶ 클릭: 전환</yellow>",
        ),
    )

    private fun commandIcon(fish: Fish) = Icon.of(
        Material.COMMAND_BLOCK,
        "<yellow>낚였을 때 실행할 명령어</yellow>",
        buildList {
            if (fish.commands.isEmpty()) {
                add("<dark_gray>없음</dark_gray>")
            } else {
                for (line in fish.commands) add("<dark_gray>- " + line + "</dark_gray>")
            }
            add("")
            add("<yellow>▶ 좌클릭: 추가</yellow>")
            add("<red>▶ 우클릭: 전부 지우기</red>")
        },
    )

    private fun giveIcon() = Icon.of(
        Material.HOPPER,
        "<green>나에게 지급</green>",
        listOf("<yellow>▶ 좌클릭: 1개  /  Shift: 64개</yellow>"),
    )

    // --- 편집 -----------------------------------------------------------------------

    /** 정의를 갈아끼운다. 추첨기는 레지스트리가 알아서 다시 세운다. */
    private fun mutate(reopen: Boolean = true, change: (Fish) -> Fish) {
        val current = fish() ?: return
        fishing.fish.put(change(current))
        if (reopen) refresh() else open(viewer)
    }

    private fun nudgeInt(
        event: InventoryClickEvent,
        label: String,
        current: Int,
        min: Int,
        max: Int,
        step: Int = 1,
        apply: (Int, Fish) -> Fish,
    ) {
        if (Editors.isPrompt(event)) {
            Editors.promptInt(fishing.prompts, viewer, label, min, max, reopen = { open(viewer) }) { value ->
                mutate(reopen = false) { apply(value, it) }
            }
            return
        }
        mutate { apply((current + Editors.step(event, step)).coerceIn(min, max), it) }
    }

    private fun nudgeSize(
        event: InventoryClickEvent,
        label: String,
        current: Double,
        apply: (Double, Fish) -> Fish,
    ) {
        if (Editors.isPrompt(event)) {
            Editors.promptDouble(fishing.prompts, viewer, label, 0.0, MAX_SIZE, reopen = { open(viewer) }) { value ->
                mutate(reopen = false) { apply(value, it) }
            }
            return
        }
        mutate { apply((current + Editors.step(event, 1.0)).coerceIn(0.0, MAX_SIZE), it) }
    }

    companion object {
        const val SIZE = 54
        const val MAX_SIZE = 100_000.0

        const val SLOT_GRADE = 10
        const val SLOT_WEIGHT = 11
        const val SLOT_MIN = 12
        const val SLOT_MAX = 13
        const val SLOT_AVG = 14
        const val SLOT_DOUBLE = 15
        const val SLOT_FATIGUE = 16
        const val SLOT_SLOTS = 19
        const val SLOT_MODE = 20
        const val SLOT_COMMANDS = 21
        const val SLOT_GIVE = 49
    }
}
