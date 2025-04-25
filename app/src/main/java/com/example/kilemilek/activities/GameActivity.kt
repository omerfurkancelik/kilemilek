package com.example.kilemilek.activities

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipDescription
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
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
    private lateinit var timeRemainingTextView: TextView

    private lateinit var gameId: String
    private lateinit var gameRequest: GameRequestModel
    private lateinit var db: FirebaseFirestore
    private lateinit var currentUserId: String
    private lateinit var shuffleButton: Button

    private lateinit var gameFont: Typeface

    // Zaman takibi için değişkenler
    private var countDownTimer: CountDownTimer? = null
    private var gameTimeLimit: Long = 0
    private var gameTimeType: String = ""

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

        // Dinamik olarak time_remaining_text oluştur veya bul
        try {
            timeRemainingTextView = findViewById(R.id.time_remaining_text)
        } catch (e: Exception) {
            // Create dynamically if not found
            timeRemainingTextView = TextView(this).apply {
                id = View.generateViewId()
                textSize = 16f
                setTextColor(Color.parseColor("#E53935"))
                text = "Kalan Süre: --:--"
            }

            // Oyun bilgi kartına ekle
            val gameInfoCard = findViewById<CardView>(R.id.game_info_card)
            val contentLayout = gameInfoCard.getChildAt(0) as ViewGroup
            contentLayout.addView(timeRemainingTextView)
        }

        shuffleButton.setOnClickListener {
            shuffleLetters()
        }

        gameFont = ResourcesCompat.getFont(this, R.font.opensans) ?: Typeface.DEFAULT

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

                    // Get time limit information
                    gameTimeLimit = gameRequest.gameData.timeLimit
                    gameTimeType = gameRequest.gameData.timeType

                    // Initialize timer if needed
                    if (gameTimeLimit > 0) {
                        startCountdownTimer()
                    }

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

    private fun startCountdownTimer() {
        // Calculate remaining time
        val currentTime = System.currentTimeMillis()

        // Eğer moveDeadline varsa ve geçerliyse onu kullan, yoksa yeni oluştur
        val deadline = if (gameRequest.moveDeadline > currentTime) {
            gameRequest.moveDeadline
        } else {
            // Oyun türüne göre süre belirle
            currentTime + gameTimeLimit
        }

        val remainingTime = deadline - currentTime

        if (remainingTime <= 0) {
            // Time is already up
            handleTimeUp()
            return
        }

        // Start countdown
        countDownTimer?.cancel() // Önce mevcut sayacı iptal et
        countDownTimer = object : CountDownTimer(remainingTime, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                updateTimeRemainingText(millisUntilFinished)
            }

            override fun onFinish() {
                handleTimeUp()
            }
        }.start()
    }

    private fun updateTimeRemainingText(millisUntilFinished: Long) {
        // Format based on time type
        val displayText = when {
            gameTimeType.startsWith("QUICK") -> {
                // Short format for minutes and seconds
                val minutes = millisUntilFinished / (60 * 1000)
                val seconds = (millisUntilFinished % (60 * 1000)) / 1000
                String.format("Kalan Süre: %02d:%02d", minutes, seconds)
            }
            gameTimeType.startsWith("EXTENDED") -> {
                // Longer format for hours and minutes
                val hours = millisUntilFinished / (60 * 60 * 1000)
                val minutes = (millisUntilFinished % (60 * 60 * 1000)) / (60 * 1000)
                String.format("Kalan Süre: %02d saat %02d dk.", hours, minutes)
            }
            else -> {
                "Kalan Süre: --:--"
            }
        }

        timeRemainingTextView.text = displayText
    }

    private fun handleTimeUp() {
        // Show time up message
        timeRemainingTextView.text = "Süre doldu!"

        // Eğer bu kullanıcının sırası ise, otomatik olarak oyunu kaybet
        if (gameRequest.gameData.playerTurn == currentUserId) {
            // Oyun durumunu güncelle
            val opponentId = if (gameRequest.senderId == currentUserId)
                gameRequest.receiverId
            else
                gameRequest.senderId

            // Rakibin puanına bir bonus ekle
            val playerScores = gameRequest.gameData.playerScores.toMutableMap()
            val opponentScore = playerScores[opponentId] ?: 0
            playerScores[opponentId] = opponentScore + 15 // 15 puanlık bonus

            db.collection("game_requests").document(gameId)
                .update(
                    mapOf(
                        "status" to "completed",
                        "gameData.playerScores" to playerScores,
                        "winnerId" to opponentId,
                        "lastUpdatedAt" to System.currentTimeMillis()
                    )
                )
                .addOnSuccessListener {
                    Toast.makeText(this, "Süre doldu! Oyunu kaybettiniz.", Toast.LENGTH_LONG).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    Log.e("GameActivity", "Error updating game after timeout: ${e.message}")
                    Toast.makeText(this, "Oyunu sonlandırırken hata: ${e.message}",
                        Toast.LENGTH_SHORT).show()
                }
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

        // Display time info if available
        if (gameTimeLimit > 0) {
            val gameType = when (gameTimeType) {
                "QUICK_2MIN" -> "Fast Game (2 min)"
                "QUICK_5MIN" -> "Fast Game (5 min)"
                "EXTENDED_12HOUR" -> "Extended Game (12 hours)"
                "EXTENDED_24HOUR" -> "Extended Game (24 hours)"
                else -> ""
            }

            // Ensure time remaining textview is visible
            timeRemainingTextView.visibility = View.VISIBLE

            // Game type bilgisini göster
            val gameTypeTextView = TextView(this).apply {
                textSize = 14f
                setTextColor(Color.parseColor("#E53935"))
                text = gameType
                visibility = View.VISIBLE
            }

            // Ekle veya bilgiyi güncelle
            val gameInfoCard = findViewById<CardView>(R.id.game_info_card)
            val contentLayout = gameInfoCard.getChildAt(0) as ViewGroup
            if (contentLayout.childCount > 3) {
                // Game type text view zaten eklenmiş olabilir
                (contentLayout.getChildAt(3) as? TextView)?.text = gameType
            } else {
                // Yeni ekle
                contentLayout.addView(gameTypeTextView, 3)
            }
        }

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
                                        Toast.makeText(this, "First move must be placed on the center square", Toast.LENGTH_SHORT).show()

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
                                }
                                // If it's not the first move and no letters have been placed in this turn
                                else if (!isFirstMoveInGame && currentTurnLetters.isEmpty()) {
                                    // Check if the new letter intersects with or is adjacent to an existing word
                                    if (!isAdjacentOrCrossesExistingWord(row, col)) {
                                        Toast.makeText(this, "Letter must be adjacent to or intersect with an existing word", Toast.LENGTH_SHORT).show()

                                        // Return the letter to the rack
                                        if (!dragSourceIsBoard) {
                                            currentDraggedView?.visibility = View.VISIBLE
                                        } else {
                                            // If dragging from the board, put it back
                                            dragSourcePosition?.let { (sourceRow, sourceCol) ->
                                                gameBoardView.placeLetter(sourceRow, sourceCol, currentDraggedLetter!!)
                                                currentTurnLetters[Pair(sourceRow, sourceCol)] = currentDraggedLetter!!
                                            }
                                        }
                                        return@setOnDragListener true
                                    }
                                }
                                // If letters have already been placed in this turn
                                else if (!isFirstMoveInGame && currentTurnLetters.isNotEmpty()) {
                                    // Check if the letters are arranged properly
                                    if (!isAdjacentToExistingLetter(row, col)) {
                                        Toast.makeText(this, "Letters must be placed in a straight line with no gaps", Toast.LENGTH_SHORT).show()

                                        // Return the letter to the rack
                                        if (!dragSourceIsBoard) {
                                            currentDraggedView?.visibility = View.VISIBLE
                                        } else {
                                            // If dragging from the board, put it back
                                            dragSourcePosition?.let { (sourceRow, sourceCol) ->
                                                gameBoardView.placeLetter(sourceRow, sourceCol, currentDraggedLetter!!)
                                                currentTurnLetters[Pair(sourceRow, sourceCol)] = currentDraggedLetter!!
                                            }
                                        }
                                        return@setOnDragListener true
                                    }
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
                        // If drag ended without drop on a valid target
                        if (!event.result) {
                            if (!dragSourceIsBoard) {
                                // Letter was from rack, make it visible again
                                currentDraggedView?.visibility = View.VISIBLE
                            } else {
                                // Letter was from board and dropped outside valid targets
                                // Remove from board first
                                dragSourcePosition?.let { (sourceRow, sourceCol) ->
                                    gameBoardView.clearLetter(sourceRow, sourceCol)
                                    currentTurnLetters.remove(Pair(sourceRow, sourceCol))
                                }

                                // Then add to player's rack
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
                        // If dropping on another letter in the rack (for shuffling)
                        val targetView = findLetterViewAt(event.x, event.y)
                        if (targetView != null && targetView != currentDraggedView && !dragSourceIsBoard) {
                            // Get the target letter
                            val targetLetter = targetView.tag as? Char

                            if (targetLetter != null && currentDraggedLetter != null) {
                                // This is a shuffle operation - swap the two letters
                                val sourceLetter = currentDraggedLetter!!

                                // Find positions in playerLetters
                                val sourceIndex = playerLetters.indexOf(sourceLetter)
                                val targetIndex = playerLetters.indexOf(targetLetter)

                                if (sourceIndex >= 0 && targetIndex >= 0) {
                                    // Swap the letters
                                    playerLetters[sourceIndex] = targetLetter
                                    playerLetters[targetIndex] = sourceLetter

                                    // Update UI
                                    updateLetterRackUI()

                                    // Update Firebase
                                    updatePlayerLettersInFirebase()
                                }
                            }
                        } else {
                            // Normal drop on rack (returning letter)
                            if (currentDraggedLetter != null) {
                                // If from board, remove from current turn letters
                                if (dragSourceIsBoard && dragSourcePosition != null) {
                                    currentTurnLetters.remove(dragSourcePosition)
                                    gameBoardView.clearLetter(dragSourcePosition!!.first, dragSourcePosition!!.second)

                                    // Add to player rack only if it came from the board
                                    playerLetters.add(currentDraggedLetter!!)

                                    // Update Firebase with the changed letters
                                    updatePlayerLettersInFirebase()
                                } else {
                                    // If the letter was already in the rack, don't add it again
                                    // Just make it visible again
                                    currentDraggedView?.visibility = View.VISIBLE
                                }

                                view.post {
                                    updateLetterRackUI()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("GameActivity", "Error handling drop on letter rack: ${e.message}")
                        e.printStackTrace()

                        // Make sure letter is visible again if there was an error
                        if (!dragSourceIsBoard) {
                            currentDraggedView?.visibility = View.VISIBLE
                        }
                    }
                    true
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    try {
                        // If drag ended without drop on a valid target
                        if (!event.result) {
                            if (!dragSourceIsBoard) {
                                // Letter was from rack, make it visible again
                                currentDraggedView?.visibility = View.VISIBLE
                            } else {
                                // Letter was from board and dropped outside valid targets
                                // Remove from board first
                                dragSourcePosition?.let { (sourceRow, sourceCol) ->
                                    gameBoardView.clearLetter(sourceRow, sourceCol)
                                    currentTurnLetters.remove(Pair(sourceRow, sourceCol))
                                }

                                // Then add to player's rack
                                currentDraggedLetter?.let {
                                    playerLetters.add(it)

                                    // Update Firebase with the changed letters
                                    updatePlayerLettersInFirebase()

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
                else -> true
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


    private fun isAdjacentOrCrossesExistingWord(row: Int, col: Int): Boolean {
        // Check if the letter intersects with an existing letter (for crosswords)
        if (placedLetters.containsKey(Pair(row, col))) {
            return true
        }

        // Check adjacent positions - this already works well
        val adjacentPositions = listOf(
            Pair(row - 1, col), // Above
            Pair(row + 1, col), // Below
            Pair(row, col - 1), // Left
            Pair(row, col + 1)  // Right
        )

        // Check if the letter is adjacent to an existing letter
        for (pos in adjacentPositions) {
            if (placedLetters.containsKey(pos)) {
                return true
            }
        }

        return false
    }

    private fun isAdjacentToExistingLetter(row: Int, col: Int): Boolean {
        // İlk hamle kontrolü - Tahtanın merkezinden başlamalı
        if (isFirstMoveInGame) {
            val centerRow = GameBoardMatrix.BOARD_SIZE / 2
            val centerCol = GameBoardMatrix.BOARD_SIZE / 2

            // İlk harf merkeze yerleştirilmeli
            if (currentTurnLetters.isEmpty()) {
                return row == centerRow && col == centerCol
            } else {
                // İlk hamledeki diğer harfler, merkeze ya da ilk harfe bağlı olmalı ve
                // hepsi aynı yönde (yatay veya dikey) olmalı
                if (!currentTurnLetters.containsKey(Pair(centerRow, centerCol))) {
                    return false // İlk hamle merkezi içermeli
                }

                // İlk hamledeki tüm harflerin aynı yönde olduğundan emin ol
                val rows = currentTurnLetters.keys.map { it.first }.toSet()
                val cols = currentTurnLetters.keys.map { it.second }.toSet()

                // Yatay diziliş
                if (rows.size == 1) {
                    // Yeni harf de aynı satırda olmalı
                    if (row != rows.first()) {
                        return false
                    }

                    // Sütun aralığını kontrol et - bitişik olmalı
                    val minCol = cols.minOrNull()!!
                    val maxCol = cols.maxOrNull()!!

                    return col == minCol - 1 || col == maxCol + 1 || (col > minCol && col < maxCol)
                }

                // Dikey diziliş
                if (cols.size == 1) {
                    // Yeni harf de aynı sütunda olmalı
                    if (col != cols.first()) {
                        return false
                    }

                    // Satır aralığını kontrol et - bitişik olmalı
                    val minRow = rows.minOrNull()!!
                    val maxRow = rows.maxOrNull()!!

                    return row == minRow - 1 || row == maxRow + 1 || (row > minRow && row < maxRow)
                }

                // Hem satır hem sütun değişiyorsa geçersiz hamle
                return false
            }
        }

        // Sonraki hamleler için - tahtadaki en az bir harfe bitişik olmalı
        if (currentTurnLetters.isEmpty()) {
            // Tahtadaki herhangi bir harfe bitişik mi kontrol et
            val adjacentPositions = listOf(
                Pair(row - 1, col), // Yukarı
                Pair(row + 1, col), // Aşağı
                Pair(row, col - 1), // Sol
                Pair(row, col + 1)  // Sağ
            )

            return adjacentPositions.any { placedLetters.containsKey(it) }
        }

        // Mevcut turdaki harfler için hamle yönünü belirle
        val rows = currentTurnLetters.keys.map { it.first }.toSet()
        val cols = currentTurnLetters.keys.map { it.second }.toSet()

        // Eğer bir harf yerleştirilmişse, yön belirlenmiş demektir
        if (currentTurnLetters.size == 1) {
            val existingPos = currentTurnLetters.keys.first()

            // Yeni harf ya aynı satırda (yatay) ya da aynı sütunda (dikey) olmalı
            if (row == existingPos.first) {
                // Yatay hareket - bitişik mi?
                return col == existingPos.second - 1 || col == existingPos.second + 1
            } else if (col == existingPos.second) {
                // Dikey hareket - bitişik mi?
                return row == existingPos.first - 1 || row == existingPos.first + 1
            }

            // Ne yatay ne dikey
            return false
        }

        // Birden fazla harf yerleştirilmişse
        // Tüm harfler ya aynı satırda ya da aynı sütunda olmalı
        val allInSameRow = rows.size == 1
        val allInSameCol = cols.size == 1

        if (!allInSameRow && !allInSameCol) {
            // Harfler hem satır hem sütun değiştiriyorsa geçersiz hamle
            return false
        }

        // Yatay kelime oluşturma
        if (allInSameRow) {
            val fixedRow = rows.first()

            // Yeni harf aynı satırda olmalı
            if (row != fixedRow) {
                return false
            }

            // Sütun aralığını kontrol et
            val minCol = cols.minOrNull()!!
            val maxCol = cols.maxOrNull()!!

            // Başa veya sona ekleme
            if (col == minCol - 1 || col == maxCol + 1) {
                return true
            }

            // Ortadaki boşluğa ekleme
            if (col > minCol && col < maxCol) {
                // Boşluk kontrolü - arada boşluk olmamalı
                for (c in minCol..maxCol) {
                    if (c != col && !currentTurnLetters.containsKey(Pair(row, c)) &&
                        !placedLetters.containsKey(Pair(row, c))) {
                        return false // Boşluk var
                    }
                }
                return true
            }

            return false
        }

        // Dikey kelime oluşturma
        if (allInSameCol) {
            val fixedCol = cols.first()

            // Yeni harf aynı sütunda olmalı
            if (col != fixedCol) {
                return false
            }

            // Satır aralığını kontrol et
            val minRow = rows.minOrNull()!!
            val maxRow = rows.maxOrNull()!!

            // Başa veya sona ekleme
            if (row == minRow - 1 || row == maxRow + 1) {
                return true
            }

            // Ortadaki boşluğa ekleme
            if (row > minRow && row < maxRow) {
                // Boşluk kontrolü - arada boşluk olmamalı
                for (r in minRow..maxRow) {
                    if (r != row && !currentTurnLetters.containsKey(Pair(r, col)) &&
                        !placedLetters.containsKey(Pair(r, col))) {
                        return false // Boşluk var
                    }
                }
                return true
            }

            return false
        }

        return false
    }

    // Harflerin mevcut bir harfe bağlanıp bağlanmadığını kontrol et
    private fun connectsToExistingLetter(): Boolean {
        // İlk hamleyse merkezi kontrol et
        if (isFirstMoveInGame) {
            val centerRow = GameBoardMatrix.BOARD_SIZE / 2
            val centerCol = GameBoardMatrix.BOARD_SIZE / 2
            return currentTurnLetters.containsKey(Pair(centerRow, centerCol))
        }

        // Diğer hamlelerde tahtadaki harflere bitişik olmalı
        for (pos in currentTurnLetters.keys) {
            val adjacentPositions = listOf(
                Pair(pos.first - 1, pos.second), // Yukarı
                Pair(pos.first + 1, pos.second), // Aşağı
                Pair(pos.first, pos.second - 1), // Sol
                Pair(pos.first, pos.second + 1)  // Sağ
            )

            if (adjacentPositions.any { placedLetters.containsKey(it) }) {
                return true
            }
        }
        return false
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

        // İlk hamle kontrolü
        if (isFirstMoveInGame) {
            val centerRow = GameBoardMatrix.BOARD_SIZE / 2
            val centerCol = GameBoardMatrix.BOARD_SIZE / 2

            if (!currentTurnLetters.containsKey(Pair(centerRow, centerCol))) {
                Toast.makeText(this, "İlk hamle merkez kareden geçmelidir", Toast.LENGTH_SHORT).show()
                return
            }
        } else {
            // Sonraki hamlelerde en az bir mevcut harfe bağlanmalı
            if (!connectsToExistingLetter()) {
                Toast.makeText(this, "Yeni harfler tahtadaki en az bir mevcut harfe bağlanmalıdır", Toast.LENGTH_SHORT).show()
                return
            }
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
        if (currentTurnLetters.size <= 1) return true

        val allLetters = currentTurnLetters.keys + placedLetters.keys

        val rows = currentTurnLetters.keys.map { it.first }.toSet()
        val cols = currentTurnLetters.keys.map { it.second }.toSet()

        if (rows.size == 1) {
            val row = rows.first()
            val allCols = allLetters.filter { it.first == row }.map { it.second }
            val minCol = allCols.minOrNull()!!
            val maxCol = allCols.maxOrNull()!!

            for (c in minCol..maxCol) {
                val pos = Pair(row, c)
                if (!currentTurnLetters.containsKey(pos) && !placedLetters.containsKey(pos)) {
                    return false
                }
            }
            return true
        }

        if (cols.size == 1) {
            val col = cols.first()
            val allRows = allLetters.filter { it.second == col }.map { it.first }
            val minRow = allRows.minOrNull()!!
            val maxRow = allRows.maxOrNull()!!

            for (r in minRow..maxRow) {
                val pos = Pair(r, col)
                if (!currentTurnLetters.containsKey(pos) && !placedLetters.containsKey(pos)) {
                    return false
                }
            }
            return true
        }

        return false
    }



    private fun submitPlay() {
        // Cancel the countdown timer when submitting a move
        countDownTimer?.cancel()

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

        // Şu anki zaman
        val currentTime = System.currentTimeMillis()

        // Bir sonraki hamle için son tarih hesaplama
        val newMoveDeadline = currentTime + gameTimeLimit

        // Update game data - preserve time limit settings
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
                timestamp = currentTime
            ),
            timeLimit = gameTimeLimit,  // Preserve the original time limit
            timeType = gameTimeType     // Preserve the original time type
        )

        // Update Firestore
        db.collection("game_requests").document(gameId)
            .update(
                mapOf(
                    "gameData" to updatedGameData,
                    "lastUpdatedAt" to currentTime,
                    "moveDeadline" to newMoveDeadline
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

    override fun onDestroy() {
        super.onDestroy()
        // Cancel the countdown timer to avoid memory leaks
        countDownTimer?.cancel()
    }
}