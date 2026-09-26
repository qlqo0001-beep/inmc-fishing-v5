package com.inmc.fishing.player

import com.inmc.fishing.Fishing
import kr.inmc.core.store.YamlFolder
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 접속한 사람들의 [Angler] 를 들고 있다가 `players/<uuid>.yml` 로 내린다.
 *
 * **접속한 사람만 메모리에 둔다.** 도감은 물고기 종류만큼 커지므로 전원을 올려두면 서버
 * 수명 내내 늘기만 한다. 오프라인 조회(랭킹·명령어)는 파일을 한 번 읽는 쪽이 낫다.
 *
 * 읽기는 워커에서, 반영은 메인에서 한다 — [Angler.load] 가 물고기 레지스트리를 보기 때문에
 * 파싱 자체를 메인으로 되돌린다.
 */
class AnglerStore(private val fishing: Fishing) {

    private val folder = YamlFolder(
        io = fishing.io,
        logger = fishing.logger,
        folderName = "players",
        header = "# 낚시 기록. 손으로 고치지 마세요.\n",
        what = "플레이어 기록",
    )

    private val online = ConcurrentHashMap<UUID, Angler>()

    fun of(player: Player): Angler? = online[player.uniqueId]

    fun of(id: UUID): Angler? = online[id]

    fun all(): List<Angler> = online.values.toList()

    /**
     * 접속한 사람의 기록을 읽어 올린다.
     *
     * 다 읽기 전에 낚싯대를 던지면 [of] 가 null 이라 아무 일도 일어나지 않는다. 빈 기록을
     * 먼저 올려두면 그 사이의 낚시가 **빈 도감에 쌓였다가 덮어써진다** — 기록이 사라진다.
     */
    fun join(player: Player, then: (Angler) -> Unit = {}) {
        val id = player.uniqueId
        online[id]?.let {
            it.name = player.name
            then(it)
            return
        }

        fishing.io.async({ readFile(id) }) { config ->
            // 워커가 도는 사이에 나갔을 수 있다. 그때 올려두면 영영 안 내려간다.
            if (!player.isOnline) return@async
            val angler = Angler.load(id, config, fishing.config, fishing.fish)
            angler.name = player.name
            online[id] = angler
            then(angler)
        }
    }

    /** 접속이 끊겼다. 마지막으로 한 번 저장하고 메모리에서 내린다. */
    fun quit(id: UUID) {
        val angler = online.remove(id) ?: return
        angler.clearSessions()
        folder.markDirty(id.toString())
        pending[id.toString()] = angler
        flush()
    }

    fun markDirty(angler: Angler) {
        folder.markDirty(angler.id.toString())
    }

    // --- 저장 -----------------------------------------------------------------------

    /** 나간 사람의 마지막 저장분. 메모리에서 내린 뒤에도 한 번은 써야 한다. */
    private val pending = ConcurrentHashMap<String, Angler>()

    fun flush() {
        folder.flushDirty { id -> render(id) }
        pending.clear()
    }

    fun flushBlocking() {
        for (angler in online.values) folder.markDirty(angler.id.toString())
        folder.flushDirtyBlocking { id -> render(id) }
        pending.clear()
    }

    /** 설정이 바뀌었다. 올라와 있는 전원을 새 설정으로 갈아끼운다. */
    fun rebindAll() {
        for (angler in online.values) {
            angler.clearSessions()
            angler.rebind(fishing.config)
        }
    }

    /** **메인 스레드에서 돈다.** [YamlFolder] 의 계약이다. */
    private fun render(id: String): YamlConfiguration? {
        val uuid = runCatching { UUID.fromString(id) }.getOrNull() ?: return null
        val angler = online[uuid] ?: pending[id] ?: return null
        return YamlConfiguration().also { angler.save(it) }
    }

    private fun readFile(id: UUID): YamlConfiguration {
        val target = java.io.File(folder.folder, "$id.yml")
        return if (target.isFile) fishing.io.load(target) else YamlConfiguration()
    }
}
