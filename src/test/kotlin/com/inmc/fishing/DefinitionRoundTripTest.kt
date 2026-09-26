package com.inmc.fishing

import com.inmc.fishing.bait.Bait
import com.inmc.fishing.fish.Fish
import com.inmc.fishing.fish.SizeRange
import com.inmc.fishing.rod.Rod
import kr.inmc.core.item.ItemRef
import kr.inmc.core.item.StorageMode
import kr.inmc.core.item.StoredItem
import kr.inmc.core.store.DefinitionKey
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * GUI 로 고친 값이 **디스크를 한 번 왕복하고도 그대로인지** 본다.
 *
 * 이 검사가 없으면 관리자가 화면에서 값을 고치고, 서버를 껐다 켜면 원래대로 돌아가 있는 일이
 * 생긴다. 그 사이에 오류는 하나도 안 난다 — 저장할 때 빠뜨린 필드는 읽을 때도 조용히 기본값이
 * 되기 때문이다. 필드를 새로 더하면 여기가 먼저 빨개져야 한다.
 */
class DefinitionRoundTripTest {

    private fun item(mode: StorageMode = StorageMode.REFERENCE) = StoredItem(
        ref = ItemRef.Vanilla(Material.COD),
        material = Material.COD,
        mode = mode,
        displayName = "참치",
    )

    /** 저장한 뒤 같은 YAML 을 다시 읽는다. 파일을 거치는 것과 같은 경로다. */
    private fun roundTrip(save: (org.bukkit.configuration.ConfigurationSection) -> Unit): org.bukkit.configuration.ConfigurationSection {
        val written = YamlConfiguration()
        save(written.createSection("entry"))
        val reread = YamlConfiguration()
        reread.loadFromString(written.saveToString())
        return reread.getConfigurationSection("entry")!!
    }

    // --- 물고기 ---------------------------------------------------------------------

    @Test
    fun `물고기의 모든 칸이 왕복해도 그대로다`() {
        val original = Fish(
            id = "tuna",
            gradeId = "s",
            item = item(StorageMode.SNAPSHOT),
            weight = 37,
            size = SizeRange(min = 10.5, max = 200.0, avg = 60.25),
            doubleEnabled = false,
            commands = listOf("say {player} 대박", "eco give {player} 100"),
            fatigueRecovery = 250,
            maxSlots = 7,
        )

        val loaded = Fish.load("tuna", "s", roundTrip { original.save(it) }, defaultSlots = 10)

        assertEquals(original, loaded)
    }

    @Test
    fun `등급은 물고기의 필드로 저장된다`() {
        // 2세대는 등급이 파일 이름이었다. 필드가 아니면 GUI 에서 등급을 바꿀 수 없다.
        val fish = Fish(
            id = "cod", gradeId = "f", item = item(), weight = 1, size = SizeRange.NONE,
            doubleEnabled = true, commands = emptyList(), fatigueRecovery = 0, maxSlots = 5,
        )

        assertEquals("f", roundTrip { fish.save(it) }.getString("grade"))
    }

    @Test
    fun `크기 없는 물고기는 크기 칸을 남기지 않는다`() {
        val trash = Fish(
            id = "boot", gradeId = "f", item = item(), weight = 5, size = SizeRange.NONE,
            doubleEnabled = false, commands = emptyList(), fatigueRecovery = 0, maxSlots = 1,
        )

        val section = roundTrip { trash.save(it) }

        assertNull(section.get("max-size"), "크기 없는 것에 크기 칸이 생기면 안 된다")
        assertEquals(SizeRange.NONE, Fish.load("boot", "f", section, defaultSlots = 10)!!.size)
    }

    @Test
    fun `최소가 최대보다 큰 잘못된 값도 지워지지 않는다`() {
        // GUI 에서 최소를 먼저 올리면 잠깐 이 상태가 된다. 그때 값이 사라지면 관리자는
        // 무엇을 고쳐야 하는지 알 수 없다.
        val broken = SizeRange(min = 90.0, max = 50.0, avg = 70.0)

        val section = roundTrip { broken.save(it) }

        assertEquals(90.0, section.getDouble("min-size"))
        assertEquals(50.0, section.getDouble("max-size"))
    }

    // --- 낚싯대 ---------------------------------------------------------------------

    @Test
    fun `낚싯대의 모든 칸이 왕복해도 그대로다`() {
        val original = Rod(
            id = "legend",
            item = item(),
            gradeBonus = mapOf("s" to 9.0, "a" to 3.5),
            bigFishChance = 4.0,
            doubleChance = 2.5,
            maxFatigue = 3000,
            fatigueRecovery = 150,
            reelPower = 12.0,
            lineStrength = 40.0,
            reelDurability = 15.0,
        )

        assertEquals(original, Rod.load("legend", roundTrip { original.save(it) }))
    }

    @Test
    fun `낚싯대의 피로도 두 칸은 서로 다른 것이다`() {
        // 하나는 자연 회복의 한계를, 다른 하나는 1회 회복량을 올린다. 한 칸으로 합치면
        // "최대치는 그대로인데 빨리 차는" 낚싯대를 만들 수 없다.
        val rod = Rod(id = "r", item = item(), maxFatigue = 500, fatigueRecovery = 50)

        val section = roundTrip { rod.save(it) }

        assertEquals(500, section.getInt("options.max-fatigue"))
        assertEquals(50, section.getInt("options.fatigue-recovery"))
    }

    // --- 미끼 ----------------------------------------------------------------------

    @Test
    fun `미끼의 모든 칸이 왕복해도 그대로다`() {
        val original = Bait(
            id = "premium",
            item = item(),
            gradeBonus = mapOf("c" to 3.0, "b" to 2.0),
            sizePercent = 10.0,
            sizeFixed = 2.5,
        )

        assertEquals(original, Bait.load("premium", roundTrip { original.save(it) }))
    }

    @Test
    fun `미끼 보정이 추첨 보정으로 그대로 넘어간다`() {
        val bait = Bait(id = "b", item = item(), gradeBonus = mapOf("s" to 1.0), sizePercent = 20.0, sizeFixed = 3.0)

        val bonuses = bait.bonuses()

        assertEquals(mapOf("s" to 1.0), bonuses.gradeFlatBonus, "등급 보정은 덧셈 축으로 간다")
        assertEquals(emptyMap(), bonuses.gradeMultipliers, "곱셈 축은 환경 보정 전용이다")
        assertEquals(20.0, bonuses.sizePercent)
        assertEquals(3.0, bonuses.sizeFixed)
    }

    // --- 이름 규칙 -------------------------------------------------------------------

    @Test
    fun `점이 들어간 이름은 등록 이름으로 쓸 수 없다`() {
        // YamlConfiguration 이 점을 경로 구분자로 다뤄서, 허용하면 그 항목이 조용히 사라진다.
        assertTrue(DefinitionKey.isValid("golden_rod"))
        assertTrue(DefinitionKey.isValid("참치"))
        assertTrue(DefinitionKey.isValid("rod-2"))

        assertFalse(DefinitionKey.isValid("boss.rod"), "점이 통과하면 안 된다")
        assertFalse(DefinitionKey.isValid("has space"))
        assertFalse(DefinitionKey.isValid(""))
        assertFalse(DefinitionKey.isValid(null))
    }

    @Test
    fun `점이 들어간 이름을 강제로 저장하면 목록에서 사라진다`() {
        // 위 규칙이 왜 필요한지를 못박는다. 규칙을 느슨하게 고치려는 사람이 이걸 먼저 본다.
        //
        // 값이 지워지는 것이 아니라 **한 칸 더 깊이 들어간다.** 레지스트리는 목록을
        // `getKeys(false)` 로 훑으므로 `boss.rod` 가 아니라 `boss` 를 보게 되고, 그 안에는
        // 읽을 것이 없어 그 항목은 통째로 없던 것이 된다. 오류는 나지 않는다.
        val config = YamlConfiguration()
        config.createSection("fish").createSection("boss.rod").set("weight", 5)

        val reread = YamlConfiguration()
        reread.loadFromString(config.saveToString())
        val root = reread.getConfigurationSection("fish")!!

        assertEquals(setOf("boss"), root.getKeys(false), "목록에는 점 앞부분만 보인다")
        assertNull(root.getConfigurationSection("boss")!!.get("weight"), "정작 값은 한 칸 더 안에 있다")
        assertNotNull(root.get("boss.rod.weight"), "값 자체는 남아 있지만 목록으로는 닿지 않는다")
    }
}
