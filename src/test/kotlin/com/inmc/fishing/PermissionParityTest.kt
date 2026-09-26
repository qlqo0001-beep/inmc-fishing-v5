package com.inmc.fishing

import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 2세대와 권한 선언이 같은가.
 *
 * 2세대는 일반 명령을 `infishing.user`(기본 true)로 막을 수 있었다. 5세대가 처음에 그 문을
 * 빼서, 특정 그룹에서 이 권한을 빼 낚시 명령을 막아 둔 서버가 **그 통제를 잃었다.**
 */
class PermissionParityTest {

    private val descriptor: YamlConfiguration =
        YamlConfiguration.loadConfiguration(File("src/main/resources/paper-plugin.yml"))

    @Test
    fun `2세대의 권한이 기본값과 상속까지 같다`() {
        assertEquals("true", descriptor.getString("permissions.infishing.user.default"))
        assertEquals("op", descriptor.getString("permissions.infishing.admin.default"))
        assertTrue(
            descriptor.getBoolean("permissions.infishing.admin.children.infishing.user"),
            "관리자는 일반 권한을 물려받아야 한다",
        )
    }

    @Test
    fun `일반 명령이 infishing_user 로 막힌다`() {
        val command = File("src/main/kotlin/com/inmc/fishing/command/FishingCommand.kt").readText(Charsets.UTF_8)
        assertTrue(command.contains("const val USER = \"infishing.user\""))
        assertTrue(command.contains("hasPermission(USER)"), "루트 명령에 USER 검사가 없습니다")
    }
}
