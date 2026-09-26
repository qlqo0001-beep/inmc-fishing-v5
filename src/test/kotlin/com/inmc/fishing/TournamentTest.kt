package com.inmc.fishing

import com.inmc.fishing.tournament.Scoreboard
import com.inmc.fishing.tournament.TournamentType
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 대회 점수와 순위를 못박는다.
 *
 * 보상이 걸린 자리라 **같은 기록이면 같은 순위가 나와야 한다.** 2세대는 이 계산이 이벤트
 * 핸들러 안에 있어서, 동점 처리나 등급 필터를 확인하려면 대회를 실제로 열어야 했다.
 */
class TournamentTest {

    private val alice = UUID.randomUUID()
    private val bob = UUID.randomUUID()
    private val carol = UUID.randomUUID()

    private fun board(type: TournamentType, grade: String? = null) = Scoreboard(type, grade)

    // --- 참가 -------------------------------------------------------------------

    @Test
    fun `참가하지 않은 사람의 기록은 무시된다`() {
        val b = board(TournamentType.COUNT)

        assertFalse(b.record(alice, "Alice", "f", 10, 30.0, 100L))
        assertNull(b.of(alice))
    }

    @Test
    fun `나가면 점수와 보상에서 빠지지만 기록은 남는다`() {
        // 2세대와 같다. 5세대가 처음에 기록을 지워서, 나갔다 들어오면 0부터 다시였다.
        val b = board(TournamentType.COUNT)
        b.join(alice, "Alice")
        b.record(alice, "Alice", "f", 10, 30.0, 100L)

        assertTrue(b.leave(alice))
        assertFalse(b.isIn(alice))
        assertEquals(1L, b.of(alice)!!.score, "기록은 남는다")
        assertFalse(b.record(alice, "Alice", "f", 10, 30.0, 200L), "나간 뒤로는 점수가 안 오른다")
        assertTrue(b.rankedActive().isEmpty(), "보상 대상이 아니다")
        assertEquals(0, b.size, "정원에서 빠진다")
        assertFalse(b.leave(alice), "두 번 나갈 수는 없다")
    }

    @Test
    fun `다시 들어오면 하던 기록이 이어진다`() {
        val b = board(TournamentType.COUNT)
        b.join(alice, "Alice")
        b.record(alice, "Alice", "f", 10, 30.0, 100L)
        b.leave(alice)

        b.join(alice, "Alice")
        b.record(alice, "Alice", "f", 10, 30.0, 300L)
        assertEquals(2L, b.of(alice)!!.score)
    }

    @Test
    fun `낚는 순간 이름이 갱신된다`() {
        // 대회 시작 때 오프라인이었거나 닉네임을 바꾼 경우.
        val b = board(TournamentType.COUNT)
        b.join(alice, "???")

        b.record(alice, "Alice", "f", 10, 30.0, 100L)

        assertEquals("Alice", b.of(alice)!!.playerName)
    }

    // --- COUNT ------------------------------------------------------------------

    @Test
    fun `마릿수 대회는 잡은 수가 점수다`() {
        val b = board(TournamentType.COUNT)
        b.join(alice, "Alice")

        repeat(3) { b.record(alice, "Alice", "f", 10, 30.0, it.toLong()) }

        assertEquals(3L, b.of(alice)!!.score)
        assertEquals(3, b.of(alice)!!.catchCount)
    }

    @Test
    fun `마릿수 대회는 쓰레기도 센다`() {
        // 크기 없는 것도 "잡은 것"이다. SIZE 대회와 다른 점이다.
        val b = board(TournamentType.COUNT)
        b.join(alice, "Alice")

        assertTrue(b.record(alice, "Alice", "f", 10, 0.0, 0L))
        assertEquals(1L, b.of(alice)!!.score)
    }

    // --- SIZE -------------------------------------------------------------------

    @Test
    fun `크기 대회는 합산이고 소수점을 살린다`() {
        val b = board(TournamentType.SIZE)
        b.join(alice, "Alice")

        b.record(alice, "Alice", "f", 10, 30.5, 0L)
        b.record(alice, "Alice", "f", 10, 20.2, 1L)

        assertEquals(50.7, b.of(alice)!!.totalSize, 1e-9)
        // 10배로 정수화한다. 0.1cm 차이가 순위를 가른다.
        assertEquals(507L, b.of(alice)!!.score)
    }

    @Test
    fun `크기 대회는 최대 기록도 따로 남긴다`() {
        val b = board(TournamentType.SIZE)
        b.join(alice, "Alice")

        b.record(alice, "Alice", "f", 10, 30.0, 0L)
        b.record(alice, "Alice", "f", 10, 80.0, 1L)
        b.record(alice, "Alice", "f", 10, 20.0, 2L)

        assertEquals(80.0, b.of(alice)!!.bestSize)
    }

    @Test
    fun `크기 대회에서 쓰레기는 점수가 아니다`() {
        // 장화를 잔뜩 낚아 1등이 되면 안 된다.
        val b = board(TournamentType.SIZE)
        b.join(alice, "Alice")

        assertFalse(b.record(alice, "Alice", "f", 10, 0.0, 0L))
        assertEquals(0L, b.of(alice)!!.score)
    }

    // --- GRADE ------------------------------------------------------------------

    @Test
    fun `등급 대회는 등급이 점수를 지배한다`() {
        // 등급 기본 × 100 + 물고기 가중치. 가중치는 같은 등급 안의 순서만 가른다.
        val b = board(TournamentType.GRADE, grade = "all")
        b.join(alice, "Alice")
        b.join(bob, "Bob")

        // S 한 마리(7×100 + 1 = 701) vs F 스무 마리(1×100 + 50 = 150씩 = 3000)
        b.record(alice, "Alice", "s", 1, 100.0, 0L)
        repeat(20) { b.record(bob, "Bob", "f", 50, 30.0, it.toLong()) }

        assertEquals(701L, b.of(alice)!!.score)
        assertEquals(3000L, b.of(bob)!!.score)
    }

    @Test
    fun `등급 대회는 대상 등급만 센다`() {
        val b = board(TournamentType.GRADE, grade = "S")
        b.join(alice, "Alice")

        assertFalse(b.record(alice, "Alice", "f", 10, 30.0, 0L), "F 는 점수가 아니다")
        assertEquals(0L, b.of(alice)!!.score)

        assertTrue(b.record(alice, "Alice", "s", 10, 30.0, 1L))
        assertEquals(710L, b.of(alice)!!.score)
    }

    @Test
    fun `대상 등급은 대소문자를 가리지 않는다`() {
        // 설정에는 `grade: S`, 코드의 등급 id 는 `s` 다.
        val b = board(TournamentType.GRADE, grade = "S")
        b.join(alice, "Alice")

        assertTrue(b.record(alice, "Alice", "s", 0, 30.0, 0L))
    }

    @Test
    fun `all 이면 모든 등급이 점수다`() {
        val b = board(TournamentType.GRADE, grade = "all")
        b.join(alice, "Alice")

        assertTrue(b.record(alice, "Alice", "f", 0, 30.0, 0L))
        assertTrue(b.record(alice, "Alice", "s", 0, 30.0, 1L))
        assertEquals(100L + 700L, b.of(alice)!!.score)
    }

    @Test
    fun `모르는 등급은 기본 점수 0 으로 들어간다`() {
        // 관리자가 grades.yml 에 등급을 추가했는데 대회 점수표에는 안 넣은 경우.
        // 점수를 못 받을 뿐 대회가 깨지지는 않아야 한다.
        val b = board(TournamentType.GRADE, grade = "all")
        b.join(alice, "Alice")

        assertTrue(b.record(alice, "Alice", "새등급", 7, 30.0, 0L))
        assertEquals(7L, b.of(alice)!!.score, "가중치만 들어간다")
    }

    // --- 순위 -------------------------------------------------------------------

    @Test
    fun `점수가 높은 순이다`() {
        val b = board(TournamentType.COUNT)
        listOf(alice to "Alice", bob to "Bob", carol to "Carol").forEach { b.join(it.first, it.second) }

        repeat(1) { b.record(alice, "Alice", "f", 0, 1.0, 10L) }
        repeat(3) { b.record(bob, "Bob", "f", 0, 1.0, 20L) }
        repeat(2) { b.record(carol, "Carol", "f", 0, 1.0, 30L) }

        assertEquals(listOf("Bob", "Carol", "Alice"), b.ranked().map { it.playerName })
    }

    @Test
    fun `동점이면 먼저 도달한 사람이 이긴다`() {
        // 규칙이 없으면 순위가 맵 순서에 흔들린다. 보상이 걸린 자리에서 일어나면 안 된다.
        val b = board(TournamentType.COUNT)
        b.join(alice, "Alice")
        b.join(bob, "Bob")

        b.record(bob, "Bob", "f", 0, 1.0, 500L)
        b.record(alice, "Alice", "f", 0, 1.0, 100L)

        assertEquals(listOf("Alice", "Bob"), b.ranked().map { it.playerName })
    }

    @Test
    fun `아무것도 안 한 참가자는 활성 순위에서 빠진다`() {
        val b = board(TournamentType.COUNT)
        b.join(alice, "Alice")
        b.join(bob, "Bob")
        b.record(alice, "Alice", "f", 0, 1.0, 0L)

        assertEquals(2, b.ranked().size, "전체 순위에는 남는다")
        assertEquals(listOf("Alice"), b.rankedActive().map { it.playerName })
    }

    @Test
    fun `참가만 한 사람들끼리는 순위가 안정적이다`() {
        // lastScoredAt 이 0 인 사람들을 시각순으로 정렬하면 전부 맨 앞으로 온다.
        val b = board(TournamentType.COUNT)
        b.join(alice, "Alice")
        b.join(bob, "Bob")
        b.record(bob, "Bob", "f", 0, 1.0, 100L)

        assertEquals("Bob", b.ranked().first().playerName, "점수를 낸 사람이 먼저다")
    }
}
