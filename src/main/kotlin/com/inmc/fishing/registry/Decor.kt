package com.inmc.fishing.registry

import kr.inmc.core.util.Text
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.inventory.ItemStack

/**
 * 아이템에 씌우는 이름과 설명.
 *
 * **왜 필요한가.** core 의 [kr.inmc.core.item.StoredItem] 이 `minecraft:cod` 같은 바닐라
 * 참조를 풀면 **꾸미지 않은 생대구**가 나온다. 그래서 배포 기본값처럼 바닐라 아이템에 이름만
 * 붙여 쓰는 경우, 이 층이 없으면 예순 종류가 전부 똑같은 회색 대구가 된다.
 *
 * **비어 있으면 아무것도 하지 않는다.** 관리자가 MMOItems 무기나 손으로 꾸민 아이템을 그대로
 * 등록했을 때 그 모습을 덮어쓰면 안 되기 때문이다 — 등록한 것과 지급되는 것이 달라진다.
 */
data class Decor(val name: String = "", val lore: List<String> = emptyList()) {

    val isEmpty: Boolean get() = name.isBlank() && lore.isEmpty()

    /**
     * 스택에 씌운다.
     *
     * @param extra 로어 뒤에 덧붙일 줄. 크기나 트로피 표시처럼 **개체마다 다른 것**이 여기 온다.
     */
    fun applyTo(stack: ItemStack, extra: List<String> = emptyList()) {
        if (isEmpty && extra.isEmpty()) return
        val meta = stack.itemMeta ?: return

        if (name.isNotBlank()) meta.displayName(Text.renderFlat(name))
        val lines = lore + extra
        if (lines.isNotEmpty()) meta.lore(lines.map { Text.renderFlat(it) })

        stack.itemMeta = meta
    }

    fun save(section: ConfigurationSection) {
        if (name.isNotBlank()) section.set(KEY_NAME, name)
        if (lore.isNotEmpty()) section.set("lore", lore)
    }

    companion object {

        val NONE = Decor()

        /**
         * 읽는다. **`name` 은 절대 보지 않는다.**
         *
         * `name` 은 core 의 [kr.inmc.core.item.StoredItem] 이 쓰는 **식별용 평문 이름**이고
         * 여기 `display-name` 은 색코드가 들어있는 **표시용 이름**이다. 한 키로 쓰면
         * `&f[F] 물약` 과 실제 아이템의 평문 이름 `[F] 물약` 이 달라 식별이 조용히 깨진다 —
         * 물약을 마셨는데 회복이 안 된다.
         *
         * `name` 을 대체값으로 쓰고 싶어지겠지만 그러면 **손에 든 것을 등록했을 때 색이
         * 날아간다.** 스냅샷에는 색이 들어간 이름이 들어 있는데, 그 위에 평문 이름을
         * 덮어씌우게 되기 때문이다. 그래서 `display-name` 이 없으면 아무것도 하지 않는다.
         */
        fun load(section: ConfigurationSection): Decor = Decor(
            name = section.getString(KEY_NAME).orEmpty(),
            lore = section.getStringList("lore"),
        )

        const val KEY_NAME = "display-name"
    }
}
