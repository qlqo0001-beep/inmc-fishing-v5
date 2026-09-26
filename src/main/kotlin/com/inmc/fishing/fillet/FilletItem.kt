package com.inmc.fishing.fillet

import com.inmc.fishing.fish.Trophy
import kr.inmc.core.item.StoredItem
import org.bukkit.configuration.ConfigurationSection

/**
 * 손질해서 나오는 것 — 생선살 하나.
 *
 * 어느 물고기를 손질했는지는 보지 않는다. **등급과 트로피 여부**만 본다. 물고기 수백 종마다
 * 결과물을 따로 만들게 하면 관리자가 물고기를 하나 추가할 때마다 결과물도 만들어야 하고,
 * 빠뜨리면 손질했는데 아무것도 안 나온다.
 */
data class FilletItem(
    val id: String,
    val item: StoredItem,
    val gradeId: String,
    val trophy: Trophy,
    val decor: com.inmc.fishing.registry.Decor = com.inmc.fishing.registry.Decor.NONE,
) {

    fun save(section: ConfigurationSection) {
        item.save(section)
        decor.save(section)
        section.set("grade", gradeId)
        section.set("trophy-type", token(trophy))
    }

    companion object {

        fun load(key: String, section: ConfigurationSection): FilletItem? {
            val id = section.getString("id").orEmpty().ifBlank { key }.lowercase()
            if (id.isBlank()) return null
            val item = StoredItem.load(section) ?: return null

            return FilletItem(
                id = id,
                item = item,
                gradeId = section.getString("grade").orEmpty().lowercase(),
                trophy = parseTrophy(section.getString("trophy-type")),
                decor = com.inmc.fishing.registry.Decor.load(section),
            )
        }

        /**
         * 설정에 적는 낱말.
         *
         * **[Trophy.NORMAL] 을 `normal` 이라고 적지 않는다.** 2세대 `fillet-items.yml` 에서
         * `normal` 은 "트로피가 **아님**"이라는 뜻이었다. 같은 낱말이 정반대를 가리키므로,
         * 그 표기를 그대로 쓰면 옛 파일을 붙여넣은 관리자가 트로피 전용 결과물을 평범한
         * 물고기에 물려놓고 왜 안 나오는지 묻게 된다. 우리는 `trophy` 라고 적는다.
         */
        fun token(trophy: Trophy): String = when (trophy) {
            Trophy.RARE -> "rare"
            Trophy.NORMAL -> "trophy"
            Trophy.NONE -> "none"
        }

        /** [token] 의 역. 2세대의 `normal`(= 트로피 아님)도 뜻 그대로 받는다. */
        fun parseTrophy(raw: String?): Trophy = when (raw?.lowercase()) {
            "rare" -> Trophy.RARE
            "trophy" -> Trophy.NORMAL
            else -> Trophy.NONE
        }
    }
}
