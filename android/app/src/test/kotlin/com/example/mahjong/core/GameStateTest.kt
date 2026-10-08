package com.example.mahjong.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GameStateTest {

    /** 与 Python 参考实现构造的同一状态，用于跨语言序列化对齐。 */
    private fun referenceState() = GameState(
        players = mutableListOf(
            PlayerState(
                seat = 0,
                hand = mutableListOf(
                    "1m", "2m", "3m", "4m", "5m", "6m", "7m", "8m",
                    "9m", "1p", "2p", "3p", "4p", "5p",
                ),
                discards = mutableListOf("9p"),
            ),
            PlayerState(
                seat = 1,
                hand = mutableListOf(
                    "1s", "1s", "1s", "5s", "6s", "7s", "8s", "9s", "1z", "1z",
                ),
                melds = mutableListOf(mutableListOf("2p", "3p", "4p")),
                discards = mutableListOf("4z"),
            ),
            PlayerState(
                seat = 2,
                hand = mutableListOf(
                    "2m", "3m", "4m", "5m", "6m", "7m", "8m", "9m",
                    "1p", "1p", "6p", "7p", "8p",
                ),
            ),
            PlayerState(
                seat = 3,
                hand = mutableListOf(
                    "1m", "1m", "1m", "9m", "9m", "2s", "3s", "4s",
                    "5s", "6s", "9s", "7z", "7z",
                ),
                discards = mutableListOf("6z"),
            ),
        ),
        wall = mutableListOf("1z", "5z"),
        dealer = 1,
        turn = 2,
        lastAction = "discard:4z",
    )

    private val referenceJson =
        """{"dealer":1,"last_action":"discard:4z","phase":"playing","players":[{"discards":["9p"],"hand":["1m","2m","3m","4m","5m","6m","7m","8m","9m","1p","2p","3p","4p","5p"],"melds":[],"seat":0},{"discards":["4z"],"hand":["1s","1s","1s","5s","6s","7s","8s","9s","1z","1z"],"melds":[["2p","3p","4p"]],"seat":1},{"discards":[],"hand":["2m","3m","4m","5m","6m","7m","8m","9m","1p","1p","6p","7p","8p"],"melds":[],"seat":2},{"discards":["6z"],"hand":["1m","1m","1m","9m","9m","2s","3s","4s","5s","6s","9s","7z","7z"],"melds":[],"seat":3}],"turn":2,"version":1,"wall":["1z","5z"],"win_patterns":[],"winner":null}"""

    private fun assertStateFailure(expected: String, block: () -> Any) {
        try {
            block()
            fail("expected StateException containing: $expected")
        } catch (exc: StateException) {
            assertTrue("message was: ${exc.message}", exc.message!!.contains(expected))
        }
    }

    @Test
    fun `json output matches the python reference implementation`() {
        val state = referenceState()
        state.validate()
        assertEquals(referenceJson, state.toJson())
    }

    @Test
    fun `json round trip keeps the state identical`() {
        val state = referenceState()
        val restored = GameState.fromJson(referenceJson)
        assertEquals(state, restored)
        assertEquals(referenceJson, restored.toJson())
        assertNull(restored.winner)
        assertEquals("playing", restored.phase)
        assertEquals(1, restored.dealer)
        assertEquals(2, restored.turn)
        assertEquals(listOf("2p", "3p", "4p"), restored.player(1).melds[0])
    }

    @Test
    fun `round trip of a dealt game is stable`() {
        val state = newGame(seed = 42)
        val text = state.toJson()
        assertEquals(state, GameState.fromJson(text))
        assertEquals(text, GameState.fromJson(text).toJson())
    }

    @Test
    fun `dealing with the same seed is deterministic`() {
        assertEquals(newGame(seed = 7).toJson(), newGame(seed = 7).toJson())
    }

    @Test
    fun `missing and malformed top level fields are rejected`() {
        assertStateFailure("状态必须是对象") { GameState.fromJson("[]") }
        assertStateFailure("状态缺少字段: players") { GameState.fromJson("""{"wall":[]}""") }
        assertStateFailure("状态缺少字段: wall") { GameState.fromJson("""{"players":[]}""") }
        assertStateFailure("JSON 解析失败") { GameState.fromJson("{oops") }
        assertStateFailure("players 必须是数组") { GameState.fromJson("""{"players":{},"wall":[]}""") }
        assertStateFailure("wall 必须是字符串数组") {
            GameState.fromJson(referenceJson.replace("""["1z","5z"]""", """[1]"""))
        }
    }

    @Test
    fun `unsupported versions are rejected`() {
        assertStateFailure("不支持的状态版本") {
            GameState.fromJson("""{"players":[],"wall":[],"version":2}""")
        }
        assertStateFailure("不支持的状态版本") {
            GameState.fromJson("""{"players":[],"wall":[],"version":"1"}""")
        }
        assertStateFailure("不支持的状态版本") {
            GameState.fromJson(referenceJson.replace("\"version\":1", "\"version\":9"))
        }
        assertStateFailure("不支持的状态版本") { referenceState().apply { version = 5 }.validate() }
    }

    @Test
    fun `type errors are rejected`() {
        assertStateFailure("dealer 必须是整数") {
            GameState.fromJson(referenceJson.replace("\"dealer\":1", "\"dealer\":\"1\""))
        }
        assertStateFailure("turn 必须是整数") {
            GameState.fromJson(referenceJson.replace("\"turn\":2", "\"turn\":true"))
        }
        assertStateFailure("winner 必须是整数") {
            GameState.fromJson(referenceJson.replace("\"winner\":null", "\"winner\":\"0\""))
        }
        assertStateFailure("winner 必须是整数") {
            GameState.fromJson(referenceJson.replace("\"winner\":null", "\"winner\":true"))
        }
        assertStateFailure("phase 必须是字符串") {
            GameState.fromJson(referenceJson.replace("\"phase\":\"playing\"", "\"phase\":1"))
        }
        assertStateFailure("last_action 必须是字符串或 null") {
            GameState.fromJson(
                referenceJson.replace("\"last_action\":\"discard:4z\"", "\"last_action\":5")
            )
        }
        assertStateFailure("玩家状态缺少字段: seat") {
            GameState.fromJson(referenceJson.replace(",\"seat\":0", ""))
        }
        assertStateFailure("melds 必须是数组") {
            GameState.fromJson(referenceJson.replace("\"melds\":[]", "\"melds\":{}"))
        }
    }

    @Test
    fun `invalid game rules are rejected`() {
        assertStateFailure("必须是 4 名玩家") {
            referenceState().apply { players.removeAt(3) }.validate()
        }
        assertStateFailure("座位号应为 1") {
            referenceState().apply { players[1].seat = 3 }.validate()
        }
        assertStateFailure("非法对局阶段") {
            referenceState().apply { phase = "paused" }.validate()
        }
        assertStateFailure("turn 必须是 0-3 的座位号") {
            referenceState().apply { turn = 7 }.validate()
        }
        assertStateFailure("dealer 必须是 0-3 的座位号") {
            referenceState().apply { dealer = -1 }.validate()
        }
        assertStateFailure("winner 只能出现在终局状态") {
            referenceState().apply { winner = 2 }.validate()
        }
        assertStateFailure("win_patterns 只能出现在终局状态") {
            referenceState().apply { winPatterns = mutableListOf(PATTERN_STANDARD) }.validate()
        }
        assertStateFailure("手牌张数非法") {
            referenceState().apply {
                players[0].hand.removeAt(players[0].hand.lastIndex)
                players[0].hand.removeAt(players[0].hand.lastIndex)
            }.validate()
        }
        assertStateFailure("副露超过 4 组") {
            referenceState().apply {
                players[0].melds = MutableList(5) { mutableListOf("1z", "1z", "1z") }
            }.validate()
        }
        assertStateFailure("的副露非法") {
            referenceState().apply { players[1].melds[0] = mutableListOf("1m", "2m", "4m") }.validate()
        }
        assertStateFailure("共出现 5 次") {
            referenceState().apply { players[3].hand.add("1m") }.validate()
        }
        assertStateFailure("中存在非法牌编码") {
            referenceState().apply { players[0].discards.add("zz") }.validate()
        }
        assertStateFailure("非法座位号") { referenceState().player(9) }
    }

    @Test
    fun `validation can be skipped on deserialization`() {
        val state = GameState.fromJson(
            referenceJson.replace("\"turn\":2", "\"turn\":9"),
            validate = false,
        )
        assertEquals(9, state.turn)
        assertStateFailure("turn 必须是 0-3 的座位号") { state.validate() }
    }

    @Test
    fun `nullable fields survive the round trip`() {
        val state = newGame(seed = 11)
        assertNull(state.lastAction)
        assertNull(state.winner)
        val restored = GameState.fromJson(state.toJson())
        assertNull(restored.lastAction)
        assertNull(restored.winner)
        assertNotNull(GameState.fromJson(state.toJson()).players)
        assertEquals(state.players, restored.players)
    }
}
