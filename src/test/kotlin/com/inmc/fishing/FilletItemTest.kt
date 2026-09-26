package com.inmc.fishing

import com.inmc.fishing.fillet.FilletItem
import com.inmc.fishing.fish.Trophy
import com.inmc.fishing.potion.Potion
import kr.inmc.core.item.ItemRef
import kr.inmc.core.item.StoredItem
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 손질 결과물과 피로회복 물약.
 *
 * 둘 다 2세대에 **별도 파일**로 있던 것이고, 옮겨 적는 과정에서 빠지기 쉬운 자리다.
 * 특히 `trophy-type` 의 `normal` 은 2세대에서 "트로피가 **아님**"을 뜻했다 — 그대로 읽으면
 * 트로피 전용 결과물이 평범한 물고기에 나온다.
 */
class FilletItemTest {

    private fun item(material: Material = Material.COD) =
        StoredItem(ItemRef.Vanilla(material), material)

    private fun roundTrip(save: (ConfigurationSection) -> Unit): ConfigurationSection {
        val written = YamlConfiguration()
        save(written.createSection("entry"))
        val reread = YamlConfiguration()
        reread.loadFromString(written.saveToString())
        return reread.getConfigurationSection("entry")!!
    }

    // --- 생선살 --------------------------------------------------------------------

    @Test
    fun `생선살이 왕복해도 그대로다`() {
        for (trophy in Trophy.entries) {
            // id 는 소문자가 표준이다 - 조회가 전부 lowercase() 를 거친다.
            val original = FilletItem("f_" + FilletItem.token(trophy), item(), "f", trophy)

            assertEquals(original, FilletItem.load(original.id, roundTrip { original.save(it) }))
        }
    }

    @Test
    fun `일반 트로피를 normal 이라고 적지 않는다`() {
        // 2세대 fillet-items.yml 에서 `normal` 은 "트로피가 아님"이었다. 같은 낱말을 쓰면
        // 옛 파일을 붙여넣은 관리자가 트로피 전용 결과물을 평범한 물고기에 물려놓게 된다.
        assertEquals("trophy", FilletItem.token(Trophy.NORMAL))
        assertEquals("none", FilletItem.token(Trophy.NONE))
        assertEquals("rare", FilletItem.token(Trophy.RARE))
    }

    @Test
    fun `2세대의 normal 은 트로피 아님으로 읽힌다`() {
        assertEquals(Trophy.NONE, FilletItem.parseTrophy("normal"))
        assertEquals(Trophy.NONE, FilletItem.parseTrophy(null))
        assertEquals(Trophy.NONE, FilletItem.parseTrophy("알 수 없는 값"))
        assertEquals(Trophy.NORMAL, FilletItem.parseTrophy("trophy"))
        assertEquals(Trophy.RARE, FilletItem.parseTrophy("RARE"))
    }

    // --- 물약 ----------------------------------------------------------------------

    @Test
    fun `물약이 왕복해도 그대로다`() {
        val original = Potion("s_potion", item(Material.POTION), amount = 16000, gradeId = "s")

        assertEquals(original, Potion.load(original.id, roundTrip { original.save(it) }))
    }

    @Test
    fun `등급을 안 적은 물약도 등록된다`() {
        // 이벤트 물약이나 상점 전용 물약에는 등급이 없다. 등급을 필수로 하면 그런 것을
        // 만들 수 없다 - 2세대는 등급이 곧 키라서 아예 불가능했다.
        val original = Potion("event", item(Material.POTION), amount = 500)

        val loaded = Potion.load(original.id, roundTrip { original.save(it) })

        assertEquals(original, loaded)
        assertEquals("", loaded!!.gradeId)
    }

    @Test
    fun `회복량이 음수면 0 으로 잘린다`() {
        val section = YamlConfiguration().createSection("p")
        item(Material.POTION).save(section)
        section.set("amount", -500)

        assertEquals(0, Potion.load("p", section)!!.amount)
    }

    @Test
    fun `아이템 정체가 없으면 읽지 않는다`() {
        // 참조도 스냅샷도 없는 항목은 지급할 수가 없다. 조용히 등록되면 나중에 "지급했는데
        // 아무것도 안 왔다"가 된다.
        val empty = YamlConfiguration().createSection("p")
        empty.set("amount", 100)

        assertNull(Potion.load("p", empty))
        assertNull(FilletItem.load("f", empty))
    }
}
