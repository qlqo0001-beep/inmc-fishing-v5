package com.inmc.fishing.fight

import com.inmc.fishing.config.FightIntro
import org.bukkit.configuration.ConfigurationSection

/**
 * 힘겨루기 설정 전부 — `fight.yml` 의 `trophy-fight`. **2세대 `fight.yml` 과 키·구조가 같다** — 옛 파일을 그대로 넣어도 읽힌다.
 *
 * 기본값은 2세대 **배포 파일**의 값이다(2세대 코드의 기본값과 몇 개 다르다 — 서버에서 돈 것은 배포 파일이다).
 * 예외는 `hud.mode` 하나: 키가 없거나 모르는 값이면 `vanilla`(2세대 코드와 같다 — 오타로 화면이 통째로 사라지지 않게).
 */
data class FightSettings(
    val general: General = General(),
    val ai: Ai = Ai(),
    val actionPower: ActionPower = ActionPower(),
    val hud: Hud = Hud(),
    val sound: Sound = Sound(),
    val intro: FightIntro = FightIntro(),
    val calc: Calc = Calc(),
    val input: Input = Input(),
    val stats: Stats = Stats(),
    val lineTangle: LineTangle = LineTangle(),
) {

    data class General(
        val enabled: Boolean = true,
        val maxTimeSeconds: Int = 120,
        val particleInterval: Int = 2,
        val lineSnappedGraceMillis: Long = 300L,
        val distanceExceededGraceMillis: Long = 300L,
        val reelBrokenGraceMillis: Long = 300L,
    )

    /** 상태 하나의 수치(`ai.states.<상태>`). */
    data class StateStats(
        val power: Double,
        val resistance: Double,
        val durationMinTicks: Int,
        /** 미포함. min 과 같으면 고정 길이. */
        val durationMaxTicks: Int,
        /** 이 상태로 바뀔 때 드는 행동력. -1 = 전부. */
        val actionPowerCost: Int,
        val tensionRate: Double,
        val autoTensionRate: Double,
        val reelStaminaMultiplier: Double,
        val reelDistanceMultiplier: Double,
        val releaseDistanceMultiplier: Double,
        val staminaRegenRatio: Double,
    )

    /** 전이 확률표. 가중치를 누적 비율로 바꿔 두고 `r < 누적` 인 첫 상태를 고른다(2세대와 같다). */
    class TransitionTable(private val states: List<FishState>, private val thresholds: DoubleArray) {

        fun pick(r: Double): FishState {
            for (i in thresholds.indices) if (r < thresholds[i]) return states[i]
            return states.last()
        }

        val entries: List<Pair<FishState, Double>>
            get() = states.indices.map { i -> states[i] to (thresholds[i] - (if (i == 0) 0.0 else thresholds[i - 1])) }

        companion object {
            /** 비었거나 합이 0 이하면 null. */
            fun of(weights: List<Pair<FishState, Double>>): TransitionTable? {
                if (weights.isEmpty()) return null
                val total = weights.sumOf { it.second }
                if (total <= 0) return null
                var running = 0.0
                val thresholds = DoubleArray(weights.size) { i -> running += weights[i].second; running / total }
                return TransitionTable(weights.map { it.first }, thresholds)
            }

            fun of(vararg weights: Pair<FishState, Double>): TransitionTable = of(weights.toList())!!
        }
    }

    data class Ai(
        val states: Map<FishState, StateStats> = DEFAULT_STATES,
        val transitions: Map<String, TransitionTable> = DEFAULT_TRANSITIONS,
        val staminaLowThreshold: Double = 0.3,
        val staminaMidThreshold: Double = 0.5,
        val actionPowerCritical: Int = 1,
        val actionPowerLow: Int = 2,
        val actionPowerMid: Int = 3,
    ) {
        fun state(state: FishState): StateStats = states.getValue(state)
        fun transition(key: String): TransitionTable = transitions.getValue(key)
    }

    data class ActionPower(
        val gradeMax: Map<String, Int> = mapOf("f" to 5, "e" to 7, "d" to 9, "c" to 12, "b" to 15, "a" to 20, "s" to 30),
        val dangerousThresholdRatio: Double = 0.3,
    )

    data class Guide(val title: String, val subtitle: String)

    data class Hud(
        /** `mode: betterhud` — 보스바·액션바·상태 타이틀을 그리지 않는다. BetterHud 가 `%inmcfishing_fight_*%` 로 그린다. */
        val betterHud: Boolean = false,
        val barColorSafe: String = "&a",
        val barColorWarning: String = "&e",
        val barColorDanger: String = "&c",
        val tensionDangerRatio: Double = 0.75,
        val tensionWarningRatio: Double = 0.5,
        val distanceDangerPercent: Double = 80.0,
        val reelDangerPercent: Double = 30.0,
        val bossBarTitleFormat: String = "거리: {distance} / {max_distance} M",
        val actionBarFormat: String = "물고기 채력 {stamina}% | 릴 상태 {reel}% | 거리 {distance}/{max_distance}M",
        val showActionPower: Boolean = false,
        val actionPowerFormat: String = " &d행동력 &f{action_power}&7/&f{max_action_power}",
        val stateNames: Map<FishState, String> = emptyMap(),
        val stateColors: Map<FishState, String> = DEFAULT_COLORS,
        val stateGuides: Map<FishState, Guide> = DEFAULT_GUIDES,
    ) {
        fun stateName(state: FishState): String = stateNames[state] ?: state.displayName
        fun stateColor(state: FishState): String = stateColors[state] ?: "&f"
        fun guide(state: FishState): Guide = stateGuides[state] ?: Guide("", "")
    }

    data class Sound(
        val interval: Int = 12,
        val fishExhausted: String = "entity.player.levelup",
        val success: String = "entity.firework_rocket.twinkle",
        val state: Map<FishState, String> = DEFAULT_STATE_SOUNDS,
    ) {
        fun of(state: FishState): String = this.state[state] ?: ""
    }

    data class Calc(
        val releaseBase: Double = 0.05,
        val releaseTensionPerCombo: Double = 0.5,
        val releaseReelRegenRatio: Double = 0.01,
        val reelDistancePerCombo: Double = 0.001,
        val reelStaminaPerCombo: Double = 0.01,
        val fishEscapeCoefficient: Double = 0.002,
        val baseReelCoefficient: Double = 0.015,
        val bonusReelCoefficient: Double = 0.025,
        val stunnedPullCoefficient: Double = 0.015,
        val staminaDecreasePerReelPower: Double = 0.005,
        val reelStateDecay: Double = 0.02,
        val reelStateIdleRegenRatio: Double = 0.0015,
        val idleTensionBase: Double = -0.001,
        val resistanceSoftening: Double = 100.0,
        val durabilitySoftening: Double = 100.0,
        val rareTrophyDifficultyMultiplier: Double = 1.5,
        val rareTrophyActionPowerMultiplier: Int = 2,
        val rodBonusDistanceRatio: Double = 0.1,
    )

    /** 클릭이 "누르고 있는 중" 으로 인정되는 시간과 연타 콤보. */
    data class Input(
        val reelGraceMillis: Long = 300L,
        val releaseGraceMillis: Long = 300L,
        val releaseComboWindowMillis: Long = 300L,
        val maxReleaseCombo: Int = 10,
        val reelComboWindowMillis: Long = 300L,
        val maxReelCombo: Int = 10,
    )

    data class Stats(
        val defaultStamina: Double = 100.0,
        val defaultPower: Double = 50.0,
        val defaultResistance: Double = 50.0,
        val defaultDistance: Double = 100.0,
        val maxDistance: Double = 150.0,
        val distancePerLineStrength: Double = 0.5,
        val minDistanceWithStamina: Map<String, Double> =
            mapOf("f" to 50.0, "e" to 60.0, "d" to 70.0, "c" to 80.0, "b" to 90.0, "a" to 100.0, "s" to 120.0),
        val defaultLineStrength: Double = 100.0,
        val defaultReelState: Double = 100.0,
        val defaultReelPower: Double = 100.0,
        val defaultReelDurability: Double = 50.0,
        val gradeDifficulty: Map<String, Double> =
            mapOf("f" to 0.5, "e" to 0.7, "d" to 1.0, "c" to 1.3, "b" to 1.6, "a" to 2.0, "s" to 3.0),
    )

    data class LineTangle(val enabled: Boolean = false, val triggerChance: Double = 0.05)

    companion object {

        /** 2세대 배포 `fight.yml` 의 `ai.states`. */
        val DEFAULT_STATES: Map<FishState, StateStats> = mapOf(
            FishState.REST to StateStats(10.0, 10.0, 40, 80, 0, 0.005, 0.01, 1.0, 0.01, 0.05, 0.01),
            FishState.SLOW_MOVE to StateStats(20.0, 20.0, 30, 60, 1, 0.009, 0.05, 1.0, 0.01, 0.05, 0.0008),
            FishState.NORMAL_MOVE to StateStats(40.0, 40.0, 20, 40, 0, 0.012, 0.15, 1.0, 0.005, 0.5, 0.0004),
            FishState.TURN to StateStats(50.0, 50.0, 15, 25, 1, 0.03, 0.3, 1.0, 0.001, 3.0, 0.0002),
            FishState.CHARGE to StateStats(80.0, 70.0, 10, 25, 3, 0.07, 3.0, 1.5, 0.0001, 4.0, 0.0),
            FishState.FINAL_STRUGGLE to StateStats(100.0, 90.0, 10, 20, -1, 0.12, 6.0, 0.0, 0.0, 6.0, 0.0),
            FishState.DIVE to StateStats(60.0, 100.0, 8, 20, 1, 0.12, 3.0, 0.0, 0.001, 4.0, 0.0),
            FishState.EXHAUSTED to StateStats(5.0, 5.0, 40, 60, 0, 0.005, 0.0, 2.0, 0.005, 0.1, 0.0),
            FishState.CIRCLE to StateStats(40.0, 60.0, 40, 60, 2, 0.03, 0.5, 1.0, 0.05, 1.0, 0.0002),
            FishState.JUMP to StateStats(30.0, 30.0, 10, 16, 2, 0.07, 2.0, 0.3, 0.005, 2.0, 0.0),
            FishState.LINE_TANGLE to StateStats(50.0, 80.0, 30, 50, 0, 0.03, 1.0, 0.2, 0.2, 3.0, 0.0),
            FishState.STUNNED to StateStats(0.0, 0.0, 0, 0, 0, 0.0, 0.0, 1.0, 1.0, 0.5, 0.0),
        )

        private val S = FishState.SLOW_MOVE
        private val N = FishState.NORMAL_MOVE
        private val T = FishState.TURN
        private val C = FishState.CHARGE
        private val D = FishState.DIVE
        private val F = FishState.FINAL_STRUGGLE
        private val J = FishState.JUMP
        private val R = FishState.REST
        private val E = FishState.EXHAUSTED
        private val O = FishState.CIRCLE

        /** 2세대 배포 `fight.yml` 의 `ai.transitions`. 열쇠는 원본과 같다(`low-stamina.ap-critical` …). 항목 순서가 곧 구간 순서다. */
        val DEFAULT_TRANSITIONS: Map<String, TransitionTable> = linkedMapOf(
            "after-exhausted" to TransitionTable.of(S to 50.0, N to 25.0, C to 25.0),
            "dangerous" to TransitionTable.of(S to 15.0, N to 15.0, T to 20.0, C to 15.0, D to 15.0, F to 10.0, J to 10.0),
            "low-stamina.ap-critical" to TransitionTable.of(R to 50.0, N to 30.0, E to 20.0),
            "low-stamina.ap-low" to TransitionTable.of(R to 30.0, S to 20.0, N to 20.0, T to 15.0, O to 15.0),
            "low-stamina.default" to TransitionTable.of(R to 45.0, S to 25.0, N to 15.0, O to 15.0),
            "mid-stamina.ap-critical" to TransitionTable.of(S to 50.0, N to 30.0, E to 20.0),
            "mid-stamina.ap-low" to TransitionTable.of(S to 20.0, N to 20.0, T to 20.0, J to 15.0, O to 15.0, C to 10.0),
            "mid-stamina.default" to TransitionTable.of(S to 20.0, N to 20.0, T to 20.0, J to 10.0, O to 15.0, C to 15.0),
            "high-stamina.ap-low" to TransitionTable.of(N to 20.0, T to 30.0, O to 20.0, S to 15.0, C to 15.0),
            "high-stamina.ap-mid" to TransitionTable.of(N to 20.0, T to 15.0, C to 25.0, D to 10.0, F to 15.0, J to 15.0),
            "high-stamina.default" to TransitionTable.of(N to 20.0, T to 15.0, C to 25.0, D to 10.0, F to 15.0, J to 15.0),
        )

        val DEFAULT_COLORS: Map<FishState, String> = mapOf(
            FishState.REST to "&a", FishState.SLOW_MOVE to "&e", FishState.NORMAL_MOVE to "&f", FishState.TURN to "&e",
            FishState.CHARGE to "&6", FishState.FINAL_STRUGGLE to "&c", FishState.DIVE to "&3", FishState.EXHAUSTED to "&7",
            FishState.CIRCLE to "&8", FishState.JUMP to "&e", FishState.LINE_TANGLE to "&c", FishState.STUNNED to "&7",
        )

        val DEFAULT_GUIDES: Map<FishState, Guide> = mapOf(
            FishState.REST to Guide("&a🐟 휴식 ({remaining_seconds}초)", "&2좌클릭으로 감기! 지금이 기회"),
            FishState.SLOW_MOVE to Guide("&e🐟 천천히 이동 ({remaining_seconds}초)", "&6좌클릭 연타! 최대 회수"),
            FishState.NORMAL_MOVE to Guide("&f🐟 이동 ({remaining_seconds}초)", "&7천천히 감으며 장력 확인!"),
            FishState.TURN to Guide("&e🐟 방향 전환 ({remaining_seconds}초)", "&e장력 상승! 무리하지 마세요."),
            FishState.CHARGE to Guide("&6🐟 돌진! ({remaining_seconds}초)", "&6우클릭으로 줄을 풀어주세요!"),
            FishState.FINAL_STRUGGLE to Guide("&c🐟 최후의 발악! ({remaining_seconds}초)", "&c버티면서 줄 장력을 관리하세요!"),
            FishState.DIVE to Guide("&3🐟 잠수! ({remaining_seconds}초)", "&3우클릭 연타로 버티세요!"),
            FishState.EXHAUSTED to Guide("&7🐟 탈진! ({remaining_seconds}초)", "&2좌클릭 연타로 마무리하세요!"),
            FishState.CIRCLE to Guide("&8🐟 원형 유영 ({remaining_seconds}초)", "&7좌우 클릭을 번갈아 누르세요."),
            FishState.JUMP to Guide("&e🐟 점프! ({remaining_seconds}초)", "&7지금은 클릭을 멈추세요"),
            FishState.LINE_TANGLE to Guide("&c🐟 줄 엉킴!", "&c우클릭으로 줄을 풀어주세요!"),
            FishState.STUNNED to Guide("&7🐟 기절!", "&a거리가 0이 될 때까지 감으세요!"),
        )

        val DEFAULT_STATE_SOUNDS: Map<FishState, String> = mapOf(
            FishState.REST to "", FishState.SLOW_MOVE to "entity.fish.swim", FishState.NORMAL_MOVE to "entity.fish.swim",
            FishState.TURN to "entity.fish.swim", FishState.CHARGE to "entity.salmon.flop", FishState.FINAL_STRUGGLE to "entity.salmon.flop",
            FishState.DIVE to "entity.generic.splash", FishState.EXHAUSTED to "", FishState.CIRCLE to "entity.fish.swim",
            FishState.JUMP to "entity.generic.splash", FishState.LINE_TANGLE to "block.chain.hit", FishState.STUNNED to "",
        )

        /** 상태 이름을 적는 두 방식. `ai.states`·`state-names` 는 밑줄, `state-color`·`state-guide`·`sound.state` 는 붙임표(2세대와 같다). */
        private fun underscore(state: FishState) = state.name.lowercase()
        private fun dash(state: FishState) = underscore(state).replace('_', '-')

        /** @param root `fight.yml` 전체. `trophy-fight` 가 없으면 전부 기본값. */
        fun load(root: ConfigurationSection?): FightSettings {
            val c = root?.getConfigurationSection("trophy-fight") ?: return FightSettings()
            val d = FightSettings()
            fun dbl(path: String, def: Double) = c.getDouble(path, def)
            fun lng(path: String, def: Long) = c.getLong(path, def).coerceAtLeast(0L)
            fun grades(path: String, def: Map<String, Double>) = def.mapValues { (g, v) -> c.getDouble("$path.$g", v) }

            val general = General(
                enabled = c.getBoolean("enabled", d.general.enabled),
                maxTimeSeconds = c.getInt("max-time-seconds", d.general.maxTimeSeconds),
                particleInterval = c.getInt("particle-interval", d.general.particleInterval).coerceAtLeast(1),
                lineSnappedGraceMillis = lng("fail-grace.line-snapped-millis", d.general.lineSnappedGraceMillis),
                distanceExceededGraceMillis = lng("fail-grace.distance-exceeded-millis", d.general.distanceExceededGraceMillis),
                reelBrokenGraceMillis = lng("fail-grace.reel-broken-millis", d.general.reelBrokenGraceMillis),
            )

            val states = FishState.entries.associateWith { state ->
                val def = DEFAULT_STATES.getValue(state)
                val p = "ai.states.${underscore(state)}."
                StateStats(
                    dbl(p + "power", def.power), dbl(p + "resistance", def.resistance),
                    c.getInt(p + "duration-ticks.min", def.durationMinTicks), c.getInt(p + "duration-ticks.max", def.durationMaxTicks),
                    c.getInt(p + "action-power-cost", def.actionPowerCost),
                    dbl(p + "tension-rate", def.tensionRate), dbl(p + "auto-tension-rate", def.autoTensionRate),
                    dbl(p + "reel-stamina-multiplier", def.reelStaminaMultiplier), dbl(p + "reel-distance-multiplier", def.reelDistanceMultiplier),
                    dbl(p + "release-distance-multiplier", def.releaseDistanceMultiplier), dbl(p + "stamina-regen-ratio", def.staminaRegenRatio),
                )
            }
            val t = "ai.transitions."
            val ai = Ai(
                states = states,
                transitions = DEFAULT_TRANSITIONS.mapValues { (key, def) -> table(c.getList(t + key)) ?: def },
                staminaLowThreshold = dbl(t + "stamina-thresholds.low", d.ai.staminaLowThreshold),
                staminaMidThreshold = dbl(t + "stamina-thresholds.mid", d.ai.staminaMidThreshold),
                actionPowerCritical = c.getInt(t + "action-power-thresholds.critical", d.ai.actionPowerCritical),
                actionPowerLow = c.getInt(t + "action-power-thresholds.low", d.ai.actionPowerLow),
                actionPowerMid = c.getInt(t + "action-power-thresholds.mid", d.ai.actionPowerMid),
            )
            val actionPower = ActionPower(
                gradeMax = d.actionPower.gradeMax.mapValues { (g, v) -> c.getInt("ai.action-power.grade-max.$g", v) },
                dangerousThresholdRatio = dbl("ai.action-power.dangerous-threshold-ratio", d.actionPower.dangerousThresholdRatio).coerceIn(0.0, 1.0),
            )

            val h = d.hud
            val hud = Hud(
                betterHud = c.getString("hud.mode", "vanilla")!!.trim().equals("betterhud", ignoreCase = true),
                barColorSafe = c.getString("hud.bar-color-safe", h.barColorSafe)!!,
                barColorWarning = c.getString("hud.bar-color-warning", h.barColorWarning)!!,
                barColorDanger = c.getString("hud.bar-color-danger", h.barColorDanger)!!,
                tensionDangerRatio = dbl("hud.tension-danger-ratio", h.tensionDangerRatio).coerceIn(0.0, 1.0),
                tensionWarningRatio = dbl("hud.tension-warning-ratio", h.tensionWarningRatio).coerceIn(0.0, 1.0),
                distanceDangerPercent = dbl("hud.distance-danger-percent", h.distanceDangerPercent).coerceIn(0.0, 100.0),
                reelDangerPercent = dbl("hud.reel-danger-percent", h.reelDangerPercent).coerceIn(0.0, 100.0),
                bossBarTitleFormat = c.getString("hud.bossbar-title-format", h.bossBarTitleFormat)!!,
                actionBarFormat = c.getString("hud.actionbar-format", h.actionBarFormat)!!,
                showActionPower = c.getBoolean("hud.show-action-power", h.showActionPower),
                actionPowerFormat = c.getString("hud.action-power-format", h.actionPowerFormat)!!,
                stateNames = FishState.entries.mapNotNull { s -> c.getString("hud.state-names.${underscore(s)}")?.takeIf { it.isNotBlank() }?.let { s to it } }.toMap(),
                stateColors = FishState.entries.associateWith { s -> c.getString("hud.state-color.${dash(s)}", DEFAULT_COLORS[s])!! },
                stateGuides = FishState.entries.associateWith { s ->
                    val def = DEFAULT_GUIDES.getValue(s)
                    Guide(c.getString("hud.state-guide.${dash(s)}.title", def.title)!!, c.getString("hud.state-guide.${dash(s)}.subtitle", def.subtitle)!!)
                },
            )
            val sound = Sound(
                interval = c.getInt("sound.interval", d.sound.interval).coerceAtLeast(1),
                fishExhausted = c.getString("sound.fish-exhausted", d.sound.fishExhausted)!!,
                success = c.getString("sound.success", d.sound.success)!!,
                state = FishState.entries.associateWith { s -> c.getString("sound.state.${dash(s)}", DEFAULT_STATE_SOUNDS[s])!! },
            )

            val k = d.calc
            val calc = Calc(
                releaseBase = dbl("calc.release-base", k.releaseBase),
                releaseTensionPerCombo = dbl("calc.release-tension-per-combo", k.releaseTensionPerCombo),
                releaseReelRegenRatio = dbl("calc.release-reel-regen-ratio", k.releaseReelRegenRatio),
                reelDistancePerCombo = dbl("calc.reel-distance-per-combo", k.reelDistancePerCombo).coerceAtLeast(0.0),
                reelStaminaPerCombo = dbl("calc.reel-stamina-per-combo", k.reelStaminaPerCombo).coerceAtLeast(0.0),
                fishEscapeCoefficient = dbl("calc.fish-escape-coefficient", k.fishEscapeCoefficient),
                baseReelCoefficient = dbl("calc.base-reel-coefficient", k.baseReelCoefficient),
                bonusReelCoefficient = dbl("calc.bonus-reel-coefficient", k.bonusReelCoefficient),
                stunnedPullCoefficient = dbl("calc.stunned-pull-coefficient", k.stunnedPullCoefficient),
                staminaDecreasePerReelPower = dbl("calc.stamina-decrease-per-reel-power", k.staminaDecreasePerReelPower),
                reelStateDecay = dbl("calc.reel-state-decay", k.reelStateDecay),
                reelStateIdleRegenRatio = dbl("calc.reel-state-idle-regen-ratio", k.reelStateIdleRegenRatio),
                idleTensionBase = dbl("calc.idle-tension-base", k.idleTensionBase),
                resistanceSoftening = dbl("calc.resistance-softening", k.resistanceSoftening).coerceAtLeast(0.0001),
                durabilitySoftening = dbl("calc.durability-softening", k.durabilitySoftening).coerceAtLeast(0.0001),
                rareTrophyDifficultyMultiplier = dbl("calc.rare-trophy-difficulty-multiplier", k.rareTrophyDifficultyMultiplier),
                rareTrophyActionPowerMultiplier = c.getInt("calc.rare-trophy-action-power-multiplier", k.rareTrophyActionPowerMultiplier).coerceAtLeast(1),
                rodBonusDistanceRatio = dbl("calc.rod-bonus-distance-ratio", k.rodBonusDistanceRatio),
            )
            val i = d.input
            val input = Input(
                reelGraceMillis = lng("input.reel-grace-millis", i.reelGraceMillis),
                releaseGraceMillis = lng("input.release-grace-millis", i.releaseGraceMillis),
                releaseComboWindowMillis = lng("input.release-combo-window-millis", i.releaseComboWindowMillis),
                maxReleaseCombo = c.getInt("input.max-release-combo", i.maxReleaseCombo).coerceAtLeast(1),
                reelComboWindowMillis = lng("input.reel-combo-window-millis", i.reelComboWindowMillis),
                maxReelCombo = c.getInt("input.max-reel-combo", i.maxReelCombo).coerceAtLeast(1),
            )
            val s = d.stats
            val stats = Stats(
                defaultStamina = dbl("stats.default-stamina", s.defaultStamina),
                defaultPower = dbl("stats.default-power", s.defaultPower),
                defaultResistance = dbl("stats.default-resistance", s.defaultResistance),
                defaultDistance = dbl("stats.default-distance", s.defaultDistance),
                maxDistance = dbl("stats.max-distance", s.maxDistance),
                distancePerLineStrength = dbl("stats.distance-per-line-strength", s.distancePerLineStrength),
                minDistanceWithStamina = grades("stats.min-distance-with-stamina", s.minDistanceWithStamina),
                // 2세대의 옛 키 max-tension 도 읽는다.
                defaultLineStrength = dbl("stats.default-line-strength", dbl("stats.max-tension", s.defaultLineStrength)),
                defaultReelState = dbl("stats.default-reel-state", s.defaultReelState).coerceAtLeast(1.0),
                defaultReelPower = dbl("stats.default-reel-power", s.defaultReelPower),
                defaultReelDurability = dbl("stats.default-reel-durability", s.defaultReelDurability),
                gradeDifficulty = grades("stats.grade-difficulty", s.gradeDifficulty),
            )
            return FightSettings(
                general, ai, actionPower, hud, sound,
                FightIntro.load(c.getConfigurationSection("intro")),
                calc, input, stats,
                LineTangle(c.getBoolean("line-tangle.enabled", false), dbl("line-tangle.trigger-chance", 0.05).coerceIn(0.0, 1.0)),
            )
        }

        /** `- { state: rest, weight: 45 }` 목록. 모르는 상태 이름은 그 항목만 건너뛴다. 읽을 것이 없으면 null(기본 표를 쓴다). */
        private fun table(raw: List<*>?): TransitionTable? {
            if (raw.isNullOrEmpty()) return null
            val weights = raw.mapNotNull { item ->
                val map = item as? Map<*, *> ?: return@mapNotNull null
                val state = map["state"]?.toString()?.trim()?.uppercase() ?: return@mapNotNull null
                val weight = (map["weight"] as? Number)?.toDouble() ?: return@mapNotNull null
                runCatching { FishState.valueOf(state) }.getOrNull()?.let { it to weight }
            }
            return TransitionTable.of(weights)
        }
    }
}
