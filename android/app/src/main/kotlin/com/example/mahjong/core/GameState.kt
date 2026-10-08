package com.example.mahjong.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull

/**
 * 对局状态及其序列化（JSON）。
 *
 * 状态可与 JSON 互相转换，序列化结果为确定性输出（键按字典序排列、紧凑格式无多余空白），
 * 便于存档、回放以及基于序列化的单元测试。
 */

const val STATE_VERSION: Int = 1
val PHASES: List<String> = listOf("playing", "finished")

private const val SEATS = 4
private const val MELD_SIZE = 3
private const val OPEN_MELDS_MAX = 4
private const val DEALER_TILES = 13
private const val DRAW_TILES = 14

/** 状态不合法或无法反序列化。 */
class StateException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

private fun stringArray(values: List<String>): JsonElement = JsonArray(values.map { JsonPrimitive(it) })

private fun expectStrList(element: JsonElement?, where: String): MutableList<String> {
    if (element !is JsonArray || element.any { it !is JsonPrimitive || !it.isString }) {
        throw StateException("$where 必须是字符串数组")
    }
    return element.map { (it as JsonPrimitive).content }.toMutableList()
}

private fun expectInt(element: JsonElement?, where: String): Int {
    val primitive = element as? JsonPrimitive
    val value = primitive?.takeUnless { it.isString || it.booleanOrNull != null }?.intOrNull
    if (value == null) {
        throw StateException("$where 必须是整数")
    }
    return value
}

private fun optionalInt(element: JsonElement?, where: String): Int? =
    if (element == null || element is JsonNull) null else expectInt(element, where)

private fun optionalString(element: JsonElement?, where: String): String? = when {
    element == null || element is JsonNull -> null
    element is JsonPrimitive && element.isString -> element.content
    else -> throw StateException("$where 必须是字符串或 null")
}

/** 一名玩家的状态。 */
data class PlayerState(
    var seat: Int,
    var hand: MutableList<String> = mutableListOf(),
    var melds: MutableList<MutableList<String>> = mutableListOf(),
    var discards: MutableList<String> = mutableListOf(),
) {

    fun toJsonElement(): JsonElement = buildJsonObject {
        put("discards", stringArray(discards))
        put("hand", stringArray(hand))
        put("melds", JsonArray(melds.map { stringArray(it) }))
        put("seat", JsonPrimitive(seat))
    }

    companion object {

        fun fromJsonElement(data: JsonElement): PlayerState {
            if (data !is JsonObject) {
                throw StateException("玩家状态必须是对象")
            }
            for (key in listOf("seat", "hand", "melds", "discards")) {
                if (key !in data) {
                    throw StateException("玩家状态缺少字段: $key")
                }
            }
            val seat = expectInt(data["seat"], "seat")
            val hand = expectStrList(data["hand"], "hand")
            val discards = expectStrList(data["discards"], "discards")
            val meldsRaw = data["melds"]
            if (meldsRaw !is JsonArray) {
                throw StateException("melds 必须是数组")
            }
            val melds = meldsRaw.map { expectStrList(it, "melds[]") }.toMutableList()
            return PlayerState(seat = seat, hand = hand, melds = melds, discards = discards)
        }
    }
}

/** 整局对局状态：牌山 + 4 名玩家 + 流程信息。 */
data class GameState(
    var players: MutableList<PlayerState> = mutableListOf(),
    var wall: MutableList<String> = mutableListOf(),
    var dealer: Int = 0,
    var turn: Int = 0,
    var phase: String = "playing",
    var winner: Int? = null,
    var winPatterns: MutableList<String> = mutableListOf(),
    var lastAction: String? = null,
    var version: Int = STATE_VERSION,
) {

    // ---------- 序列化 ----------

    fun toJsonElement(): JsonElement = buildJsonObject {
        put("dealer", JsonPrimitive(dealer))
        put("last_action", lastAction?.let { JsonPrimitive(it) } ?: JsonNull)
        put("phase", JsonPrimitive(phase))
        put("players", JsonArray(players.map { it.toJsonElement() }))
        put("turn", JsonPrimitive(turn))
        put("version", JsonPrimitive(version))
        put("wall", stringArray(wall))
        put("win_patterns", stringArray(winPatterns))
        put("winner", winner?.let { JsonPrimitive(it) } ?: JsonNull)
    }

    fun toJson(): String = Json.encodeToString(JsonElement.serializer(), toJsonElement())

    // ---------- 校验 ----------

    /** 校验状态合法性，不合法时抛 [StateException]。 */
    fun validate() {
        if (version != STATE_VERSION) {
            throw StateException("不支持的状态版本: $version")
        }
        if (players.size != SEATS) {
            throw StateException("必须是 $SEATS 名玩家，当前 ${players.size}")
        }
        players.forEachIndexed { index, player ->
            if (player.seat != index) {
                throw StateException("座位号应为 $index，实际 ${player.seat}")
            }
        }
        if (phase !in PHASES) {
            throw StateException("非法对局阶段: $phase")
        }
        for ((name, value) in listOf("dealer" to dealer, "turn" to turn)) {
            if (value !in 0 until SEATS) {
                throw StateException("$name 必须是 0-${SEATS - 1} 的座位号: $value")
            }
        }
        if (winner != null) {
            if (winner !in 0 until SEATS) {
                throw StateException("winner 必须是 0-${SEATS - 1} 的座位号或 null: $winner")
            }
            if (phase != "finished") {
                throw StateException("winner 只能出现在终局状态")
            }
        }
        if (winPatterns.isNotEmpty() && phase != "finished") {
            throw StateException("win_patterns 只能出现在终局状态")
        }

        val counts = IntArray(KIND_COUNT)

        fun scan(codes: List<String>, where: String) {
            for (code in codes) {
                val index = try {
                    tileIndex(code)
                } catch (exc: IllegalArgumentException) {
                    throw StateException("$where 中存在非法牌编码: $code", exc)
                }
                counts[index]++
            }
        }

        scan(wall, "牌山")
        for (player in players) {
            val where = "座位 ${player.seat}"
            if (player.melds.size > OPEN_MELDS_MAX) {
                throw StateException("$where 副露超过 $OPEN_MELDS_MAX 组")
            }
            for (meld in player.melds) {
                scan(meld, "$where 副露")
                if (!isValidMeld(meld)) {
                    throw StateException("$where 的副露非法: $meld")
                }
            }
            scan(player.hand, "$where 手牌")
            scan(player.discards, "$where 牌河")
            val allowed = listOf(
                DEALER_TILES - MELD_SIZE * player.melds.size,
                DRAW_TILES - MELD_SIZE * player.melds.size,
            )
            if (player.hand.size !in allowed) {
                throw StateException(
                    "$where 手牌张数非法: ${player.hand.size}（副露 ${player.melds.size} 组时应为 " +
                        "${allowed[0]} 或 ${allowed[1]} 张）"
                )
            }
        }
        for ((index, total) in counts.withIndex()) {
            if (total > COPIES_PER_KIND) {
                throw StateException("${ALL_CODES[index]} 共出现 $total 次，超过 $COPIES_PER_KIND 张")
            }
        }
        val totalTiles = counts.sum()
        if (totalTiles > TOTAL_TILES) {
            throw StateException("牌总数 $totalTiles 超过 $TOTAL_TILES 张")
        }
    }

    // ---------- 便捷访问 ----------

    fun player(seat: Int): PlayerState {
        if (seat !in players.indices) {
            throw StateException("非法座位号: $seat")
        }
        return players[seat]
    }

    companion object {

        fun fromJsonElement(data: JsonElement, validate: Boolean = true): GameState {
            if (data !is JsonObject) {
                throw StateException("状态必须是对象")
            }
            for (key in listOf("players", "wall")) {
                if (key !in data) {
                    throw StateException("状态缺少字段: $key")
                }
            }
            val versionElement = data["version"] ?: JsonPrimitive(STATE_VERSION)
            val version = (versionElement as? JsonPrimitive)
                ?.takeUnless { it.isString }
                ?.intOrNull
            if (version == null) {
                throw StateException("不支持的状态版本: $versionElement")
            }
            if (version != STATE_VERSION) {
                throw StateException("不支持的状态版本: $version")
            }
            val playersRaw = data["players"]
            if (playersRaw !is JsonArray) {
                throw StateException("players 必须是数组")
            }
            val players = playersRaw.map { PlayerState.fromJsonElement(it) }.toMutableList()
            val wall = expectStrList(data["wall"], "wall")
            val phaseElement = data["phase"] ?: JsonPrimitive("playing")
            if (phaseElement !is JsonPrimitive || !phaseElement.isString) {
                throw StateException("phase 必须是字符串")
            }
            val winPatterns = expectStrList(
                data["win_patterns"] ?: JsonArray(emptyList()),
                "win_patterns",
            )
            val state = GameState(
                players = players,
                wall = wall,
                dealer = expectInt(data["dealer"] ?: JsonPrimitive(0), "dealer"),
                turn = expectInt(data["turn"] ?: JsonPrimitive(0), "turn"),
                phase = phaseElement.content,
                winner = optionalInt(data["winner"], "winner"),
                winPatterns = winPatterns,
                lastAction = optionalString(data["last_action"], "last_action"),
                version = version,
            )
            if (validate) {
                state.validate()
            }
            return state
        }

        fun fromJson(text: String, validate: Boolean = true): GameState {
            val element = try {
                Json.parseToJsonElement(text)
            } catch (exc: Exception) {
                throw StateException("JSON 解析失败: ${exc.message}", exc)
            }
            return fromJsonElement(element, validate)
        }
    }
}
