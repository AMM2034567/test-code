package com.example.mahjong.core

import kotlin.random.Random

/**
 * 洗牌、发牌与对局流程。
 *
 * 流程约定（国标/常见四人麻将简化规则，无王牌区）：
 *
 * 1. [newGame] 洗牌并发牌：庄家 14 张，其余各家 13 张，剩余 83 张为牌山；
 * 2. 当前玩家若手牌为 13-3×副露 张，先 [drawTile] 摸牌；
 * 3. [discardTile] 打出一张并轮转到下家；
 * 4. [declareWin] 自摸和牌，或牌山摸完后 [declareDraw] 流局。
 */

private const val SEATS = 4
private const val MELD_SIZE = 3
private const val STARTING_TILES = 13

/** Fisher–Yates 洗牌，返回新列表；可用 [rng] 注入以便测试复现。 */
fun shuffleTiles(tiles: List<String>, rng: Random = Random.Default): List<String> {
    val deck = tiles.toMutableList()
    var i = deck.size - 1
    while (i > 0) {
        val j = rng.nextInt(i + 1)
        val temp = deck[i]
        deck[i] = deck[j]
        deck[j] = temp
        i--
    }
    return deck
}

/** 发牌：庄家 14 张，其余各家 13 张。返回 Pair(各家手牌, 剩余牌山)。 */
fun dealTiles(
    deck: List<String>,
    dealer: Int = 0,
    numPlayers: Int = SEATS,
): Pair<List<List<String>>, List<String>> {
    if (numPlayers != SEATS) {
        throw IllegalArgumentException("仅支持 $SEATS 人麻将")
    }
    if (dealer !in 0 until numPlayers) {
        throw IllegalArgumentException("庄家座位号非法: $dealer")
    }
    if (deck.size < STARTING_TILES * numPlayers + 1) {
        throw IllegalArgumentException("牌数量不足以发牌")
    }

    val hands = MutableList(numPlayers) { mutableListOf<String>() }
    var cursor = 0

    fun take(count: Int): List<String> {
        val tiles = deck.subList(cursor, cursor + count).toList()
        cursor += count
        return tiles
    }

    repeat(3) {
        for (seat in 0 until numPlayers) {
            hands[seat].addAll(take(4))
        }
    }
    for (seat in 0 until numPlayers) {
        hands[seat].addAll(take(1))
    }
    hands[dealer].addAll(take(1))
    return hands.map { it.toList() } to deck.subList(cursor, deck.size).toList()
}

/** 洗牌 + 发牌，返回可序列化的初始对局状态。 */
fun newGame(dealer: Int = 0, seed: Int? = null, rng: Random? = null): GameState {
    val source = rng ?: if (seed != null) Random(seed) else Random.Default
    val deck = shuffleTiles(buildWall(), source)
    val (hands, wall) = dealTiles(deck, dealer = dealer)
    val players = MutableList(SEATS) { seat ->
        PlayerState(seat = seat, hand = sortTiles(hands[seat]).toMutableList())
    }
    val state = GameState(
        players = players,
        wall = wall.toMutableList(),
        dealer = dealer,
        turn = dealer,
        phase = "playing",
    )
    state.validate()
    return state
}

/** 当前玩家从牌山摸一张牌。 */
fun drawTile(state: GameState): String {
    state.validate()
    val player = state.player(state.turn)
    val expected = STARTING_TILES - MELD_SIZE * player.melds.size
    if (player.hand.size != expected) {
        throw StateException(
            "座位 ${state.turn} 应先打牌再摸牌（当前 ${player.hand.size} 张，应为 $expected 张）"
        )
    }
    if (state.wall.isEmpty()) {
        throw StateException("牌山已空，无法摸牌（流局）")
    }
    val tile = state.wall.removeAt(state.wall.size - 1)
    player.hand = sortTiles(player.hand + tile).toMutableList()
    state.lastAction = "draw:$tile"
    return tile
}

/** 当前玩家打出一张手牌并轮转到下家。 */
fun discardTile(state: GameState, code: String): String {
    state.validate()
    val player = state.player(state.turn)
    val expected = STARTING_TILES + 1 - MELD_SIZE * player.melds.size
    if (player.hand.size != expected) {
        throw StateException(
            "座位 ${state.turn} 应先摸牌再打牌（当前 ${player.hand.size} 张，应为 $expected 张）"
        )
    }
    if (code !in player.hand) {
        throw StateException("座位 ${state.turn} 手牌中没有 $code")
    }
    player.hand.remove(code)
    player.discards.add(code)
    state.turn = (state.turn + 1) % SEATS
    state.lastAction = "discard:$code"
    return code
}

/** 当前对局中该玩家自摸和牌；和牌则置为终局并返回 true。 */
fun declareWin(state: GameState, seat: Int): Boolean {
    state.validate()
    if (state.phase != "playing") {
        throw StateException("对局已结束（阶段 ${state.phase}），无法和牌")
    }
    val player = state.player(seat)
    if (player.hand.size + MELD_SIZE * player.melds.size != STARTING_TILES + 1) {
        return false
    }
    val patterns = winPatterns(player.hand, player.melds)
    if (patterns.isEmpty()) {
        return false
    }
    state.phase = "finished"
    state.winner = seat
    state.winPatterns = patterns.toMutableList()
    state.lastAction = "win:${patterns.joinToString("+")}"
    return true
}

/** 牌山摸完无人和牌，流局。 */
fun declareDraw(state: GameState) {
    state.validate()
    if (state.phase != "playing") {
        throw StateException("对局已结束（阶段 ${state.phase}），无法流局")
    }
    if (state.wall.isNotEmpty()) {
        throw StateException("牌山还有 ${state.wall.size} 张，不能流局")
    }
    state.phase = "finished"
    state.winner = null
    state.winPatterns = mutableListOf()
    state.lastAction = "draw_game"
}

/** 该玩家听的牌：13 张（含副露）时计算，14 张（已摸牌）返回空列表。 */
fun waitsFor(state: GameState, seat: Int): List<String> {
    state.validate()
    val player = state.player(seat)
    val total = player.hand.size + MELD_SIZE * player.melds.size
    if (total == STARTING_TILES) {
        return winningTiles(player.hand, player.melds)
    }
    if (total == STARTING_TILES + 1) {
        return emptyList()
    }
    throw StateException("座位 $seat 手牌张数非法，无法计算听牌")
}
