package com.example.mahjong.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WinTest {

    private val standardHand = listOf(
        "1m", "2m", "3m", "4m", "5m", "6m", "7m", "8m", "9m",
        "1p", "1p", "1p", "5s", "5s",
    )

    private val sevenPairsHand = listOf(
        "1m", "1m", "2m", "2m", "3m", "3m", "4p", "4p",
        "5p", "5p", "6s", "6s", "7z", "7z",
    )

    private val thirteenOrphansHand = listOf(
        "1m", "9m", "1p", "9p", "1s", "9s", "1z", "2z",
        "3z", "4z", "5z", "6z", "7z", "7z",
    )

    @Test
    fun `standard pattern is four melds plus a pair`() {
        assertEquals(listOf(PATTERN_STANDARD), winPatterns(standardHand))
        assertTrue(isWin(standardHand))
    }

    @Test
    fun `seven pairs pattern`() {
        assertEquals(listOf(PATTERN_SEVEN_PAIRS), winPatterns(sevenPairsHand))
        assertTrue(isWin(sevenPairsHand))
    }

    @Test
    fun `thirteen orphans pattern`() {
        assertEquals(listOf(PATTERN_THIRTEEN_ORPHANS), winPatterns(thirteenOrphansHand))
        assertTrue(isWin(thirteenOrphansHand))
    }

    @Test
    fun `hand can match several patterns at once`() {
        val both = listOf(
            "1m", "1m", "2m", "2m", "3m", "3m", "4m", "4m",
            "5m", "5m", "6m", "6m", "7m", "7m",
        )
        assertEquals(listOf(PATTERN_STANDARD, PATTERN_SEVEN_PAIRS), winPatterns(both))
    }

    @Test
    fun `four identical tiles count as two pairs`() {
        val hand = listOf(
            "1m", "1m", "1m", "1m", "2m", "2m", "3m", "3m",
            "4m", "4m", "5m", "5m", "6m", "6m",
        )
        assertEquals(listOf(PATTERN_STANDARD, PATTERN_SEVEN_PAIRS), winPatterns(hand))
    }

    @Test
    fun `incomplete hands are not wins`() {
        val hand = listOf("1m", "2m", "4m", "5m", "7m", "8m", "9m", "1p", "3p", "5p", "7p", "9p", "2s", "4s")
        assertEquals(emptyList<String>(), winPatterns(hand))
        assertFalse(isWin(hand))
        assertEquals(emptyList<String>(), winPatterns(standardHand.dropLast(1) + "6p"))
    }

    @Test
    fun `open melds contribute to the 14 tile total`() {
        val melds = listOf(listOf("1m", "2m", "3m"))
        val hand = listOf(
            "4m", "5m", "6m", "7m", "8m", "9m",
            "1p", "1p", "1p", "5s", "5s",
        )
        assertEquals(listOf(PATTERN_STANDARD), winPatterns(hand, melds))
        assertTrue(isWin(hand, melds))
    }

    @Test
    fun `winning tiles account for open melds`() {
        val melds = listOf(listOf("1m", "2m", "3m"))
        val hand = listOf("4m", "5m", "6m", "7m", "8m", "9m", "1p", "1p", "1p", "5s")
        assertEquals(listOf("5s"), winningTiles(hand, melds))
    }

    @Test
    fun `wrong tile totals are rejected`() {
        for (size in listOf(0, 13, 15)) {
            val hand = List(size) { ALL_CODES[0] }
            try {
                winPatterns(hand)
                throw AssertionError("expected failure for $size tiles")
            } catch (exc: IllegalArgumentException) {
                assertTrue(exc.message!!.contains("和牌需要 14 张"))
            }
        }
        try {
            winningTiles(standardHand)
            throw AssertionError("expected failure for 14 tiles")
        } catch (exc: IllegalArgumentException) {
            assertTrue(exc.message!!.contains("听牌需要 13 张"))
        }
    }

    @Test
    fun `winning tiles of a tanki wait`() {
        val hand = listOf(
            "1m", "2m", "3m", "4m", "5m", "6m", "7m", "8m", "9m",
            "1p", "1p", "1p", "5s",
        )
        assertEquals(listOf("5s"), winningTiles(hand))
    }

    @Test
    fun `winning tiles stay in tile index order`() {
        val hand = listOf(
            "1m", "2m", "3m", "4m", "5m", "6m", "7m", "8m", "9m",
            "1p", "2p", "3p", "4p",
        )
        val waits = winningTiles(hand)
        assertTrue(waits.isNotEmpty())
        assertEquals(waits.sortedBy { tileIndex(it) }, waits)
        assertTrue(waits.all { isWin(hand + it) })
    }

    @Test
    fun `valid melds`() {
        assertTrue(isValidMeld(listOf("1m", "2m", "3m")))
        assertTrue(isValidMeld(listOf("7s", "8s", "9s")))
        assertTrue(isValidMeld(listOf("7z", "7z", "7z")))
        assertTrue(isValidMeld(listOf("1z", "1z", "1z")))
        assertFalse(isValidMeld(listOf("1m", "2m", "4m")))
        assertFalse(isValidMeld(listOf("1m", "1m", "2m")))
        // 字牌无顺子
        assertFalse(isValidMeld(listOf("5z", "6z", "7z")))
        // 跨花色 / 跨九
        assertFalse(isValidMeld(listOf("9s", "1z", "2z")))
        assertFalse(isValidMeld(listOf("8s", "9s", "1s")))
        assertFalse(isValidMeld(listOf("9m", "1m", "2m")))
        // 尺寸与编码
        assertFalse(isValidMeld(listOf("1m", "2m")))
        assertFalse(isValidMeld(listOf("1m", "2m", "3m", "4m")))
        assertFalse(isValidMeld(listOf("1m", "2m", "zz")))
    }

    @Test
    fun `invalid codes raise when counting a hand`() {
        try {
            winPatterns(listOf("1x", "2m", "3m", "4m", "5m", "6m", "7m", "8m", "9m", "1p", "2p", "3p", "4p", "5p"))
            throw AssertionError("expected failure")
        } catch (exc: IllegalArgumentException) {
            assertTrue(exc.message!!.contains("非法牌编码"))
        }
    }
}
