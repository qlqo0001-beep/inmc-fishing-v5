package com.inmc.fishing.fish

import com.inmc.fishing.grade.GradeTable

/**
 * 적재 뒤 검증 보고 — 2세대 `ValidatorManager` 가 콘솔에 찍던 것.
 *
 * 아래는 전부 **오류 없이 조용히 틀리는** 경우다. 물고기는 등록돼 있고 목록에도 보이는데 아무리
 * 던져도 안 나온다. 그래서 적재할 때마다 이름을 대고 알린다.
 */
object FishReport {

    fun problems(fish: Collection<Fish>, grades: GradeTable): List<String> = buildList {
        for (f in fish) {
            if (grades[f.gradeId] == null) add("물고기 '${f.id}' 의 등급 '${f.gradeId}' 이(가) grades.yml 에 없어 절대 나오지 않습니다")
            if (f.weight <= 0) add("물고기 '${f.id}' 의 weight 가 0 이하라 절대 나오지 않습니다")
        }
        for (grade in grades.ordered) {
            if (fish.none { it.gradeId == grade.id && it.weight > 0 }) {
                add("등급 '${grade.id}' 에 나올 수 있는 물고기가 없습니다 — 이 등급이 뽑히면 아무것도 낚이지 않습니다")
            }
        }
    }
}
