package com.inmc.fishing.fight

import java.util.Random

/**
 * 물고기의 마음 — 2세대 `FishAI` 그대로다(상태별 지속 시간 · 행동력 · 전이표 · 위험 판정 · 줄 엉킴 · 기절).
 *
 * **난수를 쓰는 순서까지 원본과 같다**(지속 시간 `nextInt`, 줄 엉킴 `nextDouble`, 전이 `nextDouble`). 그래서 같은 시드면
 * 2세대 클래스와 한 틱도 다르지 않다 — `FightGoldenTest` 가 2세대 원본으로 뽑은 기록과 대조한다.
 *
 * 균형의 뼈대 세 가지는 표가 아니라 코드다: 체력이 0 이면 [FishState.STUNNED] 로 가서 멈춘다 · [FishState.FINAL_STRUGGLE]
 * 다음은 반드시 [FishState.EXHAUSTED] · 탈진 다음은 `after-exhausted` 표.
 */
class FishAI(
    private val ai: FightSettings.Ai,
    maxActionPower: Int,
    /** 이 비율 이하의 체력에서 플레이어가 감고 있으면 "위험" — 모든 행동력을 걸고 저항한다. */
    private val dangerousThresholdRatio: Double,
    /** 전이 때 줄 엉킴이 날 확률(0 = 안 남). */
    private val lineTangleChance: Double,
    private val random: Random,
) {

    val maxActionPower: Int = maxActionPower.coerceAtLeast(1)
    var actionPower: Int = this.maxActionPower
        private set

    var state: FishState = FishState.NORMAL_MOVE
        private set
    /** 이 상태의 전체 길이(틱). */
    var stateDurationTicks: Int = randomDuration(state)
        private set
    private var elapsedTicks: Int = 0

    /** 지금 상태가 끝나기까지 남은 틱. 상태 타이틀의 카운트다운. */
    val remainingTicks: Int get() = (stateDurationTicks - elapsedTicks).coerceAtLeast(0)

    val power: Double get() = ai.state(state).power
    val resistance: Double get() = ai.state(state).resistance

    /**
     * 한 틱. 체력이 0 이면 기절로 가서 영영 멈춘다. 지속 시간이 다하면 다음 상태로.
     *
     * @param staminaRatio 틱을 시작할 때의 체력 비율
     * @param reeling 플레이어가 지금 감고 있는가(위험 판정에 쓴다)
     */
    fun tick(staminaRatio: Double, reeling: Boolean) {
        if (state == FishState.STUNNED) return
        if (staminaRatio <= 0.0) {
            state = FishState.STUNNED
            stateDurationTicks = 0
            elapsedTicks = 0
            return
        }
        elapsedTicks++
        if (elapsedTicks >= stateDurationTicks) transition(staminaRatio, reeling)
    }

    fun isDangerous(staminaRatio: Double, reeling: Boolean): Boolean = staminaRatio <= dangerousThresholdRatio && reeling

    private fun transition(staminaRatio: Double, reeling: Boolean) {
        val next = when {
            lineTangleChance > 0.0 && state != FishState.LINE_TANGLE && random.nextDouble() < lineTangleChance -> FishState.LINE_TANGLE
            // 발악 뒤에는 반드시 탈진 — 발악으로 깎은 보상을 몰아치기 타이밍으로 잇는다.
            state == FishState.FINAL_STRUGGLE -> FishState.EXHAUSTED
            state == FishState.EXHAUSTED -> ai.transition("after-exhausted").pick(random.nextDouble())
            else -> ai.transition(tableKey(isDangerous(staminaRatio, reeling), staminaRatio)).pick(random.nextDouble())
        }

        if (next.recoversActionPower) actionPower = maxActionPower
        state = next
        stateDurationTicks = randomDuration(next)
        elapsedTicks = 0

        val cost = ai.state(next).actionPowerCost
        if (cost > 0) actionPower -= minOf(cost, actionPower)
        else if (cost < 0) actionPower = 0
    }

    /** 어느 전이표를 쓸지. 구간(위험 / 지침 / 중간 / 초기 × 행동력)은 게임 규칙이라 코드에 있다. */
    private fun tableKey(dangerous: Boolean, staminaRatio: Double): String {
        if (dangerous) return "dangerous"
        return when {
            staminaRatio <= ai.staminaLowThreshold -> byActionPower("low-stamina")
            staminaRatio <= ai.staminaMidThreshold -> byActionPower("mid-stamina")
            actionPower <= ai.actionPowerLow -> "high-stamina.ap-low"
            actionPower <= ai.actionPowerMid -> "high-stamina.ap-mid"
            else -> "high-stamina.default"
        }
    }

    private fun byActionPower(band: String): String = when {
        actionPower <= ai.actionPowerCritical -> "$band.ap-critical"
        actionPower <= ai.actionPowerLow -> "$band.ap-low"
        else -> "$band.default"
    }

    private fun randomDuration(state: FishState): Int {
        val s = ai.state(state)
        val span = s.durationMaxTicks - s.durationMinTicks
        return if (span <= 0) s.durationMinTicks else s.durationMinTicks + random.nextInt(span)
    }
}
