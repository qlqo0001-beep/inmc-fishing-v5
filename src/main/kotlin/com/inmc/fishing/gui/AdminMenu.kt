package com.inmc.fishing.gui

import com.inmc.fishing.Fishing
import kr.inmc.core.gui.Icon
import kr.inmc.core.util.Text
import org.bukkit.Material
import org.bukkit.entity.Player

/**
 * 관리 첫 화면. `/낚시 관리` 가 여기를 연다.
 *
 * 세 종류를 한자리에 모은 이유는 관리자가 **무엇을 만들 수 있는지**를 먼저 보게 하기 위해서다.
 * 명령어로만 들어가면 미끼가 있다는 사실 자체를 모르고 넘어간다.
 */
class AdminMenu(
    fishing: Fishing,
    private val viewer: Player,
) : Menu(fishing, SIZE, Text.renderFlat("<dark_gray>낚시 — 관리</dark_gray>")) {

    override fun draw() {
        clear()
        fillEmpty(Icon.EDGE)

        set(
            SLOT_FISH,
            Icon.of(
                Material.TROPICAL_FISH,
                "<aqua>물고기</aqua>",
                listOf(
                    "<gray>낚을 수 있는 것 전부입니다.</gray>",
                    "<gray>쓰레기(장화·석탄)도 여기에 넣습니다.</gray>",
                    "",
                    "<dark_gray>등록 <white>${fishing.fish.size}</white>개</dark_gray>",
                ),
            ),
        ) { FishListMenu(fishing, viewer).open(viewer) }

        set(
            SLOT_ROD,
            Icon.of(
                Material.FISHING_ROD,
                "<yellow>낚싯대</yellow>",
                rodLore(),
            ),
        ) { RodListMenu(fishing, viewer).open(viewer) }

        set(
            SLOT_BAIT,
            Icon.of(
                Material.STRING,
                "<green>미끼</green>",
                listOf(
                    "<gray>왼손에 들면 적용되고</gray>",
                    "<gray>입질마다 한 개 소모됩니다.</gray>",
                    "",
                    "<dark_gray>등록 <white>${fishing.baits.size}</white>개</dark_gray>",
                ),
            ),
        ) { BaitListMenu(fishing, viewer).open(viewer) }

        set(
            SLOT_POTION,
            Icon.of(
                Material.POTION,
                "<light_purple>피로회복 물약</light_purple>",
                listOf(
                    "<gray>자동 낚시 피로도를 즉시 회복합니다.</gray>",
                    "<gray>자연 회복과 달리 절대 상한까지 닿습니다.</gray>",
                    "",
                    "<dark_gray>등록 <white>" + fishing.potions.size + "</white>개</dark_gray>",
                ),
            ),
        ) { PotionListMenu(fishing, viewer).open(viewer) }

        set(
            SLOT_FILLET,
            Icon.of(
                Material.COOKED_COD,
                "<gold>생선살</gold>",
                listOf(
                    "<gray>손질하면 나오는 것입니다.</gray>",
                    "<gray>등급과 트로피 여부로 찾습니다.</gray>",
                    "",
                    "<dark_gray>등록 <white>" + fishing.fillets.size + "</white>개</dark_gray>",
                ),
            ),
        ) { FilletItemListMenu(fishing, viewer).open(viewer) }

        set(SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }

    /**
     * 낚싯대는 **바깥 플러그인이 공급할 수 있다.** 그때는 여기서 고칠 수 없다는 것을 먼저 알려야
     * 한다 — 들어가서 편집 버튼이 없는 것을 보고 고장났다고 여기지 않게.
     */
    private fun rodLore(): List<String> {
        val source = fishing.rods
        return buildList {
            add("<gray>등급 보너스와 힘겨루기 성능을 정합니다.</gray>")
            add("")
            if (!source.editable) {
                add("<yellow>다른 플러그인이 공급하고 있습니다.</yellow>")
                add("<gray>낚싯대는 그쪽에서 만들고 고칩니다.</gray>")
                add("")
            }
            add("<dark_gray>등록 <white>${source.all().size}</white>개</dark_gray>")
        }
    }

    private companion object {
        const val SIZE = 27
        const val SLOT_FISH = 11
        const val SLOT_ROD = 12
        const val SLOT_BAIT = 13
        const val SLOT_POTION = 14
        const val SLOT_FILLET = 15
        const val SLOT_CLOSE = 22
    }
}
