package com.inmc.fishing

import com.inmc.fishing.modifier.Modifiers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 환경 보정이 곱연산으로 쌓이는지 못박는다.
 *
 * 축이 다섯이라 손으로 검증하기 어렵고, 틀려도 "요즘 S가 잘 안 나오네" 정도로만 느껴진다.
 */
class ModifiersTest {

    private val modifiers = Modifiers(
        world = mapOf("WORLD_NETHER" to mapOf("s" to 2.0, "a" to 1.5)),
        biome = mapOf("RIVER" to mapOf("f" to 1.2)),
        weather = mapOf("RAIN" to mapOf("a" to 1.3, "s" to 1.5)),
        time = mapOf("NIGHT" to mapOf("s" to 1.2)),
        permission = mapOf("fishing.vip" to mapOf("a" to 1.1, "s" to 1.1)),
    )

    private fun at(
        world: String = "world",
        biome: String = "OCEAN",
        weather: String = "CLEAR",
        time: String = "DAY",
        permissions: Set<String> = emptySet(),
    ) = Modifiers.Situation(world, biome, weather, time, permissions)

    @Test
    fun `해당 없는 상황은 보정이 비어 있다`() {
        assertTrue(modifiers.multipliers(at()).isEmpty())
    }

    @Test
    fun `축 하나면 그 배수만 적용된다`() {
        assertEquals(mapOf("f" to 1.2), modifiers.multipliers(at(biome = "RIVER")))
    }

    @Test
    fun `여러 축이 곱해진다`() {
        // 네더(2.0) × 비(1.5) × 밤(1.2) × VIP(1.1) = 3.96
        val result = modifiers.multipliers(
            at(world = "world_nether", weather = "RAIN", time = "NIGHT", permissions = setOf("fishing.vip")),
        )

        assertEquals(3.96, result.getValue("s"), 1e-9)
        // A 는 네더(1.5) × 비(1.3) × VIP(1.1) = 2.145
        assertEquals(2.145, result.getValue("a"), 1e-9)
    }

    @Test
    fun `보정이 없는 등급은 담기지 않는다`() {
        // 담기면 1.0 이 들어가는데, 그건 GradeTable 이 없는 키로 읽는 값과 같다.
        // 담지 않는 편이 결과가 작고 의도가 분명하다.
        val result = modifiers.multipliers(at(world = "world_nether"))

        assertEquals(setOf("s", "a"), result.keys)
    }

    @Test
    fun `월드 이름은 대소문자를 가리지 않는다`() {
        // 설정에는 world_nether, Bukkit 은 world_nether - 그래도 대문자로 정규화해 둔다.
        assertEquals(2.0, modifiers.multipliers(at(world = "WORLD_NETHER")).getValue("s"))
        assertEquals(2.0, modifiers.multipliers(at(world = "world_nether")).getValue("s"))
    }

    @Test
    fun `권한을 가지지 않으면 그 보정은 빠진다`() {
        val without = modifiers.multipliers(at(weather = "RAIN"))
        val with = modifiers.multipliers(at(weather = "RAIN", permissions = setOf("fishing.vip")))

        assertEquals(1.5, without.getValue("s"))
        assertEquals(1.65, with.getValue("s"), 1e-9)
    }

    @Test
    fun `권한 여러 개는 전부 곱해진다`() {
        val two = Modifiers(
            world = emptyMap(), biome = emptyMap(), weather = emptyMap(), time = emptyMap(),
            permission = mapOf("a.one" to mapOf("s" to 2.0), "a.two" to mapOf("s" to 3.0)),
        )

        assertEquals(6.0, two.multipliers(at(permissions = setOf("a.one", "a.two"))).getValue("s"))
    }

    @Test
    fun `설정에 등장하는 권한 노드를 알려준다`() {
        // 이걸로 "어떤 권한을 물어볼지"를 정한다. 전부 물어보면 hasPermission 이 낚시마다 쏟아진다.
        assertEquals(setOf("fishing.vip"), modifiers.permissionNodes())
    }

    @Test
    fun `보정이 아예 없는 설정도 안전하다`() {
        assertTrue(Modifiers.NONE.multipliers(at(world = "world_nether")).isEmpty())
    }
}

/**
 * 권한 노드의 **점**이 설정 읽기를 깨뜨리지 않는지 못박는다.
 *
 * `YamlConfiguration` 은 점을 경로 구분자로 다룬다. `fishing.vip` 를 평범하게 읽으면
 * `fishing` 섹션 하나만 나오고 보정이 통째로 사라지는데, **설정 파일은 멀쩡해 보이고 오류도
 * 안 난다.** 실제로 배포 리소스 테스트에서 이렇게 걸렸다.
 */
class ModifierPermissionKeyTest {

    private fun load(yaml: String): Modifiers {
        val config = org.bukkit.configuration.file.YamlConfiguration()
        config.loadFromString(yaml)
        return Modifiers.load(config)
    }

    @Test
    fun `점이 든 권한 노드를 읽는다`() {
        val modifiers = load(
            """
            permission:
              fishing.vip:
                grade-weight-multiplier:
                  S: 2.0
            """.trimIndent(),
        )

        val result = modifiers.multipliers(
            Modifiers.Situation("world", "OCEAN", "CLEAR", "DAY", setOf("fishing.vip")),
        )
        assertEquals(2.0, result.getValue("s"))
    }

    @Test
    fun `점이 여러 개여도 읽는다`() {
        val modifiers = load(
            """
            permission:
              a.b.c.vip:
                grade-weight-multiplier:
                  S: 3.0
            """.trimIndent(),
        )

        assertEquals(setOf("a.b.c.vip"), modifiers.permissionNodes())
    }

    @Test
    fun `점 없는 권한 노드도 그대로 읽는다`() {
        val modifiers = load(
            """
            permission:
              vip:
                grade-weight-multiplier:
                  S: 2.0
            """.trimIndent(),
        )

        assertEquals(setOf("vip"), modifiers.permissionNodes())
    }

    @Test
    fun `여러 권한이 섞여 있어도 각각 읽는다`() {
        val modifiers = load(
            """
            permission:
              fishing.vip:
                grade-weight-multiplier:
                  S: 2.0
              fishing.vvip:
                grade-weight-multiplier:
                  S: 3.0
            """.trimIndent(),
        )

        assertEquals(setOf("fishing.vip", "fishing.vvip"), modifiers.permissionNodes())
        val both = modifiers.multipliers(
            Modifiers.Situation("world", "OCEAN", "CLEAR", "DAY", setOf("fishing.vip", "fishing.vvip")),
        )
        assertEquals(6.0, both.getValue("s"), 1e-9)
    }
}
