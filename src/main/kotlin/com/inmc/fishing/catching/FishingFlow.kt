package com.inmc.fishing.catching

import com.inmc.fishing.Fishing
import com.inmc.fishing.fight.Fight
import com.inmc.fishing.fight.FightKeys
import com.inmc.fishing.fight.FightOutcome
import com.inmc.fishing.fight.FishState
import com.inmc.fishing.fish.Catch
import com.inmc.fishing.fish.Trophy
import com.inmc.fishing.minigame.Click
import com.inmc.fishing.minigame.ClickGame
import com.inmc.fishing.minigame.Progress
import com.inmc.fishing.player.Angler
import com.inmc.fishing.player.BiteSession
import com.inmc.fishing.player.FightSession
import com.inmc.fishing.player.IntroSession
import com.inmc.fishing.util.Ph
import kr.inmc.core.util.Text
import net.kyori.adventure.bossbar.BossBar
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.FishHook
import org.bukkit.entity.Player
import java.util.Random
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToLong

/**
 * 입질에서 지급까지의 **진행**을 맡는다.
 *
 * [CatchService] 가 "무엇을 주는가"라면 여기는 "언제 어떻게 주는가"다. 리스너는 Bukkit
 * 이벤트를 이 클래스의 낱말(`bite` · `click` · `cancel`)로 옮기기만 한다 — 그래서 진행
 * 규칙이 이벤트 핸들러 여섯 곳에 흩어지지 않는다.
 *
 * **판 하나는 셋 중 하나로만 끝난다** — 지급, 도망, 취소. 어느 쪽이든 [finish] 를 지나므로
 * 낚싯바늘을 치우고 세션을 비우는 일이 한 곳에 있다. 2세대는 이 정리가 경로마다 따로 있어서,
 * 줄이 끊어진 판만 낚싯바늘이 남았다.
 */
class FishingFlow(private val fishing: Fishing) {

    private val random = Random()

    // --- 입질 -----------------------------------------------------------------------

    /**
     * 입질이 왔다. 추첨해서 판을 연다.
     *
     * **추첨을 여기서 한다.** 미니게임 난이도가 등급에서 오므로 등급을 모르면 판을 만들 수
     * 없다. 그리고 미니게임은 이미 정해진 결과를 받을지 말지를 정하는 것이지, 결과를 바꾸는
     * 것이 아니다 — 그래야 "클릭을 잘하면 S가 나온다"는 오해가 생기지 않는다.
     *
     * @return 판을 열었으면 true. 부르는 쪽이 바닐라 낚시를 막아야 한다.
     */
    fun bite(player: Player, angler: Angler, hook: FishHook?, now: Long): Boolean {
        if (angler.isBusy) return false

        val rod = fishing.catches.rodOf(player)
        if (rod == null && !fishing.config.allowUnregisteredRod) return false

        val bait = fishing.catches.baitOf(player)
        val auto = !angler.minigameEnabled && !angler.fatigue.locked

        val result = fishing.catches.roll(player, rod, bait) ?: return false
        // 자동 낚시로 나오면 안 되는 등급은 **다시 뽑지 않고 버린다.** 다시 뽑으면 허용 등급이
        // 나올 때까지 돌게 되어, 자동 낚시가 확률을 무시하고 허용 등급을 균등하게 뱉는다.
        if (auto && !fishing.catches.isAutoAllowed(result)) return false

        consumeBait(player, bait != null && !fishing.catches.saves(rod?.baitSaveChance ?: 0.0))

        // 관리자 테스트 모드(2세대 testfightmode): 미니게임을 건너뛰고 곧바로 힘겨루기. 보상은 정상이다.
        if (angler.testFight && !auto) {
            startFight(player, angler, result, hook, now, practice = false)
            return true
        }

        angler.bite = if (auto) {
            val delay = (fishing.config.autoCatchDelayFor(result.grade.id) - (rod?.autoCatchReduce ?: 0.0)).coerceAtLeast(0.0)
            BiteSession(result, null, hook, now, autoCatchAt = now + (delay * 1000).toLong())
        } else {
            val game = ClickGame.generate(
                count = result.grade.inputCount,
                timeLimitMillis = ((result.grade.timeSeconds + (rod?.minigameSeconds ?: 0.0)) * 1000L).toLong(),
                startedAt = now,
                rolls = { random.nextDouble() },
            )
            fishing.messages.send(player, "bite")
            player.playSound(player.location, Sound.ENTITY_FISHING_BOBBER_SPLASH, 1f, 1.4f)
            BiteSession(result, game, hook, now)
        }
        return true
    }

    /** 미끼는 입질마다 한 개 쓴다. 낚을 때가 아니라 **입질할 때**다 — 놓쳐도 소모된다. */
    private fun consumeBait(player: Player, had: Boolean) {
        if (!had) return
        val offhand = player.inventory.itemInOffHand
        if (offhand.type.isAir) return
        offhand.amount = offhand.amount - 1
    }

    // --- 클릭 -----------------------------------------------------------------------

    /**
     * 좌/우클릭 하나를 넣는다.
     *
     * @return 이 클릭을 우리가 먹었으면 true. 부르는 쪽이 이벤트를 취소해야 한다 — 안 그러면
     *         같은 클릭이 낚싯대를 감아올린다.
     */
    fun click(player: Player, angler: Angler, left: Boolean, now: Long): Boolean {
        // 시작 연출 중의 클릭은 먹기만 한다. 안 먹으면 낚싯대가 감겨 판이 끝난다.
        if (angler.intro != null) return true

        angler.fight?.let { session ->
            if (left) session.fight.registerReel(now) else session.fight.registerRelease(now)
            return true
        }

        val bite = angler.bite ?: return false
        val game = bite.game
        if (game == null) {
            // 자동 낚시 중 클릭은 아무것도 하지 않는다 — 단, 웅크린 우클릭은 직접 회수다.
            // 자동은 찌를 계속 물에 두므로(피로도 최소까지), 걷을 방법이 따로 있어야 한다.
            if (player.isSneaking && !left) {
                finish(player, angler, bite.hook)
                fishing.messages.send(player, "manual-reel")
            }
            return true
        }

        when (game.press(if (left) Click.LEFT else Click.RIGHT, now)) {
            Progress.HIT -> {
                player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1.2f)
                showGame(player, bite, game, now)
            }

            Progress.DONE -> {
                player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.6f)
                resultTitle(player, "title-success", Ph.of().sequence(game.sequence.joinToString(" ") { fishing.config.clickDisplay.letter(it) }))
                succeed(player, angler, bite.result, auto = false)
            }

            Progress.MISS -> {
                player.playSound(player.location, Sound.ENTITY_ITEM_BREAK, 1f, 0.8f)
                resultTitle(player, "title-fail", Ph.of())
                escape(player, angler)
            }

            Progress.OVER -> Unit
        }
        return true
    }

    /**
     * 힘겨루기의 W(감기)·S(풀기). 누른 순간은 클릭 하나와 같고, 누르고 있는 동안은 [tickFight] 가 `hold-repeat-millis` 마다
     * 한 번 더 누른 것으로 쳐서 연타처럼 콤보가 쌓인다([FightKeys.hold]). 둘 다 누르면 아무 쪽도 아니다.
     * 힘겨루기 중에는 제자리에 묶여 있어(`FightMovement`) 눌러도 움직이지 않는다.
     */
    fun keys(angler: Angler, forward: Boolean, backward: Boolean, now: Long) {
        val session = angler.fight ?: return
        // W 를 쥔 채 S 를 눌렀다 떼도 다시 감긴다(FightKeys) — 손가락을 굴려 방향을 바꿀 수 있게.
        when (FightKeys.pressed(session.forward, session.backward, forward, backward)) {
            FightKeys.Press.REEL -> {
                session.fight.registerReel(now)
                session.keyPressedAt = now
            }
            FightKeys.Press.RELEASE -> {
                session.fight.registerRelease(now)
                session.keyPressedAt = now
            }
            null -> Unit
        }
        session.forward = forward
        session.backward = backward
    }

    // --- 틱 -------------------------------------------------------------------------

    /** 1틱마다. 미니게임 만료, 자동 낚시 완료, 힘겨루기 진행을 한 자리에서 본다. */
    fun tick(player: Player, angler: Angler, now: Long) {
        angler.intro?.let {
            tickIntro(player, angler, it, now)
            return
        }
        angler.fight?.let {
            tickFight(player, angler, it, now)
            return
        }

        val bite = angler.bite ?: return

        if (bite.isAuto) {
            if (bite.isAutoDue(now)) {
                succeed(player, angler, bite.result, auto = true)
                return
            }
            // 2세대 auto-catch 액션바: 남은 초 · 등급 · 물고기 · 피로도. 초가 바뀔 때만 보낸다.
            val seconds = ((bite.autoCatchAt - now + 999L) / 1000L).coerceAtLeast(1L)
            if (seconds != bite.shownSecond) {
                bite.shownSecond = seconds
                player.sendActionBar(
                    fishing.messages.component(
                        "auto-countdown",
                        Ph.of().seconds(seconds).grade(bite.result.grade.tag())
                            .fish(bite.result.fish.label()).count(angler.fatigue.value),
                    ),
                )
            }
            return
        }

        val game = bite.game ?: return
        if (game.expireIfDue(now)) {
            resultTitle(player, "title-timeout", Ph.of())
            escape(player, angler)
            return
        }
        showGame(player, bite, game, now)
    }

    /**
     * 미니게임 타이틀 — 2세대 `TimeBarMiniGame`: 가운데에 순서(맞힌 것 회색 · 지금 누를 것 굵은 금색 · 남은 것 흰색), 아래에
     * 남은 시간 막대 10칸과 초. **바뀔 때만 보낸다** — 초가 매초 바뀌므로 머무는 시간(1.2초) 안에 늘 다시 온다.
     */
    private fun showGame(player: Player, bite: BiteSession, game: ClickGame, now: Long) {
        val ratio = game.timeLeftRatio(now)
        val filled = (ratio * GAME_BARS).roundToLong().toInt().coerceIn(0, GAME_BARS)
        val seconds = ceil(ratio * game.timeLimitMillis / 1000.0).toLong()
        val sub = "<green>" + "|".repeat(filled) + "</green><gray>" + "|".repeat(GAME_BARS - filled) + "</gray> <yellow>" + seconds + "s</yellow>"
        // 글자는 조각마다 따로 그린다 — 색에 닫는 태그가 없어도(`&6&l` · `<gold><bold>`) 뒤 글자로 번지지 않는다.
        val pieces = game.pieces(fishing.config.clickDisplay)
        val shown = pieces.joinToString(" ") + "\n" + sub
        if (shown == bite.shownTitle) return
        bite.shownTitle = shown
        player.showTitle(
            net.kyori.adventure.title.Title.title(
                net.kyori.adventure.text.Component.join(
                    net.kyori.adventure.text.JoinConfiguration.separator(net.kyori.adventure.text.Component.space()),
                    pieces.map { Text.render(it) },
                ),
                Text.render(sub),
                net.kyori.adventure.title.Title.Times.times(java.time.Duration.ZERO, java.time.Duration.ofMillis(1200), java.time.Duration.ZERO),
            ),
        )
    }

    // --- 힘겨루기 -------------------------------------------------------------------

    /**
     * 시작 연출을 거쳐 힘겨루기로 넘어간다(2세대와 같다). 연출을 끄면 곧바로 시작한다.
     *
     * 연출은 스케줄러 작업이 아니라 [IntroSession] 이다 — 힘겨루기 틱이 시각을 보고 단계를 넘기므로,
     * 접속 종료나 리로드로 판을 접으면 [finish] 한 번에 같이 사라진다.
     */
    private fun beginFight(player: Player, angler: Angler, result: Catch, hook: FishHook?, now: Long, practice: Boolean) {
        if (!fishing.fight.intro.enabled) {
            startFight(player, angler, result, hook, now, practice)
            return
        }
        angler.bite = null
        angler.intro = IntroSession(result, hook, practice, now)
        tickIntro(player, angler, angler.intro!!, now)
    }

    private fun tickIntro(player: Player, angler: Angler, intro: IntroSession, now: Long) {
        val config = fishing.fight.intro
        val step = ((now - intro.startedAt) / config.stepMillis).toInt().coerceAtMost(INTRO_START_STEP)
        if (step <= intro.shownStep) return
        intro.shownStep = step

        val stepTicks = (config.stepMillis / 50L).coerceAtLeast(1L)
        when (step) {
            0 -> {
                title(player, config.announceTitle, config.announceSubtitle, 5, stepTicks, 5)
                sound(player, config.announceSound)
            }
            INTRO_START_STEP -> {
                title(player, config.startTitle, "", 3, 12, 5)
                sound(player, config.startSound)
                startFight(player, angler, intro.result, intro.hook, now, intro.practice)
            }
            else -> {
                // 1 → "3", 2 → "2", 3 → "1"
                title(player, "&6&l" + (INTRO_START_STEP - step), "", 2, (stepTicks - 4).coerceAtLeast(2L), 2)
                sound(player, config.countdownSound)
            }
        }
    }

    /**
     * 힘겨루기를 연다 — 2세대 `startFight`/`initStats`. 난이도는 **등급 배수 × 레어 트로피 배수**, 행동력은 등급별(레어는 배),
     * 낚싯대 수치는 기본값에 더한다. 설정은 시작 때의 것을 끝까지 든다.
     */
    private fun startFight(player: Player, angler: Angler, result: Catch, hook: FishHook?, now: Long, practice: Boolean) {
        val rod = fishing.catches.rodOf(player)
        val settings = fishing.fight

        angler.bite = null
        angler.intro = null
        val session = FightSession(
            result = result,
            fight = Fight(
                settings = settings,
                gradeId = result.grade.id.lowercase(),
                rare = result.trophy == Trophy.RARE,
                rodReelPower = rod?.reelPower ?: 0.0,
                rodLineStrength = rod?.lineStrength ?: 0.0,
                rodReelDurability = rod?.reelDurability ?: 0.0,
                startedAt = now,
            ),
            hook = hook,
            practice = practice,
        )
        // 판이 열리기 전부터 누르고 있던 키는 "누른 순간" 이 아니다 — 한 번 떼고 눌러야 감긴다.
        player.currentInput.let { session.forward = it.isForward; session.backward = it.isBackward }
        angler.fight = session
        // betterhud 면 보스바를 만들지 않는다 — BetterHud 가 %inmcfishing_fight_*% 로 그린다.
        if (!settings.hud.betterHud) updateBossBar(player, session)
        fishing.movement.restrict(player)
    }

    /** 2세대 `TrophyFightManager.tick` 의 한 사람 몫. 계산은 [Fight.tick], 여기는 화면·소리·끝내기. */
    private fun tickFight(player: Player, angler: Angler, session: FightSession, now: Long) {
        val fight = session.fight
        val settings = fight.settings
        if (session.forward != session.backward) {
            session.keyPressedAt = FightKeys.hold(fight, session.forward, session.keyPressedAt, now, settings.input.holdRepeatMillis)
        }
        val exhaustedNow = fight.tick(now)
        session.ticks++

        if (exhaustedNow) sound(player, settings.sound.fishExhausted)
        // 상태 타이틀은 매 틱 다시 보낸다 — 한 번만 보내면 긴 상태(휴식 등)에서 상태가 안 바뀌었는데 타이틀이 먼저 꺼진다.
        // betterhud 면 보내지 않는다 — BetterHud 가 같은 상태를 가운데에 그려 둘이 겹쳐 나왔다(사용자 2026-10-01). 입질 미니게임의
        // 타이틀(showGame, 2026-09-27 결정)은 그대로다.
        if (!settings.hud.betterHud) {
            stateTitle(player, fight)
            updateBossBar(player, session)
            actionBar(player, fight)
        }
        if (session.ticks % settings.general.particleInterval == 0) particles(player, fight.state)
        if (session.ticks % settings.sound.interval == 0) sound(player, settings.sound.of(fight.state))

        val outcome = fight.outcome
        if (!outcome.isOver) return

        // 바로 아래에서 판을 먼저 비우므로 finish 는 이 보스바를 못 찾는다. 여기서 치운다.
        session.bossBar?.let { player.hideBossBar(it) }
        angler.fight = null
        fishing.movement.release(player)
        if (outcome.isWin) {
            // 판은 이미 비웠으므로 낚싯바늘을 직접 넘긴다. 세션에서 다시 찾으면 null 이라
            // 바늘이 물에 남고, 다음 우클릭이 감아올리기가 된다.
            succeed(player, angler, session.result, auto = false, skipFight = true, hook = session.hook)
            if (session.practice && !session.result.trophy.isTrophy) {
                // 연습 판(일반 물고기): 난이도는 물고기 등급 그대로라 트로피보다 낮다.
                // 크기 굴림으로 트로피·레어가 나오면 본래 난이도 그대로였으므로 별도 표시 없이 끝난다.
                fishing.messages.send(player, "fight-practice-win")
            }
            sound(player, settings.sound.success)
            return
        }

        finish(player, angler, session.hook)
        fishing.messages.send(player, messageFor(outcome))
        failSound(player)
    }

    /** 2세대 `messages.yml` 의 `fail.*` — 실패 원인마다 다른 한 줄. */
    private fun messageFor(outcome: FightOutcome): String = when (outcome) {
        FightOutcome.LINE_SNAPPED -> "fight-line-snapped"
        FightOutcome.REEL_BROKEN -> "fight-reel-broken"
        FightOutcome.DISTANCE_EXCEEDED -> "fight-distance"
        FightOutcome.TIMEOUT -> "fight-timeout"
        else -> "escaped"
    }

    /** 2세대 `sounds.fail`. */
    private fun failSound(player: Player) = player.playSound(player.location, Sound.ENTITY_ITEM_BREAK, 1f, 1f)

    /**
     * 상태 타이틀 — 가운데 크게 상태, 아래에 할 일(`hud.state-guide`). 2세대 `FightHUD.updateStateTitle`(0·4·3 틱).
     */
    private fun stateTitle(player: Player, fight: Fight) {
        val hud = fight.settings.hud
        val state = fight.state
        val guide = hud.guide(state)
        fun fill(raw: String) = raw
            .replace("{state_color}", hud.stateColor(state))
            .replace("{state}", hud.stateName(state))
            .replace("{stamina}", round0(fight.stamina))
            .replace("{reel}", round0(fight.reelState))
            .replace("{remaining_seconds}", round1(fight.ai.remainingTicks / 20.0))
        title(player, fill(guide.title), fill(guide.subtitle), 0, 4, 3)
    }

    /** 게이지는 장력, 색은 장력 위험도, 제목은 거리 숫자 — 2세대 `FightHUD.updateBossBar`. */
    private fun updateBossBar(player: Player, session: FightSession) {
        val fight = session.fight
        val hud = fight.settings.hud
        val ratio = (fight.tension / fight.lineStrength.coerceAtLeast(1.0)).coerceAtMost(1.0)
        val (color, prefix) = when {
            ratio >= hud.tensionDangerRatio -> BossBar.Color.RED to hud.barColorDanger
            ratio >= hud.tensionWarningRatio -> BossBar.Color.YELLOW to hud.barColorWarning
            else -> BossBar.Color.GREEN to hud.barColorSafe
        }
        val title = Text.render(
            prefix + hud.bossBarTitleFormat
                .replace("{distance}", round0(fight.distance))
                .replace("{max_distance}", if (fight.maxDistance > 0) round0(fight.maxDistance) else "-"),
        )
        val bar = session.bossBar
            ?: BossBar.bossBar(title, 0f, color, BossBar.Overlay.PROGRESS).also { session.bossBar = it; player.showBossBar(it) }
        bar.name(title)
        bar.progress(ratio.toFloat().coerceIn(0f, 1f))
        bar.color(color)
    }

    /** 2세대 `FightHUD.updateActionBar` — `hud.actionbar-format`(+ 켜 두면 행동력). */
    private fun actionBar(player: Player, fight: Fight) {
        val hud = fight.settings.hud
        var message = hud.actionBarFormat
            .replace("{stamina}", round0(fight.stamina))
            .replace("{power}", round0(fight.power))
            .replace("{resistance}", round0(fight.resistance))
            .replace("{reel}", round0(fight.reelState))
            .replace("{distance}", round0(fight.distance))
            .replace("{max_distance}", if (fight.maxDistance > 0) round0(fight.maxDistance) else "-")
        if (hud.showActionPower) {
            message += hud.actionPowerFormat
                .replace("{action_power}", fight.ai.actionPower.toString())
                .replace("{max_action_power}", fight.ai.maxActionPower.toString())
        }
        player.sendActionBar(Text.render(message))
    }

    /** 상태마다 물보라·거품(2세대 `spawnParticles`). */
    private fun particles(player: Player, state: FishState) {
        val at = player.location.add(0.0, 1.0, 0.0)
        val world = player.world
        when (state) {
            FishState.CHARGE -> world.spawnParticle(Particle.SPLASH, at, 10, 0.5, 0.5, 0.5, 0.1)
            FishState.FINAL_STRUGGLE -> {
                world.spawnParticle(Particle.SPLASH, at, 20, 0.5, 0.5, 0.5, 0.2)
                world.spawnParticle(Particle.CRIT, at, 10, 0.5, 0.5, 0.5, 0.1)
            }
            FishState.REST -> world.spawnParticle(Particle.BUBBLE, at, 3, 0.3, 0.3, 0.3, 0.05)
            else -> world.spawnParticle(Particle.BUBBLE, at, 5, 0.3, 0.3, 0.3, 0.05)
        }
    }

    private fun bar(ratio: Double, on: String, off: String, width: Int = 8): String {
        val filled = (ratio.coerceIn(0.0, 1.0) * width).toInt()
        return on + "|".repeat(filled) + off + "|".repeat(width - filled)
    }

    private fun round0(value: Double): String = value.roundToLong().toString()

    /** 2세대 `round1` — 로케일 무관 소수 한 자리. */
    private fun round1(value: Double): String {
        val scaled = (value * 10.0).roundToLong()
        return (scaled / 10).toString() + "." + abs(scaled % 10)
    }

    // --- 마무리 ---------------------------------------------------------------------

    private fun succeed(
        player: Player,
        angler: Angler,
        result: Catch,
        auto: Boolean,
        skipFight: Boolean = false,
        hook: FishHook? = angler.bite?.hook ?: angler.fight?.hook,
    ) {
        // 트로피는 힘겨루기로 넘어간다. 자동 낚시는 예외다 — 아무도 안 보는 판을 열 수 없다.
        // 연습 모드면 트로피가 아니어도 넘어가고, 이기면 보상을 준다. 난이도는 물고기 등급
        // 그대로(일반 < 트로피 < 레어 ×1.5)라 연습은 일반 물고기로만 치러진다.
        val practice = angler.practice
        if (!skipFight && !auto && fishing.fight.general.enabled && (result.trophy.isTrophy || practice)) {
            beginFight(player, angler, result, hook, System.currentTimeMillis(), practice)
            return
        }

        if (!auto) {
            finish(player, angler, hook)
            fishing.catches.grant(player, angler, result, auto)
            return
        }

        // 자동 낚시는 찌를 거두지 않고 바로 다음 판을 연다. 걷는 조건은 둘뿐이다 —
        // 직접 걷기(웅크린 우클릭, 위 click())와 피로도 최소치. grant 가 피로도를 깎은 뒤라
        // 잠김은 grant 뒤에 본다(잠김 메시지는 grant 가 이미 보냈다).
        // finish 를 쓰면 찌가 사라지므로 세션만 비운다. 추첨 실패·등급 제한이면 판 없이
        // 찌만 남고, 다음 바닐라 입질이 다시 연다.
        angler.clearSessions()
        fishing.catches.grant(player, angler, result, auto)
        if (angler.fatigue.locked || hook == null || !hook.isValid) {
            finish(player, angler, hook)
            return
        }
        bite(player, angler, hook, System.currentTimeMillis())
    }

    private fun escape(player: Player, angler: Angler) {
        val hook = angler.bite?.hook
        finish(player, angler, hook)
        fishing.messages.send(player, "escaped")
    }

    /**
     * 판을 접는다. **지급이든 도망이든 취소든 전부 여기를 지난다.**
     *
     * 낚싯바늘을 치우는 것이 핵심이다. 남겨두면 다음 우클릭이 "감아올리기"가 되어 다음 판이
     * 시작되지 않고, 플레이어는 낚싯대가 고장 났다고 여긴다.
     */
    fun finish(player: Player, angler: Angler, hook: FishHook?) {
        angler.fight?.bossBar?.let { player.hideBossBar(it) }
        angler.clearSessions()
        // 힘겨루기로 묶었던 사람만 풀린다(기억도 PDC 표시도 없으면 아무것도 안 한다).
        fishing.movement.release(player)
        if (hook != null) {
            hook.takeIf { it.isValid }?.remove()
            if (angler.waitingHook === hook) angler.waitingHook = null
        }
        player.sendActionBar(Text.render(""))
    }

    /** 접속 종료·리로드. 조용히 접는다. */
    fun cancel(player: Player, angler: Angler) {
        val hook = angler.bite?.hook ?: angler.intro?.hook ?: angler.fight?.hook
        // 미니게임 타이틀은 1.2초 머문다. 결과 타이틀이 덮지 않는 이 길에서는 직접 지운다.
        if (angler.bite?.game != null) player.clearTitle()
        finish(player, angler, hook)
    }

    // --- 기다리는 중 -----------------------------------------------------------------

    /**
     * 찌가 물에 닿았다(BOBBING) — 2세대 `startCastStatus`. 기다리는 찌로 들고 곧바로 한 번 보인다. 그 뒤로는
     * 1초 티커가 [waiting] 으로 이어 보낸다. **던지는 순간이 아니다** — 날아가는 동안이나 땅에 박힌 찌에는 안 뜬다.
     */
    fun startWaiting(player: Player, angler: Angler, hook: FishHook) {
        angler.waitingHook = hook
        if (!angler.isBusy) sendWaiting(player, angler)
    }

    /** 감아올렸거나 찌가 물을 벗어났다 — 2세대 `stopCastStatus`. 떠 있던 액션바도 바로 지운다. */
    fun stopWaiting(player: Player, angler: Angler) {
        if (angler.waitingHook == null) return
        angler.waitingHook = null
        if (!angler.isBusy) player.sendActionBar(Text.render(""))
    }

    /** 1초마다(티커). 찌가 사라졌거나 물에 떠 있지 않으면 놓는다. 판이 도는 동안은 쉰다 — 미니게임·힘겨루기 화면과 겹치지 않게. */
    fun waiting(player: Player, angler: Angler) {
        val hook = angler.waitingHook ?: return
        if (!hook.isValid || hook.state != FishHook.HookState.BOBBING) {
            angler.waitingHook = null
            return
        }
        if (!angler.isBusy) sendWaiting(player, angler)
    }

    /** 2세대 cast-status `ready`(+ 자동 낚시면 `minigame-off`): 무엇으로 낚고 있는지, 자동 낚시면 피로도까지. 문구를 비우면 안 뜬다. */
    private fun sendWaiting(player: Player, angler: Angler) {
        val raw = fishing.messages.raw("waiting")
        if (raw.isBlank()) return
        val rod = fishing.catches.rodOf(player)
        val rodName = rod?.let { it.decor.name.ifBlank { it.item.label() }.ifBlank { it.id } } ?: "낚싯대"
        var status = Text.render(raw, Ph.of().fish(rodName))
        if (!angler.minigameEnabled) status = status.append(Text.render(fishing.messages.raw("waiting-auto"), Ph.of().count(angler.fatigue.value)))
        player.sendActionBar(status)
    }

    /**
     * 월드 이동·순간이동·죽음·아이템 버리기·단축바 바꾸기 — 2세대 `cleanupPlayer`. 미니게임과 시작 연출은 조용히 접고,
     * 진행 중인 힘겨루기는 "도망갔다" 로 끝낸다(2세대 `stopFight(CANCELLED)` → `fail.fail`).
     */
    fun abort(player: Player, angler: Angler) {
        val fighting = angler.fight != null
        cancel(player, angler)
        if (fighting) {
            fishing.messages.send(player, "escaped")
            failSound(player)
        }
    }

    /**
     * 관리자 시험 판(2세대 `/fishing testfight <등급> <trophy|rare>`). 그 등급의 첫 물고기를 평균 크기로
     * 걸어 곧바로 힘겨루기를 연다. 손에 든 낚싯대 보너스가 그대로 반영되고, **이기면 보상도 정상이다.**
     *
     * @return 열었으면 true. 이미 판이 도는 중이거나 그 등급에 물고기가 없으면 false.
     */
    fun testFight(player: Player, angler: Angler, gradeId: String, rare: Boolean): Boolean {
        if (angler.isBusy) return false
        val grade = fishing.grades[gradeId] ?: return false
        val fish = fishing.fish.ofGrade(grade.id).firstOrNull() ?: return false
        val result = Catch(
            fish = fish,
            grade = grade,
            bigFish = false,
            double = false,
            size = fish.size.avg,
            trophy = if (rare) Trophy.RARE else Trophy.NORMAL,
        )
        startFight(player, angler, result, null, System.currentTimeMillis(), practice = false)
        return true
    }

    // --- 화면 -----------------------------------------------------------------------

    /**
     * 미니게임 결과 타이틀 — 2세대 `titles`. 문구가 비어 있으면 띄우지 않는다.
     * 부제는 성공이면 `title-success-sub`(`{순서}`), 실패·시간 초과면 `title-fail-sub`. 둘 다 `{left}`·`{right}`(설정한 좌·우 글자)를 쓸 수 있다.
     */
    private fun resultTitle(player: Player, key: String, ph: Ph) {
        val sub = if (key == "title-success") "title-success-sub" else "title-fail-sub"
        val times = fishing.config.resultTitles
        val main = fishing.messages.raw(key)
        val subtitle = fishing.messages.raw(sub)
        if (main.isEmpty() && subtitle.isEmpty()) return
        val display = fishing.config.clickDisplay
        ph.letters(display.left, display.right)
        title(
            player,
            main,
            subtitle,
            (times.fadeInSeconds * 20).toLong(),
            (times.staySeconds * 20).toLong(),
            (times.fadeOutSeconds * 20).toLong(),
            ph,
        )
    }

    private fun title(player: Player, main: String, sub: String, fadeIn: Long, stay: Long, fadeOut: Long, ph: Ph? = null) {
        player.showTitle(
            net.kyori.adventure.title.Title.title(
                Text.render(main, ph),
                Text.render(sub, ph),
                net.kyori.adventure.title.Title.Times.times(
                    java.time.Duration.ofMillis(fadeIn * 50L),
                    java.time.Duration.ofMillis(stay * 50L),
                    java.time.Duration.ofMillis(fadeOut * 50L),
                ),
            ),
        )
    }

    /** 소리 키(`block.bell.use`). 비어 있으면 아무것도 안 한다. */
    private fun sound(player: Player, key: String) {
        if (key.isBlank()) return
        player.playSound(player.location, key, 1f, 1f)
    }

    private companion object {
        /** 연출의 마지막 단계. 0 등장 · 1~3 카운트다운 · 4 START. */
        const val INTRO_START_STEP = 4

        /** 미니게임 타이틀의 남은 시간 막대 칸 수(2세대와 같다). */
        const val GAME_BARS = 10
    }
}
