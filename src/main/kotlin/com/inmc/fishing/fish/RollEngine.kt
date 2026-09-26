package com.inmc.fishing.fish

import com.inmc.fishing.grade.Grade
import com.inmc.fishing.grade.GradeTable

/**
 * 난수 공급자.
 *
 * 추첨 전체를 서버 없이 재현하려고 뽑아냈다. 실제 구현은 [java.util.Random] 을 감싸고,
 * 테스트는 미리 정한 값을 순서대로 돌려준다.
 */
interface Rolls {
    /** 0.0 이상 1.0 미만. */
    fun next(): Double

    /** 평균 0 · 표준편차 1 의 정규분포 표본. */
    fun gaussian(): Double
}

/**
 * 한 번의 추첨에 걸리는 보정 전부.
 *
 * **축마다 결합 방식이 다르고, 그게 의도다.**
 *
 * | 축 | 방식 | 이유 |
 * |---|---|---|
 * | 환경 등급 보정 | 곱셈 | 축(월드·날씨·시간…)을 더해도 기존 균형이 어긋나지 않는다 |
 * | 낚싯대·미끼 등급 보너스 | 덧셈 | 가중치 1 인 S 에 2배를 곱해봐야 1 이 는다. 덧셈이라야 희귀 등급이 실제로 열린다 |
 * | 대어·더블 확률 | 덧셈 | 이미 백분율이다. 곱하면 낚싯대 두 개 차이가 기하급수로 벌어진다 |
 * | 크기 | 퍼센트 → 고정값 | 순서가 바뀌면 결과가 달라진다 |
 */
data class Bonuses(
    /** 환경 보정. **곱해진다.** */
    val gradeMultipliers: Map<String, Double> = emptyMap(),
    /** 낚싯대·미끼 보너스. **더해진다** - 가중치가 작은 최상위 등급에서 곱셈은 효과가 없다. */
    val gradeFlatBonus: Map<String, Double> = emptyMap(),
    /** 대어 확률에 더할 퍼센트 포인트. */
    val bigFishChance: Double = 0.0,
    /** 더블 확률에 더할 퍼센트 포인트. */
    val doubleChance: Double = 0.0,
    /** 크기에 곱할 퍼센트. 미끼용. */
    val sizePercent: Double = 0.0,
    /** 크기에 더할 고정값(cm). 미끼용. */
    val sizeFixed: Double = 0.0,
) {
    companion object {
        val NONE = Bonuses()
    }
}

/** 한 번 낚은 결과. */
data class Catch(
    val fish: Fish,
    val grade: Grade,
    /** 대어였는지. 대어면 [trophy] 가 반드시 [Trophy.NONE] 이 아니다. */
    val bigFish: Boolean,
    val double: Boolean,
    val size: Double,
    val trophy: Trophy,
)

/**
 * 낚시 한 번의 추첨 전체.
 *
 * **순서가 곧 사양이다.**
 *
 * 1. 등급 추첨 (가중치 × 환경 배수 + 장비 보너스)
 * 2. 그 등급의 물고기를 가중치로 추첨
 * 3. **대어 판정** — 트로피가 될 수 있는 물고기일 때만 성립한다
 * 4. 더블 판정
 * 5. 크기 추첨 → 미끼 보정
 * 6. 트로피 판정
 *
 * **대어는 등급을 올리지 않는다. 트로피를 보장한다.**
 * 2세대는 대어가 한 등급 위로 승급시키는 것이었는데, 그러면 최상위 등급에서 대어가 아무
 * 의미가 없고 "대어"라는 이름과도 어긋난다 — 대어는 큰 개체이지 다른 종이 아니다.
 * 지금은 크기를 트로피 구간에서 뽑아 반드시 트로피가 되게 한다.
 *
 * 그래서 **대어 판정이 물고기 추첨보다 뒤**다. 그 물고기의 크기 범위를 알아야 트로피 구간을
 * 정할 수 있기 때문이다. 2세대와 정반대 순서이고, 의도된 변경이다.
 *
 * 5번과 6번의 순서는 그대로다. **미끼 보정이 트로피 판정보다 먼저**라서, 미끼로 키운 크기가
 * 트로피가 될 수 있고 설정된 최대치를 넘을 수도 있다.
 */
class RollEngine(
    private val grades: GradeTable,
    private val fishByGrade: Map<String, List<Fish>>,
    private val trophyRule: TrophyRule,
    /** 기본 대어 확률(%). 낚싯대 보너스가 여기 더해진다. */
    private val bigFishChance: Double,
    /** 기본 더블 확률(%). */
    private val doubleChance: Double,
) {

    fun roll(rolls: Rolls, bonuses: Bonuses = Bonuses.NONE): Catch? {
        val grade =
            grades.draw(bonuses.gradeMultipliers, rolls.next(), bonuses.gradeFlatBonus) ?: return null

        // 2. 물고기를 **먼저** 뽑는다. 대어 판정이 이 물고기의 크기 범위를 알아야 하기 때문이다.
        val fish = drawFish(grade, rolls.next()) ?: return null

        // 3. 대어. 확률에 걸려도 **트로피가 될 수 있는 물고기일 때만** 성립한다.
        //    쓰레기(크기 없음)나 트로피 하한이 최대를 넘는 물고기는 대어가 되지 않는다 —
        //    그대로 두면 "대어인데 트로피가 아닌" 모순이 생긴다.
        val bigFish = trophyRule.canBeTrophy(fish.size) &&
            rolls.next() * 100.0 < bigFishChance + bonuses.bigFishChance

        val double = fish.doubleEnabled && rolls.next() * 100.0 < doubleChance + bonuses.doubleChance

        // 4. 크기. 대어면 트로피 구간에서 뽑아 **반드시 트로피가 되게** 한다.
        var size = if (bigFish) {
            fish.size.rollAtLeast(trophyRule.normalFloor(fish.size), rolls.next())
        } else {
            fish.size.roll(rolls.gaussian())
        }

        if (size > 0.0) {
            if (bonuses.sizePercent != 0.0) size *= (1.0 + bonuses.sizePercent / 100.0)
            size += bonuses.sizeFixed
        }

        return Catch(
            fish = fish,
            grade = grade,
            bigFish = bigFish,
            double = double,
            size = size,
            trophy = trophyRule.judge(fish.size, size),
        )
    }

    /** 한 등급 안에서 가중치로 물고기를 고른다. */
    fun drawFish(grade: Grade, roll: Double): Fish? {
        val candidates = fishByGrade[grade.id].orEmpty().filter { it.weight > 0 }
        if (candidates.isEmpty()) {
            // 가중치가 전부 0 이면 그 등급의 아무거나. 입질이 사라지는 것보다 낫다.
            return fishByGrade[grade.id].orEmpty().firstOrNull()
        }

        val total = candidates.sumOf { it.weight }
        var cursor = roll.coerceIn(0.0, 0.999999) * total
        for (fish in candidates) {
            cursor -= fish.weight
            if (cursor < 0.0) return fish
        }
        return candidates.last()
    }
}
