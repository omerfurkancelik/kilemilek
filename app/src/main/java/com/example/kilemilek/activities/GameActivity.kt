package com.example.kilemilek.activities

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipDescription
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
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
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.res.ResourcesCompat
import com.example.kilemilek.objects.LetterDistribution
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
    private lateinit var shuffleButton: Button

    private lateinit var gameFont: Typeface

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

    @RequiresApi(Build.VERSION_CODES.O)
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
        shuffleButton = findViewById(R.id.shuffle_button)


        shuffleButton.setOnClickListener {
            shuffleLetters()
        }

        gameFont = ResourcesCompat.getFont(this, R.font.opensans) ?: Typeface.DEFAULT

        // Then when creating letter tiles


        shuffleButton.tooltipText = "Shuffle Letters"
        withdrawButton.tooltipText = "Withdraw Letters"

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

        // Update Firebase with new letters
        updatePlayerLettersInFirebase()
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

        // Check if it's the player's turn or opponent's turn
        val isPlayerTurn = gameRequest.gameData.playerTurn == currentUserId

        // Create or find the opponent turn message TextView
        var opponentTurnMessage = findViewById<TextView>(R.id.opponent_turn_message)
        if (opponentTurnMessage == null) {
            // Create new TextView if it doesn't exist
            opponentTurnMessage = TextView(this).apply {
                id = R.id.opponent_turn_message
                layoutParams = ConstraintLayout.LayoutParams(
                    ConstraintLayout.LayoutParams.MATCH_PARENT,
                    ConstraintLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    // Position it between game board and letter rack
                    topToBottom = R.id.game_board_view
                    bottomToTop = R.id.letter_rack_card
                    startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                    endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                    topMargin = 8
                    bottomMargin = 8
                }
                gravity = android.view.Gravity.CENTER
                textSize = 18f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#FF9800")) // Orange color
                text = "OPPONENT'S TURN"
            }

            // Add to the constraint layout
            (findViewById<View>(R.id.game_board_view).parent as ViewGroup).addView(opponentTurnMessage)
        }

        // Update UI based on whose turn it is
        if (isPlayerTurn) {
            // Player's turn - enable play button and hide message
            playButton.isEnabled = true
            playButton.text = "Play Word"
            opponentTurnMessage.visibility = View.GONE
        } else {
            // Opponent's turn - disable play button and show message
            playButton.isEnabled = false
            playButton.text = "Wait for Opponent"
            opponentTurnMessage.visibility = View.VISIBLE
        }
    }

    private fun loadPlayerLetters() {
        // Check if player already has letters assigned in Firebase
        val letters = gameRequest.gameData.playerLetters[currentUserId]

        if (letters != null && letters.isNotEmpty()) {
            // Player already has letters assigned in Firebase
            playerLetters.clear()
            playerLetters.addAll(letters.map { it[0] }) // Convert strings back to chars
        } else {
            // Assign new letters
            playerLetters.clear()
            playerLetters.addAll(LetterDistribution.drawLetters(7))

            // Save assigned letters to Firebase
            updatePlayerLettersInFirebase()
        }

        // Update the letter rack UI
        updateLetterRackUI()
    }

    private fun updatePlayerLettersInFirebase() {
        // Create a map of all players' letters
        val updatedLettersMap = gameRequest.gameData.playerLetters.toMutableMap()
        // Update the current player's letters
        updatedLettersMap[currentUserId] = playerLetters.map { it.toString() }

        // Update Firestore
        db.collection("game_requests").document(gameId)
            .update("gameData.playerLetters", updatedLettersMap)
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Error saving letters: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
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

            tag = letter

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
                TypedValue.COMPLEX_UNIT_DIP, 45f, resources.displayMetrics
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

            textSize = 15f
            setTextColor(Color.parseColor("#212121")) // Dark gray for letter text
            textAlignment = View.TEXT_ALIGNMENT_CENTER

            // Add these lines to ensure consistent font rendering

            typeface = Typeface.DEFAULT_BOLD
            setTypeface(typeface, Typeface.BOLD)

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

        letterText.typeface = gameFont
        letterText.setTypeface(letterText.typeface, Typeface.BOLD)

        // Letter value
        val valueText = TextView(this).apply {
            text = LetterDistribution.getLetterValue(letter).toString()
            textSize = 10f
            setTextColor(Color.parseColor("#757575"))



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

    @SuppressLint("ClickableViewAccessibility")
    private fun setupBoardDragAndDrop() {
        gameBoardView.setOnDragListener { view, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED ->
                    event.clipDescription.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)

                DragEvent.ACTION_DRAG_ENTERED,
                DragEvent.ACTION_DRAG_LOCATION,
                DragEvent.ACTION_DRAG_EXITED ->
                    true

                DragEvent.ACTION_DROP -> {
                    // 1) Sıra sende değilse deneme amaçlı izin ver
                    val isPlayerTurn = gameRequest.gameData.playerTurn == currentUserId
                    if (!isPlayerTurn) {
                        Toast.makeText(
                            this,
                            "It's not your turn. You can only try placements.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    // 2) Drop pozisyonunu hesapla
                    val boardSize = GameBoardMatrix.BOARD_SIZE.toFloat()
                    val cellSize = gameBoardView.width / boardSize
                    val col = (event.x / cellSize).toInt()
                    val row = (event.y / cellSize).toInt()

                    // 3) Geçerli satır/sütun + harf verisi yoksa geri göster
                    if (row !in 0 until GameBoardMatrix.BOARD_SIZE ||
                        col !in 0 until GameBoardMatrix.BOARD_SIZE ||
                        currentDraggedLetter == null
                    ) {
                        currentDraggedView?.visibility = View.VISIBLE
                        return@setOnDragListener true
                    }

                    val pos = Pair(row, col)

                    // 4) Hücre doluysa geri göster
                    if (placedLetters.containsKey(pos) || currentTurnLetters.containsKey(pos)) {
                        currentDraggedView?.visibility = View.VISIBLE
                        return@setOnDragListener true
                    }

                    // 5) İlk hamle ve henüz bir harf yoksa: sadece merkez izinli
                    if (isFirstMoveInGame && currentTurnLetters.isEmpty()) {
                        val center = GameBoardMatrix.BOARD_SIZE / 2
                        if (row != center || col != center) {
                            currentDraggedView?.visibility = View.VISIBLE
                            return@setOnDragListener true
                        }
                    }
                    // 6) Diğer turlarda her drop anında komşu kontrolü yap
                    else if (!isFirstMoveInGame && !isAdjacentToExistingLetter(row, col)) {
                        Toast.makeText(
                            this,
                            "Letters must be adjacent to existing letters",
                            Toast.LENGTH_SHORT
                        ).show()
                        currentDraggedView?.visibility = View.VISIBLE
                        return@setOnDragListener true
                    }

                    // 7) Drop’u kabul et, harfi koy
                    gameBoardView.placeLetter(row, col, currentDraggedLetter!!)
                    currentTurnLetters[pos] = currentDraggedLetter!!

                    // 8) Sıra sende ve rack’ten gelen harfse rack’ten çıkar
                    if (isPlayerTurn && !dragSourceIsBoard) {
                        playerLetters.remove(currentDraggedLetter)
                        view.postDelayed({ updateLetterRackUI() }, 100)
                    }

                    true
                }

                DragEvent.ACTION_DRAG_ENDED -> {
                    // Başarısız drop’ta rack’e geri dön
                    if (!event.result) {
                        currentDraggedView?.visibility = View.VISIBLE
                    }
                    currentDraggedLetter = null
                    currentDraggedView = null
                    dragSourceIsBoard = false
                    dragSourcePosition = null
                    true
                }

                else -> false
            }
        }
    }

    private fun shuffleLetters() {
        playerLetters.shuffle()
        updateLetterRackUI()

        // Update Firebase with new order
        updatePlayerLettersInFirebase()
    }

    /**
     * Find a letter view at the specified coordinates in the letter rack
     */
    private fun findLetterViewAt(x: Float, y: Float): View? {
        // Convert coordinates to be relative to the letter rack
        val letterRackLocation = IntArray(2)
        letterRackLayout.getLocationOnScreen(letterRackLocation)

        val relativeX = x + letterRackLayout.scrollX

        // Check all letter tiles
        for (i in 0 until letterRackLayout.childCount) {
            val child = letterRackLayout.getChildAt(i)
            val childLocation = IntArray(2)
            child.getLocationOnScreen(childLocation)

            val childLeft = childLocation[0] - letterRackLocation[0]
            val childRight = childLeft + child.width

            // Check if point is within this view horizontally
            // (We're in a horizontal scroll view, so we only need to check X)
            if (relativeX >= childLeft && relativeX <= childRight) {
                return child
            }
        }

        return null
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

        // Update Firebase with updated letters
        updatePlayerLettersInFirebase()
    }




    /** Mevcut ya da önceki turlardaki harflerin birine komşu mu? */
    private fun isAdjacentToExistingLetter(row: Int, col: Int): Boolean {
        val adjacent = listOf(
            Pair(row - 1, col),
            Pair(row + 1, col),
            Pair(row, col - 1),
            Pair(row, col + 1)
        )
        return adjacent.any { placedLetters.containsKey(it) || currentTurnLetters.containsKey(it) }
    }




    /** "Oyna" butonuna basıldığında tüm kuralları bir arada denetler */
    private fun validateAndSubmitPlay() {
        if (currentTurnLetters.isEmpty()) {
            Toast.makeText(this, "No letters placed on the board", Toast.LENGTH_SHORT).show()
            return
        }
        if (!areLettersInStraightLine()) {
            Toast.makeText(this, "Letters must be placed in a straight line", Toast.LENGTH_SHORT).show()
            return
        }
        if (!areLettersContiguous()) {
            Toast.makeText(this, "Letters must be contiguous (no gaps)", Toast.LENGTH_SHORT).show()
            return
        }
        // İlk hamle: mutlaka bir harf merkezde olmalı
        if (isFirstMoveInGame) {
            val center = Pair(GameBoardMatrix.BOARD_SIZE / 2, GameBoardMatrix.BOARD_SIZE / 2)
            if (!currentTurnLetters.containsKey(center)) {
                Toast.makeText(
                    this,
                    "First move must include the center tile",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
        } else {
            // Sonraki hamleler: en az bir harf eski harflerle komşu olmalı
            val connects = currentTurnLetters.keys.any { (r, c) ->
                listOf(Pair(r - 1, c), Pair(r + 1, c), Pair(r, c - 1), Pair(r, c + 1))
                    .any { placedLetters.containsKey(it) }
            }
            if (!connects) {
                Toast.makeText(
                    this,
                    "New word must connect to existing letters",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
        }
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

        // Draw new letters to fill the rack
        val newLetters = LetterDistribution.drawLetters(7 - playerLetters.size)
        playerLetters.addAll(newLetters)

        // Create a map of all players' letters
        val updatedLettersMap = gameRequest.gameData.playerLetters.toMutableMap()
        // Update the current player's letters
        updatedLettersMap[currentUserId] = playerLetters.map { it.toString() }

        // Update game data
        val updatedGameData = GameData(
            boardState = boardStateMap,
            playerTurn = opponentId, // Switch turn to opponent
            playerScores = playerScores,
            playerLetters = updatedLettersMap,
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