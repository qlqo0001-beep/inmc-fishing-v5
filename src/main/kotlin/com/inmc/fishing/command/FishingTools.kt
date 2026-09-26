package com.inmc.fishing.command

import com.inmc.fishing.Fishing
import com.inmc.fishing.catching.RandomRolls
import com.inmc.fishing.fish.Fish
import com.inmc.fishing.fish.FishLore
import com.inmc.fishing.fish.FishStamp
import com.inmc.fishing.fish.Simulation
import com.inmc.fishing.fish.Trophy
import com.inmc.fishing.player.Angler
import com.inmc.fishing.util.Ph
import kr.inmc.core.util.Text
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * 2세대 `/fishing` 에 있던 가지들. 5세대가 처음에 옮기지 않았던 것을 되살린 것이다 —
 * `스탯` · `자랑` · `정보` · `디버그` · `시뮬레이션` · `목록` · `시험판` · `시험모드` ·
 * `피로도 설정/추가` · `손질 칸` · `지급 어망/트로피`.
 *
 * 트리는 [FishingCommand] 가 갖고, 여기는 **하는 일**만 둔다.
 */
class FishingTools(private val fishing: Fishing) {

    // --- 일반 -----------------------------------------------------------------------

    /** `/낚시 스탯` — 메인 화면 머리 아이콘과 같은 줄. */
    fun stats(player: Player, angler: Angler) {
        fishing.messages.send(player, "stats-header")
        for (line in fishing.catches.statsLines(player, angler)) player.sendMessage(Text.render(line))
    }

    /** `/낚시 자랑` — 손에 든 물고기를 서버 전체에 알린다(2세대 `show`). */
    fun show(player: Player) {
        val stamp = FishStamp.read(player.inventory.itemInMainHand)
        val fish = stamp?.let { fishing.fish.get(it.fishId) }
        if (stamp == null || fish == null) {
            fishing.messages.send(player, "show-hold-fish")
            return
        }
        val trophy = when (stamp.trophy) {
            Trophy.RARE -> "<light_purple>🏆 레어 트로피</light_purple>"
            Trophy.NORMAL -> "<gold>🏆 트로피</gold>"
            Trophy.NONE -> ""
        }
        val component = fishing.messages.component(
            "show-broadcast",
            Ph.of().player(player).fish(fish.label()).size(stamp.size)
                .grade(fishing.grades[stamp.gradeId]?.tag().orEmpty()).value(trophy),
        )
        for (online in Bukkit.getOnlinePlayers()) online.sendMessage(component)
    }

    /** `/낚시 정보` — 버전과 등록 수(2세대 `info`). */
    fun info(sender: CommandSender) {
        line(sender, "<gray>────── <aqua>INMC 낚시 정보</aqua> ──────</gray>")
        line(sender, "<gray>버전: <white>" + fishing.plugin.pluginMeta.version + "</white></gray>")
        line(sender, "<gray>API: <white>" + fishing.plugin.pluginMeta.apiVersion + "</white></gray>")
        counts(sender)
        if (sender is Player) {
            val busy = fishing.anglers.of(sender)?.isBusy == true
            line(sender, "<gray>진행 중인 판: <white>" + (if (busy) "있음" else "없음") + "</white></gray>")
        }
    }

    /** `/낚시 디버그` — 등록 수 · 연동 · 진행 상태(2세대 `debug`). */
    fun debug(sender: CommandSender) {
        line(sender, "<gray>────── <gold>INMC 낚시 디버그</gold> ──────</gray>")
        counts(sender)
        line(sender, "<gray>진행 중인 판: <white>" + fishing.anglers.all().count { it.isBusy } + "</white></gray>")
        line(sender, "<gray>켜짐: <white>" + fishing.config.enabled + "</white></gray>")
        line(sender, "<gray>대회: <white>" + fishing.tournaments.all().size + "</white>개 · 진행 <white>" + fishing.tournaments.running().size + "</white></gray>")
        line(sender, "<gray>WorldGuard 규칙: <white>" + fishing.regionRules.size + "</white>개</gray>")
        for ((name, on) in listOf(
            "Vault" to fishing.economy.isEnabled,
            "PlaceholderAPI" to Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI"),
            "WorldGuard" to Bukkit.getPluginManager().isPluginEnabled("WorldGuard"),
            "MMOItems" to Bukkit.getPluginManager().isPluginEnabled("MMOItems"),
        )) {
            line(sender, "<gray>$name: " + (if (on) "<green>연결됨</green>" else "<dark_gray>없음</dark_gray>") + "</gray>")
        }
    }

    private fun counts(sender: CommandSender) {
        line(
            sender,
            "<gray>등급 <white>" + fishing.grades.ordered.size + "</white> · 물고기 <white>" + fishing.fish.size +
                "</white> · 낚싯대 <white>" + fishing.rods.all().size + "</white></gray>",
        )
    }

    /**
     * `/낚시 시뮬레이션 <횟수>` — 확률 검증(2세대 `simulate`).
     *
     * 최대 천만 번을 메인에서 돌리면 서버가 멈춘다. 추첨기는 불변 스냅샷이라 **워커에서 돌리고**
     * 출력만 메인으로 되돌린다 — 2세대와 같다.
     */
    fun simulate(sender: CommandSender, count: Int) {
        line(sender, "<gray>$count 회 추첨 중...</gray>")
        val engine = fishing.engine
        Bukkit.getAsyncScheduler().runNow(fishing.plugin) {
            val result = Simulation.run(engine, count, RandomRolls())
            Bukkit.getGlobalRegionScheduler().run(fishing.plugin) { printSimulation(sender, count, result) }
        }
    }

    private fun printSimulation(sender: CommandSender, count: Int, r: Simulation.Result) {
        line(sender, "<gray>────── <gold>시뮬레이션 $count 회</gold> ──────</gray>")
        line(sender, "<gray>결과: <white>" + r.total + "</white></gray>")
        line(sender, "<gray>대어: <white>" + r.bigFish + "</white> (" + Simulation.percent(r.bigFish, r.total) + "%)</gray>")
        line(sender, "<gray>더블: <white>" + r.double + "</white> (" + Simulation.percent(r.double, r.total) + "%)</gray>")
        line(sender, "<yellow>등급별</yellow>")
        // 등급표 순서로 (처음 나온 순서가 아니라). 안 나온 등급도 0 으로 보여야 설정 문제가 드러난다.
        for (grade in fishing.grades.ordered) {
            val n = r.byGrade[grade.id] ?: 0L
            line(sender, "<gray>  " + grade.id.uppercase() + ": <white>$n</white> (" + Simulation.percent(n, r.total) + "%)</gray>")
        }
        line(sender, "<yellow>물고기별</yellow>")
        for ((id, n) in r.byFish) {
            val name = fishing.fish.get(id)?.label() ?: id
            line(sender, "<gray>  $name: <white>$n</white> (" + Simulation.percent(n, r.total) + "%)</gray>")
        }
    }

    /** `/낚시 목록 [등급]` — 물고기 목록(2세대 `list`). */
    fun list(sender: CommandSender, gradeFilter: String?) {
        line(sender, "<gray>────── <aqua>물고기 목록</aqua> ──────</gray>")
        val fish = if (gradeFilter == null) fishing.fish.all() else fishing.fish.ofGrade(gradeFilter)
        for (f in fish) {
            val tag = fishing.grades[f.gradeId]?.tag() ?: f.gradeId.uppercase()
            line(sender, "$tag <white>" + f.label() + "</white> <dark_gray>(" + f.id + ")</dark_gray>")
        }
        if (fish.isEmpty()) line(sender, "<dark_gray>없습니다.</dark_gray>")
    }

    // --- 관리자: 힘겨루기 시험 -----------------------------------------------------------

    /** `/낚시 시험판 <등급> <트로피|레어>` — 2세대 `testfight`. */
    fun testFight(player: Player, angler: Angler, gradeId: String, rare: Boolean) {
        if (fishing.grades[gradeId] == null) {
            line(player, "<red>'$gradeId' 등급이 없습니다.</red>")
            return
        }
        if (!fishing.flow.testFight(player, angler, gradeId, rare)) {
            line(player, "<red>이미 판이 진행 중이거나 그 등급에 물고기가 없습니다.</red>")
            return
        }
        val rod = fishing.catches.rodOf(player)
        val fight = angler.fight?.fight ?: return
        line(player, "<yellow>시험판 시작: " + gradeId.uppercase() + " " + (if (rare) "레어 트로피" else "트로피") + "</yellow>")
        line(player, "<gray>좌클릭·W 감기 · 우클릭·S 풀기. 이기면 보상이 정상 지급됩니다.</gray>")
        line(
            player,
            "<gray>인식된 낚싯대: <white>" + (rod?.id ?: "없음") + "</white> → 릴 파워 " + String.format("%.1f", fight.reelPower) +
                ", 줄 강도 " + String.format("%.1f", fight.lineStrength) + ", 릴 내구도 " + String.format("%.1f", fight.reelDurability) +
                ", 최대 거리 " + String.format("%.1f", fight.maxDistance) + "</gray>",
        )
    }

    /** `/낚시 시험모드 [켜기|끄기|상태]` — 2세대 `testfightmode`. 인자가 없으면 전환한다. */
    fun testMode(player: Player, angler: Angler, mode: String?) {
        when (mode) {
            null -> angler.testFight = !angler.testFight
            "켜기" -> angler.testFight = true
            "끄기" -> angler.testFight = false
            "상태" -> Unit
        }
        line(
            player,
            "<gray>시험모드: " + (if (angler.testFight) "<green>ON</green> — 입질 즉시 힘겨루기" else "<red>OFF</red>") + "</gray>",
        )
    }

    // --- 관리자: 피로도 · 손질대 ---------------------------------------------------------

    /** `/낚시 피로도 <플레이어>` — 남의 피로도 보기. */
    fun fatigueOf(sender: CommandSender, name: String) {
        val (player, angler) = online(sender, name) ?: return
        fatigueStatus(sender, player, angler)
    }

    fun fatigueStatus(sender: CommandSender, player: Player, angler: Angler) {
        val ceiling = angler.fatigue.ceiling(fishing.catches.rodOf(player)?.maxFatigue ?: 0)
        fishing.messages.send(sender, "fatigue-status", Ph.of().count(angler.fatigue.value).amount(ceiling))
        if (angler.fatigue.locked) fishing.messages.send(sender, "minigame-locked")
    }

    /** `/낚시 피로도 설정|추가 <플레이어> <값>` — 2세대 `fatigue set|add`. */
    fun fatigueAdjust(sender: CommandSender, name: String, value: Int, add: Boolean) {
        val (player, angler) = online(sender, name) ?: return
        angler.fatigue.set(if (add) angler.fatigue.value + value else value)
        fishing.anglers.markDirty(angler)
        line(sender, "<green>" + player.name + " 의 피로도: <white>" + angler.fatigue.value + "</white></green>")
        fishing.logger.info(sender.name + " 이(가) " + player.name + " 의 피로도를 " + angler.fatigue.value + " 로 바꿨습니다")
    }

    /** `/낚시 손질 칸 <플레이어> <설정|추가> <수>` — 2세대 `fillet setSlots`. */
    fun filletSlots(sender: CommandSender, name: String, value: Int, add: Boolean) {
        val (player, angler) = online(sender, name) ?: return
        val before = angler.bench.slotCount
        val after = angler.setBenchSlots(if (add) before + value else value)
        fishing.anglers.markDirty(angler)
        line(sender, "<green>" + player.name + " 의 손질대: <white>$before</white> → <white>$after</white>칸</green>")
        fishing.logger.info(sender.name + " 이(가) " + player.name + " 의 손질대를 " + after + "칸으로 바꿨습니다")
    }

    // --- 관리자: 지급 어망 · 트로피 --------------------------------------------------------

    /** `/낚시 지급 어망 <플레이어> <개수> <물고기>` — 평균 크기로 어망에 넣는다(2세대 `give net`). */
    fun giveNet(sender: CommandSender, name: String, amount: Int, fishId: String) {
        val (player, angler) = online(sender, name) ?: return
        val fish = fishing.fish.get(fishId.trim()) ?: run {
            fishing.messages.send(sender, "unknown-fish", Ph.of().fish(fishId))
            return
        }
        var added = 0
        repeat(amount) {
            val stamp = FishStamp(fish.id, fish.gradeId, fish.size.avg, Trophy.NONE, System.currentTimeMillis())
            if (angler.net.add(stamp)) added++
        }
        fishing.anglers.markDirty(angler)
        line(sender, "<green>" + player.name + " 의 어망에 " + fish.label() + " $added/$amount 마리를 넣었습니다.</green>")
        if (added < amount) line(sender, "<yellow>어망이 가득 차 일부만 들어갔습니다.</yellow>")
    }

    /**
     * 트로피 물고기 — **최대 크기**로 만든다(2세대 `give trophy`). 크기가 없는 물고기는 트로피가 될
     * 수 없으므로 null.
     */
    fun trophyStack(fish: Fish, amount: Int): ItemStack? {
        if (!fish.size.hasSize) return null
        val size = fish.size.max
        val trophy = fishing.config.trophyRule.judge(fish.size, size).takeIf { it.isTrophy } ?: Trophy.NORMAL
        val stamp = FishStamp(fish.id, fish.gradeId, size, trophy, System.currentTimeMillis())
        val stack = fishing.fish.stack(fish, amount, FishLore.of(stamp, fishing.grades[fish.gradeId]?.tag().orEmpty()))
            ?: return null
        stamp.applyTo(stack)
        return stack
    }

    // --- 공통 -----------------------------------------------------------------------

    private fun online(sender: CommandSender, name: String): Pair<Player, Angler>? {
        val player = Bukkit.getPlayerExact(name.trim())
        val angler = player?.let { fishing.anglers.of(it) }
        if (player == null || angler == null) {
            fishing.messages.send(sender, "player-not-found", Ph.of().player(name))
            return null
        }
        return player to angler
    }

    private fun line(sender: CommandSender, mini: String) = sender.sendMessage(Text.render(mini))
}
