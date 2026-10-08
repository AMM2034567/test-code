package com.example.mahjong.core

/**
 * 鸣牌（碰 / 杠 / 荣和）判定与执行。
 *
 * 约定：
 *
 * 1. 只有尚未摸牌的玩家（手牌 `13 - 3 × 副露数` 张）能鸣他人弃牌；
 * 2. 碰、杠会把弃牌从弃牌者的牌河移入鸣牌者的副露，牌总数保持 136 张；
 * 3. 荣和不改动牌河（和牌张留在牌河中作为明示），只结算胜负；
 * 4. 杠后由杠牌者从牌山补摸一张，手牌张数重新回到可打牌状态。
 */

/** 对他人弃牌可声明的动作。 */
enum class ClaimType {
    /** 荣和（吃胡）。 */
    RON,

    /** 碰。 */
    PONG,

    /** 明杠。 */
    KONG,
}

private const val SEATS = 4
private const val MELD_SIZE = 3
private const val KONG_SIZE = 4
private const val OPEN_MELDS_MAX = 4
private const val DEALER_TILES = 13
private const val DRAW_TILES = 14

private fun countOf(hand: List<String>, code: String): Int = hand.count { it == code }

/** 手牌是否处于「等待摸牌」状态（即尚未摸牌）。 */
private fun awaitingDraw(hand: List<String>, melds: List<List<String>>): Boolean =
    hand.size == DEALER_TILES - MELD_SIZE * melds.size

/** 手牌是否处于「已摸牌、等待打牌」状态。 */
private fun awaitingDiscard(hand: List<String>, melds: List<List<String>>): Boolean =
    hand.size == DRAW_TILES - MELD_SIZE * melds.size

/** 能否用该手牌荣和弃牌 [code]。 */
fun canRon(hand: List<String>, melds: List<List<String>>, code: String): Boolean =
    awaitingDraw(hand, melds) && isWin(hand + code, melds)

/** 能否碰弃牌 [code]。 */
fun canPong(hand: List<String>, melds: List<List<String>>, code: String): Boolean =
    melds.size < OPEN_MELDS_MAX && awaitingDraw(hand, melds) && countOf(hand, code) >= 2

/** 能否明杠弃牌 [code]。 */
fun canKong(hand: List<String>, melds: List<List<String>>, code: String): Boolean =
    melds.size < OPEN_MELDS_MAX && awaitingDraw(hand, melds) && countOf(hand, code) >= 3

/** 手牌中可暗杠的牌编码（四张相同），按牌种类编号升序。 */
fun concealedKongs(hand: List<String>, melds: List<List<String>>): List<String> {
    if (melds.size >= OPEN_MELDS_MAX || !awaitingDiscard(hand, melds)) {
        return emptyList()
    }
    return hand.groupingBy { it }.eachCount()
        .filterValues { it >= KONG_SIZE }
        .keys
        .sortedBy { tileIndex(it) }
}

/** 校验 [seat] 要鸣的正是 [discarder] 刚打出的 [code]，并把该牌从牌河取走。 */
private fun takeClaimedTile(state: GameState, seat: Int, discarder: Int, code: String) {
    if (seat == discarder) {
        throw StateException("座位 $seat 不能鸣自己的弃牌")
    }
    if (state.lastAction != "discard:$code") {
        throw StateException("最近的弃牌不是 $code")
    }
    val river = state.player(discarder).discards
    if (river.lastOrNull() != code) {
        throw StateException("座位 $discarder 最近没有打出 $code")
    }
    river.removeAt(river.lastIndex)
}

private fun removeTiles(hand: MutableList<String>, code: String, count: Int) {
    repeat(count) {
        if (!hand.remove(code)) {
            throw StateException("手牌中 $code 不足 $count 张")
        }
    }
}

/** 座位 [seat] 碰 [discarder] 打出的 [code]，碰后轮到 [seat] 打牌。 */
fun declarePong(state: GameState, seat: Int, discarder: Int, code: String) {
    state.validate()
    if (state.phase != "playing") {
        throw StateException("对局已结束（阶段 ${state.phase}），无法碰牌")
    }
    val claimer = state.player(seat)
    if (!canPong(claimer.hand, claimer.melds, code)) {
        throw StateException("座位 $seat 无法碰 $code")
    }
    takeClaimedTile(state, seat, discarder, code)
    removeTiles(claimer.hand, code, 2)
    claimer.melds.add(mutableListOf(code, code, code))
    state.turn = seat
    state.lastAction = "pong:$code"
    state.validate()
}

/** 座位 [seat] 明杠 [discarder] 打出的 [code]，杠后需补摸一张。 */
fun declareOpenKong(state: GameState, seat: Int, discarder: Int, code: String) {
    state.validate()
    if (state.phase != "playing") {
        throw StateException("对局已结束（阶段 ${state.phase}），无法杠牌")
    }
    val claimer = state.player(seat)
    if (!canKong(claimer.hand, claimer.melds, code)) {
        throw StateException("座位 $seat 无法杠 $code")
    }
    takeClaimedTile(state, seat, discarder, code)
    removeTiles(claimer.hand, code, 3)
    claimer.melds.add(mutableListOf(code, code, code, code))
    state.turn = seat
    state.lastAction = "kong:$code"
    state.validate()
}

/** 座位 [seat] 在自己回合暗杠手牌中的 [code]（四张相同），杠后需补摸一张。 */
fun declareConcealedKong(state: GameState, seat: Int, code: String) {
    state.validate()
    if (state.phase != "playing") {
        throw StateException("对局已结束（阶段 ${state.phase}），无法杠牌")
    }
    if (seat != state.turn) {
        throw StateException("座位 $seat 不是当前回合玩家")
    }
    val claimer = state.player(seat)
    if (code !in concealedKongs(claimer.hand, claimer.melds)) {
        throw StateException("座位 $seat 无法暗杠 $code")
    }
    removeTiles(claimer.hand, code, KONG_SIZE)
    claimer.melds.add(mutableListOf(code, code, code, code))
    state.lastAction = "ankong:$code"
    state.validate()
}

/**
 * 座位 [seat] 荣和 [discarder] 打出的 [code]。
 *
 * 无法构成和牌牌型时返回 false 且不改动状态；和牌成功则置为终局并返回 true。
 */
fun declareRon(state: GameState, seat: Int, discarder: Int, code: String): Boolean {
    state.validate()
    if (state.phase != "playing") {
        throw StateException("对局已结束（阶段 ${state.phase}），无法荣和")
    }
    if (seat == discarder) {
        throw StateException("座位 $seat 不能荣和自己的弃牌")
    }
    if (state.lastAction != "discard:$code" || state.player(discarder).discards.lastOrNull() != code) {
        throw StateException("座位 $discarder 最近没有打出 $code")
    }
    val claimer = state.player(seat)
    if (!awaitingDraw(claimer.hand, claimer.melds)) {
        return false
    }
    val patterns = winPatterns(claimer.hand + code, claimer.melds)
    if (patterns.isEmpty()) {
        return false
    }
    state.phase = "finished"
    state.winner = seat
    state.winPatterns = patterns.toMutableList()
    state.lastAction = "ron:${patterns.joinToString("+")}"
    return true
}

/** 一次针对弃牌的鸣牌候选（一名玩家对一种动作）。 */
data class ClaimCandidate(
    /** 鸣牌者座位。 */
    val seat: Int,
    /** 弃牌者座位。 */
    val discarder: Int,
    /** 被鸣的弃牌。 */
    val tile: String,
    /** 该候选对应的动作。 */
    val option: ClaimType,
)

/**
 * 对 [discarder] 打出的 [code] 的全部鸣牌候选，按规则优先级排序：
 * 荣和优先于碰/杠，同级按出牌顺序（弃牌者的下家开始）排列。
 */
fun claimCandidates(state: GameState, discarder: Int, code: String): List<ClaimCandidate> {
    if (state.phase != "playing" || state.lastAction != "discard:$code") {
        return emptyList()
    }
    val ron = mutableListOf<ClaimCandidate>()
    val meld = mutableListOf<ClaimCandidate>()
    for (step in 1 until SEATS) {
        val seat = (discarder + step) % SEATS
        val player = state.player(seat)
        if (canRon(player.hand, player.melds, code)) {
            ron.add(ClaimCandidate(seat, discarder, code, ClaimType.RON))
        }
        if (canKong(player.hand, player.melds, code)) {
            meld.add(ClaimCandidate(seat, discarder, code, ClaimType.KONG))
        } else if (canPong(player.hand, player.melds, code)) {
            meld.add(ClaimCandidate(seat, discarder, code, ClaimType.PONG))
        }
    }
    return ron + meld
}
