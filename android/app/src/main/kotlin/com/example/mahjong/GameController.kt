package com.example.mahjong

import com.example.mahjong.ai.AiBrain
import com.example.mahjong.core.ClaimCandidate
import com.example.mahjong.core.ClaimType
import com.example.mahjong.core.GameState
import com.example.mahjong.core.PATTERN_SEVEN_PAIRS
import com.example.mahjong.core.PATTERN_STANDARD
import com.example.mahjong.core.PATTERN_THIRTEEN_ORPHANS
import com.example.mahjong.core.StateException
import com.example.mahjong.core.claimCandidates
import com.example.mahjong.core.concealedKongs
import com.example.mahjong.core.declareConcealedKong
import com.example.mahjong.core.declareDraw
import com.example.mahjong.core.declareOpenKong
import com.example.mahjong.core.declarePong
import com.example.mahjong.core.declareRon
import com.example.mahjong.core.declareWin
import com.example.mahjong.core.discardTile
import com.example.mahjong.core.drawTile
import com.example.mahjong.core.isWin
import com.example.mahjong.core.tileName
import com.example.mahjong.core.newGame as coreNewGame
import kotlin.random.Random

/** 界面需要响应的对局状态。 */
enum class AwaitKind {
    /** 等待 AI 行动（轮到 AI，或等待自动摸牌）。 */
    AI,

    /** 等待选手打牌。 */
    DISCARD,

    /** 等待选手对他人弃牌表态（和/碰/杠/过）。 */
    CLAIM,

    /** 对局已结束。 */
    FINISHED,
}

/** 牌型中文名。 */
fun patternLabel(pattern: String): String = when (pattern) {
    PATTERN_STANDARD -> "四副一将"
    PATTERN_SEVEN_PAIRS -> "七对子"
    PATTERN_THIRTEEN_ORPHANS -> "十三幺"
    else -> pattern
}

private const val HISTORY_LIMIT = 50
private const val SEATS = 4
private const val MELD_SIZE = 3
private const val DEALER_TILES = 13
private const val DRAW_TILES = 14

/**
 * 对局控制器：人类选手（座位 0）对阵三个 AI。
 *
 * 控制器是纯逻辑、同步的（便于单元测试），由界面按节拍调用 [tick] 推进 AI 行动。
 * 所有需要人类介入的时机都通过 [awaitKind] 暴露：
 *
 * - [AwaitKind.DISCARD]  打牌（含自摸和牌、暗杠）；
 * - [AwaitKind.CLAIM]    对他人弃牌表态（[pendingClaim] 给出候选）；
 * - [AwaitKind.AI]       可以安全地推进一步 [tick]。
 */
class GameController(
    val humanSeat: Int = 0,
    private val rng: Random = Random.Default,
    private val ai: AiBrain = AiBrain(rng),
    initialState: GameState? = null,
) {

    var state: GameState = initialState ?: coreNewGame(dealer = humanSeat, rng = rng)
        private set

    /** 等待人类表态的鸣牌候选。 */
    var pendingClaim: ClaimCandidate? = null
        private set

    /** 最近一条播报。 */
    var message: String = ""
        private set

    /** 播报历史（旧 → 新）。 */
    val history: List<String>
        get() = historyBuffer

    /** 最近一次打出的牌（座位 to 编码）。 */
    var lastDiscard: Pair<Int, String>? = null
        private set

    private val historyBuffer = mutableListOf<String>()
    private var claimQueue: List<ClaimCandidate> = emptyList()
    private var claimIndex = 0

    init {
        announce("洗牌发牌完成，你坐庄先出牌")
    }

    val isFinished: Boolean
        get() = state.phase == "finished"

    /** 当前界面等待的动作类型。 */
    val awaitKind: AwaitKind
        get() = when {
            isFinished -> AwaitKind.FINISHED
            pendingClaim != null -> AwaitKind.CLAIM
            state.turn == humanSeat && awaitingDiscardOfHuman() -> AwaitKind.DISCARD
            else -> AwaitKind.AI
        }

    /** 座位的显示名：自己为「你」，其余按相对方位。 */
    fun seatName(seat: Int): String = when ((seat - humanSeat + SEATS) % SEATS) {
        0 -> "你"
        1 -> "下家"
        2 -> "对家"
        else -> "上家"
    }

    /** 界面可以直接读取的最新播报（失败时用于提示）。 */
    fun report(text: String) {
        message = text
    }

    /** 开新局。 */
    fun newGame(seed: Int? = null) {
        state = if (seed != null) {
            coreNewGame(dealer = humanSeat, seed = seed)
        } else {
            coreNewGame(dealer = humanSeat, rng = rng)
        }
        pendingClaim = null
        claimQueue = emptyList()
        claimIndex = 0
        lastDiscard = null
        historyBuffer.clear()
        announce("洗牌发牌完成，你坐庄先出牌")
    }

    /** 推进一步：摸牌，或让当前 AI 打牌。人类回合不会改动状态。 */
    fun tick() {
        if (isFinished || pendingClaim != null) {
            return
        }
        val seat = state.turn
        val player = state.player(seat)
        if (awaitingDraw(player.hand, player.melds)) {
            drawFor(seat)
            return
        }
        if (seat == humanSeat) {
            return
        }
        val tile = ai.chooseDiscard(player.hand)
        performDiscard(seat, tile)
    }

    /** 人类打出手牌中的 [code]。 */
    fun humanDiscard(code: String) {
        if (awaitKind != AwaitKind.DISCARD) {
            throw StateException("现在还不能打牌")
        }
        performDiscard(humanSeat, code)
    }

    /** 人类自摸和牌。 */
    fun humanDeclareWin() {
        if (awaitKind != AwaitKind.DISCARD) {
            throw StateException("现在还不能和牌")
        }
        if (!declareWin(state, humanSeat)) {
            throw StateException("当前手牌无法和牌")
        }
        announce("你自摸和牌（${winPatternText()}）")
    }

    /** 人类暗杠手牌中的 [code]，随后自动补摸。 */
    fun humanConcealedKong(code: String) {
        if (awaitKind != AwaitKind.DISCARD) {
            throw StateException("现在还不能杠牌")
        }
        declareConcealedKong(state, humanSeat, code)
        announce("你暗杠 ${tileName(code)}，补摸一张")
    }

    /** 人类对 [pendingClaim] 表态：true 接受（和/碰/杠），false 过。 */
    fun humanRespond(accept: Boolean) {
        val candidate = pendingClaim ?: throw StateException("当前没有可响应的鸣牌")
        pendingClaim = null
        if (accept && applyClaim(candidate)) {
            claimQueue = emptyList()
            return
        }
        resolveClaims(claimIndex + 1)
    }

    // ---------- 内部流程 ----------

    private fun awaitingDraw(hand: List<String>, melds: List<List<String>>): Boolean =
        hand.size == DEALER_TILES - MELD_SIZE * melds.size

    private fun awaitingDiscard(hand: List<String>, melds: List<List<String>>): Boolean =
        hand.size == DRAW_TILES - MELD_SIZE * melds.size

    private fun awaitingDiscardOfHuman(): Boolean {
        val player = state.player(humanSeat)
        return awaitingDiscard(player.hand, player.melds)
    }

    private fun drawFor(seat: Int) {
        if (state.wall.isEmpty()) {
            declareDraw(state)
            announce("流局：牌山已空，无人和牌")
            return
        }
        val tile = drawTile(state)
        announce("${seatName(seat)}摸到 ${tileName(tile)}")
        if (seat == humanSeat) {
            // 人类自己决定：和牌按钮 / 暗杠按钮 / 直接打牌
            return
        }
        val player = state.player(seat)
        if (isWin(player.hand, player.melds)) {
            declareWin(state, seat)
            announce("${seatName(seat)}自摸和牌（${winPatternText()}）")
            return
        }
        val kong = concealedKongs(player.hand, player.melds).firstOrNull()
        if (kong != null && ai.wantsConcealedKong()) {
            declareConcealedKong(state, seat, kong)
            announce("${seatName(seat)}暗杠 ${tileName(kong)}，补摸一张")
        }
    }

    private fun performDiscard(seat: Int, code: String) {
        discardTile(state, code)
        lastDiscard = seat to code
        announce("${seatName(seat)}打出 ${tileName(code)}")
        claimQueue = claimCandidates(state, seat, code)
        resolveClaims(0)
    }

    /** 按优先级处理鸣牌候选，遇到人类候选则挂起等待表态。 */
    private fun resolveClaims(start: Int) {
        var index = start
        while (index < claimQueue.size) {
            val candidate = claimQueue[index]
            if (candidate.seat == humanSeat) {
                pendingClaim = candidate
                claimIndex = index
                return
            }
            if (ai.chooseClaim(candidate.option) && applyClaim(candidate)) {
                claimQueue = emptyList()
                return
            }
            index++
        }
        claimQueue = emptyList()
        claimIndex = 0
    }

    /** 执行一次鸣牌；无法执行时返回 false（继续处理后续候选）。 */
    private fun applyClaim(candidate: ClaimCandidate): Boolean {
        val seat = candidate.seat
        val tile = candidate.tile
        return when (candidate.option) {
            ClaimType.RON -> {
                if (declareRon(state, seat, candidate.discarder, tile)) {
                    announce("${seatName(seat)}荣和 ${tileName(tile)}（${winPatternText()}）")
                    true
                } else {
                    false
                }
            }
            ClaimType.PONG -> {
                declarePong(state, seat, candidate.discarder, tile)
                announce("${seatName(seat)}碰 ${tileName(tile)}")
                true
            }
            ClaimType.KONG -> {
                declareOpenKong(state, seat, candidate.discarder, tile)
                announce("${seatName(seat)}杠 ${tileName(tile)}，补摸一张")
                true
            }
        }
    }

    private fun winPatternText(): String = state.winPatterns.joinToString(" + ") { patternLabel(it) }

    private fun announce(text: String) {
        message = text
        historyBuffer.add(text)
        if (historyBuffer.size > HISTORY_LIMIT) {
            historyBuffer.removeAt(0)
        }
    }
}
