package com.example.tetris.firebase

import com.google.firebase.database.FirebaseDatabase

// Data class representing a high score
data class HighScore(val playerName: String, val score: Int)

// Function to save high scores to Firebase Realtime Database
fun saveHighScore(playerName: String, score: Int) {
    val database = FirebaseDatabase.getInstance().reference
    val highScore = HighScore(playerName, score)

    database.child("highscores").push().setValue(highScore)
        .addOnCompleteListener { task ->
            if (task.isSuccessful) {
                // Successfully saved
                println("High score saved successfully")
            } else {
                // Handle failure
                task.exception?.let {
                    println("Error saving high score: ${it.message}")
                }
            }
        }
}

fun getHighScores(onResult: (List<HighScore>) -> Unit) {
    val database = FirebaseDatabase.getInstance().reference

    database.child("highscores").get().addOnCompleteListener { task ->
        if (task.isSuccessful) {
            val snapshot = task.result
            val scores = snapshot?.children?.mapNotNull { it.getValue(HighScore::class.java) } ?: emptyList()
            onResult(scores)
        } else {
            task.exception?.let {
                println("Error retrieving high scores: ${it.message}")
            }
            onResult(emptyList())
        }
    }
}


