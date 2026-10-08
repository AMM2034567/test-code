package com.example.mahjong.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.random.Random

class GameTest {

    private fun assertMessage(expected: String, block: () -> Any) {
        try {
            block()
            fail("expected an exception containing: $expected")
        } catch (exc: IllegalArgumentException) {
            assertTrue("message was: ${exc.message}", exc.message!!.contains(expected))
        }
    }

    /** 一副已可自摸和牌的四人局面（与 Python 参考实现校验一致）。 */
    private fun winningState() = GameState(
        players = mutableListOf(
            PlayerState(
                seat = 0,
                hand = mutableListOf(
                    "1m", "2m", "3m", "4m", "5m", "6m", "7m", "8m",
                    "9m", "1p", "1p", "1p", "5s", "5s",
                ),
            ),
            PlayerState(
                seat = 1,
                hand = mutableListOf(
                    "1s", "2s", "3s", "4s", "5s", "6s", "7s", "8s",
                    "9s", "1z", "1z", "1z", "2z",
                ),
            ),
            PlayerState(
                seat = 2,
                hand = mutableListOf(
                    "1p", "2p", "3p", "4p", "5p", "6p", "7p", "8p",
                    "9p", "2z", "2z", "2z", "3z",
                ),
            ),
            PlayerState(
                seat = 3,
                hand = mutableListOf(
                    "1m", "1m", "1m", "9m", "9m", "9m", "5z", "5z",
                    "5z", "6z", "6z", "6z", "7z",
                ),
            ),
        ),
        wall = mutableListOf("7z"),
        dealer = 0,
        turn = 0,
    )

    @Test
    fun `shuffle keeps every tile and is reproducible`() {
        val wall = buildWall()
        val shuffled = shuffleTiles(wall, Random(42))
        assertEquals(136, shuffled.size)
        assertEquals(wall.sorted(), shuffled.sorted())
        assertEquals(shuffled, shuffleTiles(wall, Random(42)))
        assertFalse(wall == shuffled)
    }

    @Test
    fun `deal gives the dealer 14 tiles and the others 13`() {
        val deck = shuffleTiles(buildWall(), Random(1))
        val (hands, wall) = dealTiles(deck)
        assertEquals(4, hands.size)
        assertEquals(14, hands[0].size)
        assertEquals(listOf(13, 13, 13), hands.drop(1).map { it.size })
        assertEquals(83, wall.size)
        assertEquals(136, hands.sumOf { it.size } + wall.size)
        assertEquals(136, (hands.flatten() + wall).size)
    }

    @Test
    fun `deal rejects impossible requests`() {
        val deck = buildWall()
        assertMessage("仅支持 4 人麻将") { dealTiles(deck, numPlayers = 3) }
        assertMessage("庄家座位号非法: 4") { dealTiles(deck, dealer = 4) }
        assertMessage("庄家座位号非法: -1") { dealTiles(deck, dealer = -1) }
        assertMessage("牌数量不足以发牌") { dealTiles(deck.dropLast(90)) }
    }

    @Test
    fun `new game deals a playable table`() {
        val state = newGame(seed = 1)
        state.validate()
        assertEquals(83, state.wall.size)
        assertEquals(listOf(14, 13, 13, 13), state.players.map { it.hand.size })
        assertEquals(0, state.dealer)
        assertEquals(0, state.turn)
        assertEquals("playing", state.phase)
        assertNull(state.winner)
        assertNull(state.lastAction)
        state.players.forEach { assertEquals(sortTiles(it.hand), it.hand) }

        val counts = mutableMapOf<String, Int>()
        (state.wall + state.players.flatMap { it.hand }).forEach { counts[it] = (counts[it] ?: 0) + 1 }
        assertEquals(136, counts.values.sum())
        assertTrue(counts.values.all { it <= COPIES_PER_KIND })
        assertEquals(COPIES_PER_KIND, counts.getValue("5m"))
    }

    @Test
    fun `the dealer seat is configurable`() {
        val state = newGame(dealer = 2, seed = 4)
        assertEquals(2, state.dealer)
        assertEquals(2, state.turn)
        assertEquals(14, state.player(2).hand.size)
        assertEquals(13, state.player(0).hand.size)
    }

    @Test
    fun `dealing is deterministic per seed`() {
        assertEquals(newGame(seed = 9).toJson(), newGame(seed = 9).toJson())
        assertEquals(newGame(rng = Random(9)).toJson(), newGame(rng = Random(9)).toJson())
        assertFalse(newGame(seed = 9).toJson() == newGame(seed = 10).toJson())
    }

    @Test
    fun `a full round of draws and discards ends in a draw`() {
        val state = newGame(seed = 5)
        discardTile(state, state.player(state.turn).hand.last())
        while (state.wall.isNotEmpty()) {
            drawTile(state)
            discardTile(state, state.player(state.turn).hand.last())
        }
        assertEquals(0, state.wall.size)
        declareDraw(state)
        assertEquals("finished", state.phase)
        assertNull(state.winner)
        assertEquals("draw_game", state.lastAction)
        assertEquals(emptyList<String>(), state.winPatterns)
        assertMessage("对局已结束") { declareDraw(state) }
    }

    @Test
    fun `drawing requires a discard first`() {
        val state = newGame(seed = 2)
        assertMessage("应先打牌再摸牌") { drawTile(state) }
    }

    @Test
    fun `drawing from an empty wall is rejected`() {
        val state = winningState()
        state.wall.clear()
        state.turn = 1
        assertMessage("牌山已空") { drawTile(state) }
    }

    @Test
    fun `discard rotates the turn and drawing follows it`() {
        val state = newGame(seed = 2)
        val tile = state.player(0).hand.first()
        val wallBefore = state.wall.size

        assertEquals(tile, discardTile(state, tile))
        assertEquals(1, state.turn)
        assertEquals(listOf(tile), state.player(0).discards)
        assertEquals(13, state.player(0).hand.size)
        assertEquals("discard:$tile", state.lastAction)
        assertEquals(wallBefore, state.wall.size)

        val drawn = drawTile(state)
        assertEquals(14, state.player(1).hand.size)
        assertEquals("draw:$drawn", state.lastAction)
        assertTrue(drawn in state.player(1).hand)

        assertMessage("手牌中没有") { discardTile(state, "9z") }
    }

    @Test
    fun `a hand that cannot win is not declared as a win`() {
        val state = winningState()
        state.players[0].hand = mutableListOf(
            "1m", "2m", "4m", "5m", "7m", "8m", "9m", "1p",
            "3p", "5p", "7p", "9p", "2s", "4s",
        )
        state.validate()
        assertFalse(declareWin(state, 0))
        assertEquals("playing", state.phase)
        assertNull(state.winner)
        assertNull(state.lastAction)
    }

    @Test
    fun `a winning hand finishes the game`() {
        val state = winningState()
        state.validate()
        assertTrue(declareWin(state, 0))
        assertEquals("finished", state.phase)
        assertEquals(0, state.winner)
        assertEquals(listOf(PATTERN_STANDARD), state.winPatterns)
        assertEquals("win:standard", state.lastAction)
        assertEquals(state, GameState.fromJson(state.toJson()))

        assertMessage("对局已结束") { declareWin(state, 0) }
        assertMessage("对局已结束") { declareDraw(state) }
        assertMessage("对局已结束") { declareWin(state, 1) }
    }

    @Test
    fun `declare win needs a full hand`() {
        val state = winningState()
        state.players[0].hand.removeAt(state.players[0].hand.lastIndex)
        assertFalse(declareWin(state, 0))
        assertEquals("playing", state.phase)
    }

    @Test
    fun `declaring a draw needs an empty wall`() {
        val state = winningState()
        assertMessage("牌山还有 1 张") { declareDraw(state) }
        state.wall.clear()
        declareDraw(state)
        assertEquals("finished", state.phase)
        assertNull(state.winner)
        assertEquals("draw_game", state.lastAction)
    }

    @Test
    fun `waits are computed for a thirteen tile hand`() {
        val state = winningState()
        assertEquals(listOf("2z"), waitsFor(state, 1))
        assertEquals(emptyList<String>(), waitsFor(state, 0))
        assertEquals(state.player(1).hand.size, 13)
        assertTrue(waitsFor(state, 1).all { isWin(state.player(1).hand + it) })
    }

    @Test
    fun `waits need a legal hand size`() {
        val state = winningState()
        assertMessage("非法座位号") { waitsFor(state, 7) }
        state.players[2].hand.removeAt(state.players[2].hand.lastIndex)
        assertMessage("手牌张数非法") { waitsFor(state, 2) }
    }

    @Test
    fun `state stays serializable during play`() {
        val state = newGame(seed = 13)
        discardTile(state, state.player(0).hand.last())
        val drawn = drawTile(state)
        val restored = GameState.fromJson(state.toJson())
        assertEquals(state, restored)
        assertTrue(drawn in restored.player(restored.turn).hand)
        assertEquals(state.wall, restored.wall)
    }
}
