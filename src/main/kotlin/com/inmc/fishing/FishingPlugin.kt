package com.inmc.fishing

import com.inmc.fishing.config.FishingConfig
import com.inmc.fishing.config.Messages
import com.inmc.fishing.grade.GradeTable
import com.inmc.fishing.modifier.Modifiers
import com.inmc.fishing.region.RegionRules
import com.inmc.fishing.scheduler.FightTicker
import com.inmc.fishing.scheduler.Ticker
import org.bukkit.plugin.java.JavaPlugin

/**
 * 진입점. **배선만** 한다.
 *
 * 도메인 로직은 한 줄도 여기 두지 않는다. 2세대의 `InMcFishing` 은 383줄이었고 그 안에
 * 레지스트리 로딩·연동 탐지·명령어·리스너·스케줄러가 뒤섞여 있었다.
 */
class FishingPlugin : JavaPlugin() {

    private lateinit var fishing: Fishing
    private lateinit var ticker: Ticker
    private lateinit var fightTicker: FightTicker
    private lateinit var metrics: com.inmc.fishing.hook.MetricsHook
    private lateinit var papi: com.inmc.fishing.hook.PapiHook

    override fun onEnable() {
        fishing = Fishing(this)
        ticker = Ticker(fishing)
        fightTicker = FightTicker(fishing)
        metrics = com.inmc.fishing.hook.MetricsHook(fishing)
        papi = com.inmc.fishing.hook.PapiHook(fishing)

        fishing.mmoItems.setup()
        fishing.customItems.setup()
        fishing.economy.setup()

        registerListeners()
        com.inmc.fishing.command.FishingCommand(fishing).register(this)
        registerSignalCatalog()
        com.inmc.fishing.registry.FishingSettings.register()
        // 커스텀아이템에 낚싯대·미끼·물약·생선살·물고기 겉모습 역할을 내놓는다(core ItemRoles).
        for (role in com.inmc.fishing.registry.FishingRoles.roles(fishing)) kr.inmc.core.integration.ItemRoles.register(role)
        kr.inmc.core.integration.ItemRoles.listen(com.inmc.fishing.registry.FishingRoles.OWNER) { role ->
            if (!fishing.ready) return@listen
            for (store in listOf(fishing.fish, fishing.rodRegistry, fishing.baits, fishing.potions, fishing.fillets)) store.onRolesChanged(role)
        }

        reload {
            fishing.ranks.load {
                fishing.mailbox.load {
                    fishing.markReady()
                    ticker.start()
                    fightTicker.start()
                    metrics.start()
                    papi.setup()
                    for (player in server.onlinePlayers) fishing.anglers.join(player)
                    logger.info("inmc-fishing 활성화 완료")
                }
            }
        }
    }

    /**
     * "낚시가 쏘는 신호에는 이런 값이 온다" 를 core 에 알린다.
     *
     * **이게 없으면 업적 관리자가 물고기 id 를 손으로 타이핑한다.** 오타가 나면 그 업적은
     * 오류 없이 영영 완료되지 않고, 만든 사람은 이유를 모른다.
     *
     * 목록을 **람다로** 넘기는 것이 중요하다. 등록 시점에 떠서 넘기면 관리자가 GUI 로
     * 새로 만든 물고기가 저쪽 편집기에 안 나오고, 적재 순서상 지금은 비어 있을 수도 있다.
     */
    private fun registerSignalCatalog() {
        kr.inmc.core.event.SignalCatalog.register(
            source = SOURCE, type = "catch",
            subjectLabel = "물고기",
            subjects = { fishing.fish.all().map { it.id to it.label() } },
            dataKeys = listOf(
                "grade" to "등급", "trophy" to "트로피", "big" to "대어",
                "double" to "더블", "size" to "크기",
            ),
            description = "물고기를 낚았을 때",
        )
        kr.inmc.core.event.SignalCatalog.register(
            source = SOURCE, type = "register",
            subjectLabel = "물고기",
            subjects = { fishing.fish.all().map { it.id to it.label() } },
            dataKeys = listOf("grade" to "등급", "perfect" to "퍼펙트", "slots" to "등록 칸"),
            description = "도감에 등록했을 때 (낚는 것과 다른 사건)",
        )
        kr.inmc.core.event.SignalCatalog.register(
            source = SOURCE, type = "tournament",
            subjectLabel = "대회",
            subjects = { fishing.tournaments.all().map { it.id to it.name } },
            dataKeys = listOf("rank" to "순위", "score" to "점수"),
            description = "대회가 끝났을 때 (오프라인에게도 옵니다)",
        )
    }

    override fun onDisable() {
        if (!::fishing.isInitialized) return
        // 람다가 이 플러그인의 객체와 클래스로더를 붙들고 있다. 빼지 않으면 샌다.
        kr.inmc.core.event.SignalCatalog.unregisterAll(SOURCE)
        kr.inmc.core.integration.ItemRoles.unregisterAll(com.inmc.fishing.registry.FishingRoles.OWNER)
        com.inmc.fishing.registry.FishingSettings.unregister()
        ticker.stop()
        fightTicker.stop()
        metrics.stop()
        // 진행 중인 대회는 끝내고 보상을 준다 (2세대와 같다). 워커를 닫기 전에.
        fishing.tournamentHud.clear()
        fishing.tournaments.shutdown()
        papi.teardown()
        // 종료 경로에서는 워커를 못 믿는다. 여기서 바로 쓰고 내려간다.
        fishing.ranks.flushBlocking()
        fishing.mailbox.flushBlocking()
        fishing.fish.flushBlocking()
        // 커스텀아이템으로 옮긴 파일은 쓰지 않는다(쓰면 다음 시작에 또 옮긴다).
        for (store in listOf(fishing.rodRegistry, fishing.baits, fishing.potions, fishing.fillets)) if (!store.retired) store.flushBlocking()
        fishing.anglers.flushBlocking()
        fishing.io.shutdown()
    }

    private fun registerListeners() {
        val manager = server.pluginManager
        manager.registerEvents(com.inmc.fishing.listener.ChatInputListener(fishing), this)
        manager.registerEvents(com.inmc.fishing.listener.FishingListener(fishing), this)
        manager.registerEvents(kr.inmc.core.listener.MenuListener(fishing), this)
        manager.registerEvents(papi, this)
    }

    /**
     * 설정 넷과 정의 다섯을 다시 읽어 스냅샷을 교체한다.
     *
     * 파일 읽기는 워커에서, 파싱과 반영은 메인에서 한다 — core 의
     * [kr.inmc.core.config.ConfigService] 가 지키는 계약이다.
     *
     * **추첨기는 정의가 전부 읽힌 뒤에 만든다.** 먼저 만들면 물고기가 비어 있는 추첨기가
     * 잠깐 쓰이고, 그 사이에 던진 사람은 아무것도 못 낚는다.
     */
    fun reload(then: () -> Unit = {}) {
        // 커스텀아이템으로 옮긴 파일은 배포 기본값을 다시 깔지 않는다 — 깔면 다음 시작에 또 옮긴다.
        for (name in RESOURCES + COPY_ONLY) {
            if (kr.inmc.core.integration.ItemRoles.isRetired(fishing.io.file(name))) continue
            fishing.io.copyDefault(name, fishing.io.file(name))
        }

        fishing.io.async({
            RESOURCES.map { fishing.io.load(fishing.io.file(it)) }
        }) { loaded ->
            // List 의 구조분해는 다섯까지다. 여섯째부터는 번호로 읽는다.
            val (configYaml, messagesYaml, gradesYaml, modifiersYaml, worldGuardYaml) = loaded
            fishing.rodLore = com.inmc.fishing.rod.RodLoreFormat.load(loaded[5].getConfigurationSection("rod"))
            fishing.config = FishingConfig.from(configYaml)
            fishing.fight = com.inmc.fishing.fight.FightSettings.load(loaded[6])
            fishing.messages = Messages.from(messagesYaml)
            fishing.grades = GradeTable.load(gradesYaml.getConfigurationSection("grades"))
            fishing.modifiers = Modifiers.load(modifiersYaml)
            fishing.regionRules = RegionRules.load(worldGuardYaml.getConfigurationSection("regions")) {
                logger.warning(it)
            }

            // 정의 객체가 교체되므로 열린 화면은 닫는다 - 그대로 두면 버려진 객체를 계속
            // 편집하게 되고, 저장은 되는데 반영이 안 된다.
            closeOpenMenus()
            cancelSessions()

            loadDefinitions {
                // 커스텀아이템이 우리가 다 읽기 전에 꽂혔으면 그 알림은 지나갔다 — 여기서 한 번 따라잡는다(적재 순서는 보장되지 않는다).
                if (kr.inmc.core.integration.ItemRoles.active) {
                    for (store in listOf(fishing.fish, fishing.rodRegistry, fishing.baits, fishing.potions, fishing.fillets)) store.onRolesChanged(null)
                }
                fishing.rebuildEngine()
                fishing.anglers.rebindAll()
                // 2세대 ValidatorManager — 안 나오는 물고기를 적재 때마다 이름을 대고 알린다.
                for (problem in com.inmc.fishing.fish.FishReport.problems(fishing.fish.all(), fishing.grades)) {
                    logger.warning(problem)
                }
                then()
            }
        }
    }

    /**
     * 진행 중이던 판을 전부 접는다.
     *
     * 물고기·등급·힘겨루기 계수가 전부 교체되므로 그대로 두면 낡은 값으로 계산된다.
     * 낚싯바늘까지 치우는 것이 핵심이다 — 남겨두면 다음 우클릭이 감아올리기가 되어
     * 다음 판이 시작되지 않는다.
     */
    private fun cancelSessions() {
        for (player in server.onlinePlayers) {
            fishing.anglers.of(player)?.let { fishing.flow.cancel(player, it) }
        }
    }

    /**
     * 정의 파일들을 **차례로** 읽는다.
     *
     * 각 [kr.inmc.core.store.YamlFileStore.load] 가 워커에 한 번 갔다 오므로 콜백을 그대로
     * 중첩하면 다섯 겹이 된다. 목록을 훑는 재귀 한 줄이 같은 일을 하고, 파일이 늘어도 목록에
     * 한 줄만 더하면 된다.
     */
    private fun loadDefinitions(then: () -> Unit) {
        val stores = listOf(
            fishing.fish,
            fishing.tournaments,
            fishing.collectionRewards,
            fishing.rodRegistry,
            fishing.baits,
            fishing.potions,
            fishing.fillets,
        )

        fun step(index: Int) {
            if (index >= stores.size) {
                then()
                return
            }
            stores[index].load { step(index + 1) }
        }
        step(0)
    }

    /**
     * 이 플러그인이 띄운 화면을 전부 닫는다.
     *
     * core 가 소유한 화면(공용 확인창)도 잡아야 하므로 클래스가 아니라
     * [kr.inmc.core.gui.Menu.owner] 로 가려낸다.
     */
    private fun closeOpenMenus() {
        for (player in server.onlinePlayers) {
            val holder = player.openInventory.topInventory.holder
            if (holder !is kr.inmc.core.gui.Menu || holder.owner !== fishing) continue
            player.closeInventory()
        }
    }

    private companion object {
        /** core 의 신호 목록에서 이 플러그인을 가리키는 이름. */
        const val SOURCE = "fishing"

        /** 처음 한 번 깔아주고, 리로드 때 여기서 직접 읽는 것들. 순서가 곧 구조분해 순서다. */
        val RESOURCES = listOf("config.yml", "messages.yml", "grades.yml", "modifiers.yml", "worldguard.yml", "item-format.yml", "fight.yml")

        /** 깔기만 하고 읽기는 각자 [kr.inmc.core.store.YamlFileStore] 가 하는 것들. */
        val COPY_ONLY = listOf(
            "tournaments.yml",
            "collections.yml",
            "fish.yml",
            "rods.yml",
            "baits.yml",
            "potions.yml",
            "fillet-items.yml",
        )
    }
}
