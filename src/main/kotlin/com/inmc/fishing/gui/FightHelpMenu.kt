package com.inmc.fishing.gui

import com.inmc.fishing.Fishing
import com.inmc.fishing.fight.FishState
import com.inmc.fishing.player.Angler
import kr.inmc.core.gui.Icon
import kr.inmc.core.util.Text
import org.bukkit.Material
import org.bukkit.entity.Player

/**
 * 힘겨루기 설명 — 2세대 `TrophyHelpGui`. 목표 · 조작법 · 물고기 상태 · 실패 조건 네 칸.
 *
 * 상태 목록은 [FishState] 에서 **그대로 뽑는다.** 손으로 적어 두면 상태가 늘거나 권장 클릭이
 * 바뀌었을 때 설명만 옛날 것으로 남는다.
 */
class FightHelpMenu(
    fishing: Fishing,
    private val viewer: Player,
    private val angler: Angler,
) : Menu(fishing, SIZE, Text.renderFlat("<gold>트로피 파이트 설명</gold>")) {

    override fun draw() {
        clear()
        fillEmpty(Icon.EDGE)

        set(
            SLOT_GOAL,
            Icon.of(
                Material.TARGET, "<gold>[ 목표 ]</gold>",
                listOf(
                    "<white>1. 물고기 체력을 0으로</white>",
                    "<white>2. 거리를 0으로</white>",
                    "<gray>둘 다 0이 되면 승리!</gray>",
                ),
            ),
        )
        set(
            SLOT_CONTROL,
            Icon.of(
                Material.FISHING_ROD, "<gold>[ 조작법 ]</gold>",
                listOf(
                    "<yellow>좌클릭 · W: 릴 감기 (제압)</yellow>",
                    "<gray>→ 거리 회수, 장력 상승, 물고기 체력 소모</gray>",
                    "<yellow>우클릭 · S: 릴 풀기 (회복)</yellow>",
                    "<gray>→ 장력 감소, 릴 상태 회복 (거리가 늘어남)</gray>",
                    "<gray>우클릭(S)을 연타할수록 장력이 크게 풀립니다.</gray>",
                    "<gray>W·S 는 누르고 있는 동안 계속 감거나 풉니다.</gray>",
                ),
            ),
        )
        set(
            SLOT_STATE,
            Icon.of(
                Material.TROPICAL_FISH, "<gold>[ 물고기 상태 ]</gold>",
                FishState.entries.map { "<white>" + it.displayName + "</white><gray> — " + it.advice.hint + "</gray>" },
            ),
        )
        val grace = fishing.fight.general.lineSnappedGraceMillis
        set(
            SLOT_FAIL,
            Icon.of(
                Material.BARRIER, "<red>[ 실패 조건 ]</red>",
                buildList {
                    add("<red>장력 ≥ 줄 강도 (줄 끊김)</red>")
                    add("<red>거리 ≥ 상한 (도망)</red>")
                    add("<red>릴 상태 ≤ 0 (파손)</red>")
                    add("<red>제한 시간 초과 (" + fishing.fight.general.maxTimeSeconds + "초)</red>")
                    if (grace > 0) add("<gray>한계에 닿아도 잠깐은 버틸 수 있습니다. 바로 되돌리세요.</gray>")
                },
            ),
        )

        set(SLOT_BACK, Icon.back()) { MainMenu(fishing, viewer, angler).open(viewer) }
        set(SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }

    private companion object {
        const val SIZE = 27
        const val SLOT_GOAL = 10
        const val SLOT_CONTROL = 12
        const val SLOT_STATE = 14
        const val SLOT_FAIL = 16
        const val SLOT_BACK = 18
        const val SLOT_CLOSE = 26
    }
}
