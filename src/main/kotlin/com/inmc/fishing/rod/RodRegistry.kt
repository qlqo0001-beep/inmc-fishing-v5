package com.inmc.fishing.rod

import com.inmc.fishing.Fishing
import com.inmc.fishing.registry.DefinitionStore
import kr.inmc.core.item.StoredItem
import org.bukkit.configuration.ConfigurationSection

/**
 * 이 플러그인이 직접 관리하는 낚싯대. `rods.yml` 과 GUI 가 내용을 정한다.
 *
 * [RodSource] 의 **기본 구현**이다. 커스텀아이템 플러그인이 생기면 그쪽 구현으로 갈아끼우게
 * 되는데, 그때도 이 클래스는 남는다 — 설정 파일에 적힌 낚싯대가 사라지면 안 되기 때문이다.
 */
class RodRegistry(fishing: Fishing) : DefinitionStore<Rod>(
    fishing = fishing,
    fileName = "rods.yml",
    header = "낚싯대. /낚시 관리 에서 손에 든 것을 그대로 등록할 수 있습니다.\n",
    what = "낚싯대",
    rootKey = "rods",
), RodSource {

    override val role: String? get() = com.inmc.fishing.registry.FishingRoles.ROD

    override fun idOf(value: Rod): String = value.id

    override fun itemOf(value: Rod): StoredItem = value.item

    override fun decorOf(value: Rod) = value.decor

    override fun read(key: String, section: ConfigurationSection): Rod? = Rod.load(key, section)

    override fun write(value: Rod, section: ConfigurationSection) = value.save(section)

    // --- RodSource -----------------------------------------------------------------

    override fun byId(id: String?): Rod? = get(id)

    override val editable: Boolean get() = true

    override fun register(rod: Rod) = put(rod)

    override fun unregister(id: String): Boolean = remove(id)
}
