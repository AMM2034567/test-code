package com.example.mahjong.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mahjong.core.HONOR_NAMES
import com.example.mahjong.core.SUIT_NAMES
import com.example.mahjong.core.Tile
import com.example.mahjong.core.tileName

/** 各类牌面的固定尺寸。 */
object TileSizes {
    /** 手牌。 */
    val Hand: Dp = 42.dp
    val HandHeight: Dp = 58.dp

    /** 焦点牌（最近弃牌等）。 */
    val Focus: Dp = 46.dp
    val FocusHeight: Dp = 62.dp

    /** 副露。 */
    val Mini: Dp = 22.dp
    val MiniHeight: Dp = 30.dp

    /** 牌河。 */
    val Micro: Dp = 20.dp
    val MicroHeight: Dp = 27.dp

    /** 对手暗牌。 */
    val Back: Dp = 15.dp
    val BackHeight: Dp = 21.dp
}

private val FaceTop = Color(0xFFFFFDF6)
private val FaceBottom = Color(0xFFEBDFC4)
private val FaceEdge = Color(0xFFC6B99A)
private val BackTop = Color(0xFF5D8FBF)
private val BackBottom = Color(0xFF2F5480)
private val Ink = Color(0xFF1D1D1D)

private val SuitColors: Map<Char, Color> = mapOf(
    'm' to Color(0xFFC62828),
    'p' to Color(0xFF1565C0),
    's' to Color(0xFF2E7D32),
)

private val HonorColors: List<Color> = listOf(
    Color(0xFF1A237E),
    Color(0xFF1A237E),
    Color(0xFF1A237E),
    Color(0xFF1A237E),
    Color(0xFFC62828),
    Color(0xFF2E7D32),
    Color(0xFF1565C0),
)

private const val SINGLE_LINE_LIMIT_DP = 26

/**
 * 一张麻将牌：圆角牌面 + 文字（或牌背）。
 *
 * [selected] 选中态（金色描边 + 抬升阴影），[marked] 标记态（最近打出的牌）。
 */
@Composable
fun MahjongTile(
    code: String,
    modifier: Modifier = Modifier,
    faceUp: Boolean = true,
    selected: Boolean = false,
    marked: Boolean = false,
) {
    val shape = RoundedCornerShape(6.dp)
    val label = if (faceUp) tileName(code) else "暗牌"
    BoxWithConstraints(
        modifier = modifier
            .shadow(if (selected) 10.dp else 2.dp, shape = shape, clip = false)
            .clip(shape)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        val width = maxWidth
        val height = maxHeight
        val tile = if (faceUp) runCatching { Tile.fromCode(code) }.getOrNull() else null
        val edgeColor = when {
            selected -> MahjongPalette.tileSelected
            marked -> MahjongPalette.tileLast
            else -> FaceEdge
        }
        val edgeWidth = if (selected || marked) 2.5.dp else 1.2.dp

        Canvas(Modifier.fillMaxSize()) {
            val corner = CornerRadius(6.dp.toPx())
            if (faceUp) {
                drawRoundRect(
                    brush = Brush.verticalGradient(listOf(FaceTop, FaceBottom)),
                    cornerRadius = corner,
                )
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.5f),
                    topLeft = Offset(3.dp.toPx(), 3.dp.toPx()),
                    size = Size(size.width - 6.dp.toPx(), size.height * 0.26f),
                    cornerRadius = corner,
                )
                drawRoundRect(
                    color = edgeColor,
                    cornerRadius = corner,
                    style = Stroke(width = edgeWidth.toPx()),
                )
                if (code == WHITE_DRAGON) {
                    val insetX = size.width * 0.24f
                    val insetY = size.height * 0.26f
                    drawRoundRect(
                        color = Color(0xFF1565C0),
                        topLeft = Offset(insetX, insetY),
                        size = Size(size.width - 2 * insetX, size.height - 2 * insetY),
                        cornerRadius = CornerRadius(3.dp.toPx()),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            } else {
                drawRoundRect(
                    brush = Brush.verticalGradient(listOf(BackTop, BackBottom)),
                    cornerRadius = corner,
                )
                val inset = 3.dp.toPx()
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.35f),
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - 2 * inset, size.height - 2 * inset),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx()),
                )
                var start = -size.height
                while (start < size.width) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.16f),
                        start = Offset(start, 0f),
                        end = Offset(start + size.height, size.height),
                        strokeWidth = 1.4.dp.toPx(),
                    )
                    start += 5.dp.toPx()
                }
                drawRoundRect(
                    color = edgeColor,
                    cornerRadius = corner,
                    style = Stroke(width = edgeWidth.toPx()),
                )
            }
        }

        if (faceUp && tile != null && code != WHITE_DRAGON) {
            val singleLine = width.value < SINGLE_LINE_LIMIT_DP
            val colors = if (tile.isHonor) {
                HonorColors.getOrElse(tile.rank - 1) { Ink }
            } else {
                SuitColors[tile.suit] ?: Ink
            }
            if (singleLine) {
                Text(
                    text = if (tile.isHonor) HONOR_NAMES[tile.rank - 1] else "${tile.rank}${SUIT_NAMES[tile.suit]}",
                    color = colors,
                    fontSize = (width.value * 0.52f).sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (tile.isHonor) {
                        Text(
                            text = HONOR_NAMES[tile.rank - 1],
                            color = colors,
                            fontSize = (width.value * 0.5f).sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            lineHeight = (width.value * 0.58f).sp,
                        )
                    } else {
                        Text(
                            text = tile.rank.toString(),
                            color = Ink,
                            fontSize = (width.value * 0.42f).sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            lineHeight = (width.value * 0.46f).sp,
                        )
                        Text(
                            text = SUIT_NAMES[tile.suit] ?: "",
                            color = colors,
                            fontSize = (width.value * 0.3f).sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            lineHeight = (width.value * 0.34f).sp,
                        )
                    }
                }
            }
        }
    }
}

private const val WHITE_DRAGON = "7z"
