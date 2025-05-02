package com.example.kilemilek.activities

import android.annotation.SuppressLint
import android.app.ProgressDialog.show
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
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.lifecycleScope
import com.example.kilemilek.objects.LetterDistribution
import com.example.kilemilek.R
import com.example.kilemilek.models.GameData
import com.example.kilemilek.models.GameRequestModel
import com.example.kilemilek.models.LastMove
import com.example.kilemilek.objects.GameBoardMatrix
import com.example.kilemilek.utils.BoardWordValidator
import com.example.kilemilek.utils.TurkishDictionary
import com.example.kilemilek.views.GameBoardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
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
    private lateinit var actionsButton: Button
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
    private val jokerPositions = mutableSetOf<Pair<Int, Int>>()

    // Game state flags
    private var isFirstMoveInGame = false

    private lateinit var turkishDictionary: TurkishDictionary
    private lateinit var boardValidator: BoardWordValidator
    private var isDictionaryLoaded = false


    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        // Enable back button in the action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Kilemilek"

        // Initialize views
        gameBoardView = findViewById(R.id.game_board_view)
        // Remove the gameIdTextView initialization
        // gameIdTextView = findViewById(R.id.game_id_text)
        opponentNameTextView = findViewById(R.id.opponent_name_text)
        yourScoreTextView = findViewById(R.id.your_score_text)
        opponentScoreTextView = findViewById(R.id.opponent_score_text)
        remainingLettersTextView = findViewById(R.id.remaining_letters_text)
        letterRackLayout = findViewById(R.id.letter_rack)
        playButton = findViewById(R.id.play_button)
        withdrawButton = findViewById(R.id.withdraw_button)
        shuffleButton = findViewById(R.id.shuffle_button)
        actionsButton = findViewById(R.id.actions_button)

        // Setup Actions button
        setupActionsButton()

        loadDictionary()

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

    private fun showResignConfirmation() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Resign Game")
            .setMessage("Are you sure you want to resign? This will end the game and you will lose.")
            .setPositiveButton("Resign") { _, _ ->
                resignGame()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPassConfirmation() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Pass Turn")
            .setMessage("Are you sure you want to pass your turn? If there are 3 consecutive passes, the game will end.")
            .setPositiveButton("Pass") { _, _ ->
                passTurn()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun resignGame() {
        // Cancel any active countdown timer
        countDownTimer?.cancel()

        // Get opponent ID
        val opponentId = if (gameRequest.senderId == currentUserId) {
            gameRequest.receiverId
        } else {
            gameRequest.senderId
        }

        // Update game status to completed, set winner to opponent
        db.collection("game_requests").document(gameId)
            .update(
                mapOf(
                    "status" to "completed",
                    "winnerId" to opponentId,
                    "lastUpdatedAt" to System.currentTimeMillis()
                )
            )
            .addOnSuccessListener {
                Toast.makeText(this, "You resigned. Game over.", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Error resigning: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun passTurn() {
        // Cancel any active countdown timer
        countDownTimer?.cancel()

        // Get opponent ID
        val opponentId = if (gameRequest.senderId == currentUserId) {
            gameRequest.receiverId
        } else {
            gameRequest.senderId
        }

        // Get current pass count from game data
        val passCount = gameRequest.gameData.passCount ?: 0
        val newPassCount = passCount + 1

        // Check if this is the third consecutive pass
        if (newPassCount >= 3) {
            endGameDueToConsecutivePasses()
            return
        }

        // Current time
        val currentTime = System.currentTimeMillis()

        // Calculate next move deadline
        val newMoveDeadline = currentTime + gameTimeLimit

        // Update game data with pass information
        val updatedGameData = gameRequest.gameData.copy(
            playerTurn = opponentId,
            passCount = newPassCount,
            lastMove = LastMove(
                playerId = currentUserId,
                word = "PASS",
                points = 0,
                timestamp = currentTime
            )
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
                Toast.makeText(this, "Turn passed to opponent", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Error passing turn: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun endGameDueToConsecutivePasses() {
        // Three consecutive passes - end the game
        // The player with higher score wins

        // Get player scores
        val yourScore = gameRequest.gameData.playerScores[currentUserId] ?: 0
        val opponentId = if (gameRequest.senderId == currentUserId) {
            gameRequest.receiverId
        } else {
            gameRequest.senderId
        }
        val opponentScore = gameRequest.gameData.playerScores[opponentId] ?: 0

        // Determine winner
        val winnerId = if (yourScore > opponentScore) {
            currentUserId
        } else if (opponentScore > yourScore) {
            opponentId
        } else {
            // It's a tie - no winner
            ""
        }

        // Update game status to completed
        db.collection("game_requests").document(gameId)
            .update(
                mapOf(
                    "status" to "completed",
                    "winnerId" to winnerId,
                    "lastUpdatedAt" to System.currentTimeMillis(),
                    "gameData.endReason" to "consecutive_passes"
                )
            )
            .addOnSuccessListener {
                val resultMessage = when {
                    winnerId == currentUserId -> "Game ended due to 3 consecutive passes. You won!"
                    winnerId == opponentId -> "Game ended due to 3 consecutive passes. You lost."
                    else -> "Game ended due to 3 consecutive passes. It's a tie!"
                }

                Toast.makeText(this, resultMessage, Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Error ending game: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showJokerLetterSelection(position: Pair<Int, Int>) {
        // Create a dialog to select a letter
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Select Letter for Joker")
            .setCancelable(false)
            .create()

        // Create the layout for letter selection
        val layout = LayoutInflater.from(this).inflate(R.layout.dialog_joker_selection, null)
        val letterGrid = layout.findViewById<GridLayout>(R.id.joker_letter_grid)

        // Turkish alphabet letters to choose from
        val turkishLetters = listOf(
            'A', 'B', 'C', 'Ç', 'D', 'E', 'F', 'G', 'Ğ', 'H', 'I', 'İ', 'J', 'K', 'L',
            'M', 'N', 'O', 'Ö', 'P', 'R', 'S', 'Ş', 'T', 'U', 'Ü', 'V', 'Y', 'Z'
        )

        // Add buttons for each letter
        for (letter in turkishLetters) {
            val button = Button(this).apply {
                text = letter.toString()
                layoutParams = GridLayout.LayoutParams().apply {
                    width = resources.getDimensionPixelSize(R.dimen.joker_button_width)
                    height = resources.getDimensionPixelSize(R.dimen.joker_button_height)
                    setMargins(4, 4, 4, 4)
                }
                setOnClickListener {
                    // Place the selected letter on the board (as a joker)
                    placeJokerLetter(position, letter)
                    dialog.dismiss()
                }
            }
            letterGrid.addView(button)
        }

        dialog.setView(layout)
        dialog.show()
    }

    private fun placeJokerLetter(position: Pair<Int, Int>, letter: Char) {
        // Place the selected letter as a joker (with 0 value)
        gameBoardView.placeLetter(position.first, position.second, letter)

        // Mark this as a joker in the current turn letters
        currentTurnLetters[position] = letter

        // Also store that this is a joker (for scoring)
        jokerPositions.add(position)

        // Maybe update UI or other game state as needed
        updateLetterRackUI()
    }



    private fun setupActionsButton() {
        actionsButton.setOnClickListener {
            val popupMenu = PopupMenu(this, actionsButton)

            // Inflate the menu resource
            popupMenu.menuInflater.inflate(R.menu.game_actions_menu, popupMenu.menu)

            // Set up click listener for menu items
            popupMenu.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_resign -> {
                        showResignConfirmation()
                        true
                    }
                    R.id.action_pass -> {
                        showPassConfirmation()
                        true
                    }
                    else -> false
                }
            }

            // Show the popup menu
            popupMenu.show()
        }
    }


    private fun loadDictionary() {
        // Show loading indicator


        // Launch coroutine to load dictionary
        lifecycleScope.launch {
            try {
                turkishDictionary = TurkishDictionary(applicationContext)
                val result = turkishDictionary.loadDictionary("turkish.txt")

                isDictionaryLoaded = result

                if (result) {
                    boardValidator = BoardWordValidator(turkishDictionary)
                    Log.d("GameActivity", "Dictionary loaded successfully with ${turkishDictionary.dictionarySize()} words")
                    Toast.makeText(
                        this@GameActivity,
                        "Dictionary loaded with ${turkishDictionary.dictionarySize()} words",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Log.e("GameActivity", "Failed to load dictionary")
                    Toast.makeText(
                        this@GameActivity,
                        "Failed to load dictionary. Word validation will be skipped.",
                        Toast.LENGTH_LONG
                    ).show()
                }

                // Hide loading dialog

            } catch (e: Exception) {
                Log.e("GameActivity", "Error loading dictionary", e)


                Toast.makeText(
                    this@GameActivity,
                    "Error loading dictionary: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
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

        // Update the letter rack UI (local only)
        updateLetterRackUI()

        // Note: Removed the Firebase update call here
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

                    // ✅ Sadece oyuncunun sırasıysa timer başlasın
                    if (gameTimeLimit > 0 && gameRequest.gameData.playerTurn == currentUserId) {
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
        // 🔒 Extra güvenlik: sırası kullanıcıda mı kontrol et
        if (gameRequest.gameData.playerTurn != currentUserId) {
            Log.d("GameActivity", "Not user's turn, countdown will not start.")
            return
        }

        val currentTime = System.currentTimeMillis()

        val deadline = if (gameRequest.moveDeadline > currentTime) {
            gameRequest.moveDeadline
        } else {
            currentTime + gameTimeLimit
        }

        val remainingTime = deadline - currentTime

        if (remainingTime <= 0) {
            handleTimeUp()
            return
        }

        countDownTimer?.cancel()
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
                String.format("Remaining Time: %02d:%02d", minutes, seconds)
            }
            gameTimeType.startsWith("EXTENDED") -> {
                // Longer format for hours and minutes
                val hours = millisUntilFinished / (60 * 60 * 1000)
                val minutes = (millisUntilFinished % (60 * 60 * 1000)) / (60 * 1000)
                String.format("Remaining Time: %02d saat %02d dk.", hours, minutes)
            }
            else -> {
                "Remaining Time: --:--"
            }
        }

        timeRemainingTextView.text = displayText
    }

    private fun handleTimeUp() {
        // Show time up message
        timeRemainingTextView.text = "Time is up!"

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
                    Toast.makeText(this, "Time's up! You've lost the game.", Toast.LENGTH_LONG).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    Log.e("GameActivity", "Error updating game after timeout: ${e.message}")
                    Toast.makeText(this, "Error while ending the game: ${e.message}",
                        Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun updateGameUI() {
        // Set game ID

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

            // Save assigned letters to Firebase (only on first load)
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
        // Create a red dot indicator view to show drop location
        val redDotIndicator = View(this).apply {
            setBackgroundResource(R.drawable.red_dot_indicator)
            layoutParams = ViewGroup.LayoutParams(24, 24)
            visibility = View.GONE
        }

        // Add the indicator to the root view
        val rootView = findViewById<ViewGroup>(android.R.id.content)
        rootView.addView(redDotIndicator)

        // Make board cells clickable for withdrawing letters
        gameBoardView.setOnCellTouchListener { row, col, letter ->
            try {
                // Only allow withdrawing letters that were placed in the current turn
                // Check if the cell contains a letter, if it's in currentTurnLetters, and NOT in placedLetters
                if (letter != null && currentTurnLetters.containsKey(Pair(row, col)) && !placedLetters.containsKey(Pair(row, col))) {
                    // This is a letter placed in current turn - withdraw it to the rack
                    withdrawLetterFromBoard(row, col)
                    true
                } else {
                    // Either no letter, or it's from a previous turn (in placedLetters)
                    if (letter != null && placedLetters.containsKey(Pair(row, col))) {
                        // This is a letter from a previous turn - show a message
                        Toast.makeText(
                            this,
                            "Cannot withdraw letters from previous turns",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
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
                DragEvent.ACTION_DRAG_LOCATION -> {
                    // Update the position of the red dot indicator as drag moves
                    val boardSize = GameBoardMatrix.BOARD_SIZE.toFloat()
                    val cellSize = gameBoardView.width / boardSize

                    val col = (event.x / cellSize).toInt()
                    val row = (event.y / cellSize).toInt()

                    if (row in 0 until GameBoardMatrix.BOARD_SIZE && col in 0 until GameBoardMatrix.BOARD_SIZE) {
                        // Calculate cell center position
                        val cellCenterX = gameBoardView.x + col * cellSize + cellSize / 2
                        val cellCenterY = gameBoardView.y + row * cellSize + cellSize / 2

                        // Position red dot at cell center
                        redDotIndicator.x = cellCenterX - redDotIndicator.layoutParams.width / 2
                        redDotIndicator.y = cellCenterY - redDotIndicator.layoutParams.height / 2

                        // Show indicator only if the cell is empty
                        val cellIsEmpty = !placedLetters.containsKey(Pair(row, col)) &&
                                !currentTurnLetters.containsKey(Pair(row, col))
                        redDotIndicator.visibility = if (cellIsEmpty) View.VISIBLE else View.GONE
                    }
                    true
                }
                DragEvent.ACTION_DROP -> {
                    // Hide indicator when letter is dropped
                    redDotIndicator.visibility = View.GONE

                    try {
                        val boardSize = GameBoardMatrix.BOARD_SIZE.toFloat()
                        val cellSize = gameBoardView.width / boardSize

                        val col = (event.x / cellSize).toInt()
                        val row = (event.y / cellSize).toInt()

                        // Check if the position is within board bounds and we have a valid letter
                        if (row !in 0 until GameBoardMatrix.BOARD_SIZE || col !in 0 until GameBoardMatrix.BOARD_SIZE || currentDraggedLetter == null) {
                            returnToRack()
                            return@setOnDragListener true
                        }

                        // Check if there's already a letter in this position (from current or previous turns)
                        if (placedLetters.containsKey(Pair(row, col)) || currentTurnLetters.containsKey(Pair(row, col))) {
                            Toast.makeText(this, "Cannot place a letter on top of another letter", Toast.LENGTH_SHORT).show()
                            returnToRack()
                            return@setOnDragListener true
                        }

                        if (currentDraggedLetter == '*') {
                            // This is a joker, show letter selection dialog
                            val position = Pair(row, col)

                            // First remove the joker from player's rack
                            val index = playerLetters.indexOf('*')
                            if (index >= 0) {
                                playerLetters.removeAt(index)
                                view.postDelayed({
                                    updateLetterRackUI()
                                }, 100)
                            }

                            // Show letter selection dialog
                            showJokerLetterSelection(position)
                            return@setOnDragListener true
                        }

                        // First move checks
                        if (isFirstMoveInGame) {
                            val centerRow = GameBoardMatrix.BOARD_SIZE / 2
                            val centerCol = GameBoardMatrix.BOARD_SIZE / 2

                            if (currentTurnLetters.isEmpty() && placedLetters.isEmpty()) {
                                if (row != centerRow || col != centerCol) {
                                    Toast.makeText(this, "You should put the first letter in the center", Toast.LENGTH_SHORT).show()
                                    returnToRack()
                                    return@setOnDragListener true
                                }
                            } else {
                                val tempTurn = currentTurnLetters.toMutableMap()
                                tempTurn[Pair(row, col)] = currentDraggedLetter!!

                                val rows = tempTurn.keys.map { it.first }.toSet()
                                val cols = tempTurn.keys.map { it.second }.toSet()

                                val sameRow = rows.size == 1
                                val sameCol = cols.size == 1

                                if (!sameRow && !sameCol) {
                                    Toast.makeText(this, "In the first move you can only move horizontally or vertically", Toast.LENGTH_SHORT).show()
                                    returnToRack()
                                    return@setOnDragListener true
                                }
                            }
                        } else {
                            if (currentTurnLetters.isEmpty()) {
                                val adjacentPositions = listOf(
                                    Pair(row - 1, col),
                                    Pair(row + 1, col),
                                    Pair(row, col - 1),
                                    Pair(row, col + 1)
                                )

                                val connects = adjacentPositions.any { placedLetters.containsKey(it) }
                                if (!connects) {
                                    Toast.makeText(this, "The new letter must be adjacent to an existing letter", Toast.LENGTH_SHORT).show()
                                    returnToRack()
                                    return@setOnDragListener true
                                }
                            } else {
                                if (!isAdjacentToExistingLetter(row, col)) {
                                    Toast.makeText(this, "The new letter must be in the correct orientation", Toast.LENGTH_SHORT).show()
                                    returnToRack()
                                    return@setOnDragListener true
                                }
                            }
                        }

                        // If we reached here, we can place the letter
                        val letterToPlace = currentDraggedLetter!!

                        gameBoardView.placeLetter(row, col, letterToPlace)
                        currentTurnLetters[Pair(row, col)] = letterToPlace

                        if (!dragSourceIsBoard) {
                            val index = playerLetters.indexOf(letterToPlace)
                            if (index >= 0) {
                                playerLetters.removeAt(index)
                                view.postDelayed({
                                    updateLetterRackUI()
                                }, 100)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("GameActivity", "Error in drop: ${e.message}")
                        returnToRack()
                    }

                    true
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    // Hide the indicator when drag operation ends
                    redDotIndicator.visibility = View.GONE

                    try {
                        if (!event.result) {
                            returnToRack()
                        }
                    } catch (e: Exception) {
                        Log.e("GameActivity", "Error in drag ended: ${e.message}")
                    }

                    currentDraggedLetter = null
                    currentDraggedView = null
                    dragSourceIsBoard = false
                    dragSourcePosition = null

                    true
                }
                DragEvent.ACTION_DRAG_EXITED -> {
                    // Hide indicator when drag leaves the board
                    redDotIndicator.visibility = View.GONE
                    true
                }
                else -> true
            }
        }

        // Set up drop on letter rack (for returning letters)
        letterRackLayout.setOnDragListener { view, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> true
                DragEvent.ACTION_DROP -> {
                    try {
                        val targetView = findLetterViewAt(event.x, event.y)
                        if (targetView != null && targetView != currentDraggedView && !dragSourceIsBoard) {
                            val targetLetter = targetView.tag as? Char

                            if (targetLetter != null && currentDraggedLetter != null) {
                                val sourceLetter = currentDraggedLetter!!

                                val sourceIndex = playerLetters.indexOf(sourceLetter)
                                val targetIndex = playerLetters.indexOf(targetLetter)

                                if (sourceIndex >= 0 && targetIndex >= 0) {
                                    playerLetters[sourceIndex] = targetLetter
                                    playerLetters[targetIndex] = sourceLetter

                                    updateLetterRackUI()
                                    // Note: Removed the Firebase update call here
                                }
                            }
                        } else {
                            if (currentDraggedLetter != null) {
                                if (dragSourceIsBoard && dragSourcePosition != null) {
                                    currentTurnLetters.remove(dragSourcePosition)
                                    gameBoardView.clearLetter(dragSourcePosition!!.first, dragSourcePosition!!.second)

                                    playerLetters.add(currentDraggedLetter!!)

                                    // Note: Removed the Firebase update call here
                                } else {
                                    currentDraggedView?.visibility = View.VISIBLE
                                }

                                view.post { updateLetterRackUI() }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("GameActivity", "Error handling drop on letter rack: ${e.message}")
                        returnToRack()
                    }
                    true
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    try {
                        if (!event.result) {
                            returnToRack()
                        }
                    } catch (e: Exception) {
                        Log.e("GameActivity", "Error in drag ended: ${e.message}")
                    }

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

        // Note: Removed the Firebase update call here
    }

    private fun returnToRack() {
        if (!dragSourceIsBoard) {
            currentDraggedView?.visibility = View.VISIBLE
        } else {
            dragSourcePosition?.let { (sourceRow, sourceCol) ->
                gameBoardView.placeLetter(sourceRow, sourceCol, currentDraggedLetter!!)
                currentTurnLetters[Pair(sourceRow, sourceCol)] = currentDraggedLetter!!
            }
        }
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

        // Update letter rack UI (local only)
        updateLetterRackUI()

        // Note: Removed the Firebase update call here
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
        if (isFirstMoveInGame) {
            val centerRow = GameBoardMatrix.BOARD_SIZE / 2
            val centerCol = GameBoardMatrix.BOARD_SIZE / 2

            if (currentTurnLetters.isEmpty()) {
                // İlk harf merkeze yerleştirilmeli
                return row == centerRow && col == centerCol
            }

            if (!currentTurnLetters.containsKey(Pair(centerRow, centerCol))) {
                return false
            }

            val rows = currentTurnLetters.keys.map { it.first }.toSet()
            val cols = currentTurnLetters.keys.map { it.second }.toSet()

            val sameRow = rows.size == 1
            val sameCol = cols.size == 1

            if (sameRow && !sameCol) {
                // Yatay diziliyoruz, satır sabit olmalı
                return row == centerRow
            } else if (!sameRow && sameCol) {
                // Dikey diziliyoruz, sütun sabit olmalı
                return col == centerCol
            } else if (sameRow && sameCol) {
                // Şu an sadece merkezdeyiz, ilk ekleme yapılacak
                return (row == centerRow && (col == centerCol - 1 || col == centerCol + 1)) ||
                        (col == centerCol && (row == centerRow - 1 || row == centerRow + 1))
            } else {
                return false
            }
        }

        // İlk hamle değilse: normal bitişiklik kontrolü
        if (currentTurnLetters.isEmpty()) {
            val adjacentPositions = listOf(
                Pair(row - 1, col),
                Pair(row + 1, col),
                Pair(row, col - 1),
                Pair(row, col + 1)
            )
            return adjacentPositions.any { placedLetters.containsKey(it) }
        }

        val rows = currentTurnLetters.keys.map { it.first }.toSet()
        val cols = currentTurnLetters.keys.map { it.second }.toSet()

        if (currentTurnLetters.size == 1) {
            val existingPos = currentTurnLetters.keys.first()
            if (row == existingPos.first) {
                return col == existingPos.second - 1 || col == existingPos.second + 1
            } else if (col == existingPos.second) {
                return row == existingPos.first - 1 || row == existingPos.first + 1
            }
            return false
        }

        val allInSameRow = rows.size == 1
        val allInSameCol = cols.size == 1

        if (!allInSameRow && !allInSameCol) {
            return false
        }

        if (allInSameRow) {
            val fixedRow = rows.first()
            if (row != fixedRow) {
                return false
            }
            val minCol = cols.minOrNull()!!
            val maxCol = cols.maxOrNull()!!
            return col == minCol - 1 || col == maxCol + 1
        }

        if (allInSameCol) {
            val fixedCol = cols.first()
            if (col != fixedCol) {
                return false
            }
            val minRow = rows.minOrNull()!!
            val maxRow = rows.maxOrNull()!!
            return row == minRow - 1 || row == maxRow + 1
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

        val centerRow = GameBoardMatrix.BOARD_SIZE / 2
        val centerCol = GameBoardMatrix.BOARD_SIZE / 2

        if (isFirstMoveInGame) {
            if (!currentTurnLetters.containsKey(Pair(centerRow, centerCol))) {
                Toast.makeText(this, "The first move must pass through the center square", Toast.LENGTH_SHORT).show()
                return
            }

            val firstMoveRows = currentTurnLetters.keys.map { it.first }.toSet()
            val firstMoveCols = currentTurnLetters.keys.map { it.second }.toSet()

            val isStraightRow = firstMoveRows.size == 1
            val isStraightCol = firstMoveCols.size == 1

            if (!isStraightRow && !isStraightCol) {
                Toast.makeText(this, "In the first move the word must be only horizontal or only vertical", Toast.LENGTH_SHORT).show()
                return
            }
        } else {
            if (!connectsToExistingLetter()) {
                Toast.makeText(this, "New letters must be connected to existing letters on the board", Toast.LENGTH_SHORT).show()
                return
            }
        }

        // If dictionary is loaded, validate the words
        if (isDictionaryLoaded) {
            // Create a map of board state for validation
            val boardStateMap = mutableMapOf<String, String>()

            // Add existing letters
            placedLetters.forEach { (position, letter) ->
                boardStateMap["${position.first},${position.second}"] = letter.toString()
            }

            // Add new letters
            currentTurnLetters.forEach { (position, letter) ->
                boardStateMap["${position.first},${position.second}"] = letter.toString()
            }

            // Get positions of new letters for main word extraction
            val newLetterPositions = currentTurnLetters.keys.toList()

            // Validate words on the board
            val (isValid, mainWord, invalidWords) = boardValidator.validateNewWord(boardStateMap, newLetterPositions)

            if (!isValid) {
                // Show dialog with invalid words
                val message = if (invalidWords.isEmpty()) {
                    "The word \"$mainWord\" is not a valid Turkish word."
                } else {
                    "Invalid Turkish words found: ${invalidWords.joinToString(", ")}"
                }

                MaterialAlertDialogBuilder(this)
                    .setTitle("Invalid Words")
                    .setMessage(message)
                    .setPositiveButton("Try Again") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()

                return
            }
        }

        // All validation passed, submit the play
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

        // Calculate score (with joker handling)
        val playScore = currentTurnLetters.entries.sumOf { (position, letter) ->
            val tileType = GameBoardMatrix.getTileType(position.first, position.second)

            // Check if this letter is a joker (worth 0 points)
            var letterValue = if (jokerPositions.contains(position)) {
                0 // Jokers are worth 0 points
            } else {
                LetterDistribution.getLetterValue(letter)
            }

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

        // Current time
        val currentTime = System.currentTimeMillis()

        // Calculate next move deadline
        val newMoveDeadline = currentTime + gameTimeLimit

        // Reset the pass count since a word was played
        val passCount = 0

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
            timeType = gameTimeType,    // Preserve the original time type
            passCount = passCount       // Reset pass count since a word was played
        )

        // Update Firestore - THIS IS THE ONLY PLACE WE UPDATE FIREBASE
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

                // Clear joker positions
                jokerPositions.clear()

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