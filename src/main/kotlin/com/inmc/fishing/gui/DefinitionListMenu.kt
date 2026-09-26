package com.inmc.fishing.gui

import com.inmc.fishing.Fishing
import kr.inmc.core.gui.ConfirmMenu
import kr.inmc.core.gui.Editors
import kr.inmc.core.gui.Icon
import kr.inmc.core.gui.Paging
import kr.inmc.core.item.StorageMode
import kr.inmc.core.item.StoredItem
import kr.inmc.core.store.DefinitionKey
import kr.inmc.core.util.Text
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * 정의 목록 한 장. **생성·수정·삭제가 전부 여기서 시작한다.**
 *
 * 물고기·낚싯대·미끼가 같은 화면을 쓴다. 셋을 따로 그리면 버튼 자리가 갈라지고, 그러면
 * 관리자가 화면을 옮길 때마다 "등록" 버튼을 다시 찾아야 한다. 종류마다 다른 것은 칸에 적히는
 * 내용과 편집 화면뿐이라 그 둘만 하위 클래스가 정한다.
 *
 * **레지스트리 타입을 요구하지 않는다.** 필요한 것은 "전부 달라", "이 이름 있어?", "지워"
 * 셋뿐이다. 낚싯대는 언젠가 바깥 플러그인이 공급하게 되는데
 * ([com.inmc.fishing.rod.RodSource]), 그때도 이 화면은 그대로 쓰인다 — 다만 [editable] 이
 * false 가 되어 만들기·지우기 버튼이 사라진다. 열어두고 눌렀을 때 아무 일도 안 일어나는 것보다
 * 낫다.
 *
 * 화면 인스턴스는 **보는 사람마다 하나**다. 그래서 페이지·필터를 필드로 들고 다시 열기만 하면
 * 되고, 확인창을 다녀와도 보던 자리로 돌아온다.
 */
abstract class DefinitionListMenu<T : Any>(
    fishing: Fishing,
    protected val viewer: Player,
    title: String,
) : Menu(fishing, SIZE, Text.renderFlat(title)) {

    protected var page: Int = 0

    /** 문장에 그대로 들어가는 이름. 예: `물고기`. */
    protected abstract val what: String

    protected abstract fun listAll(): List<T>

    protected abstract fun exists(id: String): Boolean

    protected abstract fun delete(id: String)

    protected abstract fun idOf(value: T): String

    protected abstract fun itemOf(value: T): StoredItem

    /** 칸에 적을 설명. 이름과 조작 안내는 이 클래스가 붙인다. */
    protected abstract fun summary(value: T): List<String>

    protected abstract fun openEdit(value: T)

    /** 손에 든 스택을 그 종류의 **기본값**으로 등록한다. 값은 이어지는 편집 화면에서 채운다. */
    protected abstract fun create(id: String, stack: ItemStack): T

    /** 이 화면에서 만들고 지울 수 있는지. 바깥 플러그인이 공급하는 목록은 false 다. */
    protected open val editable: Boolean get() = true

    /** 목록에 보일 것. 필터가 있는 화면이 좁힌다. */
    protected open fun visible(): List<T> = listAll()

    /** 필터 같은 추가 버튼. */
    protected open fun decorate() {}

    /** 지금 등록할 수 없는 사유. null 이면 등록 가능하다. */
    protected open fun blockedReason(): String? = null

    final override fun draw() {
        clear()

        val shown = visible()
        page = Paging.clamp(page, shown.size)

        for ((index, value) in Paging.slice(shown, page).withIndex()) {
            set(index, tile(value)) { event ->
                if (editable && event.isRightClick) confirmDelete(value) else openEdit(value)
            }
        }

        set(Paging.SLOT_BACK, registerButton()) { promptRegister() }

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

        decorate()

        set(SLOT_HOME, Icon.back()) { AdminMenu(fishing, viewer).open(viewer) }
        set(Paging.SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }

    /** 아이콘은 등록된 아이템 그대로라 관리자가 눈으로 알아본다. */
    private fun tile(value: T): ItemStack {
        val item = itemOf(value)
        val icon = fishing.itemResolver.icon(item)
        val lore = buildList {
            addAll(summary(value))
            add("<gray>저장: <white>" + modeLabel(item) + "</white></gray>")
            addAll(icon.notes().map { "<dark_gray>" + it + "</dark_gray>" })
            add("")
            add("<yellow>▶ 좌클릭: 설정</yellow>")
            if (editable) add("<red>▶ 우클릭: 삭제</red>")
        }
        return Icon.annotate(icon.stack.clone(), "<white>" + idOf(value) + "</white>", lore)
    }

    private fun modeLabel(item: StoredItem): String =
        if (item.mode == StorageMode.SNAPSHOT) "스냅샷(고정)" else "동적(참조)"

    private fun registerButton(): ItemStack {
        if (!editable) {
            return Icon.of(
                Material.BARRIER,
                "<red>여기서는 만들 수 없습니다</red>",
                listOf(
                    "<gray>다른 플러그인이 " + what + "을(를) 공급하고 있습니다.</gray>",
                    "<gray>만들고 고치는 것은 그쪽에서 합니다.</gray>",
                ),
            )
        }
        blockedReason()?.let { reason ->
            return Icon.of(
                Material.BARRIER,
                "<red>지금은 등록할 수 없습니다</red>",
                listOf("<gray>" + reason + "</gray>"),
            )
        }
        val hand = viewer.inventory.itemInMainHand
        if (hand.type.isAir) {
            return Icon.of(
                Material.BARRIER,
                "<red>등록하려면 아이템을 손에 드세요</red>",
                listOf("<gray>등록할 아이템을 주 손에 들고</gray>", "<gray>이 버튼을 누르세요.</gray>"),
            )
        }
        return Icon.of(
            Material.WRITABLE_BOOK,
            "<green>손에 든 것을 " + what + "(으)로 등록</green>",
            listOf(
                "<gray>대상: <white>" + (StoredItem.plainName(hand) ?: hand.type.name) + "</white></gray>",
                "",
                "<yellow>▶ 클릭하면 이름을 물어봅니다</yellow>",
            ),
        )
    }

    /**
     * 이름을 물어보고 등록한다.
     *
     * 이름은 설정 파일의 **섹션 이름**이 되므로 규칙을 검사한다 — 점이 들어가면
     * YamlConfiguration 이 계층을 만들어 다시 읽히지 않는다.
     */
    private fun promptRegister() {
        if (!editable || blockedReason() != null) return
        val hand = viewer.inventory.itemInMainHand.clone()
        if (hand.type.isAir) return

        Editors.promptText(
            fishing.prompts,
            viewer,
            what + " 이름",
            listOf(
                "<gray>" + DefinitionKey.HINT + ".</gray>",
                "<gray>예: <white>참치</white>, <white>golden_rod</white></gray>",
            ),
            reopen = { open(viewer) },
        ) { raw ->
            val id = raw.trim().lowercase()
            if (!DefinitionKey.isValid(id)) {
                viewer.sendMessage(Text.render("<red>이름 규칙에 맞지 않습니다: " + raw + "</red>"))
                return@promptText
            }
            if (exists(id)) {
                viewer.sendMessage(Text.render("<red>'" + id + "' 은(는) 이미 있습니다.</red>"))
                return@promptText
            }
            val created = create(id, hand)
            viewer.sendMessage(Text.render("<green>'" + id + "' 로 등록했습니다. 이어서 설정을 채우세요.</green>"))
            openEdit(created)
        }
    }

    private fun confirmDelete(value: T) {
        val id = idOf(value)
        ConfirmMenu(
            owner = fishing,
            question = "<red>'" + id + "' 을(를) 삭제할까요?</red>",
            detail = listOf(
                "<gray>이미 플레이어가 갖고 있는 아이템은 남습니다.</gray>",
                "<gray>다만 이 플러그인이 더는 알아보지 못합니다.</gray>",
            ),
            onConfirm = {
                delete(id)
                open(viewer)
            },
            onCancel = { open(viewer) },
        ).open(viewer)
    }

    companion object {
        const val SIZE = 54

        /** 관리 첫 화면으로. 페이지 버튼과 겹치지 않는 자리다. */
        const val SLOT_HOME = 49

        /** 하위 화면이 쓰는 필터 자리. [Paging] 버튼들과 겹치지 않는다. */
        const val SLOT_FILTER = 48
    }
}
