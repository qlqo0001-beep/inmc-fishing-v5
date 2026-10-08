package com.inmc.fishing.tournament

import org.bukkit.configuration.ConfigurationSection
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 대회 하나의 설정. `tournaments.yml` 에서 온다.
 *
 * 보상은 **콘솔 명령어 목록**이다. 아이템으로 두면 대회 보상을 바꿀 때마다 아이템을 등록해야
 * 하고, 돈이나 포인트처럼 아이템이 아닌 보상을 줄 수가 없다.
 *
 * ⚠ 끝날 때 **접속 중이 아닌 참가자에게도** 명령어가 돈다. 오프라인에서 동작하는 명령어를
 * 쓸 것 — 이코노미·메일 계열은 대개 되고, 바닐라 `give` 는 실패한다.
 */
data class TournamentDef(
    val id: String,
    val name: String,
    val description: List<String>,
    val type: TournamentType,
    /** GRADE 형에서 셀 등급. 비었거나 `all` 이면 전부 센다. */
    val targetGrade: String,
    val durationMinutes: Int,
    val autoStart: Boolean,
    val schedule: Schedule,
    val scheduleDay: DayOfWeek?,
    val scheduleTime: LocalTime,
    val minPlayers: Int,
    val maxPlayers: Int,
    val entryFee: Double,
    val refundOnLeave: Boolean,
    /** 순위(1부터) → 실행할 명령어. */
    val rewards: Map<Int, List<String>>,
    val participationReward: List<String>,
    /** 참가비 화폐 id — 비우면 기본 화폐(2026-10-08). `currency` 열쇠. */
    val currency: String = "",
) {

    enum class Schedule { NONE, DAILY, WEEKLY }

    val durationMillis: Long get() = durationMinutes.coerceAtLeast(1) * 60_000L

    /**
     * [from] 이후 처음으로 자동 시작할 시각.
     *
     * 지금이 정확히 시작 시각이어도 **다음 주기**를 준다. 안 그러면 같은 대회가 끝나자마자
     * 같은 분에 다시 열린다.
     */
    fun nextStart(from: LocalDateTime): LocalDateTime? {
        if (!autoStart) return null
        return when (schedule) {
            Schedule.NONE -> null

            Schedule.DAILY -> {
                val today = from.toLocalDate().atTime(scheduleTime)
                if (today.isAfter(from)) today else today.plusDays(1)
            }

            Schedule.WEEKLY -> {
                val day = scheduleDay ?: DayOfWeek.SUNDAY
                var candidate = from.toLocalDate().atTime(scheduleTime)
                while (candidate.dayOfWeek != day || !candidate.isAfter(from)) {
                    candidate = candidate.plusDays(1)
                }
                candidate
            }
        }
    }

    fun rewardFor(rank: Int): List<String> = rewards[rank].orEmpty()

    companion object {

        fun load(key: String, section: ConfigurationSection): TournamentDef? {
            val id = key.lowercase().ifBlank { return null }

            val rewards = HashMap<Int, List<String>>()
            section.getConfigurationSection("rewards")?.let { node ->
                for (rankKey in node.getKeys(false)) {
                    val rank = parseRank(rankKey) ?: continue
                    rewards[rank] = node.getStringList(rankKey)
                }
            }

            return TournamentDef(
                id = id,
                name = section.getString("name").orEmpty().ifBlank { id },
                description = section.getStringList("description"),
                type = TournamentType.parse(section.getString("type")),
                targetGrade = section.getString("grade").orEmpty().lowercase(),
                durationMinutes = section.getInt("duration-minutes", 60).coerceAtLeast(1),
                autoStart = section.getBoolean("auto-start", false),
                schedule = parseSchedule(section.getString("schedule")),
                scheduleDay = parseDay(section.getString("schedule-day")),
                scheduleTime = parseTime(section.getString("schedule-time")),
                minPlayers = section.getInt("min-players", 1).coerceAtLeast(1),
                maxPlayers = section.getInt("max-players", 100).coerceAtLeast(1),
                entryFee = section.getDouble("entry-fee", 0.0).coerceAtLeast(0.0),
                refundOnLeave = section.getBoolean("refund-on-leave", true),
                rewards = rewards,
                participationReward = section.getStringList("participation-reward"),
                currency = section.getString("currency").orEmpty(),
            )
        }

        /** `1st` · `2nd` · `1` 을 전부 받는다. 2세대 파일을 그대로 붙여넣을 수 있어야 한다. */
        fun parseRank(raw: String?): Int? {
            val digits = raw?.takeWhile { it.isDigit() }?.takeIf { it.isNotEmpty() } ?: return null
            return digits.toIntOrNull()?.takeIf { it > 0 }
        }

        private fun parseSchedule(raw: String?): Schedule =
            runCatching { Schedule.valueOf(raw.orEmpty().uppercase()) }.getOrDefault(Schedule.NONE)

        private fun parseDay(raw: String?): DayOfWeek? =
            runCatching { DayOfWeek.valueOf(raw.orEmpty().uppercase()) }.getOrNull()

        /** `HH:MM`. 못 읽으면 자정으로 — 조용히 지금으로 두면 리로드마다 대회가 열린다. */
        fun parseTime(raw: String?): LocalTime {
            val parts = raw.orEmpty().split(":")
            val hour = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: return LocalTime.MIDNIGHT
            val minute = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
            if (hour !in 0..23 || minute !in 0..59) return LocalTime.MIDNIGHT
            return LocalTime.of(hour, minute)
        }
    }
}
