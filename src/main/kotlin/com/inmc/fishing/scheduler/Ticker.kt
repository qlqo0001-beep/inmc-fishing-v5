package com.inmc.fishing.scheduler

import com.inmc.fishing.Fishing
import com.inmc.fishing.gui.FilletMenu
import kr.inmc.core.scheduler.TickerBase

/**
 * 이 플러그인의 **유일한** 반복 작업, 1Hz.
 *
 * 주기적인 것은 전부 여기 얹는다 — 미니게임 만료, 힘겨루기 진행, 피로도 회복, 손질 완료,
 * 대회 시작·종료, 랭킹 갱신, 프롬프트 만료, 디스크 플러시.
 *
 * 힘겨루기만 예외다. 초당 20틱으로 돌아야 해서 [FightTicker] 가 따로 있다 — 2세대의
 * `TrophyFightManager` 와 같은 이유다.
 */
class Ticker(private val fishing: Fishing) : TickerBase(fishing.plugin) {

    override val periodTicks = PERIOD_TICKS

    override fun ready(): Boolean = fishing.ready

    override fun tick(now: Long) {
        step("prompts") { fishing.prompts.tick(now) }
        step("ranks") { fishing.ranks.tick(now) }
        step("tournament") { fishing.tournaments.tick(now) }
        step("tournament-hud") { fishing.tournamentHud.update(now) }
        step("fatigue") { recover(now) }
        step("waiting") { waiting() }
        step("fillet-menu") { refreshFilletMenus() }
        step("flush") {
            fishing.ranks.flush()
            fishing.anglers.flush()
            fishing.mailbox.flush()
            fishing.fish.flush()
            for (store in listOf(fishing.rodRegistry, fishing.baits, fishing.potions, fishing.fillets)) if (!store.retired) store.flush()
            fishing.tournaments.flushState()
        }
    }

    /**
     * 피로도 회복과 손질 완료.
     *
     * 회복 보너스는 **지금 들고 있는 낚싯대**에서 온다. 저장해두면 낚싯대를 바꿔도 옛 보너스가
     * 따라다니고, 그러면 좋은 낚싯대를 한 번 들었다 놓는 것이 영구 강화가 된다.
     */
    private fun recover(now: Long) {
        for (angler in fishing.anglers.all()) {
            val player = org.bukkit.Bukkit.getPlayer(angler.id) ?: continue
            val rod = fishing.catches.rodOf(player)

            val wasLocked = angler.fatigue.locked
            val healed = angler.fatigue.recover(
                now = now,
                maxBonus = rod?.maxFatigue ?: 0,
                amountBonus = rod?.fatigueRecovery ?: 0,
            )
            if (healed > 0) fishing.anglers.markDirty(angler)
            if (wasLocked && !angler.fatigue.locked) fishing.messages.send(player, "fatigue-unlocked")

            val done = angler.bench.collect(now)
            if (done.isEmpty()) continue
            for (slot in done) fishing.fillet.deliver(player, slot)
            fishing.anglers.markDirty(angler)
        }
    }

    /** "물고기를 기다리는 중" 액션바를 이어 보낸다(2세대 `cast-status.refresh-interval-ticks` 20 과 같은 1초). */
    private fun waiting() {
        for (angler in fishing.anglers.all()) {
            if (angler.waitingHook == null) continue
            val player = org.bukkit.Bukkit.getPlayer(angler.id) ?: continue
            fishing.flow.waiting(player, angler)
        }
    }

    /**
     * 열려 있는 손질대를 다시 그린다(2세대 `FilletManager.tick` 과 같다). 안 그리면 남은 시간이
     * 멈춰 보이고, 위 단계가 이미 건네준 칸이 화면에 그대로 남는다.
     */
    private fun refreshFilletMenus() {
        for (player in org.bukkit.Bukkit.getOnlinePlayers()) {
            (player.openInventory.topInventory.holder as? FilletMenu)?.refresh()
        }
    }

    companion object {
        const val PERIOD_TICKS = 20L
    }
}
