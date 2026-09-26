package com.inmc.fishing.gui

import com.inmc.fishing.Fishing
import com.inmc.fishing.rod.Rod
import kr.inmc.core.gui.Editors
import kr.inmc.core.gui.Icon
import kr.inmc.core.gui.Paging
import kr.inmc.core.item.StorageMode
import kr.inmc.core.util.Text
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent

/**
 * 낚싯대 하나의 설정.
 *
 * 값은 전부 **더해지는 추가값**이다. 힘겨루기 세 칸은 `config.yml` 의 기본값 위에 얹히고,
 * 등급 보너스는 가중치에 더해진다. 어느 것도 배수가 아니다 — 배수면 기본값이 0 인 축에서
 * 아무 효과가 없고, 가중치가 1 인 최상위 등급에서 특히 그렇다.
 *
 * **정의가 아니라 id 를 들고 있다.** [Rod] 는 불변이라 한 칸을 고칠 때마다 새 객체가 되는데,
 * 화면이 옛 객체를 붙들고 있으면 두 번째 편집이 첫 번째를 지운다.
 */
class RodEditMenu(
    fishing: Fishing,
    private val viewer: Player,
    private val id: String,
) : Menu(fishing, SIZE, Text.renderFlat("<dark_gray>낚싯대 — " + id + "</dark_gray>")) {

    private fun rod(): Rod? = fishing.rods.byId(id)

    override fun draw() {
        clear()
        val rod = rod() ?: run {
            RodListMenu(fishing, viewer).open(viewer)
            return
        }
        fillEmpty(Icon.EDGE)

        set(
            SLOT_BONUS,
            Icon.of(
                Material.ENCHANTED_BOOK,
                "<yellow>등급 보너스</yellow>",
                rod.describe(fishing.grades.ordered.map { it.id })
                    .ifEmpty { listOf("<dark_gray>없음</dark_gray>") } +
                    listOf("", "<yellow>▶ 클릭: 등급별로 설정</yellow>"),
            ),
        ) {
            GradeBonusMenu(
                fishing,
                viewer,
                "<dark_gray>낚싯대 보너스 — " + id + "</dark_gray>",
                current = { rod()?.gradeBonus },
                onChange = { next -> mutate { it.copy(gradeBonus = next) } },
                back = { open(viewer) },
            ).open(viewer)
        }

        set(
            SLOT_BIG_FISH,
            Editors.numberIcon(
                Material.GOLD_NUGGET,
                "<yellow>대어 확률 보너스</yellow>",
                rod.bigFishChance,
                unit = "%p",
                extra = listOf(
                    "<gray>기본 <white>" + fishing.config.bigFishChance + "%</white> 에 더해집니다.</gray>",
                    "<gray>대어는 반드시 트로피가 됩니다.</gray>",
                ),
            ),
        ) { event ->
            nudge(event, "대어 확률 보너스", rod.bigFishChance, 0.0, 100.0) { v, r -> r.copy(bigFishChance = v) }
        }

        set(
            SLOT_DOUBLE,
            Editors.numberIcon(
                Material.GOLD_INGOT,
                "<yellow>더블 확률 보너스</yellow>",
                rod.doubleChance,
                unit = "%p",
                extra = listOf("<gray>기본 <white>" + fishing.config.doubleChance + "%</white> 에 더해집니다.</gray>"),
            ),
        ) { event ->
            nudge(event, "더블 확률 보너스", rod.doubleChance, 0.0, 100.0) { v, r -> r.copy(doubleChance = v) }
        }

        set(
            SLOT_MAX_FATIGUE,
            Editors.intIcon(
                Material.GOLDEN_APPLE,
                "<yellow>최대 피로도 보너스</yellow>",
                rod.maxFatigue,
                extra = listOf(
                    "<gray>이걸 들고 있는 동안 <white>자연 회복 한계</white>가 올라갑니다.</gray>",
                    "<gray>기본 <white>" + fishing.config.fatigue.default + "</white> · 절대 상한 <white>" +
                        fishing.config.fatigue.max + "</white></gray>",
                ),
                stepLabel = "50",
            ),
        ) { event ->
            nudgeInt(event, "최대 피로도 보너스", rod.maxFatigue, 0, fishing.config.fatigue.max, step = 50) { v, r ->
                r.copy(maxFatigue = v)
            }
        }

        set(
            SLOT_FATIGUE_RECOVERY,
            Editors.intIcon(
                Material.POTION,
                "<yellow>피로 회복 보너스</yellow>",
                rod.fatigueRecovery,
                extra = listOf(
                    "<gray>자연 회복 1회당 이만큼 더 회복합니다.</gray>",
                    "<gray>기본 <white>" + fishing.config.fatigue.recoveryAmount + "</white></gray>",
                ),
                stepLabel = "10",
            ),
        ) { event ->
            nudgeInt(event, "피로 회복 보너스", rod.fatigueRecovery, 0, 100_000, step = 10) { v, r ->
                r.copy(fatigueRecovery = v)
            }
        }

        set(
            SLOT_REEL_POWER,
            Editors.numberIcon(
                Material.LEAD,
                "<yellow>릴 파워</yellow>",
                rod.reelPower,
                extra = listOf("<gray>물고기 체력을 깎는 속도입니다.</gray>"),
            ),
        ) { event -> nudge(event, "릴 파워", rod.reelPower, 0.0, MAX_STAT) { v, r -> r.copy(reelPower = v) } }

        set(
            SLOT_LINE,
            Editors.numberIcon(
                Material.STRING,
                "<yellow>줄 강도</yellow>",
                rod.lineStrength,
                extra = listOf(
                    "<gray>줄이 버티는 장력입니다.</gray>",
                    "<gray>물고기가 도망갈 수 있는 거리도 같이 늘어납니다.</gray>",
                ),
            ),
        ) { event -> nudge(event, "줄 강도", rod.lineStrength, 0.0, MAX_STAT) { v, r -> r.copy(lineStrength = v) } }

        set(
            SLOT_REEL_DURABILITY,
            Editors.numberIcon(
                Material.IRON_INGOT,
                "<yellow>릴 내구도</yellow>",
                rod.reelDurability,
                extra = listOf("<gray>릴이 부서지기까지 버티는 정도입니다.</gray>"),
            ),
        ) { event ->
            nudge(event, "릴 내구도", rod.reelDurability, 0.0, MAX_STAT) { v, r -> r.copy(reelDurability = v) }
        }

        set(
            SLOT_MINIGAME,
            Editors.numberIcon(
                Material.CLOCK,
                "<yellow>미니게임 시간</yellow>",
                rod.minigameSeconds,
                unit = "초",
                extra = listOf("<gray>등급마다 정해진 제한 시간에 더해집니다.</gray>"),
            ),
        ) { event -> nudge(event, "미니게임 시간", rod.minigameSeconds, 0.0, 60.0) { v, r -> r.copy(minigameSeconds = v) } }

        set(
            SLOT_BAIT_SAVE,
            Editors.numberIcon(
                Material.WHEAT_SEEDS,
                "<yellow>미끼 절약</yellow>",
                rod.baitSaveChance,
                unit = "%",
                extra = listOf("<gray>입질 때 왼손 미끼를 쓰지 않을 확률입니다.</gray>"),
            ),
        ) { event -> nudge(event, "미끼 절약", rod.baitSaveChance, 0.0, 100.0) { v, r -> r.copy(baitSaveChance = v) } }

        set(
            SLOT_SIZE,
            Editors.numberIcon(
                Material.TROPICAL_FISH,
                "<yellow>크기 보너스</yellow>",
                rod.sizePercent,
                unit = "%",
                extra = listOf("<gray>낚은 물고기 크기에 더해집니다. 미끼의 크기 보정과 합쳐집니다.</gray>"),
            ),
        ) { event -> nudge(event, "크기 보너스", rod.sizePercent, 0.0, 500.0) { v, r -> r.copy(sizePercent = v) } }

        set(
            SLOT_FATIGUE_SAVE,
            Editors.numberIcon(
                Material.SUGAR,
                "<yellow>피로 절약</yellow>",
                rod.fatigueSaveChance,
                unit = "%",
                extra = listOf("<gray>자동 낚시로 낚을 때 피로도를 쓰지 않을 확률입니다.</gray>"),
            ),
        ) { event -> nudge(event, "피로 절약", rod.fatigueSaveChance, 0.0, 100.0) { v, r -> r.copy(fatigueSaveChance = v) } }

        set(
            SLOT_AUTO_CATCH,
            Editors.numberIcon(
                Material.HOPPER_MINECART,
                "<yellow>자동 낚시 단축</yellow>",
                rod.autoCatchReduce,
                unit = "초",
                extra = listOf("<gray>자동 낚시에서 입질 뒤 낚기까지 기다리는 시간에서 뺍니다.</gray>"),
            ),
        ) { event -> nudge(event, "자동 낚시 단축", rod.autoCatchReduce, 0.0, 60.0) { v, r -> r.copy(autoCatchReduce = v) } }

        set(SLOT_MODE, modeIcon(rod)) { mutate { it.copy(item = it.item.withMode(it.item.mode.toggle())) } }

        set(SLOT_GIVE, giveIcon()) {
            val stack = fishing.catches.rodStack(rod, 1)
            if (stack == null) {
                viewer.sendMessage(Text.render("<red>아이템을 만들 수 없습니다 - 연동 플러그인이 꺼져 있나요?</red>"))
                return@set
            }
            for (left in viewer.inventory.addItem(stack).values) {
                viewer.world.dropItem(viewer.location, left)
            }
        }

        set(Paging.SLOT_BACK, Icon.back()) { RodListMenu(fishing, viewer).open(viewer) }
        set(Paging.SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }

    private fun modeIcon(rod: Rod) = Icon.of(
        Material.CHEST,
        "<yellow>저장 방식: <white>" +
            (if (rod.item.mode == StorageMode.SNAPSHOT) "스냅샷(고정)" else "동적(참조)") +
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
        listOf("<gray>설정을 고친 자리에서 바로 확인합니다.</gray>"),
    )

    // --- 편집 -----------------------------------------------------------------------

    private fun mutate(reopen: Boolean = true, change: (Rod) -> Rod) {
        val current = rod() ?: return
        fishing.rods.register(change(current))
        if (reopen) refresh() else open(viewer)
    }

    private fun nudge(
        event: InventoryClickEvent,
        label: String,
        current: Double,
        min: Double,
        max: Double,
        apply: (Double, Rod) -> Rod,
    ) {
        if (Editors.isPrompt(event)) {
            Editors.promptDouble(fishing.prompts, viewer, label, min, max, reopen = { open(viewer) }) { value ->
                mutate(reopen = false) { apply(value, it) }
            }
            return
        }
        mutate { apply((current + Editors.step(event, 1.0)).coerceIn(min, max), it) }
    }

    private fun nudgeInt(
        event: InventoryClickEvent,
        label: String,
        current: Int,
        min: Int,
        max: Int,
        step: Int,
        apply: (Int, Rod) -> Rod,
    ) {
        if (Editors.isPrompt(event)) {
            Editors.promptInt(fishing.prompts, viewer, label, min, max, reopen = { open(viewer) }) { value ->
                mutate(reopen = false) { apply(value, it) }
            }
            return
        }
        mutate { apply((current + Editors.step(event, step)).coerceIn(min, max), it) }
    }

    companion object {
        const val SIZE = 54
        const val MAX_STAT = 10_000.0

        const val SLOT_BONUS = 10
        const val SLOT_BIG_FISH = 11
        const val SLOT_DOUBLE = 12
        const val SLOT_MAX_FATIGUE = 14
        const val SLOT_FATIGUE_RECOVERY = 15
        const val SLOT_REEL_POWER = 19
        const val SLOT_LINE = 20
        const val SLOT_REEL_DURABILITY = 21
        const val SLOT_MODE = 23
        const val SLOT_MINIGAME = 28
        const val SLOT_BAIT_SAVE = 29
        const val SLOT_SIZE = 30
        const val SLOT_FATIGUE_SAVE = 32
        const val SLOT_AUTO_CATCH = 33
        const val SLOT_GIVE = 49
    }
}
