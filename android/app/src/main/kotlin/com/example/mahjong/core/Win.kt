package com.example.mahjong.core

/**
 * 胡牌判定。
 *
 * 支持的和牌牌型：
 *
 * - [PATTERN_STANDARD]          四副（顺子/刻子）+ 一对将
 * - [PATTERN_SEVEN_PAIRS]       七对子（仅 14 张门清时，四张相同计为两对）
 * - [PATTERN_THIRTEEN_ORPHANS]  十三幺（13 种幺九字牌各一张，其中一种成对）
 */

const val PATTERN_STANDARD: String = "standard"
const val PATTERN_SEVEN_PAIRS: String = "seven_pairs"
const val PATTERN_THIRTEEN_ORPHANS: String = "thirteen_orphans"

private const val MELD_SIZE = 3
private const val KONG_SIZE = 4
private const val HAND_SIZE = 14
private const val NUMBERED_KINDS = 27

private val TERMINAL_HONOR_INDEXES: Set<Int> = TERMINAL_HONOR_CODES.map { tileIndex(it) }.toSet()

private fun countArray(codes: Iterable<String>): IntArray {
    val counts = IntArray(KIND_COUNT)
    for (code in codes) {
        counts[tileIndex(code)]++
    }
    return counts
}

/** 剩余牌能否全部拆成顺子/刻子（回溯搜索）。 */
private fun canFormMelds(counts: IntArray): Boolean {
    var i = 0
    while (i < counts.size && counts[i] == 0) {
        i++
    }
    if (i == counts.size) {
        return true
    }
    // 刻子
    if (counts[i] >= MELD_SIZE) {
        counts[i] -= MELD_SIZE
        val ok = canFormMelds(counts)
        counts[i] += MELD_SIZE
        if (ok) {
            return true
        }
    }
    // 顺子：仅数牌，且必须是同花色的起点（i 为最左侧非零，只能当首张）
    if (i < NUMBERED_KINDS && (i % 9) <= 6 && counts[i + 1] > 0 && counts[i + 2] > 0) {
        counts[i] -= 1
        counts[i + 1] -= 1
        counts[i + 2] -= 1
        val ok = canFormMelds(counts)
        counts[i] += 1
        counts[i + 1] += 1
        counts[i + 2] += 1
        if (ok) {
            return true
        }
    }
    return false
}

private fun isStandard(counts: IntArray): Boolean {
    for (pair in counts.indices) {
        if (counts[pair] < 2) {
            continue
        }
        counts[pair] -= 2
        val ok = canFormMelds(counts)
        counts[pair] += 2
        if (ok) {
            return true
        }
    }
    return false
}

private fun isSevenPairs(counts: IntArray): Boolean {
    if (counts.sum() != HAND_SIZE) {
        return false
    }
    var pairs = 0
    for (count in counts) {
        if (count != 0 && count != 2 && count != 4) {
            return false
        }
        pairs += count / 2
    }
    return pairs == 7
}

private fun isThirteenOrphans(counts: IntArray): Boolean {
    if (counts.sum() != HAND_SIZE) {
        return false
    }
    var pairs = 0
    for (index in 0 until KIND_COUNT) {
        val count = counts[index]
        if (index in TERMINAL_HONOR_INDEXES) {
            if (count == 2) {
                pairs++
            } else if (count != 1) {
                return false
            }
        } else if (count != 0) {
            return false
        }
    }
    return pairs == 1
}

/** 一组副露是否为合法刻子、杠子或顺子（杠子为四张相同的牌）。 */
fun isValidMeld(codes: List<String>): Boolean {
    if (codes.size != MELD_SIZE && codes.size != KONG_SIZE) {
        return false
    }
    val indexes = try {
        codes.map { tileIndex(it) }
    } catch (_: IllegalArgumentException) {
        return false
    }
    if (indexes.toSet().size == 1) {
        // 刻子（3 张）或杠子（4 张）
        return true
    }
    if (codes.size != MELD_SIZE) {
        // 杠子必须四张相同
        return false
    }
    val sorted = indexes.sorted()
    val first = sorted[0]
    val second = sorted[1]
    val third = sorted[2]
    if (first >= NUMBERED_KINDS) {
        // 字牌无顺子
        return false
    }
    return second == first + 1 && third == first + 2 && first / 9 == third / 9
}

/** 返回手牌能构成的和牌牌型列表（可能同时命中多种）。 */
fun winPatterns(hand: Iterable<String>, melds: List<List<String>> = emptyList()): List<String> {
    val handList = hand.toList()
    val total = handList.size + MELD_SIZE * melds.size
    if (total != HAND_SIZE) {
        throw IllegalArgumentException("和牌需要 $HAND_SIZE 张（含副露），当前 $total 张")
    }

    val counts = countArray(handList)
    val patterns = mutableListOf<String>()
    if (isStandard(counts)) {
        patterns.add(PATTERN_STANDARD)
    }
    if (melds.isEmpty()) {
        if (isSevenPairs(counts)) {
            patterns.add(PATTERN_SEVEN_PAIRS)
        }
        if (isThirteenOrphans(counts)) {
            patterns.add(PATTERN_THIRTEEN_ORPHANS)
        }
    }
    return patterns
}

/** 是否和牌。 */
fun isWin(hand: Iterable<String>, melds: List<List<String>> = emptyList()): Boolean =
    winPatterns(hand, melds).isNotEmpty()

/** 听牌时能和的牌，按牌种类编号升序返回。 */
fun winningTiles(hand: Iterable<String>, melds: List<List<String>> = emptyList()): List<String> {
    val handList = hand.toList()
    val total = handList.size + MELD_SIZE * melds.size
    if (total != HAND_SIZE - 1) {
        throw IllegalArgumentException("听牌需要 ${HAND_SIZE - 1} 张（含副露），当前 $total 张")
    }
    return ALL_CODES.filter { code -> isWin(handList + code, melds) }
}
