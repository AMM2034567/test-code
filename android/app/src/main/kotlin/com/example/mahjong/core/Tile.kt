package com.example.mahjong.core

/**
 * 牌型定义。
 *
 * 牌编码规则（与主流麻将平台一致的紧凑字符串编码）：
 *
 *     万(m)  1m..9m
 *     筒(p)  1p..9p
 *     条(s)  1s..9s
 *     字(z)  1z..7z  东南西北中发白
 *
 * 全副牌共 34 种、136 张，每种 4 张。
 */

val SUITS: List<Char> = listOf('m', 'p', 's')

const val HONOR: Char = 'z'

val SUIT_NAMES: Map<Char, String> = mapOf('m' to "万", 'p' to "筒", 's' to "条")

val HONOR_NAMES: List<String> = listOf("东", "南", "西", "北", "中", "发", "白")

private val SUIT_OFFSETS: Map<Char, Int> = mapOf('m' to 0, 'p' to 9, 's' to 18, 'z' to 27)

const val KIND_COUNT: Int = 34
const val TOTAL_TILES: Int = 136
const val COPIES_PER_KIND: Int = 4

private val TILE_REGEX: Regex = Regex("^(?:([1-9])[mps]|([1-7])z)$")

/**
 * 一张牌：花色 + 序数。
 */
data class Tile(val suit: Char, val rank: Int) {

    /** 牌编码，如 `5m`。 */
    val code: String
        get() = "$rank$suit"

    /** 在 0..33 的牌种类编号。 */
    val index: Int
        get() = (SUIT_OFFSETS[suit] ?: throw IllegalArgumentException("非法花色: $suit")) + rank - 1

    /** 中文牌名，如 1万 / 中。 */
    val name: String
        get() = if (isHonor) {
            HONOR_NAMES[rank - 1]
        } else {
            "$rank${SUIT_NAMES[suit] ?: throw IllegalArgumentException("非法花色: $suit")}"
        }

    /** 是否字牌。 */
    val isHonor: Boolean
        get() = suit == HONOR

    /** 幺九牌：序数 1/9 或字牌。 */
    val isTerminal: Boolean
        get() = isHonor || rank == 1 || rank == 9

    companion object {

        /** 解析牌编码，非法输入抛 [IllegalArgumentException]。 */
        fun fromCode(code: String): Tile {
            val match = TILE_REGEX.matchEntire(code)
                ?: throw IllegalArgumentException("非法牌编码: $code")
            val digit = match.groupValues[1]
            return if (digit.isNotEmpty()) {
                Tile(suit = code[1], rank = digit.toInt())
            } else {
                Tile(suit = HONOR, rank = match.groupValues[2].toInt())
            }
        }
    }
}

/** 全部 34 种牌编码，按牌种类编号升序。 */
val ALL_CODES: List<String> =
    SUITS.flatMap { suit -> (1..9).map { rank -> "$rank$suit" } } + (1..7).map { rank -> "${rank}z" }

/** 十三幺所需的 13 种幺九/字牌。 */
val TERMINAL_HONOR_CODES: List<String> = ALL_CODES.filter { Tile.fromCode(it).isTerminal }

/** 解析牌编码，非法输入抛 [IllegalArgumentException]。 */
fun parseTile(code: String): Tile = Tile.fromCode(code)

/** 牌种类编号 0..33。 */
fun tileIndex(code: String): Int = Tile.fromCode(code).index

/** 牌的中文名。 */
fun tileName(code: String): String = Tile.fromCode(code).name

/** 按 万→筒→条→字、序数升序排列。 */
fun sortTiles(codes: Iterable<String>): List<String> = codes.sortedBy { tileIndex(it) }

/** 构建未洗牌的全副牌（136 张）。 */
fun buildWall(): List<String> = ALL_CODES.flatMap { code -> List(COPIES_PER_KIND) { code } }

/** 校验一组牌编码均可解析，非法输入抛 [IllegalArgumentException]。 */
fun validateCodes(codes: Iterable<String>) {
    codes.forEach { Tile.fromCode(it) }
}
