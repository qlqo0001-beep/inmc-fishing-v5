package com.inmc.fishing.listener

import com.inmc.fishing.Fishing
import kr.inmc.core.input.Clicks
import io.papermc.paper.event.entity.FishHookStateChangeEvent
import org.bukkit.GameMode
import org.bukkit.entity.FishHook
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerFishEvent
import org.bukkit.event.player.PlayerInputEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

/**
 * 바닐라 낚시를 이 플러그인의 진행으로 바꿔 끼운다.
 *
 * **바닐라 전리품은 한 번도 나오지 않는다.** 입질 순간에 판을 열고, 그 뒤의 감아올리기를
 * 전부 막은 뒤, 판이 끝날 때 낚싯바늘을 우리가 치운다. 바닐라에게 결과를 맡겨두고 그 위에
 * 덧붙이면 두 전리품이 같이 나오거나, 한쪽만 취소되는 판이 생긴다.
 *
 * 리스너는 **판단하지 않는다.** Bukkit 이벤트를 [com.inmc.fishing.catching.FishingFlow] 의
 * 낱말로 옮기기만 한다.
 */
class FishingListener(private val fishing: Fishing) : Listener {

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onFish(event: PlayerFishEvent) {
        val player = event.player
        val angler = fishing.anglers.of(player) ?: return

        when (event.state) {
            PlayerFishEvent.State.FISHING -> {
                // 던지는 순간. 여기서 막아야 "던졌는데 아무 일도 없는" 상태가 안 생긴다.
                if (!fishing.ready) {
                    fishing.messages.send(player, "not-ready")
                    event.isCancelled = true
                    return
                }
                if (!fishing.config.enabled) {
                    fishing.messages.send(player, "disabled")
                    event.isCancelled = true
                    return
                }
                if (!fishing.config.isWorldAllowed(player.world.name)) {
                    fishing.messages.send(player, "world-not-allowed")
                    event.isCancelled = true
                    return
                }
                if (!fishing.catches.isRegionAllowed(player)) {
                    fishing.messages.send(player, "region-blocked")
                    event.isCancelled = true
                    return
                }
                // 등록 안 된 낚싯대를 막는 설정이면 **던지기부터** 막는다(2세대와 같다). 던지게 두면
                // 입질 때 판을 못 열고 바닐라에 넘어가 바닐라 전리품이 나온다.
                val rod = fishing.catches.rodOf(player)
                if (rod == null && !fishing.config.allowUnregisteredRod) {
                    fishing.messages.send(player, "unregistered-rod")
                    event.isCancelled = true
                    return
                }
                // "기다리는 중" 액션바는 여기서 띄우지 않는다 — 찌가 물에 닿을 때(onHookState)부터다(2세대와 같다).
            }

            PlayerFishEvent.State.BITE -> {
                // 던진 뒤 금지 구역으로 걸어 들어간 경우. 던질 때 이미 알렸으니 조용히 막는다.
                if (!fishing.catches.isRegionAllowed(player)) {
                    event.isCancelled = true
                    return
                }
                val opened = fishing.flow.bite(player, angler, event.hook, System.currentTimeMillis())
                // 판을 못 열었으면 바닐라에 맡긴다 — 등록된 물고기가 하나도 없는 서버에서
                // 낚시가 통째로 죽는 것보다 낫다.
                if (!opened) return
            }

            // 판이 도는 중의 감아올리기는 전부 막는다. 클릭이 곧 입력이기 때문이다. 판 밖이면 찌를 거둔 것이니 기다리기도 끝.
            PlayerFishEvent.State.CAUGHT_FISH,
            PlayerFishEvent.State.CAUGHT_ENTITY,
            PlayerFishEvent.State.REEL_IN,
            PlayerFishEvent.State.FAILED_ATTEMPT,
            -> if (angler.isBusy) event.isCancelled = true else fishing.flow.stopWaiting(player, angler)

            // 찌가 땅에 박혔다 — 입질이 올 수 없다.
            PlayerFishEvent.State.IN_GROUND -> fishing.flow.stopWaiting(player, angler)

            // LURED(물고기가 다가오는 중)는 아직 기다리는 중이다. 2세대는 FISHING·BITE 말고는 전부 멈춰서 여기서 액션바가 끊겼다.
            else -> Unit
        }
    }

    /** 찌가 물에 닿으면 "기다리는 중" 을 시작하고, 물을 벗어나거나 무언가에 걸리면 멈춘다(2세대 `onFishHookStateChange`). */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onHookState(event: FishHookStateChangeEvent) {
        val hook = event.entity
        val player = hook.shooter as? Player ?: return
        val angler = fishing.anglers.of(player) ?: return
        if (event.newHookState == FishHook.HookState.BOBBING) fishing.flow.startWaiting(player, angler, hook)
        else if (angler.waitingHook === hook) fishing.flow.stopWaiting(player, angler)
    }

    /** 힘겨루기의 W(감기)·S(풀기). 입력이 **바뀔 때만** 오는 사건이라 판 밖에서는 조회 한 번으로 끝난다. */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onInput(event: PlayerInputEvent) {
        val angler = fishing.anglers.of(event.player) ?: return
        if (angler.fight == null) return
        fishing.flow.keys(angler, event.input.isForward, event.input.isBackward, System.currentTimeMillis())
    }

    /**
     * 미니게임과 힘겨루기의 좌/우클릭.
     *
     * **낮은 우선순위로 먼저 받아 취소한다.** 그대로 두면 같은 우클릭이 낚싯대를 감아올려
     * 판이 시작되자마자 끝난다.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
    fun onClick(event: PlayerInteractEvent) {
        val player = event.player
        val angler = fishing.anglers.of(player) ?: return
        if (!angler.isBusy) return

        val left = when (event.action) {
            Action.LEFT_CLICK_AIR, Action.LEFT_CLICK_BLOCK -> true
            Action.RIGHT_CLICK_AIR, Action.RIGHT_CLICK_BLOCK -> false
            else -> return
        }

        // 한 번의 클릭이 주 손과 왼손으로 두 번 들어온다. 한 번만 센다.
        if (event.hand != org.bukkit.inventory.EquipmentSlot.HAND) {
            event.isCancelled = true
            return
        }

        // 우클릭에 딸려 오는 손 흔들기 유령 좌클릭. 받으면 미니게임에서 우클릭 한 번이 "우 + 좌" 가 되어 틀리고,
        // 힘겨루기에서 풀기가 감기로 덮인다.
        if (Clicks.isGhost(event)) {
            event.isCancelled = true
            return
        }

        if (fishing.flow.click(player, angler, left, System.currentTimeMillis())) {
            event.isCancelled = true
        }
    }

    /**
     * 판을 접는 순간들 — 2세대 `cleanupPlayer` 를 부르던 곳 그대로(월드 이동 · 두 칸 넘는 순간이동 · 죽음 · 아이템 버리기 ·
     * 단축바 바꾸기). 힘겨루기 중에는 제자리에 묶여 있으므로(`FightMovement`) 따로 움직임을 막지 않는다.
     */
    private fun abort(player: org.bukkit.entity.Player) {
        val angler = fishing.anglers.of(player) ?: return
        if (angler.isBusy) fishing.flow.abort(player, angler)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onWorldChange(event: org.bukkit.event.player.PlayerChangedWorldEvent) = abort(event.player)

    /** 안티치트 되돌림처럼 사실상 제자리인 이동은 무시한다(2세대와 같다). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onTeleport(event: org.bukkit.event.player.PlayerTeleportEvent) {
        val from = event.from
        val to = event.to
        if (from.world == to.world && from.distanceSquared(to) < 4.0) return
        abort(event.player)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onDeath(event: org.bukkit.event.entity.PlayerDeathEvent) = abort(event.player)

    @EventHandler(priority = EventPriority.MONITOR)
    fun onDrop(event: org.bukkit.event.player.PlayerDropItemEvent) = abort(event.player)

    @EventHandler(priority = EventPriority.MONITOR)
    fun onHeld(event: org.bukkit.event.player.PlayerItemHeldEvent) = abort(event.player)

    /**
     * 피로회복 물약은 우클릭으로 쓴다. 판이 도는 중에는 [onClick] 이 먼저 먹는다.
     *
     * 허공 클릭은 처음부터 '취소됨'으로 태어난다(클릭한 블록이 없어 블록 사용이 DENY) — `ignoreCancelled` 로 받으면 허공 클릭이 통째로 빠진다. 다른 플러그인이 막았는지는 아이템 사용 쪽을 본다.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    fun onDrink(event: PlayerInteractEvent) {
        if (event.useItemInHand() == org.bukkit.event.Event.Result.DENY) return
        if (event.hand != org.bukkit.inventory.EquipmentSlot.HAND) return
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return
        val player = event.player
        if (fishing.anglers.of(player)?.isBusy == true) return

        val hand = player.inventory.itemInMainHand
        if (!fishing.catches.drinkPotion(player, hand)) return
        event.isCancelled = true
        // 크리에이티브에서는 위에서 줄인 개수가 되돌아온다. 문제 될 것은 없다.
        if (player.gameMode == GameMode.CREATIVE) hand.amount = hand.amount + 1
    }

    /**
     * 낚싯대를 든 채 **Shift+F** — 낚시 메인 화면(2세대와 같다).
     *
     * 가장 먼저(LOWEST) 받아 취소한다. 서버 메뉴 플러그인들이 흔히 Shift+F 를 쓰는데, 낚싯대가
     * 아니면 건드리지 않고 그쪽에 넘긴다.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    fun onSneakSwap(event: org.bukkit.event.player.PlayerSwapHandItemsEvent) {
        val player = event.player
        if (!player.isSneaking) return
        // 이벤트의 mainHandItem 은 **바뀐 뒤** 주손에 올 것이다. 지금 주손을 봐야 왼손 낚싯대를 오인하지 않는다.
        if (player.inventory.itemInMainHand.type != org.bukkit.Material.FISHING_ROD) return
        if (!player.hasPermission("infishing.user") && !player.hasPermission("infishing.admin")) return
        val angler = fishing.anglers.of(player) ?: return
        event.isCancelled = true
        com.inmc.fishing.gui.MainMenu(fishing, player, angler).open(player)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onJoin(event: PlayerJoinEvent) {
        // 힘겨루기 도중 서버가 꺼졌으면 걷지 못하고 나는 채로 남아 있다.
        fishing.movement.restoreOnJoin(event.player)
        fishing.anglers.join(event.player)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) {
        val player = event.player
        fishing.anglers.of(player)?.let { fishing.flow.cancel(player, it) }
        fishing.anglers.quit(player.uniqueId)
        fishing.tournamentHud.forget(player.uniqueId)
    }
}
