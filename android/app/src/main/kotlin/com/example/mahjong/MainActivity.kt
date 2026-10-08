package com.example.mahjong

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.mahjong.ui.MahjongScreen
import com.example.mahjong.ui.MahjongTheme

/** 麻将对局界面（Jetpack Compose）。 */
class MainActivity : ComponentActivity() {

    private val viewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MahjongTheme {
                MahjongScreen(viewModel)
            }
        }
    }
}
