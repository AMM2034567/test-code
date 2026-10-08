package com.example.mahjong.ai

import com.example.mahjong.core.ALL_CODES
import com.example.mahjong.core.ClaimType
import com.example.mahjong.core.buildWall
import com.example.mahjong.core.sortTiles
import com.example.mahjong.core.tileIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class AiTest {

    private fun randomHand(rng: Random): List<String> {
        val pool = buildWall().shuffled(rng)
        val size = rng.nextInt(10, 15)
        return sortTiles(pool.take(size))
    }

    /** 与 [code] 不同花色、且和它无关联的一组连张，用来当“必须保留”的背景牌。 */
    private fun backgroundFor(code: String): List<String> = when (tileIndex(code) / 9) {
        0 -> (1..9).map { "${it}p" } + listOf("1s", "2s", "3s", "4s")
        1 -> (1..9).map { "${it}m" } + listOf("1s", "2s", "3s", "4s")
        else -> (1..9).map { "${it}m" } + listOf("1p", "2p", "3p", "4p")
    }

    @Test
    fun `discards are always taken from the hand`() {
        val brain = AiBrain(Random(7))
        repeat(300) {
            val hand = randomHand(Random(it))
            val tile = brain.chooseDiscard(hand)
            assertTrue("tile $tile not in $hand", tile in hand)
        }
    }

    @Test
    fun `an isolated terminal is discarded before connected tiles`() {
        val brain = AiBrain(Random(0))
        val hand = listOf(
            "1m", "9p", "9p", "1s", "2s", "3s", "4s", "5s", "6s", "7s", "8s", "9s", "5z", "5z",
        )
        assertEquals("1m", brain.chooseDiscard(hand))
    }

    @Test
    fun `lone tiles go before pairs and sequences`() {
        val brain = AiBrain(Random(3))
        val hand = listOf(
            "7z", "1m", "2m", "3m", "4m", "5m", "6m", "7m", "8m", "1p", "2p", "3p", "4z", "4z",
        )
        assertEquals("7z", brain.chooseDiscard(hand))
    }

    @Test
    fun `ties are broken randomly among the worst tiles`() {
        val hand = listOf(
            "1m", "9m", "2p", "3p", "4p", "5p", "6p", "7p", "8p", "9p", "1s", "2s", "3s", "4s",
        )
        val brain = AiBrain(Random(5))
        val chosen = mutableSetOf<String>()
        repeat(50) { chosen.add(brain.chooseDiscard(hand)) }
        // 1m 与 9m 都是孤张幺九，随机挑选应能覆盖两者
        assertEquals(setOf("1m", "9m"), chosen)
    }

    @Test
    fun `every kind of tile can end up being discarded`() {
        val brain = AiBrain(Random(9))
        for (code in ALL_CODES) {
            val hand = listOf(code) + backgroundFor(code)
            assertEquals("孤立的 $code 应被优先打出", code, brain.chooseDiscard(hand))
        }
    }

    @Test
    fun `ron is always accepted and melds follow the chance`() {
        assertTrue(AiBrain(Random(1)).chooseClaim(ClaimType.RON))
        assertTrue(AiBrain(Random(2)).chooseClaim(ClaimType.RON))
        assertTrue(AiBrain(Random(3), claimChance = 1.0).chooseClaim(ClaimType.PONG))
        assertTrue(AiBrain(Random(3), claimChance = 1.0).chooseClaim(ClaimType.KONG))
        assertFalse(AiBrain(Random(3), claimChance = 0.0).chooseClaim(ClaimType.PONG))
        assertFalse(AiBrain(Random(3), claimChance = 0.0).chooseClaim(ClaimType.KONG))
    }

    @Test
    fun `concealed kong follows the chance`() {
        assertTrue(AiBrain(Random(4), claimChance = 1.0).wantsConcealedKong())
        assertFalse(AiBrain(Random(4), claimChance = 0.0).wantsConcealedKong())
    }

    @Test
    fun `empty hands are rejected`() {
        try {
            AiBrain(Random(0)).chooseDiscard(emptyList())
            throw AssertionError("expected failure")
        } catch (exc: IllegalArgumentException) {
            assertTrue(exc.message!!.contains("手牌为空"))
        }
    }
}
