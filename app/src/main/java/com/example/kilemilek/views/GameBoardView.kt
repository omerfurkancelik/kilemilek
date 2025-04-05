package com.example.kilemilek.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
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

        // Draw grid lines
        drawGridLines(canvas)

        // Draw letters
        drawLetters(canvas)
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