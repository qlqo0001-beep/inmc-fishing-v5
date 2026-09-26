package com.inmc.fishing.config

import org.bukkit.configuration.ConfigurationSection

/**
 * 힘겨루기 시작 연출 — `config.yml` 의 `fight.intro`. 2세대 `fight.yml` 의 `intro` 와 같은 값이다.
 *
 * 등장(!!!) → 3 → 2 → 1 → START!! 를 [stepSeconds] 간격으로 보여주고 그 뒤에 힘겨루기가 시작된다.
 * 소리는 `block.bell.use` 같은 소리 키다. 비우면 소리를 내지 않는다.
 */
data class FightIntro(
    val enabled: Boolean = true,
    val announceTitle: String = "&6&l!!!",
    val announceSubtitle: String = "&e대물의 기운이 느껴진다...",
    val announceSound: String = "block.bell.use",
    val countdownSound: String = "block.note_block.hat",
    val startTitle: String = "&e&lSTART!!",
    val startSound: String = "entity.player.levelup",
    val stepSeconds: Double = 1.0,
) {
    val stepMillis: Long get() = (stepSeconds.coerceAtLeast(0.05) * 1000).toLong()

    companion object {
        fun load(section: ConfigurationSection?): FightIntro {
            val d = FightIntro()
            if (section == null) return d
            return FightIntro(
                enabled = section.getBoolean("enabled", d.enabled),
                announceTitle = section.getString("announce-title", d.announceTitle)!!,
                announceSubtitle = section.getString("announce-subtitle", d.announceSubtitle)!!,
                announceSound = section.getString("announce-sound", d.announceSound)!!,
                countdownSound = section.getString("countdown-sound", d.countdownSound)!!,
                startTitle = section.getString("start-title", d.startTitle)!!,
                startSound = section.getString("start-sound", d.startSound)!!,
                stepSeconds = section.getDouble("step-seconds", d.stepSeconds),
            )
        }
    }
}

/**
 * 미니게임 결과 타이틀의 시간 — `config.yml` 의 `titles`. 2세대 `messages.yml` 의 `titles` 에 있던
 * 시간 값이다. 문구는 `messages.yml` 의 `title-*` 키가 갖는다.
 */
data class ResultTitles(
    val fadeInSeconds: Double = 0.5,
    val staySeconds: Double = 2.0,
    val fadeOutSeconds: Double = 0.5,
) {
    companion object {
        fun load(section: ConfigurationSection?): ResultTitles {
            val d = ResultTitles()
            if (section == null) return d
            return ResultTitles(
                fadeInSeconds = section.getDouble("fade-in", d.fadeInSeconds),
                staySeconds = section.getDouble("display-seconds", d.staySeconds),
                fadeOutSeconds = section.getDouble("fade-out", d.fadeOutSeconds),
            )
        }
    }
}
