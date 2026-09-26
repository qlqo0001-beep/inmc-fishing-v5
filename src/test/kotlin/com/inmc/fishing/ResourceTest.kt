package com.inmc.fishing

import com.inmc.fishing.config.Messages
import com.inmc.fishing.fatigue.FatigueConfig
import com.inmc.fishing.fillet.FilletConfig
import com.inmc.fishing.fish.TrophyRule
import com.inmc.fishing.grade.GradeTable
import com.inmc.fishing.modifier.Modifiers
import org.bukkit.configuration.file.YamlConfiguration
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 배포 리소스를 플러그인이 기동 때 읽는 방식 그대로 읽어본다.
 *
 * 배포본의 YAML 오타는 이 테스트가 없으면 **첫 기동이 깨지고 나서야** 드러난다. 한글이 많아
 * 인코딩 회귀도 눈에 잘 띄지 않는다.
 */
class ResourceTest {

    private fun load(path: String): YamlConfiguration {
        val stream = javaClass.classLoader.getResourceAsStream(path)
        assertNotNull(stream, "리소스를 찾을 수 없습니다: $path")
        return stream.use {
            YamlConfiguration.loadConfiguration(InputStreamReader(it, StandardCharsets.UTF_8))
        }
    }

    // --- messages.yml --------------------------------------------------------------

    @Test
    fun `배포 messages 와 내장 기본값의 키가 정확히 같다`() {
        // 파일에만 있는 키는 코드가 절대 읽지 않고, 코드에만 있는 키는 관리자가 파일에서
        // 찾을 수 없다. 788줄짜리 2세대 messages.yml 에 실제로 양쪽이 다 있었다.
        val shipped = load("messages.yml").getKeys(false).toSet()
        val builtIn = Messages.DEFAULTS.keys

        assertEquals(emptySet(), builtIn - shipped, "배포 messages.yml 에 빠진 키")
        assertEquals(emptySet(), shipped - builtIn, "코드가 읽지 않는 키")
    }

    @Test
    fun `프롬프트 네 키가 있다`() {
        // core 의 ChatPrompt 가 이 넷을 요구한다. 빠지면 입력 안내가 조용히 무음이 된다.
        val keys = load("messages.yml").getKeys(false)

        for (key in listOf("prompt-enter", "prompt-cancelled", "prompt-timeout", "prompt-invalid-number")) {
            assertTrue(key in keys, "$key 가 없습니다")
        }
    }

    @Test
    fun `한글이 깨지지 않는다`() {
        // UTF-8 을 명시하지 않은 스크립트가 파일을 다시 쓰면 여기서 잡힌다.
        val messages = load("messages.yml")

        assertTrue(messages.getString("bite").orEmpty().contains("입질"))
        assertTrue(messages.getString("trophy").orEmpty().contains("트로피"))
    }

    // --- grades.yml ----------------------------------------------------------------

    @Test
    fun `배포 등급표가 읽히고 합이 100 이다`() {
        val grades = GradeTable.load(load("grades.yml").getConfigurationSection("grades"))

        assertEquals(7, grades.ordered.size)
        assertEquals(100.0, grades.ordered.sumOf { it.weight }, 1e-9)
    }

    @Test
    fun `배포 등급표에 next-grade 가 남아 있지 않다`() {
        // 대어가 "한 등급 위로 승급"에서 "반드시 트로피가 되는 큰 개체"로 바뀌면서 쓰이지
        // 않게 된 설정이다. 남아 있으면 관리자가 고쳐놓고 왜 안 먹는지 묻게 된다.
        val grades = load("grades.yml").getConfigurationSection("grades")!!

        for (key in grades.getKeys(false)) {
            val section = grades.getConfigurationSection(key)!!
            assertTrue(section.getString("next-grade") == null, "$key 에 next-grade 가 남아 있다")
        }
    }

    @Test
    fun `등급이 올라갈수록 어렵고 희귀하다`() {
        val grades = GradeTable.load(load("grades.yml").getConfigurationSection("grades"))
        val ordered = grades.ordered

        for (i in 1 until ordered.size) {
            val prev = ordered[i - 1]
            val cur = ordered[i]
            assertTrue(cur.weight <= prev.weight, "${cur.id} 가 ${prev.id} 보다 흔하다")
            assertTrue(cur.inputCount >= prev.inputCount, "${cur.id} 가 ${prev.id} 보다 클릭이 적다")
            assertTrue(cur.timeSeconds <= prev.timeSeconds, "${cur.id} 가 ${prev.id} 보다 시간이 넉넉하다")
        }
    }

    // --- modifiers.yml -------------------------------------------------------------

    @Test
    fun `배포 보정표가 곱연산으로 읽힌다`() {
        val modifiers = Modifiers.load(load("modifiers.yml"))

        val result = modifiers.multipliers(
            Modifiers.Situation("world_nether", "OCEAN", "RAIN", "NIGHT", setOf("fishing.vip")),
        )

        // 2.0 × 1.5 × 1.2 × 1.1 = 3.96
        assertEquals(3.96, result.getValue("s"), 1e-9)
    }

    @Test
    fun `보정표의 등급 id 가 소문자로 정규화된다`() {
        // 설정에는 `S` 로 적히지만 GradeTable 은 `s` 로 찾는다. 안 맞으면 보정이 조용히 사라진다.
        val modifiers = Modifiers.load(load("modifiers.yml"))

        val result = modifiers.multipliers(
            Modifiers.Situation("world_nether", "OCEAN", "CLEAR", "DAY"),
        )
        assertTrue("s" in result, "소문자 키로 들어와야 한다")
    }

    // --- config.yml ----------------------------------------------------------------

    @Test
    fun `배포 설정이 플러그인이 읽는 키를 전부 갖고 있다`() {
        val config = load("config.yml")

        assertTrue(config.getBoolean("settings.enabled"))
        assertTrue(config.getBoolean("settings.require-empty-slot"))
        assertEquals(7.0, config.getDouble("rates.double-chance"))
        assertEquals(1.0, config.getDouble("rates.big-fish-chance"))
        assertEquals(100, config.getInt("net.max-size"))
        assertEquals(54, config.getInt("rewards.mailbox-limit"))
    }

    @Test
    fun `배포 트로피 기준이 코드 기본값과 같다`() {
        val rule = TrophyRule.load(load("config.yml").getConfigurationSection("rates"))

        assertEquals(TrophyRule.DEFAULT.normalMultiplier, rule.normalMultiplier)
        assertEquals(TrophyRule.DEFAULT.rareMultiplier, rule.rareMultiplier)
    }

    @Test
    fun `피로도 잠금과 해제 임계값이 다르다`() {
        // 같으면 경계에서 잠금이 껐다 켜졌다 해서 자동 낚시가 한 마리 잡고 멈추기를 반복한다.
        val fatigue = FatigueConfig.load(load("config.yml").getConfigurationSection("fatigue"))

        assertTrue(
            fatigue.unlockThreshold > fatigue.lockThreshold,
            "해제(${fatigue.unlockThreshold})가 잠금(${fatigue.lockThreshold})보다 커야 한다",
        )
    }

    @Test
    fun `피로도 소모가 등급별로 커진다`() {
        val fatigue = FatigueConfig.load(load("config.yml").getConfigurationSection("fatigue"))

        val order = listOf("f", "e", "d", "c", "b", "a", "s")
        for (i in 1 until order.size) {
            assertTrue(
                fatigue.consumeFor(order[i]) > fatigue.consumeFor(order[i - 1]),
                "${order[i]} 가 ${order[i - 1]} 보다 덜 쓴다",
            )
        }
    }

    @Test
    fun `손질 시간이 등급별로 길어진다`() {
        val fillet = FilletConfig.load(load("config.yml").getConfigurationSection("fillet"))

        val order = listOf("f", "e", "d", "c", "b", "a", "s")
        for (i in 1 until order.size) {
            assertTrue(
                fillet.secondsFor(order[i], com.inmc.fishing.fish.Trophy.NONE, 1) >
                    fillet.secondsFor(order[i - 1], com.inmc.fishing.fish.Trophy.NONE, 1),
                "${order[i]} 손질이 ${order[i - 1]} 보다 빠르다",
            )
        }
    }

    @Test
    fun `자동 낚시에 최상위 등급이 없다`() {
        // 클릭 없이 S 등급이 나오면 미니게임을 할 이유가 없다.
        val grades = load("config.yml").getStringList("minigame-off.allowed-grades")

        assertTrue("s" !in grades.map { it.lowercase() }, "자동 낚시로 S 가 나온다")
        assertTrue(grades.isNotEmpty(), "비우면 전부 허용이 되어 S 도 나온다")
    }
}
