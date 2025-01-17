package com.example.tetris.ui

import TETROMINOS
import Tetromino
import TetrominoType
import android.content.ContentValues.TAG
import android.os.Handler
import android.os.Looper
import android.view.View
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.view.MotionEvent
import android.media.MediaPlayer
import android.util.Log
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import com.example.tetris.R
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import android.widget.RelativeLayout

class GameView(context: Context) : View(context) {
    private val paint = Paint()
    private var tetromino: Tetromino = getNextTetromino() // Initialize Tetromino

    // Fast drop variables
    private var isFastDropping = false
    private val fastDropHandler = Handler(Looper.getMainLooper())
    private lateinit var fastDropRunnable: Runnable

    // Grid dimensions (10x20 grid)
    private var blockSize = 0 // Initialize blockSize
    private val gridWidth = 15
    private val gridHeight = 30
    private var offsetX = 0
    private var offsetY = 0

    // Level variables
    private var dropDelay: Long = 1000 // Initial delay for dropping the Tetromino

    // Score variables
    private var score = 0 // Initialize the score
    private var level = 1 // Initialize the level
    private var gameIsStarted = false // Track whether the game has started
    private var gameOver = false // Track whether the game is over
    private var paused = false  // Track whether the game is paused
    private var playerName: String = "playerOne" // Variable to store the player's name
    private var nameInput: EditText? = null // Nullable to avoid initialization issues


    // Highscore variables
    private var highScoreSaved = false
    private var fetchedHighScoresBool = false
    private var fetchedHighScores: List<Pair<String, Long>> = emptyList()

    // Firebase Firestore instance
    private val db = Firebase.firestore

    // Game loop handler
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var gameRunnable: Runnable

    // Sound effects
    private var mediaPlayer: MediaPlayer? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        blockSize = (w / gridWidth).coerceAtMost(h / gridHeight)
        val totalGridWidth = blockSize * gridWidth
        val totalGridHeight = blockSize * gridHeight

        offsetX = (w - totalGridWidth) / 2
        offsetY = (h - totalGridHeight) / 2
    }

    // Game grid represented by 2D array
    private val grid = Array(gridHeight) { IntArray(gridWidth) }

    init {
        paint.color = Color.BLACK
        isFocusable = true // Enable focus for key events
        initFastDropRunnable() // Initialize fast drop runnable
        initGameRunnable() // Initialize the game loop runnable
    }

    private fun updateDropDelay() {
        dropDelay =
            (1000 / level).coerceAtLeast(100).toLong() // Decrease delay based on level (minimum 100 ms)
    }

    private fun initGameRunnable() {
        gameRunnable = Runnable {
            moveDown() // Move down every time the game loop runs
            updateDropDelay() // Update the drop delay based on current level
            handler.postDelayed(gameRunnable, dropDelay) // Schedule next move with updated delay
        }
    }

    private fun initFastDropRunnable() {
        fastDropRunnable = Runnable {
            moveDown() // Move down repeatedly
            if (isFastDropping) {
                fastDropHandler.postDelayed(fastDropRunnable, 100) // Fast drop every 100 ms
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (gameOver) {
            drawGameOverMessage(canvas)
            return
        }

        if (paused) {
            drawPauseScreen(canvas) // Show the paused screen
            return
        }

        if (!gameIsStarted) {
            drawStartScreen(canvas) // Show the start screen
            return
        }

        drawBackground(canvas)  // Draw the background first
        drawGrid(canvas) // Draw the game grid
        drawTetromino(canvas) // Draw the current Tetromino
        drawScore(canvas) // Draw the current score
        drawLevel(canvas) // Draw the current level
    }

    private fun drawPauseScreen(canvas: Canvas) {
        // 1. Draw the background gradient
        val paint = Paint()
        val gradient = LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            Color.BLUE, Color.CYAN, Shader.TileMode.CLAMP
        )
        paint.shader = gradient
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        // 2. Text scaling based on screen size
        val baseTextSize = canvas.height * 0.05f // Base text size as 5% of the height

        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = baseTextSize
            textAlign = Paint.Align.CENTER
            setShadowLayer(baseTextSize * 0.1f, 5f, 5f, Color.BLACK) // Shadow with 10% of text size
        }

        val labelPaint = Paint().apply {
            color = Color.YELLOW
            textSize = baseTextSize
            textAlign = Paint.Align.CENTER
            setShadowLayer(baseTextSize * 0.1f, 5f, 5f, Color.BLACK)
        }

        paint.reset()
        paint.style = Paint.Style.STROKE
        paint.color = Color.BLACK
        paint.strokeWidth = canvas.height * 0.005f // Border width as 0.5% of height

        // 3. Calculate dynamic area heights
        val padding = canvas.height * 0.02f // Padding between areas (2% of screen height)
        val pauseAreaHeight = canvas.height * 0.1f
        val rotateAreaHeight = canvas.height * 0.15f
        val movementAreaHeight = canvas.height * 0.25f
        val fastDropAreaHeight = canvas.height * 0.4f

        // Pause/Resume button area
        canvas.drawRect(
            0f,
            0f,
            canvas.width.toFloat(),
            pauseAreaHeight,
            paint
        )
        canvas.drawText(
            "Pause Game",
            (canvas.width / 2).toFloat(),
            pauseAreaHeight / 2 + baseTextSize / 2,
            labelPaint
        )

        // Rotate Tetromino area
        val rotateAreaTop = pauseAreaHeight + padding
        canvas.drawRect(
            0f,
            rotateAreaTop,
            canvas.width.toFloat(),
            rotateAreaTop + rotateAreaHeight,
            paint
        )
        canvas.drawText(
            "Rotate Tetromino",
            (canvas.width / 2).toFloat(),
            rotateAreaTop + rotateAreaHeight / 2 + baseTextSize / 2,
            labelPaint
        )

        // Left movement area
        val movementAreaTop = rotateAreaTop + rotateAreaHeight + padding
        canvas.drawRect(
            0f,
            movementAreaTop,
            (canvas.width / 2).toFloat(),
            movementAreaTop + movementAreaHeight,
            paint
        )
        canvas.drawText(
            "Move Left",
            (canvas.width / 4).toFloat(),
            movementAreaTop + movementAreaHeight / 2 + baseTextSize / 2,
            labelPaint
        )

        // Right movement area
        canvas.drawRect(
            (canvas.width / 2).toFloat(),
            movementAreaTop,
            canvas.width.toFloat(),
            movementAreaTop + movementAreaHeight,
            paint
        )
        canvas.drawText(
            "Move Right",
            (3 * canvas.width / 4).toFloat(),
            movementAreaTop + movementAreaHeight / 2 + baseTextSize / 2,
            labelPaint
        )

        // Fast drop area
        val fastDropAreaTop = movementAreaTop + movementAreaHeight + padding
        canvas.drawRect(
            0f,
            fastDropAreaTop,
            canvas.width.toFloat(),
            canvas.height.toFloat(),
            paint
        )
        canvas.drawText(
            "Fast Drop",
            (canvas.width / 2).toFloat(),
            fastDropAreaTop + fastDropAreaHeight / 2,
            labelPaint
        )

        // Additional text for guidance
        canvas.drawText(
            "Tap to resume",
            (canvas.width / 2).toFloat(),
            fastDropAreaTop + fastDropAreaHeight / 4,
            textPaint
        )
        canvas.drawText(
            "Touch layout",
            (canvas.width / 2).toFloat(),
            fastDropAreaTop + fastDropAreaHeight / 4 * 3,
            textPaint
        )
    }

    private fun drawStartScreen(canvas: Canvas) {
        // 1. Draw the background gradient
        val paint = Paint()
        val gradient = LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            Color.BLUE, Color.CYAN, Shader.TileMode.CLAMP
        )
        paint.shader = gradient
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        // 2. Text scaling based on screen size
        val baseTextSize = canvas.height * 0.05f // Base text size as 5% of the height

        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = baseTextSize
            textAlign = Paint.Align.CENTER
            setShadowLayer(baseTextSize * 0.1f, 5f, 5f, Color.BLACK) // Shadow with 10% of text size
        }

        val labelPaint = Paint().apply {
            color = Color.YELLOW
            textSize = baseTextSize
            textAlign = Paint.Align.CENTER
            setShadowLayer(baseTextSize * 0.1f, 5f, 5f, Color.BLACK)
        }

        paint.reset()
        paint.style = Paint.Style.STROKE
        paint.color = Color.BLACK
        paint.strokeWidth = canvas.height * 0.005f // Border width as 0.5% of height

        // 3. Calculate dynamic area heights
        val padding = canvas.height * 0.02f // Padding between areas (2% of screen height)
        val pauseAreaHeight = canvas.height * 0.1f
        val rotateAreaHeight = canvas.height * 0.15f
        val movementAreaHeight = canvas.height * 0.25f
        val fastDropAreaHeight = canvas.height * 0.4f

        // Pause/Resume button area
        canvas.drawRect(
            0f,
            0f,
            canvas.width.toFloat(),
            pauseAreaHeight,
            paint
        )
        canvas.drawText(
            "Pause Game",
            (canvas.width / 2).toFloat(),
            pauseAreaHeight / 2 + baseTextSize / 2,
            labelPaint
        )

        // Rotate Tetromino area
        val rotateAreaTop = pauseAreaHeight + padding
        canvas.drawRect(
            0f,
            rotateAreaTop,
            canvas.width.toFloat(),
            rotateAreaTop + rotateAreaHeight,
            paint
        )
        canvas.drawText(
            "Rotate Tetromino",
            (canvas.width / 2).toFloat(),
            rotateAreaTop + rotateAreaHeight / 2 + baseTextSize / 2,
            labelPaint
        )

        // Left movement area
        val movementAreaTop = rotateAreaTop + rotateAreaHeight + padding
        canvas.drawRect(
            0f,
            movementAreaTop,
            (canvas.width / 2).toFloat(),
            movementAreaTop + movementAreaHeight,
            paint
        )
        canvas.drawText(
            "Move Left",
            (canvas.width / 4).toFloat(),
            movementAreaTop + movementAreaHeight / 2 + baseTextSize / 2,
            labelPaint
        )

        // Right movement area
        canvas.drawRect(
            (canvas.width / 2).toFloat(),
            movementAreaTop,
            canvas.width.toFloat(),
            movementAreaTop + movementAreaHeight,
            paint
        )
        canvas.drawText(
            "Move Right",
            (3 * canvas.width / 4).toFloat(),
            movementAreaTop + movementAreaHeight / 2 + baseTextSize / 2,
            labelPaint
        )

        // Fast drop area
        val fastDropAreaTop = movementAreaTop + movementAreaHeight + padding
        canvas.drawRect(
            0f,
            fastDropAreaTop,
            canvas.width.toFloat(),
            canvas.height.toFloat(),
            paint
        )
        canvas.drawText(
            "Fast Drop",
            (canvas.width / 2).toFloat(),
            fastDropAreaTop + fastDropAreaHeight / 2,
            labelPaint
        )

        // Additional text for guidance
        canvas.drawText(
            "Tap to Start",
            (canvas.width / 2).toFloat(),
            fastDropAreaTop + fastDropAreaHeight / 4,
            textPaint
        )
        canvas.drawText(
            "Touch layout",
            (canvas.width / 2).toFloat(),
            fastDropAreaTop + fastDropAreaHeight / 4 * 3,
            textPaint
        )

    addNameInputFieldIfNeeded()
    }

    fun addPlayerNameInput() {
        // Get the root RelativeLayout from your XML
        val rootLayout = findViewById<RelativeLayout>(R.id.root_layout) // Ensure this ID matches your XML root layout

        // Check if the EditText already exists
        if (rootLayout.findViewWithTag<EditText>("playerNameInput") == null) {
            // Create the EditText dynamically
            val nameInput = EditText(this).apply {
                tag = "playerNameInput" // Assign a tag for identification
                hint = "Enter Player Name"
                textSize = 18f
                setPadding(16, 16, 16, 16)
                setBackgroundColor(Color.WHITE)
            }

            // Define layout parameters for positioning
            val layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                addRule(RelativeLayout.BELOW, R.id.hello_world) // Position below the TextView
                setMargins(16, 16, 16, 16) // Add margins
            }

            // Add the EditText to the RelativeLayout
            rootLayout.addView(nameInput, layoutParams)

            // Handle player name input
            nameInput.setOnEditorActionListener { _, _, _ ->
                val input = nameInput.text.toString().trim()
                if (input.isNotEmpty()) {
                    playerName = input // Save to the playerName variable
                    Toast.makeText(this, "Welcome, $playerName!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Name cannot be empty!", Toast.LENGTH_SHORT).show()
                }
                false // Dismiss the keyboard
            }
        }
    }

    // Function to retrieve the player's name when needed
    private fun getPlayerName(): String {
        return playerName
    }

    private fun drawGameOverMessage(canvas: Canvas) {
        // 1. Draw the background gradient
        val paint = Paint()
        val gradient = LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            Color.BLUE, Color.CYAN, Shader.TileMode.CLAMP
        )
        paint.shader = gradient
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        // 2. Draw the game over text
        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 100f
            textAlign = Paint.Align.CENTER
            setShadowLayer(10f, 5f, 5f, Color.BLACK)
        }

        // Base Y position for the first text
        var baseY = (canvas.height / 2).toFloat() - 800
        val lineSpacing = 150f

        canvas.drawText("Game Over", (canvas.width / 2).toFloat(), baseY, textPaint)
        baseY += lineSpacing
        canvas.drawText("Score: $score", (canvas.width / 2).toFloat(), baseY, textPaint)
        baseY += lineSpacing
        canvas.drawText("Level: $level", (canvas.width / 2).toFloat(), baseY, textPaint)
        baseY += lineSpacing
        canvas.drawText("Tap to restart", (canvas.width / 2).toFloat(), baseY, textPaint)

        if (!highScoreSaved) {
            // Save the high score only once
            saveHighScore(playerName, score)
            highScoreSaved = true // Set the flag to prevent duplicate writes
        }
        if (!fetchedHighScoresBool) {
            getHighScores()
            fetchedHighScoresBool = true
        } else {
            drawHighScores(canvas)
        }


    }

    private fun saveHighScore(playerName: String, score: Int) {
        val user = hashMapOf(
            "playerName" to playerName,
            "score" to score,
            "timestamp" to System.currentTimeMillis() // Add a timestamp
        )

        db.collection("high_scores")
            .add(user)
            .addOnSuccessListener { documentReference ->
                Log.d(TAG, "DocumentSnapshot added with ID: ${documentReference.id}")
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Error adding document", e)
            }
    }

        private fun drawHighScores(canvas: Canvas) {
            val textPaint = Paint().apply {
                color = Color.WHITE
                textSize = 100f
                textAlign = Paint.Align.CENTER
                setShadowLayer(10f, 5f, 5f, Color.BLACK)
            }

            // Positioning variables
            val baseY = (canvas.height / 2).toFloat() + 200
            val lineSpacing = 200f
            var currentY = baseY

            // Draw header text
            canvas.drawText("High Scores", (canvas.width / 2).toFloat(), baseY - 200, textPaint)

            // Draw each high score
            fetchedHighScores.forEachIndexed { index, (playerName, score) ->
                val scoreText = "${index + 1}. $playerName: $score"
                canvas.drawText(scoreText, (canvas.width / 2).toFloat(), currentY, textPaint)
                currentY += lineSpacing
            }
        }

    fun getHighScores() {
        db.collection("high_scores")
            .orderBy("score", Query.Direction.DESCENDING)
            .limit(5)
            .get()
            .addOnSuccessListener { result ->
                fetchedHighScores = result.documents.mapNotNull { doc ->
                    val playerName = doc.getString("playerName")
                    val score = doc.getLong("score")
                    if (playerName != null && score != null) {
                        playerName to score // Store as a Pair
                    } else null
                }
                postInvalidate() // Trigger a redraw of the Canvas
            }
            .addOnFailureListener { e ->
                println("Error getting documents: $e")
            }
    }

    private fun drawLevel(canvas: Canvas) {
        paint.color = Color.YELLOW // Bright color for the level
        paint.textSize = 60f // Larger text size
        canvas.drawText("Level: $level", 20f, 120f, paint) // Draw the level below the score
    }

    private fun drawScore(canvas: Canvas) {
        paint.color = Color.CYAN // Bright color for score
        paint.textSize = 60f // Larger text size
        canvas.drawText("Score: $score", 20f, 60f, paint) // Draw the score at the top left corner
    }

    private fun drawTetromino(canvas: Canvas) {
        val borderPaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }

        paint.reset() // Reset paint
        paint.color = when (tetromino.type) {
            TetrominoType.I -> Color.CYAN
            TetrominoType.O -> Color.YELLOW
            TetrominoType.T -> Color.MAGENTA
            TetrominoType.S -> Color.GREEN
            TetrominoType.Z -> Color.RED
            TetrominoType.J -> Color.BLUE
            TetrominoType.L -> Color.rgb(255, 165, 0)
        }

        for (i in tetromino.shape.indices) {
            for (j in tetromino.shape[i].indices) {
                if (tetromino.shape[i][j] != 0) {
                    val left = offsetX + (tetromino.xPos + j) * blockSize
                    val top = offsetY + (tetromino.yPos + i) * blockSize
                    val right = left + blockSize
                    val bottom = top + blockSize

                    // Draw filled block
                    canvas.drawRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(), paint)

                    // Draw border
                    canvas.drawRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(), borderPaint)
                }
            }
        }

        // Draw rectangles in the sides of the screen
        paint.color = Color.BLACK
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, offsetX.toFloat(), height.toFloat(), paint)
        canvas.drawRect((offsetX + gridWidth * blockSize).toFloat(), 0f, width.toFloat(), height.toFloat(), paint)
    }

    private fun drawGrid(canvas: Canvas) {
        for (i in 0 until gridHeight) {
            for (j in 0 until gridWidth) { // Start from column 0
                if (grid[i][j] != 0) { // Check if block exists
                    paint.color = grid[i][j]
                    paint.style = Paint.Style.FILL

                    // Draw filled block
                    canvas.drawRect(
                        (offsetX + j * blockSize).toFloat(),
                        (offsetY + i * blockSize).toFloat(),
                        (offsetX + (j + 1) * blockSize).toFloat(),
                        (offsetY + (i + 1) * blockSize).toFloat(),
                        paint
                    )

                    // Draw borders
                    paint.style = Paint.Style.STROKE
                    paint.color = Color.BLACK
                    paint.strokeWidth = 4f
                    canvas.drawRect(
                        (offsetX + j * blockSize).toFloat(),
                        (offsetY + i * blockSize).toFloat(),
                        (offsetX + (j + 1) * blockSize).toFloat(),
                        (offsetY + (i + 1) * blockSize).toFloat(),
                        paint
                    )
                }
            }
        }
    }

    private fun drawBackground(canvas: Canvas) {
        // Initialize the gradient dynamically with the correct height
        paint.reset() // Reset paint
        val gradient = LinearGradient(
            0f, 0f, 0f, canvas.height.toFloat(),
            Color.BLUE, Color.CYAN, Shader.TileMode.CLAMP
        )
        paint.shader = gradient

        // Draw gradient background
        canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), paint)

        // Reset shader for the bottom rectangle
        paint.shader = null
        paint.color = Color.BLACK // Black color for the bottom bar

        // Calculate the bottom of the grid in pixels
        val gridBottom = blockSize * gridHeight // canvas height and grid height are not the same !!!

        // Draw the rectangle from the bottom of the grid to the bottom of the screen
        canvas.drawRect(
            0f,
            gridBottom.toFloat(), // Top of the rectangle (bottom of the grid)
            canvas.width.toFloat(),
            canvas.height.toFloat(), // Bottom of the screen
            paint
        )
    }

    private fun togglePause() {
        paused = !paused // Toggle the pause state
        if (paused) {
            handler.removeCallbacks(gameRunnable) // Pause the game loop
            fastDropHandler.removeCallbacks(fastDropRunnable) // Stop fast drop if running
            mediaPlayer?.pause() // Pause sound effects if needed
        } else {
            handler.postDelayed(gameRunnable, dropDelay) // Resume the game loop
            if (isFastDropping) {
                fastDropHandler.post(fastDropRunnable) // Resume fast drop if it was active
            }
            mediaPlayer?.start() // Resume sounds if applicable
        }
        invalidate() // Redraw the view to reflect the paused state
    }

    private fun rotateTetromino() {
        val originalShape = tetromino.shape // Save original shape
        tetromino.rotate() // Rotate the tetromino

        // Check for collision after rotation
        if (checkCollision(tetromino, tetromino.xPos, tetromino.yPos)) {
            tetromino.shape = originalShape // Revert to original shape if collision occurs
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // Handle Pause/Resume button tap
                val x = event.x
                val y = event.y

                // Pause button press detection
                if (y < 150) {
                    togglePause() // Toggle pause state
                    return true
                }

                // Resume press detection (when paused)
                if (paused) {
                    togglePause() // Resume the game
                    return true
                }

                // If game is over, restart the game
                if (gameOver) {
                    highScoreSaved = false // Reset high score saved flag
                    fetchedHighScoresBool = false // Reset fetched high scores flag
                    resetGame() // Restart game if game over
                    return true
                }

                if (!gameIsStarted) {
                    gameIsStarted = true
                    handler.post(gameRunnable) // Start the game loop
                    invalidate() // Redraw the view
                    return true
                }

                // Handle touch events for other buttons
                if (y > 220 && y < height * 2 / 6) {
                    rotateTetromino() // Rotate Tetromino
                } else if (y > height * 4 / 6) {
                    isFastDropping = true
                    fastDropHandler.post(fastDropRunnable) // Start fast drop
                } else {
                    // Move left or right based on touch position
                    if (x < width / 2 && y > height * 2 / 6 && y < height * 4 / 6) {
                        moveLeft()
                    } else if (x > width / 2 && y > height * 2 / 6 && y < height * 4 / 6) {
                        moveRight()
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // Stop fast dropping when touch ends
                isFastDropping = false
                fastDropHandler.removeCallbacks(fastDropRunnable) // Remove the runnable
            }
        }
        invalidate() // Redraw the view
        return true
    }

    private fun updateLevel(linesCleared: Int) {
        level += linesCleared // Increase level by the number of lines cleared
        if (level > 10) level = 10 // Cap the level at a maximum
        updateDropDelay() // Adjust the drop delay based on the new level
    }


    private fun getNextTetromino(): Tetromino {
        val newType = TetrominoType.values().random() // Randomly select a Tetromino type
        val color = when (newType) {
            TetrominoType.I -> Color.CYAN
            TetrominoType.O -> Color.YELLOW
            TetrominoType.T -> Color.MAGENTA
            TetrominoType.S -> Color.GREEN
            TetrominoType.Z -> Color.RED
            TetrominoType.J -> Color.BLUE
            TetrominoType.L -> Color.rgb(255, 165, 0) // Custom orange color for L
        }
                val newTetromino = TETROMINOS[newType]?.copy(color = color, xPos = 6, yPos = 0)
                ?: throw IllegalStateException("Tetromino type $newType not found in TETROMINOS map.")

        // Check for game over condition
        if (isGameOver(newTetromino)) {
            gameOver = true // Set the game over flag
            onGameOver() // Handle game over logic
        }

        return newTetromino
    }

    private fun moveDown() {
        // Check for collision with the block below
        if (!gameOver && !checkCollision(tetromino, tetromino.xPos, tetromino.yPos + 1)) {
            tetromino.yPos++ // Move down if no collision
            playClickSound(context) // Make sure to pass the current context
        } else {
            if (!gameOver) { // Only lock if game is not over
                lockPiece(tetromino) // Lock the Tetromino in place
                clearFullLines() // Clear any full lines
                tetromino = getNextTetromino() // Get the next Tetromino
                invalidate() // Invalidate to redraw the new Tetromino immediately
            }
        }
        // Invalidate again to reflect movement if Tetromino is still moving down
        invalidate()
    }

    private fun playClickSound(context: Context) {
        // Reset MediaPlayer if it's already playing
        if (mediaPlayer != null) {
            mediaPlayer!!.release()
        }

        // Create MediaPlayer instance with the sound resource
        mediaPlayer = MediaPlayer.create(context, R.raw.click)
        mediaPlayer?.setOnCompletionListener {
            // Release the MediaPlayer once the sound has finished playing
            mediaPlayer?.release()
            mediaPlayer = null
        }

        mediaPlayer?.start() // Start playing the sound
    }

    private fun playGameOverSound(context: Context) {
        // Reset MediaPlayer if it's already playing
        if (mediaPlayer != null) {
            mediaPlayer!!.release()
        }

        // Create MediaPlayer instance with the sound resource
        mediaPlayer = MediaPlayer.create(context, R.raw.gameover)
        mediaPlayer?.setOnCompletionListener {
            // Release the MediaPlayer once the sound has finished playing
            mediaPlayer?.release()
            mediaPlayer = null
        }

        mediaPlayer?.start() // Start playing the sound
    }

    private fun moveLeft() {
        if (!checkCollision(tetromino, tetromino.xPos - 1, tetromino.yPos)) {
            tetromino.xPos--
        }
        invalidate() // Redraw after moving
    }

    private fun moveRight() {
        if (!checkCollision(tetromino, tetromino.xPos + 1, tetromino.yPos)) {
            tetromino.xPos++
        }
        invalidate() // Redraw after moving
    }

    private fun checkCollision(tetromino: Tetromino, newX: Int, newY: Int): Boolean {
        for (i in tetromino.shape.indices) {
            for (j in tetromino.shape[i].indices) {
                if (tetromino.shape[i][j] != 0) {
                    val x = newX + j
                    val y = newY + i

                    // Check within bounds and for existing blocks
                    if (x < 0 || x >= gridWidth || y >= gridHeight || (y >= 0 && grid[y][x] != 0)) {
                        return true
                    }
                }
            }
        }
        return false
    }


    private fun lockPiece(tetromino: Tetromino) {
        val color = when (tetromino.type) {
            TetrominoType.I -> Color.CYAN
            TetrominoType.O -> Color.YELLOW
            TetrominoType.T -> Color.MAGENTA
            TetrominoType.S -> Color.GREEN
            TetrominoType.Z -> Color.RED
            TetrominoType.J -> Color.BLUE
            TetrominoType.L -> Color.rgb(255, 165, 0)
        }

        var lockedAtTop = false

        for (i in tetromino.shape.indices) {
            for (j in tetromino.shape[i].indices) {
                if (tetromino.shape[i][j] != 0) {
                    val y = tetromino.yPos + i
                    val x = tetromino.xPos + j

                    if (y == 0) {
                        lockedAtTop = true
                    }

                    // Use grid with offsetX
                    if (x in 0 until gridWidth && y in 0 until gridHeight) {
                        grid[y][x] = color
                    }
                }
            }
        }

        if (lockedAtTop) {
            onGameOver()
            return
        }
    }

    private fun onGameOver() {
        // Set the gameOver flag to ensure the correct screen is drawn
        gameOver = true

        // Stop the game loop
        handler.removeCallbacks(gameRunnable)

        // Play the game over sound
        playGameOverSound(context)
    }

    private fun isGameOver(tetromino: Tetromino): Boolean {
        for (i in tetromino.shape.indices) {
            for (j in tetromino.shape[i].indices) {
                if (tetromino.shape[i][j] != 0) { // Check for occupied blocks
                    if (tetromino.yPos + i < 0) { // If any part is above the grid
                        return true // Game over
                    }
                }
            }
        }
        return false // No collision with the top
    }

    private fun clearFullLines() {
        var linesCleared = 0
        for (i in grid.indices) {
            if (grid[i].all { it != 0 }) { // Line is full
                linesCleared++
                for (j in i downTo 1) {
                    grid[j] = grid[j - 1] // Shift lines down
                }
                grid[0] = IntArray(gridWidth) // Clear the top line
            }
        }

        if (linesCleared > 0) {
            score += linesCleared * 100 // Update score
            updateLevel(linesCleared) // Update level
        }
    }

    // Method to reset the game
    private fun resetGame() {
        // Reset game-related variables
        score = 0 // Reset score
        level = 1 // Reset level, if applicable
        gameOver = false // Reset game over status

        // Initialize game state
        initializeGame()  // Ensure this method is defined to set up the game

        // Start the game loop again
        handler.post(gameRunnable) // Restart the game loop
    }


    private fun initializeGame() {
        // Reset game variables
        score = 0
        level = 1
        gameOver = false
        grid.forEachIndexed { index, _ ->
            grid[index] = IntArray(gridWidth) // Reset each row to empty
        }
        tetromino = getNextTetromino() // Get the first Tetromino
        updateDropDelay() // Reset the drop delay
    }
}
