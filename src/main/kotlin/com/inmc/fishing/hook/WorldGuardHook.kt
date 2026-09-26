package com.inmc.fishing.hook

import kr.inmc.core.integration.PluginClasses
import org.bukkit.Bukkit
import org.bukkit.Location
import java.util.logging.Logger

/**
 * "이 자리는 어느 WorldGuard 구역 안인가". 규칙은 [com.inmc.fishing.region.RegionRules] 가 갖는다.
 *
 * WorldGuard 는 compileOnly 라 설치되지 않은 서버에서 그 클래스를 건드리면 터진다. [regionsAt]
 * 이 먼저 확인한다. **처음 필요할 때 확인하고, 없으면 다음에 다시 본다** — 의존이 `load: OMIT`
 * 라 WorldGuard 가 우리보다 늦게 켜질 수 있다.
 */
class WorldGuardHook(private val logger: Logger) {

    @Volatile
    private var available = false

    /** 설치돼 있지 않거나 조회에 실패하면 빈 집합 — 제한 없음으로 동작한다. */
    fun regionsAt(location: Location): Set<String> {
        if (!available && !probe()) return emptySet()
        return try {
            val query = com.sk89q.worldguard.WorldGuard.getInstance().platform.regionContainer.createQuery()
            query.getApplicableRegions(com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(location))
                .regions.mapTo(HashSet()) { it.id }
        } catch (t: Throwable) {
            // 입질마다 도는 경로다. WorldGuard 내부 오류 하나로 낚시 전체가 멈추면 안 된다.
            logger.warning("WorldGuard 구역 조회 실패: ${t.message}")
            emptySet()
        }
    }

    private fun probe(): Boolean {
        if (!Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) return false
        available = runCatching {
            PluginClasses.require("WorldGuard", "com.sk89q.worldguard.WorldGuard")
            PluginClasses.require("WorldGuard", "com.sk89q.worldedit.bukkit.BukkitAdapter")
        }.isSuccess
        if (available) logger.info("WorldGuard 연동 활성화 (worldguard.yml)")
        return available
    }
}
