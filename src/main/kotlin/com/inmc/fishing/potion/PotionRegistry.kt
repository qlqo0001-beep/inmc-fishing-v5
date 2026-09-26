package com.inmc.fishing.potion

import com.inmc.fishing.Fishing
import com.inmc.fishing.registry.DefinitionStore
import kr.inmc.core.item.StoredItem
import org.bukkit.configuration.ConfigurationSection

/** 피로회복 물약 전부. `potions.yml` 한 파일에 담긴다. */
class PotionRegistry(fishing: Fishing) : DefinitionStore<Potion>(
    fishing = fishing,
    fileName = "potions.yml",
    header = "피로회복 물약. 손에 들고 우클릭하면 한 개를 쓰고 즉시 회복합니다.\n",
    what = "물약",
    rootKey = "potions",
) {

    /**
     * 그 등급의 물약. 같은 등급이 여럿이면 **먼저 등록된 것**이다.
     *
     * 지급 명령(`/낚시 지급 물약 <등급>`)이 쓴다. 2세대와 같은 사용감을 유지하기 위한 조회이고,
     * 이름으로 직접 지급하는 길은 [get] 이 따로 있다.
     */
    fun ofGrade(gradeId: String?): Potion? {
        val target = gradeId?.lowercase() ?: return null
        return all().firstOrNull { it.gradeId == target }
    }

    override val role: String? get() = com.inmc.fishing.registry.FishingRoles.POTION

    override fun idOf(value: Potion): String = value.id

    override fun itemOf(value: Potion): StoredItem = value.item

    override fun decorOf(value: Potion) = value.decor

    override fun read(key: String, section: ConfigurationSection): Potion? = Potion.load(key, section)

    override fun write(value: Potion, section: ConfigurationSection) = value.save(section)
}
