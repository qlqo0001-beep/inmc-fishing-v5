package com.inmc.fishing.command

import com.inmc.fishing.Fishing
import com.inmc.fishing.FishingPlugin
import com.inmc.fishing.gui.AdminMenu
import com.inmc.fishing.gui.CollectionMenu
import com.inmc.fishing.gui.FilletMenu
import com.inmc.fishing.gui.MainMenu
import com.inmc.fishing.gui.NetMenu
import com.inmc.fishing.gui.RankingMenu
import com.inmc.fishing.gui.TournamentMenu
import com.inmc.fishing.player.Angler
import com.inmc.fishing.util.Ph
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.suggestion.SuggestionProvider
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import kr.inmc.core.item.StoredItem
import kr.inmc.core.util.Text
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin

/**
 * `/낚시` 한 트리.
 *
 * 한글 인자는 전부 [StringArgumentType.greedyString] 이다 — Brigadier 의 `word()`/`string()`
 * 은 한글 첫 글자에서 멈춘다. greedy 는 마지막 인자여야만 해서, 지급 명령은 이름을 맨 뒤로
 * 밀고 개수를 앞에 뒀다.
 */
class FishingCommand(private val fishing: Fishing) {

    private val tools = FishingTools(fishing)

    fun register(plugin: JavaPlugin) {
        plugin.lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            event.registrar().register(tree().build(), "INMC 낚시", listOf("fishing", "낚시터"))
        }
    }

    // --- 추천 -----------------------------------------------------------------------

    private fun suggest(ids: () -> List<String>) = SuggestionProvider<CommandSourceStack> { _, builder ->
        ids().filter { it.startsWith(builder.remainingLowerCase, ignoreCase = true) }
            .forEach { builder.suggest(it) }
        builder.buildFuture()
    }

    private val players = SuggestionProvider<CommandSourceStack> { _, builder ->
        Bukkit.getOnlinePlayers()
            .filter { it.name.startsWith(builder.remainingLowerCase, ignoreCase = true) }
            .forEach { builder.suggest(it.name) }
        builder.buildFuture()
    }

    // --- 트리 -----------------------------------------------------------------------

    private fun tree(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("낚시")
            // 2세대는 일반 명령을 `infishing.user`(기본 true)로 막을 수 있었다. 이 문을 없애면
            // 특정 그룹에서 그 권한을 빼 낚시 명령을 막아 둔 서버가 그 통제를 잃는다.
            .requires { it.sender.hasPermission(USER) || it.sender.hasPermission(ADMIN) }
            .executes { ctx -> usage(ctx.source.sender) }

            .then(Commands.literal("도감").executes { ctx -> withAngler(ctx.source.sender) { p, a -> CollectionMenu(fishing, p, a).open(p) } })
            .then(Commands.literal("어망").executes { ctx -> withAngler(ctx.source.sender) { p, a -> NetMenu(fishing, p, a).open(p) } })
            .then(
                Commands.literal("손질")
                    .executes { ctx -> withAngler(ctx.source.sender) { p, a -> FilletMenu(fishing, p, a).open(p) } }
                    // 2세대 `fillet setSlots <플레이어> <set|add> <수>`
                    .then(
                        Commands.literal("칸").requires(::isAdmin)
                            .then(adjustBranch("설정", add = false) { s, n, v, a -> tools.filletSlots(s, n, v, a) })
                            .then(adjustBranch("추가", add = true) { s, n, v, a -> tools.filletSlots(s, n, v, a) }),
                    ),
            )
            .then(Commands.literal("메인").executes { ctx -> withAngler(ctx.source.sender) { p, a -> MainMenu(fishing, p, a).open(p) } })
            .then(Commands.literal("스탯").executes { ctx -> withAngler(ctx.source.sender) { p, a -> tools.stats(p, a) } })
            .then(Commands.literal("자랑").executes { ctx -> withPlayer(ctx.source.sender) { tools.show(it) } })
            .then(Commands.literal("정보").executes { ctx -> tools.info(ctx.source.sender).let { 1 } })
            .then(
                Commands.literal("목록")
                    .executes { ctx -> tools.list(ctx.source.sender, null).let { 1 } }
                    .then(
                        Commands.argument("등급", StringArgumentType.word())
                            .suggests(suggest { fishing.grades.ordered.map { it.id } })
                            .executes { ctx -> tools.list(ctx.source.sender, StringArgumentType.getString(ctx, "등급")).let { 1 } },
                    ),
            )
            .then(Commands.literal("랭킹").executes { ctx -> withPlayer(ctx.source.sender) { RankingMenu(fishing, it).open(it) } })
            .then(
                Commands.literal("대회")
                    .executes { ctx -> withPlayer(ctx.source.sender) { TournamentMenu(fishing, it).open(it) } }
                    .then(Commands.literal("목록").executes { ctx -> tournamentList(ctx.source.sender) })
                    .then(Commands.literal("우승").executes { ctx -> tournamentWins(ctx.source.sender) })
                    .then(tournamentBranch("참가", joining = true))
                    .then(tournamentBranch("퇴장", joining = false)),
            )
            .then(
                Commands.literal("피로도")
                    .executes { ctx -> withAngler(ctx.source.sender) { p, a -> tools.fatigueStatus(p, p, a) } }
                    // 2세대 `fatigue add|set <플레이어> <값>` · `fatigue <플레이어>`
                    .then(adjustBranch("설정", add = false) { s, n, v, a -> tools.fatigueAdjust(s, n, v, a) }.requires(::isAdmin))
                    .then(adjustBranch("추가", add = true) { s, n, v, a -> tools.fatigueAdjust(s, n, v, a) }.requires(::isAdmin))
                    .then(
                        Commands.argument("플레이어", StringArgumentType.word()).requires(::isAdmin).suggests(players)
                            .executes { ctx -> tools.fatigueOf(ctx.source.sender, StringArgumentType.getString(ctx, "플레이어")).let { 1 } },
                    ),
            )
            .then(
                Commands.literal("미니게임")
                    .executes { ctx -> withAngler(ctx.source.sender) { p, a -> toggleMinigame(p, a) } }
                    // 2세대 `minigame on|off|status`
                    .then(Commands.literal("켜기").executes { ctx -> withAngler(ctx.source.sender) { p, a -> fishing.catches.setMinigame(p, a, true) } })
                    .then(Commands.literal("끄기").executes { ctx -> withAngler(ctx.source.sender) { p, a -> fishing.catches.setMinigame(p, a, false) } })
                    .then(
                        Commands.literal("상태").executes { ctx ->
                            withAngler(ctx.source.sender) { p, a ->
                                fishing.messages.send(p, if (a.minigameEnabled) "minigame-on" else "minigame-off")
                            }
                        },
                    ),
            )

            .then(Commands.literal("관리").requires(::isAdmin).executes { ctx -> withPlayer(ctx.source.sender) { AdminMenu(fishing, it).open(it) } })
            .then(Commands.literal("디버그").requires(::isAdmin).executes { ctx -> tools.debug(ctx.source.sender).let { 1 } })
            .then(
                Commands.literal("시뮬레이션").requires(::isAdmin).then(
                    Commands.argument("횟수", IntegerArgumentType.integer(1, 10_000_000))
                        .executes { ctx -> tools.simulate(ctx.source.sender, IntegerArgumentType.getInteger(ctx, "횟수")).let { 1 } },
                ),
            )
            .then(
                Commands.literal("시험판").requires(::isAdmin).then(
                    Commands.argument("등급", StringArgumentType.word())
                        .suggests(suggest { fishing.grades.ordered.map { it.id } })
                        .then(testFightBranch("트로피", rare = false))
                        .then(testFightBranch("레어", rare = true)),
                ),
            )
            .then(
                Commands.literal("시험모드").requires(::isAdmin)
                    .executes { ctx -> withAngler(ctx.source.sender) { p, a -> tools.testMode(p, a, null) } }
                    .also { node ->
                        for (mode in listOf("켜기", "끄기", "상태")) {
                            node.then(Commands.literal(mode).executes { ctx -> withAngler(ctx.source.sender) { p, a -> tools.testMode(p, a, mode) } })
                        }
                    },
            )
            .then(Commands.literal("리로드").requires(::isAdmin).executes { ctx -> reload(ctx.source.sender) })

            .then(
                Commands.literal("대회시작").requires(::isAdmin)
                    .then(
                        Commands.argument("대회", StringArgumentType.word())
                            .suggests(suggest { fishing.tournaments.all().map { it.id } })
                            .executes { ctx ->
                                startTournament(ctx.source.sender, StringArgumentType.getString(ctx, "대회"))
                            },
                    ),
            )
            .then(
                Commands.literal("대회종료").requires(::isAdmin)
                    .executes { ctx -> stopTournament(ctx.source.sender, null) }
                    .then(
                        Commands.argument("대회", StringArgumentType.word())
                            .suggests(suggest { fishing.tournaments.running().map { it.def.id } })
                            .executes { ctx -> stopTournament(ctx.source.sender, StringArgumentType.getString(ctx, "대회")) },
                    ),
            )

            .then(
                Commands.literal("지급").requires(::isAdmin)
                    .also { give -> for (kind in GiveKind.entries) give.then(giveBranch(kind)) }
                    // 2세대 `give net` — 아이템이 아니라 어망으로 간다.
                    .then(
                        playerCountName("어망", suggest { fishing.fish.ids() }) { s, n, c, id -> tools.giveNet(s, n, c, id) },
                    ),
            )

    /** `<설정|추가> <플레이어> <값>`. 피로도와 손질대 칸이 같은 모양이다. */
    private fun adjustBranch(
        label: String,
        add: Boolean,
        action: (CommandSender, String, Int, Boolean) -> Unit,
    ): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal(label).then(
            Commands.argument("플레이어", StringArgumentType.word()).suggests(players).then(
                Commands.argument("값", IntegerArgumentType.integer(if (add) Int.MIN_VALUE else 0))
                    .executes { ctx ->
                        action(
                            ctx.source.sender,
                            StringArgumentType.getString(ctx, "플레이어"),
                            IntegerArgumentType.getInteger(ctx, "값"),
                            add,
                        )
                        1
                    },
            ),
        )

    /** `시험판 <등급> <트로피|레어>`. */
    private fun testFightBranch(label: String, rare: Boolean): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal(label).executes { ctx ->
            withAngler(ctx.source.sender) { p, a -> tools.testFight(p, a, StringArgumentType.getString(ctx, "등급"), rare) }
        }

    /** `<label> <플레이어> <개수> <이름...>` — [giveBranch] 와 같은 모양, 동작만 다르다. */
    private fun playerCountName(
        label: String,
        names: SuggestionProvider<CommandSourceStack>,
        action: (CommandSender, String, Int, String) -> Unit,
    ): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal(label).then(
            Commands.argument("플레이어", StringArgumentType.word()).suggests(players).then(
                Commands.argument("개수", IntegerArgumentType.integer(1, 2304)).then(
                    Commands.argument("이름", StringArgumentType.greedyString()).suggests(names)
                        .executes { ctx ->
                            action(
                                ctx.source.sender,
                                StringArgumentType.getString(ctx, "플레이어"),
                                IntegerArgumentType.getInteger(ctx, "개수"),
                                StringArgumentType.getString(ctx, "이름"),
                            )
                            1
                        },
                ),
            ),
        )

    /** `대회 참가|퇴장 <id>`. 열리기 전이면 사전 신청·신청 취소가 된다. */
    private fun tournamentBranch(label: String, joining: Boolean): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal(label).then(
            Commands.argument("대회", StringArgumentType.word())
                .suggests(suggest { fishing.tournaments.all().map { it.id } })
                .executes { ctx ->
                    withPlayer(ctx.source.sender) {
                        tournamentJoin(it, StringArgumentType.getString(ctx, "대회"), joining)
                    }
                },
        )

    /** `지급 <종류> <플레이어> <개수> <이름...>`. 이름이 한글일 수 있어 맨 뒤다. */
    private fun giveBranch(kind: GiveKind): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal(kind.label)
            .then(
                Commands.argument("플레이어", StringArgumentType.word()).suggests(players)
                    .then(
                        Commands.argument("개수", IntegerArgumentType.integer(1, 2304))
                            .then(
                                Commands.argument("이름", StringArgumentType.greedyString())
                                    .suggests(suggest { idsOf(kind) })
                                    .executes { ctx ->
                                        give(
                                            ctx.source.sender,
                                            kind,
                                            StringArgumentType.getString(ctx, "플레이어"),
                                            IntegerArgumentType.getInteger(ctx, "개수"),
                                            StringArgumentType.getString(ctx, "이름"),
                                        )
                                    },
                            ),
                    ),
            )

    // --- 동작 -----------------------------------------------------------------------

    private fun isAdmin(source: CommandSourceStack): Boolean = source.sender.hasPermission(ADMIN)

    private fun usage(sender: CommandSender): Int {
        sender.sendMessage(Text.render("<gray>────── <aqua>INMC 낚시</aqua> ──────</gray>"))
        sender.sendMessage(Text.render("<yellow>/낚시 메인</yellow> <gray>(낚싯대를 들고 Shift+F)</gray>"))
        sender.sendMessage(Text.render("<yellow>/낚시 도감</yellow> <gray>· <yellow>어망</yellow> · <yellow>손질</yellow> · <yellow>랭킹</yellow> · <yellow>대회</yellow> · <yellow>스탯</yellow> · <yellow>자랑</yellow></gray>"))
        sender.sendMessage(Text.render("<yellow>/낚시 대회</yellow> <gray>[목록 · 참가 · 퇴장 · 우승]</gray>"))
        sender.sendMessage(Text.render("<yellow>/낚시 피로도</yellow> <gray>· <yellow>미니게임</yellow> [켜기 · 끄기 · 상태] · <yellow>목록</yellow> · <yellow>정보</yellow></gray>"))
        if (sender.hasPermission(ADMIN)) {
            sender.sendMessage(Text.render("<gold>/낚시 관리</gold> <gray>- 물고기·낚싯대·미끼·물약·생선살</gray>"))
            sender.sendMessage(Text.render("<gold>/낚시 리로드 · 지급 · 대회시작 · 대회종료 · 디버그 · 시뮬레이션</gold>"))
            sender.sendMessage(Text.render("<gold>/낚시 시험판 · 시험모드 · 피로도 설정|추가 · 손질 칸</gold>"))
        }
        return 1
    }

    private fun withPlayer(sender: CommandSender, block: (Player) -> Unit): Int {
        val player = sender as? Player ?: run {
            fishing.messages.send(sender, "player-only")
            return 0
        }
        block(player)
        return 1
    }

    /**
     * 기록이 아직 안 올라온 사이에는 아무것도 하지 않는다.
     *
     * 빈 기록으로 화면을 열면 그 위에서 등록·회수를 하게 되고, 진짜 기록이 올라오는 순간
     * 그 조작이 통째로 사라진다.
     */
    private fun withAngler(sender: CommandSender, block: (Player, Angler) -> Unit): Int =
        withPlayer(sender) { player ->
            val angler = fishing.anglers.of(player)
            if (angler == null) {
                fishing.messages.send(player, "not-ready")
                return@withPlayer
            }
            block(player, angler)
        }

    private fun toggleMinigame(player: Player, angler: Angler) {
        fishing.catches.setMinigame(player, angler, !angler.minigameEnabled)
    }

    private fun reload(sender: CommandSender): Int {
        val plugin = fishing.plugin as? FishingPlugin ?: return 0
        plugin.reload {
            fishing.messages.send(sender, "reloaded")
            sender.sendMessage(
                Text.render(
                    "<gray>물고기 " + fishing.fish.size +
                        " · 낚싯대 " + fishing.rods.all().size +
                        " · 미끼 " + fishing.baits.size +
                        " · 물약 " + fishing.potions.size +
                        " · 생선살 " + fishing.fillets.size +
                        " · 대회 " + fishing.tournaments.all().size + "</gray>",
                ),
            )
        }
        return 1
    }

    private fun startTournament(sender: CommandSender, id: String): Int {
        val def = fishing.tournaments.get(id) ?: run {
            fishing.messages.send(sender, "tournament-not-found")
            return 0
        }
        if (!fishing.tournaments.start(def)) {
            sender.sendMessage(Text.render("<red>'" + def.id + "' 은(는) 이미 진행 중입니다.</red>"))
            return 0
        }
        return 1
    }

    /** `[id]` 를 빼면 도는 대회가 하나일 때만 그것을 닫는다. 여럿이면 무엇을 닫을지 묻는다. */
    private fun stopTournament(sender: CommandSender, id: String?): Int {
        val running = fishing.tournaments.running()
        val target = when {
            id != null -> fishing.tournaments.runOf(id)
            running.size == 1 -> running.first()
            else -> null
        }
        if (target == null) {
            if (id == null && running.size > 1) {
                sender.sendMessage(Text.render("<yellow>여러 대회가 진행 중입니다: <white>" + running.joinToString(", ") { it.def.id } + "</white></yellow>"))
            } else {
                fishing.messages.send(sender, "tournament-not-running")
            }
            return 0
        }
        fishing.tournaments.stop(target.def.id)
        return 1
    }

    private fun tournamentList(sender: CommandSender): Int {
        fishing.messages.send(sender, "tournament-list-header")
        for (def in fishing.tournaments.all()) {
            val status = if (fishing.tournaments.runOf(def.id) != null) "진행 중" else "대기 중"
            fishing.messages.send(
                sender,
                "tournament-list-line",
                Ph.of().id(def.id).tournament(def.name).value(status),
            )
        }
        return 1
    }

    private fun tournamentJoin(player: Player, id: String, joining: Boolean) {
        val def = fishing.tournaments.get(id) ?: run {
            fishing.messages.send(player, "tournament-not-found")
            return
        }
        if (joining) fishing.tournaments.join(player, def) else fishing.tournaments.leave(player, def)
    }

    /** 우승 횟수 상위 10명 — 2세대 `/fishing tournament wins`. */
    private fun tournamentWins(sender: CommandSender): Int {
        val top = fishing.tournaments.winners.top(WINS_SHOWN)
        fishing.messages.send(sender, "tournament-wins-header")
        if (top.isEmpty()) fishing.messages.send(sender, "tournament-no-wins")
        for ((index, entry) in top.withIndex()) {
            fishing.messages.send(
                sender,
                "tournament-wins-line",
                Ph.of().rank(index + 1).player(entry.first).count(entry.second),
            )
        }
        return 1
    }

    // --- 지급 -----------------------------------------------------------------------

    private enum class GiveKind(val label: String) {
        FISH("물고기"), ROD("낚싯대"), BAIT("미끼"), POTION("물약"), FILLET("생선살"),

        /** 2세대 `give trophy` — 최대 크기의 트로피 물고기. */
        TROPHY("트로피"),
    }

    private fun idsOf(kind: GiveKind): List<String> = when (kind) {
        GiveKind.FISH -> fishing.fish.ids()
        GiveKind.ROD -> fishing.rods.all().map { it.id }
        GiveKind.BAIT -> fishing.baits.ids()
        GiveKind.POTION -> fishing.potions.ids()
        GiveKind.FILLET -> fishing.fillets.ids()
        GiveKind.TROPHY -> fishing.fish.all().filter { it.size.hasSize }.map { it.id }
    }

    private fun stackOf(kind: GiveKind, id: String, amount: Int): ItemStack? = when (kind) {
        GiveKind.FISH -> fishing.fish.get(id)?.let { fishing.fish.stack(it, amount) }
        GiveKind.ROD -> fishing.rods.byId(id)?.let { fishing.catches.rodStack(it, amount) }
        GiveKind.BAIT -> fishing.baits.get(id)?.let { fishing.baits.stack(it, amount) }
        GiveKind.POTION -> fishing.potions.get(id)?.let { fishing.potions.stack(it, amount) }
        GiveKind.FILLET -> fishing.fillets.get(id)?.let { fishing.fillets.stack(it, amount) }
        GiveKind.TROPHY -> fishing.fish.get(id)?.let { tools.trophyStack(it, amount) }
    }

    /**
     * 물고기를 지급하면 **크기를 새로 뽑아 찍는다.**
     *
     * 안 찍으면 도감에 등록할 수 없는 물고기가 나온다 — 크기가 없으면 [FishStamp] 도 없고,
     * 도감은 그 스탬프를 보고 등록하기 때문이다.
     */
    private fun give(sender: CommandSender, kind: GiveKind, playerName: String, amount: Int, rawId: String): Int {
        val target = Bukkit.getPlayerExact(playerName.trim()) ?: run {
            fishing.messages.send(sender, "player-not-found", Ph.of().player(playerName))
            return 0
        }
        val id = rawId.trim().lowercase()

        val stack = stackOf(kind, id, amount) ?: run {
            fishing.messages.send(sender, "unknown-fish", Ph.of().fish(rawId))
            return 0
        }

        if (kind == GiveKind.FISH) {
            val fish = fishing.fish.get(id)
            if (fish != null) {
                com.inmc.fishing.fish.FishStamp(
                    fishId = fish.id,
                    gradeId = fish.gradeId,
                    size = fish.size.roll(0.0),
                    trophy = com.inmc.fishing.fish.Trophy.NONE,
                    caughtAt = System.currentTimeMillis(),
                ).applyTo(stack)
            }
        }

        for (left in target.inventory.addItem(stack).values) {
            target.world.dropItem(target.location, left)
        }

        fishing.messages.send(
            sender,
            "given",
            Ph.of().player(target.name).fish(StoredItem.plainName(stack) ?: id).amount(amount),
        )
        return 1
    }

    private companion object {
        const val ADMIN = "infishing.admin"
        const val USER = "infishing.user"

        /** `대회 우승` 이 보여주는 인원. 2세대와 같다. */
        const val WINS_SHOWN = 10
    }
}
