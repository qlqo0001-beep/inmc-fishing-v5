package com.inmc.fishing.gui

import com.inmc.fishing.Fishing
import com.inmc.fishing.tournament.ActiveTournament
import com.inmc.fishing.tournament.TournamentDef
import com.inmc.fishing.tournament.TournamentType
import kr.inmc.core.gui.Icon
import kr.inmc.core.gui.Paging
import kr.inmc.core.util.Text
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 대회.
 *
 * **진행 중인 대회가 먼저**, 그 뒤에 예정된 대회. 한 칸이 한 대회다 — 여러 대회가 동시에 돌 수
 * 있으므로(2세대와 같다) "지금 열린 대회" 칸 하나로는 안 된다.
 *
 * 클릭 한 번이 상황에 맞는 일을 한다: 열려 있으면 참가/나가기, 아직이면 **사전 신청/신청 취소**.
 */
class TournamentMenu(
    fishing: Fishing,
    private val viewer: Player,
) : Menu(fishing, SIZE, Text.renderFlat("<dark_gray>낚시 — 대회</dark_gray>")) {

    private var page = 0

    override fun draw() {
        clear()
        fillBorder(Icon.EDGE)

        set(SLOT_WINS, winsTile())

        val defs = fishing.tournaments.all().sortedBy { if (fishing.tournaments.runOf(it.id) != null) 0 else 1 }
        page = Paging.clamp(page, defs.size, PER_PAGE)
        for ((index, def) in Paging.slice(defs, page, PER_PAGE).withIndex()) {
            set(LIST_SLOTS[index], tile(def)) {
                val running = fishing.tournaments.runOf(def.id)
                val inside = if (running != null) {
                    running.board.isIn(viewer.uniqueId)
                } else {
                    fishing.tournaments.hasApplied(def, viewer.uniqueId)
                }
                if (inside) fishing.tournaments.leave(viewer, def) else fishing.tournaments.join(viewer, def)
                refresh()
            }
        }

        if (page > 0) {
            set(Paging.SLOT_PREV, Icon.prevPage()) {
                page--
                refresh()
            }
        }
        if (page < Paging.pageCount(defs.size, PER_PAGE) - 1) {
            set(Paging.SLOT_NEXT, Icon.nextPage()) {
                page++
                refresh()
            }
        }

        mainButton(SLOT_MAIN, viewer)
        set(Paging.SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }

    private fun tile(def: TournamentDef): ItemStack {
        val running = fishing.tournaments.runOf(def.id)
        return if (running != null) runningTile(running) else waitingTile(def)
    }

    private fun runningTile(running: ActiveTournament): ItemStack {
        val def = running.def
        val joined = running.board.isIn(viewer.uniqueId)
        val left = running.remainingSeconds(System.currentTimeMillis())
        return Icon.of(
            if (joined) Material.LIME_DYE else Material.CLOCK,
            "<gold>" + def.name + "</gold> <green>진행 중</green>",
            buildList {
                addAll(def.description.map { "<gray>$it</gray>" })
                add("<gray>종류: <white>" + typeLabel(def.type) + "</white></gray>")
                add("<gray>남은 시간: <white>" + (left / 60) + "분 " + (left % 60) + "초</white></gray>")
                add("<gray>참가: <white>" + running.board.size + "</white> / " + def.maxPlayers + "</gray>")
                if (def.entryFee > 0.0) add("<gray>참가비: <white>" + fishing.economy.format(def.entryFee) + "</white></gray>")
                add("")
                val ranked = running.board.rankedActive().take(TOP_SHOWN)
                if (ranked.isEmpty()) add("<dark_gray>아직 아무도 점수를 내지 못했습니다.</dark_gray>")
                for ((index, standing) in ranked.withIndex()) {
                    add("<gray>" + (index + 1) + ". <white>" + standing.playerName + "</white> " + standing.score + "</gray>")
                }
                running.board.of(viewer.uniqueId)?.takeIf { !it.left }?.let {
                    add("<aqua>내 점수: <white>" + it.score + "</white></aqua>")
                }
                add("")
                add(if (joined) "<red>▶ 클릭: 나가기</red>" else "<green>▶ 클릭: 참가하기</green>")
            },
        )
    }

    private fun waitingTile(def: TournamentDef): ItemStack {
        val applied = fishing.tournaments.hasApplied(def, viewer.uniqueId)
        val next = fishing.tournaments.nextStartAt(def)
        return Icon.of(
            if (applied) Material.WRITABLE_BOOK else Material.FISHING_ROD,
            "<white>" + def.name + "</white>",
            buildList {
                addAll(def.description.map { "<gray>$it</gray>" })
                add("<gray>종류: <white>" + typeLabel(def.type) + "</white></gray>")
                add("<gray>진행 시간: <white>" + def.durationMinutes + "분</white></gray>")
                if (def.entryFee > 0.0) add("<gray>참가비: <white>" + fishing.economy.format(def.entryFee) + "</white></gray>")
                add(
                    when {
                        !def.autoStart -> "<dark_gray>관리자가 직접 엽니다.</dark_gray>"
                        next == null -> "<dark_gray>일정이 없습니다.</dark_gray>"
                        else -> "<gray>다음: <white>" + format(next) + "</white></gray>"
                    },
                )
                add(
                    "<gray>사전 신청: <white>" + fishing.tournaments.applicantsOf(def.id).size + "</white> / " +
                        def.maxPlayers + " <dark_gray>(최소 " + def.minPlayers + "명이어야 자동으로 열립니다)</dark_gray></gray>",
                )
                add("")
                add(if (applied) "<yellow>신청함</yellow> <red>▶ 클릭: 신청 취소</red>" else "<green>▶ 클릭: 사전 신청</green>")
            },
        )
    }

    private fun winsTile(): ItemStack {
        val top = fishing.tournaments.winners.top(TOP_SHOWN)
        return Icon.of(
            Material.GOLDEN_HELMET,
            "<yellow>우승 기록</yellow>",
            buildList {
                if (top.isEmpty()) add("<dark_gray>아직 우승자가 없습니다.</dark_gray>")
                for ((index, entry) in top.withIndex()) {
                    add("<gray>" + (index + 1) + ". <white>" + entry.first + "</white> " + entry.second + "회</gray>")
                }
            },
        )
    }

    private fun typeLabel(type: TournamentType): String = when (type) {
        TournamentType.GRADE -> "등급 점수"
        TournamentType.SIZE -> "크기 합산"
        TournamentType.COUNT -> "마릿수"
    }

    private fun format(epochMillis: Long): String =
        FORMAT.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

    internal companion object {
        const val SIZE = 54
        const val SLOT_MAIN = 52
        const val SLOT_WINS = 4
        /**
         * 대회 칸 — 테두리 **안쪽**(1~4줄 × 1~7열). 예전엔 18번부터 이어서 27칸을 깔아, 첫 칸이
         * 왼쪽 테두리(0열)에 그려져 한 칸 밀려 보였고 26·35·44번 오른쪽 테두리까지 덮었다.
         */
        val LIST_SLOTS: List<Int> = (1..4).flatMap { row -> (1..7).map { col -> row * 9 + col } }
        const val PER_PAGE = 28
        const val TOP_SHOWN = 5
        val FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("M월 d일 HH:mm")
    }
}
