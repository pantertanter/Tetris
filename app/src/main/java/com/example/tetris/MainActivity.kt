package com.example.tetris

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.viewinterop.AndroidView
import com.example.tetris.ui.theme.TetrisTheme
import com.example.tetris.ui.GameView // Ensure this import is correct

import com.google.firebase.FirebaseApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this) // Initialize Firebase
        setContent {
            TetrisTheme {
                GameScreen()
            }
        }
    }
}

@Composable
fun GameScreen() {
    // Integrate GameView into Jetpack Compose using AndroidView
    AndroidView(factory = { context ->
        GameView(context) // Create an instance of GameView
    })
}
