package com.inmc.fishing

import com.inmc.fishing.fight.Fight
import com.inmc.fishing.fight.FightSettings
import com.inmc.fishing.fight.FishState
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.Random
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * 힘겨루기가 **2세대와 한 틱도 다르지 않은가.**
 *
 * `fight-golden.txt` 는 2세대 원본 클래스(`FishAI` · `FightCalculator` · `FightSession` · `FightConfig`)를 원본 `fight.yml` 로
 * 돌려 뽑은 틱별 기록이다(틱 순서는 `TrophyFightManager.tick` 을 그대로 옮겼다). 같은 시드·같은 클릭으로 우리 [Fight] 를 돌려
 * 상태·남은 틱·행동력·체력·힘·저항·거리·장력·릴·입력·콤보·시간 정지·결과를 전부 맞춘다. 앞 300 틱은 매 틱, 그 뒤는
 * 20 틱마다와 끝난 틱이 적혀 있다 — 어디서든 한 번 어긋나면 뒤가 전부 어긋나므로 충분하다.
 *
 * 이 테스트가 깨지면 "원본대로" 가 깨진 것이다. 기록을 다시 뽑아 맞추지 말고, 무엇이 달라졌는지 먼저 찾는다.
 */
class FightGoldenTest {

    private val shipped: FightSettings =
        FightSettings.load(YamlConfiguration.loadConfiguration(File("src/main/resources/fight.yml")))

    private data class Scenario(
        val name: String, val grade: String, val rare: Boolean,
        val rodReel: Double, val rodLine: Double, val rodDur: Double,
        val policy: String, val seed: Long, val tangle: Double,
        val rows: MutableList<List<String>> = ArrayList(),
    )

    private fun scenarios(): List<Scenario> {
        val text = javaClass.classLoader.getResourceAsStream("fight-golden.txt")!!.reader(Charsets.UTF_8).readLines()
        val list = ArrayList<Scenario>()
        for (line in text) {
            if (line.isBlank()) continue
            val f = line.trim().split(' ')
            if (f[0] == "#") {
                list += Scenario(f[1], f[2], f[3].toBoolean(), f[4].toDouble(), f[5].toDouble(), f[6].toDouble(), f[7], f[8].toLong(), f[9].toDouble())
            } else {
                list.last().rows += f
            }
        }
        return list
    }

    private fun advice(state: FishState, tick: Int): Char = when (state) {
        FishState.REST, FishState.EXHAUSTED, FishState.SLOW_MOVE, FishState.NORMAL_MOVE, FishState.STUNNED -> 'L'
        FishState.CHARGE, FishState.DIVE, FishState.FINAL_STRUGGLE, FishState.LINE_TANGLE -> 'R'
        FishState.TURN, FishState.CIRCLE -> if (tick % 2 == 0) 'L' else 'R'
        FishState.JUMP -> '-'
    }

    private fun click(policy: String, fight: Fight, tick: Int): Char = when (policy) {
        "IDLE" -> '-'
        "MASHER" -> if (tick % 2 == 0) 'L' else '-'
        "SMART" -> when {
            fight.tension >= 0.6 * fight.lineStrength -> 'R'
            fight.reelState < 0.3 * fight.maxReelState -> '-'
            else -> advice(fight.state, tick)
        }
        else -> advice(fight.state, tick)
    }

    private fun near(expected: String, actual: Double, what: String) {
        val e = expected.toDouble()
        if (abs(e - actual) > 1e-8) fail("$what: 2세대 $e · 지금 $actual")
    }

    @Test
    fun `2세대 원본과 틱마다 같다`() {
        val all = scenarios()
        assertEquals(10, all.size, "기록의 시나리오 수")
        for (s in all) {
            val settings = if (s.tangle > 0) shipped.copy(lineTangle = FightSettings.LineTangle(true, s.tangle)) else shipped
            val t0 = 1_000_000L
            val fight = Fight(settings, s.grade, s.rare, s.rodReel, s.rodLine, s.rodDur, startedAt = t0, random = Random(s.seed))
            val expected = s.rows.associateBy { it[0].toInt() }
            var compared = 0
            for (tick in 1..6000) {
                val now = t0 + tick * 50L
                val c = click(s.policy, fight, tick)
                if (c == 'L') fight.registerReel(now) else if (c == 'R') fight.registerRelease(now)
                val paused = fight.tick(now).let { fight.timerPaused }
                val row = expected[tick]
                if (row != null) {
                    val at = "${s.name} 틱 $tick"
                    assertEquals(row[1][0], c, "$at 클릭")
                    assertEquals(row[2], fight.state.name, "$at 상태")
                    assertEquals(row[3].toInt(), fight.ai.remainingTicks, "$at 남은 틱")
                    assertEquals(row[4].toInt(), fight.ai.actionPower, "$at 행동력")
                    near(row[5], fight.stamina, "$at 체력")
                    near(row[6], fight.power, "$at 힘")
                    near(row[7], fight.resistance, "$at 저항")
                    near(row[8], fight.distance, "$at 거리")
                    near(row[9], fight.tension, "$at 장력")
                    near(row[10], fight.reelState, "$at 릴")
                    assertEquals(row[11].toBoolean(), fight.isReeling(now), "$at 감는 중")
                    assertEquals(row[12].toBoolean(), fight.isReleasing(now), "$at 푸는 중")
                    assertEquals(row[13].toInt(), fight.reelCombo, "$at 감기 콤보")
                    assertEquals(row[14].toInt(), fight.releaseCombo, "$at 풀기 콤보")
                    assertEquals(row[15].toBoolean(), paused, "$at 시간 정지")
                    assertEquals(row[16], fight.outcome.name, "$at 결과")
                    compared++
                }
                if (fight.outcome.isOver) break
            }
            assertEquals(s.rows.size, compared, "${s.name}: 기록된 틱을 전부 지나야 한다(더 일찍/늦게 끝났다)")
        }
    }

    @Test
    fun `기록은 모든 상태와 결과를 지난다`() {
        val rows = scenarios().flatMap { it.rows }
        val states = rows.map { it[2] }.toSet()
        for (state in FishState.entries) assertTrue(state.name in states, "${state.name} 가 기록에 없다 — 대조가 그 상태를 못 본다")
        val outcomes = rows.map { it[16] }.toSet()
        for (o in listOf("SUCCESS", "LINE_SNAPPED", "DISTANCE_EXCEEDED", "REEL_BROKEN")) assertTrue(o in outcomes, "$o 로 끝난 판이 없다")
        assertTrue(rows.any { it[15] == "true" }, "시간 정지(체력 0)가 기록에 없다")
    }
}
