package com.inmc.fishing

import com.inmc.fishing.collection.Collection
import com.inmc.fishing.collection.CollectionEntry
import com.inmc.fishing.collection.ScoreWeights
import com.inmc.fishing.fish.Trophy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 도감의 상태 전이와 점수를 못박는다.
 *
 * **발견과 등록이 다르다**는 것이 이 시스템의 핵심인데, 코드에서 둘을 헷갈리면 "낚기만 해도
 * 도감이 찬다" 또는 "등록했는데 도감에 안 뜬다"가 된다. 둘 다 조용히 잘못 도는 종류다.
 */
class CollectionEntryTest {

    private val now = 1_700_000_000_000L

    private fun entry(maxSlots: Int = 10) = CollectionEntry("cod", "f", maxSlots)

    @Test
    fun `낚아도 도감 칸은 차지 않는다`() {
        // 이게 이 시스템의 전제다. 낚는 것과 바치는 것은 다르다.
        val e = entry()

        e.recordCatch(30.0, Trophy.NONE, now)

        assertTrue(e.discovered, "발견은 된다")
        assertEquals(0, e.registeredSlots, "등록은 안 된다")
        assertEquals(1, e.totalCaught)
    }

    @Test
    fun `첫 발견만 true 를 돌려준다`() {
        // 최초 등록 보상을 한 번만 주려면 이 값이 정확해야 한다.
        val e = entry()

        assertTrue(e.recordCatch(30.0, Trophy.NONE, now))
        assertFalse(e.recordCatch(40.0, Trophy.NONE, now))
    }

    @Test
    fun `등록하면 칸이 찬다`() {
        val e = entry(maxSlots = 3)

        assertTrue(e.register(30.0, now))
        assertTrue(e.register(31.0, now))
        assertEquals(2, e.registeredSlots)
        assertFalse(e.isPerfect)

        assertTrue(e.register(32.0, now))
        assertTrue(e.isPerfect)
    }

    @Test
    fun `가득 찬 도감에는 더 못 넣는다`() {
        // false 를 돌려주지 않으면 부르는 쪽이 아이템만 삼키고 칸은 안 찬다.
        val e = entry(maxSlots = 1)
        e.register(30.0, now)

        assertFalse(e.register(30.0, now))
        assertEquals(1, e.registeredSlots)
    }

    @Test
    fun `낚은 적 없이 등록해도 발견으로 친다`() {
        // 남이 준 물고기를 바로 바치는 경우가 있다.
        val e = entry()

        e.register(30.0, now)

        assertTrue(e.discovered)
        assertEquals(now, e.firstCaughtAt)
    }

    @Test
    fun `등록을 되돌릴 수 있다`() {
        val e = entry()
        e.register(30.0, now)

        assertTrue(e.unregister())
        assertEquals(0, e.registeredSlots)
        assertFalse(e.unregister(), "0 밑으로는 안 내려간다")
    }

    @Test
    fun `회수는 마지막에 넣은 물고기의 크기를 돌려준다`() {
        // 2세대 unregisterFish 와 같다 — 이 크기로 아이템을 다시 만들어 준다
        val e = entry()
        e.register(30.0, now)
        e.register(45.5, now)

        assertEquals(45.5, e.lastRegisteredSize())
        assertTrue(e.unregister())
        assertEquals(30.0, e.lastRegisteredSize())
        assertTrue(e.unregister())
        assertEquals(null, e.lastRegisteredSize(), "등록된 것이 없으면 돌려줄 것도 없다")
    }

    @Test
    fun `등록 크기는 저장했다 읽어도 남는다`() {
        val e = entry()
        e.register(30.0, now)
        e.register(45.5, now)
        val section = org.bukkit.configuration.file.YamlConfiguration()
        e.save(section)

        val loaded = CollectionEntry.load(e.fishId, e.gradeId, e.maxSlots, section)
        assertEquals(45.5, loaded.lastRegisteredSize())
    }

    // --- 크기 기록 -----------------------------------------------------------------

    @Test
    fun `가장 큰 것과 가장 작은 것을 따로 기록한다`() {
        val e = entry()

        e.recordCatch(30.0, Trophy.NONE, now)
        e.recordCatch(50.0, Trophy.NONE, now)
        e.recordCatch(20.0, Trophy.NONE, now)

        assertEquals(50.0, e.largestSize)
        assertEquals(20.0, e.smallestSize)
    }

    @Test
    fun `첫 기록은 가장 작은 것으로도 들어간다`() {
        // smallestSize 의 초기값 0 을 "기록 없음"으로 다루지 않으면 영원히 0 에 머문다.
        val e = entry()

        e.recordCatch(30.0, Trophy.NONE, now)

        assertEquals(30.0, e.smallestSize)
    }

    @Test
    fun `크기가 없는 것은 기록에 영향을 주지 않는다`() {
        // 장화를 낚았다고 최소 크기가 0 이 되면 안 된다.
        val e = entry()
        e.recordCatch(30.0, Trophy.NONE, now)

        e.recordCatch(0.0, Trophy.NONE, now)

        assertEquals(30.0, e.smallestSize)
        assertEquals(30.0, e.largestSize)
    }

    @Test
    fun `트로피를 종류별로 센다`() {
        val e = entry()

        e.recordCatch(70.0, Trophy.NORMAL, now)
        e.recordCatch(75.0, Trophy.RARE, now)
        e.recordCatch(76.0, Trophy.RARE, now)

        assertEquals(1, e.trophyCount)
        assertEquals(2, e.rareTrophyCount)
        assertTrue(e.hasTrophy)
    }

    // --- 보상 수령 -----------------------------------------------------------------

    @Test
    fun `같은 보상은 한 번만 받는다`() {
        val e = entry()

        assertTrue(e.claim("first-register"))
        assertFalse(e.claim("first-register"))
        assertTrue(e.hasClaimed("first-register"))
        assertFalse(e.hasClaimed("milestone-5"))
    }
}

class CollectionScoreTest {

    private fun collection(vararg entries: CollectionEntry) = Collection().apply {
        entries.forEach { put(it) }
    }

    private fun entry(id: String, slots: Int, maxSlots: Int = 10, trophy: Int = 0, rare: Int = 0) =
        CollectionEntry(id, "f", maxSlots).apply {
            repeat(slots) { register(0.0, 0L) }
            repeat(trophy) { recordCatch(50.0, Trophy.NORMAL, 0L) }
            repeat(rare) { recordCatch(80.0, Trophy.RARE, 0L) }
        }

    @Test
    fun `점수는 칸 퍼펙트 트로피 레어의 가중합이다`() {
        // 칸 3 + 퍼펙트 0 + 트로피 1×10 + 레어 1×25 = 38
        val c = collection(entry("cod", slots = 3, trophy = 1, rare = 1))

        assertEquals(38L, c.score())
    }

    @Test
    fun `퍼펙트는 따로 더 쳐준다`() {
        // 한 물고기를 파고든 것을 흔한 물고기 여러 마리와 같게 보면 안 된다.
        val perfect = collection(entry("cod", slots = 3, maxSlots = 3))
        val spread = collection(entry("cod", slots = 3, maxSlots = 10))

        assertEquals(3L + 5L, perfect.score())
        assertEquals(3L, spread.score())
    }

    @Test
    fun `가중치를 설정으로 바꿀 수 있다`() {
        val c = collection(entry("cod", slots = 2, trophy = 1))
        val flat = ScoreWeights(slot = 1, perfect = 1, trophy = 1, rareTrophy = 1)

        assertEquals(2L + 10L, c.score())
        assertEquals(2L + 1L, c.score(flat))
    }

    @Test
    fun `빈 도감은 0 점이다`() {
        assertEquals(0L, Collection().score())
    }

    // --- 완성 판정 -----------------------------------------------------------------

    @Test
    fun `등급 올등록은 한 칸씩만 있어도 된다`() {
        val c = collection(entry("cod", slots = 1), entry("salmon", slots = 1))

        assertTrue(c.isGradeAllRegistered("f", listOf("cod", "salmon")))
        assertFalse(c.isGradePerfect("f", listOf("cod", "salmon")), "퍼펙트는 아직 아니다")
    }

    @Test
    fun `한 마리라도 빠지면 올등록이 아니다`() {
        val c = collection(entry("cod", slots = 1))

        assertFalse(c.isGradeAllRegistered("f", listOf("cod", "salmon")))
    }

    @Test
    fun `등급 퍼펙트는 전부 가득 차야 한다`() {
        val c = collection(
            entry("cod", slots = 3, maxSlots = 3),
            entry("salmon", slots = 2, maxSlots = 3),
        )

        assertFalse(c.isGradePerfect("f", listOf("cod", "salmon")))

        c.of("salmon")!!.register(0.0, 0L)
        assertTrue(c.isGradePerfect("f", listOf("cod", "salmon")))
    }

    @Test
    fun `물고기가 없는 등급은 완성으로 치지 않는다`() {
        // 빈 목록에 all{} 은 true 다. 그대로 두면 등급 하나를 지우는 순간 전원이
        // 그 등급 보상을 받는다.
        val c = Collection()

        assertFalse(c.isGradeAllRegistered("f", emptyList()))
        assertFalse(c.isGradePerfect("f", emptyList()))
        assertFalse(c.isComplete(emptyList()))
    }

    @Test
    fun `전체 완성은 모든 물고기가 퍼펙트일 때다`() {
        val c = collection(
            entry("cod", slots = 2, maxSlots = 2),
            entry("tuna", slots = 2, maxSlots = 2),
        )

        assertTrue(c.isComplete(listOf("cod", "tuna")))
        assertFalse(c.isComplete(listOf("cod", "tuna", "shark")))
    }
}
