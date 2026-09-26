package com.inmc.fishing

import com.inmc.fishing.bait.Bait
import com.inmc.fishing.registry.Decor
import com.inmc.fishing.registry.FishingRoles
import com.inmc.fishing.rod.Rod
import kr.inmc.core.integration.ItemRoles
import kr.inmc.core.item.ItemRef
import kr.inmc.core.item.StoredItem
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 커스텀아이템으로 옮긴 정의가 **역할 값을 한 번 거치고도 그대로인지** — 낚시 관리 화면이 고친 값이 커스텀아이템의 `items.yml` 에
 * 글자로 적혔다가 다시 정의로 돌아온다(`DefinitionStore.values` → `rebuild`). 타입을 잃으면 YAML 읽기가 글자 "30" 을 숫자로 안 보고
 * 조용히 기본값 0 이 된다.
 */
class FishingRolesTest {

    private val ours = StoredItem(ItemRef.Namespaced("inmc", "legend"), Material.FISHING_ROD)

    /** 정의 → 값 → (역할을 맡은 아이템) → 정의. 실제 경로와 같다. */
    private fun <T> roundTrip(save: (org.bukkit.configuration.ConfigurationSection) -> Unit, load: (org.bukkit.configuration.ConfigurationSection) -> T): T {
        val written = YamlConfiguration()
        save(written)
        val values = FishingRoles.flatten(written)
        // 커스텀아이템이 값을 글자로 적었다 다시 읽는다.
        val stored = YamlConfiguration()
        for ((key, value) in values) stored.set(key, value)
        val reread = YamlConfiguration().apply { loadFromString(stored.saveToString()) }
        val back = reread.getKeys(false).associateWith { reread.getString(it).orEmpty() }
        val section = FishingRoles.unflatten(back)
        ours.save(section)
        return load(section)
    }

    @Test
    fun `낚싯대의 모든 칸이 역할 값을 거쳐도 그대로다(아이템은 역할을 맡은 것)`() {
        val original = Rod(
            id = "legend", item = StoredItem(ItemRef.Vanilla(Material.FISHING_ROD), Material.FISHING_ROD, displayName = "전설 낚싯대"),
            gradeBonus = mapOf("s" to 9.0, "a" to 3.5), bigFishChance = 4.0, doubleChance = 2.5,
            maxFatigue = 3000, fatigueRecovery = 150, reelPower = 12.0, lineStrength = 40.0, reelDurability = 15.0,
            decor = Decor("<gold>전설", listOf("설명")),
        )
        val back = roundTrip({ original.save(it) }) { Rod.load("legend", it)!! }
        assertEquals(original.copy(item = ours, decor = Decor.NONE), back)
    }

    @Test
    fun `아이템 정체와 겉모습은 값에 넣지 않는다`() {
        val yaml = YamlConfiguration()
        Bait(id = "worm", item = StoredItem(ItemRef.Vanilla(Material.WHEAT_SEEDS), Material.WHEAT_SEEDS, displayName = "지렁이"), sizePercent = 5.0,
            decor = Decor("<green>지렁이", listOf("꿈틀"))).save(yaml)
        val values = FishingRoles.flatten(yaml)
        for (key in listOf("item", "mode", "material", "name", "snapshot", Decor.KEY_NAME, "lore")) assertFalse(key in values, "$key 가 값에 들어갔다: $values")
        assertEquals("5.0", values["size-bonus-percent"])
    }

    @Test
    fun `목록 값과 점 든 경로가 되돌아온다`() {
        val yaml = YamlConfiguration()
        yaml.set("options.max-fatigue", 3000)
        yaml.set("list", listOf("a", "b"))
        yaml.set("flag", true)
        val back = FishingRoles.unflatten(FishingRoles.flatten(yaml))
        assertEquals(3000, back.getInt("options.max-fatigue"))
        assertEquals(listOf("a", "b"), back.getStringList("list"))
        assertTrue(back.getBoolean("flag"))
    }

    @Test
    fun `옛 아이템은 값에 숨겨 돌아온다`() {
        val old = StoredItem(ItemRef.Vanilla(Material.PAPER), Material.PAPER, displayName = "보호권")
        val values = ItemRoles.withLegacy(mapOf("kind" to "x"), old)
        assertEquals(old, ItemRoles.legacy(values))
        assertEquals(mapOf("kind" to "x"), ItemRoles.withLegacy(mapOf("kind" to "x"), StoredItem(ItemRef.Namespaced("inmc", "a"), Material.PAPER)))
    }
}
