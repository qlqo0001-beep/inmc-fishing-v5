package com.inmc.fishing.catching

import com.inmc.fishing.Fishing
import com.inmc.fishing.bait.Bait
import com.inmc.fishing.fish.Bonuses
import com.inmc.fishing.fish.Catch
import com.inmc.fishing.fish.FishLore
import com.inmc.fishing.fish.FishStamp
import com.inmc.fishing.fish.Rolls
import com.inmc.fishing.fish.Trophy
import com.inmc.fishing.modifier.Modifiers
import com.inmc.fishing.player.Angler
import com.inmc.fishing.rod.Rod
import com.inmc.fishing.rod.RodData
import kr.inmc.core.integration.CustomEnchantHook
import kr.inmc.core.item.ItemRef
import kr.inmc.core.item.StoredItem
import org.bukkit.Material
import com.inmc.fishing.util.Ph
import kr.inmc.core.util.Text
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.Random

/** [Rolls] 의 실제 구현. 테스트는 정해진 값을 주는 다른 구현을 쓴다. */
class RandomRolls(private val random: Random = Random()) : Rolls {
    override fun next(): Double = random.nextDouble()
    override fun gaussian(): Double = random.nextGaussian()
}

/**
 * 낚시 한 번의 **판정과 지급**.
 *
 * 리스너는 "언제"만 알고, "무엇을 주는가"는 전부 여기서 정한다. 그렇게 나눠두면 자동 낚시와
 * 미니게임 낚시가 같은 길로 결과를 받고, 관리자 명령으로 강제 지급하는 것도 같은 길을 탄다 —
 * 세 곳에 같은 지급 코드를 두면 언젠가 한 곳만 도감을 안 올린다.
 */
class CatchService(private val fishing: Fishing) {

    private val rolls = RandomRolls()

    // --- 장비 -----------------------------------------------------------------------

    /**
     * 지급할 낚싯대 아이템. 장식(이름·설명) 뒤에 **능력 로어**(`item-format.yml`)를 붙인다 —
     * 2세대와 같다. 명령어 지급과 관리 화면의 "나에게 지급" 이 같은 길을 쓴다.
     */
    fun rodStack(rod: Rod, amount: Int): ItemStack? {
        val stack = fishing.itemResolver.create(rod.item, amount) ?: return null
        rod.decor.applyTo(stack)
        val ability = fishing.rodLore.lines(rod, fishing.grades.ordered)
        if (ability.isEmpty()) return stack
        // 기존 로어 **뒤에** 붙인다. 덮어쓰면 손에 든 것을 등록한 낚싯대(스냅샷·MMOItems)의
        // 원래 설명이 사라진다.
        val meta = stack.itemMeta ?: return stack
        meta.lore(meta.lore().orEmpty() + ability.map { Text.renderFlat(it) })
        stack.itemMeta = meta
        return stack
    }

    /**
     * 주 손의 낚싯대. 등록되지 않은 바닐라 낚싯대면 null 이다.
     *
     * 낚싯대에 붙은 **커스텀 인첸트의 낚시 수치**(core `CustomEnchantHook`)를 더해서 돌려준다.
     * 등록 안 된 바닐라 낚싯대라도 인첸트가 있고 서버가 그런 낚싯대를 허락하면(`allow-unregistered-vanilla-rod`)
     * 빈 낚싯대 위에 얹는다 — 허락하지 않는 서버에서 인첸트가 낚싯대 등록을 우회하면 안 된다.
     */
    fun rodOf(player: Player): Rod? {
        val stack = player.inventory.itemInMainHand
        val registered = fishing.rods.identify(stack)
        val enchanted = CustomEnchantHook.data(stack)
        if (!RodData.hasFishingKeys(enchanted)) return registered
        val base = registered ?: if (stack.type == Material.FISHING_ROD && fishing.config.allowUnregisteredRod) VANILLA else return null
        return RodData.plus(base, enchanted)
    }

    /** 왼손(오프핸드)의 미끼. */
    fun baitOf(player: Player): Bait? = fishing.baits.identify(player.inventory.itemInOffHand)

    /**
     * 지금 이 자리의 보정 전부.
     *
     * **축마다 결합 방식이 다르고 그게 의도다.** 환경은 곱하고(월드·바이옴·날씨·시간·권한),
     * 장비는 더한다(낚싯대·미끼). 장비를 곱셈으로 하면 가중치 1 인 S 등급에 배수를 줘봐야
     * 1 이 늘 뿐이라 "좋은 낚싯대가 희귀한 것을 낚게 해준다"는 약속이 성립하지 않는다.
     */
    fun bonusesOf(player: Player, rod: Rod?, bait: Bait?): Bonuses {
        val flat = HashMap<String, Double>()
        rod?.gradeBonus?.forEach { (grade, value) -> flat[grade] = (flat[grade] ?: 0.0) + value }
        bait?.gradeBonus?.forEach { (grade, value) -> flat[grade] = (flat[grade] ?: 0.0) + value }

        // 구역 배수도 환경이다 — 다섯 축과 같이 곱한다 (2세대 WeightCalculator 5-2).
        val multipliers = HashMap(fishing.modifiers.multipliers(situationOf(player)))
        for ((grade, factor) in fishing.regionRules.multipliers(player.world.name, regionsOf(player))) {
            multipliers[grade] = (multipliers[grade] ?: 1.0) * factor
        }

        return Bonuses(
            gradeMultipliers = multipliers,
            gradeFlatBonus = flat,
            bigFishChance = rod?.bigFishChance ?: 0.0,
            doubleChance = rod?.doubleChance ?: 0.0,
            sizePercent = (bait?.sizePercent ?: 0.0) + (rod?.sizePercent ?: 0.0),
            sizeFixed = bait?.sizeFixed ?: 0.0,
        )
    }

    /**
     * Bukkit 세계를 [Modifiers.Situation] 값으로 옮긴다.
     *
     * 권한은 **설정에 적힌 노드만** 확인한다. 전부 훑을 방법도 없고, 훑을 수 있어도 낚을
     * 때마다 수백 번 물어보게 된다.
     */
    fun situationOf(player: Player): Modifiers.Situation {
        val world = player.world
        val held = player.location.block.biome
        val ticks = world.time
        return Modifiers.Situation(
            world = world.name,
            biome = held.key().value(),
            weather = when {
                world.isThundering -> "THUNDER"
                world.hasStorm() -> "RAIN"
                else -> "CLEAR"
            },
            // 12000~23999 가 밤이다. 바닐라 기준 그대로다.
            time = if (ticks in 12_000..23_999) "NIGHT" else "DAY",
            permissions = fishing.modifiers.permissionNodes().filter { player.hasPermission(it) }.toSet(),
        )
    }

    /**
     * 미니게임을 켜고 끈다. 명령어와 메인 화면이 같이 쓴다.
     *
     * **끄는 쪽만 막는다.** 피로도가 바닥이면 자동 낚시를 못 하지만, 켜는 것은 언제나 된다 —
     * 잠긴 사람이 직접 낚는 길까지 막으면 회복할 방법이 사라진다.
     *
     * @return 바뀌었으면 true.
     */
    fun setMinigame(player: Player, angler: Angler, on: Boolean): Boolean {
        if (!fishing.config.allowMinigameToggle) {
            fishing.messages.send(player, "no-permission")
            return false
        }
        if (!on && angler.fatigue.locked) {
            fishing.messages.send(player, "minigame-locked")
            return false
        }
        angler.minigameEnabled = on
        fishing.anglers.markDirty(angler)
        fishing.messages.send(player, if (on) "minigame-on" else "minigame-off")
        return true
    }

    /**
     * "내 낚시 스탯" — 2세대 `MainGui.buildStatsLines`. 메인 화면의 머리 아이콘과 `/낚시 스탯` 이
     * 같은 줄을 쓴다.
     *
     * 힘겨루기 수치는 **기본값 + 손에 든 낚싯대 보너스**, 피로도 상한과 회복량도 낚싯대를 반영한다.
     */
    fun statsLines(player: Player, angler: Angler): List<String> {
        val rod = rodOf(player)
        val stats = fishing.fight.stats
        val fatigue = fishing.config.fatigue
        val rodName = rod?.let { it.decor.name.ifBlank { it.item.label() }.ifBlank { it.id } } ?: "인식된 낚싯대 없음"
        return buildList {
            add("<gray>현재 낚싯대: <white>$rodName</white></gray>")
            add("<yellow>릴 파워: <white>" + fmt(stats.defaultReelPower + (rod?.reelPower ?: 0.0)) + "</white></yellow>")
            add("<yellow>줄 강도: <white>" + fmt(stats.defaultLineStrength + (rod?.lineStrength ?: 0.0)) + "</white></yellow>")
            add("<yellow>릴 내구성: <white>" + fmt(stats.defaultReelDurability + (rod?.reelDurability ?: 0.0)) + "</white></yellow>")
            add(
                "<aqua>피로도: <white>" + angler.fatigue.value + "</white><gray> / </gray><white>" +
                    angler.fatigue.ceiling(rod?.maxFatigue ?: 0) + "</white></aqua>",
            )
            add(
                "<aqua>자연회복량: <white>" + (fatigue.recoveryAmount + (rod?.fatigueRecovery ?: 0)) +
                    "</white><gray> / </gray><white>" + fatigue.recoveryIntervalSeconds + "초마다</white></aqua>",
            )
            if (rod == null) {
                add("<gold>등급 추가 출현확률: </gold><gray>등록된 낚싯대 없음</gray>")
            } else {
                val bonus = fishing.grades.ordered.joinToString("<gray>, </gray>") { grade ->
                    "<white>" + grade.id.uppercase() + "</white><gray>+</gray><white>" +
                        fmt(rod.gradeBonus[grade.id] ?: 0.0) + "</white>"
                }
                add("<gold>등급 추가 출현확률: </gold>$bonus")
            }
        }
    }

    private fun fmt(value: Double): String =
        if (value == Math.floor(value)) value.toLong().toString() else String.format("%.1f", value)

    /** `worldguard.yml` 이 비어 있으면 WorldGuard 에 묻지도 않는다. */
    private fun regionsOf(player: Player): Set<String> =
        if (fishing.regionRules.isEmpty) emptySet() else fishing.worldGuard.regionsAt(player.location)

    /** `worldguard.yml` 에서 `fishing-enabled: false` 인 구역 안이 아닌가. */
    fun isRegionAllowed(player: Player): Boolean =
        fishing.regionRules.isFishingEnabled(player.world.name, regionsOf(player))

    /** 한 번 추첨한다. 등록된 물고기가 없으면 null. */
    fun roll(player: Player, rod: Rod?, bait: Bait?): Catch? =
        fishing.engine.roll(rolls, bonusesOf(player, rod, bait))

    /**
     * 자동 낚시(미니게임 끄기)로 나올 수 있는 결과인지.
     *
     * 안 되는 등급이 나오면 **다시 뽑지 않고 버린다.** 다시 뽑으면 허용 등급만 남을 때까지
     * 돌게 되어, 자동 낚시가 확률을 무시하고 허용 등급을 균등하게 뱉는다.
     */
    fun isAutoAllowed(result: Catch): Boolean = fishing.config.isAutoCatchable(result.grade.id)

    // --- 지급 -----------------------------------------------------------------------

    /**
     * 결과를 실제로 준다. **모든 낚시 경로가 여기로 모인다.**
     *
     * 순서에 의미가 있다.
     * 1. 아이템 먼저 — 이게 실패하면(인벤토리 가득) 기록도 남기지 않는다. 기록만 남고
     *    물고기가 없으면 "낚았다는데 없다"가 된다.
     * 2. 도감 → 랭킹 → 대회 순. 랭킹은 도감의 파생이라 도감이 먼저다.
     * 3. 피로도는 마지막. 자동 낚시일 때만 깎는다.
     */
    fun grant(player: Player, angler: Angler, result: Catch, auto: Boolean): Boolean {
        val amount = if (result.double) 2 else 1
        // 자동 입망이 켜져 있으면 가방 대신 어망에 바로 넣는다. 다 들어갈 때만 — 일부만 들어가면
        // 나머지가 가방으로 가서 어디에 뭐가 있는지 헷갈린다. 어망이 차면 가방으로(기존 길).
        val netted = netStore(player, angler, result, amount)
        if (!netted && !give(player, result, amount)) {
            fishing.messages.send(player, "inventory-full")
            return false
        }

        val now = System.currentTimeMillis()
        announce(player, result)

        val entry = angler.collection.getOrCreate(result.fish.id, result.grade.id, result.fish.maxSlots)
        repeat(amount) { entry.recordCatch(result.size, result.trophy, now) }

        publishRanks(angler)
        fishing.tournaments.record(player, result, amount, now)

        if (auto && !saves(rodOf(player)?.fatigueSaveChance ?: 0.0)) {
            angler.fatigue.consume(result.grade.id)
            if (angler.fatigue.locked) fishing.messages.send(player, "minigame-locked")
        }

        runCommands(player, result)
        fishing.anglers.markDirty(angler)
        publishSignal(player, result, amount)
        // 바닐라 통계 "잡은 물고기"에도 한 번 센다(바닐라도 한 번 낚을 때 1). 이 플러그인이 바닐라
        // 낚기를 가로채므로 두면 통계가 영영 0 이고, 그 통계를 보는 것 — 업적 기본 정의의 `첫 낚시` 등 —
        // 이 조용히 멈춘다. 실서버에서 낚은 뒤에도 `첫 낚시` 가 0/1 로 남아 드러났다.
        player.incrementStatistic(org.bukkit.Statistic.FISH_CAUGHT)
        return true
    }

    /**
     * "이 사람이 이걸 낚았다" 를 core 의 신호로 알린다.
     *
     * **이 플러그인은 누가 듣는지 모른다.** 업적이든 통계든, 듣는 쪽이 하나도 없으면
     * [kr.inmc.core.event.InmcSignalEvent.fire] 가 **이벤트 객체조차 만들지 않는다** —
     * 그래서 평소 비용이 0 이다.
     *
     * 지급이 확정된 **뒤**에 있는 것이 중요하다. 인벤토리가 가득 차 실패한 낚시는 위에서
     * 이미 돌아갔으므로 여기 오지 않는다.
     *
     * 크기는 **여기서 형식을 고정한다.** `42.10000000001` 을 그대로 보내면 받는 쪽의
     * 문자열 비교가 영원히 안 맞고, 조용히 안 맞는다.
     */
    private fun publishSignal(player: Player, result: Catch, amount: Int) {
        kr.inmc.core.event.InmcSignalEvent.fire(
            source = "fishing",
            type = "catch",
            playerId = player.uniqueId,
            subject = result.fish.id,
            amount = amount.toLong(),
            player = player,
        ) {
            mapOf(
                "fish" to result.fish.id,
                "grade" to result.grade.id,
                "trophy" to result.trophy.name,
                "big" to result.bigFish.toString(),
                "double" to result.double.toString(),
                "size" to String.format("%.1f", result.size),
            )
        }
    }

    /**
     * 낚은 물고기를 어망에 바로 넣는다. 전부 들어갈 때만 true — 일부만 들어가면 나머지가
     * 가방으로 가서 어디에 뭐가 있는지 헷갈린다. 설정이 꺼져 있으면 손대지 않고 false.
     *
     * 도감·랭킹·대회 기록은 받는 쪽(grant)이 하므로 여기서는 넣기만 한다.
     */
    fun netStore(player: Player, angler: Angler, result: Catch, amount: Int): Boolean {
        if (!kr.inmc.core.integration.PlayerSettings.enabled(player, com.inmc.fishing.registry.FishingSettings.AUTO_NET, true)) {
            return false
        }
        if (angler.net.capacity > 0 && angler.net.size + amount > angler.net.capacity) {
            fishing.messages.send(player, "net-full", Ph.of().amount(angler.net.capacity))
            return false
        }
        val now = System.currentTimeMillis()
        repeat(amount) {
            if (!angler.net.add(FishStamp(result.fish.id, result.grade.id, result.size, result.trophy, now))) {
                return false
            }
        }
        fishing.anglers.markDirty(angler)
        return true
    }

    /** 아이템을 만들어 준다. 만들 수 없으면(참조 해제 실패) false. */
    fun give(player: Player, result: Catch, amount: Int): Boolean {
        val stamp = FishStamp(
            fishId = result.fish.id,
            gradeId = result.grade.id,
            size = result.size,
            trophy = result.trophy,
            caughtAt = System.currentTimeMillis(),
        )
        val stack = fishing.fish.stack(result.fish, amount, FishLore.of(stamp, result.grade.tag()))
            ?: return false
        stamp.applyTo(stack)

        if (fishing.config.requireEmptySlot && player.inventory.firstEmpty() < 0) return false

        val left = player.inventory.addItem(stack)
        if (left.isEmpty()) return true
        if (!fishing.config.dropOverflow) return false
        for (overflow in left.values) player.world.dropItem(player.location, overflow)
        return true
    }

    /** 랭킹은 도감의 **파생**이다. 멱등하므로 낚을 때마다 그대로 다시 올린다. */
    fun publishRanks(angler: Angler) {
        fishing.fishingRanks.publish(
            playerId = angler.id,
            playerName = angler.name,
            collectionData = angler.collection,
            fishName = { id -> fishing.fish.get(id)?.label() ?: id },
        ) { rankable, score, record ->
            fishing.ranks.put(rankable, angler.id, angler.name, score, record, record)
        }
    }

    // --- 알림 -----------------------------------------------------------------------

    private fun announce(player: Player, result: Catch) {
        val ph = Ph.of()
            .player(kr.inmc.core.integration.TitleForgeNames.displayName(player.uniqueId, player.name))
            .fish(result.fish.label())
            .grade(result.grade.tag())
            .size(result.size)

        fishing.messages.send(player, if (result.fish.hasSize) "caught" else "caught-no-size", ph)
        if (result.double) fishing.messages.send(player, "double", ph)
        if (result.bigFish) fishing.messages.send(player, "big-fish", ph)

        when (result.trophy) {
            Trophy.NORMAL -> {
                fishing.messages.send(player, "trophy", ph)
                broadcast("trophy-broadcast", ph)
            }

            Trophy.RARE -> {
                fishing.messages.send(player, "rare-trophy", ph)
                broadcast("rare-trophy-broadcast", ph)
            }

            Trophy.NONE -> Unit
        }
    }

    /** 트로피는 서버 전체에 알린다. 낚은 사람에게는 이미 따로 보냈으므로 빼고 보낸다. */
    private fun broadcast(key: String, ph: Ph) {
        if (!fishing.config.broadcastRewards) return
        val component = fishing.messages.component(key, ph)
        for (online in Bukkit.getOnlinePlayers()) online.sendMessage(component)
    }

    private fun runCommands(player: Player, result: Catch) {
        if (result.fish.commands.isEmpty()) return
        val console = Bukkit.getConsoleSender()
        for (raw in result.fish.commands) {
            val line = raw.replace("{player}", player.name)
                .replace("{플레이어}", player.name)
                .replace("{fish}", result.fish.id)
                .replace("{size}", String.format("%.1f", result.size))
            runCatching { Bukkit.dispatchCommand(console, line) }
                .onFailure { fishing.logger.warning("낚시 명령어 실행 실패: " + line + " - " + it.message) }
        }
    }

    // --- 회복 물약 -------------------------------------------------------------------

    /**
     * 손에 든 것이 피로회복 물약이면 한 개 쓰고 회복한다.
     *
     * @return 물약을 썼으면 true. 그때 부르는 쪽이 이벤트를 취소해야 한다.
     */
    fun drinkPotion(player: Player, stack: ItemStack?): Boolean {
        val potion = fishing.potions.identify(stack) ?: return false
        val angler = fishing.anglers.of(player) ?: return false
        if (potion.amount <= 0) return false

        val wasLocked = angler.fatigue.locked
        angler.fatigue.restore(potion.amount)
        fishing.anglers.markDirty(angler)
        // 2세대 `fatigue.unlocked` — 풀린 순간을 알린다. 안 알리면 계속 잠긴 줄 안다.
        if (wasLocked && !angler.fatigue.locked) fishing.messages.send(player, "fatigue-unlocked")

        stack?.let { it.amount = it.amount - 1 }
        fishing.messages.send(player, "fatigue-restored", Ph.of().amount(potion.amount))
        player.sendActionBar(
            Text.render(
                "<gray>피로도 <white>" + angler.fatigue.value + "</white> / " +
                    fishing.config.fatigue.max + "</gray>",
            ),
        )
        return true
    }

    /** 확률(%) 한 번. 절약 계열(미끼·피로)이 쓴다. */
    fun saves(chance: Double): Boolean = chance > 0.0 && rolls.next() * 100.0 < chance

    private companion object {
        /** 등록 안 된 바닐라 낚싯대. 인첸트 수치를 얹을 바탕이다. */
        val VANILLA = Rod("vanilla", StoredItem(ItemRef.Vanilla(Material.FISHING_ROD), Material.FISHING_ROD))
    }
}
