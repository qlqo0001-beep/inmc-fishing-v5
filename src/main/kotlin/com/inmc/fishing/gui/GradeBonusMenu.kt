package com.inmc.fishing.gui

import com.inmc.fishing.Fishing
import kr.inmc.core.gui.Editors
import kr.inmc.core.gui.Icon
import kr.inmc.core.gui.Paging
import kr.inmc.core.util.Text
import org.bukkit.Material
import org.bukkit.entity.Player

/**
 * 등급별 보너스 표를 고치는 화면. 낚싯대와 미끼가 같이 쓴다.
 *
 * **이 보너스는 더해진다.** 곱셈이면 가중치 1 인 S 에 배수를 줘봐야 1 이 늘 뿐이라, 좋은
 * 장비가 희귀 등급을 열어준다는 약속이 성립하지 않는다. 그래서 값 옆에 실제 확률이 어떻게
 * 바뀌는지를 같이 적는다 — `+2` 가 큰 값인지 작은 값인지는 그 등급의 가중치를 봐야 안다.
 *
 * 값을 함수로 받고 함수로 돌려주는 것은, 부르는 쪽의 정의가 **불변**이라 고칠 때마다 새
 * 객체가 되기 때문이다. 객체를 붙들고 있으면 두 번째 편집이 첫 번째를 지운다.
 */
class GradeBonusMenu(
    fishing: Fishing,
    private val viewer: Player,
    title: String,
    private val current: () -> Map<String, Double>?,
    private val onChange: (Map<String, Double>) -> Unit,
    private val back: () -> Unit,
) : Menu(fishing, SIZE, Text.renderFlat(title)) {

    override fun draw() {
        clear()
        val bonus = current() ?: run {
            back()
            return
        }
        fillEmpty(Icon.EDGE)

        for ((index, grade) in fishing.grades.ordered.withIndex()) {
            if (index >= Paging.PER_PAGE) break
            val value = bonus[grade.id] ?: 0.0
            set(
                index,
                Editors.numberIcon(
                    Material.PAPER,
                    grade.tag(),
                    value,
                    extra = listOf(
                        "<gray>기본 가중치 <white>" + grade.weight + "</white> 에 더해집니다.</gray>",
                        "<gray>이 장비를 들면 <white>" + (grade.weight + value) + "</white> 이 됩니다.</gray>",
                    ),
                ),
            ) { event ->
                if (Editors.isPrompt(event)) {
                    Editors.promptDouble(
                        fishing.prompts,
                        viewer,
                        grade.displayName + " 보너스",
                        -MAX,
                        MAX,
                        reopen = { open(viewer) },
                    ) { typed -> apply(grade.id, typed) }
                    return@set
                }
                apply(grade.id, (value + Editors.step(event, 1.0)).coerceIn(-MAX, MAX))
            }
        }

        set(Paging.SLOT_BACK, Icon.back()) { back() }
        set(Paging.SLOT_CLOSE, Icon.close()) { viewer.closeInventory() }
    }

    /** 0 은 표에서 아예 뺀다. 남겨두면 설정 파일이 안 쓰는 항목으로 채워진다. */
    private fun apply(gradeId: String, value: Double) {
        val next = HashMap(current() ?: return)
        if (value == 0.0) next.remove(gradeId) else next[gradeId] = value
        onChange(next)
        refresh()
    }

    companion object {
        const val SIZE = 54
        const val MAX = 10_000.0
    }
}
