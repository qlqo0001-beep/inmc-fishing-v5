package com.inmc.fishing.ranking

import com.inmc.fishing.collection.Collection
import com.inmc.fishing.collection.ScoreWeights
import kr.inmc.core.rank.Better
import kr.inmc.core.rank.RankConfig
import kr.inmc.core.rank.RankMode
import kr.inmc.core.rank.Rankable
import kr.inmc.core.reward.RewardTable
import java.util.UUID

/**
 * 낚시의 랭킹 하나.
 *
 * core 의 [Rankable] 은 원래 "게임 한 판을 마치면 기록을 올린다"는 모양이지만, 낚시 랭킹은
 * 전부 **도감에서 파생되는 현재 값**이다. 그래서 [kr.inmc.core.rank.RankService.record] 가
 * 아니라 `put` 으로 올린다 — 다시 계산해도 점수가 불어나지 않는다.
 */
class FishingRank(
    override val id: String,
    override val displayName: String,
    override val recordUnit: String,
    override val better: Better = Better.HIGHER,
    override val ranking: RankConfig = RankConfig(mode = RankMode.CUMULATIVE_SCORE),
    override val rewards: RewardTable = RewardTable(),
) : Rankable

/**
 * 도감에서 순위를 뽑아 core 의 랭킹 보드에 올린다.
 *
 * 2세대는 랭킹이 **표시 전용**이었다 — 시즌도 보상도 없었다. core 를 쓰면 시즌 초기화와
 * 순위 보상이 덤으로 딸려오고, 설정을 비워두면 아무 일도 일어나지 않으므로 예전과 똑같이
 * 동작한다. 쓰고 싶어지면 설정만 채우면 된다.
 *
 * **물고기별 사이즈 보드는 물고기 수만큼 생긴다.** core 의 보드는 문자열 키로 나뉘므로
 * 따로 장치가 필요 없었다 — id 만 다르게 주면 된다.
 */
class FishingRanks(
    /** 도감 점수 가중치. 도감 랭킹이 이 값으로 계산된다. */
    private val weights: ScoreWeights = ScoreWeights.DEFAULT,
) {

    val collection = FishingRank(COLLECTION_ID, "도감", "점", ranking = RankConfig(mode = RankMode.CUMULATIVE_SCORE))

    val trophy = FishingRank(TROPHY_ID, "트로피", "개", ranking = RankConfig(mode = RankMode.CUMULATIVE_SCORE))

    private val sizeBoards = HashMap<String, FishingRank>()

    /**
     * 이 물고기의 사이즈 보드. 없으면 만든다.
     *
     * 사이즈는 **최고 기록** 모드다 — 누적이 아니라 "가장 크게 낚은 한 마리"로 겨룬다.
     */
    fun sizeBoard(fishId: String, fishName: String): FishingRank =
        sizeBoards.getOrPut(fishId.lowercase()) {
            FishingRank(
                id = sizeBoardId(fishId),
                displayName = "$fishName 최대 크기",
                recordUnit = "cm",
                ranking = RankConfig(mode = RankMode.BEST_RECORD),
            )
        }

    /** core 의 `rankables` 가 요구하는 목록. 지금까지 만들어진 보드 전부. */
    fun all(): List<Rankable> = listOf(collection, trophy) + sizeBoards.values

    fun byId(id: String?): Rankable? = all().firstOrNull { it.id.equals(id, ignoreCase = true) }

    /**
     * 한 사람의 도감을 보드에 반영한다.
     *
     * **멱등하다.** 몇 번을 불러도 같은 결과가 나오므로 틱커가 주기적으로 돌려도 되고,
     * 물고기를 낚은 직후에 한 번만 불러도 된다.
     *
     * @param publish 실제 올리기. `RankService.put` 을 넘긴다 — 이 클래스가 core 를 직접
     *                호출하지 않게 해서 서버 없이 검증할 수 있다.
     */
    fun publish(
        playerId: UUID,
        playerName: String,
        collectionData: Collection,
        fishName: (String) -> String,
        publish: (Rankable, Long, Long) -> Unit,
    ) {
        publish(collection, collectionData.score(weights), 0L)
        publish(trophy, (collectionData.trophyCount + collectionData.rareTrophyCount).toLong(), 0L)

        for (entry in collectionData.all()) {
            // 크기 기록이 없는 물고기(쓰레기, 아직 못 잡은 것)는 보드를 만들지 않는다.
            // 만들면 빈 보드가 물고기 수만큼 생기고 GUI 가 그걸 전부 보여준다.
            if (entry.largestSize <= 0.0) continue
            val board = sizeBoard(entry.fishId, fishName(entry.fishId))
            // 0.1cm 까지 순위에 반영하려고 10배로 정수화한다. 대회 SIZE 와 같은 방식이다.
            publish(board, 0L, (entry.largestSize * 10).toLong())
        }
    }

    companion object {
        const val COLLECTION_ID = "fishing_collection"
        const val TROPHY_ID = "fishing_trophy"

        /** 물고기별 사이즈 보드의 id. 다른 보드와 섞이지 않게 접두사를 붙인다. */
        fun sizeBoardId(fishId: String): String = "fishing_size_" + fishId.lowercase()
    }
}
