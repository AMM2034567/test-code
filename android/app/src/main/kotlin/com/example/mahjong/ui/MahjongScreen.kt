package com.example.mahjong.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mahjong.AwaitKind
import com.example.mahjong.GameViewModel
import com.example.mahjong.core.ClaimType
import com.example.mahjong.core.tileName
import com.example.mahjong.patternLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val AI_STEP_DELAY_MS = 700L

/**
 * 麻将对局界面：牌桌 + 可视化牌面，点击手牌选中、再次点击（或按「打出」）出牌。
 */
@Composable
fun MahjongScreen(viewModel: GameViewModel) {
    val ui = viewModel.uiState
    var selectedIndex by remember(ui.epoch) { mutableStateOf<Int?>(null) }

    // AI 节拍：轮到 AI 时按固定间隔推进，直到需要人类介入。
    LaunchedEffect(ui.epoch) {
        while (isActive) {
            if (!viewModel.aiTurn) break
            delay(AI_STEP_DELAY_MS)
            viewModel.tick()
        }
    }

    val onTileClick: (Int) -> Unit = { index ->
        if (ui.awaitKind == AwaitKind.DISCARD) {
            if (selectedIndex == index) {
                viewModel.discard(ui.human.hand[index])
                selectedIndex = null
            } else {
                selectedIndex = index
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MahjongPalette.felt)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Header(ui)

        MainArea(ui, Modifier.weight(1f))

        MyArea(ui)

        HandSection(ui, selectedIndex, onTileClick)
        ActionBar(ui, selectedIndex, viewModel)
    }
}

// ---------- 主区域 ----------

@Composable
private fun MainArea(ui: UiState, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    ) {
        if (maxWidth >= maxHeight) {
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TableArea(ui, threeAcross = true, Modifier.weight(1.7f))
                CenterArea(ui, Modifier.weight(1f))
            }
        } else {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TableArea(ui, threeAcross = false, Modifier.weight(1.5f))
                CenterArea(ui, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TableArea(ui: UiState, threeAcross: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (threeAcross) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Top,
            ) {
                OpponentPanel(ui, ui.leftSeat, Modifier.weight(1f))
                OpponentPanel(ui, ui.topSeat, Modifier.weight(1f))
                OpponentPanel(ui, ui.rightSeat, Modifier.weight(1f))
            }
        } else {
            OpponentPanel(ui, ui.topSeat, Modifier.fillMaxWidth())
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Top,
            ) {
                OpponentPanel(ui, ui.leftSeat, Modifier.weight(1f))
                OpponentPanel(ui, ui.rightSeat, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CenterArea(ui: UiState, modifier: Modifier = Modifier) {
    CenterInfo(
        ui,
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    )
}

// ---------- 顶部信息栏 ----------

@Composable
private fun Header(ui: UiState) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MahjongPalette.feltDark)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("麻将", color = MahjongPalette.accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        StatChip("牌山", "${ui.wallCount} 张")
        StatChip("轮到", ui.turnName)
        StatChip("庄家", seatLabel(ui))
        Spacer(Modifier.weight(1f))
        if (ui.humanSeat == ui.dealer) {
            Text("你坐庄", color = MahjongPalette.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun seatLabel(ui: UiState): String = when ((ui.dealer - ui.humanSeat + 4) % 4) {
    0 -> "你"
    1 -> "下家"
    2 -> "对家"
    else -> "上家"
}

@Composable
private fun StatChip(label: String, value: String) {
    Column {
        Text(label, color = MahjongPalette.textSecondary, fontSize = 10.sp)
        Text(value, color = MahjongPalette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ---------- 对手面板 ----------

@Composable
private fun OpponentPanel(ui: UiState, seat: Int, modifier: Modifier = Modifier) {
    val opponent = ui.opponents[seat] ?: return
    val active = ui.isTurn(seat)
    BoxWithConstraints(
        modifier
            .background(
                if (active) Color(0xFF14684A) else MahjongPalette.panel,
                RoundedCornerShape(10.dp),
            )
            .border(
                width = if (active) 2.dp else 1.dp,
                color = if (active) MahjongPalette.accent else MahjongPalette.panelBorder,
                shape = RoundedCornerShape(10.dp),
            )
            .padding(6.dp),
    ) {
        val panelWidth = maxWidth
        val backPerRow = slotsPerRow(panelWidth, TileSizes.Back)
        val microPerRow = slotsPerRow(panelWidth, TileSizes.Micro)
        val lastMarked = ui.lastDiscard?.first == seat &&
            opponent.discards.lastOrNull() == ui.lastDiscard?.second

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    opponent.name,
                    color = MahjongPalette.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (active) {
                    Text("行动中", color = MahjongPalette.accent, fontSize = 10.sp)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "${opponent.handCount} 张",
                    color = MahjongPalette.textSecondary,
                    fontSize = 11.sp,
                )
            }

            TileGrid(
                codes = List(opponent.handCount) { FACE_DOWN },
                faceUp = false,
                perRow = backPerRow,
                tileWidth = TileSizes.Back,
                tileHeight = TileSizes.BackHeight,
            )

            if (opponent.melds.isNotEmpty()) {
                MeldRow(opponent.melds)
            }

            if (opponent.discards.isNotEmpty()) {
                TileGrid(
                    codes = opponent.discards,
                    perRow = microPerRow,
                    tileWidth = TileSizes.Micro,
                    tileHeight = TileSizes.MicroHeight,
                    markLast = lastMarked,
                )
            }
        }
    }
}

// ---------- 中央信息 ----------

@Composable
private fun CenterInfo(ui: UiState, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(MahjongPalette.panel, RoundedCornerShape(10.dp))
                .border(1.dp, MahjongPalette.panelBorder, RoundedCornerShape(10.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                ui.message,
                color = MahjongPalette.accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )

            val lastDiscard = ui.lastDiscard
            if (lastDiscard != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MahjongTile(
                        lastDiscard.second,
                        Modifier
                            .width(TileSizes.Focus)
                            .height(TileSizes.FocusHeight),
                        marked = true,
                    )
                    Text(
                        ui.lastDiscardName.orEmpty(),
                        color = MahjongPalette.textPrimary,
                        fontSize = 12.sp,
                    )
                }
            }

            val waits = ui.human.waits
            if (waits.isNotEmpty()) {
                Text(
                    "听牌 ${waits.size} 种：${waits.joinToString(" ") { tileName(it) }}",
                    color = MahjongPalette.textSecondary,
                    fontSize = 11.sp,
                )
            }
        }

        if (ui.isFinished) {
            ResultCard(ui)
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0A3A24), RoundedCornerShape(10.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("牌局记录", color = MahjongPalette.textSecondary, fontSize = 10.sp)
                ui.history.takeLast(6).reversed().forEach { line ->
                    Text(
                        line,
                        color = MahjongPalette.textPrimary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultCard(ui: UiState) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF5D4037), RoundedCornerShape(10.dp))
            .border(1.dp, MahjongPalette.accent, RoundedCornerShape(10.dp))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val winner = ui.winner
        Text(
            if (winner == null) "流局" else "${ui.winnerName}和牌",
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
        )
        if (winner != null && ui.winPatterns.isNotEmpty()) {
            Text(
                ui.winPatterns.joinToString(" + ") { patternLabel(it) },
                color = MahjongPalette.accent,
                fontSize = 13.sp,
            )
        }
        Text(
            "点击「新局」再来一盘",
            color = Color(0xFFD7CCC8),
            fontSize = 11.sp,
        )
    }
}

// ---------- 我的副露与牌河 ----------

@Composable
private fun MyArea(ui: UiState) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SectionLabel("副露")
        Row(
            Modifier
                .weight(0.45f)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (ui.human.melds.isEmpty()) {
                Text("无", color = MahjongPalette.textSecondary, fontSize = 11.sp)
            } else {
                ui.human.melds.forEach { meld ->
                    Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                        meld.forEach { code ->
                            MahjongTile(
                                code,
                                Modifier
                                    .width(TileSizes.Mini)
                                    .height(TileSizes.MiniHeight),
                            )
                        }
                    }
                }
            }
        }

        SectionLabel("牌河")
        Row(
            Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (ui.human.discards.isEmpty()) {
                Text("无", color = MahjongPalette.textSecondary, fontSize = 11.sp)
            } else {
                val marked = ui.lastDiscard?.first == ui.humanSeat &&
                    ui.human.discards.lastOrNull() == ui.lastDiscard?.second
                ui.human.discards.forEachIndexed { index, code ->
                    MahjongTile(
                        code,
                        Modifier
                            .width(TileSizes.Micro)
                            .height(TileSizes.MicroHeight),
                        marked = marked && index == ui.human.discards.lastIndex,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = MahjongPalette.textSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.width(34.dp),
    )
}

// ---------- 我的手牌 ----------

@Composable
private fun HandSection(ui: UiState, selectedIndex: Int?, onTileClick: (Int) -> Unit) {
    val hand = ui.human.hand
    Column(
        Modifier
            .fillMaxWidth()
            .background(MahjongPalette.feltDark),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("我的手牌", color = MahjongPalette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            if (ui.awaitKind == AwaitKind.DISCARD) {
                Text("· 轮到你出牌", color = MahjongPalette.accent, fontSize = 12.sp)
            }
            Spacer(Modifier.weight(1f))
            Text(
                "${hand.size} 张",
                color = MahjongPalette.textSecondary,
                fontSize = 11.sp,
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (hand.isEmpty()) {
                Text("（无手牌）", color = MahjongPalette.textSecondary, fontSize = 12.sp)
            }
            hand.forEachIndexed { index, code ->
                val selected = index == selectedIndex
                val offsetY by animateDpAsState(
                    targetValue = if (selected) (-8).dp else 0.dp,
                    label = "tileLift",
                )
                MahjongTile(
                    code,
                    Modifier
                        .width(TileSizes.Hand)
                        .height(TileSizes.HandHeight)
                        .offset(y = offsetY)
                        .clickable { onTileClick(index) },
                    selected = selected,
                )
            }
        }
    }
}

// ---------- 操作栏 ----------

@Composable
private fun ActionBar(ui: UiState, selectedIndex: Int?, viewModel: GameViewModel) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MahjongPalette.panel)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when (ui.awaitKind) {
            AwaitKind.CLAIM -> {
                val claim = ui.claim
                OutlinedButton(
                    onClick = { viewModel.respond(false) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("过")
                }
                Button(
                    onClick = { viewModel.respond(true) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MahjongPalette.action),
                ) {
                    Text(claimLabel(claim?.option))
                }
            }

            AwaitKind.DISCARD -> {
                val selectedCode = selectedIndex?.let { ui.human.hand.getOrNull(it) }
                Button(
                    onClick = { selectedCode?.let { viewModel.discard(it) } },
                    enabled = selectedCode != null,
                    modifier = Modifier.weight(1.3f),
                    colors = ButtonDefaults.buttonColors(containerColor = MahjongPalette.action),
                ) {
                    Text(if (selectedCode == null) "选牌打出" else "打出 ${tileName(selectedCode)}")
                }
                if (ui.human.canWin) {
                    Button(
                        onClick = { viewModel.declareWin() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MahjongPalette.action),
                    ) {
                        Text("和牌")
                    }
                }
                val kongCode = selectedIndex
                    ?.let { ui.human.hand.getOrNull(it) }
                    ?.takeIf { it in ui.human.concealedKongs }
                    ?: ui.human.concealedKongs.firstOrNull()
                if (kongCode != null) {
                    Button(
                        onClick = { viewModel.concealedKong(kongCode) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("暗杠")
                    }
                }
            }

            else -> Unit
        }

        Spacer(Modifier.weight(1f))

        OutlinedButton(onClick = { viewModel.newGame() }) {
            Text("新局")
        }
    }
}

private fun claimLabel(option: ClaimType?): String = when (option) {
    ClaimType.RON -> "和牌"
    ClaimType.PONG -> "碰"
    ClaimType.KONG -> "杠"
    null -> "响应"
}

// ---------- 通用小部件 ----------

/** 按宽度估算一行能放下的牌数。 */
private fun slotsPerRow(width: Dp, tile: Dp): Int {
    val perRow = ((width.value - 14) / (tile.value + 2)).toInt()
    return perRow.coerceIn(1, 14)
}

/** 一组牌的网格（自动换行）。 */
@Composable
private fun TileGrid(
    codes: List<String>,
    perRow: Int,
    tileWidth: Dp,
    tileHeight: Dp,
    faceUp: Boolean = true,
    markLast: Boolean = false,
) {
    if (codes.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        codes.chunked(perRow.coerceAtLeast(1)).forEach { rowCodes ->
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                rowCodes.forEachIndexed { index, code ->
                    MahjongTile(
                        code,
                        Modifier
                            .width(tileWidth)
                            .height(tileHeight),
                        faceUp = faceUp,
                        marked = faceUp && markLast && index == rowCodes.lastIndex,
                    )
                }
            }
        }
    }
}

/** 副露：每组之间留空。 */
@Composable
private fun MeldRow(melds: List<List<String>>) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        melds.forEach { meld ->
            Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                meld.forEach { code ->
                    MahjongTile(
                        code,
                        Modifier
                            .width(TileSizes.Mini)
                            .height(TileSizes.MiniHeight),
                    )
                }
            }
        }
    }
}

private const val FACE_DOWN = "1m"
