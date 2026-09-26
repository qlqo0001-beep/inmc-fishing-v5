package com.inmc.fishing.gui

import com.inmc.fishing.Fishing
import net.kyori.adventure.text.Component

/**
 * core 의 [kr.inmc.core.gui.Menu] 에 이 플러그인의 서비스 로케이터를 다시 붙인 얇은 층.
 *
 * core 는 [Fishing] 을 알지 못하고 알 필요도 없다. 그 둘을 잇는 것이 이 파일의 전부다.
 */
abstract class Menu(
    protected val fishing: Fishing,
    size: Int,
    title: Component,
) : kr.inmc.core.gui.Menu(size, title) {

    /** 리로드 때 열린 화면을 닫는 청소가 이 값으로 우리 것을 가려낸다. */
    override val owner: Any get() = fishing

    /**
     * "메인으로" 버튼 — 2세대처럼 메인 화면에서 들어온 화면마다 돌아가는 길을 둔다.
     * 슬롯은 화면마다 자기 `SLOT_MAIN` 상수로 넘긴다(MenuLayoutTest 가 상수를 읽는다).
     */
    protected fun mainButton(slot: Int, viewer: org.bukkit.entity.Player) {
        set(slot, kr.inmc.core.gui.Icon.of(org.bukkit.Material.COMPASS, "<aqua>◀ 낚시 메인으로</aqua>")) {
            val angler = fishing.anglers.of(viewer) ?: return@set
            MainMenu(fishing, viewer, angler).open(viewer)
        }
    }
}
