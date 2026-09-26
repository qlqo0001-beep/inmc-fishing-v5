package com.inmc.fishing

import kr.inmc.core.gui.Paging
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 화면 슬롯 배치를 못박는다.
 *
 * 슬롯 충돌은 컴파일러가 잡지 못하고 테스트도 보통 놓친다 — 두 버튼이 같은 칸에 그려지면
 * 나중에 그린 쪽만 보이고, **안 보이는 버튼의 클릭 핸들러는 그대로 남아** 엉뚱한 동작을 한다.
 * 배치를 소스에서 직접 읽어 검사하므로 상수를 옮기면 여기가 따라온다.
 */
class MenuLayoutTest {

    private fun slotsOf(file: String): Map<String, Int> {
        val source = File("src/main/kotlin/com/inmc/fishing/gui/$file")
        assertTrue(source.exists(), "소스를 찾을 수 없습니다: ${source.absolutePath}")
        return Regex("""const val (SLOT_[A-Z_]+) = (\d+)""")
            .findAll(source.readText())
            .associate { it.groupValues[1] to it.groupValues[2].toInt() }
    }

    /** 편집 화면들이 공통으로 쓰는 버튼. 여기에 상수가 겹치면 안 된다. */
    private val shared = mapOf(
        "Paging.SLOT_BACK" to Paging.SLOT_BACK,
        "Paging.SLOT_CLOSE" to Paging.SLOT_CLOSE,
    )

    private fun assertLayout(file: String, size: Int = 54) {
        val slots = slotsOf(file)
        assertTrue(slots.isNotEmpty(), "$file 에서 슬롯 상수를 하나도 못 읽었습니다")

        val all = slots + shared
        assertEquals(
            all.size,
            all.values.toSet().size,
            "$file 슬롯 충돌: " + all.entries.groupBy { it.value }.filterValues { it.size > 1 },
        )
        for ((name, slot) in slots) {
            assertTrue(slot in 0 until size, "$file 의 $name = $slot 이 ${size}칸 창을 벗어납니다")
        }
    }

    @Test
    fun `물고기 설정 화면의 슬롯이 겹치지 않는다`() = assertLayout("FishEditMenu.kt")

    @Test
    fun `낚싯대 설정 화면의 슬롯이 겹치지 않는다`() = assertLayout("RodEditMenu.kt")

    @Test
    fun `미끼 설정 화면의 슬롯이 겹치지 않는다`() = assertLayout("BaitEditMenu.kt")

    @Test
    fun `물약 설정 화면의 슬롯이 겹치지 않는다`() = assertLayout("PotionEditMenu.kt")

    @Test
    fun `생선살 설정 화면의 슬롯이 겹치지 않는다`() = assertLayout("FilletItemEditMenu.kt")

    @Test
    fun `관리 첫 화면의 슬롯이 겹치지 않고 세 줄 안에 들어간다`() {
        val slots = slotsOf("AdminMenu.kt")

        assertEquals(slots.size, slots.values.toSet().size, "슬롯 충돌: $slots")
        for ((name, slot) in slots) assertTrue(slot in 0 until 27, "$name = $slot 이 27칸 창을 벗어납니다")
    }

    @Test
    fun `손질대 화면의 슬롯이 겹치지 않고 여섯 줄 안에 들어간다`() {
        // 사람마다 칸 수가 다르고(최대 35, 2세대 setSlots) 2세대와 같은 6줄 배치를 쓴다.
        val slots = slotsOf("FilletMenu.kt")
        val positions = Regex("""val SLOT_POSITIONS = listOf\(([^)]*)\)""")
            .find(File("src/main/kotlin/com/inmc/fishing/gui/FilletMenu.kt").readText())
            ?.groupValues?.get(1)
            ?.split(",")?.mapNotNull { it.trim().toIntOrNull() }
            .orEmpty()

        assertTrue(positions.isNotEmpty(), "슬롯 자리 목록을 못 읽었습니다")
        assertEquals(positions.size, positions.toSet().size, "손질 슬롯 자리가 겹칩니다: $positions")

        assertEquals(
            com.inmc.fishing.fillet.FilletBench.MAX_SLOTS, positions.size,
            "칸 수 상한만큼 자리가 있어야 한다 — 모자라면 늘린 칸이 화면에 안 보인다",
        )

        val all = positions + slots.values + Paging.SLOT_CLOSE
        assertEquals(all.size, all.toSet().size, "버튼과 슬롯이 겹칩니다: $all")
        for (slot in all) assertTrue(slot in 0 until 54, "$slot 이 54칸 창을 벗어납니다")
    }

    @Test
    fun `도감과 어망의 버튼이 항목 칸을 덮지 않는다`() {
        // 둘 다 0 부터 PER_PAGE - 1 까지를 항목에 쓴다. 버튼이 그 안으로 들어가면 항목
        // 하나가 가려지고, 가려진 항목은 클릭도 되지 않는다.
        for (file in listOf("CollectionMenu.kt", "NetMenu.kt")) {
            val slots = slotsOf(file)
            for ((name, slot) in slots) {
                assertTrue(slot >= Paging.PER_PAGE, "$file 의 $name($slot) 이 항목 칸을 덮습니다")
            }

            val reserved = slots + mapOf(
                "Paging.SLOT_PREV" to Paging.SLOT_PREV,
                "Paging.SLOT_NEXT" to Paging.SLOT_NEXT,
                "Paging.SLOT_CLOSE" to Paging.SLOT_CLOSE,
            )
            assertEquals(
                reserved.size,
                reserved.values.toSet().size,
                "$file 버튼 슬롯 충돌: " + reserved.entries.groupBy { it.value }.filterValues { it.size > 1 },
            )
        }
    }

    @Test
    fun `대회 목록은 테두리 안쪽에만 그리고 버튼과 겹치지 않는다`() {
        // 예전엔 18번부터 이어서 깔아 첫 칸이 왼쪽 테두리에 그려졌다(인게임에서 한 칸 밀려 보였다).
        val slots = com.inmc.fishing.gui.TournamentMenu.LIST_SLOTS
        assertEquals(com.inmc.fishing.gui.TournamentMenu.PER_PAGE, slots.size)
        assertEquals(slots.size, slots.toSet().size, "대회 칸이 겹칩니다")
        for (slot in slots) {
            val row = slot / 9
            val col = slot % 9
            assertTrue(row in 1..4 && col in 1..7, "대회 칸 $slot 이 테두리 위에 있습니다")
        }
        val buttons = listOf(
            com.inmc.fishing.gui.TournamentMenu.SLOT_WINS,
            com.inmc.fishing.gui.TournamentMenu.SLOT_MAIN,
            Paging.SLOT_PREV,
            Paging.SLOT_NEXT,
            Paging.SLOT_CLOSE,
        )
        for (button in buttons) assertTrue(button !in slots, "버튼 $button 이 대회 칸을 덮습니다")
    }

    @Test
    fun `목록 화면의 버튼이 항목 칸을 덮지 않는다`() {
        // 항목은 0 부터 PER_PAGE - 1 까지를 쓴다. 버튼이 그 안에 들어가면 항목 하나가
        // 통째로 가려지고, 가려진 항목은 클릭도 되지 않는다.
        val slots = slotsOf("DefinitionListMenu.kt")

        for ((name, slot) in slots) {
            assertTrue(slot >= Paging.PER_PAGE, "$name($slot) 이 항목 칸을 덮습니다")
        }

        val reserved = slots + mapOf(
            "Paging.SLOT_BACK" to Paging.SLOT_BACK,
            "Paging.SLOT_PREV" to Paging.SLOT_PREV,
            "Paging.SLOT_NEXT" to Paging.SLOT_NEXT,
            "Paging.SLOT_CLOSE" to Paging.SLOT_CLOSE,
        )
        assertEquals(
            reserved.size,
            reserved.values.toSet().size,
            "버튼 슬롯 충돌: " + reserved.entries.groupBy { it.value }.filterValues { it.size > 1 },
        )
        for ((name, slot) in reserved) assertTrue(slot in 0 until 54, "$name($slot) 이 창을 벗어납니다")
    }
}
