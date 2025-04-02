package com.example.kilemilek.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.kilemilek.LetterDistribution
import com.example.kilemilek.objects.GameBoardMatrix

class GameBoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val boardPaint = Paint().apply {
        color = Color.parseColor("#E0E0E0") // Light gray for board
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint().apply {
        color = Color.parseColor("#BDBDBD") // Medium gray for grid lines
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    private val tilePaint = Paint().apply {
        style = Paint.Style.FILL
    }

    private val textPaint = Paint().apply {
        color = Color.parseColor("#424242") // Dark gray for text
        textAlign = Paint.Align.CENTER
        textSize = 24f
    }

    private val letterPaint = Paint().apply {
        color = Color.BLACK
        textAlign = Paint.Align.CENTER
        textSize = 36f
        isFakeBoldText = true
    }

    private val letterValuePaint = Paint().apply {
        color = Color.parseColor("#757575") // Medium gray for letter values
        textAlign = Paint.Align.RIGHT
        textSize = 16f
    }

    private var cellSize = 0f
    private var boardSize = GameBoardMatrix.BOARD_SIZE
    private val textBounds = Rect()

    // Current board state with placed letters
    private val boardLetters = Array(boardSize) { Array<Char?>(boardSize) { null } }

    // Interface for handling cell touch events
    private var cellTouchListener: ((Int, Int, Char?) -> Boolean)? = null

    // Set listener for cell touch events
    fun setOnCellTouchListener(listener: (Int, Int, Char?) -> Boolean) {
        cellTouchListener = listener
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        // Make the board square
        val width = measuredWidth
        val height = measuredHeight
        val size = width.coerceAtMost(height)
        setMeasuredDimension(size, size)

        // Calculate cell size
        cellSize = size / boardSize.toFloat()

        // Update text sizes based on cell size
        textPaint.textSize = cellSize * 0.3f
        letterPaint.textSize = cellSize * 0.5f
        letterValuePaint.textSize = cellSize * 0.2f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), boardPaint)

        // Draw cells
        for (row in 0 until boardSize) {
            for (col in 0 until boardSize) {
                val left = col * cellSize
                val top = row * cellSize
                val right = left + cellSize
                val bottom = top + cellSize

                // Get tile type and set appropriate color
                val tileType = GameBoardMatrix.getTileType(row, col)
                tilePaint.color = GameBoardMatrix.getTileColor(tileType)

                // Draw cell background
                canvas.drawRect(left + 1, top + 1, right - 1, bottom - 1, tilePaint)

                // Draw tile text (H², H³, K², K³, etc.)
                val tileText = GameBoardMatrix.getTileText(tileType)
                if (tileText.isNotEmpty() && boardLetters[row][col] == null) {
                    textPaint.getTextBounds(tileText, 0, tileText.length, textBounds)
                    val textX = left + cellSize / 2
                    val textY = top + (cellSize + textBounds.height()) / 2
                    canvas.drawText(tileText, textX, textY, textPaint)
                }

                // Draw letters if any are placed
                boardLetters[row][col]?.let { letter ->
                    val letterStr = letter.toString()
                    letterPaint.getTextBounds(letterStr, 0, letterStr.length, textBounds)
                    val letterX = left + cellSize / 2
                    val letterY = top + (cellSize + textBounds.height()) / 2

                    // Draw letter
                    canvas.drawText(letterStr, letterX, letterY, letterPaint)

                    // Draw letter value
                    val value = LetterDistribution.getLetterValue(letter)
                    if (value > 0) {
                        canvas.drawText(
                            value.toString(),
                            left + cellSize - 5,
                            top + cellSize - 5,
                            letterValuePaint
                        )
                    }
                }

                // Draw grid lines
                canvas.drawRect(left, top, right, bottom, gridPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            try {
                // Calculate which cell was tapped
                val row = (event.y / cellSize).toInt()
                val col = (event.x / cellSize).toInt()

                // Check if cell is within bounds
                if (row in 0 until boardSize && col in 0 until boardSize) {
                    // Get the letter at this position (if any)
                    val letter = boardLetters[row][col]

                    // Notify the listener if there's a letter to drag
                    if (letter != null) {
                        val handled = cellTouchListener?.invoke(row, col, letter) ?: false
                        if (handled) {
                            return true
                        }
                    }
                }
            } catch (e: Exception) {
                // Log exception but don't crash
                android.util.Log.e("GameBoardView", "Error in touch event: ${e.message}")
            }
        }

        return super.onTouchEvent(event)
    }

    // Method to place a letter on the board
    fun placeLetter(row: Int, col: Int, letter: Char) {
        if (row in 0 until boardSize && col in 0 until boardSize) {
            boardLetters[row][col] = letter
            invalidate()
        }
    }

    // Method to clear a letter from the board
    fun clearLetter(row: Int, col: Int) {
        if (row in 0 until boardSize && col in 0 until boardSize) {
            boardLetters[row][col] = null
            invalidate()
        }
    }

    // Method to clear the entire board
    fun clearBoard() {
        for (row in 0 until boardSize) {
            for (col in 0 until boardSize) {
                boardLetters[row][col] = null
            }
        }
        invalidate()
    }

    // Get letter at specified position
    fun getLetterAt(row: Int, col: Int): Char? {
        return if (row in 0 until boardSize && col in 0 until boardSize) {
            boardLetters[row][col]
        } else {
            null
        }
    }
}