package com.example.mahjong.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ClaimTest {

    private fun stateOf(
        hands: List<List<String>>,
        wall: List<String> = emptyList(),
        dealer: Int = 0,
        turn: Int = 0,
    ) = GameState(
        players = hands
            .mapIndexed { seat, hand -> PlayerState(seat = seat, hand = hand.toMutableList()) }
            .toMutableList(),
        wall = wall.toMutableList(),
        dealer = dealer,
        turn = turn,
    ).also { it.validate() }

    /** 座位 0 手持 14 张，下家持两张 5m，随时可以碰。 */
    private fun pongTable() = stateOf(
        hands = listOf(
            listOf("1m", "2m", "3m", "4p", "5p", "6p", "7s", "8s", "9s", "1z", "1z", "1z", "5m", "7z"),
            listOf("5m", "5m", "1p", "2p", "3p", "4s", "5s", "6s", "7p", "8p", "9p", "2z", "3z"),
            listOf("9m", "9m", "8m", "7m", "6m", "1s", "2s", "3s", "4z", "4z", "5z", "6z", "3z"),
            listOf("1s", "1s", "2s", "2s", "3s", "3s", "4m", "5m", "6m", "8p", "9p", "1z", "4z"),
        ),
        wall = listOf("9p"),
    )

    /** 下家摸打路线固定，最终会打出 6s，而座位 0 正好单钓 6s。 */
    private fun ronTable() = stateOf(
        hands = listOf(
            listOf("1m", "2m", "3m", "4p", "5p", "6p", "7s", "8s", "9s", "1z", "1z", "1z", "6s", "7z"),
            listOf("6s", "1m", "2m", "3m", "4m", "5m", "6m", "7m", "8m", "9m", "1p", "2p", "3p"),
            listOf("9m", "9m", "8m", "8m", "7p", "7p", "6p", "6p", "5s", "5s", "4s", "4s", "3z"),
            listOf("1p", "2p", "3p", "4p", "5p", "8p", "9p", "1s", "2s", "3s", "4z", "5z", "6z"),
        ),
        wall = listOf("5m"),
    )

    private fun assertStateFailure(expected: String, block: () -> Any) {
        try {
            block()
            fail("expected StateException containing: $expected")
        } catch (exc: StateException) {
            assertTrue("message was: ${exc.message}", exc.message!!.contains(expected))
        }
    }

    @Test
    fun `pong takes the tile out of the river and forms a meld`() {
        val state = pongTable()
        discardTile(state, "5m")
        assertEquals(listOf("5m"), state.player(0).discards)

        declarePong(state, 1, 0, "5m")

        assertEquals(listOf("5m", "5m", "5m"), state.player(1).melds[0])
        assertEquals(11, state.player(1).hand.size)
        assertEquals(13, state.player(0).hand.size)
        assertEquals(emptyList<String>(), state.player(0).discards)
        assertEquals(1, state.turn)
        assertEquals("pong:5m", state.lastAction)
        state.validate()
        assertEquals(state, GameState.fromJson(state.toJson()))
        assertTrue(state.player(1).hand.none { it == "5m" })
    }

    @Test
    fun `pong needs two copies and a legal claim`() {
        val state = pongTable()
        assertStateFailure("最近的弃牌不是 5m") { declarePong(state, 1, 0, "5m") }

        discardTile(state, "5m")
        assertStateFailure("座位 2 无法碰 5m") { declarePong(state, 2, 0, "5m") }
        assertStateFailure("座位 3 无法碰 5m") { declarePong(state, 3, 0, "5m") }
        assertStateFailure("座位 0 无法碰 5m") { declarePong(state, 0, 0, "5m") }
        assertStateFailure("非法座位号") { declarePong(state, 4, 0, "5m") }
        assertEquals("playing", state.phase)
        assertEquals(1, state.player(0).discards.size)
    }

    @Test
    fun `a player cannot claim their own discard`() {
        val state = pongTable()
        discardTile(state, "1z")
        assertEquals(2, state.player(0).hand.count { it == "1z" })
        assertTrue(canPong(state.player(0).hand, state.player(0).melds, "1z"))
        assertStateFailure("不能鸣自己的弃牌") { declarePong(state, 0, 0, "1z") }
    }

    @Test
    fun `open kong is followed by a replacement draw`() {
        val state = pongTable()
        discardTile(state, "5m")
        // 下家换成三张 5m 以便明杠（先移走牌河之外的最后一张 5m，保证每种牌不超过 4 张）
        state.players[3].hand.remove("5m")
        state.players[3].hand.add("7z")
        state.players[1].hand = mutableListOf(
            "5m", "5m", "5m", "1p", "2p", "3p", "4s", "5s", "6s", "7p", "8p", "9p", "2z",
        ).toMutableList()
        state.validate()
        assertTrue(canKong(state.player(1).hand, state.player(1).melds, "5m"))

        declareOpenKong(state, 1, 0, "5m")

        assertEquals(listOf("5m", "5m", "5m", "5m"), state.player(1).melds[0])
        assertEquals(10, state.player(1).hand.size)
        assertEquals(1, state.turn)
        state.validate()

        val drawn = drawTile(state)
        assertEquals(11, state.player(1).hand.size)
        assertTrue(drawn in state.player(1).hand)
        state.validate()
        assertEquals(state, GameState.fromJson(state.toJson()))
    }

    @Test
    fun `concealed kong needs four copies on your own turn`() {
        val state = stateOf(
            hands = listOf(
                listOf("1z", "1z", "1z", "1z", "1m", "2m", "3m", "4p", "5p", "6p", "7s", "8s", "9s", "5m"),
                listOf("9m", "9m", "8m", "8m", "7p", "7p", "6p", "6p", "5s", "5s", "4s", "4s", "3z"),
                listOf("1p", "2p", "3p", "4p", "5p", "8p", "9p", "1s", "2s", "3s", "4z", "5z", "6z"),
                listOf("9s", "8s", "7s", "6s", "5s", "4s", "3s", "2s", "1s", "2z", "3z", "4z", "6z"),
            ),
            wall = listOf("9p"),
        )
        assertEquals(
            listOf("1z"),
            concealedKongs(state.player(0).hand, state.player(0).melds),
        )
        assertEquals(emptyList<String>(), concealedKongs(state.player(1).hand, state.player(1).melds))

        assertStateFailure("座位 1 不是当前回合玩家") { declareConcealedKong(state, 1, "1z") }
        assertStateFailure("座位 0 无法暗杠 5m") { declareConcealedKong(state, 0, "5m") }

        declareConcealedKong(state, 0, "1z")
        assertEquals(listOf("1z", "1z", "1z", "1z"), state.player(0).melds[0])
        assertEquals(10, state.player(0).hand.size)
        assertEquals(0, state.turn)
        assertEquals("ankong:1z", state.lastAction)
        state.validate()

        val drawn = drawTile(state)
        assertEquals(11, state.player(0).hand.size)
        assertTrue(drawn in state.player(0).hand)
        state.validate()
    }

    @Test
    fun `ron finishes the game and keeps the winning tile in the river`() {
        val state = ronTable()
        assertTrue(canRon(state.player(1).hand, state.player(1).melds, "6s"))
        discardTile(state, "6s")

        assertTrue(declareRon(state, 1, 0, "6s"))

        assertEquals("finished", state.phase)
        assertEquals(1, state.winner)
        assertEquals(listOf(PATTERN_STANDARD), state.winPatterns)
        assertEquals("ron:standard", state.lastAction)
        assertEquals(listOf("6s"), state.player(0).discards)
        state.validate()
        assertEquals(state, GameState.fromJson(state.toJson()))
    }

    @Test
    fun `ron needs a winning hand and a fresh discard`() {
        val state = ronTable()
        assertStateFailure("最近没有打出 6s") { declareRon(state, 1, 0, "6s") }

        discardTile(state, "6s")
        assertFalse(declareRon(state, 2, 0, "6s"))
        assertFalse(declareRon(state, 3, 0, "6s"))
        assertEquals("playing", state.phase)
        assertEquals(null, state.winner)
        assertStateFailure("不能荣和自己的弃牌") { declareRon(state, 0, 0, "6s") }
    }

    @Test
    fun `claim candidates prefer ron and follow the turn order`() {
        val state = pongTable()
        discardTile(state, "5m")

        val candidates = claimCandidates(state, 0, "5m")
        assertEquals(listOf(ClaimCandidate(1, 0, "5m", ClaimType.PONG)), candidates)

        // 自己的弃牌不产生候选
        assertEquals(emptyList<ClaimCandidate>(), claimCandidates(state, 1, "5m"))
        // 状态不匹配（非最新弃牌）不产生候选
        assertEquals(emptyList<ClaimCandidate>(), claimCandidates(state, 0, "9z"))

        val ronState = ronTable()
        discardTile(ronState, "6s")
        val ronCandidates = claimCandidates(ronState, 0, "6s")
        assertEquals(1, ronCandidates.size)
        assertEquals(ClaimCandidate(1, 0, "6s", ClaimType.RON), ronCandidates[0])
    }

    @Test
    fun `claim helpers mirror the hand size rules`() {
        // 一副已有一组副露、等待摸牌的 10 张手牌（13 - 3 × 1）
        val hand = listOf("5m", "5m", "5m", "1p", "2p", "3p", "4s", "5s", "6s", "2z")
        val melds = listOf(listOf("1m", "2m", "3m"))
        assertTrue(canPong(hand, melds, "5m"))
        assertTrue(canKong(hand, melds, "5m"))
        assertFalse(canPong(hand, melds, "3z"))
        assertFalse(canKong(hand, melds, "9s"))
        // 已摸牌的 11 张状态（14 - 3 × 1）不能鸣牌
        assertFalse(canPong(hand + "9z", melds, "5m"))
        assertFalse(canKong(hand + "9z", melds, "5m"))
        assertFalse(canRon(hand + "9z", melds, "5m"))
        // 四张相同才可暗杠，且手牌须处于已摸牌状态
        assertEquals(emptyList<String>(), concealedKongs(hand, melds))
        val four = listOf("1z", "1z", "1z", "1z", "1m", "2m", "3m", "4p", "5p", "6p", "7s", "8s", "9s", "5m")
        assertEquals(listOf("1z"), concealedKongs(four, emptyList()))
    }

    @Test
    fun `kong melds are accepted by state validation`() {
        assertTrue(isValidMeld(listOf("5m", "5m", "5m", "5m")))
        assertTrue(isValidMeld(listOf("1z", "1z", "1z", "1z")))
        assertTrue(isValidMeld(listOf("5m", "5m", "5m")))
        assertFalse(isValidMeld(listOf("1m", "2m", "3m", "4m")))
        assertFalse(isValidMeld(listOf("5m", "5m")))
    }
}
