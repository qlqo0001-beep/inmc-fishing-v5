package com.inmc.fishing.listener

import com.inmc.fishing.Fishing
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

/**
 * 관리 화면의 채팅 입력을 core 의 [kr.inmc.core.input.ChatPrompt] 로 넘긴다.
 *
 * 이게 없으면 화면이 이름이나 숫자를 물어본 뒤 **영영 기다린다.** 오류는 나지 않고 입력만
 * 채팅에 그대로 찍힌다.
 *
 * 비동기 스레드에서 돈다. 콜백을 그 플레이어의 리전으로 되돌리는 일은 `ChatPrompt` 가
 * 안에서 처리한다.
 */
class ChatInputListener(private val fishing: Fishing) : Listener {

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onChat(event: AsyncChatEvent) {
        val player = event.player
        if (!fishing.prompts.isWaiting(player.uniqueId)) return
        val text = PlainTextComponentSerializer.plainText().serialize(event.message())
        if (fishing.prompts.submit(player, text)) event.isCancelled = true
    }
}
