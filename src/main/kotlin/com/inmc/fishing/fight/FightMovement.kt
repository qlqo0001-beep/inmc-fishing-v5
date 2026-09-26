package com.inmc.fishing.fight

import com.inmc.fishing.fish.FishStamp
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

/**
 * 힘겨루기 중 제자리에 묶기 — 2세대 `restrictMovement`/`releaseMovement`/`restoreOnJoin` 그대로다.
 * 걷기·날기 속도를 0 으로 두고 날게 해 제자리에 띄운다. 원래 값은 기억해 두었다 돌려준다.
 *
 * **원래 값을 플레이어 PDC 에도 적는다.** 걷기 속도·비행은 `playerdata.dat` 에 저장되므로, 힘겨루기 도중 서버가 죽으면
 * 걷지 못하고 나는 채로 남는다. PDC 는 **같은 파일에 함께** 저장되어 "속도 0 이 저장됐다" 와 "되돌릴 값이 있다" 가 늘 같이
 * 참이거나 같이 거짓이다. 표시가 없으면 아무것도 하지 않는다 — 무조건 기본값으로 되돌리면 다른 플러그인이 걸어 둔 속도를 부순다.
 */
class FightMovement(private val logger: Logger) {

    private data class Snapshot(val walkSpeed: Float, val flySpeed: Float, val allowFlight: Boolean, val flying: Boolean) {
        /** 로케일에 흔들리지 않는 고정 형식(2세대와 같다). */
        fun serialize(): String = "$walkSpeed;$flySpeed;$allowFlight;$flying"

        companion object {
            fun parse(raw: String?): Snapshot? {
                val parts = raw?.split(';') ?: return null
                if (parts.size != 4) return null
                return runCatching { Snapshot(parts[0].toFloat(), parts[1].toFloat(), parts[2].toBooleanStrict(), parts[3].toBooleanStrict()) }.getOrNull()
            }
        }
    }

    private val snapshots = ConcurrentHashMap<UUID, Snapshot>()

    fun restrict(player: Player) {
        val snapshot = Snapshot(player.walkSpeed, player.flySpeed, player.allowFlight, player.isFlying)
        snapshots[player.uniqueId] = snapshot
        player.persistentDataContainer.set(KEY, PersistentDataType.STRING, snapshot.serialize())
        player.walkSpeed = 0f
        player.flySpeed = 0f
        player.allowFlight = true
        player.isFlying = true
    }

    /** 묶었던 사람만 푼다. 판이 없었으면(기억도 PDC 표시도 없으면) 아무것도 안 한다. */
    fun release(player: Player) {
        val pdc = player.persistentDataContainer
        val snapshot = snapshots.remove(player.uniqueId) ?: Snapshot.parse(pdc.get(KEY, PersistentDataType.STRING)) ?: return
        pdc.remove(KEY)
        apply(player, snapshot)
    }

    /** 접속 때 — 힘겨루기 도중 서버가 꺼져 남은 묶음을 푼다. 2세대가 남긴 표시(`inmc-fishing:fight_movement`)도 본다. */
    fun restoreOnJoin(player: Player) {
        val pdc = player.persistentDataContainer
        for (key in listOf(KEY, LEGACY_KEY)) {
            val raw = pdc.get(key, PersistentDataType.STRING) ?: continue
            pdc.remove(key)
            val snapshot = Snapshot.parse(raw)
            if (snapshot == null) {
                logger.warning("이동 상태 스냅샷을 해석하지 못했습니다: ${player.name} ($raw)")
                continue
            }
            apply(player, snapshot)
            logger.info("힘겨루기 도중 서버가 꺼져 이동 상태를 되돌렸습니다: ${player.name}")
        }
    }

    private fun apply(player: Player, snapshot: Snapshot) {
        player.walkSpeed = snapshot.walkSpeed
        player.flySpeed = snapshot.flySpeed
        player.allowFlight = snapshot.allowFlight
        player.isFlying = snapshot.flying
    }

    @Suppress("DEPRECATION")
    private companion object {
        /** 물고기 도장과 같은 고정 네임스페이스(`infishing`) — 플러그인 이름을 쓰지 않는다. */
        val KEY = NamespacedKey(FishStamp.Keys.NAMESPACE, "fight_movement")
        val LEGACY_KEY = NamespacedKey("inmc-fishing", "fight_movement")
    }
}
