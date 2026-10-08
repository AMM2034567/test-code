package com.example.mahjong.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TileTest {

    @Test
    fun `all codes round trip with sequential indexes`() {
        assertEquals(34, ALL_CODES.size)
        ALL_CODES.forEachIndexed { index, code ->
            assertEquals(code, Tile.fromCode(code).code)
            assertEquals(index, Tile.fromCode(code).index)
            assertEquals(index, tileIndex(code))
        }
        assertEquals(ALL_CODES.size, ALL_CODES.map { tileIndex(it) }.toSet().size)
    }

    @Test
    fun `indexes follow the m p s z layout`() {
        assertEquals(0, tileIndex("1m"))
        assertEquals(8, tileIndex("9m"))
        assertEquals(9, tileIndex("1p"))
        assertEquals(17, tileIndex("9p"))
        assertEquals(18, tileIndex("1s"))
        assertEquals(26, tileIndex("9s"))
        assertEquals(27, tileIndex("1z"))
        assertEquals(33, tileIndex("7z"))
    }

    @Test
    fun `chinese tile names`() {
        assertEquals("1万", tileName("1m"))
        assertEquals("9万", tileName("9m"))
        assertEquals("9筒", tileName("9p"))
        assertEquals("5条", tileName("5s"))
        assertEquals("东", tileName("1z"))
        assertEquals("中", tileName("5z"))
        assertEquals("白", tileName("7z"))
    }

    @Test
    fun `terminal and honor flags`() {
        assertTrue(Tile.fromCode("1m").isTerminal)
        assertTrue(Tile.fromCode("9p").isTerminal)
        assertTrue(Tile.fromCode("5z").isHonor)
        assertTrue(Tile.fromCode("5z").isTerminal)
        assertFalse(Tile.fromCode("5m").isTerminal)
        assertFalse(Tile.fromCode("5m").isHonor)
    }

    @Test
    fun `invalid codes are rejected`() {
        for (code in listOf("", "0m", "10m", "1x", "8z", "1", "m1", "1n", "5M")) {
            try {
                Tile.fromCode(code)
                throw AssertionError("expected failure for: $code")
            } catch (exc: IllegalArgumentException) {
                assertTrue(exc.message!!.contains("非法牌编码"))
            }
        }
    }

    @Test
    fun `validate codes accepts the full wall and rejects bad input`() {
        validateCodes(buildWall())
        try {
            validateCodes(listOf("1m", "zz"))
            throw AssertionError("expected failure")
        } catch (exc: IllegalArgumentException) {
            assertTrue(exc.message!!.contains("非法牌编码"))
        }
    }

    @Test
    fun `terminal and honor codes for thirteen orphans`() {
        assertEquals(
            listOf("1m", "9m", "1p", "9p", "1s", "9s", "1z", "2z", "3z", "4z", "5z", "6z", "7z"),
            TERMINAL_HONOR_CODES,
        )
    }

    @Test
    fun `build wall contains 136 tiles with 4 copies of each kind`() {
        val wall = buildWall()
        assertEquals(136, wall.size)
        for (code in ALL_CODES) {
            assertEquals(4, wall.count { it == code })
        }
    }

    @Test
    fun `sort tiles orders by suit then rank`() {
        val unsorted = listOf("5z", "9s", "1m", "3p", "1z", "7m", "1s")
        assertEquals(
            listOf("1m", "7m", "3p", "1s", "9s", "1z", "5z"),
            sortTiles(unsorted),
        )
        assertEquals(sortTiles(buildWall()), sortTiles(shuffleTiles(buildWall(), kotlin.random.Random(3))))
    }
}
