package com.inmc.fishing

import com.inmc.fishing.collection.Collection
import com.inmc.fishing.collection.CollectionEntry
import com.inmc.fishing.fatigue.FatigueConfig
import com.inmc.fishing.fatigue.PlayerFatigue
import com.inmc.fishing.fight.Fight
import com.inmc.fishing.fight.FightSettings
import com.inmc.fishing.fish.Trophy
import com.inmc.fishing.hook.FishingPlaceholders
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 2세대 `%inmcfishing_*%` 와 같은가.
 *
 * 5세대가 처음에 이 확장을 **통째로 빠뜨렸다.** 2세대 배포 설정이 `hud.mode: betterhud` 라
 * 힘겨루기 화면 전체가 이 값들로 그려지고 있었고, 그대로 옮겼으면 그 서버의 HUD 는
 * **오류 없이 빈칸**이 됐다. 문자열 하나만 달라도 같은 일이 나므로 키와 기본값을 고정한다.
 */
class PlaceholderParityTest {

    /** 2세대 `FishingPlaceholderExpansion.fightPlaceholder` 의 세션 없음 표. */
    private val idleFight = mapOf(
        "active" to "0", "tension" to "0", "tension_percent" to "0", "stamina" to "0",
        "stamina_percent" to "0", "reel" to "0", "reel_percent" to "0", "distance" to "0",
        "distance_percent" to "0", "combo" to "0", "reel_combo" to "0", "danger" to "0",
        "distance_danger" to "0", "reel_danger" to "0", "state_percent" to "0",
        "tension_max" to "1", "stamina_max" to "1", "reel_max" to "1", "distance_max" to "1",
        "state_seconds" to "0.0", "state" to "none",
        "state_name" to "", "state_display" to "", "guide" to "", "click" to "NONE",
    )

    private fun fight() = Fight(FightSettings(), "d", rare = false, startedAt = 0L, random = java.util.Random(1))
    private val hud = FightSettings.Hud()

    private val collectionKeys = listOf(
        "collection_registered", "collection_perfect", "collection_discovered", "collection_slots",
        "collection_slots_max", "collection_total", "collection_percent",
        "trophy", "rare_trophy", "total_caught",
    )

    @Test
    fun `식별자가 2세대와 같다`() {
        assertEquals("inmcfishing", FishingPlaceholders.IDENTIFIER)
    }

    @Test
    fun `힘겨루기가 없을 때의 값이 글자 단위로 같다`() {
        for ((key, value) in idleFight) assertEquals(value, FishingPlaceholders.idleFight(key), key)
    }

    @Test
    fun `힘겨루기 중에는 2세대의 모든 키에 답한다`() {
        val fight = fight()
        for (key in idleFight.keys) assertNotNull(FishingPlaceholders.fight(key, fight, hud), key)
        assertEquals("1", FishingPlaceholders.fight("active", fight, hud))
        assertEquals("normal_move", FishingPlaceholders.fight("state", fight, hud))
    }

    @Test
    fun `상태 카운트다운과 할 일이 2세대처럼 나온다`() {
        // 5세대가 처음에는 상태에 길이가 없어 0 을 줬다 — BetterHud 의 카운트다운 게이지가 늘 비어 있었다.
        val fight = fight()
        val remaining = fight.ai.remainingTicks
        assertTrue(remaining > 0)
        assertEquals(String.format(java.util.Locale.ROOT, "%.1f", remaining / 20.0), FishingPlaceholders.fight("state_seconds", fight, hud))
        assertEquals("100.0", FishingPlaceholders.fight("state_percent", fight, hud), "막 시작한 상태는 남은 시간이 전부다")
        assertEquals("&7천천히 감으며 장력 확인!", FishingPlaceholders.fight("guide", fight, hud))
        assertEquals("&f이동 중", FishingPlaceholders.fight("state_display", fight, hud))
    }

    @Test
    fun `모르는 키는 null 이라 원문이 남는다`() {
        // 빈 문자열을 돌려주면 오타가 조용히 사라진다.
        assertNull(FishingPlaceholders.idleFight("tension_pct"))
        assertNull(FishingPlaceholders.fight("tension_pct", fight(), hud))
        assertNull(FishingPlaceholders.collection("collection_pct", null, 0) { true })
        assertNull(FishingPlaceholders.fatigue("fatigue_pct", 1, 1))
    }

    @Test
    fun `기록이 없으면 도감 값은 0 계열이다`() {
        for (key in collectionKeys) {
            val expected = if (key == "collection_percent") "0.0" else "0"
            val total = if (key == "collection_total") "0" else expected
            assertEquals(total, FishingPlaceholders.collection(key, null, 0) { true }, key)
        }
        assertEquals("0.0", FishingPlaceholders.bestSize(null, "tuna"))
    }

    @Test
    fun `오프라인 피로도는 0 계열이다`() {
        assertEquals("0", FishingPlaceholders.fatigue("fatigue", null, null))
        assertEquals("0", FishingPlaceholders.fatigue("fatigue_max", null, null))
        assertEquals("0.0", FishingPlaceholders.fatigue("fatigue_percent", null, null))
    }

    @Test
    fun `피로도 상한은 낚싯대 보너스를 더하되 절대 상한을 넘지 않는다`() {
        // 2세대 getEffectiveMax = min(절대 상한, 기본 + 낚싯대). 회복과 같은 식이어야 한다.
        val fatigue = PlayerFatigue(FatigueConfig(default = 2000, max = 2500))
        assertEquals(2000, fatigue.ceiling())
        assertEquals(2300, fatigue.ceiling(300))
        assertEquals(2500, fatigue.ceiling(900))
    }

    @Test
    fun `도감 수치를 그대로 옮긴다`() {
        val collection = Collection()
        collection.put(
            CollectionEntry("tuna", "a", maxSlots = 3).apply {
                recordCatch(42.0, Trophy.NORMAL, 0L)
                register(42.0, 0L)
            },
        )
        collection.put(CollectionEntry("gone", "a", maxSlots = 5).apply { recordCatch(1.0, Trophy.NONE, 0L) })

        val active = { id: String -> id == "tuna" }
        assertEquals("1", FishingPlaceholders.collection("collection_registered", collection, 4, active))
        assertEquals("2", FishingPlaceholders.collection("collection_discovered", collection, 4, active))
        assertEquals("1", FishingPlaceholders.collection("collection_slots", collection, 4, active))
        // 지운 물고기의 칸은 최대치에서 빠진다 (2세대 ACTIVE 기준).
        assertEquals("3", FishingPlaceholders.collection("collection_slots_max", collection, 4, active))
        assertEquals("25.0", FishingPlaceholders.collection("collection_percent", collection, 4, active))
        assertEquals("2", FishingPlaceholders.collection("total_caught", collection, 4, active))
        assertEquals("42.0", FishingPlaceholders.bestSize(collection, "TUNA"))
    }

    @Test
    fun `위험도는 설정한 문턱을 따른다`() {
        val fight = fight()
        // 시작 장력은 0, 릴은 가득, 거리는 100/150.
        assertEquals("0", FishingPlaceholders.fight("danger", fight, hud))
        assertEquals("0", FishingPlaceholders.fight("reel_danger", fight, hud))
        assertEquals("0", FishingPlaceholders.fight("distance_danger", fight, hud))
        assertEquals("2", FishingPlaceholders.fight("distance_danger", fight, FightSettings.Hud(distanceDangerPercent = 60.0)))
        assertEquals("150", FishingPlaceholders.fight("distance_max", fight, hud))
    }

    @Test
    fun `콤보는 입력 종류별로 나뉘고 첫 입력이 1 이다`() {
        val fight = fight()
        fight.registerRelease(1_000L)
        assertEquals("1", FishingPlaceholders.fight("combo", fight, hud))
        assertEquals("0", FishingPlaceholders.fight("reel_combo", fight, hud))
        fight.registerRelease(1_050L)
        assertEquals("2", FishingPlaceholders.fight("combo", fight, hud))

        fight.registerReel(1_100L)
        assertEquals("0", FishingPlaceholders.fight("combo", fight, hud))
        assertEquals("1", FishingPlaceholders.fight("reel_combo", fight, hud))
    }

    @Test
    fun `보스바 제목 서식은 2세대 배포값과 같다`() {
        val v2 = "거리: {distance} / {max_distance} M"
        assertEquals(v2, FightSettings.Hud().bossBarTitleFormat, "키를 지웠을 때의 기본값")

        val stream = javaClass.classLoader.getResourceAsStream("fight.yml")
        assertNotNull(stream)
        val deployed = stream.use {
            org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                java.io.InputStreamReader(it, Charsets.UTF_8),
            )
        }
        assertEquals(v2, FightSettings.load(deployed).hud.bossBarTitleFormat)
    }
}
