package com.inmc.fishing

import com.inmc.fishing.fillet.FilletBench
import com.inmc.fishing.fillet.FilletConfig
import com.inmc.fishing.fish.FishStamp
import com.inmc.fishing.fish.Trophy
import com.inmc.fishing.net.FishNet
import com.inmc.fishing.net.NetSort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 어망의 담기·꺼내기·정렬. */
class FishNetTest {

    private fun stamp(
        fishId: String,
        gradeId: String = "f",
        size: Double = 30.0,
        trophy: Trophy = Trophy.NONE,
        at: Long = 0L,
    ) = FishStamp(fishId, gradeId, size, trophy, at)

    private val gradeOrder = listOf("s", "a", "b", "c", "d", "e", "f")

    @Test
    fun `가득 차면 더 못 넣는다`() {
        // false 를 안 돌려주면 부르는 쪽이 아이템만 삼킨다.
        val net = FishNet(capacity = 2)

        assertTrue(net.add(stamp("cod")))
        assertTrue(net.add(stamp("cod")))
        assertFalse(net.add(stamp("cod")))
        assertEquals(2, net.size)
    }

    @Test
    fun `용량 0 이하는 무제한으로 본다`() {
        val net = FishNet(capacity = 0)

        repeat(500) { assertTrue(net.add(stamp("cod"))) }
        assertEquals(500, net.size)
    }

    @Test
    fun `번호로 꺼낸다`() {
        val net = FishNet(10)
        net.add(stamp("cod"))
        net.add(stamp("tuna"))

        assertEquals("cod", net.removeAt(0)?.fishId)
        assertEquals(1, net.size)
        assertNull(net.removeAt(99), "범위 밖은 null")
    }

    @Test
    fun `최근순은 늦게 잡은 것이 앞이다`() {
        val net = FishNet(10)
        net.add(stamp("a", at = 100L))
        net.add(stamp("b", at = 300L))
        net.add(stamp("c", at = 200L))

        assertEquals(listOf("b", "c", "a"), net.sorted(NetSort.RECENT, gradeOrder).map { it.fishId })
    }

    @Test
    fun `크기순은 큰 것이 앞이다`() {
        val net = FishNet(10)
        net.add(stamp("a", size = 30.0))
        net.add(stamp("b", size = 80.0))

        assertEquals(listOf("b", "a"), net.sorted(NetSort.SIZE, gradeOrder).map { it.fishId })
    }

    @Test
    fun `등급순은 설정 순서를 따르고 같은 등급은 큰 것이 앞이다`() {
        // 등급은 설정값이라 코드가 순서를 모른다. 밖에서 받아야 한다.
        val net = FishNet(10)
        net.add(stamp("small_s", gradeId = "s", size = 10.0))
        net.add(stamp("big_f", gradeId = "f", size = 90.0))
        net.add(stamp("big_s", gradeId = "s", size = 50.0))

        assertEquals(
            listOf("big_s", "small_s", "big_f"),
            net.sorted(NetSort.GRADE, gradeOrder).map { it.fishId },
        )
    }

    @Test
    fun `모르는 등급은 맨 뒤로 간다`() {
        val net = FishNet(10)
        net.add(stamp("unknown", gradeId = "없는등급"))
        net.add(stamp("cod", gradeId = "f"))

        assertEquals(listOf("cod", "unknown"), net.sorted(NetSort.GRADE, gradeOrder).map { it.fishId })
    }

    @Test
    fun `트로피만 골라낸다`() {
        val net = FishNet(10)
        net.add(stamp("a"))
        net.add(stamp("b", trophy = Trophy.NORMAL))
        net.add(stamp("c", trophy = Trophy.RARE))

        assertEquals(listOf("b", "c"), net.trophies().map { it.fishId })
    }

    @Test
    fun `물고기별 최대 기록을 찾는다`() {
        val net = FishNet(10)
        net.add(stamp("cod", size = 30.0))
        net.add(stamp("cod", size = 80.0))
        net.add(stamp("tuna", size = 200.0))

        assertEquals(80.0, net.largestOf("cod")?.size)
        assertEquals(80.0, net.largestOf("COD")?.size, "대소문자를 가리지 않는다")
        assertNull(net.largestOf("없는물고기"))
    }

    @Test
    fun `정렬은 저장 순서를 바꾸지 않는다`() {
        // 바꾸면 정렬을 바꿀 때마다 "몇 번째 칸"이 흔들려 엉뚱한 물고기를 꺼낸다.
        val net = FishNet(10)
        net.add(stamp("a", size = 10.0))
        net.add(stamp("b", size = 90.0))

        net.sorted(NetSort.SIZE, gradeOrder)

        assertEquals(listOf("a", "b"), net.all().map { it.fishId })
    }
}

/** 손질대의 시간 계산과 슬롯 운영. */
class FilletTest {

    private val config = FilletConfig(
        baseYield = 3,
        defaultSlots = 3,
        concurrent = true,
        gradeSeconds = mapOf("f" to 10, "s" to 180),
        trophyMultiplier = 1.5,
        rareTrophyMultiplier = 2.0,
        useRarityFactor = true,
        secondsPerItem = 5,
    )

    @Test
    fun `시간은 등급 시간에 트로피 배수를 곱하고 마리당 시간을 더한다`() {
        // (10 × 1.0) + (1 × 5) = 15
        assertEquals(15, config.secondsFor("f", Trophy.NONE, 1))
        // (10 × 1.5) + (1 × 5) = 20
        assertEquals(20, config.secondsFor("f", Trophy.NORMAL, 1))
        // (10 × 2.0) + (1 × 5) = 25
        assertEquals(25, config.secondsFor("f", Trophy.RARE, 1))
    }

    @Test
    fun `트로피 배수는 마리당 시간에는 안 곱해진다`() {
        // 곱하면 트로피를 여러 마리 넣었을 때 시간이 폭발한다.
        // (10 × 2.0) + (10 × 5) = 70. 배수를 전부에 곱하면 (10 + 50) × 2 = 120 이다.
        assertEquals(70, config.secondsFor("f", Trophy.RARE, 10))
    }

    @Test
    fun `희귀도 계수를 끄면 등급 시간만 쓴다`() {
        val flat = config.copy(useRarityFactor = false)

        assertEquals(15, flat.secondsFor("f", Trophy.RARE, 1))
    }

    @Test
    fun `모르는 등급은 마리당 시간만 든다`() {
        assertEquals(5, config.secondsFor("없는등급", Trophy.NONE, 1))
    }

    @Test
    fun `시간은 최소 1초다`() {
        val instant = FilletConfig(gradeSeconds = emptyMap(), secondsPerItem = 0)

        assertEquals(1, instant.secondsFor("f", Trophy.NONE, 1))
    }

    @Test
    fun `수확량은 트로피일수록 많다`() {
        assertEquals(3, config.yieldFor(Trophy.NONE, 1))
        assertEquals(4, config.yieldFor(Trophy.NORMAL, 1), "3 × 1.5 = 4.5 -> 4")
        assertEquals(6, config.yieldFor(Trophy.RARE, 1))
        assertEquals(30, config.yieldFor(Trophy.NONE, 10))
    }

    // --- 슬롯 -------------------------------------------------------------------

    @Test
    fun `빈 슬롯에 차례로 들어간다`() {
        val bench = FilletBench(config)

        assertEquals(0, bench.submit("cod", "f", Trophy.NONE, 1, 0L))
        assertEquals(1, bench.submit("cod", "f", Trophy.NONE, 1, 0L))
        assertEquals(2, bench.submit("cod", "f", Trophy.NONE, 1, 0L))
        assertEquals(-1, bench.submit("cod", "f", Trophy.NONE, 1, 0L), "자리가 없으면 -1")
        assertFalse(bench.hasRoom())
    }

    @Test
    fun `동시 모드는 전부 지금 시작한다`() {
        val bench = FilletBench(config)

        bench.submit("cod", "f", Trophy.NONE, 1, 1000L)
        bench.submit("cod", "f", Trophy.NONE, 1, 1000L)

        assertEquals(1000L, bench.slotAt(0)!!.startedAt)
        assertEquals(1000L, bench.slotAt(1)!!.startedAt)
    }

    @Test
    fun `차례 모드는 앞의 것이 끝난 뒤에 시작한다`() {
        val bench = FilletBench(config.copy(concurrent = false))

        bench.submit("cod", "f", Trophy.NONE, 1, 0L) // 15초 -> 0 ~ 15000
        bench.submit("cod", "f", Trophy.NONE, 1, 0L)

        assertEquals(15_000L, bench.slotAt(1)!!.startedAt)
    }

    @Test
    fun `다 된 것만 거둔다`() {
        val bench = FilletBench(config)
        bench.submit("cod", "f", Trophy.NONE, 1, 0L) // 15초
        bench.submit("tuna", "s", Trophy.NONE, 1, 0L) // 185초

        val done = bench.collect(20_000L)

        assertEquals(listOf("cod"), done.map { it.fishId })
        assertEquals(1, bench.used(), "안 끝난 것은 남는다")
    }

    @Test
    fun `진행도가 0 에서 1 사이다`() {
        val bench = FilletBench(config)
        bench.submit("cod", "f", Trophy.NONE, 1, 0L) // 15초

        val slot = bench.slotAt(0)!!
        assertEquals(0.0, slot.progress(0L))
        assertEquals(0.5, slot.progress(7_500L), 1e-9)
        assertEquals(1.0, slot.progress(99_999L), "넘어가도 1 을 안 넘는다")
        assertEquals(0, slot.remainingSeconds(99_999L))
    }

    @Test
    fun `취소하면 슬롯이 빈다`() {
        val bench = FilletBench(config)
        bench.submit("cod", "f", Trophy.NONE, 1, 0L)

        assertEquals("cod", bench.cancel(0)?.fishId)
        assertNull(bench.slotAt(0))
        assertNull(bench.cancel(0), "빈 슬롯은 null")
    }

    @Test
    fun `취소로 돌려받는 물고기는 건 크기 그대로다`() {
        // 2세대 cancelFillet 은 크기를 되살렸다. 0 으로 돌려주면 트로피가 크기를 잃는다
        val bench = FilletBench(config)
        bench.submit("cod", "f", Trophy.NORMAL, 1, 0L, size = 72.5)

        val section = org.bukkit.configuration.file.YamlConfiguration()
        com.inmc.fishing.fillet.FilletSlotYaml.save(bench.slotAt(0)!!, section)
        val restored = com.inmc.fishing.fillet.FilletSlotYaml.load(section)!!

        assertEquals(72.5, restored.size)
        assertEquals(72.5, bench.cancel(0)?.size)
    }

    @Test
    fun `슬롯을 줄여도 돌던 것은 지우지 않는다`() {
        // 관리자가 칸을 줄였다고 남의 생선이 사라지면 안 된다. 뒤쪽의 빈 칸만 걷는다.
        val bench = FilletBench(config)
        bench.submit("a", "f", Trophy.NONE, 1, 0L)
        bench.submit("b", "f", Trophy.NONE, 1, 0L)
        bench.submit("c", "f", Trophy.NONE, 1, 0L)

        assertEquals(3, bench.resize(1), "셋 다 돌고 있으면 줄지 않는다")
        assertEquals("c", bench.slotAt(2)?.fishId)

        bench.cancel(2)
        bench.cancel(1)
        assertEquals(1, bench.resize(1), "비면 줄어든다")
    }

    @Test
    fun `칸 수는 1에서 35 사이다`() {
        // 2세대 setSlots 의 상한.
        val bench = FilletBench(config)
        assertEquals(FilletBench.MAX_SLOTS, bench.resize(100))
        assertEquals(1, FilletBench(config, size = 0).slotCount)
    }

    @Test
    fun `슬롯을 늘리면 빈 자리가 생긴다`() {
        val bench = FilletBench(config)

        bench.resize(5)

        assertEquals(5, bench.slotCount)
        assertTrue(bench.hasRoom())
    }
}
