package com.example.tetris

import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.viewinterop.AndroidView
import com.example.tetris.ui.GameView
import com.example.tetris.ui.theme.TetrisTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            TetrisTheme {
                GameScreen()
            }
        }
    }

    @Composable
    fun GameScreen() {
        AndroidView(factory = { context ->
            val frameLayout = FrameLayout(context)
            val gameView = GameView(context)
            frameLayout.addView(gameView) // Add GameView to FrameLayout
            frameLayout // Return the FrameLayout containing GameView
        })
    }
}
