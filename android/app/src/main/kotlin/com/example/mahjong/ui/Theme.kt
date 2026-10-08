package com.example.mahjong.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** 牌桌配色。 */
object MahjongPalette {
    /** 牌桌绿呢。 */
    val felt = Color(0xFF0F5132)

    /** 牌桌深色边。 */
    val feltDark = Color(0xFF0A3A24)

    /** 卡片底色。 */
    val panel = Color(0xFF0B4630)

    /** 卡片描边。 */
    val panelBorder = Color(0xFF2E7D57)

    /** 主文字。 */
    val textPrimary = Color(0xFFF1F8F4)

    /** 次要文字。 */
    val textSecondary = Color(0xFFA5D6BC)

    /** 强调（庄家/轮转）。 */
    val accent = Color(0xFFFFC107)

    /** 操作按钮。 */
    val action = Color(0xFFE53935)

    /** 牌面高光/选中。 */
    val tileSelected = Color(0xFFFFB300)

    /** 弃牌高亮。 */
    val tileLast = Color(0xFFFF7043)
}

@Composable
fun MahjongTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = MahjongPalette.accent,
            onPrimary = Color(0xFF212121),
            secondary = Color(0xFF80CBC4),
            background = MahjongPalette.felt,
            onBackground = MahjongPalette.textPrimary,
            surface = MahjongPalette.panel,
            onSurface = MahjongPalette.textPrimary,
            surfaceVariant = MahjongPalette.feltDark,
            onSurfaceVariant = MahjongPalette.textSecondary,
            outline = MahjongPalette.panelBorder,
            error = MahjongPalette.action,
            onError = Color.White,
        ),
        content = content,
    )
}
