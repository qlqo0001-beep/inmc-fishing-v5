package com.inmc.fishing.hook

import com.inmc.fishing.Fishing
import com.inmc.fishing.collection.Collection
import com.inmc.fishing.fight.Fight
import com.inmc.fishing.fight.FightSettings
import com.inmc.fishing.fight.FightOutcome
import com.inmc.fishing.fight.FishState
import com.inmc.fishing.ranking.FishingRanks
import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.server.PluginEnableEvent
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * PlaceholderAPI — `%inmcfishing_<키>%`.
 *
 * **2세대와 식별자·키·기본값이 같다.** 스코어보드·TAB·BetterHud 설정이 이 문자열을 그대로
 * 들고 있어서, 하나라도 다르면 그 서버의 HUD 가 오류 없이 빈칸이 된다.
 *
 * PlaceholderAPI 클래스는 compileOnly 라 설치되지 않은 서버에서 [FishingExpansion] 을 건드리는
 * 순간 `NoClassDefFoundError` 가 난다. [setup] 의 검사가 유일한 문이다.
 *
 * **늦게 켜지는 PlaceholderAPI 도 받는다** — 의존이 `load: OMIT` 라 적재 순서가 보장되지 않는다.
 * 2세대는 softdepend 로 순서를 강제했다.
 */
class PapiHook(private val fishing: Fishing) : Listener {

    private var expansion: FishingExpansion? = null

    fun setup() {
        if (expansion != null) return
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) return
        try {
            expansion = FishingExpansion(fishing).also { it.register() }
            fishing.logger.info("PlaceholderAPI 연동 활성화 (%${FishingPlaceholders.IDENTIFIER}_...%)")
        } catch (t: Throwable) {
            fishing.logger.warning("PlaceholderAPI 연동 실패: ${t.message}")
            expansion = null
        }
    }

    fun teardown() {
        expansion?.let { runCatching { it.unregister() } }
        expansion = null
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPluginEnable(event: PluginEnableEvent) {
        if (event.plugin.name == "PlaceholderAPI") setup()
    }
}

/**
 * 값을 만드는 부분. **Bukkit 도 PlaceholderAPI 도 모른다** — 서버 없이 2세대와 같은 키가
 * 같은 값을 내는지 테스트로 못박는다.
 *
 * 스코어보드 플러그인이 메인 밖에서 초당 여러 번 부른다. **메모리만 읽는다.**
 * 모르는 키는 null — PlaceholderAPI 가 원문을 남겨 관리자가 오타를 알아챈다. 빈 문자열을
 * 돌려주면 조용히 사라진다.
 */
object FishingPlaceholders {

    const val IDENTIFIER = "inmcfishing"

    /**
     * 힘겨루기 중이 아닐 때. HUD 가 이 값으로 조건이 안 맞아 **저절로 숨는다.**
     * `_max` 가 1 인 것은 게이지가 0 으로 나누지 않게 하려는 2세대 값이다.
     */
    fun idleFight(key: String): String? = when (key) {
        "active", "tension", "tension_percent", "stamina", "stamina_percent",
        "reel", "reel_percent", "distance", "distance_percent",
        "combo", "reel_combo", "danger", "distance_danger", "reel_danger",
        "state_percent" -> "0"
        "tension_max", "stamina_max", "reel_max", "distance_max" -> "1"
        "state_seconds" -> "0.0"
        "state" -> "none"
        "state_name", "state_display", "guide" -> ""
        "click" -> "NONE"
        else -> null
    }

    /** 2세대 `FishingPlaceholderExpansion.fightPlaceholder` 와 키·값이 같다. */
    fun fight(key: String, fight: Fight, hud: FightSettings.Hud): String? = when (key) {
        "active" -> "1"
        "state" -> fight.state.name.lowercase(Locale.ROOT)
        "state_name" -> hud.stateName(fight.state)
        "state_display" -> hud.stateColor(fight.state) + hud.stateName(fight.state)
        "state_seconds" -> fmt1(fight.ai.remainingTicks / 20.0)
        // 남은 시간 비율(0~100) — 카운트다운 게이지의 value(max=100 고정).
        "state_percent" -> fight.ai.stateDurationTicks.let { duration -> if (duration <= 0) "0" else fmt1(fight.ai.remainingTicks * 100.0 / duration) }
        "guide" -> hud.guide(fight.state).subtitle
        "click" -> when (fight.state.advice) {
            FishState.Advice.REEL -> "L"
            FishState.Advice.RELEASE -> "R"
            FishState.Advice.BALANCE -> "LR"
            FishState.Advice.WAIT -> "WAIT"
        }

        "tension" -> fmt1(fight.tension)
        "tension_max" -> fmt1(max(1.0, fight.lineStrength))
        "tension_percent" -> percentOf(fight.tension, fight.lineStrength)
        // 3 = 이미 한계에 닿아 유예 중(곧 실패). HUD 의 `== 2` 조건이 저절로 꺼지므로 일반 경고와
        // 최종 경고가 조건 하나로 갈린다 — 2세대와 같다.
        "danger" -> if (fight.inGrace(FightOutcome.LINE_SNAPPED)) "3" else {
            val ratio = fight.tension / max(1.0, fight.lineStrength)
            when {
                ratio >= hud.tensionDangerRatio -> "2"
                ratio >= hud.tensionWarningRatio -> "1"
                else -> "0"
            }
        }

        "stamina" -> fmt1(fight.stamina)
        "stamina_max" -> fmt1(max(1.0, fight.maxStamina))
        "stamina_percent" -> percentOf(fight.stamina, fight.maxStamina)

        "reel" -> fmt1(fight.reelState)
        "reel_max" -> fmt1(max(1.0, fight.maxReelState))
        "reel_percent" -> percentOf(fight.reelState, fight.maxReelState)
        "reel_danger" -> if (fight.inGrace(FightOutcome.REEL_BROKEN)) "3" else if (ratioPercent(fight.reelState, fight.maxReelState) < hud.reelDangerPercent) "2" else "0"

        "distance" -> fight.distance.roundToLong().toString()
        "distance_max" -> max(1.0, fight.maxDistance).roundToLong().toString()
        "distance_percent" -> percentOf(fight.distance, fight.maxDistance)
        "distance_danger" ->
            if (fight.inGrace(FightOutcome.DISTANCE_EXCEEDED)) "3" else if (ratioPercent(fight.distance, fight.maxDistance) >= hud.distanceDangerPercent) "2" else "0"

        "combo" -> fight.releaseCombo.toString()
        "reel_combo" -> fight.reelCombo.toString()
        else -> null
    }

    /**
     * 도감·랭킹 값. [collection] 이 null 이면(오프라인·기록 적재 중) 0 계열 — 2세대와 같다.
     *
     * @param isActive 아직 정의가 남아 있는 물고기인가. 지운 물고기의 칸은 최대치에서 뺀다
     *   (2세대 `ACTIVE` 항목 기준).
     */
    fun collection(key: String, collection: Collection?, totalFish: Int, isActive: (String) -> Boolean): String? {
        val entries = collection?.all().orEmpty()
        return when (key) {
            "collection_registered" -> registered(collection).toString()
            "collection_perfect" -> (collection?.perfectCount ?: 0).toString()
            "collection_discovered" -> (collection?.discoveredCount ?: 0).toString()
            "collection_slots" -> (collection?.totalRegisteredSlots ?: 0).toString()
            "collection_slots_max" -> entries.filter { isActive(it.fishId) }.sumOf { it.maxSlots }.toString()
            "collection_total" -> totalFish.toString()
            "collection_percent" -> percent(registered(collection).toLong(), totalFish.toLong())
            "trophy" -> (collection?.trophyCount ?: 0).toString()
            "rare_trophy" -> (collection?.rareTrophyCount ?: 0).toString()
            "total_caught" -> entries.sumOf { it.totalCaught.toLong() }.toString()
            else -> null
        }
    }

    /** 피로도. 유효 상한이 **손에 든 낚싯대**에 따라 달라지므로 온라인 전용이다. */
    fun fatigue(key: String, value: Int?, ceiling: Int?): String? = when (key) {
        "fatigue" -> (value ?: 0).toString()
        "fatigue_max" -> (ceiling ?: 0).toString()
        "fatigue_percent" -> if (value == null || ceiling == null) "0.0" else percent(value.toLong(), ceiling.toLong())
        else -> null
    }

    fun bestSize(collection: Collection?, fishId: String): String =
        fmt1(collection?.of(fishId)?.largestSize ?: 0.0)

    private fun registered(collection: Collection?): Int = collection?.all()?.count { it.isRegistered } ?: 0

    private fun fmt1(value: Double): String = String.format(Locale.ROOT, "%.1f", value)

    /** 경고 비교용 0~100. 문자열로 만들었다 다시 읽지 않는다. */
    private fun ratioPercent(value: Double, max: Double): Double =
        if (max <= 0.0) 0.0 else (value * 100.0 / max).coerceIn(0.0, 100.0)

    /** 게이지용. 0~100 으로 자른다. */
    private fun percentOf(value: Double, max: Double): String =
        if (max <= 0.0) "0" else fmt1(ratioPercent(value, max))

    /** 도감·피로도용. 자르지 않는다 — 물약으로 상한을 넘긴 피로도는 100 을 넘어 보인다. */
    private fun percent(value: Long, max: Long): String =
        if (max <= 0L) "0.0" else fmt1(value * 100.0 / max)
}

/** PlaceholderAPI 가 있을 때만 적재된다. 값은 전부 [FishingPlaceholders] 가 만든다. */
private class FishingExpansion(private val fishing: Fishing) : PlaceholderExpansion() {

    override fun getIdentifier(): String = FishingPlaceholders.IDENTIFIER

    override fun getAuthor(): String = "INMC"

    override fun getVersion(): String = fishing.plugin.pluginMeta.version

    /** false 면 `/papi reload` 뒤 확장이 버려지고 다시 등록할 계기가 없다. */
    override fun persist(): Boolean = true

    override fun onRequest(player: OfflinePlayer?, params: String): String? {
        if (player == null) return null
        val key = params.lowercase(Locale.ROOT)
        val online = player.player
        val angler = online?.let { fishing.anglers.of(it) }

        if (key.startsWith(FIGHT)) {
            val sub = key.substring(FIGHT.length)
            val fight = angler?.fight?.fight?.takeIf { !it.outcome.isOver }
                ?: return FishingPlaceholders.idleFight(sub)
            return FishingPlaceholders.fight(sub, fight, fishing.fight.hud)
        }

        // 물고기 id 는 원문 대소문자로 넘긴다. 도감이 알아서 소문자로 찾는다.
        if (key.startsWith(BEST_SIZE)) {
            return FishingPlaceholders.bestSize(angler?.collection, params.substring(BEST_SIZE.length))
        }

        return when (key) {
            "fatigue", "fatigue_max", "fatigue_percent" -> {
                if (online == null || angler == null) {
                    FishingPlaceholders.fatigue(key, null, null)
                } else {
                    val bonus = fishing.catches.rodOf(online)?.maxFatigue ?: 0
                    FishingPlaceholders.fatigue(key, angler.fatigue.value, angler.fatigue.ceiling(bonus))
                }
            }

            // 순위표는 전원을 들고 있으므로 오프라인도 답한다.
            "rank" -> (fishing.ranks.rankOf(fishing.fishingRanks.collection, player.uniqueId) ?: 0).toString()
            "score" -> (
                angler?.collection?.score()
                    ?: fishing.ranks.boardsOf(FishingRanks.COLLECTION_ID).season.entryOf(player.uniqueId)?.score
                    ?: 0L
                ).toString()

            else -> FishingPlaceholders.collection(key, angler?.collection, fishing.fish.all().size) {
                fishing.fish.get(it) != null
            }
        }
    }

    private companion object {
        const val FIGHT = "fight_"
        const val BEST_SIZE = "best_size_"
    }
}
