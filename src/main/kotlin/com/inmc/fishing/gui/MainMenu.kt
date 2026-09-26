package com.inmc.fishing.gui

import com.inmc.fishing.Fishing
import com.inmc.fishing.player.Angler
import kr.inmc.core.gui.Icon
import kr.inmc.core.util.Text
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta

/**
 * 낚시 메인 — 2세대 `MainGui`. `/낚시 메인` 과 **낚싯대를 든 채 Shift+F** 로 연다.
 *
 * 내 스탯(머리 아이콘) · 어망 · 도감 · 랭킹 · 대회 · 손질대 로 가는 길과, 미니게임 켜고 끄기 ·
 * 힘겨루기 연습 모드 · 힘겨루기 설명을 한 화면에 둔다. 들어간 화면마다 "메인으로" 버튼이 있다.
 */
class MainMenu(
    fishing: Fishing,
    private val viewer: Player,
    private val angler: Angler,
) : Menu(fishing, SIZE, Text.renderFlat("<dark_aqua>낚시 메인메뉴</dark_aqua>")) {

    override fun draw() {
        clear()
        fillEmpty(Icon.EDGE)

        set(SLOT_STATS, statsHead())
        set(SLOT_NET, nav(Material.CHEST, "어망", "낚은 물고기를 보관·정렬·꺼냅니다.")) {
            NetMenu(fishing, viewer, angler).open(viewer)
        }
        set(SLOT_COLLECTION, nav(Material.BOOK, "도감", "낚은 물고기를 등록하고 보상을 받습니다.")) {
            CollectionMenu(fishing, viewer, angler).open(viewer)
        }
        set(SLOT_RANK, nav(Material.GOLDEN_SWORD, "랭킹", "도감·사이즈·트로피 랭킹을 확인합니다.")) {
            RankingMenu(fishing, viewer).open(viewer)
        }
        set(SLOT_TOURNAMENT, nav(Material.FISHING_ROD, "대회", "낚시 대회 참가·정보를 확인합니다.")) {
            TournamentMenu(fishing, viewer).open(viewer)
        }
        set(SLOT_FILLET, nav(Material.COOKED_COD, "손질대", "물고기를 생선살로 손질합니다.")) {
            FilletMenu(fishing, viewer, angler).open(viewer)
        }

        set(SLOT_MINIGAME, minigameTile()) {
            fishing.catches.setMinigame(viewer, angler, !angler.minigameEnabled)
            refresh()
        }
        set(SLOT_PRACTICE, practiceTile()) { togglePractice() }
        set(SLOT_FIGHT_HELP, nav(Material.WRITABLE_BOOK, "힘겨루기 설명", "물고기와의 힘겨루기 방식을 안내합니다.")) {
            FightHelpMenu(fishing, viewer, angler).open(viewer)
        }
        set(SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }

    private fun nav(material: Material, name: String, description: String): ItemStack =
        Icon.of(material, "<gold>$name</gold>", listOf("<gray>$description</gray>", "<gray>클릭: $name 열기</gray>"))

    private fun statsHead(): ItemStack {
        val head = ItemStack(Material.PLAYER_HEAD)
        (head.itemMeta as? SkullMeta)?.let {
            it.owningPlayer = viewer
            head.itemMeta = it
        }
        return Icon.annotate(head, "<gold>내 낚시 스탯</gold>", fishing.catches.statsLines(viewer, angler))
    }

    private fun minigameTile(): ItemStack {
        val on = angler.minigameEnabled
        val ceiling = angler.fatigue.ceiling(fishing.catches.rodOf(viewer)?.maxFatigue ?: 0)
        return Icon.of(
            if (on) Material.LIME_DYE else Material.GRAY_DYE,
            "<gold>미니게임 " + (if (on) "ON" else "OFF") + "</gold>",
            buildList {
                add("<gray>L/R 클릭 미니게임을 켜거나 끕니다.</gray>")
                add("<gray>OFF면 입질 뒤 자동으로 낚습니다. (자동 낚시)</gray>")
                add("<aqua>피로도: <white>" + angler.fatigue.value + "</white><gray> / </gray><white>$ceiling</white></aqua>")
                if (angler.fatigue.locked) add("<red>피로도가 낮아 미니게임 OFF 가 잠겨 있습니다.</red>")
                add("<yellow>현재: " + (if (on) "<green>ON (미니게임)</green>" else "<red>OFF (자동 낚시)</red>") + "</yellow>")
                add("<gray>클릭: 전환</gray>")
            },
        )
    }

    private fun practiceTile(): ItemStack {
        val on = angler.practice
        return Icon.of(
            if (on) Material.GOLDEN_SWORD else Material.STONE_SWORD,
            "<gold>트로피 파이트 연습모드 " + (if (on) "ON" else "OFF") + "</gold>",
            listOf(
                "<gray>ON이면 일반 물고기여도 낚시 성공 시</gray>",
                "<gray>트로피 파이트가 발동됩니다. <red>(보상 없음)</red></gray>",
                "<gray>미니게임은 그대로 합니다.</gray>",
                "<yellow>현재: " + (if (on) "<green>ON</green>" else "<red>OFF</red>") + "</yellow>",
                "<gray>클릭: 전환</gray>",
            ),
        )
    }

    /** 2세대처럼 켜면 타이틀로 짧게 알린다. */
    private fun togglePractice() {
        angler.practice = !angler.practice
        fishing.anglers.markDirty(angler)
        val (title, subtitle) = if (angler.practice) {
            fishing.messages.raw("practice-on-title") to fishing.messages.raw("practice-on-subtitle")
        } else {
            fishing.messages.raw("practice-off-title") to ""
        }
        if (title.isNotEmpty() || subtitle.isNotEmpty()) {
            viewer.showTitle(net.kyori.adventure.title.Title.title(Text.render(title), Text.render(subtitle)))
        }
        refresh()
    }

    private companion object {
        const val SIZE = 27
        const val SLOT_STATS = 4
        const val SLOT_NET = 11
        const val SLOT_COLLECTION = 12
        const val SLOT_RANK = 13
        const val SLOT_TOURNAMENT = 14
        const val SLOT_FILLET = 15
        const val SLOT_MINIGAME = 21
        const val SLOT_PRACTICE = 22
        const val SLOT_FIGHT_HELP = 23
        const val SLOT_CLOSE = 26
    }
}
