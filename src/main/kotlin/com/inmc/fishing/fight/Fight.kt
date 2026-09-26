package com.inmc.fishing.fight

import java.util.Random

/** 힘겨루기가 끝난 이유. 2세대 `FightState` + `FightFailReason`. */
enum class FightOutcome {
    RUNNING,
    SUCCESS,

    /** 장력이 줄 강도에 닿았다 — 2세대 `LINE_SNAPPED`. */
    LINE_SNAPPED,

    /** 거리가 최대 거리에 닿았다 — 2세대 `DISTANCE_EXCEEDED`("줄 길이가 부족하네.."). */
    DISTANCE_EXCEEDED,

    REEL_BROKEN,

    /** 제한 시간. 물고기를 한 번이라도 완전히 지치게 했으면 더는 안 센다. */
    TIMEOUT;

    val isOver: Boolean get() = this != RUNNING
    val isWin: Boolean get() = this == SUCCESS
}

/**
 * 힘겨루기 한 판 — 2세대 `FightSession` + `FightCalculator` + `TrophyFightManager.tick` 을 **그대로** 옮겼다.
 *
 * 순수 계산이다. Bukkit 도 플레이어도 모르고, 시각은 인자로 받는다(2세대는 `System.currentTimeMillis` 를 직접 불러
 * 서버 없이는 확인할 수 없었다). 2세대 원본 클래스로 뽑은 틱별 기록(`fight-golden.txt`)과 `FightGoldenTest` 가 대조한다 —
 * **수식·순서를 고치면 그 테스트가 먼저 알려 준다.**
 *
 * 입력은 "누르고 있음" 을 흉내 낸다: 클릭 하나가 [FightSettings.Input.reelGraceMillis] 동안 감는 중으로 남고, 같은 창 안의
 * 다음 클릭이 콤보를 올린다. 감기와 풀기는 서로를 끈다.
 */
class Fight(
    val settings: FightSettings,
    /** 등급 id(소문자) — 난이도·행동력·최소 거리가 등급마다 다르다. */
    gradeId: String,
    rare: Boolean,
    rodReelPower: Double = 0.0,
    rodLineStrength: Double = 0.0,
    rodReelDurability: Double = 0.0,
    val startedAt: Long,
    random: Random = Random(),
) {
    private val calc = settings.calc
    private val input = settings.input

    private val rawDifficulty: Double = (settings.stats.gradeDifficulty[gradeId] ?: 1.0) *
        (if (rare) calc.rareTrophyDifficultyMultiplier else 1.0)

    /** 등급 난이도 × 레어 트로피. 상태의 힘·저항에 매 틱 곱해진다(시작 수치는 자르기 전 값 — 2세대와 같다). */
    val difficulty: Double = rawDifficulty.coerceAtLeast(0.01)

    val maxStamina: Double = (settings.stats.defaultStamina * rawDifficulty).coerceAtLeast(0.0)
    var stamina: Double = maxStamina
        private set
    var power: Double = (settings.stats.defaultPower * rawDifficulty).coerceAtLeast(0.0)
        private set
    var resistance: Double = (settings.stats.defaultResistance * rawDifficulty).coerceAtLeast(0.0)
        private set

    // 낚싯대는 기본값에 **더한다**(2세대와 같다).
    val reelPower: Double = (settings.stats.defaultReelPower + rodReelPower.coerceAtLeast(0.0)).coerceAtLeast(0.0)
    val lineStrength: Double = (settings.stats.defaultLineStrength + rodLineStrength.coerceAtLeast(0.0)).coerceAtLeast(0.0)
    val reelDurability: Double = (settings.stats.defaultReelDurability + rodReelDurability.coerceAtLeast(0.0)).coerceAtLeast(0.0)

    val maxReelState: Double = settings.stats.defaultReelState.coerceAtLeast(0.0)
    var reelState: Double = maxReelState
        private set
    var distance: Double = settings.stats.defaultDistance.coerceAtLeast(0.0)
        private set
    var tension: Double = 0.0
        private set

    /** 줄이 튼튼할수록 더 멀리 가도 버틴다. */
    val maxDistance: Double = (settings.stats.maxDistance + rodLineStrength.coerceAtLeast(0.0) * settings.stats.distancePerLineStrength).coerceAtLeast(0.0)

    /** 체력이 남은 동안 이보다 가까이 끌어올 수 없다(등급별). */
    val minDistanceWithStamina: Double = (settings.stats.minDistanceWithStamina[gradeId] ?: 50.0).coerceAtLeast(0.0)

    val ai = FishAI(
        settings.ai,
        (settings.actionPower.gradeMax[gradeId] ?: 5) * (if (rare) calc.rareTrophyActionPowerMultiplier else 1),
        settings.actionPower.dangerousThresholdRatio,
        if (settings.lineTangle.enabled) settings.lineTangle.triggerChance else 0.0,
        random,
    )

    val state: FishState get() = ai.state

    // --- 입력 ---
    private var lastReelClickAt: Long? = null
    private var lastReleaseClickAt: Long? = null
    private var lastReelComboAt: Long? = null
    private var lastReleaseComboAt: Long? = null

    /** 좌클릭 연타 콤보. 우클릭이 오면 0. */
    var reelCombo: Int = 0
        private set

    /** 우클릭 연타 콤보 — 장력을 콤보 × 계수만큼 푼다. 좌클릭이 오면 0. */
    var releaseCombo: Int = 0
        private set

    fun isReeling(now: Long): Boolean = lastReelClickAt?.let { now - it <= input.reelGraceMillis } ?: false
    fun isReleasing(now: Long): Boolean = lastReleaseClickAt?.let { now - it <= input.releaseGraceMillis } ?: false

    fun registerReel(now: Long) {
        reelCombo = if (lastReelComboAt?.let { now - it <= input.reelComboWindowMillis } == true) minOf(input.maxReelCombo, reelCombo + 1) else 1
        lastReelComboAt = now
        lastReelClickAt = now
        lastReleaseClickAt = null
        releaseCombo = 0
    }

    fun registerRelease(now: Long) {
        releaseCombo = if (lastReleaseComboAt?.let { now - it <= input.releaseComboWindowMillis } == true) minOf(input.maxReleaseCombo, releaseCombo + 1) else 1
        lastReleaseComboAt = now
        lastReleaseClickAt = now
        lastReelClickAt = null
        reelCombo = 0
    }

    /**
     * W 를 누르고 있는 동안 매 틱 — 감는 중이면 "누르고 있음" 을 늘린다. 누른 순간은 [registerReel] 이 따로 받는다.
     * **콤보는 건드리지 않는다**(연타만 콤보를 올린다). 그 사이 우클릭이 풀기로 바꿨으면 아무것도 안 한다 — 가장 최근 입력이 이긴다.
     */
    fun holdReel(now: Long) {
        if (lastReelClickAt != null) lastReelClickAt = now
    }

    /** S 를 누르고 있는 동안 — [holdReel] 의 풀기 쪽. */
    fun holdRelease(now: Long) {
        if (lastReleaseClickAt != null) lastReleaseClickAt = now
    }

    // --- 진행 ---

    /**
     * 물고기를 처음 완전히 지치게 한 순간부터 제한 시간을 멈춘다. 체력이 다시 올라도 풀리지 않는다 — 한 번 지치게 했으면
     * 시간 압박 없이 마무리할 수 있어야 한다(2세대와 같다).
     */
    var timerPaused: Boolean = false
        private set

    var outcome: FightOutcome = FightOutcome.RUNNING
        private set

    /** 이유마다 따로 도는 실패 유예 시계 — 조건에 처음 닿은 시각. 조건에서 벗어나면 지운다. */
    private val graceSince = HashMap<FightOutcome, Long>()

    /** 한계에 닿아 유예 중인가. PlaceholderAPI 의 위험도 `3` 이 이것이다. */
    fun inGrace(reason: FightOutcome): Boolean = reason in graceSince

    fun staminaRatio(): Double = if (maxStamina > 0) stamina / maxStamina else 0.0

    /**
     * 한 틱(2세대 `TrophyFightManager.tick` 의 1~8 과 11). 물고기를 이번 틱에 완전히 지치게 했으면 true 를 돌려준다
     * (2세대는 그 순간 `fish-exhausted` 소리를 냈다).
     */
    fun tick(now: Long): Boolean {
        if (outcome.isOver) return false
        val staminaRatio = staminaRatio()
        val reeling = isReeling(now)
        ai.tick(staminaRatio, reeling)
        val state = ai.state
        val stats = settings.ai.state(state)

        power = (ai.power * difficulty).coerceAtLeast(0.0)
        resistance = (ai.resistance * difficulty).coerceAtLeast(0.0)
        val releasing = isReleasing(now)

        // 체력 — 감으면 깎이고(상태 배수·릴 상태), 안 감으면 상태에 비례해 회복한다.
        if (reeling) {
            val reelRatio = if (maxReelState > 0) reelState / maxReelState else 0.0
            var decrease = if (reelPower <= 0) 0.0
            else reelPower * calc.staminaDecreasePerReelPower * reelRatio.coerceIn(0.0, 1.0) * stats.reelStaminaMultiplier
            decrease += calc.reelStaminaPerCombo * reelCombo.coerceAtLeast(0)
            stamina = (stamina - decrease).coerceAtLeast(0.0)
        } else {
            stamina = (stamina + maxStamina.coerceAtLeast(0.0) * stats.staminaRegenRatio).coerceIn(0.0, maxStamina)
        }

        // 거리 — 기본 릴 파워에 낚싯대 보너스는 비율로만 더한다(좋은 낚싯대가 거리를 압도하지 않게).
        val defaultReelPower = settings.stats.defaultReelPower
        val effectiveReelPower = defaultReelPower + (reelPower - defaultReelPower).coerceAtLeast(0.0) * calc.rodBonusDistanceRatio
        var distanceChange = if (releasing) {
            power * calc.fishEscapeCoefficient + calc.releaseBase * stats.releaseDistanceMultiplier
        } else {
            distanceChange(effectiveReelPower, staminaRatio, reeling, state, stats)
        }
        if (!releasing && reeling) distanceChange += -(calc.reelDistancePerCombo * reelCombo.coerceAtLeast(0))
        distance = capped((distance + distanceChange).coerceAtLeast(0.0), maxDistance)
        if (minDistanceWithStamina > 0 && stamina > 0 && distance < minDistanceWithStamina) {
            distance = capped(minDistanceWithStamina.coerceAtLeast(0.0), maxDistance)
        }

        // 장력 — 풀면 콤보만큼 떨어지고, 감으면 상태 상승률만큼, 가만히 있으면 기본 감소 + 상태 자동 상승.
        val tensionChange = when {
            releasing -> -(calc.releaseTensionPerCombo * releaseCombo.coerceAtLeast(1))
            reeling -> lineStrength.coerceAtLeast(1.0) * stats.tensionRate
            else -> calc.idleTensionBase + stats.autoTensionRate
        }
        tension = capped((tension + tensionChange).coerceAtLeast(0.0), lineStrength)

        // 릴 — 감으면 물고기의 힘·저항에 닳고, 풀면 빨리, 가만히 있으면 천천히 회복한다.
        val reelChange = when {
            reeling -> -(power + resistance) * calc.reelStateDecay * (calc.durabilitySoftening / (calc.durabilitySoftening + reelDurability.coerceAtLeast(0.0)))
            releasing -> maxReelState.coerceAtLeast(0.0) * calc.releaseReelRegenRatio
            else -> maxReelState.coerceAtLeast(0.0) * calc.reelStateIdleRegenRatio
        }
        reelState = (reelState + reelChange).coerceIn(0.0, maxReelState)

        var exhaustedNow = false
        if (stamina <= 0 && !timerPaused) {
            timerPaused = true
            exhaustedNow = true
        }
        outcome = judge(now)
        return exhaustedNow
    }

    /** 2세대 `calculateDistanceChange` — 감기와 가만히 있기. 기절한 물고기는 독립 계수로만 끌려온다. */
    private fun distanceChange(reelPower: Double, staminaRatio: Double, reeling: Boolean, state: FishState, stats: FightSettings.StateStats): Double {
        if (state == FishState.STUNNED) return if (reeling) -(reelPower * calc.stunnedPullCoefficient) else 0.0
        val fishEscape = power * calc.fishEscapeCoefficient
        if (!reeling) return fishEscape
        val resistanceFactor = calc.resistanceSoftening / (calc.resistanceSoftening + resistance.coerceAtLeast(0.0))
        val exhaustionFactor = 1.0 - staminaRatio.coerceIn(0.0, 1.0)
        val baseReel = reelPower * calc.baseReelCoefficient * resistanceFactor * stats.reelDistanceMultiplier
        val bonusReel = reelPower * calc.bonusReelCoefficient * exhaustionFactor * resistanceFactor
        return fishEscape - (baseReel + bonusReel)
    }

    /** 성공을 먼저 본다. 실패 셋은 이유마다 유예 시계가 따로 돈다. */
    private fun judge(now: Long): FightOutcome {
        if (distance <= 0 && stamina <= 0) return FightOutcome.SUCCESS
        val general = settings.general
        if (failing(FightOutcome.LINE_SNAPPED, tension >= lineStrength, general.lineSnappedGraceMillis, now)) return FightOutcome.LINE_SNAPPED
        if (failing(FightOutcome.DISTANCE_EXCEEDED, maxDistance > 0 && distance >= maxDistance, general.distanceExceededGraceMillis, now)) return FightOutcome.DISTANCE_EXCEEDED
        if (failing(FightOutcome.REEL_BROKEN, reelState <= 0, general.reelBrokenGraceMillis, now)) return FightOutcome.REEL_BROKEN
        if (!timerPaused) {
            val maxMs = general.maxTimeSeconds * 1000L
            if (maxMs > 0 && now - startedAt > maxMs) return FightOutcome.TIMEOUT
        }
        return FightOutcome.RUNNING
    }

    private fun failing(reason: FightOutcome, met: Boolean, graceMillis: Long, now: Long): Boolean {
        if (!met) {
            graceSince.remove(reason)
            return false
        }
        val since = graceSince.getOrPut(reason) { now }
        return now - since >= graceMillis
    }

    /** 상한이 0 이하면 "제한 없음". */
    private fun capped(value: Double, max: Double): Double = if (max > 0) minOf(max, value) else value
}
