package com.example.mahjong.ui

import com.example.mahjong.AwaitKind
import com.example.mahjong.core.ClaimType

/** 人类选手（座位 0）的界面状态。 */
data class HumanUi(
    val hand: List<String>,
    val melds: List<List<String>>,
    val discards: List<String>,
    val isTurn: Boolean,
    /** 当前手牌可以自摸和牌。 */
    val canWin: Boolean,
    /** 手牌中可暗杠的编码（按优先级）。 */
    val concealedKongs: List<String>,
    /** 13 张时的听牌列表。 */
    val waits: List<String>,
)

/** 对手的界面状态（不展示手牌内容，只展示张数）。 */
data class OpponentUi(
    val seat: Int,
    val name: String,
    val handCount: Int,
    val melds: List<List<String>>,
    val discards: List<String>,
    val isTurn: Boolean,
)

/** 等待人类表态的鸣牌候选。 */
data class ClaimUi(
    val discarder: Int,
    val discarderName: String,
    val tile: String,
    val option: ClaimType,
)

/** 一帧完整的游戏界面状态（不可变，随每次操作重建）。 */
data class UiState(
    val epoch: Int,
    val awaitKind: AwaitKind,
    val humanSeat: Int,
    val dealer: Int,
    val turn: Int,
    val turnName: String,
    val wallCount: Int,
    val phase: String,
    val winner: Int?,
    val winnerName: String?,
    val winPatterns: List<String>,
    val message: String,
    val history: List<String>,
    val lastDiscard: Pair<Int, String>?,
    val lastDiscardName: String?,
    val human: HumanUi,
    val opponents: Map<Int, OpponentUi>,
    val claim: ClaimUi?,
) {
    val isFinished: Boolean
        get() = awaitKind == AwaitKind.FINISHED

    /** 指定座位是否轮到行动。 */
    fun isTurn(seat: Int): Boolean = turn == seat && !isFinished

    /** 相对方位的对手座位号。 */
    val rightSeat: Int get() = (humanSeat + 1) % SEAT_COUNT
    val topSeat: Int get() = (humanSeat + 2) % SEAT_COUNT
    val leftSeat: Int get() = (humanSeat + 3) % SEAT_COUNT

    companion object {
        const val SEAT_COUNT = 4
    }
}
