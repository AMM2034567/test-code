package com.example.mahjong

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import com.example.mahjong.core.GameState
import com.example.mahjong.core.StateException
import com.example.mahjong.core.declareDraw
import com.example.mahjong.core.declareWin
import com.example.mahjong.core.discardTile
import com.example.mahjong.core.drawTile
import com.example.mahjong.core.newGame
import com.example.mahjong.core.tileName
import com.example.mahjong.core.waitsFor

/**
 * 测试用界面：展示麻将核心逻辑（发牌 / 摸打 / 和牌 / 听牌 / 序列化）的运行结果。
 */
class MainActivity : Activity() {

    private lateinit var state: GameState
    private lateinit var statusText: TextView
    private lateinit var messageText: TextView
    private lateinit var handText: TextView
    private lateinit var riverText: TextView
    private lateinit var jsonText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        messageText = findViewById(R.id.messageText)
        handText = findViewById(R.id.handText)
        riverText = findViewById(R.id.riverText)
        jsonText = findViewById(R.id.jsonText)

        findViewById<Button>(R.id.btnNew).setOnClickListener { onNewGame() }
        findViewById<Button>(R.id.btnDraw).setOnClickListener { onDraw() }
        findViewById<Button>(R.id.btnDiscard).setOnClickListener { onDiscard() }
        findViewById<Button>(R.id.btnWin).setOnClickListener { onWin() }
        findViewById<Button>(R.id.btnWaits).setOnClickListener { onWaits() }
        findViewById<Button>(R.id.btnDrawGame).setOnClickListener { onDrawGame() }

        onNewGame()
    }

    private fun onNewGame() {
        runAction {
            state = newGame()
            "已开局（洗牌 + 发牌，庄家 14 张）"
        }
    }

    private fun onDraw() {
        runAction {
            val tile = drawTile(state)
            "摸到 ${tile}（${tileName(tile)}）"
        }
    }

    private fun onDiscard() {
        runAction {
            val hand = state.player(state.turn).hand
            require(hand.isNotEmpty()) { "当前玩家没有手牌" }
            val tile = hand.last()
            discardTile(state, tile)
            "打出 ${tile}（${tileName(tile)}），轮到座位 ${state.turn}"
        }
    }

    private fun onWin() {
        runAction {
            if (declareWin(state, state.turn)) {
                "和牌！牌型 ${state.winPatterns.joinToString(" + ")}"
            } else {
                "座位 ${state.turn} 无法和牌"
            }
        }
    }

    private fun onWaits() {
        runAction {
            val tiles = waitsFor(state, state.turn)
            if (tiles.isEmpty()) {
                "当前已摸牌（14 张），无听牌信息"
            } else {
                "听 ${tiles.size} 种：${tiles.joinToString(" ") { "${it}(${tileName(it)})" }}"
            }
        }
    }

    private fun onDrawGame() {
        runAction {
            declareDraw(state)
            "流局（牌山已空，无人和牌）"
        }
    }

    private fun runAction(action: () -> String) {
        val message = try {
            action()
        } catch (exc: StateException) {
            exc.message ?: exc.javaClass.simpleName
        } catch (exc: IllegalArgumentException) {
            exc.message ?: exc.javaClass.simpleName
        }
        refresh(message)
    }

    private fun refresh(message: String) {
        val player = state.player(state.turn)
        val winner = state.winner
        statusText.text = buildString {
            append("庄家 ${state.dealer} · 当前座位 ${state.turn} · ")
            append(if (state.phase == "finished") "已结束" else "进行中")
            append(" · 牌山 ${state.wall.size} 张")
            if (winner != null) {
                append(" · 胜者 座位$winner")
            }
        }
        messageText.text = message
        handText.text = if (player.hand.isEmpty()) {
            "（空）"
        } else {
            player.hand.joinToString("  ") { "${it} ${tileName(it)}" }
        }
        riverText.text = if (player.discards.isEmpty()) {
            "（空）"
        } else {
            player.discards.joinToString(" ") { "${it} ${tileName(it)}" }
        }
        jsonText.text = state.toJson()
    }
}
