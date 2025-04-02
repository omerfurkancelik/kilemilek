package com.example.kilemilek.objects

object GameBoardMatrix {
    // Board size (15x15 standard grid)
    const val BOARD_SIZE = 15

    // Tile types
    const val NORMAL = 0    // Normal tile
    const val DL = 1        // Double Letter (H2)
    const val TL = 2        // Triple Letter (H3)
    const val DW = 3        // Double Word (K2)
    const val TW = 4        // Triple Word (K3)
    const val CENTER = 5    // Center tile (Star)

    // Create the standard 15x15 board matrix
    val boardMatrix = arrayOf(
        // Row 0
        intArrayOf(NORMAL, NORMAL, TW, NORMAL, NORMAL, DL, NORMAL, NORMAL, NORMAL, DL, NORMAL, NORMAL, TW, NORMAL, NORMAL),
        // Row 1
        intArrayOf(NORMAL, TL, NORMAL, NORMAL, NORMAL, NORMAL, DL, NORMAL, DL, NORMAL, NORMAL, NORMAL, NORMAL, TL, NORMAL),
        // Row 2
        intArrayOf(TW, NORMAL, NORMAL, NORMAL, NORMAL, NORMAL, NORMAL, DW, NORMAL, NORMAL, NORMAL, NORMAL, NORMAL, NORMAL, TW),
        // Row 3
        intArrayOf(NORMAL, NORMAL, NORMAL, DW, NORMAL, NORMAL, NORMAL, NORMAL, NORMAL, NORMAL, NORMAL, DW, NORMAL, NORMAL, NORMAL),
        // Row 4
        intArrayOf(NORMAL, NORMAL, NORMAL, NORMAL, TL, NORMAL, NORMAL, NORMAL, NORMAL, NORMAL, TL, NORMAL, NORMAL, NORMAL, NORMAL),
        // Row 5
        intArrayOf(DL, NORMAL, NORMAL, NORMAL, NORMAL, DL, NORMAL, NORMAL, NORMAL, DL, NORMAL, NORMAL, NORMAL, NORMAL, DL),
        // Row 6
        intArrayOf(NORMAL, DL, NORMAL, NORMAL, NORMAL, NORMAL, DL, NORMAL, DL, NORMAL, NORMAL, NORMAL, NORMAL, DL, NORMAL),
        // Row 7
        intArrayOf(NORMAL, NORMAL, DW, NORMAL, NORMAL, NORMAL, NORMAL, CENTER, NORMAL, NORMAL, NORMAL, NORMAL, DW, NORMAL, NORMAL),
        // Row 8
        intArrayOf(NORMAL, DL, NORMAL, NORMAL, NORMAL, NORMAL, DL, NORMAL, DL, NORMAL, NORMAL, NORMAL, NORMAL, DL, NORMAL),
        // Row 9
        intArrayOf(DL, NORMAL, NORMAL, NORMAL, NORMAL, DL, NORMAL, NORMAL, NORMAL, DL, NORMAL, NORMAL, NORMAL, NORMAL, DL),
        // Row 10
        intArrayOf(NORMAL, NORMAL, NORMAL, NORMAL, TL, NORMAL, NORMAL, NORMAL, NORMAL, NORMAL, TL, NORMAL, NORMAL, NORMAL, NORMAL),
        // Row 11
        intArrayOf(NORMAL, NORMAL, NORMAL, DW, NORMAL, NORMAL, NORMAL, NORMAL, NORMAL, NORMAL, NORMAL, DW, NORMAL, NORMAL, NORMAL),
        // Row 12
        intArrayOf(TW, NORMAL, NORMAL, NORMAL, NORMAL, NORMAL, NORMAL, DW, NORMAL, NORMAL, NORMAL, NORMAL, NORMAL, NORMAL, TW),
        // Row 13
        intArrayOf(NORMAL, TL, NORMAL, NORMAL, NORMAL, NORMAL, DL, NORMAL, DL, NORMAL, NORMAL, NORMAL, NORMAL, TL, NORMAL),
        // Row 14
        intArrayOf(NORMAL, NORMAL, TW, NORMAL, NORMAL, DL, NORMAL, NORMAL, NORMAL, DL, NORMAL, NORMAL, TW, NORMAL, NORMAL),
    )

    // Get tile type at specific position
    fun getTileType(row: Int, col: Int): Int {
        return if (row in 0 until BOARD_SIZE && col in 0 until BOARD_SIZE) {
            boardMatrix[row][col]
        } else {
            NORMAL // Default to normal if out of bounds
        }
    }

    // Get color for specific tile type
    fun getTileColor(tileType: Int): Int {
        return when (tileType) {
            DL -> android.graphics.Color.parseColor("#81D4FA") // Light blue for H2
            TL -> android.graphics.Color.parseColor("#E1BEE7") // Light purple for H3
            DW -> android.graphics.Color.parseColor("#AED581") // Light green for K2
            TW -> android.graphics.Color.parseColor("#FFCC80") // Light orange for K3
            CENTER -> android.graphics.Color.parseColor("#FFC107") // Amber for center star
            else -> android.graphics.Color.parseColor("#E0E0E0") // Light gray for normal
        }
    }

    // Get text for specific tile type
    fun getTileText(tileType: Int): String {
        return when (tileType) {
            DL -> "H²"
            TL -> "H³"
            DW -> "K²"
            TW -> "K³"
            CENTER -> "★"
            else -> ""
        }
    }
}