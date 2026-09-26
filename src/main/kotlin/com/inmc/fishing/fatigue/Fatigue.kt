package com.inmc.fishing.fatigue

import org.bukkit.configuration.ConfigurationSection

/**
 * 피로도 설정. `config.yml` 의 `fatigue` 절에서 온다.
 *
 * 피로도는 **자동 낚시(미니게임 끄기)를 제어하는 장치**다. 직접 미니게임을 하는 사람은
 * 영향을 받지 않고, 자동으로 돌리는 사람만 피로도가 바닥나면 멈춘다.
 */
data class FatigueConfig(
    /**
     * 새 플레이어의 시작 피로도이자 **자연 회복의 한계**.
     *
     * [max] 와 역할이 다르다. 쉬어서 차오르는 것은 여기까지고, 그 위는 회복 물약과 관리자
     * 명령만 닿는다. 둘을 구분하지 않으면 밤새 접속만 해두어도 상한까지 차서 자동 낚시에
     * 사실상 제한이 없어진다.
     */
    val default: Int = 2000,
    /** 가질 수 있는 절대 최대치. 수동 회복만 여기까지 닿는다. */
    val max: Int = 20000,
    /** [recoveryIntervalSeconds] 초마다 이만큼 회복한다. */
    val recoveryAmount: Int = 100,
    val recoveryIntervalSeconds: Int = 300,
    /** 이 값 **이하**로 떨어지면 자동 낚시가 잠긴다. */
    val lockThreshold: Int = 0,
    /** 잠긴 뒤 이 값 **이상**으로 올라와야 풀린다. */
    val unlockThreshold: Int = 100,
    /** 등급별 소모량. */
    val consume: Map<String, Int> = emptyMap(),
) {

    fun consumeFor(gradeId: String): Int = consume[gradeId.lowercase()] ?: 0

    companion object {

        fun load(section: ConfigurationSection?): FatigueConfig {
            if (section == null) return FatigueConfig()
            val consume = HashMap<String, Int>()
            section.getConfigurationSection("consume")?.let { node ->
                for (key in node.getKeys(false)) consume[key.lowercase()] = node.getInt(key, 0)
            }
            return FatigueConfig(
                default = section.getInt("default", 2000).coerceAtLeast(0),
                max = section.getInt("max", 20000).coerceAtLeast(1),
                recoveryAmount = section.getInt("recovery.amount", 100).coerceAtLeast(0),
                recoveryIntervalSeconds = section.getInt("recovery.interval", 300).coerceAtLeast(1),
                lockThreshold = section.getInt("auto-minigame-lock-threshold", 0),
                unlockThreshold = section.getInt("auto-minigame-unlock-threshold", 100),
                consume = consume,
            )
        }
    }
}

/**
 * 한 사람의 피로도.
 *
 * 잠금이 **이력 현상(hysteresis)** 이다 — 0 에서 잠기고 100 이상이라야 풀린다. 두 값이 같으면
 * 경계에서 잠금이 매 틱 껐다 켜졌다 하고, 그러면 자동 낚시가 한 마리 잡고 멈추기를 반복한다.
 *
 * 낚싯대는 **알지 못한다.** 들고 있는 낚싯대의 보너스는 [recover] 에 인자로 들어온다 —
 * 낚싯대는 회복 중에 바뀔 수 있고, 그때마다 이 객체를 다시 만들 이유는 없다.
 */
class PlayerFatigue(private val config: FatigueConfig, current: Int = config.default) {

    var value: Int = current.coerceIn(0, config.max)
        private set

    /** 자동 낚시가 잠겨 있는지. 이력 현상 때문에 값만 보고는 알 수 없어 상태로 들고 있다. */
    var locked: Boolean = false
        private set

    /**
     * 마지막으로 회복한 시각. 아직 한 번도 안 했으면 null.
     *
     * **0 을 "없음" 표식으로 쓰지 않는다.** 0 도 정상적인 시각이라, 표식으로 쓰면 그 순간
     * 회복이 영원히 시작되지 않는다. 실제로 테스트에서 그렇게 걸렸다.
     */
    var lastRecoveredAt: Long? = null
        private set

    /**
     * 낚은 만큼 소모한다.
     *
     * @return 실제로 소모한 양. 모자라면 남은 만큼만 쓰고 잠긴다.
     */
    fun consume(gradeId: String): Int {
        val cost = config.consumeFor(gradeId)
        if (cost <= 0) return 0

        val spent = minOf(cost, value)
        value -= spent
        if (value <= config.lockThreshold) locked = true
        return spent
    }

    /**
     * 자연 회복이 닿는 상한. `min(절대 상한, 기본값 + 낚싯대 보너스)` — 2세대 `getEffectiveMax` 와 같다.
     *
     * 회복과 화면 표시(`%inmcfishing_fatigue_max%`)가 같은 식을 써야 한다. 따로 계산하면
     * 게이지는 가득인데 회복이 멈추지 않거나, 반대로 덜 찼는데 멈춘다.
     */
    fun ceiling(maxBonus: Int = 0): Int = (config.default + maxBonus).coerceAtMost(config.max)

    /**
     * 시간에 따라 회복한다.
     *
     * **지난 만큼 한꺼번에 회복한다.** 서버가 꺼져 있던 동안이나 접속을 끊었던 동안도 포함이다 —
     * 피로도는 "쉬면 낫는 것"이라 접속 여부와 무관해야 한다.
     *
     * **한계는 [FatigueConfig.default] 지 [FatigueConfig.max] 가 아니다.** 물약으로 그 위까지
     * 채워둔 사람이 자연 회복 때문에 **깎이지는 않는다** — 이미 한계 위면 그대로 둔다.
     *
     * @param maxBonus 들고 있는 낚싯대의 [com.inmc.fishing.rod.Rod.maxFatigue].
     * @param amountBonus 들고 있는 낚싯대의 [com.inmc.fishing.rod.Rod.fatigueRecovery].
     * @return 이번에 회복한 양.
     */
    fun recover(now: Long, maxBonus: Int = 0, amountBonus: Int = 0): Int {
        val amount = config.recoveryAmount + amountBonus
        if (amount <= 0) return 0
        val since = lastRecoveredAt
        if (since == null) {
            lastRecoveredAt = now
            return 0
        }

        val intervalMillis = config.recoveryIntervalSeconds * 1000L
        val elapsed = now - since
        if (elapsed < intervalMillis) return 0

        val steps = (elapsed / intervalMillis).toInt()
        val before = value
        value = (value + steps * amount).coerceAtMost(maxOf(value, ceiling(maxBonus)))
        // 남은 나머지는 다음 회복으로 이월한다. 매번 now 로 맞추면 주기가 조금씩 밀린다.
        lastRecoveredAt = since + steps * intervalMillis

        if (locked && value >= config.unlockThreshold) locked = false
        return value - before
    }

    /** 회복 물약 등으로 즉시 채운다. 여기는 절대 상한까지 닿는다. */
    fun restore(amount: Int) {
        if (amount <= 0) return
        value = (value + amount).coerceAtMost(config.max)
        if (locked && value >= config.unlockThreshold) locked = false
    }

    /**
     * 저장된 상태를 **그대로** 되돌린다. 디스크에서 읽을 때와 설정 리로드 때 쓴다.
     *
     * [set] 을 쓰면 안 된다. 그쪽은 값에서 잠금을 **다시 계산**하는데, 이력 현상 때문에
     * 잠긴 사람의 값은 해제 기준보다 낮은 것이 정상이라 그대로 두면 잠금이 풀린다 —
     * 리로드 한 번으로 자동 낚시 제한이 사라진다.
     */
    fun restoreState(amount: Int, wasLocked: Boolean, recoveredAt: Long?) {
        value = amount.coerceIn(0, config.max)
        locked = wasLocked
        lastRecoveredAt = recoveredAt
    }

    /** 관리자용. 값을 직접 세운다. */
    fun set(amount: Int) {
        value = amount.coerceIn(0, config.max)
        locked = value <= config.lockThreshold
    }

    /** 화면 게이지용 0.0~1.0. */
    fun ratio(): Double = if (config.max <= 0) 0.0 else (value.toDouble() / config.max).coerceIn(0.0, 1.0)
}
