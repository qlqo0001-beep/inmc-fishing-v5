package com.inmc.fishing.rod

import org.bukkit.inventory.ItemStack

/**
 * 낚싯대 정의가 **어디서 오는가**.
 *
 * 지금은 이 플러그인이 `rods.yml` 과 GUI 로 직접 관리하지만([YamlRodSource]), 앞으로 자체
 * 커스텀아이템 플러그인이 생기면 낚싯대를 거기서 만드는 편이 낫다 — 아이템 정의가 한 곳에
 * 모이고, 능력 로어나 강화 같은 것을 그 플러그인이 통째로 담당할 수 있다.
 *
 * 그때 필요한 것은 **이 인터페이스를 구현하는 다른 구현체 하나**뿐이다. 추첨·힘겨루기·로어는
 * 전부 이 인터페이스만 보고 있으므로 한 줄도 바뀌지 않는다.
 *
 * ```kotlin
 * // 커스텀아이템 플러그인이 생기면
 * fishing.rods = CustomItemRodSource(customItemPlugin)
 * ```
 *
 * 그래서 [com.inmc.fishing.Fishing.rods] 는 `val` 이 아니라 **바꿔 끼울 수 있는 자리**다.
 */
interface RodSource {

    /** 이 공급처가 아는 낚싯대 전부. 목록 GUI 와 명령어 추천이 쓴다. */
    fun all(): List<Rod>

    fun byId(id: String?): Rod?

    /**
     * 이 아이템이 어떤 낚싯대인지. 아니면 null.
     *
     * **핫 패스다** — 낚싯대를 던질 때마다 불린다.
     */
    fun identify(stack: ItemStack?): Rod?

    /**
     * 이 공급처가 낚싯대를 **만들고 고칠 수 있는지**.
     *
     * 외부 플러그인이 공급처가 되면 여기서 고칠 수 없다 — 그쪽에서 고쳐야 한다. GUI 가 이
     * 값을 보고 편집 버튼을 감춘다. 열어두고 눌렀을 때 조용히 아무 일도 안 하는 것보다 낫다.
     */
    val editable: Boolean get() = false

    /** 편집 가능한 공급처만 구현한다. */
    fun register(rod: Rod) {
        throw UnsupportedOperationException("이 낚싯대 공급처는 편집할 수 없습니다")
    }

    fun unregister(id: String): Boolean = false
}
