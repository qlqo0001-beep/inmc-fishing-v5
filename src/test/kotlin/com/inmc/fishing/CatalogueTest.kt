package com.inmc.fishing

import com.inmc.fishing.bait.Bait
import com.inmc.fishing.fillet.FilletItem
import com.inmc.fishing.fish.Fish
import com.inmc.fishing.fish.TrophyRule
import com.inmc.fishing.fish.Trophy
import com.inmc.fishing.grade.GradeTable
import com.inmc.fishing.potion.Potion
import com.inmc.fishing.rod.Rod
import com.inmc.fishing.tournament.TournamentDef
import kr.inmc.core.item.ItemRef
import kr.inmc.core.store.DefinitionKey
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 배포 아이템 목록을 플러그인이 기동 때 읽는 방식 그대로 읽어본다.
 *
 * 이 파일들은 손으로 옮겨 적은 것이라 **한 줄만 어긋나도 그 항목이 통째로 사라진다.**
 * 오류는 나지 않는다 — 읽히지 않은 물고기는 그냥 안 나올 뿐이고, 그걸 알아차리려면
 * 며칠 낚시를 해봐야 한다.
 */
class CatalogueTest {

    private fun load(path: String): YamlConfiguration {
        val stream = javaClass.classLoader.getResourceAsStream(path)
        assertNotNull(stream, "리소스를 찾을 수 없습니다: $path")
        return stream.use {
            YamlConfiguration.loadConfiguration(InputStreamReader(it, StandardCharsets.UTF_8))
        }
    }

    private fun entries(path: String, root: String): List<Pair<String, ConfigurationSection>> {
        val section = load(path).getConfigurationSection(root)
        assertNotNull(section, "$path 에 $root 절이 없습니다")
        return section.getKeys(false).map { key ->
            key to assertNotNull(section.getConfigurationSection(key), "$key 가 섹션이 아닙니다")
        }
    }

    private val grades: GradeTable by lazy {
        GradeTable.load(load("grades.yml").getConfigurationSection("grades"))
    }

    // --- 물고기 --------------------------------------------------------------------

    @Test
    fun `배포 물고기가 한 마리도 빠짐없이 읽힌다`() {
        val rows = entries("fish.yml", "fish")
        assertEquals(70, rows.size, "7등급 × 10종이어야 합니다")

        for ((key, section) in rows) {
            val grade = section.getString("grade").orEmpty()
            val fish = Fish.load(key, grade, section, defaultSlots = 10)
            assertNotNull(fish, "$key 를 읽지 못했습니다 - item 값을 확인하세요")
            assertTrue(DefinitionKey.isValid(key), "$key 는 등록 이름 규칙에 맞지 않습니다")
        }
    }

    @Test
    fun `배포 물고기의 등급이 전부 등급표에 있다`() {
        // 없는 등급을 적으면 그 물고기는 어느 묶음에도 안 들어가 **영영 추첨되지 않는다.**
        for ((key, section) in entries("fish.yml", "fish")) {
            val grade = section.getString("grade").orEmpty()
            assertNotNull(grades[grade], "$key 의 등급 '$grade' 가 grades.yml 에 없습니다")
        }
    }

    @Test
    fun `등급마다 뽑힐 수 있는 물고기가 있다`() {
        // 가중치가 전부 0 인 등급이 나오면 추첨기가 "아무거나" 로 떨어진다. 배포본이
        // 그 상태면 안 된다.
        val byGrade = entries("fish.yml", "fish").groupBy { it.second.getString("grade").orEmpty() }

        for (grade in grades.ordered) {
            val rows = byGrade[grade.id].orEmpty()
            assertTrue(rows.isNotEmpty(), "${grade.id} 등급에 물고기가 없습니다")
            assertTrue(
                rows.any { it.second.getInt("weight", 0) > 0 },
                "${grade.id} 등급의 가중치가 전부 0 입니다",
            )
        }
    }

    @Test
    fun `크기 있는 물고기는 트로피가 될 수 있다`() {
        // 평균이 최대에 너무 가까우면 `평균 × 1.45` 가 최대를 넘어 **트로피가 불가능**하다.
        // 대어 판정이 그런 물고기를 걸러내므로 조용히 대어가 안 나오게 된다.
        val rule = TrophyRule.load(load("config.yml").getConfigurationSection("rates"))

        for ((key, section) in entries("fish.yml", "fish")) {
            val fish = Fish.load(key, section.getString("grade").orEmpty(), section, 10)!!
            if (!fish.hasSize) continue
            assertTrue(rule.canBeTrophy(fish.size), "$key 는 트로피가 될 수 없는 크기 범위입니다")
        }
    }

    @Test
    fun `크기 없는 물고기는 더블이 꺼져 있다`() {
        // 장화를 두 짝 주는 것은 보상이 아니다.
        for ((key, section) in entries("fish.yml", "fish")) {
            val fish = Fish.load(key, section.getString("grade").orEmpty(), section, 10)!!
            if (fish.hasSize) continue
            assertTrue(!fish.doubleEnabled, "$key 는 크기가 없는데 더블이 켜져 있습니다")
        }
    }

    // --- 장비 ----------------------------------------------------------------------

    @Test
    fun `배포 낚싯대가 전부 읽히고 등급 보너스가 등급표와 맞는다`() {
        val rows = entries("rods.yml", "rods")
        assertTrue(rows.size >= 5, "낚싯대가 너무 적습니다: ${rows.size}")

        for ((key, section) in rows) {
            val rod = Rod.load(key, section)
            assertNotNull(rod, "$key 를 읽지 못했습니다")
            for (gradeId in rod.gradeBonus.keys) {
                assertNotNull(grades[gradeId], "$key 의 보너스 등급 '$gradeId' 가 등급표에 없습니다")
            }
        }
    }

    @Test
    fun `낚싯대가 위로 갈수록 강해진다`() {
        // 배포본 열 종은 사다리다. 중간이 뒤집히면 상점 가격표와 어긋난다.
        val rods = entries("rods.yml", "rods").map { (key, section) -> Rod.load(key, section)!! }
        val totals = rods.map { it.gradeBonus.values.sum() + it.reelPower + it.lineStrength }

        assertEquals(
            totals.sorted(),
            totals,
            "낚싯대 성능이 파일 순서대로 올라가지 않습니다",
        )
    }

    @Test
    fun `배포 미끼가 전부 읽힌다`() {
        for ((key, section) in entries("baits.yml", "baits")) {
            val bait = Bait.load(key, section)
            assertNotNull(bait, "$key 를 읽지 못했습니다")
            for (gradeId in bait.gradeBonus.keys) {
                assertNotNull(grades[gradeId], "$key 의 보너스 등급 '$gradeId' 가 등급표에 없습니다")
            }
        }
    }

    // --- 물약 ----------------------------------------------------------------------

    @Test
    fun `배포 물약이 등급마다 하나씩 있고 회복량이 커진다`() {
        val potions = entries("potions.yml", "potions").map { (key, section) ->
            assertNotNull(Potion.load(key, section), "$key 를 읽지 못했습니다")
        }

        for (grade in grades.ordered) {
            assertTrue(potions.any { it.gradeId == grade.id }, "${grade.id} 등급 물약이 없습니다")
        }

        val order = grades.ordered.map { g -> potions.first { it.gradeId == g.id }.amount }
        assertEquals(order.sorted(), order, "등급이 올라가는데 회복량이 늘지 않습니다")
    }

    @Test
    fun `물약의 표시 이름과 식별 이름이 따로 적혀 있다`() {
        // 한 키로 쓰면 색코드가 들어간 이름과 실제 평문 이름이 달라 **식별이 조용히 깨진다** —
        // 물약을 마셨는데 아무 일도 안 일어난다.
        for ((key, section) in entries("potions.yml", "potions")) {
            val plain = section.getString("name").orEmpty()
            assertTrue(plain.isNotBlank(), "$key 에 식별용 name 이 없습니다")
            assertTrue(!plain.contains('&'), "$key 의 name 에 색코드가 들어 있습니다: $plain")
        }
    }

    // --- 생선살 --------------------------------------------------------------------

    @Test
    fun `등급과 트로피 조합 스물한 칸이 전부 채워져 있다`() {
        // 비면 한 단계 아래로 내려가고, 보통 칸까지 비면 손질했는데 아무것도 안 나온다.
        val fillets = entries("fillet-items.yml", "fillets").map { (key, section) ->
            assertNotNull(FilletItem.load(key, section), "$key 를 읽지 못했습니다")
        }
        assertEquals(21, fillets.size, "7등급 × 3종이어야 합니다")

        for (grade in grades.ordered) {
            for (trophy in Trophy.entries) {
                assertTrue(
                    fillets.any { it.gradeId == grade.id && it.trophy == trophy },
                    "${grade.id} 등급의 ${FilletItem.token(trophy)} 결과물이 없습니다",
                )
            }
        }
    }

    @Test
    fun `생선살이 2세대 표기를 쓰지 않는다`() {
        // `normal` 은 2세대에서 "트로피가 **아님**" 이었다. 우리 코드는 그렇게 읽으므로
        // 그대로 두면 트로피 전용 결과물이 평범한 물고기에 나온다.
        for ((key, section) in entries("fillet-items.yml", "fillets")) {
            val raw = section.getString("trophy-type").orEmpty()
            assertTrue(raw in listOf("none", "trophy", "rare"), "$key 의 trophy-type 이 '$raw' 입니다")
        }
    }

    // --- 대회 ----------------------------------------------------------------------

    @Test
    fun `배포 대회가 전부 읽히고 최소 인원이 둘 이상이다`() {
        val rows = entries("tournaments.yml", "tournaments")
        assertTrue(rows.isNotEmpty(), "대회가 하나도 없습니다")

        for ((key, section) in rows) {
            val def = TournamentDef.load(key, section)
            assertNotNull(def, "$key 를 읽지 못했습니다")
            assertTrue(
                def.minPlayers >= 2,
                "$key 의 최소 인원이 ${def.minPlayers} 입니다 - 혼자 열어 1등 보상을 받을 수 있습니다",
            )
            assertTrue(def.maxPlayers >= def.minPlayers, "$key 의 최대 인원이 최소보다 적습니다")
        }
    }

    @Test
    fun `자동 시작 대회는 일정이 있다`() {
        // auto-start 만 켜고 schedule 을 안 적으면 **영영 안 열린다.** 오류도 안 난다.
        for ((key, section) in entries("tournaments.yml", "tournaments")) {
            val def = TournamentDef.load(key, section)!!
            if (!def.autoStart) continue
            assertTrue(
                def.schedule != TournamentDef.Schedule.NONE,
                "$key 는 auto-start 인데 schedule 이 없습니다",
            )
            assertNotNull(
                def.nextStart(java.time.LocalDateTime.now()),
                "$key 의 다음 시작 시각을 계산할 수 없습니다",
            )
        }
    }

    // --- 공통 ----------------------------------------------------------------------

    @Test
    fun `모든 배포 아이템이 참조를 갖고 있다`() {
        // 참조도 스냅샷도 없으면 지급할 수가 없다. 배포본은 전부 바닐라 참조여야 한다.
        val files = mapOf(
            "fish.yml" to "fish",
            "rods.yml" to "rods",
            "baits.yml" to "baits",
            "potions.yml" to "potions",
            "fillet-items.yml" to "fillets",
        )

        for ((file, root) in files) {
            for ((key, section) in entries(file, root)) {
                val ref = ItemRef.parse(section.getString("item"))
                assertTrue(ref is ItemRef.Vanilla, "$file 의 $key 가 바닐라 참조가 아닙니다: $ref")
            }
        }
    }

    @Test
    fun `배포 fight yml 은 코드 기본값과 같다 — 키를 지워도 같은 값`() {
        val loaded = com.inmc.fishing.fight.FightSettings.load(load("fight.yml"))
        val defaults = com.inmc.fishing.fight.FightSettings()

        assertFalse(loaded.hud.betterHud, "배포값은 vanilla — 보스바·액션바·상태 타이틀(2세대 1.6.0)")
        // 배포본은 상태 이름을 적어 두고 코드는 enum 이름으로 되짚는다 — 보이는 이름이 같으면 된다.
        assertEquals(defaults.hud, loaded.hud.copy(stateNames = emptyMap()))
        for (state in com.inmc.fishing.fight.FishState.entries) assertEquals(defaults.hud.stateName(state), loaded.hud.stateName(state), "$state 이름")
        assertEquals(defaults.general, loaded.general)
        assertEquals(defaults.ai.states, loaded.ai.states)
        for ((key, table) in defaults.ai.transitions) {
            assertEquals(table.entries, loaded.ai.transitions.getValue(key).entries, "전이표 $key")
        }
        assertEquals(
            defaults.ai.copy(states = emptyMap(), transitions = emptyMap()),
            loaded.ai.copy(states = emptyMap(), transitions = emptyMap()),
        )
        assertEquals(defaults.actionPower, loaded.actionPower)
        assertEquals(defaults.sound, loaded.sound)
        assertEquals(defaults.intro, loaded.intro)
        assertEquals(defaults.calc, loaded.calc)
        assertEquals(defaults.input, loaded.input)
        assertEquals(defaults.stats, loaded.stats)
        assertEquals(defaults.lineTangle, loaded.lineTangle)
    }

    @Test
    fun `배포 설정에 도감 기본 칸 수가 있다`() {
        assertTrue(load("config.yml").getInt("collection.default-max-slots", 0) > 0)
    }

    @Test
    fun `배포 물고기와 등급으로는 적재 경고가 하나도 없다`() {
        // 2세대 ValidatorManager 가 찍던 경고. 배포본에서 나오면 새 서버가 처음부터 경고를 본다.
        val fish = entries("fish.yml", "fish").map { (key, section) ->
            Fish.load(key, section.getString("grade").orEmpty(), section, 10)!!
        }
        assertEquals(emptyList(), com.inmc.fishing.fish.FishReport.problems(fish, grades))
    }

    @Test
    fun `모르는 등급과 가중치 0 은 경고한다`() {
        val fish = entries("fish.yml", "fish").first().let { (key, section) -> Fish.load(key, "zz", section, 10)!! }
        val problems = com.inmc.fishing.fish.FishReport.problems(listOf(fish), grades)
        assertTrue(problems.any { it.contains("zz") }, problems.toString())
        assertTrue(problems.any { it.contains("나올 수 있는 물고기가 없습니다") })
    }
}
