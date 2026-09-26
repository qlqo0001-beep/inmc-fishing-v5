package com.inmc.fishing.gui

import com.inmc.fishing.Fishing
import com.inmc.fishing.fish.Fish
import com.inmc.fishing.fish.FishLore
import com.inmc.fishing.fish.FishStamp
import com.inmc.fishing.player.Angler
import com.inmc.fishing.util.Ph
import kr.inmc.core.gui.Editors
import kr.inmc.core.gui.Icon
import kr.inmc.core.gui.Paging
import kr.inmc.core.util.Text
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * 도감.
 *
 * **발견과 등록은 다르다.** 낚으면 발견되고(회색으로 보이기 시작하고), 손에 들고 여기서
 * 눌러야 등록된다. 그 구분이 흐려지면 "낚기만 해도 도감이 찬다" 또는 "등록했는데 안 뜬다"가
 * 되는데, 둘 다 조용히 잘못 도는 종류라 화면에서 상태를 분명히 보여준다.
 *
 * 못 잡은 물고기는 **이름도 보여주지 않는다.** 도감의 재미는 무엇이 있는지 모르는 데서 온다.
 */
class CollectionMenu(
    fishing: Fishing,
    private val viewer: Player,
    private val angler: Angler,
) : Menu(fishing, SIZE, Text.renderFlat("<dark_gray>낚시 — 도감</dark_gray>")) {

    private var page = 0
    private var gradeFilter: String? = null

    override fun draw() {
        clear()

        val shown = visible()
        page = Paging.clamp(page, shown.size)

        for ((index, fish) in Paging.slice(shown, page).withIndex()) {
            set(index, tile(fish)) { event -> onClick(fish, event.isRightClick) }
        }

        if (page > 0) {
            set(Paging.SLOT_PREV, Icon.prevPage()) {
                page--
                refresh()
            }
        }
        if (page < Paging.pageCount(shown.size) - 1) {
            set(Paging.SLOT_NEXT, Icon.nextPage()) {
                page++
                refresh()
            }
        }

        val options = listOf<String?>(null) + fishing.grades.ordered.map { it.id }
        set(
            SLOT_FILTER,
            Icon.of(
                Material.HOPPER,
                "<yellow>등급 필터: <white>" + label(gradeFilter) + "</white></yellow>",
                Editors.optionList(options, gradeFilter) { label(it) } + Editors.cycleHint,
            ),
        ) { event ->
            gradeFilter = Editors.cycle(event, options, gradeFilter)
            page = 0
            refresh()
        }

        set(SLOT_SUMMARY, summary())
        mainButton(SLOT_MAIN, viewer)
        set(Paging.SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }

    private fun visible(): List<Fish> {
        val all = gradeFilter?.let { fishing.fish.ofGrade(it) } ?: fishing.fish.all()
        // 등급 순서대로 보여준다. 등록 순서는 관리자의 편의고 도감은 플레이어가 본다.
        val rank = fishing.grades.ordered.withIndex().associate { (i, g) -> g.id to i }
        return all.sortedWith(compareBy({ rank[it.gradeId] ?: Int.MAX_VALUE }, { -it.weight }))
    }

    private fun tile(fish: Fish): ItemStack {
        val entry = angler.collection.of(fish.id)
        val grade = fishing.grades[fish.gradeId]

        if (entry == null || !entry.discovered) {
            return Icon.of(
                Material.GRAY_DYE,
                "<dark_gray>???</dark_gray>",
                listOf(
                    "<gray>등급: <white>" + (grade?.tag() ?: fish.gradeId.uppercase()) + "</white></gray>",
                    "<dark_gray>아직 낚아보지 못했습니다.</dark_gray>",
                ),
            )
        }

        val icon = fishing.itemResolver.icon(fish.item)
        val lore = buildList {
            add("<gray>등급: <white>" + (grade?.tag() ?: fish.gradeId.uppercase()) + "</white></gray>")
            add("<gray>등록: <white>" + entry.registeredSlots + "</white> / " + entry.maxSlots + "</gray>")
            add("<gray>잡은 횟수: <white>" + entry.totalCaught + "</white></gray>")
            if (entry.largestSize > 0.0) {
                add("<gray>최대: <white>" + entry.largestSize + "cm</white></gray>")
                add("<gray>최소: <white>" + entry.smallestSize + "cm</white></gray>")
            }
            if (entry.hasTrophy) {
                add("<gold>트로피 " + entry.trophyCount + "</gold> <light_purple>레어 " + entry.rareTrophyCount + "</light_purple>")
            }
            add("")
            if (entry.isPerfect) {
                add("<gold>★ 퍼펙트</gold>")
            } else {
                add("<yellow>▶ 좌클릭: 손에 든 것을 등록</yellow>")
            }
            if (entry.isRegistered) add("<red>▶ 우클릭: 한 칸 회수</red>")
        }
        return Icon.annotate(icon.stack.clone(), "<white>" + fish.label() + "</white>", lore)
    }

    /**
     * 좌클릭은 등록, 우클릭은 회수.
     *
     * 등록은 **손에 든 그 물고기**를 먹는다. 크기가 같이 기록되므로 아무 물고기나 되는 것이
     * 아니라 그 종류여야 한다.
     */
    private fun onClick(fish: Fish, right: Boolean) {
        val entry = angler.collection.getOrCreate(fish.id, fish.gradeId, fish.maxSlots)
        if (!entry.discovered) return

        if (right) {
            // 회수한 물고기는 돌려준다(2세대와 같다). 칸만 비우면 바친 물고기가 그대로 사라진다.
            val size = entry.lastRegisteredSize() ?: return
            if (viewer.inventory.firstEmpty() < 0) {
                fishing.messages.send(viewer, "inventory-full")
                return
            }
            val stamp = FishStamp(
                fishId = fish.id,
                gradeId = fish.gradeId,
                size = size,
                trophy = fishing.config.trophyRule.judge(fish.size, size),
                caughtAt = System.currentTimeMillis(),
            )
            val stack = fishing.fish.stack(fish, 1, FishLore.of(stamp, fishing.grades[fish.gradeId]?.tag().orEmpty()))
                ?: return
            stamp.applyTo(stack)
            if (!entry.unregister()) return
            viewer.inventory.addItem(stack)
            fishing.catches.publishRanks(angler)
            fishing.anglers.markDirty(angler)
            refresh()
            return
        }

        if (entry.isPerfect) {
            fishing.messages.send(viewer, "collection-full", Ph.of().fish(fish.label()))
            return
        }

        val hand = viewer.inventory.itemInMainHand
        val stamp = FishStamp.read(hand)
        if (stamp == null || !stamp.fishId.equals(fish.id, ignoreCase = true)) {
            fishing.messages.send(viewer, "collection-need-item", Ph.of().fish(fish.label()))
            return
        }

        val first = entry.registeredSlots == 0
        if (!entry.register(stamp.size, System.currentTimeMillis())) {
            fishing.messages.send(viewer, "collection-full", Ph.of().fish(fish.label()))
            return
        }

        hand.amount = hand.amount - 1
        viewer.playSound(viewer.location, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f)

        val ph = Ph.of().fish(fish.label()).count(entry.registeredSlots).amount(entry.maxSlots)
        fishing.messages.send(viewer, if (first) "collection-first" else "collection-registered", ph)
        if (entry.isPerfect) fishing.messages.send(viewer, "collection-perfect", ph)

        fishing.collectionRewards.onRegistered(viewer, angler, entry)
        fishing.catches.publishRanks(angler)
        fishing.anglers.markDirty(angler)
        refresh()
    }

    private fun summary(): ItemStack {
        val data = angler.collection
        val total = fishing.fish.size
        return Icon.of(
            Material.BOOK,
            "<aqua>내 도감</aqua>",
            listOf(
                "<gray>발견: <white>" + data.discoveredCount + "</white> / " + total + "</gray>",
                "<gray>등록 칸: <white>" + data.totalRegisteredSlots + "</white></gray>",
                "<gray>퍼펙트: <white>" + data.perfectCount + "</white></gray>",
                "<gray>트로피: <gold>" + data.trophyCount + "</gold> · 레어 <light_purple>" +
                    data.rareTrophyCount + "</light_purple></gray>",
                "",
                "<yellow>점수: <white>" + data.score() + "</white></yellow>",
                "<dark_gray>칸 1 · 퍼펙트 5 · 트로피 10 · 레어 25</dark_gray>",
            ),
        )
    }

    private fun label(id: String?): String =
        if (id == null) "전체" else fishing.grades[id]?.displayName ?: id.uppercase()

    companion object {
        const val SIZE = 54
        const val SLOT_MAIN = 52
        const val SLOT_FILTER = 48
        const val SLOT_SUMMARY = 49
    }
}
