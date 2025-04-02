package com.example.kilemilek.activities

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipDescription
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.DragEvent
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import com.example.kilemilek.LetterDistribution
import com.example.kilemilek.R
import com.example.kilemilek.models.GameData
import com.example.kilemilek.models.GameRequestModel
import com.example.kilemilek.models.LastMove
import com.example.kilemilek.objects.GameBoardMatrix
import com.example.kilemilek.views.GameBoardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import de.hdodenhof.circleimageview.CircleImageView
import java.util.*

class GameActivity : AppCompatActivity() {

    private lateinit var gameBoardView: GameBoardView
    private lateinit var gameIdTextView: TextView
    private lateinit var opponentNameTextView: TextView
    private lateinit var yourScoreTextView: TextView
    private lateinit var opponentScoreTextView: TextView
    private lateinit var remainingLettersTextView: TextView
    private lateinit var letterRackLayout: LinearLayout
    private lateinit var playButton: Button
    private lateinit var withdrawButton: Button

    private lateinit var gameId: String
    private lateinit var gameRequest: GameRequestModel
    private lateinit var db: FirebaseFirestore
    private lateinit var currentUserId: String

    // Letters in the player's rack
    private val playerLetters = mutableListOf<Char>()

    // Map to track which letters have been placed on the board
    private val placedLetters = mutableMapOf<Pair<Int, Int>, Char>()

    // Map to track which letters have been placed in the current turn
    private val currentTurnLetters = mutableMapOf<Pair<Int, Int>, Char>()

    // Current dragged letter data
    private var currentDraggedLetter: Char? = null
    private var currentDraggedView: View? = null
    private var dragSourceIsBoard = false
    private var dragSourcePosition: Pair<Int, Int>? = null

    // Game state flags
    private var isFirstMoveInGame = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        // Enable back button in the action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Word Finder Game"

        // Initialize views
        gameBoardView = findViewById(R.id.game_board_view)
        gameIdTextView = findViewById(R.id.game_id_text)
        opponentNameTextView = findViewById(R.id.opponent_name_text)
        yourScoreTextView = findViewById(R.id.your_score_text)
        opponentScoreTextView = findViewById(R.id.opponent_score_text)
        remainingLettersTextView = findViewById(R.id.remaining_letters_text)
        letterRackLayout = findViewById(R.id.letter_rack)
        playButton = findViewById(R.id.play_button)
        withdrawButton = findViewById(R.id.withdraw_button)

        // Initialize Firestore
        db = FirebaseFirestore.getInstance()

        // Get current user ID
        currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

        // Get game ID from intent
        gameId = intent.getStringExtra("GAME_ID") ?: ""

        if (gameId.isEmpty()) {
            Toast.makeText(this, "Game ID is missing", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Set up drag and drop for the game board
        setupBoardDragAndDrop()

        // Set up play button
        playButton.setOnClickListener {
            validateAndSubmitPlay()
        }

        // Set up withdraw button
        withdrawButton.setOnClickListener {
            withdrawLetters()
        }

        // Load game data
        loadGameData()
    }

    private fun withdrawLetters() {
        // Return all current turn letters to the rack
        currentTurnLetters.forEach { (position, letter) ->
            // Clear the letter from the board
            gameBoardView.clearLetter(position.first, position.second)

            // Add the letter back to the player's rack
            playerLetters.add(letter)
        }

        // Clear the current turn letters map
        currentTurnLetters.clear()

        // Update the letter rack UI
        updateLetterRackUI()

        Toast.makeText(this, "Letters withdrawn", Toast.LENGTH_SHORT).show()
    }

    private fun loadGameData() {
        db.collection("game_requests").document(gameId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    // Parse game request
                    gameRequest = document.toObject(GameRequestModel::class.java) ?: return@addOnSuccessListener

                    // Check if user is part of this game
                    if (gameRequest.senderId != currentUserId && gameRequest.receiverId != currentUserId) {
                        Toast.makeText(this, "You are not part of this game", Toast.LENGTH_SHORT).show()
                        finish()
                        return@addOnSuccessListener
                    }

                    // Determine if this is the first move in the game
                    isFirstMoveInGame = gameRequest.gameData.boardState.isEmpty()

                    // Update UI
                    updateGameUI()

                    // Load or initialize player letters
                    loadPlayerLetters()
                } else {
                    Toast.makeText(this, "Game not found", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Error loading game: ${exception.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun updateGameUI() {
        // Set game ID
        gameIdTextView.text = "Game ID: $gameId"

        // Set opponent name
        val opponentName = if (gameRequest.senderId == currentUserId) {
            gameRequest.receiverName
        } else {
            gameRequest.senderName
        }
        opponentNameTextView.text = "Playing against: $opponentName"

        // Set scores
        val yourScore = gameRequest.gameData.playerScores[currentUserId] ?: 0
        val opponentId = if (gameRequest.senderId == currentUserId) gameRequest.receiverId else gameRequest.senderId
        val opponentScore = gameRequest.gameData.playerScores[opponentId] ?: 0

        yourScoreTextView.text = "Your score: $yourScore"
        opponentScoreTextView.text = "Opponent score: $opponentScore"

        // Set remaining letters count
        val remainingCount = LetterDistribution.getRemainingLetterCount()
        remainingLettersTextView.text = "Remaining letters: $remainingCount"

        // Clear the placed letters tracking
        placedLetters.clear()

        // Set board state if available
        gameRequest.gameData.boardState.forEach { (position, letter) ->
            try {
                val parts = position.split(",")
                val row = parts[0].toInt()
                val col = parts[1].toInt()
                gameBoardView.placeLetter(row, col, letter[0])

                // Track placed letters (these are from previous turns)
                placedLetters[Pair(row, col)] = letter[0]
            } catch (e: Exception) {
                // Skip invalid positions
            }
        }
    }

    private fun loadPlayerLetters() {
        // Check if player already has letters assigned
        val playerLettersKey = "${gameId}_${currentUserId}_letters"
        val sharedPreferences = getSharedPreferences("kilemilek_game", MODE_PRIVATE)
        val savedLetters = sharedPreferences.getString(playerLettersKey, null)

        if (savedLetters != null && savedLetters.isNotEmpty()) {
            // Player already has letters assigned
            playerLetters.clear()
            playerLetters.addAll(savedLetters.toList())
        } else {
            // Assign new letters
            playerLetters.clear()
            playerLetters.addAll(LetterDistribution.drawLetters(7))

            // Save assigned letters
            sharedPreferences.edit()
                .putString(playerLettersKey, playerLetters.joinToString(""))
                .apply()
        }

        // Update the letter rack UI
        updateLetterRackUI()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun updateLetterRackUI() {
        // Clear existing letters
        letterRackLayout.removeAllViews()

        // Create letter tiles
        playerLetters.forEach { letter ->
            // Create letter tile card
            val letterCard = createLetterTile(letter, false) // false means it's not a gray tile

            // Add touch listener for drag and drop
            letterCard.setOnTouchListener { view, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    try {
                        // Start drag operation
                        currentDraggedLetter = letter
                        currentDraggedView = view
                        dragSourceIsBoard = false
                        dragSourcePosition = null

                        val item = ClipData.Item(letter.toString())
                        val dragData = ClipData(
                            letter.toString(),
                            arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN),
                            item
                        )

                        val shadowBuilder = View.DragShadowBuilder(view)
                        view.startDragAndDrop(
                            dragData,
                            shadowBuilder,
                            view,
                            0
                        )

                        // Hide the original view during drag
                        view.visibility = View.INVISIBLE
                        true
                    } catch (e: Exception) {
                        Log.e("GameActivity", "Error starting drag: ${e.message}")
                        view.visibility = View.VISIBLE
                        false
                    }
                } else {
                    false
                }
            }

            // Add to the letter rack
            letterRackLayout.addView(letterCard)
        }
    }

    private fun createLetterTile(letter: Char, isGray: Boolean): CardView {
        // Create card container
        val letterCard = CardView(this).apply {
            radius = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 8f, resources.displayMetrics
            )
            elevation = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 4f, resources.displayMetrics
            )
            cardElevation = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 4f, resources.displayMetrics
            )

            // Use gray background for tiles on board
            if (isGray) {
                setCardBackgroundColor(Color.parseColor("#BDBDBD")) // Gray for placed tiles
            } else {
                setCardBackgroundColor(Color.parseColor("#FFF59D")) // Yellow for rack tiles
            }

            val cardSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 40f, resources.displayMetrics
            ).toInt()

            layoutParams = LinearLayout.LayoutParams(cardSize, cardSize).apply {
                marginEnd = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 8f, resources.displayMetrics
                ).toInt()
            }
        }

        // Create letter content
        val letterLayout = ConstraintLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Letter display
        val letterText = TextView(this).apply {
            text = letter.toString()
            textSize = 20f
            setTextColor(Color.parseColor("#212121")) // Dark gray for letter text
            textAlignment = View.TEXT_ALIGNMENT_CENTER

            // Center the text
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            }
        }

        // Letter value
        val valueText = TextView(this).apply {
            text = LetterDistribution.getLetterValue(letter).toString()
            textSize = 10f
            setTextColor(Color.parseColor("#757575")) // Medium gray for value

            // Position at bottom right
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                marginEnd = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 2f, resources.displayMetrics
                ).toInt()
                bottomMargin = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 2f, resources.displayMetrics
                ).toInt()
            }
        }

        // Add views to layout
        letterLayout.addView(letterText)
        letterLayout.addView(valueText)
        letterCard.addView(letterLayout)

        return letterCard
    }

    private fun setupBoardDragAndDrop() {
        // Make board cells clickable for withdrawing letters
        gameBoardView.setOnCellTouchListener { row, col, letter ->
            try {
                if (letter != null && currentTurnLetters.containsKey(Pair(row, col))) {
                    // This is a letter placed in current turn - withdraw it to the rack
                    withdrawLetterFromBoard(row, col)
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                Log.e("GameActivity", "Error handling cell touch: ${e.message}")
                false
            }
        }

        // Set up drop on board
        gameBoardView.setOnDragListener { view, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> {
                    event.clipDescription.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)
                }
                DragEvent.ACTION_DRAG_ENTERED, DragEvent.ACTION_DRAG_LOCATION -> {
                    true
                }
                DragEvent.ACTION_DRAG_EXITED -> {
                    true
                }
                DragEvent.ACTION_DROP -> {
                    try {
                        // Get the drop position
                        val boardSize = GameBoardMatrix.BOARD_SIZE.toFloat()
                        val cellSize = gameBoardView.width / boardSize

                        val col = (event.x / cellSize).toInt()
                        val row = (event.y / cellSize).toInt()

                        // Check if position is valid
                        if (row in 0 until GameBoardMatrix.BOARD_SIZE &&
                            col in 0 until GameBoardMatrix.BOARD_SIZE &&
                            currentDraggedLetter != null) {

                            // Check if the cell is already occupied
                            if (placedLetters.containsKey(Pair(row, col)) ||
                                currentTurnLetters.containsKey(Pair(row, col))) {

                                // Cell is occupied, return letter to rack
                                if (!dragSourceIsBoard) {
                                    currentDraggedView?.visibility = View.VISIBLE
                                } else {
                                    // If dragging from board, put it back
                                    dragSourcePosition?.let { (sourceRow, sourceCol) ->
                                        gameBoardView.placeLetter(sourceRow, sourceCol, currentDraggedLetter!!)
                                        currentTurnLetters[Pair(sourceRow, sourceCol)] = currentDraggedLetter!!
                                    }
                                }
                            } else {
                                // Check if this is first move in the game
                                if (isFirstMoveInGame && placedLetters.isEmpty() && currentTurnLetters.isEmpty()) {
                                    // First move must include the center tile
                                    val centerRow = GameBoardMatrix.BOARD_SIZE / 2
                                    val centerCol = GameBoardMatrix.BOARD_SIZE / 2

                                    if (row != centerRow || col != centerCol) {
                                        Toast.makeText(this, "First move must include the center tile", Toast.LENGTH_SHORT).show()

                                        // Return letter to rack
                                        if (!dragSourceIsBoard) {
                                            currentDraggedView?.visibility = View.VISIBLE
                                        } else {
                                            // If dragging from board, put it back
                                            dragSourcePosition?.let { (sourceRow, sourceCol) ->
                                                gameBoardView.placeLetter(sourceRow, sourceCol, currentDraggedLetter!!)
                                                currentTurnLetters[Pair(sourceRow, sourceCol)] = currentDraggedLetter!!
                                            }
                                        }

                                        return@setOnDragListener true
                                    }
                                } else if (!isFirstMoveInGame && !currentTurnLetters.isEmpty() && !isAdjacentToExistingLetter(row, col)) {
                                    // After first move, letters must be adjacent to existing letters
                                    Toast.makeText(this, "Letters must be adjacent to existing letters", Toast.LENGTH_SHORT).show()

                                    // Return letter to rack
                                    if (!dragSourceIsBoard) {
                                        currentDraggedView?.visibility = View.VISIBLE
                                    } else {
                                        // If dragging from board, put it back
                                        dragSourcePosition?.let { (sourceRow, sourceCol) ->
                                            gameBoardView.placeLetter(sourceRow, sourceCol, currentDraggedLetter!!)
                                            currentTurnLetters[Pair(sourceRow, sourceCol)] = currentDraggedLetter!!
                                        }
                                    }

                                    return@setOnDragListener true
                                }

                                // Place letter on the board
                                val letterToPlace = currentDraggedLetter!!

                                // For board view, just place the letter
                                gameBoardView.placeLetter(row, col, letterToPlace)

                                // Track placed letter in current turn
                                currentTurnLetters[Pair(row, col)] = letterToPlace

                                // If the letter was from the rack (not from board)
                                if (!dragSourceIsBoard) {
                                    // Remove letter from player's rack
                                    val letterIndex = playerLetters.indexOf(letterToPlace)
                                    if (letterIndex >= 0) {
                                        playerLetters.removeAt(letterIndex)
                                        // Update the letter rack UI with a slight delay to avoid UI conflicts
                                        view.postDelayed({
                                            updateLetterRackUI()
                                        }, 100)
                                    }
                                }
                            }
                        } else {
                            // Return letter to rack if drop location is invalid
                            if (!dragSourceIsBoard) {
                                currentDraggedView?.visibility = View.VISIBLE
                            } else {
                                // If dragging from board, put it back
                                dragSourcePosition?.let { (sourceRow, sourceCol) ->
                                    gameBoardView.placeLetter(sourceRow, sourceCol, currentDraggedLetter!!)
                                    currentTurnLetters[Pair(sourceRow, sourceCol)] = currentDraggedLetter!!
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("GameActivity", "Error in drop: ${e.message}")
                        e.printStackTrace()
                        // Make sure letter is visible again if there was an error
                        if (!dragSourceIsBoard) {
                            currentDraggedView?.visibility = View.VISIBLE
                        } else {
                            dragSourcePosition?.let { (sourceRow, sourceCol) ->
                                if (currentDraggedLetter != null) {
                                    gameBoardView.placeLetter(sourceRow, sourceCol, currentDraggedLetter!!)
                                    currentTurnLetters[Pair(sourceRow, sourceCol)] = currentDraggedLetter!!
                                }
                            }
                        }
                    }

                    true
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    try {
                        // If drag ended without drop, show the view again or add back to letters
                        if (!event.result) {
                            if (!dragSourceIsBoard) {
                                // Letter was from rack
                                currentDraggedView?.visibility = View.VISIBLE
                            } else {
                                // Letter was from board, add back to player letters
                                currentDraggedLetter?.let {
                                    playerLetters.add(it)
                                    updateLetterRackUI()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("GameActivity", "Error in drag ended: ${e.message}")
                        e.printStackTrace()
                    }

                    // Reset drag state
                    currentDraggedLetter = null
                    currentDraggedView = null
                    dragSourceIsBoard = false
                    dragSourcePosition = null

                    true
                }
                else -> false
            }
        }

        // Set up drop on letter rack (for returning letters)
        letterRackLayout.setOnDragListener { view, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> {
                    // Accept all drags
                    true
                }
                DragEvent.ACTION_DROP -> {
                    try {
                        // Return the letter to the rack
                        if (currentDraggedLetter != null) {
                            // If from board, remove from current turn letters
                            if (dragSourceIsBoard && dragSourcePosition != null) {
                                currentTurnLetters.remove(dragSourcePosition)
                            }

                            // Add to player rack
                            playerLetters.add(currentDraggedLetter!!)
                            view.post {
                                updateLetterRackUI()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("GameActivity", "Error returning letter to rack: ${e.message}")
                        e.printStackTrace()
                    }
                    true
                }
                else -> true
            }
        }
    }

    private fun withdrawLetterFromBoard(row: Int, col: Int) {
        val position = Pair(row, col)
        val letter = currentTurnLetters[position] ?: return

        // Remove the letter from the board
        gameBoardView.clearLetter(row, col)

        // Remove from current turn letters
        currentTurnLetters.remove(position)

        // Add back to player's rack
        playerLetters.add(letter)

        // Update letter rack UI
        updateLetterRackUI()
    }

    private fun isAdjacentToExistingLetter(row: Int, col: Int): Boolean {
        // If this is the very first letter being placed in the turn
        if (currentTurnLetters.isEmpty()) {
            // Check if the position is adjacent to any existing letter from previous turns
            val positions = listOf(
                Pair(row - 1, col), // Above
                Pair(row + 1, col), // Below
                Pair(row, col - 1), // Left
                Pair(row, col + 1)  // Right
            )

            for (pos in positions) {
                // Only check letters from previous turns (placedLetters)
                if (placedLetters.containsKey(pos)) {
                    return true
                }
            }

            return false
        } else {
            // If we're placing subsequent letters in the same turn,
            // they must be adjacent to another letter placed in this turn
            // and they must form a straight line

            // Get the positions of all currently placed letters in this turn
            val currentPositions = currentTurnLetters.keys.toList()

            // Check if this is the second letter being placed
            if (currentTurnLetters.size == 1) {
                val firstPos = currentPositions[0]

                // Check if the new position is adjacent to the first letter
                return (
                        (row == firstPos.first && (col == firstPos.second - 1 || col == firstPos.second + 1)) || // Same row, adjacent column
                                (col == firstPos.second && (row == firstPos.first - 1 || row == firstPos.first + 1))     // Same column, adjacent row
                        )
            } else {
                // If more than one letter has been placed, determine the orientation
                val rows = currentPositions.map { it.first }.toSet()
                val cols = currentPositions.map { it.second }.toSet()

                if (rows.size == 1) {
                    // Letters are in a horizontal line
                    val fixedRow = rows.first()

                    // New letter must be in the same row
                    if (row != fixedRow) {
                        return false
                    }

                    // Check if the new position is adjacent to the existing line
                    val minCol = cols.minOrNull()!!
                    val maxCol = cols.maxOrNull()!!

                    return (col == minCol - 1 || col == maxCol + 1)
                } else if (cols.size == 1) {
                    // Letters are in a vertical line
                    val fixedCol = cols.first()

                    // New letter must be in the same column
                    if (col != fixedCol) {
                        return false
                    }

                    // Check if the new position is adjacent to the existing line
                    val minRow = rows.minOrNull()!!
                    val maxRow = rows.maxOrNull()!!

                    return (row == minRow - 1 || row == maxRow + 1)
                } else {
                    // Letters are not in a straight line, which should not happen
                    // because we validate this elsewhere
                    return false
                }
            }
        }
    }

    private fun validateAndSubmitPlay() {
        // Validate the current play
        if (currentTurnLetters.isEmpty()) {
            Toast.makeText(this, "No letters placed on the board", Toast.LENGTH_SHORT).show()
            return
        }

        // Check if letters are in a straight line
        if (!areLettersInStraightLine()) {
            Toast.makeText(this, "Letters must be placed in a straight line", Toast.LENGTH_SHORT).show()
            return
        }

        // Check if letters are contiguous
        if (!areLettersContiguous()) {
            Toast.makeText(this, "Letters must be contiguous (no gaps)", Toast.LENGTH_SHORT).show()
            return
        }

        // If all validations pass, submit the play
        submitPlay()
    }

    private fun areLettersInStraightLine(): Boolean {
        if (currentTurnLetters.size <= 1) {
            return true // Single letter is always in a straight line
        }

        // Get all row and column values
        val rows = currentTurnLetters.keys.map { it.first }.toSet()
        val cols = currentTurnLetters.keys.map { it.second }.toSet()

        // If all letters are in the same row or same column, they're in a straight line
        return rows.size == 1 || cols.size == 1
    }

    private fun areLettersContiguous(): Boolean {
        if (currentTurnLetters.size <= 1) {
            return true // Single letter is always contiguous
        }

        // Get all row and column values
        val positions = currentTurnLetters.keys.toList()
        val rows = positions.map { it.first }.toSet()
        val cols = positions.map { it.second }.toSet()

        // If letters are in a single row
        if (rows.size == 1) {
            val row = rows.first()
            val minCol = cols.minOrNull()!!
            val maxCol = cols.maxOrNull()!!

            // Check for gaps in the column sequence
            for (col in minCol..maxCol) {
                val position = Pair(row, col)
                if (!currentTurnLetters.containsKey(position) && !placedLetters.containsKey(position)) {
                    return false // Gap found
                }
            }
            return true
        }

        // If letters are in a single column
        if (cols.size == 1) {
            val col = cols.first()
            val minRow = rows.minOrNull()!!
            val maxRow = rows.maxOrNull()!!

            // Check for gaps in the row sequence
            for (row in minRow..maxRow) {
                val position = Pair(row, col)
                if (!currentTurnLetters.containsKey(position) && !placedLetters.containsKey(position)) {
                    return false // Gap found
                }
            }
            return true
        }

        return false // Letters are neither in a single row nor in a single column
    }

    private fun submitPlay() {
        // Create updated board state map (merging existing and new letters)
        val boardStateMap = mutableMapOf<String, String>()

        // Add existing letters
        placedLetters.forEach { (position, letter) ->
            boardStateMap["${position.first},${position.second}"] = letter.toString()
        }

        // Add new letters
        currentTurnLetters.forEach { (position, letter) ->
            boardStateMap["${position.first},${position.second}"] = letter.toString()
        }

        // Calculate score (very basic implementation for now)
        val playScore = currentTurnLetters.entries.sumOf { (position, letter) ->
            val tileType = GameBoardMatrix.getTileType(position.first, position.second)
            var letterValue = LetterDistribution.getLetterValue(letter)

            // Apply letter multipliers
            when (tileType) {
                GameBoardMatrix.DL -> letterValue *= 2 // Double Letter
                GameBoardMatrix.TL -> letterValue *= 3 // Triple Letter
            }

            letterValue
        }

        // Apply word multipliers
        var wordMultiplier = 1
        currentTurnLetters.keys.forEach { position ->
            val tileType = GameBoardMatrix.getTileType(position.first, position.second)
            when (tileType) {
                GameBoardMatrix.DW -> wordMultiplier *= 2 // Double Word
                GameBoardMatrix.TW -> wordMultiplier *= 3 // Triple Word
            }
        }

        // Final score with word multiplier
        val finalScore = playScore * wordMultiplier

        // Get current player scores
        val playerScores = gameRequest.gameData.playerScores.toMutableMap()
        val currentScore = playerScores[currentUserId] ?: 0
        playerScores[currentUserId] = currentScore + finalScore

        // Determine next player's turn
        val opponentId = if (gameRequest.senderId == currentUserId) gameRequest.receiverId else gameRequest.senderId

        // Update game data
        val updatedGameData = GameData(
            boardState = boardStateMap,
            playerTurn = opponentId, // Switch turn to opponent
            playerScores = playerScores,
            lastMove = LastMove(
                playerId = currentUserId,
                word = currentTurnLetters.entries.sortedBy { it.key.first * 100 + it.key.second }
                    .joinToString("") { it.value.toString() },
                points = finalScore,
                timestamp = System.currentTimeMillis()
            )
        )

        // Update Firestore
        db.collection("game_requests").document(gameId)
            .update(
                mapOf(
                    "gameData" to updatedGameData,
                    "lastUpdatedAt" to System.currentTimeMillis()
                )
            )
            .addOnSuccessListener {
                Toast.makeText(this, "Play submitted! Your score: +$finalScore", Toast.LENGTH_SHORT).show()

                // Draw new letters to fill the rack
                val newLetters = LetterDistribution.drawLetters(7 - playerLetters.size)
                playerLetters.addAll(newLetters)

                // Save updated player letters
                val playerLettersKey = "${gameId}_${currentUserId}_letters"
                getSharedPreferences("kilemilek_game", MODE_PRIVATE).edit()
                    .putString(playerLettersKey, playerLetters.joinToString(""))
                    .apply()

                // Clear current turn letters as they're now part of the board
                currentTurnLetters.clear()

                // Navigate back to the main activity
                finish()
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Error submitting play: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            // Handle the back button
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}