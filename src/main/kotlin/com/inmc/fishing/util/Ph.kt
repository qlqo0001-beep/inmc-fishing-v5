package com.inmc.fishing.util

import kr.inmc.core.util.TokenBag
import org.bukkit.entity.Player

/**
 * 메시지 한 번 렌더링에 쓰이는 토큰 주머니.
 *
 * 별칭표는 2세대 `messages.yml` 에서 뽑았다 — 관리자가 고쳐 쓰던 파일을 그대로 옮겨와도
 * 돌아야 한다. 한글 표기는 다른 5세대 플러그인과 맞추려고 덧붙였고, 어느 쪽으로 적어도 같다.
 */
class Ph : TokenBag<Ph>() {

    override val aliases: Map<String, List<String>> get() = ALIASES

    fun player(name: String): Ph = put(PLAYER, name)

    fun player(player: Player): Ph = put(PLAYER, player.name)

    fun fish(name: String): Ph = put(FISH, name)

    fun grade(tag: String): Ph = put(GRADE, tag)

    /** cm 단위 크기. 소수점 한 자리로 찍는다. */
    fun size(value: Double): Ph = put(SIZE, String.format("%.1f", value))

    fun count(value: Int): Ph = put(COUNT, value.toString())

    fun amount(value: Int): Ph = put(AMOUNT, value.toString())

    fun rank(value: Int): Ph = put(RANK, value.toString())

    fun score(value: Long): Ph = put(SCORE, String.format("%,d", value))

    fun remaining(text: String): Ph = put(REMAINING, text)

    fun seconds(value: Long): Ph = put(SECONDS, value.toString())

    fun sequence(text: String): Ph = put(SEQUENCE, text)

    /** 미니게임 좌·우 글자(`config.yml` `minigame.letters`) — 결과 타이틀의 안내에 쓴다. */
    fun letters(left: String, right: String): Ph = put(LEFT, left).put(RIGHT, right)

    fun tournament(name: String): Ph = put(TOURNAMENT, name)

    fun reason(text: String): Ph = put(REASON, text)

    /** 돈처럼 이미 서식을 입힌 수량. */
    fun amount(text: String): Ph = put(AMOUNT, text)

    fun id(text: String): Ph = put(ID, text)

    fun value(text: String): Ph = put(VALUE, text)

    fun copy(): Ph = copyValuesInto(Ph())

    companion object {

        fun of(): Ph = Ph()

        const val PLAYER = "player"
        const val FISH = "fish"
        const val GRADE = "grade"
        const val SIZE = "size"
        const val COUNT = "count"
        const val AMOUNT = "amount"
        const val RANK = "rank"
        const val SCORE = "score"
        const val REMAINING = "remaining"
        const val SECONDS = "seconds"
        const val SEQUENCE = "sequence"
        const val LEFT = "left"
        const val RIGHT = "right"
        const val TOURNAMENT = "tournament"
        const val REASON = "reason"
        const val ID = "id"
        const val VALUE = "value"

        private val ALIASES: Map<String, List<String>> = mapOf(
            PLAYER to listOf("{플레이어}", "{player}"),
            FISH to listOf("{물고기}", "{fish}", "{fish_name}"),
            GRADE to listOf("{등급}", "{grade}"),
            SIZE to listOf("{크기}", "{size}"),
            COUNT to listOf("{개수}", "{count}"),
            AMOUNT to listOf("{수량}", "{amount}"),
            RANK to listOf("{순위}", "{rank}"),
            SCORE to listOf("{점수}", "{score}"),
            REMAINING to listOf("{남은시간}", "{remaining}"),
            SECONDS to listOf("{초}", "{seconds}"),
            SEQUENCE to listOf("{순서}", "{sequence}"),
            LEFT to listOf("{좌}", "{left}"),
            RIGHT to listOf("{우}", "{right}"),
            TOURNAMENT to listOf("{대회}", "{tournament}"),
            REASON to listOf("{사유}", "{reason}"),
            ID to listOf("{아이디}", "{id}"),
            VALUE to listOf("{값}", "{value}"),
        )
    }
}
