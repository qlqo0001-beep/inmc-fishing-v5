package com.inmc.fishing

import com.inmc.fishing.collection.Collection
import com.inmc.fishing.collection.CollectionEntry
import com.inmc.fishing.fish.Trophy
import com.inmc.fishing.ranking.FishingRanks
import kr.inmc.core.rank.RankMode
import kr.inmc.core.rank.Rankable
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 도감에서 랭킹이 뽑혀 나오는 과정을 못박는다.
 *
 * **멱등성이 핵심이다.** 파생 랭킹은 주기적으로 다시 계산되므로, 같은 도감을 두 번 올렸을 때
 * 점수가 달라지면 가만히 있어도 순위가 오른다.
 */
class FishingRanksTest {

    private val alice: UUID = UUID.randomUUID()

    /** `publish` 로 올라온 것을 그대로 받아 적는다. */
    private class Recorder {
        val calls = mutableListOf<Triple<String, Long, Long>>()
        fun capture(def: Rankable, score: Long, record: Long) {
            calls += Triple(def.id, score, record)
        }

        fun scoreOf(id: String): Long? = calls.lastOrNull { it.first == id }?.second
        fun recordOf(id: String): Long? = calls.lastOrNull { it.first == id }?.third
    }

    private fun collection(vararg entries: CollectionEntry) = Collection().apply {
        entries.forEach { put(it) }
    }

    private fun entry(
        id: String,
        slots: Int = 0,
        maxSlots: Int = 10,
        largest: Double = 0.0,
        trophy: Int = 0,
        rare: Int = 0,
    ) = CollectionEntry(id, "f", maxSlots).apply {
        repeat(slots) { register(0.0, 0L) }
        if (largest > 0.0) recordCatch(largest, Trophy.NONE, 0L)
        repeat(trophy) { recordCatch(1.0, Trophy.NORMAL, 0L) }
        repeat(rare) { recordCatch(1.0, Trophy.RARE, 0L) }
    }

    private fun publish(data: Collection, into: Recorder) {
        FishingRanks().publish(alice, "Alice", data, { "$it 이름" }, into::capture)
    }

    @Test
    fun `도감 점수가 도감 보드로 간다`() {
        val data = collection(entry("cod", slots = 3, trophy = 1))
        val recorder = Recorder()

        publish(data, recorder)

        // 칸 3 + 트로피 1×10 = 13
        assertEquals(13L, recorder.scoreOf(FishingRanks.COLLECTION_ID))
    }

    @Test
    fun `트로피 보드는 일반과 레어를 합쳐 센다`() {
        val data = collection(entry("cod", trophy = 2, rare = 3))
        val recorder = Recorder()

        publish(data, recorder)

        assertEquals(5L, recorder.scoreOf(FishingRanks.TROPHY_ID))
    }

    @Test
    fun `물고기마다 사이즈 보드가 따로 생긴다`() {
        val data = collection(entry("cod", largest = 80.0), entry("tuna", largest = 150.5))
        val recorder = Recorder()

        publish(data, recorder)

        // 0.1cm 까지 반영하려고 10배로 정수화한다.
        assertEquals(800L, recorder.recordOf(FishingRanks.sizeBoardId("cod")))
        assertEquals(1505L, recorder.recordOf(FishingRanks.sizeBoardId("tuna")))
    }

    @Test
    fun `크기 기록이 없는 물고기는 보드를 만들지 않는다`() {
        // 쓰레기나 아직 못 잡은 물고기까지 보드를 만들면 GUI 가 빈 보드로 가득 찬다.
        val data = collection(entry("boot", slots = 2, largest = 0.0))
        val recorder = Recorder()

        publish(data, recorder)

        assertTrue(recorder.calls.none { it.first.startsWith("fishing_size_") })
    }

    @Test
    fun `여러 번 올려도 결과가 같다`() {
        // 파생 랭킹의 생명이다. 틱커가 주기적으로 돌려도 점수가 불어나면 안 된다.
        val data = collection(entry("cod", slots = 3, largest = 80.0, trophy = 1))
        val ranks = FishingRanks()
        val first = Recorder()
        val second = Recorder()

        ranks.publish(alice, "Alice", data, { it }, first::capture)
        ranks.publish(alice, "Alice", data, { it }, second::capture)

        assertEquals(first.scoreOf(FishingRanks.COLLECTION_ID), second.scoreOf(FishingRanks.COLLECTION_ID))
        assertEquals(
            first.recordOf(FishingRanks.sizeBoardId("cod")),
            second.recordOf(FishingRanks.sizeBoardId("cod")),
        )
    }

    // --- 보드 정의 -----------------------------------------------------------------

    @Test
    fun `사이즈 보드는 최고 기록 모드다`() {
        // 누적이면 여러 번 낚을수록 순위가 오른다. "가장 크게 낚은 한 마리"로 겨뤄야 한다.
        val ranks = FishingRanks()

        assertEquals(RankMode.BEST_RECORD, ranks.sizeBoard("cod", "대구").ranking.mode)
    }

    @Test
    fun `도감과 트로피는 누적 모드다`() {
        val ranks = FishingRanks()

        assertEquals(RankMode.CUMULATIVE_SCORE, ranks.collection.ranking.mode)
        assertEquals(RankMode.CUMULATIVE_SCORE, ranks.trophy.ranking.mode)
    }

    @Test
    fun `같은 물고기의 보드는 한 번만 만들어진다`() {
        val ranks = FishingRanks()

        val first = ranks.sizeBoard("cod", "대구")
        val second = ranks.sizeBoard("COD", "대구")

        assertTrue(first === second, "대소문자가 달라도 같은 보드여야 한다")
    }

    @Test
    fun `보드 목록에 고정 보드 둘이 항상 있다`() {
        val ranks = FishingRanks()

        assertEquals(2, ranks.all().size)
        ranks.sizeBoard("cod", "대구")
        assertEquals(3, ranks.all().size)
    }

    @Test
    fun `id 로 보드를 찾을 수 있다`() {
        val ranks = FishingRanks()
        ranks.sizeBoard("cod", "대구")

        assertEquals(FishingRanks.COLLECTION_ID, ranks.byId("fishing_collection")?.id)
        assertEquals(FishingRanks.sizeBoardId("cod"), ranks.byId("FISHING_SIZE_COD")?.id)
        assertEquals(null, ranks.byId("없는보드"))
    }
}
