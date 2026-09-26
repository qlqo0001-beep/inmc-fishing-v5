package com.inmc.fishing.potion

import kr.inmc.core.item.StoredItem
import org.bukkit.configuration.ConfigurationSection

/**
 * 피로회복 물약 하나. 손에 들고 우클릭하면 한 개를 쓰고 [amount] 만큼 즉시 회복한다.
 *
 * **자연 회복과 다른 길이다.** 자연 회복은 `fatigue.default` 까지만 올리지만 물약은 절대
 * 상한(`fatigue.max`)까지 닿는다. 그 차이가 물약의 존재 이유다.
 *
 * 2세대는 등급(f~s)을 **키로** 썼다. 그러면 등급당 한 종류밖에 만들 수 없고, 이벤트 물약이나
 * 상점 전용 물약을 넣을 자리가 없다. 여기서는 이름이 키이고 등급은 **필드**다 — 지급 명령이
 * 등급으로 찾는 것은 그대로 되면서, 같은 등급에 여러 종류를 둘 수 있다.
 */
data class Potion(
    val id: String,
    val item: StoredItem,
    /** 회복량. 0 이면 마셔도 아무 일도 없다. */
    val amount: Int = 0,
    /** 연관 등급. 비워둘 수 있다 — 지급 명령이 등급으로 찾을 때만 쓴다. */
    val gradeId: String = "",
    val decor: com.inmc.fishing.registry.Decor = com.inmc.fishing.registry.Decor.NONE,
) {

    fun save(section: ConfigurationSection) {
        item.save(section)
        decor.save(section)
        section.set("amount", amount)
        section.set("grade", gradeId)
    }

    companion object {

        fun load(key: String, section: ConfigurationSection): Potion? {
            val id = section.getString("id").orEmpty().ifBlank { key }.lowercase()
            if (id.isBlank()) return null
            val item = StoredItem.load(section) ?: return null

            return Potion(
                id = id,
                item = item,
                amount = section.getInt("amount", 0).coerceAtLeast(0),
                gradeId = section.getString("grade").orEmpty().lowercase(),
                decor = com.inmc.fishing.registry.Decor.load(section),
            )
        }
    }
}
