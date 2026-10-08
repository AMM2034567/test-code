package com.example.mahjong

import com.example.mahjong.ai.AiBrain
import com.example.mahjong.core.ClaimType
import com.example.mahjong.core.GameState
import com.example.mahjong.core.PlayerState
import com.example.mahjong.core.PATTERN_STANDARD
import com.example.mahjong.core.StateException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.random.Random

class GameControllerTest {

    /** 人类手牌里有两张 5m，下家随后一定会打出 5m。 */
    private fun pongTable() = GameState(
        players = mutableListOf(
            PlayerState(
                seat = 0,
                hand = mutableListOf(
                    "5m", "5m", "1m", "4m", "7m", "2p", "5p", "8p", "3s", "6s", "9s", "1z", "1z", "7z",
                ),
            ),
            PlayerState(
                seat = 1,
                hand = mutableListOf(
                    "5m", "1m", "2m", "3m", "7m", "8m", "9m", "1p", "2p", "3p", "4s", "5s", "6s",
                ),
            ),
            PlayerState(
                seat = 2,
                hand = mutableListOf(
                    "9m", "9m", "8m", "8m", "7p", "7p", "6p", "6p", "5s", "5s", "4s", "4s", "3z",
                ),
            ),
            PlayerState(
                seat = 3,
                hand = mutableListOf(
                    "1p", "2p", "3p", "4p", "5p", "8p", "9p", "1s", "2s", "3s", "4z", "5z", "6z",
                ),
            ),
        ),
        wall = mutableListOf("4p"),
        dealer = 0,
        turn = 0,
    ).also { it.validate() }

    /** 人类单钓 6s，下家摸牌后必定打出 6s。 */
    private fun ronTable() = GameState(
        players = mutableListOf(
            PlayerState(
                seat = 0,
                hand = mutableListOf(
                    "1m", "2m", "3m", "4p", "5p", "6p", "7s", "8s", "9s", "1z", "1z", "1z", "6s", "7z",
                ),
            ),
            PlayerState(
                seat = 1,
                hand = mutableListOf(
                    "6s", "1m", "2m", "3m", "4m", "5m", "6m", "7m", "8m", "9m", "1p", "2p", "3p",
                ),
            ),
            PlayerState(
                seat = 2,
                hand = mutableListOf(
                    "9m", "9m", "8m", "8m", "7p", "7p", "6p", "6p", "5s", "5s", "4s", "4s", "3z",
                ),
            ),
            PlayerState(
                seat = 3,
                hand = mutableListOf(
                    "1p", "2p", "3p", "4p", "5p", "8p", "9p", "1s", "2s", "3s", "4z", "5z", "6z",
                ),
            ),
        ),
        wall = mutableListOf("5m"),
        dealer = 0,
        turn = 0,
    ).also { it.validate() }

    private fun assertMessage(expected: String, block: () -> Any) {
        try {
            block()
            fail("expected an exception containing: $expected")
        } catch (exc: IllegalArgumentException) {
            assertTrue("message was: ${exc.message}", exc.message!!.contains(expected))
        }
    }

    @Test
    fun `a new game starts on the dealer's discard`() {
        val game = GameController(rng = Random(3))
        assertEquals(AwaitKind.DISCARD, game.awaitKind)
        assertEquals(0, game.state.turn)
        assertEquals(14, game.state.player(0).hand.size)
        assertEquals(83, game.state.wall.size)
        assertTrue(game.history.isNotEmpty())
    }

    @Test
    fun `the human must play in turn`() {
        val game = GameController(rng = Random(3))
        assertMessage("手牌中没有") { game.humanDiscard("9z") }
        assertMessage("当前没有可响应的鸣牌") { game.humanRespond(false) }
        assertEquals(AwaitKind.DISCARD, game.awaitKind)
    }

    @Test
    fun `three AI players finish a whole game legally`() {
        for (seed in 1..4) {
            val ai = AiBrain(Random(seed), claimChance = 0.6)
            val game = GameController(rng = Random(seed), ai = ai)
            var steps = 0
            while (!game.isFinished && steps < 4000) {
                steps++
                when (game.awaitKind) {
                    AwaitKind.CLAIM -> game.humanRespond(false)
                    AwaitKind.DISCARD -> game.humanDiscard(game.state.player(0).hand.last())
                    else -> game.tick()
                }
                game.state.validate()
            }
            assertTrue("seed=$seed 未在 $steps 步内结束：${game.message}", game.isFinished)
            assertTrue(game.history.isNotEmpty())
            assertEquals(game.state, GameState.fromJson(game.state.toJson()))
        }
    }

    @Test
    fun `the human pongs an AI discard`() {
        val ai = AiBrain(Random(1), claimChance = 1.0)
        val game = GameController(ai = ai, initialState = pongTable())
        assertEquals(AwaitKind.DISCARD, game.awaitKind)

        game.humanDiscard("7z")

        // 下家摸 4p 后必然打出孤张 5m，人类碰之
        assertEquals(AwaitKind.AI, game.awaitKind)
        game.tick()
        assertEquals(AwaitKind.AI, game.awaitKind)
        game.tick()

        assertEquals(AwaitKind.CLAIM, game.awaitKind)
        val claim = game.pendingClaim
        assertNotNull(claim)
        assertEquals(ClaimType.PONG, claim!!.option)
        assertEquals(1, claim.discarder)
        assertEquals("5m", claim.tile)

        game.humanRespond(true)

        assertEquals(listOf("5m", "5m", "5m"), game.state.player(0).melds[0])
        assertEquals(11, game.state.player(0).hand.size)
        assertEquals(0, game.state.turn)
        assertEquals(AwaitKind.DISCARD, game.awaitKind)
        game.state.validate()
        assertTrue(game.message.contains("你碰"))
    }

    @Test
    fun `passing a claim lets the game continue`() {
        val ai = AiBrain(Random(1), claimChance = 0.0)
        val game = GameController(ai = ai, initialState = pongTable())
        game.humanDiscard("7z")
        game.tick()
        game.tick()

        assertEquals(AwaitKind.CLAIM, game.awaitKind)
        game.humanRespond(false)

        assertEquals(emptyList<List<String>>(), game.state.player(0).melds)
        assertEquals(13, game.state.player(0).hand.size)
        assertEquals(AwaitKind.AI, game.awaitKind)
        game.state.validate()
    }

    @Test
    fun `the human rons an AI discard`() {
        val ai = AiBrain(Random(2), claimChance = 1.0)
        val game = GameController(ai = ai, initialState = ronTable())

        game.humanDiscard("7z")
        assertEquals(AwaitKind.AI, game.awaitKind)
        game.tick()
        assertEquals(AwaitKind.AI, game.awaitKind)
        game.tick()

        assertEquals(AwaitKind.CLAIM, game.awaitKind)
        val claim = game.pendingClaim
        assertNotNull(claim)
        assertEquals(ClaimType.RON, claim!!.option)
        assertEquals("6s", claim.tile)

        game.humanRespond(true)

        assertTrue(game.isFinished)
        assertEquals(AwaitKind.FINISHED, game.awaitKind)
        assertEquals(0, game.state.winner)
        assertEquals(listOf(PATTERN_STANDARD), game.state.winPatterns)
        assertEquals("ron:standard", game.state.lastAction)
        game.state.validate()
        assertEquals(game.state, GameState.fromJson(game.state.toJson()))
        assertTrue(game.message.contains("荣和"))
    }

    @Test
    fun `the winner is announced in the history`() {
        val game = GameController(ai = AiBrain(Random(2)), initialState = ronTable())
        game.humanDiscard("7z")
        game.tick()
        game.tick()
        game.humanRespond(true)
        assertTrue(game.history.last().contains("荣和"))
        assertEquals(game.message, game.history.last())
    }

    @Test
    fun `starting a new game resets the table`() {
        val game = GameController(rng = Random(5))
        game.humanDiscard(game.state.player(0).hand.last())
        while (!game.isFinished && game.awaitKind == AwaitKind.AI) {
            game.tick()
        }
        game.newGame(seed = 8)
        assertEquals(AwaitKind.DISCARD, game.awaitKind)
        assertEquals(0, game.state.turn)
        assertEquals(14, game.state.player(0).hand.size)
        assertEquals(83, game.state.wall.size)
        assertEquals(null, game.state.winner)
        assertEquals(1, game.history.size)
        game.state.validate()
    }

    @Test
    fun `seat names follow the table direction`() {
        val game = GameController(humanSeat = 0, rng = Random(1))
        assertEquals("你", game.seatName(0))
        assertEquals("下家", game.seatName(1))
        assertEquals("对家", game.seatName(2))
        assertEquals("上家", game.seatName(3))
    }

    @Test
    fun `state exceptions are reported for illegal human moves`() {
        val game = GameController(ai = AiBrain(Random(2)), initialState = ronTable())
        try {
            game.humanDeclareWin()
            fail("expected a failure")
        } catch (exc: StateException) {
            assertTrue(exc.message!!.contains("无法和牌"))
        }
        assertEquals(AwaitKind.DISCARD, game.awaitKind)
        game.state.validate()
    }
}
