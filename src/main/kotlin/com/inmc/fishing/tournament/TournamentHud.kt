package com.inmc.fishing.tournament

import com.inmc.fishing.Fishing
import kr.inmc.core.util.Text
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scoreboard.Criteria
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.Scoreboard
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * 대회 참가자의 화면 — 2세대 `TournamentHudManager` 와 같은 내용이다.
 *
 * - **보스바**: 참가 중인 대회마다 하나. 이름과 남은 시간.
 * - **사이드바**: 첫 번째 참가 대회의 시작·종료 시각, 상위 셋, 내 순위와 기록.
 *
 * **참가자에게만 손댄다.** 비참가자의 스코어보드를 건드리면 다른 플러그인의 사이드바를 지운다
 * (2세대가 한때 그랬다). 우리가 사이드바를 준 사람만 기억해 두고, 그 사람만 되돌린다. 되돌릴
 * 때는 새 스코어보드가 아니라 **서버 기본 스코어보드**로 — 새로 만들면 팀·이름표가 날아간다.
 */
class TournamentHud(private val fishing: Fishing) {

    private val bars = HashMap<UUID, MutableMap<String, BossBar>>()
    private val boards = HashMap<UUID, Scoreboard>()

    /** 마지막으로 그린 줄. 같으면 다시 쓰지 않는다 — 매초 다시 쓰면 사이드바가 깜빡인다. */
    private val rendered = HashMap<UUID, List<String>>()

    /** 1Hz. 도는 대회가 없고 보여준 것도 없으면 접속자를 훑지도 않는다. */
    fun update(now: Long) {
        val running = fishing.tournaments.running()
        if (running.isEmpty() && bars.isEmpty() && boards.isEmpty()) return
        for (player in Bukkit.getOnlinePlayers()) update(player, running, now)
    }

    private fun update(player: Player, running: List<ActiveTournament>, now: Long) {
        val mine = running.filter { it.board.isIn(player.uniqueId) }
        if (mine.isEmpty()) {
            if (player.uniqueId in bars || player.uniqueId in boards) remove(player)
            return
        }
        updateBars(player, mine, now)
        updateSidebar(player, mine.first(), now)
    }

    private fun updateBars(player: Player, mine: List<ActiveTournament>, now: Long) {
        val shown = bars.getOrPut(player.uniqueId) { HashMap() }
        for (run in mine) {
            val bar = shown.getOrPut(run.def.id) {
                BossBar.bossBar(Text.render(""), 1f, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS)
                    .also { player.showBossBar(it) }
            }
            val left = run.remainingSeconds(now)
            bar.name(
                Text.render(
                    "<yellow>⏱ </yellow><white>" + Text.plain(Text.render(run.def.name)) + "</white>" +
                        "<gray> | 남은 시간: </gray><green>" + clock(left) + "</green><gray> 남음</gray>",
                ),
            )
            val total = (run.endsAt - run.startedAt).coerceAtLeast(1L)
            bar.progress(((run.endsAt - now).toDouble() / total).coerceIn(0.0, 1.0).toFloat())
        }
        // 더 이상 참가하지 않는 대회의 막대는 치운다.
        val ids = mine.map { it.def.id }.toSet()
        val gone = shown.keys.filter { it !in ids }
        for (id in gone) shown.remove(id)?.let { player.hideBossBar(it) }
    }

    private fun updateSidebar(player: Player, run: ActiveTournament, now: Long) {
        val lines = ArrayList<String>()
        add(lines, "<gray>시작: </gray><white>" + hhmm(run.startedAt) + "</white>")
        add(lines, "<gray>종료: </gray><white>" + hhmm(run.endsAt) + "</white>")
        add(lines, "<gray>─────────────</gray>")
        val ranked = run.board.ranked().filter { !it.left }
        for ((index, standing) in ranked.take(TOP).withIndex()) {
            add(lines, "<gold>#" + (index + 1) + " </gold><white>" + standing.playerName + "</white><gray> — </gray><aqua>" + record(run, standing) + "</aqua>")
        }
        add(lines, "<gray>─────────────</gray>")
        run.board.of(player.uniqueId)?.let { me ->
            val rank = ranked.indexOfFirst { it.playerId == player.uniqueId }.let { if (it < 0) ranked.size + 1 else it + 1 }
            add(lines, "<yellow>내 순위: </yellow><white>" + rank + "위</white>")
            add(lines, "<yellow>내 기록: </yellow><white>" + record(run, me) + "</white>")
        }

        val board = boards.getOrPut(player.uniqueId) { Bukkit.getScoreboardManager().newScoreboard }
        val title = Text.render("<yellow>[</yellow><white>" + Text.plain(Text.render(run.def.name)) + "</white><yellow>]</yellow>")
        val objective = board.getObjective(OBJECTIVE)
            ?: board.registerNewObjective(OBJECTIVE, Criteria.DUMMY, title).also { it.displaySlot = DisplaySlot.SIDEBAR }
        objective.displayName(title)

        if (lines != rendered[player.uniqueId]) {
            for (old in board.entries) board.resetScores(old)
            var score = lines.size
            for (line in lines) objective.getScore(line).score = score--
            rendered[player.uniqueId] = lines
        }
        if (player.scoreboard !== board) player.scoreboard = board
    }

    /**
     * 사이드바 줄은 **글자 자체가 열쇠**다. 같은 문자열을 두 번 넣으면 한 줄만 남는다 — 2세대에서
     * 구분선 두 개가 같아서 한 줄이 사라져 있었다. 보이지 않는 리셋 코드를 붙여 다르게 만든다.
     */
    private fun add(lines: MutableList<String>, mini: String) {
        var line = LEGACY.serialize(Text.render(mini))
        while (line in lines) line += "§r"
        lines += line
    }

    private fun record(run: ActiveTournament, standing: Standing): String = when (run.def.type) {
        TournamentType.GRADE -> String.format("%,d", standing.score) + "점"
        // 순위가 합산으로 정해지므로 합산을 보이고, 개인 최고는 곁들인다.
        TournamentType.SIZE -> String.format("%.1fcm", standing.totalSize) +
            "<dark_gray> (최고 " + String.format("%.1fcm", standing.bestSize) + ")</dark_gray>"
        TournamentType.COUNT -> standing.catchCount.toString() + "마리"
    }

    /** 이 사람에게 준 것을 전부 치운다. 준 적이 없으면 아무것도 안 한다. */
    fun remove(player: Player) {
        bars.remove(player.uniqueId)?.values?.forEach { player.hideBossBar(it) }
        rendered.remove(player.uniqueId)
        if (boards.remove(player.uniqueId) != null) player.scoreboard = Bukkit.getScoreboardManager().mainScoreboard
    }

    /** 퇴장. 클라이언트가 이미 떠났으므로 기억만 지운다. */
    fun forget(playerId: UUID) {
        bars.remove(playerId)
        boards.remove(playerId)
        rendered.remove(playerId)
    }

    /** 끌 때. 접속 중인 사람에게서 전부 치운다. */
    fun clear() {
        for (player in Bukkit.getOnlinePlayers()) remove(player)
        bars.clear()
        boards.clear()
        rendered.clear()
    }

    private fun clock(seconds: Long): String = String.format("%02d:%02d", seconds / 60, seconds % 60)

    /** 서버 시간대의 HH:mm. 2세대가 한때 UTC 로 찍어 KST 서버에서 9시간 어긋났다. */
    private fun hhmm(epochMillis: Long): String =
        HHMM.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

    private companion object {
        const val OBJECTIVE = "inmc_tournament"
        const val TOP = 3
        val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val LEGACY: LegacyComponentSerializer = LegacyComponentSerializer.legacySection()
    }
}
