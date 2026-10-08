package com.inmc.fishing.verify

import com.inmc.fishing.Fishing
import com.inmc.fishing.gui.AdminMenu
import com.inmc.fishing.minigame.Click
import com.inmc.fishing.minigame.ClickGame
import com.inmc.fishing.minigame.Progress
import com.inmc.fishing.rod.RodData
import com.inmc.fishing.util.Ph
import org.bukkit.entity.Player
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * `/낚시 검증` — 정의·낚싯대 데이터·도감·대회 정의·미니게임 판정·화면을 서버 안에서 확인한다(드랍·상점 검증기와 같은 틀, 2026-10-08).
 *
 * - 실제로 낚지는 않는다(물·낚싯대·시간이 필요하다). 미니게임은 판정 객체(`ClickGame`)를 직접 눌러 본다.
 * - 대회는 정의만 본다 — 시작하면 서버 전체에 공지가 나가고 다른 사람이 참가할 수 있다.
 * - 낚싯대는 만들어 되알아보는 데까지(가방에 넣지 않는다).
 */
class Verifier(private val fishing: Fishing) {

    data class Result(val name: String, val failure: String?) {
        val skipped: Boolean get() = failure?.startsWith(SKIP) == true
    }

    private class Check(val name: String, val run: (Fishing, Player) -> String?)

    fun run(player: Player) {
        val results = CHECKS.map { check ->
            val failure = try {
                check.run(fishing, player)
            } catch (t: Throwable) {
                "검증기 오류: " + t.javaClass.simpleName + (t.message?.let { ": $it" } ?: "")
            }
            Result(check.name, failure)
        }
        player.closeInventory()

        val failures = results.filter { it.failure != null && !it.skipped }
        val skips = results.filter { it.skipped }
        fishing.messages.send(
            player, "verify-done",
            Ph.of().count(results.size - failures.size - skips.size).amount(failures.size)
                .fish(if (skips.isEmpty()) "" else " · 건너뜀 ${skips.size}"),
        )
        for (f in failures) fishing.messages.send(player, "verify-failure", Ph.of().fish("${f.name} — ${f.failure}"))
        for (s in skips) fishing.messages.send(player, "verify-skipped", Ph.of().fish("${s.name} — ${s.failure!!.removePrefix(SKIP).trim()}"))

        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val file = fishing.io.file("verify", "fishing-$stamp.txt")
        val text = buildString {
            appendLine("# inmc-fishing 검증 - ${LocalDateTime.now()} - ${player.name}")
            for (r in results) {
                appendLine((if (r.failure == null) "PASS " else if (r.skipped) "SKIP " else "FAIL ") + r.name + (r.failure?.let { " — $it" } ?: ""))
            }
        }
        fishing.io.asyncRun {
            file.parentFile.mkdirs()
            kr.inmc.core.util.AtomicFiles.write(file, text)
        }
        fishing.messages.send(player, "verify-report", Ph.of().fish("plugins/${fishing.plugin.name}/verify/${file.name}"))
    }

    companion object {
        const val SKIP = "건너뜀:"

        private fun ok(condition: Boolean, failure: String): String? = if (condition) null else failure

        private val CHECKS: List<Check> = listOf(
            Check("물고기 정의 — 하나 이상, 등급이 전부 있다") { f, _ ->
                val all = f.fish.all()
                ok(all.isNotEmpty(), "물고기가 하나도 없습니다")
                    ?: all.filter { it.gradeId.isBlank() }.takeIf { it.isNotEmpty() }?.let { "등급이 빈 물고기: " + it.joinToString(", ") { x -> x.id } }
            },
            Check("물고기 아이템 — 만들어서 되알아본다") { f, _ ->
                val fish = f.fish.all().firstOrNull() ?: return@Check "$SKIP 물고기가 없습니다"
                val stack = f.fish.stack(fish, 1) ?: return@Check "'${fish.id}' 를 만들지 못합니다(공급처 없음)"
                ok(f.fish.identify(stack)?.id == fish.id, "만든 '${fish.id}' 를 ${f.fish.identify(stack)?.id} 로 알아봅니다")
            },
            Check("낚싯대 — 정의가 있고 만든 낚싯대를 되알아본다(RodData 키)") { f, _ ->
                val rods = f.rodRegistry.all()
                val rod = rods.firstOrNull() ?: return@Check "$SKIP 낚싯대 정의가 없습니다"
                val stack = f.catches.rodStack(rod, 1) ?: return@Check "'${rod.id}' 를 만들지 못합니다"
                ok(f.rodRegistry.identify(stack)?.id == rod.id, "만든 '${rod.id}' 를 ${f.rodRegistry.identify(stack)?.id} 로 알아봅니다")
                    ?: ok(RodData.ROD.startsWith("fishing."), "RodData 키 접두사가 바뀌었습니다: ${RodData.ROD}")
            },
            Check("미니게임 판정 — 순서대로 누르면 DONE, 틀리면 MISS, 시간이 지나면 끝") { _, _ ->
                val now = 1_000L
                val game = ClickGame(listOf(Click.LEFT, Click.LEFT, Click.LEFT), timeLimitMillis = 5_000L, startedAt = now)
                val first = game.press(Click.LEFT, now + 100)
                val second = game.press(Click.LEFT, now + 200)
                val third = game.press(Click.LEFT, now + 300)
                ok(first == Progress.HIT && second == Progress.HIT, "맞게 눌렀는데 $first / $second")
                    ?: ok(third == Progress.DONE && game.finished, "셋 다 눌렀는데 $third (DONE 여야)")
                    ?: run {
                        val late = ClickGame(listOf(Click.LEFT), timeLimitMillis = 1_000L, startedAt = now)
                        ok(late.isTimedOut(now + 2_000L), "1초 판이 2초 뒤에도 안 끝납니다")
                    }
            },
            Check("도감 — 내 기록이 읽히고 물고기 정의와 맞는다") { f, p ->
                val angler = f.anglers.of(p) ?: return@Check "$SKIP 낚시꾼 기록이 아직 안 올라왔습니다"
                val unknown = angler.collection.all().filter { f.fish.get(it.fishId) == null }
                ok(unknown.isEmpty(), "정의가 없는 도감 항목: " + unknown.joinToString(", ") { it.fishId })
            },
            Check("대회 정의 — 읽혔고 보상 순위가 1부터 이어진다") { f, _ ->
                val defs = f.tournaments.all()
                if (defs.isEmpty()) return@Check "$SKIP 대회 정의가 없습니다"
                defs.firstOrNull { d -> d.rewards.keys.any { it < 1 } }?.let { "'${it.id}' 의 보상 순위에 1 보다 작은 값" }
                    ?: defs.firstOrNull { it.entryFee > 0.0 && !f.economy.isEnabled }?.let { "'${it.id}' 는 참가비가 있는데 경제 플러그인이 없습니다" }
            },
            Check("순위판 — 낚시 순위가 등록돼 있다") { f, _ ->
                ok(f.rankables.isNotEmpty(), "순위판이 없습니다")
            },
            Check("관리 화면이 열린다") { f, p ->
                AdminMenu(f, p).open(p)
                val opened = p.openInventory.topInventory.holder is AdminMenu
                p.closeInventory()
                ok(opened, "관리 화면이 안 열렸습니다")
            },
        )
    }
}
