package com.example.mahjong.ai

import com.example.mahjong.core.ClaimType
import com.example.mahjong.core.tileIndex
import kotlin.random.Random

/**
 * 简单的麻将 AI。
 *
 * 当前策略（后续可继续升级）：
 *
 * - 出牌：按「联络度」给手牌打分，拆掉搭子/对子收益最低的那张；同分时随机。
 * - 鸣牌：荣和必定接受，碰/杠按 [claimChance] 概率接受。
 * - 暗杠：按 [claimChance] 概率进行。
 */
class AiBrain(
    private val rng: Random = Random.Default,
    private val claimChance: Double = DEFAULT_CLAIM_CHANCE,
) {

    /** 选择要打出的牌：永远返回手牌中存在的编码。 */
    fun chooseDiscard(hand: List<String>): String {
        if (hand.isEmpty()) {
            throw IllegalArgumentException("手牌为空，无法出牌")
        }
        val counts = IntArray(KIND_COUNT)
        for (code in hand) {
            counts[tileIndex(code)]++
        }
        val scores = hand.associateWith { utility(it, counts) }
        val lowest = scores.values.min()
        val candidates = hand.distinct().filter { scores[it] == lowest }
        return candidates[rng.nextInt(candidates.size)]
    }

    /** 是否要对弃牌声明 [option]（荣和必接）。 */
    fun chooseClaim(option: ClaimType): Boolean = when (option) {
        ClaimType.RON -> true
        ClaimType.PONG, ClaimType.KONG -> rng.nextDouble() < claimChance
    }

    /** 是否进行暗杠。 */
    fun wantsConcealedKong(): Boolean = rng.nextDouble() < claimChance

    /**
     * 牌的联络度：对子/刻子、同花色相邻牌都会加分，孤张幺九字牌分数最低。
     */
    private fun utility(code: String, counts: IntArray): Double {
        val index = tileIndex(code)
        var score = (counts[index] - 1) * PAIR_WEIGHT
        if (index < NUMBERED_KINDS) {
            val suitStart = index / SUIT_SIZE * SUIT_SIZE
            for ((offset, weight) in NEIGHBOURS) {
                val neighbour = index + offset
                if (neighbour in suitStart until suitStart + SUIT_SIZE) {
                    score += counts[neighbour] * weight
                }
            }
            val rank = index % SUIT_SIZE
            if (rank != 0 && rank != SUIT_SIZE - 1) {
                score += MIDDLE_TILE_BONUS
            }
        }
        return score
    }

    companion object {
        const val DEFAULT_CLAIM_CHANCE: Double = 0.5
        private const val KIND_COUNT = 34
        private const val NUMBERED_KINDS = 27
        private const val SUIT_SIZE = 9
        private const val PAIR_WEIGHT = 2.0
        private const val MIDDLE_TILE_BONUS = 0.25
        private val NEIGHBOURS: List<Pair<Int, Double>> =
            listOf(-2 to 0.7, -1 to 1.5, 1 to 1.5, 2 to 0.7)
    }
}
