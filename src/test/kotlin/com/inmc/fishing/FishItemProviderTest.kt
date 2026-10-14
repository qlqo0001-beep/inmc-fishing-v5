package com.inmc.fishing

import com.inmc.fishing.fish.Fish
import com.inmc.fishing.fish.Rolls
import com.inmc.fishing.fish.SizeRange
import com.inmc.fishing.fish.Trophy
import com.inmc.fishing.fish.TrophyRule
import com.inmc.fishing.hook.FishItemProvider
import kr.inmc.core.item.ItemRef
import kr.inmc.core.item.StoredItem
import org.bukkit.Material
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 만들기 전용 공급처 `inmcfish:` 의 id 문법과 크기 굴림(진짜 낚은 것과 같은 식). */
class FishItemProviderTest {

    private class Scripted(private val values: List<Double>, private val gaussian: Double = 0.0) : Rolls {
        private var index = 0
        override fun next(): Double = values.getOrElse(index++) { values.lastOrNull() ?: 0.0 }
        override fun gaussian(): Double = gaussian
    }

    private fun fish(size: SizeRange = SizeRange(10.0, 80.0, 35.0)) = Fish(
        id = "cod", gradeId = "f",
        item = StoredItem(ItemRef.Vanilla(Material.COD), Material.COD, displayName = "cod"),
        weight = 1, size = size, doubleEnabled = true, commands = emptyList(), fatigueRecovery = 0, maxSlots = 10,
    )

    @Test
    fun `id 문법 - 물고기와 트로피만 받는다`() {
        assertEquals("cod" to false, FishItemProvider.parse("cod"))
        assertEquals("cod" to false, FishItemProvider.parse("COD"))
        assertEquals("cod" to true, FishItemProvider.parse("cod:trophy"))
        assertNull(FishItemProvider.parse(""))
        assertNull(FishItemProvider.parse("cod:big"))
        assertNull(FishItemProvider.parse("cod:trophy:x"))
        assertNull(FishItemProvider.parse(":trophy"))
    }

    @Test
    fun `대어가 아니면 정규분포 크기 - 평균이면 트로피 아님`() {
        val stamp = FishItemProvider.rollStamp(fish(), TrophyRule.DEFAULT, bigFishChance = 0.0, Scripted(listOf(0.99), 0.0), now = 5L)
        assertEquals(35.0, stamp.size)
        assertEquals(Trophy.NONE, stamp.trophy)
        assertEquals("f", stamp.gradeId)
        assertEquals(5L, stamp.caughtAt)
    }

    @Test
    fun `대어가 걸리면 반드시 트로피`() {
        // 첫 next 는 대어 판정(0.0 < 100%), 둘째는 트로피 구간 안의 위치.
        val stamp = FishItemProvider.rollStamp(fish(), TrophyRule.DEFAULT, bigFishChance = 100.0, Scripted(listOf(0.0, 0.5)), now = 0L)
        assertTrue(stamp.trophy.isTrophy)
        assertTrue(stamp.size >= TrophyRule.DEFAULT.normalFloor(fish().size))
    }

    @Test
    fun `크기 없는 것은 0 이고 트로피가 아니다`() {
        val stamp = FishItemProvider.rollStamp(fish(SizeRange(0.0, 0.0, 0.0)), TrophyRule.DEFAULT, 100.0, Scripted(listOf(0.0)), 0L)
        assertEquals(0.0, stamp.size)
        assertEquals(Trophy.NONE, stamp.trophy)
    }
}
