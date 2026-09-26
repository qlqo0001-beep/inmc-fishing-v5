package com.inmc.fishing

import com.inmc.fishing.fillet.FilletItemRegistry
import com.inmc.fishing.fish.Trophy
import com.inmc.fishing.registry.Decor
import com.inmc.fishing.tournament.TournamentDef
import com.inmc.fishing.tournament.TournamentType
import org.bukkit.configuration.file.YamlConfiguration
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 대회 일정 계산과 손질 결과물 내려받기.
 *
 * 둘 다 **틀려도 오류가 안 나는** 종류다 — 일정이 틀리면 대회가 안 열리거나 두 번 열리고,
 * 내려받기가 틀리면 손질했는데 아무것도 안 나온다. 서버 없이 확인할 수 있게 순수 함수로
 * 뽑아둔 이유가 그것이다.
 */
class TournamentScheduleTest {

    private fun def(
        schedule: TournamentDef.Schedule,
        day: DayOfWeek? = null,
        time: LocalTime = LocalTime.of(20, 0),
        autoStart: Boolean = true,
    ) = TournamentDef(
        id = "t",
        name = "대회",
        description = emptyList(),
        type = TournamentType.COUNT,
        targetGrade = "",
        durationMinutes = 60,
        autoStart = autoStart,
        schedule = schedule,
        scheduleDay = day,
        scheduleTime = time,
        minPlayers = 2,
        maxPlayers = 10,
        entryFee = 0.0,
        refundOnLeave = true,
        rewards = emptyMap(),
        participationReward = emptyList(),
    )

    // --- 일정 -----------------------------------------------------------------------

    @Test
    fun `매일 대회는 오늘 시각이 아직 안 지났으면 오늘이다`() {
        val now = LocalDateTime.of(2026, 9, 14, 10, 0)

        assertEquals(
            LocalDateTime.of(2026, 9, 14, 20, 0),
            def(TournamentDef.Schedule.DAILY).nextStart(now),
        )
    }

    @Test
    fun `매일 대회는 시각이 지났으면 내일이다`() {
        val now = LocalDateTime.of(2026, 9, 14, 21, 0)

        assertEquals(
            LocalDateTime.of(2026, 9, 15, 20, 0),
            def(TournamentDef.Schedule.DAILY).nextStart(now),
        )
    }

    @Test
    fun `시작 시각 정각에는 다음 주기를 준다`() {
        // 안 그러면 대회가 끝나자마자 같은 분에 다시 열린다. 끝내는 것도 여는 것도 같은
        // 틱커라 그 순간의 시각이 정확히 일치할 수 있다.
        val exactly = LocalDateTime.of(2026, 9, 14, 20, 0)

        assertEquals(
            LocalDateTime.of(2026, 9, 15, 20, 0),
            def(TournamentDef.Schedule.DAILY).nextStart(exactly),
        )
    }

    @Test
    fun `주간 대회는 그 요일을 찾아간다`() {
        // 2026-09-14 는 월요일이다.
        val monday = LocalDateTime.of(2026, 9, 14, 10, 0)

        val next = def(TournamentDef.Schedule.WEEKLY, DayOfWeek.SUNDAY).nextStart(monday)

        assertEquals(DayOfWeek.SUNDAY, next?.dayOfWeek)
        assertEquals(LocalDateTime.of(2026, 9, 20, 20, 0), next)
    }

    @Test
    fun `주간 대회는 같은 요일 이른 시각이면 오늘이다`() {
        val sundayMorning = LocalDateTime.of(2026, 9, 20, 9, 0)

        assertEquals(
            LocalDateTime.of(2026, 9, 20, 20, 0),
            def(TournamentDef.Schedule.WEEKLY, DayOfWeek.SUNDAY).nextStart(sundayMorning),
        )
    }

    @Test
    fun `자동 시작이 꺼져 있으면 일정이 없다`() {
        assertNull(def(TournamentDef.Schedule.DAILY, autoStart = false).nextStart(LocalDateTime.now()))
        assertNull(def(TournamentDef.Schedule.NONE).nextStart(LocalDateTime.now()))
    }

    // --- 파싱 -----------------------------------------------------------------------

    @Test
    fun `보상 순위 표기를 2세대 형태로도 읽는다`() {
        assertEquals(1, TournamentDef.parseRank("1st"))
        assertEquals(2, TournamentDef.parseRank("2nd"))
        assertEquals(3, TournamentDef.parseRank("3rd"))
        assertEquals(10, TournamentDef.parseRank("10"))
        assertNull(TournamentDef.parseRank("participation"))
        assertNull(TournamentDef.parseRank(null))
        assertNull(TournamentDef.parseRank("0"), "0위는 없다")
    }

    @Test
    fun `잘못된 시각은 자정으로 떨어진다`() {
        // 조용히 "지금"으로 두면 **리로드할 때마다 대회가 열린다.**
        assertEquals(LocalTime.of(20, 30), TournamentDef.parseTime("20:30"))
        assertEquals(LocalTime.MIDNIGHT, TournamentDef.parseTime("25:00"))
        assertEquals(LocalTime.MIDNIGHT, TournamentDef.parseTime("abc"))
        assertEquals(LocalTime.MIDNIGHT, TournamentDef.parseTime(null))
        assertEquals(LocalTime.of(9, 0), TournamentDef.parseTime("9"), "분을 안 적으면 0분")
    }

    // --- 손질 결과물 내려받기 -----------------------------------------------------------

    @Test
    fun `결과물은 내려가기만 하고 올라가지 않는다`() {
        // 평범한 물고기에 트로피 전용 결과물을 주면 트로피를 낚을 이유가 사라진다.
        assertEquals(
            listOf(Trophy.RARE, Trophy.NORMAL, Trophy.NONE),
            FilletItemRegistry.fallbackChain(Trophy.RARE),
        )
        assertEquals(
            listOf(Trophy.NORMAL, Trophy.NONE),
            FilletItemRegistry.fallbackChain(Trophy.NORMAL),
        )
        assertEquals(listOf(Trophy.NONE), FilletItemRegistry.fallbackChain(Trophy.NONE))
    }

    // --- 꾸미기 ---------------------------------------------------------------------

    @Test
    fun `꾸미기는 식별용 이름을 건드리지 않는다`() {
        // 한 키로 쓰면 `&f[F] 물약` 과 실제 평문 이름 `[F] 물약` 이 달라 식별이 깨진다.
        val section = YamlConfiguration().createSection("x")
        section.set("name", "[F] 피로회복 물약")
        Decor("&f[F] 피로회복 물약", listOf("&7설명")).save(section)

        assertEquals("[F] 피로회복 물약", section.getString("name"), "식별용 이름이 덮이면 안 된다")
        assertEquals("&f[F] 피로회복 물약", section.getString(Decor.KEY_NAME))
        assertEquals(listOf("&7설명"), section.getStringList("lore"))
    }

    @Test
    fun `display-name 이 없으면 꾸미기는 비어 있다`() {
        // 손에 든 것을 등록하면 스냅샷에 색이 들어간 이름이 이미 있다. 그 위에 평문 이름을
        // 덮어씌우면 색이 날아간다.
        val section = YamlConfiguration().createSection("x")
        section.set("name", "내 보스 검")

        assertTrue(Decor.load(section).isEmpty, "name 을 꾸미기로 끌어다 쓰면 안 된다")
    }

    @Test
    fun `꾸미기가 왕복해도 그대로다`() {
        val original = Decor("&6황금 낚싯대", listOf("&7전설의 낚싯대", "&7크기 +20%"))
        val section = YamlConfiguration().createSection("x")
        original.save(section)

        assertEquals(original, Decor.load(section))
    }
}
