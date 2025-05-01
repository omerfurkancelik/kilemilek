package com.example.kilemilek.utils

import android.util.Log
import com.example.kilemilek.objects.GameBoardMatrix

/**
 * Utility class to validate words on the game board
 * It checks all words formed horizontally and vertically
 */
class BoardWordValidator(private val turkishDictionary: TurkishDictionary) {

    companion object {
        private const val TAG = "BoardWordValidator"
        private const val MIN_WORD_LENGTH = 2 // Minimum length for a valid word
    }

    /**
     * Validates all words on the board
     * @param boardState Map of positions to letters
     * @return Pair<Boolean, List<String>> - First value indicates if all words are valid,
     *         second value is a list of invalid words found
     */
    fun validateBoard(boardState: Map<String, String>): Pair<Boolean, List<String>> {
        // Convert board state to a 2D array for easier processing
        val board = Array(GameBoardMatrix.BOARD_SIZE) { Array<Char?>(GameBoardMatrix.BOARD_SIZE) { null } }

        // Fill the board with letters from boardState
        boardState.forEach { (posStr, letterStr) ->
            try {
                val parts = posStr.split(",")
                val row = parts[0].toInt()
                val col = parts[1].toInt()

                if (row in 0 until GameBoardMatrix.BOARD_SIZE &&
                    col in 0 until GameBoardMatrix.BOARD_SIZE &&
                    letterStr.isNotEmpty()) {

                    board[row][col] = letterStr[0]
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing board position: $posStr", e)
            }
        }

        // Get all words formed on the board
        val allWords = findAllWords(board)

        // Validate each word against the dictionary
        val invalidWords = mutableListOf<String>()

        allWords.forEach { word ->
            if (word.length >= MIN_WORD_LENGTH && !turkishDictionary.isValidWord(word)) {
                invalidWords.add(word)
            }
        }

        return Pair(invalidWords.isEmpty(), invalidWords)
    }

    /**
     * Finds all words on the board (horizontal and vertical)
     * @param board 2D array of characters representing the board
     * @return List of all words found
     */
    private fun findAllWords(board: Array<Array<Char?>>): List<String> {
        val words = mutableListOf<String>()

        // Find horizontal words
        for (row in 0 until GameBoardMatrix.BOARD_SIZE) {
            var currentWord = StringBuilder()

            for (col in 0 until GameBoardMatrix.BOARD_SIZE) {
                val letter = board[row][col]

                if (letter != null) {
                    currentWord.append(letter)
                } else {
                    // End of word, check if it's long enough
                    if (currentWord.length >= MIN_WORD_LENGTH) {
                        words.add(currentWord.toString())
                    }
                    currentWord = StringBuilder()
                }
            }

            // Check word at the end of row
            if (currentWord.length >= MIN_WORD_LENGTH) {
                words.add(currentWord.toString())
            }
        }

        // Find vertical words
        for (col in 0 until GameBoardMatrix.BOARD_SIZE) {
            var currentWord = StringBuilder()

            for (row in 0 until GameBoardMatrix.BOARD_SIZE) {
                val letter = board[row][col]

                if (letter != null) {
                    currentWord.append(letter)
                } else {
                    // End of word, check if it's long enough
                    if (currentWord.length >= MIN_WORD_LENGTH) {
                        words.add(currentWord.toString())
                    }
                    currentWord = StringBuilder()
                }
            }

            // Check word at the end of column
            if (currentWord.length >= MIN_WORD_LENGTH) {
                words.add(currentWord.toString())
            }
        }

        return words
    }

    /**
     * Validates a specific new word formed by the current move
     * @param boardState Complete board state including new letters
     * @param newLetterPositions Positions of newly placed letters
     * @return Triple<Boolean, String, List<String>> - First value indicates if the word is valid,
     *         second value is the word formed, third value is the list of all invalid words created
     */
    fun validateNewWord(
        boardState: Map<String, String>,
        newLetterPositions: List<Pair<Int, Int>>
    ): Triple<Boolean, String, List<String>> {
        // First validate the whole board
        val (allValid, invalidWords) = validateBoard(boardState)

        // Extract the main word formed by the new letters
        val mainWord = extractMainWord(boardState, newLetterPositions)

        // Check if the main word is valid
        val isMainWordValid = mainWord.isEmpty() || turkishDictionary.isValidWord(mainWord)

        return Triple(allValid && isMainWordValid, mainWord, invalidWords)
    }

    /**
     * Extracts the main word formed by newly placed letters
     * @param boardState Complete board state
     * @param newLetterPositions Positions of new letters
     * @return The main word formed
     */
    private fun extractMainWord(
        boardState: Map<String, String>,
        newLetterPositions: List<Pair<Int, Int>>
    ): String {
        if (newLetterPositions.isEmpty()) {
            return ""
        }

        // Check if letters are in a straight line
        val rows = newLetterPositions.map { it.first }.toSet()
        val cols = newLetterPositions.map { it.second }.toSet()

        // Convert board state to a 2D array for easier processing
        val board = Array(GameBoardMatrix.BOARD_SIZE) { Array<Char?>(GameBoardMatrix.BOARD_SIZE) { null } }

        boardState.forEach { (posStr, letterStr) ->
            try {
                val parts = posStr.split(",")
                val row = parts[0].toInt()
                val col = parts[1].toInt()

                if (letterStr.isNotEmpty()) {
                    board[row][col] = letterStr[0]
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing board position: $posStr", e)
            }
        }

        // If letters are in the same row, find the horizontal word
        if (rows.size == 1) {
            val row = rows.first()
            val minCol = newLetterPositions.minOf { it.second }
            val maxCol = newLetterPositions.maxOf { it.second }

            // Extend word to the left
            var startCol = minCol
            while (startCol > 0 && board[row][startCol - 1] != null) {
                startCol--
            }

            // Extend word to the right
            var endCol = maxCol
            while (endCol < GameBoardMatrix.BOARD_SIZE - 1 && board[row][endCol + 1] != null) {
                endCol++
            }

            // Build the word
            val word = StringBuilder()
            for (col in startCol..endCol) {
                val letter = board[row][col]
                if (letter != null) {
                    word.append(letter)
                } else {
                    // There shouldn't be any gaps, but just in case
                    return ""
                }
            }

            return word.toString()
        }
        // If letters are in the same column, find the vertical word
        else if (cols.size == 1) {
            val col = cols.first()
            val minRow = newLetterPositions.minOf { it.first }
            val maxRow = newLetterPositions.maxOf { it.first }

            // Extend word upward
            var startRow = minRow
            while (startRow > 0 && board[startRow - 1][col] != null) {
                startRow--
            }

            // Extend word downward
            var endRow = maxRow
            while (endRow < GameBoardMatrix.BOARD_SIZE - 1 && board[endRow + 1][col] != null) {
                endRow++
            }

            // Build the word
            val word = StringBuilder()
            for (row in startRow..endRow) {
                val letter = board[row][col]
                if (letter != null) {
                    word.append(letter)
                } else {
                    // There shouldn't be any gaps, but just in case
                    return ""
                }
            }

            return word.toString()
        }

        // If letters aren't in a straight line, return empty string
        return ""
    }
}