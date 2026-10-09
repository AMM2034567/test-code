package com.example.mahjong

import androidx.compose.runtime.snapshots.Snapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameViewModelTest {

    @Test
    fun `a new game waits for the human to discard`() {
        val viewModel = GameViewModel()
        assertEquals(AwaitKind.DISCARD, viewModel.uiState.awaitKind)
        assertTrue(viewModel.aiTurn.not())
    }

    /**
     * uiState 必须是快照状态：只有写进快照系统，Compose 才会重组、
     * LaunchedEffect(ui.epoch) 才会重启 AI 节拍，否则下家会一直「思考」不出牌。
     */
    @Test
    fun `ui state updates are observable by the compose snapshot system`() {
        val viewModel = GameViewModel()
        viewModel.discard(viewModel.uiState.human.hand.last())
        assertTrue(viewModel.aiTurn)
        val epochBefore = viewModel.uiState.epoch

        var writes = 0
        val handle = Snapshot.registerGlobalWriteObserver { writes++ }
        try {
            viewModel.tick()
            Snapshot.sendApplyNotifications()
        } finally {
            handle.dispose()
        }

        assertEquals(epochBefore + 1, viewModel.uiState.epoch)
        assertTrue(
            "tick() 的新 uiState 没有进入快照系统，界面不会重组，AI 会一直思考不出牌",
            writes > 0,
        )
    }
}
