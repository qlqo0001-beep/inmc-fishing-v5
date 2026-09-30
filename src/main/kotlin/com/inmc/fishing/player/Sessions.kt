package com.inmc.fishing.player

import com.inmc.fishing.fight.Fight
import com.inmc.fishing.fish.Catch
import com.inmc.fishing.minigame.ClickGame
import org.bukkit.entity.FishHook

/**
 * 입질부터 결과까지의 한 판.
 *
 * **추첨은 입질 순간에 이미 끝나 있다.** 미니게임은 그 결과를 받을지 말지를 정할 뿐이다.
 * 순서를 반대로 하면(미니게임 성공 뒤에 추첨) 클릭을 잘하는 것이 등급에 영향을 주지 않는데도
 * 플레이어는 영향을 준다고 믿게 되고, 무엇보다 미니게임 난이도가 등급에서 오므로 **추첨을
 * 먼저 해야 난이도를 정할 수 있다.**
 */
class BiteSession(
    val result: Catch,
    /** 미니게임. 자동 낚시면 null 이다. */
    val game: ClickGame?,
    val hook: FishHook?,
    val startedAt: Long,
    /** 자동 낚시일 때 결과가 나올 시각. 미니게임이면 0. */
    val autoCatchAt: Long = 0L,
) {

    val isAuto: Boolean get() = game == null

    fun isAutoDue(now: Long): Boolean = isAuto && now >= autoCatchAt

    /** 자동 낚시 액션바에 마지막으로 보인 남은 초. 초가 바뀔 때만 다시 보낸다(2세대는 1초마다). */
    var shownSecond: Long = -1L

    /** 미니게임 타이틀에 마지막으로 보인 것. 바뀔 때만 다시 보낸다. */
    var shownTitle: String? = null
}

/**
 * 힘겨루기 한 판. 계산은 [Fight](물고기의 마음 [Fight.ai] 포함)가 하고, 여기는 화면·낚싯바늘처럼 판에 딸린 것을 든다.
 * 클릭은 곧바로 [Fight.registerReel]/[Fight.registerRelease] 로 간다 — 2세대처럼 클릭이 "누르고 있음" 을 잠깐 남긴다.
 * W·S 도 같다 — 누른 순간이 클릭 하나, 누르고 있는 동안은 `hold-repeat-millis` 마다 한 번 더 누른 것으로 친다([FightKeys.hold]).
 */
class FightSession(
    val result: Catch,
    val fight: Fight,
    val hook: FishHook?,
    /** 연습 판. 이겨도 보상이 없다. */
    val practice: Boolean = false,
) {
    /** 판이 시작된 뒤 지난 틱. 파티클·상태 소리의 간격(`particle-interval` · `sound.interval`)을 잰다. */
    var ticks: Int = 0

    /** 장력 게이지 + 거리 숫자(2세대 보스바). vanilla 화면일 때만 만들고 `FishingFlow.finish` 가 치운다. */
    var bossBar: net.kyori.adventure.bossbar.BossBar? = null

    /** 지금 W(감기)·S(풀기)를 누르고 있는지 — `PlayerInputEvent` 가 바뀔 때마다 적는다. 누른 순간을 가려내려고 든다. */
    var forward: Boolean = false
    var backward: Boolean = false

    /** W·S 를 마지막으로 누른(또는 되풀이한) 시각 — 누르고 있으면 여기서부터 `hold-repeat-millis` 를 잰다([FightKeys.hold]). */
    var keyPressedAt: Long? = null
}

/**
 * 힘겨루기 시작 연출 한 판 — 2세대 `fight.yml` 의 `intro`.
 *
 * 스케줄러에 작업을 여러 개 걸지 않고 **힘겨루기 틱이 시각을 보고 단계를 넘긴다.** 걸어 둔
 * 작업은 접속 종료·리로드 때 하나씩 찾아 취소해야 하는데, 이렇게 두면 판을 접는
 * `FishingFlow.finish` 하나로 같이 사라진다.
 */
class IntroSession(
    val result: Catch,
    val hook: FishHook?,
    val practice: Boolean,
    val startedAt: Long,
) {
    /** 마지막으로 보여준 단계. 0 = 등장, 1~3 = 3·2·1, 4 = START. */
    var shownStep: Int = -1
}
