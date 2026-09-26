package com.inmc.fishing

import com.inmc.fishing.region.RegionRules
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `worldguard.yml` — 2세대 WorldGuard 구역 규칙.
 *
 * 5세대가 처음에 **빌드 의존만 남기고 이 기능을 빠뜨렸다.** 옮겨 온 서버의 금지 구역에서
 * 낚시가 그대로 됐을 것이고 오류는 없었다.
 */
class RegionRulesTest {

    private fun rules(yaml: String, warnings: MutableList<String> = mutableListOf()): RegionRules {
        val config = YamlConfiguration()
        config.loadFromString(yaml)
        return RegionRules.load(config.getConfigurationSection("regions")) { warnings += it }
    }

    private val sample = """
        regions:
          lake:
            world: world
            region: fishing_lake
            grade-weight-multiplier: { S: 1.5, a: 2.0 }
          boost:
            world: world
            region: event_zone
            grade-weight-multiplier: { s: 2.0 }
          spawn:
            world: world
            region: spawn_protection
            fishing-enabled: false
    """.trimIndent()

    @Test
    fun `금지 구역에서 막는다`() {
        val r = rules(sample)
        assertFalse(r.isFishingEnabled("world", setOf("spawn_protection")))
        assertTrue(r.isFishingEnabled("world", setOf("fishing_lake")))
        assertTrue(r.isFishingEnabled("world", emptySet()))
    }

    @Test
    fun `겹치면 하나라도 금지면 금지다`() {
        assertFalse(rules(sample).isFishingEnabled("world", setOf("fishing_lake", "spawn_protection")))
    }

    @Test
    fun `월드가 다르면 같은 이름의 구역이라도 무관하다`() {
        assertTrue(rules(sample).isFishingEnabled("world_nether", setOf("spawn_protection")))
    }

    @Test
    fun `대소문자를 가리지 않는다`() {
        // WorldGuard 는 구역 id 를 소문자로 저장한다. 설정에 대문자로 적어도 맞아야 한다.
        assertFalse(rules(sample).isFishingEnabled("WORLD", setOf("Spawn_Protection")))
    }

    @Test
    fun `겹친 구역의 배수는 곱한다`() {
        val m = rules(sample).multipliers("world", setOf("fishing_lake", "event_zone"))
        assertEquals(3.0, m["s"]!!, 1e-9)
        assertEquals(2.0, m["a"]!!, 1e-9)
    }

    @Test
    fun `world 나 region 이 빠진 항목은 경고하고 버린다`() {
        val warnings = mutableListOf<String>()
        val r = rules(
            """
            regions:
              broken: { world: world }
              dup1: { world: world, region: x, fishing-enabled: false }
              dup2: { world: world, region: x, fishing-enabled: true }
            """.trimIndent(),
            warnings,
        )
        assertEquals(1, r.size)
        assertEquals(2, warnings.size)
        // 먼저 나온 것이 이긴다 (2세대 putIfAbsent).
        assertFalse(r.isFishingEnabled("world", setOf("x")))
    }

    @Test
    fun `배포 파일은 비어 있어 아무것도 막지 않는다`() {
        val config = YamlConfiguration.loadConfiguration(File("src/main/resources/worldguard.yml"))
        assertTrue(RegionRules.load(config.getConfigurationSection("regions")).isEmpty)
    }
}
