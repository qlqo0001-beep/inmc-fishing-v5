package com.inmc.fishing

import com.inmc.fishing.grade.Grade
import com.inmc.fishing.rod.Rod
import com.inmc.fishing.rod.RodLoreFormat
import kr.inmc.core.item.ItemRef
import kr.inmc.core.item.StoredItem
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `item-format.yml` — 지급 낚싯대의 능력 로어(2세대와 같은 키·같은 모양).
 */
class RodLoreFormatTest {

    private fun grade(id: String, color: String) = Grade(
        id = id, displayName = id.uppercase(), weight = 1.0, inputCount = 1, timeSeconds = 1.0, color = color,
    )

    private val grades = listOf(grade("f", "&7"), grade("s", "&6"))

    private fun rod(vararg bonus: Pair<String, Double>, reelPower: Double = 0.0, doubleChance: Double = 0.0) = Rod(
        id = "r",
        item = StoredItem(ItemRef.Vanilla(org.bukkit.Material.FISHING_ROD), org.bukkit.Material.FISHING_ROD),
        gradeBonus = bonus.toMap(),
        reelPower = reelPower,
        doubleChance = doubleChance,
    )

    @Test
    fun `능력이 없으면 아무 줄도 붙지 않는다`() {
        // 구분선만 덩그러니 남으면 안 된다.
        assertTrue(RodLoreFormat().lines(rod(), grades).isEmpty())
    }

    @Test
    fun `2세대와 같은 모양으로 쌓인다`() {
        val lines = RodLoreFormat().lines(rod("s" to 2.0, reelPower = 10.0, doubleChance = 1.5), grades)
        assertEquals("&8━━━━━━━━━━━━━━━━━━━━", lines.first())
        assertTrue("&6✧ 등급 보너스" in lines)
        // 양수는 등급 고유 색, 정수는 소수점 없이
        assertTrue("  &8▸ &7S 등급   &6+2" in lines, lines.toString())
        assertTrue("  &8▸ &7더블 확률   &a+1.5%" in lines, lines.toString())
        assertTrue("  &8▸ &7릴 파워   &a+10" in lines, lines.toString())
        assertEquals("&8━━━━━━━━━━━━━━━━━━━━", lines.last(), "footer-divider")
    }

    @Test
    fun `0 인 등급 보너스는 적지 않는다`() {
        val lines = RodLoreFormat().lines(rod("f" to 0.0, "s" to 1.0), grades)
        assertTrue(lines.none { it.contains("F 등급") })
    }

    @Test
    fun `배포 파일을 읽으면 기본값과 같다`() {
        val config = YamlConfiguration.loadConfiguration(File("src/main/resources/item-format.yml"))
        assertEquals(RodLoreFormat(), RodLoreFormat.load(config.getConfigurationSection("rod")))
    }
}
