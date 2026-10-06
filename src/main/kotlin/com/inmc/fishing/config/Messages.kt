package com.inmc.fishing.config

import com.inmc.fishing.util.Ph
import kr.inmc.core.config.MessageCatalog
import org.bukkit.configuration.file.YamlConfiguration

/**
 * `messages.yml` 한 벌.
 *
 * 읽고 보내는 부분은 전부 core 의 [MessageCatalog] 가 갖고 있다. 남는 것은 기본값 표뿐이고
 * 그게 이 플러그인의 도메인이다.
 *
 * 2세대 `messages.yml` 은 788줄이었다. 여기 담는 것은 **코드가 실제로 읽는 키**뿐이고,
 * `ResourceTest` 가 배포 파일과 이 표가 정확히 일치하는지 지킨다 — 한쪽에만 있는 키는
 * 관리자가 고쳐도 안 나오거나, 코드가 찾는데 파일에 없는 키다.
 */
class Messages(values: Map<String, String>) : MessageCatalog<Ph>(values, DEFAULTS) {

    companion object {

        fun from(config: YamlConfiguration): Messages = Messages(merge(DEFAULTS, config))

        val DEFAULTS: Map<String, String> = mapOf(
            PREFIX to "<gradient:#3fb6ff:#9be7ff>[ 낚시 ]</gradient> ",

            // --- 낚시 기본 ---------------------------------------------------------
            "bite" to "<yellow>입질이 왔습니다! 순서대로 클릭하세요.</yellow>",
            "caught" to "{grade} <white>{fish}</white> <gray>({size}cm)</gray> <green>를 낚았습니다!</green>",
            "caught-no-size" to "{grade} <white>{fish}</white> <green>를 낚았습니다!</green>",
            "escaped" to "<red>물고기가 도망갔습니다...</red>",
            "double" to "<aqua>운이 좋군요! 두 마리를 낚았습니다.</aqua>",
            "big-fish" to "<gold>대어! 이 종의 가장 큰 개체가 걸렸습니다 — 트로피 확정!</gold>",
            "inventory-full" to "<red>인벤토리에 빈 칸이 없습니다.</red>",
            "world-not-allowed" to "<red>이 월드에서는 낚시를 할 수 없습니다.</red>",
            "region-blocked" to "<red>이 구역에서는 낚시할 수 없습니다.</red>",
            "disabled" to "<red>낚시 기능이 꺼져 있습니다.</red>",

            // --- 미니게임 ----------------------------------------------------------
            "minigame-on" to "<yellow>자동낚시를 껐습니다. 직접 미니게임으로 낚습니다.</yellow>",
            "minigame-off" to "<green>자동낚시를 켰습니다. 입질 뒤 자동으로 낚입니다.</green>",
            "minigame-locked" to "<red>피로도가 부족해 자동 낚시를 할 수 없습니다.</red>",
            "manual-reel" to "<gray>찌를 거두었습니다. 자동 낚시를 끝냅니다.</gray>",

            // --- 트로피 ------------------------------------------------------------
            "trophy" to "<gold>트로피! <white>{fish}</white> <gray>({size}cm)</gray></gold>",
            "rare-trophy" to "<light_purple>레어 트로피! <white>{fish}</white> <gray>({size}cm)</gray></light_purple>",
            "trophy-broadcast" to "<gold>{player} 님이 {fish} 트로피({size}cm)를 낚았습니다!</gold>",
            "rare-trophy-broadcast" to "<light_purple>{player} 님이 {fish} 레어 트로피({size}cm)를 낚았습니다!</light_purple>",

            // --- 힘겨루기 ----------------------------------------------------------
            "fight-line-snapped" to "<red>줄이 끊겼네..</red>",
            "fight-distance" to "<red>줄 길이가 부족하네..</red>",
            "fight-reel-broken" to "<red>릴이 고장났네..</red>",
            "fight-timeout" to "<red>시간이 초과되어 놓쳤네..</red>",
            "title-success" to "&f잡았다!",
            "title-success-sub" to "&a{sequence}",
            "title-fail" to "&f물고기가 도망갔네..",
            "title-timeout" to "&f물고기가 도망갔네..",
            "title-fail-sub" to "&cL : 좌클릭 R : 우클릭",
            "fight-practice-win" to "<yellow>[연습모드]</yellow> <gray>파이트 연습을 완료했습니다. (보상 지급)</gray>",
            "practice-on-title" to "&e&l연습모드 ON",
            "practice-on-subtitle" to "&7일반 물고기로 연습 · 보상 지급",
            "practice-off-title" to "&7연습모드 OFF",
            "show-broadcast" to "<gold>[ 낚시 ]</gold> <white>{player}</white><gray> 님이 </gray>{grade} <white>{fish}</white><gray> ({size}cm) 을(를) 자랑합니다!</gray> {value}",
            "show-hold-fish" to "<red>자랑할 물고기를 손에 들어 주세요.</red>",
            "stats-header" to "<gray>────── <aqua>내 낚시 스탯</aqua> ──────</gray>",

            // --- 도감 --------------------------------------------------------------
            "collection-registered" to "<green>{fish} 를 도감에 등록했습니다. ({count}/{amount})</green>",
            "collection-full" to "<yellow>{fish} 도감이 이미 가득 찼습니다.</yellow>",
            "collection-perfect" to "<gold>{fish} 도감을 퍼펙트 완성했습니다!</gold>",
            "collection-need-item" to "<red>등록할 {fish} 가 없습니다.</red>",
            "collection-first" to "<aqua>{fish} 를 처음으로 도감에 등록했습니다!</aqua>",
            "collection-registered-many" to "<green>{fish} {count}개를 도감에 등록했습니다.</green>",
            "collection-grade-done" to "<green>{grade} 도감 일괄등록 완료: {count}개 등록했습니다.</green>",
            "collection-grade-empty" to "<yellow>등록할 물고기가 없습니다. (인벤·배낭·어망 확인)</yellow>",

            // --- 어망 --------------------------------------------------------------
            "net-full" to "<red>어망이 가득 찼습니다. ({amount}마리)</red>",
            "net-stored" to "<green>{fish} 를 어망에 넣었습니다.</green>",
            "net-stored-many" to "<green>물고기 {count}마리를 어망에 넣었습니다.</green>",
            "net-taken" to "<green>{fish} 를 어망에서 꺼냈습니다.</green>",
            "net-empty" to "<gray>어망이 비어 있습니다.</gray>",

            // --- 피로도 ------------------------------------------------------------
            "fatigue-status" to "<gray>피로도: <white>{count}</white> / {amount}</gray>",
            "fatigue-restored" to "<green>피로도를 {amount} 회복했습니다.</green>",
            "fatigue-unlocked" to "<green>피로도가 회복되어 다시 자동 낚시를 쓸 수 있습니다.</green>",
            "waiting" to "<green>물고기를 기다리는 중~</green> <white>{fish}</white>",
            "waiting-auto" to " <dark_gray>|</dark_gray> <white>자동 낚시 모드</white> <dark_gray>|</dark_gray> <aqua>피로도 {count}</aqua>",
            "auto-countdown" to "<yellow>{seconds} 초</yellow><gray> 후 낚음...</gray> {grade} <gray>{fish}</gray> <dark_gray>|</dark_gray> <aqua>피로도 {count}</aqua>",
            "unregistered-rod" to "<red>등록되지 않은 낚싯대로는 낚시할 수 없습니다.</red>",

            // --- 손질 --------------------------------------------------------------
            "fillet-started" to "<green>{fish} 손질을 시작했습니다. ({seconds}초)</green>",
            "fillet-started-many" to "<green>물고기 {count}마리를 손질대에 걸었습니다.</green>",
            "fillet-done" to "<green>손질이 끝났습니다. {fish} {amount}개를 받았습니다.</green>",
            "fillet-no-room" to "<red>손질대에 빈 자리가 없습니다.</red>",

            // --- 대회 --------------------------------------------------------------
            "tournament-started" to "<gold>{tournament} 가 시작되었습니다! <gray>사전 신청자는 자동으로 참가했습니다.</gray></gold>",
            "tournament-ended" to "<gold>{tournament} 가 끝났습니다.</gold>",
            "tournament-joined" to "<green>{tournament} 에 참가했습니다.</green>",
            "tournament-left" to "<yellow>{tournament} 에서 나왔습니다.</yellow>",
            "tournament-already-joined" to "<red>이미 참가 중입니다.</red>",
            "tournament-full" to "<red>참가 인원이 가득 찼습니다.</red>",
            "tournament-not-enough" to "<yellow>신청 인원이 모자라 {tournament} 가 열리지 않았습니다({amount}명). <gray>신청은 다음 대회로 이어집니다.</gray></yellow>",
            "tournament-not-running" to "<red>진행 중인 대회가 없습니다.</red>",
            "tournament-fee-short" to "<red>참가비가 부족합니다. ({amount})</red>",
            "tournament-result" to "<gold>{tournament} {rank}위 — {player} ({score})</gold>",
            "tournament-started-fee" to "<gold>{tournament} 가 시작되었습니다! <yellow>(참가비: {amount})</yellow> <gray>사전 신청자는 자동으로 참가했습니다.</gray></gold>",
            "tournament-applied" to "<green>{tournament} 에 사전 신청했습니다. 열리면 자동으로 참가합니다.</green>",
            "tournament-already-applied" to "<red>이미 사전 신청한 대회입니다.</red>",
            "tournament-not-applied" to "<red>신청하지 않은 대회입니다.</red>",
            "tournament-application-cancelled" to "<yellow>{tournament} 사전 신청을 취소했습니다.</yellow>",
            "tournament-not-participating" to "<red>참가 중인 대회가 아닙니다.</red>",
            "tournament-no-economy" to "<red>참가비를 처리할 Vault 이코노미가 연결되지 않았습니다.</red>",
            "tournament-not-found" to "<red>없는 대회입니다.</red>",
            "tournament-list-header" to "<gray>────── <aqua>낚시 대회</aqua> ──────</gray>",
            "tournament-list-line" to "<gray>· <white>{id}</white> {tournament} <dark_gray>({value})</dark_gray></gray>",
            "tournament-wins-header" to "<gray>────── <gold>대회 우승 순위</gold> ──────</gray>",
            "tournament-wins-line" to "<gray>{rank}. <white>{player}</white> — <gold>{count}회</gold></gray>",
            "tournament-no-wins" to "<dark_gray>아직 우승자가 없습니다.</dark_gray>",

            // --- 랭킹 --------------------------------------------------------------
            "rank-reward-received" to "<gold>{subject} {rank}위 보상을 받았습니다!</gold>",
            "rank-none" to "<gray>아직 순위에 오르지 않았습니다.</gray>",

            // --- 명령어 공통 --------------------------------------------------------
            "no-permission" to "<red>권한이 없습니다.</red>",
            "player-only" to "<red>이 명령어는 플레이어만 사용할 수 있습니다.</red>",
            "not-ready" to "<gray>플러그인이 아직 준비 중입니다. 잠시 후 다시 시도해주세요.</gray>",
            "usage" to "<gray>/낚시 <white>[도감|어망|랭킹|대회|피로도|손질|미니게임]</white></gray>",
            "reloaded" to "<green>설정을 다시 불러왔습니다.</green>",
            "unknown-fish" to "<red>'{fish}' 라는 물고기가 없습니다.</red>",
            "player-not-found" to "<red>'{player}' 을(를) 찾을 수 없습니다.</red>",
            "given" to "<green>{player} 에게 {fish} {amount}개를 지급했습니다.</green>",
            "hand-empty" to "<red>손에 아이템을 들고 있어야 합니다.</red>",
            "registered" to "<green>손에 든 아이템을 '{fish}' 로 등록했습니다.</green>",
            "unregistered" to "<green>'{fish}' 등록을 해제했습니다.</green>",

            // --- 프롬프트 (core ChatPrompt 가 요구하는 네 키) -------------------------
            "prompt-enter" to "<yellow>채팅으로 값을 입력하세요. <gray>(취소: 취소)</gray></yellow>",
            "prompt-cancelled" to "<gray>입력을 취소했습니다.</gray>",
            "prompt-timeout" to "<gray>입력 시간이 지났습니다.</gray>",
            "prompt-invalid-number" to "<red>숫자를 입력해주세요.</red>",
        )
    }
}
