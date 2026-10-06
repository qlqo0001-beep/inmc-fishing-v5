package com.inmc.fishing.registry

import kr.inmc.core.integration.PlayerSettings
import org.bukkit.Material

/**
 * 낚시가 core 개인 설정 창구에 올리는 것 — 플레이어 메뉴의 개인 설정 화면에 보인다.
 * 끈 사람에게만 어망 자동 넣기를 건너뛴다. 정의가 없을 때(core 가 옛 판) 기본은 켜짐이라 지금과 같다.
 */
internal object FishingSettings {

    const val OWNER = "낚시"

    /** 낚은 물고기를 어망에 바로 넣기. */
    const val AUTO_NET = "fishing.auto-net"

    fun register() {
        PlayerSettings.register(
            PlayerSettings.Setting(
                AUTO_NET, OWNER, "잡은 물고기 어망에 넣기", Material.CHEST,
                listOf("켜면 낚은 물고기가 가방 대신 어망에 들어갑니다.", "끄면 가방으로 들어갑니다."),
                PlayerSettings.Toggle(true),
            ),
        )
    }

    fun unregister() = PlayerSettings.unregisterAll(OWNER)
}
