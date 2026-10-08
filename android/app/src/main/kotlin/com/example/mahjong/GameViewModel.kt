package com.example.mahjong

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.mahjong.core.concealedKongs
import com.example.mahjong.core.isWin
import com.example.mahjong.core.tileName
import com.example.mahjong.core.winningTiles
import com.example.mahjong.ui.ClaimUi
import com.example.mahjong.ui.HumanUi
import com.example.mahjong.ui.OpponentUi
import com.example.mahjong.ui.UiState

/**
 * 界面与 [GameController] 之间的桥：持有控制器、把状态拍成 [UiState] 帧。
 *
 * 每个用户操作都会重建一帧（`uiState`），Compose 依此重组；
 * AI 的节拍由界面调用 [tick] 驱动。
 */
class GameViewModel : ViewModel() {

    val controller = GameController()

    private var epoch = 0

    var uiState: UiState = buildUiState()
        private set

    /** 当前是否轮到 AI（供界面的节拍循环判断）。 */
    val aiTurn: Boolean
        get() = controller.awaitKind == AwaitKind.AI

    fun tick() = mutate(controller::tick)

    fun newGame() = mutate { controller.newGame() }

    fun discard(code: String) = mutate { controller.humanDiscard(code) }

    fun respond(accept: Boolean) = mutate { controller.humanRespond(accept) }

    fun declareWin() = mutate { controller.humanDeclareWin() }

    fun concealedKong(code: String) = mutate { controller.humanConcealedKong(code) }

    private fun mutate(action: () -> Unit) {
        try {
            action()
        } catch (exc: IllegalArgumentException) {
            controller.report(exc.message ?: "操作失败")
        } catch (exc: IllegalStateException) {
            controller.report(exc.message ?: "操作失败")
        }
        epoch++
        uiState = buildUiState()
    }

    private fun buildUiState(): UiState {
        val game = controller
        val state = game.state
        val humanSeat = game.humanSeat
        val human = state.player(humanSeat)
        val awaitKind = game.awaitKind
        val total = human.hand.size + 3 * human.melds.size
        val canDiscard = awaitKind == AwaitKind.DISCARD
        val waits = if (total == 13) {
            winningTiles(human.hand, human.melds)
        } else {
            emptyList()
        }

        val opponents = (0 until UiState.SEAT_COUNT)
            .filter { it != humanSeat }
            .associateWith { seat ->
                val player = state.player(seat)
                OpponentUi(
                    seat = seat,
                    name = game.seatName(seat),
                    handCount = player.hand.size,
                    melds = player.melds.map { it.toList() },
                    discards = player.discards.toList(),
                    isTurn = state.turn == seat && !game.isFinished,
                )
            }

        val claimCandidate = game.pendingClaim
        return UiState(
            epoch = epoch,
            awaitKind = awaitKind,
            humanSeat = humanSeat,
            dealer = state.dealer,
            turn = state.turn,
            turnName = game.seatName(state.turn),
            wallCount = state.wall.size,
            phase = state.phase,
            winner = state.winner,
            winnerName = state.winner?.let { game.seatName(it) },
            winPatterns = state.winPatterns.toList(),
            message = game.message,
            history = game.history.toList(),
            lastDiscard = game.lastDiscard,
            lastDiscardName = game.lastDiscard?.let { "${game.seatName(it.first)}打出 ${tileName(it.second)}" },
            human = HumanUi(
                hand = human.hand.toList(),
                melds = human.melds.map { it.toList() },
                discards = human.discards.toList(),
                isTurn = state.turn == humanSeat && !game.isFinished,
                canWin = canDiscard && isWin(human.hand, human.melds),
                concealedKongs = if (canDiscard) concealedKongs(human.hand, human.melds) else emptyList(),
                waits = waits,
            ),
            opponents = opponents,
            claim = claimCandidate?.let {
                ClaimUi(
                    discarder = it.discarder,
                    discarderName = game.seatName(it.discarder),
                    tile = it.tile,
                    option = it.option,
                )
            },
        )
    }
}
