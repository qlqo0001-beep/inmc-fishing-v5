package com.inmc.fishing.gui

import com.inmc.fishing.Fishing
import kr.inmc.core.gui.Icon
import kr.inmc.core.gui.Paging
import kr.inmc.core.rank.Rankable
import kr.inmc.core.util.Text
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * 랭킹 보드 목록. 누르면 [RankBoardMenu] 로 들어간다.
 *
 * 물고기별 크기 보드는 **기록이 하나라도 있는 물고기만** 만들어진다
 * ([com.inmc.fishing.ranking.FishingRanks]). 전부 만들면 빈 보드가 물고기 수만큼 생겨
 * 이 화면을 채운다.
 */
class RankingMenu(
    fishing: Fishing,
    private val viewer: Player,
) : Menu(fishing, SIZE, Text.renderFlat("<dark_gray>낚시 — 랭킹</dark_gray>")) {

    private var page = 0

    override fun draw() {
        clear()

        val boards = fishing.fishingRanks.all()
        page = Paging.clamp(page, boards.size)

        for ((index, board) in Paging.slice(boards, page).withIndex()) {
            set(index, tile(board)) { RankBoardMenu(fishing, viewer, board).open(viewer) }
        }

        if (page > 0) {
            set(Paging.SLOT_PREV, Icon.prevPage()) {
                page--
                refresh()
            }
        }
        if (page < Paging.pageCount(boards.size) - 1) {
            set(Paging.SLOT_NEXT, Icon.nextPage()) {
                page++
                refresh()
            }
        }

        mainButton(SLOT_MAIN, viewer)
        set(Paging.SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }

    private fun tile(board: Rankable): ItemStack {
        val mine = fishing.ranks.rankOf(board, viewer.uniqueId)
        val top = fishing.ranks.top(board, 1).firstOrNull()
        return Icon.of(
            material(board),
            "<yellow>" + board.displayName + "</yellow>",
            listOf(
                "<gray>1위: <white>" + (top?.name ?: "-") + "</white> " +
                    (top?.let { fishing.ranks.formatValue(board, it) } ?: ""),
                "<gray>내 순위: <white>" + (mine?.toString() ?: "기록 없음") + "</white></gray>",
                "",
                "<yellow>▶ 클릭: 전체 보기</yellow>",
            ),
        )
    }

    private fun material(board: Rankable): Material = when (board.id) {
        com.inmc.fishing.ranking.FishingRanks.COLLECTION_ID -> Material.BOOK
        com.inmc.fishing.ranking.FishingRanks.TROPHY_ID -> Material.GOLD_INGOT
        else -> Material.FISHING_ROD
    }

    private companion object {
        const val SIZE = 54
        const val SLOT_MAIN = 52
    }
}

/** 보드 하나의 상위 목록. */
class RankBoardMenu(
    fishing: Fishing,
    private val viewer: Player,
    private val board: Rankable,
) : Menu(fishing, SIZE, Text.renderFlat("<dark_gray>랭킹 — " + board.displayName + "</dark_gray>")) {

    override fun draw() {
        clear()

        val top = fishing.ranks.top(board, TOP)
        if (top.isEmpty()) {
            set(22, Icon.of(Material.BARRIER, "<gray>아직 기록이 없습니다.</gray>"))
        }

        for ((index, entry) in top.withIndex()) {
            set(
                index,
                Icon.of(
                    medal(index + 1),
                    "<yellow>" + (index + 1) + "위 <white>" + entry.name + "</white></yellow>",
                    listOf("<gray>" + fishing.ranks.formatValue(board, entry) + "</gray>"),
                ),
            )
        }

        val mine = fishing.ranks.rankOf(board, viewer.uniqueId)
        set(
            SLOT_MINE,
            Icon.of(
                Material.PLAYER_HEAD,
                "<aqua>내 순위</aqua>",
                listOf(
                    if (mine != null) {
                        "<gray><white>" + mine + "</white> 위</gray>"
                    } else {
                        "<gray>아직 순위에 오르지 않았습니다.</gray>"
                    },
                ),
            ),
        )

        set(Paging.SLOT_BACK, Icon.back()) { RankingMenu(fishing, viewer).open(viewer) }
        set(Paging.SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }

    /** 1·2·3위만 다른 아이콘. 전부 같으면 위쪽 세 칸이 눈에 안 들어온다. */
    private fun medal(rank: Int): Material = when (rank) {
        1 -> Material.GOLD_BLOCK
        2 -> Material.IRON_BLOCK
        3 -> Material.COPPER_BLOCK
        else -> Material.PAPER
    }

    private companion object {
        const val SIZE = 54
        const val TOP = 45
        const val SLOT_MINE = 49
    }
}
