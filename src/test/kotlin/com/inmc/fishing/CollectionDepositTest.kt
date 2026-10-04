package com.inmc.fishing

import com.inmc.fishing.collection.CollectionDeposit
import com.inmc.fishing.fish.FishStamp
import com.inmc.fishing.fish.Trophy
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 도감 등록용 꺼내기 순서 — 작은 것·일반부터, 트로피·레어는 나중.
 * Bukkit 없이 돈다.
 */
class CollectionDepositTest {

    private fun stamp(size: Double, trophy: Trophy = Trophy.NONE): FishStamp =
        FishStamp(fishId = "tuna", gradeId = "b", size = size, trophy = trophy, caughtAt = 0L)

    @Test
    fun `일반부터 쓰고 레어는 나중에 쓴다`() {
        val sorted = CollectionDeposit.sortForDeposit(
            listOf(
                stamp(10.0, Trophy.RARE),
                stamp(50.0, Trophy.NONE),
                stamp(5.0, Trophy.NORMAL),
                stamp(1.0, Trophy.NONE),
            ),
        )
        assertEquals(Trophy.NONE, sorted[0].trophy)
        assertEquals(1.0, sorted[0].size)
        assertEquals(Trophy.NONE, sorted[1].trophy)
        assertEquals(Trophy.NORMAL, sorted[2].trophy)
        assertEquals(Trophy.RARE, sorted[3].trophy)
    }

    @Test
    fun `같은 무리 안에서는 작은 크기부터 쓴다`() {
        val sorted = CollectionDeposit.sortForDeposit(
            listOf(stamp(30.0), stamp(10.0), stamp(20.0)),
        )
        assertEquals(listOf(10.0, 20.0, 30.0), sorted.map { it.size })
    }
}
