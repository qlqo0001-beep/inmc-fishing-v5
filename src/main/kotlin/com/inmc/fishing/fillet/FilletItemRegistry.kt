package com.inmc.fishing.fillet

import com.inmc.fishing.Fishing
import com.inmc.fishing.fish.Trophy
import com.inmc.fishing.registry.DefinitionStore
import kr.inmc.core.item.StoredItem
import org.bukkit.configuration.ConfigurationSection

/** 손질 결과물 전부. `fillet-items.yml` 한 파일에 담긴다. */
class FilletItemRegistry(fishing: Fishing) : DefinitionStore<FilletItem>(
    fishing = fishing,
    fileName = "fillet-items.yml",
    header = "손질하면 나오는 것. 등급과 트로피 여부로 찾습니다.\n",
    what = "생선살",
    rootKey = "fillets",
) {

    /**
     * 이 등급·트로피에 해당하는 결과물.
     *
     * **트로피 전용을 안 만들었으면 일반 결과물로 내려온다.** 없으면 손질했는데 아무것도
     * 안 나오고, 그건 재료만 사라지는 것으로 보인다. 21칸(7등급 × 3)을 전부 채우기 전에는
     * 손질을 쓸 수 없게 하는 것보다 내려받는 편이 낫다.
     *
     * 레어 트로피는 일반 트로피로 한 단계, 거기서도 없으면 평범한 결과물로 또 한 단계 내려간다.
     */
    fun find(gradeId: String?, trophy: Trophy): FilletItem? {
        val grade = gradeId?.lowercase() ?: return null
        val candidates = all().filter { it.gradeId == grade }
        if (candidates.isEmpty()) return null

        for (step in fallbackChain(trophy)) {
            candidates.firstOrNull { it.trophy == step }?.let { return it }
        }
        return null
    }

    companion object {

        /**
         * 찾아내려가는 순서.
         *
         * **올라가지는 않는다.** 평범한 물고기에 트로피 전용 결과물을 주면
         * 트로피를 낚을 이유가 사라진다.
         */
        fun fallbackChain(trophy: Trophy): List<Trophy> = when (trophy) {
            Trophy.RARE -> listOf(Trophy.RARE, Trophy.NORMAL, Trophy.NONE)
            Trophy.NORMAL -> listOf(Trophy.NORMAL, Trophy.NONE)
            Trophy.NONE -> listOf(Trophy.NONE)
        }
    }

    override val role: String? get() = com.inmc.fishing.registry.FishingRoles.FILLET

    override fun idOf(value: FilletItem): String = value.id

    override fun itemOf(value: FilletItem): StoredItem = value.item

    override fun decorOf(value: FilletItem) = value.decor

    override fun read(key: String, section: ConfigurationSection): FilletItem? = FilletItem.load(key, section)

    override fun write(value: FilletItem, section: ConfigurationSection) = value.save(section)
}
