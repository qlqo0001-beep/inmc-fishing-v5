package com.inmc.fishing.tournament

import com.inmc.fishing.Fishing
import com.inmc.fishing.fish.Catch
import com.inmc.fishing.util.Ph
import kr.inmc.core.store.YamlFileStore
import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * 대회를 열고 닫는다. **여러 대회가 동시에 돌 수 있다** — 2세대와 같다.
 *
 * 5세대가 처음에 "2세대도 하나였다"며 한 번에 하나로 줄였는데 사실이 아니었다. 2세대는 대회마다
 * 따로 열고 닫았고, 낚은 한 마리는 참가한 대회 전부에 점수가 됐다.
 *
 * **시작 전에는 사전 신청을 받는다.** 참가비는 신청할 때 걷고, 대회가 열리면 신청자가 자동으로
 * 참가한다. 자동 시작은 신청자가 최소 인원에 못 미치면 열리지 않고 신청은 다음 일정까지 남는다
 * (2세대). 진행 중에 들어오는 것도 된다.
 *
 * 저장은 2세대와 같은 자리다 — 진행 상태와 신청은 `tournaments/active.yml`(비정상 종료 뒤에도
 * 이어진다), 끝난 결과는 `tournaments/history/<id>.yml`, 우승 횟수는 `tournaments/winners.yml`.
 * **신청도 저장한다.** 2세대는 진행 중인 대회만 저장해서 시작 전에 재시작하면 참가비를 낸 신청이
 * 사라졌다.
 *
 * 정의(`tournaments.yml`)는 읽기만 한다. 보상이 콘솔 명령어라 화면으로 안전하게 편집할 물건이 아니다.
 */
class TournamentService(private val fishing: Fishing) : YamlFileStore(
    io = fishing.io,
    path = listOf("tournaments.yml"),
    header = "",
    what = "대회",
) {

    private val definitions = LinkedHashMap<String, TournamentDef>()

    /** 다음 자동 시작 예정 시각. 리로드마다 다시 계산한다. */
    private val nextStarts = HashMap<String, Long>()

    /** 대회 id → 진행 중인 판. */
    private val runs = LinkedHashMap<String, ActiveTournament>()

    /** 대회 id → 사전 신청자(순서 유지) → 이름. */
    private val applications = HashMap<String, LinkedHashMap<UUID, String>>()

    private val state = StateStore()

    /** 우승 횟수. `/낚시 대회 우승` 이 읽는다. */
    val winners = WinnerStore()

    /**
     * 저장된 상태를 다 읽었는가. **읽기 전에는 쓰지 않는다** — 적재에 실패한 채 꺼지거나 그 사이에
     * 누가 신청하면, 메모리에 없는 옛 대회·신청을 빈 파일이 덮어 참가비를 낸 기록이 사라진다.
     */
    @Volatile
    private var stateLoaded = false

    @Volatile
    private var winnersLoaded = false

    /** 재시작 뒤 한 번만 되살린다. 리로드마다 되살리면 메모리의 최신 점수를 디스크의 옛 점수가 덮는다. */
    private var restored = false

    /** 점수는 낚을 때마다 바뀐다. 매번 쓰지 않고 [SCORE_SAVE_MILLIS] 마다 한 번 쓴다. */
    private var scoresChanged = false
    private var lastScoreSave = 0L

    fun all(): List<TournamentDef> = definitions.values.toList()

    fun get(id: String?): TournamentDef? = id?.let { definitions[it.lowercase()] }

    fun running(): List<ActiveTournament> = runs.values.toList()

    fun runOf(id: String?): ActiveTournament? = id?.let { runs[it.lowercase()] }

    val isRunning: Boolean get() = runs.isNotEmpty()

    fun applicantsOf(id: String): Map<UUID, String> = applications[id.lowercase()].orEmpty()

    fun hasApplied(def: TournamentDef, playerId: UUID): Boolean = applications[def.id]?.containsKey(playerId) == true

    // --- 여닫기 ---------------------------------------------------------------------

    /**
     * 대회를 연다. 사전 신청자는 그대로 참가자가 된다(참가비는 신청 때 이미 냈다).
     *
     * @param manual 관리자가 연 것. 자동 시작만 최소 인원(신청자 기준)을 본다 — 2세대와 같다.
     * @return 열었으면 true. 이미 도는 중이거나 자동 시작 인원이 모자라면 false.
     */
    fun start(def: TournamentDef, now: Long = System.currentTimeMillis(), manual: Boolean = true): Boolean {
        if (runs.containsKey(def.id)) return false
        val applicants = applications[def.id].orEmpty()
        if (!manual && applicants.size < def.minPlayers) {
            // 아무도 신청하지 않았으면 조용히 다음 시각으로 — 매번 콘솔에 남기면 오류처럼 보인다(사용자 2026-09-30).
            // 신청자가 있으면 그 사람들에게 알린다. 신청과 참가비는 그대로 다음 대회로 이어진다.
            if (applicants.isNotEmpty()) {
                fishing.logger.info("대회 '" + def.id + "' 는 신청 " + applicants.size + "/" + def.minPlayers + "명이라 열지 않았습니다 — 신청은 다음 대회로 이어집니다")
                val ph = Ph.of().tournament(def.name).amount(applicants.size.toString() + "/" + def.minPlayers)
                for (id in applicants.keys) Bukkit.getPlayer(id)?.let { fishing.messages.send(it, "tournament-not-enough", ph) }
            }
            return false
        }
        applications.remove(def.id)

        val board = Scoreboard(def.type, targetGrade = def.targetGrade.takeIf { it.isNotBlank() })
        for ((playerId, name) in applicants) board.join(playerId, name)
        runs[def.id] = ActiveTournament(def, board, startedAt = now, endsAt = now + def.durationMillis)

        val ph = Ph.of().tournament(def.name).amount(fishing.economy.format(def.entryFee))
        broadcast(if (def.entryFee > 0.0) "tournament-started-fee" else "tournament-started", ph)
        state.markDirty()
        return true
    }

    /** 대회 하나를 끝내고 보상을 준다. 진행 중이 아니면 false. */
    fun stop(id: String, now: Long = System.currentTimeMillis()): Boolean {
        val running = runs.remove(id.lowercase()) ?: return false
        finish(running, now, shutdown = false)
        state.markDirty()
        return true
    }

    /**
     * 서버가 꺼질 때. **진행 중인 대회를 전부 끝내고 보상을 준다** — 2세대와 같다. 공지는 하지
     * 않는다. `active.yml` 은 비정상 종료(크래시) 때를 위한 것이다.
     */
    fun shutdown(now: Long = System.currentTimeMillis()) {
        for (running in runs.values.toList()) finish(running, now, shutdown = true)
        runs.clear()
        if (stateLoaded) state.flushBlocking()
        if (winnersLoaded) winners.flushBlocking()
    }

    /**
     * **최소 인원에 못 미치면 보상 없이 접고 참가비를 돌려준다.** 두 명이 들어와 1·2등 보상을
     * 나눠 갖는 것은 대회가 아니다. (자동 시작의 신청 인원 검사와 별개로, 도중에 나가서 모자라게
     * 된 경우를 막는다.)
     */
    private fun finish(running: ActiveTournament, now: Long, shutdown: Boolean) {
        val def = running.def
        val ranked = running.board.rankedActive()
        writeHistory(running, now, blocking = shutdown)

        if (!shutdown) broadcast("tournament-ended", Ph.of().tournament(def.name))

        if (running.board.size < def.minPlayers) {
            for (standing in running.board.participants()) refund(standing.playerId, def)
            scheduleNext(def, now)
            return
        }

        ranked.firstOrNull()?.let { winners.add(it.playerId, it.playerName, def.id, now) }

        for ((index, standing) in ranked.withIndex()) {
            val rank = index + 1
            val commands = def.rewardFor(rank)
            if (commands.isNotEmpty()) run(commands, standing.playerName)

            // 시상 시점에 **접속해 있지 않을 수 있다.** core 의 신호가 `playerId` 를 필수로 받고
            // `player` 를 null 허용으로 둔 이유가 정확히 이 자리다.
            kr.inmc.core.event.InmcSignalEvent.fire(
                source = "fishing",
                type = "tournament",
                playerId = standing.playerId,
                subject = def.id,
                player = Bukkit.getPlayer(standing.playerId),
            ) {
                mapOf("tournament" to def.id, "rank" to rank.toString(), "score" to standing.score.toString())
            }

            // 공지는 상위 셋만 — 2세대와 같다. 전원을 공지하면 참가자 수만큼 채팅이 밀린다.
            if (!shutdown && rank <= ANNOUNCED) {
                broadcast(
                    "tournament-result",
                    Ph.of().tournament(def.name).rank(rank).player(standing.playerName).score(standing.score),
                )
            }
        }

        // 참가상은 점수를 못 낸 사람에게도 준다. 참가비를 낸 대가다. 스스로 나간 사람은 빠진다.
        if (def.participationReward.isNotEmpty()) {
            for (standing in running.board.participants()) run(def.participationReward, standing.playerName)
        }

        scheduleNext(def, now)
    }

    // --- 참가 -----------------------------------------------------------------------

    /**
     * 참가한다. 열려 있으면 바로 들어가고, 아직이면 **사전 신청**이 된다.
     * 참가비는 둘 다 이 자리에서 걷는다.
     */
    fun join(player: Player, def: TournamentDef): Boolean {
        val running = runs[def.id]
        if (running != null) {
            if (running.board.isIn(player.uniqueId)) {
                fishing.messages.send(player, "tournament-already-joined")
                return false
            }
            if (running.board.size >= def.maxPlayers) {
                fishing.messages.send(player, "tournament-full")
                return false
            }
            if (!charge(player, def)) return false
            // 나갔다 다시 오면 하던 기록이 이어진다 (2세대).
            running.board.join(player.uniqueId, player.name)
            state.markDirty()
            fishing.messages.send(player, "tournament-joined", Ph.of().tournament(def.name))
            return true
        }

        val list = applications.getOrPut(def.id) { LinkedHashMap() }
        if (list.containsKey(player.uniqueId)) {
            fishing.messages.send(player, "tournament-already-applied")
            return false
        }
        if (list.size >= def.maxPlayers) {
            fishing.messages.send(player, "tournament-full")
            return false
        }
        if (!charge(player, def)) return false
        list[player.uniqueId] = player.name
        state.markDirty()
        fishing.messages.send(player, "tournament-applied", Ph.of().tournament(def.name))
        return true
    }

    /** 나간다. 열리기 전이면 신청을 취소한다. `refund-on-leave` 면 참가비를 돌려준다. */
    fun leave(player: Player, def: TournamentDef): Boolean {
        val running = runs[def.id]
        if (running == null) {
            if (applications[def.id]?.remove(player.uniqueId) == null) {
                fishing.messages.send(player, "tournament-not-applied")
                return false
            }
            if (def.refundOnLeave) refund(player.uniqueId, def)
            state.markDirty()
            fishing.messages.send(player, "tournament-application-cancelled", Ph.of().tournament(def.name))
            return true
        }

        if (!running.board.leave(player.uniqueId)) {
            fishing.messages.send(player, "tournament-not-participating")
            return false
        }
        if (def.refundOnLeave) refund(player.uniqueId, def)
        state.markDirty()
        fishing.messages.send(player, "tournament-left", Ph.of().tournament(def.name))
        return true
    }

    private fun charge(player: Player, def: TournamentDef): Boolean {
        if (def.entryFee <= 0.0) return true
        if (!fishing.economy.isEnabled) {
            fishing.messages.send(player, "tournament-no-economy")
            return false
        }
        if (!fishing.economy.withdraw(player, def.entryFee)) {
            fishing.messages.send(player, "tournament-fee-short", Ph.of().amount(fishing.economy.format(def.entryFee)))
            return false
        }
        return true
    }

    /** 낚은 것을 참가 중인 **모든** 대회에 반영한다. 참가자가 아니면 아무 일도 없다. */
    fun record(player: Player, result: Catch, amount: Int, now: Long) {
        for (running in runs.values) {
            repeat(amount) {
                val scored = running.board.record(
                    playerId = player.uniqueId,
                    playerName = player.name,
                    gradeId = result.grade.id,
                    fishWeight = result.fish.weight,
                    size = result.size,
                    now = now,
                )
                if (scored) scoresChanged = true
            }
        }
    }

    // --- 틱 -------------------------------------------------------------------------

    /** 1Hz. 끝날 때가 된 것은 닫고, 열 때가 된 것은 연다. 점수는 가끔 저장한다. */
    fun tick(now: Long) {
        for (running in runs.values.toList()) {
            if (now >= running.endsAt) stop(running.def.id, now)
        }

        for (def in definitions.values) {
            if (!def.autoStart || runs.containsKey(def.id)) continue
            if ((nextStarts[def.id] ?: Long.MAX_VALUE) > now) continue
            scheduleNext(def, now)
            start(def, now, manual = false)
        }

        if (scoresChanged && now - lastScoreSave >= SCORE_SAVE_MILLIS) {
            scoresChanged = false
            lastScoreSave = now
            state.markDirty()
        }
    }

    /** 디스크에 쓴다. 바뀐 것이 없으면 아무것도 안 한다. */
    fun flushState() {
        if (stateLoaded) state.flush()
        if (winnersLoaded) winners.flush()
    }

    private fun scheduleNext(def: TournamentDef, now: Long) {
        val from = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(now), ZoneId.systemDefault())
        val next = def.nextStart(from) ?: run {
            nextStarts.remove(def.id)
            return
        }
        nextStarts[def.id] = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    fun nextStartAt(def: TournamentDef): Long? = nextStarts[def.id]

    // --- 보상 실행 -------------------------------------------------------------------

    /**
     * 콘솔 명령어를 돌린다. **오프라인 참가자에게도 돈다.**
     *
     * 2세대 초기에는 오프라인이면 1등이어도 보상이 그냥 사라졌다. 대회가 끝나는 시각에
     * 반드시 접속해 있어야 한다는 규칙은 어디에도 적혀 있지 않았다.
     */
    private fun run(commands: List<String>, playerName: String) {
        val console = Bukkit.getConsoleSender()
        for (raw in commands) {
            val line = raw.replace("{player}", playerName).replace("{플레이어}", playerName)
            val ok = runCatching { Bukkit.dispatchCommand(console, line) }.getOrDefault(false)
            if (!ok) fishing.logger.warning("대회 보상 명령어가 실패했습니다: " + line)
        }
    }

    private fun refund(playerId: UUID, def: TournamentDef) {
        if (def.entryFee <= 0.0) return
        fishing.economy.deposit(Bukkit.getOfflinePlayer(playerId), def.entryFee)
    }

    private fun broadcast(key: String, ph: Ph) {
        val component = fishing.messages.component(key, ph)
        for (online in Bukkit.getOnlinePlayers()) online.sendMessage(component)
    }

    /**
     * `history/<id>.yml` — 2세대와 같은 형식. 나간 사람까지 전원 적는다.
     *
     * YAML 은 메인에서 만들고 파일 쓰기만 워커로 보낸다(core 의 계약). 서버가 꺼지는 중이면
     * 워커를 못 믿으므로 이 자리에서 바로 쓴다.
     */
    private fun writeHistory(running: ActiveTournament, now: Long, blocking: Boolean) {
        val config = YamlConfiguration()
        config.set("id", running.def.id)
        config.set("name", running.def.name)
        config.set("type", running.def.type.name)
        config.set("endedAt", timestamp(now))
        config.set(
            "ranking",
            running.board.ranked().mapIndexed { index, s ->
                linkedMapOf(
                    "rank" to index + 1,
                    "playerName" to s.playerName,
                    "playerUuid" to s.playerId.toString(),
                    "score" to s.score,
                    "catchCount" to s.catchCount,
                    "bestSize" to s.bestSize,
                    "totalSize" to s.totalSize,
                    "left" to s.left,
                )
            },
        )
        val text = config.saveToString()
        val file = fishing.io.file("tournaments", "history", running.def.id + ".yml")
        val write = {
            runCatching {
                file.parentFile?.mkdirs()
                file.writeText(text, Charsets.UTF_8)
            }.onFailure { fishing.logger.severe("대회 결과 저장 실패: " + it.message) }
        }
        if (blocking) write() else fishing.io.asyncRun { write() }
    }

    // --- 영속화 ---------------------------------------------------------------------

    override fun read(config: YamlConfiguration) {
        definitions.clear()
        nextStarts.clear()
        val root = config.getConfigurationSection("tournaments")
        if (root != null) {
            for (key in root.getKeys(false)) {
                val section = root.getConfigurationSection(key) ?: continue
                val def = TournamentDef.load(key, section)
                if (def == null) {
                    fishing.logger.warning("대회 '" + key + "' 을(를) 읽지 못했습니다")
                    continue
                }
                definitions[def.id] = def
            }
        }

        val now = System.currentTimeMillis()
        for (def in definitions.values) scheduleNext(def, now)

        if (!restored) {
            restored = true
            state.load()
            winners.load()
        }
    }

    /** 정의는 파일에서만 온다. 화면으로 고치지 않으므로 되쓸 것이 없다. */
    override fun write(config: YamlConfiguration) = Unit

    /** `tournaments/active.yml` — 진행 중인 대회와 사전 신청. */
    private inner class StateStore : YamlFileStore(fishing.io, listOf("tournaments", "active.yml"), what = "진행 중인 대회") {

        override fun read(config: YamlConfiguration) {
            stateLoaded = true
            val now = System.currentTimeMillis()
            config.getConfigurationSection("active")?.let { active ->
                for (id in active.getKeys(false)) {
                    val def = definitions[id] ?: run {
                        fishing.logger.warning("진행 중이던 대회 '$id' 의 정의가 없어 되살리지 못했습니다")
                        null
                    } ?: continue
                    val section = active.getConfigurationSection(id) ?: continue
                    val startedAt = section.getLong("started-at", now)
                    val board = Scoreboard(def.type, targetGrade = def.targetGrade.takeIf { it.isNotBlank() })
                    section.getConfigurationSection("entries")?.let { entries ->
                        for (key in entries.getKeys(false)) {
                            val playerId = runCatching { UUID.fromString(key) }.getOrNull() ?: continue
                            val e = entries.getConfigurationSection(key) ?: continue
                            board.join(playerId, e.getString("name").orEmpty()).restore(
                                score = e.getLong("score"),
                                bestSize = e.getDouble("best-size"),
                                totalSize = e.getDouble("total-size"),
                                catchCount = e.getInt("catch-count"),
                                lastScoredAt = e.getLong("last-scored-at"),
                                left = e.getBoolean("left"),
                            )
                        }
                    }
                    // 꺼져 있던 사이에 끝났어야 하는 대회는 다음 틱에 바로 닫힌다 (2세대와 같다).
                    runs[def.id] = ActiveTournament(def, board, startedAt, startedAt + def.durationMillis)
                    fishing.logger.info("진행 중이던 대회 '$id' 를 이어서 엽니다 (참가 ${board.size}명)")
                }
            }
            config.getConfigurationSection("applications")?.let { apps ->
                for (id in apps.getKeys(false)) {
                    if (!definitions.containsKey(id)) continue
                    val section = apps.getConfigurationSection(id) ?: continue
                    val list = applications.getOrPut(id) { LinkedHashMap() }
                    for (key in section.getKeys(false)) {
                        val playerId = runCatching { UUID.fromString(key) }.getOrNull() ?: continue
                        list[playerId] = section.getString(key).orEmpty()
                    }
                }
            }
        }

        override fun write(config: YamlConfiguration) {
            for ((id, running) in runs) {
                val section = config.createSection("active.$id")
                section.set("started-at", running.startedAt)
                for (s in running.board.all()) {
                    val e = section.createSection("entries." + s.playerId)
                    e.set("name", s.playerName)
                    e.set("score", s.score)
                    e.set("best-size", s.bestSize)
                    e.set("total-size", s.totalSize)
                    e.set("catch-count", s.catchCount)
                    e.set("last-scored-at", s.lastScoredAt)
                    e.set("left", s.left)
                }
            }
            for ((id, list) in applications) {
                if (list.isEmpty()) continue
                val section = config.createSection("applications.$id")
                for ((playerId, name) in list) section.set(playerId.toString(), name)
            }
        }
    }

    /**
     * `tournaments/winners.yml` — **2세대와 같은 형식**이라 2세대 파일을 그대로 옮겨 놓으면 우승
     * 기록이 이어진다.
     */
    inner class WinnerStore : YamlFileStore(fishing.io, listOf("tournaments", "winners.yml"), what = "대회 우승 기록") {

        private val entries = LinkedHashMap<UUID, Winner>()

        fun add(playerId: UUID, name: String, tournamentId: String, now: Long) {
            val winner = entries.getOrPut(playerId) { Winner(name, 0, mutableListOf()) }
            winner.name = name
            winner.count++
            winner.history += tournamentId + " @ " + timestamp(now)
            markDirty()
        }

        /** 우승 횟수가 많은 순. */
        fun top(limit: Int): List<Pair<String, Int>> =
            entries.values.sortedByDescending { it.count }.take(limit).map { it.name to it.count }

        override fun read(config: YamlConfiguration) {
            entries.clear()
            winnersLoaded = true
            val root = config.getConfigurationSection("winners") ?: return
            for (key in root.getKeys(false)) {
                val playerId = runCatching { UUID.fromString(key) }.getOrNull() ?: continue
                val section = root.getConfigurationSection(key) ?: continue
                entries[playerId] = Winner(
                    section.getString("playerName").orEmpty(),
                    section.getInt("count"),
                    section.getStringList("history").toMutableList(),
                )
            }
        }

        override fun write(config: YamlConfiguration) {
            for ((playerId, winner) in entries) {
                val section = config.createSection("winners.$playerId")
                section.set("playerName", winner.name)
                section.set("count", winner.count)
                section.set("history", winner.history)
            }
        }
    }

    private class Winner(var name: String, var count: Int, val history: MutableList<String>)

    private companion object {
        /** 결과 공지는 상위 몇 명까지. 2세대와 같다. */
        const val ANNOUNCED = 3

        const val SCORE_SAVE_MILLIS = 60_000L

        fun timestamp(now: Long): String = LocalDateTime
            .ofInstant(java.time.Instant.ofEpochMilli(now), ZoneId.systemDefault())
            .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    }
}

/** 지금 도는 대회. */
class ActiveTournament(
    val def: TournamentDef,
    val board: Scoreboard,
    val startedAt: Long,
    val endsAt: Long,
) {
    fun remainingSeconds(now: Long): Long = ((endsAt - now).coerceAtLeast(0L)) / 1000L
}
