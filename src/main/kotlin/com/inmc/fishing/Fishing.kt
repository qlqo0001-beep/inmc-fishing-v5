package com.inmc.fishing

import com.inmc.fishing.bait.BaitRegistry
import com.inmc.fishing.catching.CatchService
import com.inmc.fishing.catching.FishingFlow
import com.inmc.fishing.collection.CollectionRewards
import com.inmc.fishing.collection.ScoreWeights
import com.inmc.fishing.config.FishingConfig
import com.inmc.fishing.config.Messages
import com.inmc.fishing.fish.FishRegistry
import com.inmc.fishing.fillet.FilletItemRegistry
import com.inmc.fishing.fillet.FilletService
import com.inmc.fishing.fish.RollEngine
import com.inmc.fishing.fish.TrophyRule
import com.inmc.fishing.grade.GradeTable
import com.inmc.fishing.modifier.Modifiers
import com.inmc.fishing.player.AnglerStore
import com.inmc.fishing.potion.PotionRegistry
import com.inmc.fishing.ranking.FishingRanks
import com.inmc.fishing.rod.CombinedRodSource
import com.inmc.fishing.rod.CustomItemRodSource
import com.inmc.fishing.rod.RodRegistry
import com.inmc.fishing.rod.RodSource
import com.inmc.fishing.tournament.TournamentService
import com.inmc.fishing.util.Ph
import kr.inmc.core.integration.CustomItemHook
import kr.inmc.core.integration.EconomyHook
import kr.inmc.core.integration.MMOItemsHook
import kr.inmc.core.input.ChatPrompt
import kr.inmc.core.item.ItemMatcher
import kr.inmc.core.item.ItemResolver
import kr.inmc.core.rank.RankService
import kr.inmc.core.rank.Rankable
import kr.inmc.core.reward.Mailbox
import kr.inmc.core.reward.RewardHost
import kr.inmc.core.reward.RewardService
import kr.inmc.core.reward.RewardSettings
import kr.inmc.core.util.Placeholders
import net.kyori.adventure.text.Component
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin

/**
 * 플러그인을 엮는 서비스 로케이터.
 *
 * 전부 한 번 만들고 `fishing.<서비스>` 로 닿는다. 리로드는 volatile 한 설정 스냅샷만 갈아끼우고
 * 서비스는 절대 다시 만들지 않는다 — 리스너·화면·진행 중인 힘겨루기가 낡은 참조를 들게 된다.
 *
 * [RewardHost] 를 구현한다. [kr.inmc.core.InmcHost] 보다 아홉 개를 더 요구하는 대신 core 의
 * 랭킹·보상·우편함이 통째로 딸려 온다. 2세대 랭킹에는 시즌도 보상도 없었으므로 **아무것도
 * 깨지지 않고**, 설정을 비워두면 예전과 똑같이 표시 전용으로 동작한다.
 */
class Fishing(override val plugin: JavaPlugin) : RewardHost {

    val logger: java.util.logging.Logger = plugin.logger

    override val io = kr.inmc.core.config.ConfigService(plugin)

    /** core 의 ChatPrompt·MenuListener·보상 서비스가 메시지를 보낼 때 쓰는 통로. */
    override fun tell(target: CommandSender, key: String, ph: Placeholders?) =
        messages.send(target, key, ph as? Ph)

    override fun messageComponent(key: String, ph: Placeholders?): Component =
        messages.component(key, ph as? Ph)

    /**
     * core 가 쓰는 의미 이름을 이 플러그인의 토큰으로 옮긴다.
     * core 는 `{물고기}` 를 모르고 알 필요도 없다 — `subject` 라고만 말한다.
     */
    override fun placeholders(vararg pairs: Pair<String, String>): Placeholders {
        val ph = Ph.of()
        for ((name, value) in pairs) when (name) {
            "subject" -> ph.fish(value)
            "player" -> ph.player(value)
            "item" -> ph.fish(value)
            "rank" -> ph.rank(value.toIntOrNull() ?: 0)
            "season" -> ph.count(value.toIntOrNull() ?: 0)
            "count" -> ph.count(value.toIntOrNull() ?: 0)
            else -> ph.raw(name, value)
        }
        return ph
    }

    // --- 연동 (전부 선택) --------------------------------------------------------
    val mmoItems = MMOItemsHook(logger)
    val customItems = CustomItemHook(logger)
    override val economy = EconomyHook(logger)
    val worldGuard = com.inmc.fishing.hook.WorldGuardHook(logger)

    // --- 아이템 계층 --------------------------------------------------------------
    override val itemResolver = ItemResolver(mmoItems, customItems, logger)
    val itemMatcher = ItemMatcher(mmoItems, customItems)

    // --- 설정 (리로드로 통째 교체) --------------------------------------------------
    @Volatile
    var config: FishingConfig = FishingConfig.from(YamlConfiguration())

    /** 힘겨루기 — `fight.yml`(2세대와 같은 파일). */
    @Volatile
    var fight: com.inmc.fishing.fight.FightSettings = com.inmc.fishing.fight.FightSettings()

    @Volatile
    var messages: Messages = Messages.from(YamlConfiguration())

    override val rewardSettings: RewardSettings get() = config

    // --- core 의 보상·랭킹 ----------------------------------------------------------
    override val mailbox = Mailbox(this)
    override val rewards = RewardService(this)
    override val ranks = RankService(this)

    val fishingRanks = FishingRanks(ScoreWeights.DEFAULT)

    override val rankables: List<Rankable> get() = fishingRanks.all()

    // --- 정의 레지스트리 -----------------------------------------------------------
    val fish = FishRegistry(this)
    val baits = BaitRegistry(this)
    val potions = PotionRegistry(this)
    val fillets = FilletItemRegistry(this)

    /**
     * `rods.yml` 을 읽고 쓰는 쪽. **로케이터가 들고 있어야 하는 것은 이쪽**이다 —
     * 공급처를 갈아끼워도 설정 파일에 적힌 낚싯대는 그대로 읽고 저장해야 한다.
     */
    val rodRegistry = RodRegistry(this)

    /**
     * 낚싯대 공급처. **바꿔 끼울 수 있는 자리다.**
     *
     * 기본값은 **커스텀아이템 우선, `rods.yml` 차선**이다. 아이템 플러그인으로 옮겨가는
     * 중인 서버가 실제 상황이라 — 예전 낚싯대가 아직 사람들 손에 있는데 새것부터 저쪽에서
     * 만들기 시작한다 — 둘 중 하나만 되면 그 이행 기간에 한쪽이 통째로 죽는다.
     *
     * [CustomItemRodSource] 는 커스텀아이템 플러그인을 **컴파일 시점에 알지 않는다.**
     * core 의 [kr.inmc.core.integration.CustomItemHook] 에게 "이 참조에 딸린 값"만 묻고,
     * 아무도 안 꽂혀 있으면 빈 맵이 와서 자연히 `rods.yml` 만 남는다.
     *
     * 통째로 다른 것을 끼우고 싶으면 [RodSource] 구현 하나를 여기 넣으면 된다 —
     * 추첨·힘겨루기·로어·GUI 는 전부 이 인터페이스만 보고 있다.
     */
    @Volatile
    var rods: RodSource = rodRegistry

    // --- 추첨 (리로드와 목록 변경으로 통째 교체) ------------------------------------------
    @Volatile
    var grades: GradeTable = GradeTable(emptyList())

    @Volatile
    var modifiers: Modifiers = Modifiers.load(YamlConfiguration())

    /** `worldguard.yml`. 구역별 낚시 금지와 등급 배수. */
    @Volatile
    var regionRules: com.inmc.fishing.region.RegionRules = com.inmc.fishing.region.RegionRules.NONE

    /** `item-format.yml`. 지급하는 낚싯대의 능력 로어 서식. */
    @Volatile
    var rodLore: com.inmc.fishing.rod.RodLoreFormat = com.inmc.fishing.rod.RodLoreFormat()

    /**
     * 추첨기. 등급표·물고기 목록·확률 설정의 **스냅샷**이라 셋 중 하나가 바뀌면 다시 만들어야 한다.
     *
     * 매번 새로 만들지 않는 이유는 [RollEngine] 이 등급별 묶음을 들고 있기 때문이다 — 낚을
     * 때마다 만들면 전체 목록을 매번 훑게 된다.
     */
    @Volatile
    var engine: RollEngine = RollEngine(GradeTable(emptyList()), emptyMap(), TrophyRule.DEFAULT, 0.0, 0.0)
        private set

    // --- 진행 -----------------------------------------------------------------------

    /** 접속한 사람들의 도감·어망·피로도·손질대. */
    val anglers = AnglerStore(this)

    /** 낚시 한 번의 판정과 지급. 모든 낚시 경로가 여기로 모인다. */
    val catches = CatchService(this)

    val tournaments = TournamentService(this)
    val tournamentHud = com.inmc.fishing.tournament.TournamentHud(this)

    /** 입질에서 지급까지의 진행. */
    val flow = FishingFlow(this)

    /** 힘겨루기 중 제자리에 묶기(서버가 꺼져도 풀린다). */
    val movement = com.inmc.fishing.fight.FightMovement(logger)

    /** 손질대. */
    val fillet = FilletService(this)

    /** 도감 보상 다섯 갈래. */
    val collectionRewards = CollectionRewards(this)

    /** 등급표·물고기·확률 중 하나라도 바뀌면 부른다. */
    fun rebuildEngine() {
        engine = RollEngine(
            grades = grades,
            fishByGrade = fish.grouped(),
            trophyRule = config.trophyRule,
            bigFishChance = config.bigFishChance,
            doubleChance = config.doubleChance,
        )
    }

    // --- 입력 --------------------------------------------------------------------
    val prompts = ChatPrompt(this)

    /** 저장된 상태를 다 읽기 전에는 false. 그 전의 낚시는 전부 보류한다. */
    @Volatile
    var ready: Boolean = false
        private set

    fun markReady() {
        ready = true
    }
}
