package com.example.kilemilek.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.example.kilemilek.R
import com.example.kilemilek.models.BoardPowerup
import com.example.kilemilek.models.MinePowerupType
import com.example.kilemilek.objects.GameBoardMatrix
import kotlin.math.floor

class GameBoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Board dimensions
    private val boardSize = GameBoardMatrix.BOARD_SIZE
    private var cellSize = 0f
    private var boardStartX = 0f
    private var boardStartY = 0f

    private val minePositions = mutableSetOf<Pair<Int, Int>>()
    private val rewardPositions = mutableSetOf<Pair<Int, Int>>()
    private val mineTypes = mutableMapOf<Pair<Int, Int>, MinePowerupType>()
    private var showPowerups = false

    private val mineIcons = mutableMapOf<MinePowerupType, Drawable?>()

    // Data structures to hold letters
    private val boardLetters = Array(boardSize) { Array<Char?>(boardSize) { null } }
    private val currentTurnLetters = mutableMapOf<Pair<Int, Int>, Char>()

    // Callback for cell touch events
    private var onCellTouchListener: ((Int, Int, Char?) -> Boolean)? = null

    // Paints for different board elements
    private val boardPaint = Paint().apply {
        style = Paint.Style.FILL
    }

    private val linePaint = Paint().apply {
        color = Color.parseColor("#CCCCCC")
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    private val minePaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#80E53935") // Semi-transparent red
    }

    private val rewardPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#809C27B0") // Semi-transparent purple
    }

    private val textPaint = Paint().apply {
        color = Color.BLACK
        textAlign = Paint.Align.CENTER
        textSize = 24f
    }

    private val smallTextPaint = Paint().apply {
        color = Color.BLACK
        textAlign = Paint.Align.CENTER
        textSize = 12f
    }

    fun initializeIcons() {
        mineIcons[MinePowerupType.SCORE_SPLIT] = ContextCompat.getDrawable(context, R.drawable.ic_score_split)
        mineIcons[MinePowerupType.POINT_TRANSFER] = ContextCompat.getDrawable(context, R.drawable.ic_point_transfer)
        mineIcons[MinePowerupType.LETTER_LOSS] = ContextCompat.getDrawable(context, R.drawable.ic_letter_loss)
        mineIcons[MinePowerupType.EXTRA_MOVE_BARRIER] = ContextCompat.getDrawable(context, R.drawable.ic_barrier)
        mineIcons[MinePowerupType.WORD_CANCELLATION] = ContextCompat.getDrawable(context, R.drawable.ic_cancel)
        mineIcons[MinePowerupType.REGION_BAN] = ContextCompat.getDrawable(context, R.drawable.ic_region_ban)
        mineIcons[MinePowerupType.LETTER_BAN] = ContextCompat.getDrawable(context, R.drawable.ic_letter_ban)
        mineIcons[MinePowerupType.EXTRA_MOVE] = ContextCompat.getDrawable(context, R.drawable.ic_extra_move)
    }


    // Colors for letter tiles
    private val currentTurnLetterColor = Color.parseColor("#FFF59D") // Yellow for current turn
    private val previousTurnLetterColor = Color.parseColor("#BDBDBD") // Gray for previous turns

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        // Calculate the size of each cell based on the view dimensions
        val minDimension = minOf(w, h)
        cellSize = minDimension / boardSize.toFloat()

        // Center the board in the view
        boardStartX = (w - boardSize * cellSize) / 2
        boardStartY = (h - boardSize * cellSize) / 2

        // Adjust text size based on cell size
        textPaint.textSize = cellSize * 0.5f
        smallTextPaint.textSize = cellSize * 0.25f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw the board tiles
        drawBoardTiles(canvas)

        // Draw mines and rewards if visible
        if (showPowerups) {
            drawMinesAndRewards(canvas)
        }

        // Draw grid lines
        drawGridLines(canvas)

        // Draw letters
        drawLetters(canvas)
    }

    private fun drawMinesAndRewards(canvas: Canvas) {
        // Draw mines (red indicators)
        for (position in minePositions) {
            val row = position.first
            val col = position.second

            val left = boardStartX + col * cellSize
            val top = boardStartY + row * cellSize
            val right = left + cellSize
            val bottom = top + cellSize

            // Draw mine background
            canvas.drawRect(left, top, right, bottom, minePaint)

            // Draw mine icon if available
            val mineType = mineTypes[position] ?: continue
            val icon = mineIcons[mineType] ?: continue

            // Set icon bounds
            val padding = cellSize * 0.2f
            icon.setBounds(
                (left + padding).toInt(),
                (top + padding).toInt(),
                (right - padding).toInt(),
                (bottom - padding).toInt()
            )

            // Draw the icon
            icon.draw(canvas)
        }

        // Draw rewards (purple indicators)
        for (position in rewardPositions) {
            val row = position.first
            val col = position.second

            val left = boardStartX + col * cellSize
            val top = boardStartY + row * cellSize
            val right = left + cellSize
            val bottom = top + cellSize

            // Draw reward background
            canvas.drawRect(left, top, right, bottom, rewardPaint)

            // Draw reward icon if available
            val rewardType = mineTypes[position] ?: continue
            val icon = mineIcons[rewardType] ?: continue

            // Set icon bounds
            val padding = cellSize * 0.2f
            icon.setBounds(
                (left + padding).toInt(),
                (top + padding).toInt(),
                (right - padding).toInt(),
                (bottom - padding).toInt()
            )

            // Draw the icon
            icon.draw(canvas)
        }
    }

    fun togglePowerupVisibility(show: Boolean) {
        showPowerups = show
        invalidate()
    }


    fun setMinesAndRewards(powerups: List<BoardPowerup>) {
        minePositions.clear()
        rewardPositions.clear()
        mineTypes.clear()

        for (powerup in powerups) {
            val position = powerup.position

            // Classify as mine or reward
            when (powerup.type) {
                MinePowerupType.SCORE_SPLIT,
                MinePowerupType.POINT_TRANSFER,
                MinePowerupType.LETTER_LOSS,
                MinePowerupType.EXTRA_MOVE_BARRIER,
                MinePowerupType.WORD_CANCELLATION -> {
                    minePositions.add(position)
                }

                MinePowerupType.REGION_BAN,
                MinePowerupType.LETTER_BAN,
                MinePowerupType.EXTRA_MOVE -> {
                    rewardPositions.add(position)
                }
            }

            // Store type for icon display
            mineTypes[position] = powerup.type
        }

        invalidate()
    }

    private fun drawBoardTiles(canvas: Canvas) {
        // Draw each tile with its appropriate color and text
        for (row in 0 until boardSize) {
            for (col in 0 until boardSize) {
                val left = boardStartX + col * cellSize
                val top = boardStartY + row * cellSize
                val right = left + cellSize
                val bottom = top + cellSize

                val tileType = GameBoardMatrix.getTileType(row, col)

                // Draw tile background
                boardPaint.color = GameBoardMatrix.getTileColor(tileType)
                canvas.drawRect(left, top, right, bottom, boardPaint)

                // Draw tile text (like H², K³, etc.) if it's a special tile
                if (tileType != GameBoardMatrix.NORMAL) {
                    val tileText = GameBoardMatrix.getTileText(tileType)
                    canvas.drawText(
                        tileText,
                        left + cellSize / 2,
                        top + cellSize / 2 + smallTextPaint.textSize / 3,
                        smallTextPaint
                    )
                }
            }
        }
    }

    private fun drawGridLines(canvas: Canvas) {
        // Draw horizontal grid lines
        for (i in 0..boardSize) {
            val y = boardStartY + i * cellSize
            canvas.drawLine(
                boardStartX,
                y,
                boardStartX + boardSize * cellSize,
                y,
                linePaint
            )
        }

        // Draw vertical grid lines
        for (i in 0..boardSize) {
            val x = boardStartX + i * cellSize
            canvas.drawLine(
                x,
                boardStartY,
                x,
                boardStartY + boardSize * cellSize,
                linePaint
            )
        }
    }

    private fun drawLetters(canvas: Canvas) {
        for (row in 0 until boardSize) {
            for (col in 0 until boardSize) {
                val letter = boardLetters[row][col] ?: continue

                val left = boardStartX + col * cellSize
                val top = boardStartY + row * cellSize
                val right = left + cellSize
                val bottom = top + cellSize

                // Draw letter tile background
                val position = Pair(row, col)
                val isCurrentTurn = currentTurnLetters.containsKey(position)
                boardPaint.color = if (isCurrentTurn) currentTurnLetterColor else previousTurnLetterColor

                val padding = cellSize * 0.1f
                val tileRect = RectF(
                    left + padding,
                    top + padding,
                    right - padding,
                    bottom - padding
                )
                canvas.drawRect(tileRect, boardPaint)

                // Draw the letter with improved font rendering
                textPaint.color = Color.BLACK
                textPaint.typeface = Typeface.DEFAULT_BOLD  // Use default bold font

                // Ensure text rendering uses proper alignment
                canvas.drawText(
                    letter.toString(),
                    left + cellSize / 2,
                    top + cellSize / 2 + textPaint.textSize / 3, // Adjust for vertical centering
                    textPaint
                )
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val col = floor((event.x - boardStartX) / cellSize).toInt()
            val row = floor((event.y - boardStartY) / cellSize).toInt()

            // Check if touch is within the board bounds
            if (row in 0 until boardSize && col in 0 until boardSize) {
                // Get the letter at this position
                val letter = boardLetters[row][col]

                // Notify the listener and allow them to handle the event
                if (onCellTouchListener?.invoke(row, col, letter) == true) {
                    return true
                }
            }
        }

        return super.onTouchEvent(event)
    }

    // Set a letter on the board
    fun placeLetter(row: Int, col: Int, letter: Char) {
        if (row in 0 until boardSize && col in 0 until boardSize) {
            boardLetters[row][col] = letter
            // Only mark as current turn if it's not already on the board
            // from a previous turn
            if (!currentTurnLetters.containsKey(Pair(row, col))) {
                currentTurnLetters[Pair(row, col)] = letter
            }
            invalidate()
        }
    }

    // Clear a letter from the board
    fun clearLetter(row: Int, col: Int) {
        if (row in 0 until boardSize && col in 0 until boardSize) {
            boardLetters[row][col] = null
            currentTurnLetters.remove(Pair(row, col))
            invalidate()
        }
    }

    // Set callback for cell touch events
    fun setOnCellTouchListener(listener: (Int, Int, Char?) -> Boolean) {
        onCellTouchListener = listener
    }

    // Get all letters placed in the current turn
    fun getCurrentTurnLetters(): Map<Pair<Int, Int>, Char> {
        return currentTurnLetters.toMap()
    }

    // Get the cell coordinates for a point on the board
    fun getCellCoordinates(x: Float, y: Float): Pair<Int, Int>? {
        val col = floor((x - boardStartX) / cellSize).toInt()
        val row = floor((y - boardStartY) / cellSize).toInt()

        return if (row in 0 until boardSize && col in 0 until boardSize) {
            Pair(row, col)
        } else {
            null
        }
    }

    // Check if a position is within the board
    fun isWithinBoard(x: Float, y: Float): Boolean {
        return x >= boardStartX &&
                x < boardStartX + boardSize * cellSize &&
                y >= boardStartY &&
                y < boardStartY + boardSize * cellSize
    }

    // Mark that a letter was placed in the current turn
    fun markAsCurrentTurn(row: Int, col: Int) {
        if (row in 0 until boardSize && col in 0 until boardSize && boardLetters[row][col] != null) {
            currentTurnLetters[Pair(row, col)] = boardLetters[row][col]!!
            invalidate()
        }
    }

    // Mark all current turn letters as previous turn letters
    fun confirmTurn() {
        currentTurnLetters.clear()
        invalidate()
    }

    // Get the letter at a specific position
    fun getLetterAt(row: Int, col: Int): Char? {
        return if (row in 0 until boardSize && col in 0 until boardSize) {
            boardLetters[row][col]
        } else {
            null
        }
    }

    // Check if a position contains a letter from the current turn
    fun isCurrentTurnLetter(row: Int, col: Int): Boolean {
        return currentTurnLetters.containsKey(Pair(row, col))
    }
}