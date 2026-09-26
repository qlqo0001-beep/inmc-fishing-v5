package com.inmc.fishing

import com.inmc.fishing.rod.Rod
import com.inmc.fishing.rod.RodSource
import kr.inmc.core.item.ItemRef
import kr.inmc.core.item.StoredItem
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 낚싯대를 **바깥에서 공급할 수 있는지** 확인한다.
 *
 * 앞으로 자체 커스텀아이템 플러그인이 생기면 낚싯대는 거기서 만드는 편이 낫다 — 아이템 정의가
 * 한 곳에 모인다. 그때 필요한 것이 [RodSource] 구현 하나뿐이어야 하고, 낚시 쪽 코드는 한 줄도
 * 바뀌지 않아야 한다. 이 테스트는 **그 계약이 실제로 성립하는지**를 본다 — 여기가 컴파일되지
 * 않으면 인터페이스가 플러그인 내부에 새고 있는 것이다.
 *
 * 서버 없이 돈다. 이것도 계약의 일부다: [RodSource] 가 Bukkit 서버를 요구하면 바깥 플러그인이
 * 붙기 전에 검증할 방법이 없다.
 */
class RodSourceTest {

    private fun rod(id: String) = Rod(
        id = id,
        item = StoredItem(ItemRef.Vanilla(Material.FISHING_ROD), Material.FISHING_ROD),
        gradeBonus = mapOf("s" to 5.0),
    )

    /** 바깥 플러그인 흉내. 목록을 직접 들고 있고, 여기서는 고칠 수 없다고 말한다. */
    private class ExternalRods(private val rods: List<Rod>) : RodSource {
        override fun all(): List<Rod> = rods
        override fun byId(id: String?): Rod? = rods.firstOrNull { it.id == id?.lowercase() }
        override fun identify(stack: ItemStack?): Rod? = null
    }

    @Test
    fun `바깥 공급처는 세 가지만 구현하면 된다`() {
        // 이 클래스가 컴파일된다는 사실 자체가 계약이다. 필수 멤버가 늘면 여기가 먼저 깨진다.
        val source: RodSource = ExternalRods(listOf(rod("legend"), rod("basic")))

        assertEquals(2, source.all().size)
        assertEquals("legend", source.byId("LEGEND")?.id, "조회는 대소문자를 가리지 않아야 한다")
        assertNull(source.byId("없는것"))
        assertNull(source.identify(null))
    }

    @Test
    fun `바깥 공급처는 기본적으로 편집 불가다`() {
        // GUI 가 이 값을 보고 만들기·지우기 버튼을 감춘다. 기본값이 true 였다면 붙이는 쪽이
        // 깜빡했을 때 관리자가 눌러도 아무 일도 안 일어나는 버튼이 남는다.
        val source: RodSource = ExternalRods(listOf(rod("legend")))

        assertFalse(source.editable)
        assertFalse(source.unregister("legend"), "지울 수 없으면 false 를 돌려준다")
    }

    @Test
    fun `편집 불가 공급처에 등록하면 조용히 넘어가지 않는다`() {
        // 아무 일도 안 하고 성공한 척하면, 등록했는데 목록에 없는 상태가 된다.
        val source: RodSource = ExternalRods(emptyList())

        assertFailsWith<UnsupportedOperationException> { source.register(rod("새것")) }
    }

    @Test
    fun `편집 가능한 공급처는 편집 가능하다고 말한다`() {
        val editable = object : RodSource {
            private val rods = LinkedHashMap<String, Rod>()
            override fun all(): List<Rod> = rods.values.toList()
            override fun byId(id: String?): Rod? = id?.let { rods[it.lowercase()] }
            override fun identify(stack: ItemStack?): Rod? = null
            override val editable: Boolean get() = true
            override fun register(rod: Rod) {
                rods[rod.id] = rod
            }

            override fun unregister(id: String): Boolean = rods.remove(id.lowercase()) != null
        }

        assertTrue(editable.editable)
        editable.register(rod("legend"))
        assertEquals(1, editable.all().size)
        assertTrue(editable.unregister("legend"))
        assertEquals(0, editable.all().size)
    }

    @Test
    fun `낚싯대 보너스는 덧셈 축으로만 간다`() {
        // 곱셈이면 가중치 1 인 S 에 배수를 줘봐야 1 이 늘 뿐이라, 좋은 낚싯대가 희귀 등급을
        // 열어준다는 약속이 성립하지 않는다. 공급처가 바뀌어도 이 규칙은 그대로여야 한다.
        val bonuses = rod("legend").bonuses()

        assertEquals(mapOf("s" to 5.0), bonuses.gradeFlatBonus)
        assertEquals(emptyMap(), bonuses.gradeMultipliers, "곱셈 축은 환경 보정 전용이다")
    }
}
