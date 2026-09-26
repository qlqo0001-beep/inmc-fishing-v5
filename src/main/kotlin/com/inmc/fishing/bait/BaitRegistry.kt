package com.inmc.fishing.bait

import com.inmc.fishing.Fishing
import com.inmc.fishing.registry.DefinitionStore
import kr.inmc.core.item.StoredItem
import org.bukkit.configuration.ConfigurationSection

/** 미끼 전부. `baits.yml` 한 파일에 담긴다. */
class BaitRegistry(fishing: Fishing) : DefinitionStore<Bait>(
    fishing = fishing,
    fileName = "baits.yml",
    header = "미끼. 왼손에 들면 적용되고 입질마다 한 개 소모됩니다.\n",
    what = "미끼",
    rootKey = "baits",
) {

    override val role: String? get() = com.inmc.fishing.registry.FishingRoles.BAIT

    override fun idOf(value: Bait): String = value.id

    override fun itemOf(value: Bait): StoredItem = value.item

    override fun decorOf(value: Bait) = value.decor

    override fun read(key: String, section: ConfigurationSection): Bait? = Bait.load(key, section)

    override fun write(value: Bait, section: ConfigurationSection) = value.save(section)
}
