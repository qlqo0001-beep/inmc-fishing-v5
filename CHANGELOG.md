# inmc-fishing 변경 기록

날짜는 작업한 날입니다. 설계 이유는 `CLAUDE.md` 의 규칙 번호로 적습니다.

---

## 2026-10-01 — 힘겨루기: BetterHud 와 타이틀이 겹치던 것 · W·S 를 꾹 누르면 콤보가 쌓이게

- 사용자: 힘겨루기 중 상태 타이틀과 BetterHud 패널이 같이 떠서 겹친다 → `hud.mode: betterhud` 면 상태 타이틀도 보내지 않는다
  (보스바·액션바와 같다). 입질 미니게임 타이틀(09-27 결정)은 그대로
- 사용자: W 를 꾹 누르면 콤보가 쌓이며 감겨야 하는데 한 번만 눌린다 — 누르고 있는 동안은 "감는 중" 만 이어 가고 콤보는 누른 순간에만
  올랐다. `input.hold-repeat-millis`(기본 150ms)마다 한 번 더 누른 것으로 친다(`FightKeys.hold`) — 1.5초 쥐면 콤보 10. S 도 같다.
  판 전부터 쥔 키는 되풀이하지 않는다(한 번 떼고 눌러야). 테스트 추가

## 2026-09-30 — 대회가 인원 미달로 안 열릴 때

- 사용자: "대회가 자동으로 실패하면 에러 로그가 뜬다" — 확인하니 예외가 아니라 "자동 시작을 건너뜁니다 — 신청 0/2명" 안내 줄(INFO)이 매번 찍힌 것.
  **아무도 신청하지 않았으면 조용히** 다음 시각으로 넘기고, 신청자가 있으면 콘솔에 한 줄 + **신청자에게 알림**(`tournament-not-enough`
  — 신청과 참가비는 다음 대회로 이어진다. 전에는 아무 말 없이 넘어갔다)

## 2026-09-27 — 힘겨루기 타이틀 · 아이템 그림

- **힘겨루기(좌클릭 감기 · 우클릭 풀기)를 2세대처럼 가운데 타이틀로** — 상태 · 남은 초 · 할 일(`hud.state-guide`)을 매 틱.
  `hud.mode: betterhud` 여도 타이틀은 뜬다(보스바·액션바만 BetterHud 몫). 배포값 `hud.mode` 는 `vanilla`(2세대 1.6.0 과 같다) —
  보스바·액션바까지 그린다. **이미 깔린 `fight.yml` 은 덮지 않으므로** 보스바·액션바가 필요하면 그 파일의 `mode` 를 `vanilla` 로.
  입질 뒤 L/R 순서 미니게임은 이미 2세대 `TimeBarMiniGame` 과 같은 타이틀이다(아래 "옮기지 않은 것" 표의 "액션바" 줄은 낡은 기록)
- **힘겨루기 W·S 키**(감기·풀기, 좌·우클릭과 함께) — 쥐고 있으면 계속, 두드리면 연타. W 를 쥔 채 S 를 눌렀다 떼면(또는 반대) 떼지 않고
  바로 방향이 바뀐다(`FightKeys` — 전에는 쥐고 있던 키를 떼고 다시 눌러야 했다). 상태 타이틀의 할 일에 `좌클릭·W` · `우클릭·S` 를 적었다
  (이미 깔린 `fight.yml` 의 `state-guide` 문구는 그대로이니 바꾸려면 그 파일을 고치거나 지워서 다시 받는다)
- **물고기 68 · 생선살 21 · 미끼 11 · 낚싯대 10 의 그림**은 커스텀아이템이 들고 있다(`inmc-customitems` 의 `role-appearance.yml`).
  낚시가 옮겨 준 아이템에 그림·등급이 저절로 입혀진다 — 낚시 쪽 코드는 바뀌지 않았다(커스텀아이템 규칙 2). 커스텀아이템이 없으면 지금처럼 바닐라 모양

## 2026-09-26 — 트로피 힘겨루기를 2세대 원본대로

5세대의 힘겨루기는 2세대를 옮긴 것이 아니라 새로 짠 것이었다(물고기 AI·수식·입력·난이도가 전부 달랐다). 원본 그대로 되돌렸다.

- **설정은 `fight.yml`**(2세대와 같은 파일·키·값) — `config.yml` 의 `fight:` 절은 없어졌다. 옛 `fight.yml` 을 그대로 넣어도 된다
- **물고기 AI**: 상태마다 힘·저항·지속 시간(`ai.states`) · **행동력**(등급별, 레어는 2배) · 체력 구간 × 행동력 구간 **전이표** ·
  **위험**(체력이 낮은데 감고 있으면 강하게 저항) · 발악 → 탈진 → `after-exhausted` · 줄 엉킴(기본 꺼짐) · 체력 0 → 기절
- **수식**: 2세대 `FightCalculator` 그대로 — 감을 때 거리 = 도망 − (기본 회수 + 지칠수록 붙는 회수), 낚싯대 릴 파워는 거리에 0.1 비율로만,
  안 감으면 체력 회복, 장력은 상태별 상승률, 등급별 **최소 거리**(체력이 남은 동안), 최대 거리 = 기본 + 줄 강도 보너스
- **난이도**: 등급 배수(f 0.5 … s 3.0) × 레어 1.5 — 5세대는 등급 배수가 아예 없었고 힘·저항을 제곱근으로 줄였다
- **입력**: 클릭 하나가 0.3초 "누르고 있음", 우클릭 연타 콤보만큼 장력이 풀린다. 감기·풀기는 서로를 끈다
- **시간 정지**: 물고기를 한 번 완전히 지치게 하면 제한 시간이 멈춘다. 실패 유예는 이유마다 따로
- **화면**: 상태 타이틀(상태 · 남은 초)과 할 일(`hud.state-guide`)을 매 틱, 보스바(장력·거리), 액션바(`hud.actionbar-format`).
  배포값 `hud.mode: betterhud`(2세대와 같다) — BetterHud 가 그린다. `%inmcfishing_fight_state_seconds%`·`_state_percent`·`_guide` 가
  2세대 값을 준다(5세대는 0·빈칸이었다)
- **소리·파티클**: 상태별 소리(`sound.state`)·파티클, 물고기가 지치는 순간·이긴 순간 소리
- **결과 문구**: 2세대 `fail.*` 그대로("줄이 끊겼네..", "줄 길이가 부족하네..", "릴이 고장났네..", "시간이 초과되어 놓쳤네..").
  시작 안내·"힘겨루기에 성공했습니다" 문구는 2세대에 없어 뺐다
- **제자리 묶기**: 걷기·날기 속도 0 + 비행(2세대와 같다), 서버가 꺼져도 접속 때 되돌린다(`infishing:fight_movement`, 2세대 표시도 읽는다).
  월드 이동·순간이동·죽음·아이템 버리기·단축바 바꾸기는 판을 접는다
- `FightGoldenTest` — 2세대 원본 클래스로 뽑은 틱별 기록 10판(모든 상태·결과·시간 정지)과 우리 계산을 매 틱 대조한다

## 2026-09-26 — 유령 좌클릭

- 우클릭 한 번이 미니게임에서 "우 + 좌" 로 들어가 틀리던 것 — 낚싯대를 쓰는 우클릭 뒤 같은 틱에 손 흔들기가 `LEFT_CLICK_AIR` 로
  한 번 더 왔다. 힘겨루기에서는 풀기가 감기로 덮였다. core `Clicks.isGhost` 로 거른다(2세대 `lastRightClickTick` 과 같은 규칙)

## 2026-09-25 — 커스텀아이템 연동

- 커스텀아이템이 있으면 **낚싯대·미끼·물약·생선살**은 커스텀아이템 역할에서 온다(`rods`·`baits`·`potions`·`fillet-items.yml` 은 처음 한 번
  옮기고 `.migrated`). id 는 지킨다. **물고기는 겉모습만** 커스텀아이템으로(확률·크기·등급은 `fish.yml` 그대로). 낚시 관리 화면에서
  고쳐도 커스텀아이템에 적힌다. 커스텀아이템 물고기는 이름·툴팁을 그대로 두고 크기·등급 줄만 덧붙인다

## 2026-09-24 — 허공 클릭 결함

- **고친 것: 피로회복 물약이 허공 우클릭으로는 안 마셔지던 결함**(블록을 보고 눌러야만 됐다). Bukkit 은 허공 클릭 사건을 처음부터 '취소됨'으로 만든다(클릭한 블록이 없어 블록 사용이 DENY). `ignoreCancelled = true` 로 받던 리스너가 허공 클릭을 통째로 못 받고 있었다 — 이제 아이템 사용이 막혔는지(`useItemInHand() == DENY`)를 직접 본다. 커스텀아이템 검증이 같은 결함을 잡아 전 플러그인을 훑었다

## 2026-09-23 — 인게임 테스트 (실제 클라이언트 26.2)

확인한 것: Shift+F 메인 화면 · 어망 넣기/꺼내기 · 도감 발견/등록 · 랭킹 · 대회 목록과 사전 신청(재시작 뒤 유지) ·
자동 낚시(액션바 대기·카운트다운 → 지급, 미끼 1개 소모) · 손질대 · 힘겨루기 시험판(물고기 상태별 권장 클릭,
도망 판정) · 업적 연동(`fishing/catch` 신호).

고친 것:

| 결함 | 수정 |
|---|---|
| **도감 회수(우클릭)가 물고기를 돌려주지 않고 없앴다.** 2세대 `unregisterFish` 는 넣은 크기 그대로 돌려줬다 | 등록한 크기를 순서대로 기록(`sizes`)하고, 회수하면 마지막 것을 그 크기·등급·트로피 판정으로 되돌려준다. 인벤토리가 가득이면 회수하지 않는다(어망 꺼내기와 같다). `CollectionEntry.lastRegisteredSize` |
| **손질대 화면이 멈춰 있었다** — 남은 시간이 안 흐르고, 다 된 칸이 건네진 뒤에도 남았다. 2세대는 1초마다 다시 그렸다 | 틱커 `fillet-menu` 단계가 열린 손질대를 1초마다 다시 그린다 |
| **손질 취소가 크기 0 의 물고기를 돌려줬다**(트로피가 크기를 잃음). 2세대는 크기를 되살렸다 | `FilletSlot.size` 를 두고 저장(`size`)·취소에 쓴다 |
| **힘겨루기 보스바가 빠져 있었다.** 2세대는 장력 게이지 + `거리: X / MAX M` 보스바를 띄웠다. 액션바의 거리 게이지만으로는 남은 거리를 읽을 수 없다(테스트 중 빈 게이지를 "가깝다"로 잘못 읽었다) | vanilla 모드에서 보스바를 되살렸다. 게이지 = 장력, 색 = 장력 문턱(초록/노랑/빨강), 제목 = `fight.hud.bossbar-title-format`(2세대 기본값 그대로). 판이 끝나면 치운다 |
| **업적 `첫 낚시` 가 영영 0/1** — 기본 정의가 바닐라 통계 `FISH_CAUGHT` 를 보는데, 이 플러그인이 바닐라 낚기를 가로채 통계가 오르지 않았다 | 지급(`CatchService.grant`) 끝에서 `FISH_CAUGHT` 를 1 올린다. 재확인: 낚자마자 토스트 "첫 낚시" · 점수 +2 |

남긴 것(결함 아님): 2세대의 `actionbar-format`·`state-title-format` 서식 문자열은 옮기지 않았다 — 5세대 상태에는 정해진
지속 시간이 없어 `{remaining_seconds}` 를 채울 수 없고, 액션바는 게이지 넷 + 상태 + 권장 클릭을 그린다.
`/낚시 지급 물고기` 는 2세대처럼 크기 설명 없이 준다.

테스트 342개(+5).

## 2026-09-23 — 재기동 시 정의 파일 소실 (core 에서 수정)

- 테스트 서버를 두 번째로 켜자 `fish.yml` · `rods.yml` · `baits.yml` · `potions.yml` ·
  `fillet-items.yml` 이 `expected '<document start>'` 로 읽히지 않고 **모든 등급에 물고기 0마리**가 됐습니다.
  첫 종료 때 저장하면서 `#` 없는 머리말이 붙은 탓입니다
- 이 플러그인 코드는 바꾸지 않았습니다 — 같은 실수가 세 플러그인 9곳에 있어 core `YamlFileStore` 가
  머리말을 주석으로 바꾸게 했습니다 (`inmc-core/CHANGELOG.md`)
- 확인: 종료 → 재부팅에서 `등급 7 · 물고기 70 · 낚싯대 10`, 오류 0

## 2026-09-23 — 2세대 대조 검증과 결여 복원

2세대 `낚시/`(InMc-Fishing) 와 **명령어 · 설정 파일 · 메시지 · 연동**을 하나씩 대조했습니다.
5세대가 처음 재구성할 때 옮기지 않은 기능이 여럿 나와 전부 되살렸습니다 (규칙 31).

### 되살린 기능

| 2세대 기능 | 5세대에서 | 파일 |
|---|---|---|
| PlaceholderAPI `%inmcfishing_*%` (약 40개: 도감·피로도·랭킹·`best_size_<물고기>`·힘겨루기 HUD `fight_*`) | 같은 식별자·키·기본값. 키는 테스트가 2세대와 대조 | `hook/PapiHook.kt` · `PlaceholderParityTest` |
| 힘겨루기 HUD 모드 `vanilla`/`betterhud` · 위험 문턱 | `fight.hud` (`mode` · `tension-*-ratio` · `distance-danger-percent` · `reel-danger-percent`) | `config/FightHud.kt` |
| WorldGuard 구역 규칙 `worldguard.yml` (구역별 낚시 금지 · 등급 배수) | 같은 형식. 겹치면 금지 우선, 배수는 곱 | `region/RegionRules.kt` · `hook/WorldGuardHook.kt` |
| 대회 **여러 개 동시 진행** | 대회마다 따로 열고 닫음. 한 마리가 참가 중인 대회 전부에 점수 | `tournament/TournamentService.kt` |
| 대회 **사전 신청** (참가비 선징수 · 시작 시 자동 참가 · 신청 취소 환불) | 같음. 자동 시작은 신청이 최소 인원 미만이면 열리지 않음 | 〃 |
| 진행 중 대회 저장 · 재시작 후 이어가기 (`active.yml`) | 같음 + **사전 신청도 저장** | 〃 |
| 대회 결과 기록 `history/<id>.yml` · 우승 횟수 `winners.yml` · `/fishing tournament wins` | 같은 형식(2세대 `winners.yml` 을 옮겨 놓으면 이어짐) · `/낚시 대회 우승` | 〃 |
| 대회 HUD (보스바 남은 시간 · 사이드바 순위) | 같음. 참가자에게만, 되돌릴 때 서버 기본 스코어보드로 | `tournament/TournamentHud.kt` |
| `/fishing tournament list·join·leave` | `/낚시 대회 목록·참가·퇴장` | `command/FishingCommand.kt` |
| 메인 GUI + **낚싯대 들고 Shift+F** | `MainMenu` — 스탯 머리 · 어망 · 도감 · 랭킹 · 대회 · 손질대 · 미니게임 · 연습 모드 · 힘겨루기 설명. 각 화면에 "메인으로" | `gui/MainMenu.kt` · `listener/FishingListener.kt` |
| 트로피 파이트 설명 GUI | `FightHelpMenu` (상태 목록은 `FishState` 에서 자동) | `gui/FightHelpMenu.kt` |
| 트로피 파이트 **연습 모드** (저장됨, 보상 없음) | 같음 | `player/Angler.kt` · `catching/FishingFlow.kt` |
| 힘겨루기 **시작 연출** (!!! → 3 → 2 → 1 → START!!) | 같음. `fight.intro` 로 설정 | `config/Titles.kt` · `player/Sessions.kt` |
| 미니게임 **결과 타이틀** (잡았다! / 도망갔네..) | 같음. 문구는 `messages.yml`, 시간은 `titles` | `catching/FishingFlow.kt` |
| 힘겨루기 **실패 유예** `fail-grace` (300ms) · HUD 위험도 `3` | 같음 | `fight/Fight.kt` |
| `/fishing stats` · `show`(자랑) · `info` · `debug` · `list` | `/낚시 스탯` · `자랑` · `정보` · `디버그` · `목록` | `command/FishingTools.kt` |
| `/fishing simulate <n>` (워커에서) | `/낚시 시뮬레이션 <횟수>` | `fish/Simulation.kt` |
| `/fishing testfight <등급> <trophy\|rare>` · `testfightmode` | `/낚시 시험판` · `/낚시 시험모드` | `command/FishingTools.kt` |
| `/fishing fatigue <플레이어>` · `add\|set` | `/낚시 피로도 <플레이어>` · `설정\|추가` | 〃 |
| `/fishing fillet setSlots` (사람마다 칸 수, 1~35) | `/낚시 손질 칸 설정\|추가` · 손질대 6줄 35칸 배치 · 잠긴 칸 표시 | `fillet/Fillet.kt` · `gui/FilletMenu.kt` |
| `/fishing minigame on\|off\|status` | `/낚시 미니게임 켜기·끄기·상태` | `command/FishingCommand.kt` |
| `/fishing give net` · `give trophy` | `/낚시 지급 어망` · `지급 트로피` | 〃 |
| 던질 때 액션바 상태 · 자동 낚시 남은 초 액션바 | `waiting` · `auto-countdown` | `listener/FishingListener.kt` · `catching/FishingFlow.kt` |
| 피로도가 풀릴 때 알림 | `fatigue-unlocked` | `scheduler/Ticker.kt` · `catching/CatchService.kt` |
| 지급 낚싯대의 능력 로어 서식 `item-format.yml` | 같은 키. 기존 로어 **뒤에** 붙임 | `rod/RodLoreFormat.kt` |
| 적재 검증 보고 (`ValidatorManager`) | 모르는 등급 · 가중치 0 · 물고기 없는 등급을 경고 | `fish/FishReport.kt` |

### 고친 결함

| | |
|---|---|
| **대회 등급 점수표가 2세대와 달랐습니다** | 주석은 "2세대 값 그대로"인데 값은 1·2·3·5·8·13·21 이었습니다. 2세대 `getGradeBaseScore` 는 F=1 … S=7. 바로잡고 테스트를 고쳤습니다 |
| **등록 안 된 낚싯대에서 바닐라 전리품이 나왔습니다** | `allow-unregistered-vanilla-rod: false` 일 때 판을 안 열고 바닐라에 넘겼습니다. 이제 던지는 순간 막습니다(2세대와 같음) |
| **힘겨루기에 이기면 낚싯바늘이 남았습니다** | 세션을 먼저 비운 뒤 바늘을 찾아 null 이었습니다. 바늘을 직접 넘깁니다 (규칙 17) |
| **권한 `infishing.user` 가 없었습니다** | 2세대는 일반 명령을 이 권한(기본 true)으로 막을 수 있었습니다. 되살렸고 관리자 권한이 물려받습니다 (`PermissionParityTest`) |
| 대회 결과 공지 | 참가자 전원을 공지하던 것을 2세대처럼 상위 셋만 |
| 대회 정상 종료 | 끌 때 진행 중 대회가 보상 없이 사라지고 참가비도 안 돌아갔습니다. 2세대처럼 끝내고 보상을 줍니다 |
| 나간 참가자 | 기록을 지웠습니다. 2세대처럼 남기고(보상·정원에서만 제외) 다시 들어오면 이어집니다 |
| `/낚시 피로도` 상한 | 절대 상한을 보였습니다. 손에 든 낚싯대를 반영한 실제 상한(`ceiling`)으로 |
| `config.yml` 주석 | 한 줄이 인코딩이 깨져 있었고, 없는 명령(`/낚시 등록`)을 안내하고 있었습니다 |
| README | "선택 연동은 전부 리플렉션" 이 사실과 달랐습니다(PAPI·WorldGuard 는 설치 확인 후 직접 참조) |

### 테스트

307 → **338개**. 새로: `PlaceholderParityTest` · `RegionRulesTest` · `ParityRestoreTest`(실패 유예·시뮬레이션) ·
`RodLoreFormatTest` · 대회 나가기/다시 들어오기 · 손질대 칸 · 배포 물고기 적재 경고 0.

### 옮기지 않은 것

| | 이유 |
|---|---|
| `/fishing dumpbalance` | 2세대 스스로 "임시 명령어, 이관 검증 후 삭제" 라고 적어 둔 개발 도구입니다. 5세대는 `FightBalanceTest` 가 같은 일을 합니다 |
| `FishCatchEvent` (Bukkit 이벤트) | 2세대 내부 타입(`Fish`·`RewardEntry`)을 싣고 있어 그대로는 못 옮깁니다. 같은 정보를 `InmcSignalEvent(fishing/catch)` 로 냅니다 |
| 미니게임을 **타이틀**로 그리던 것 | 5세대는 액션바에 그립니다(표시 방식). 결과 타이틀은 되살렸습니다 |
| 힘겨루기 AI 의 상태별 지속 시간·행동력 | 5세대 힘겨루기는 13단계에서 다시 설계했습니다(균형 테스트로 검증). 그래서 `fight_state_seconds`/`state_percent` 는 "없음" 값입니다 |
| 2세대 플레이어 데이터(SQLite `playerdata.db`) 이관 | **하지 않음** (운영자 결정, 2026-09-23) — 5세대는 새 데이터로 시작합니다 |

---

## 2026-09-22 — 15단계: 업적 연동

- `InmcSignalEvent` 발행 세 곳: `fishing/catch`(낚음) · `fishing/register`(도감 등록) · `fishing/tournament`(대회 순위, 오프라인 포함)
- `SignalCatalog` 등록 — 업적 편집기에 진짜 물고기·대회 목록이 나옵니다. 끌 때 `unregisterAll`

## 2026-09-14 — 14단계: 커스텀아이템과 낚싯대

- `RodSource` 공급처 분리, 기본 `CombinedRodSource(커스텀아이템, rods.yml)`
- `CustomItemRodSource` — 커스텀아이템의 `fishing.*` 연동 값으로 낚싯대를 만듭니다

## 2026-09-14 — 13단계: 5세대 전면 재구성

2세대(Java 108파일 20,112줄 · 테스트 0개)를 Kotlin 으로 새로 만들었습니다. 원본은 건드리지 않았습니다.

- 추첨(`RollEngine`) · 등급표 · 환경 보정 5축 · 미니게임(`ClickGame`) · 힘겨루기(`Fight` + `FishAI`)
- 도감 · 어망 · 손질대 · 피로도 · 대회 · 랭킹(core `RankService`) · 우편함
- 물고기·낚싯대·미끼·물약·생선살 **전부 GUI 로 관리**
- 바로잡은 것: 힘겨루기 삼중 벌칙(S등급 승률 0/200) · 자연 회복 상한 · 권한 보정 점 · 오프라인 대회 보상 ·
  미끼/물약/생선살 누락 · `fillet-items.yml` 의 `normal` 뜻 뒤집힘 — 자세한 것은 README "2세대에서 바로잡은 것"
