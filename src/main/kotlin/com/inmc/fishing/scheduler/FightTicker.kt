package com.inmc.fishing.scheduler

import com.inmc.fishing.Fishing
import kr.inmc.core.scheduler.TickerBase
import org.bukkit.Bukkit

/**
 * 진행 중인 판만 도는 **1틱** 티커.
 *
 * 초당 20번이라 [Ticker] 와 따로 있다. 피로도 회복이나 디스크 플러시까지 20Hz 로 돌릴 이유는
 * 없고, 반대로 힘겨루기를 1Hz 로 돌리면 게이지가 뚝뚝 끊긴다 — 2세대가 `TrophyFightManager`
 * 를 따로 둔 것과 같은 이유다.
 *
 * **아무 판도 안 돌면 곧바로 돌아온다.** 접속자 목록조차 훑지 않는다.
 */
class FightTicker(private val fishing: Fishing) : TickerBase(fishing.plugin) {

    override val periodTicks = 1L

    override fun ready(): Boolean = fishing.ready

    override fun tick(now: Long) {
        val busy = fishing.anglers.all().filter { it.isBusy }
        if (busy.isEmpty()) return

        for (angler in busy) {
            val player = Bukkit.getPlayer(angler.id) ?: continue
            step("fight") { fishing.flow.tick(player, angler, now) }
        }
    }
}
