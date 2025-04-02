package com.example.kilemilek.objects

/**
 * Represents the distribution and values of letters in the game
 */
object LetterDistribution {
    // Letter data: letter, count, value
    data class LetterData(val letter: Char, val count: Int, val value: Int)

    // Complete set of letters with their counts and values based on the provided table
    private val letterSet = listOf(
        LetterData('A', 12, 1),
        LetterData('B', 2, 3),
        LetterData('C', 2, 4),
        LetterData('Ç', 2, 4),
        LetterData('D', 2, 3),
        LetterData('E', 8, 1),
        LetterData('F', 1, 7),
        LetterData('G', 1, 5),
        LetterData('Ğ', 1, 8),
        LetterData('H', 1, 5),
        LetterData('I', 4, 2),
        LetterData('İ', 7, 1),
        LetterData('J', 1, 10),
        LetterData('K', 7, 1),
        LetterData('L', 7, 1),
        LetterData('M', 4, 2),
        LetterData('N', 5, 1),
        LetterData('O', 3, 2),
        LetterData('Ö', 1, 7),
        LetterData('P', 1, 5),
        LetterData('R', 6, 1),
        LetterData('S', 3, 2),
        LetterData('Ş', 2, 4),
        LetterData('T', 5, 1),
        LetterData('U', 3, 2),
        LetterData('Ü', 2, 3),
        LetterData('V', 1, 7),
        LetterData('Y', 2, 3),
        LetterData('Z', 2, 4),
        LetterData('*', 2, 0)  // Joker
    )

    // The total count of all letters in the game
    val totalLetterCount: Int = letterSet.sumOf { it.count }

    // Create the complete letter bag with all letters according to their counts
    private val completeLetterBag: List<Char> = letterSet.flatMap { letterData ->
        List(letterData.count) { letterData.letter }
    }.shuffled()

    // Get value for a specific letter
    fun getLetterValue(letter: Char): Int {
        return letterSet.find { it.letter == letter }?.value ?: 0
    }

    // Create a mutable copy of the complete bag for game use
    private val letterBag = completeLetterBag.toMutableList()

    // Reset the letter bag to its initial state
    fun resetLetterBag() {
        letterBag.clear()
        letterBag.addAll(completeLetterBag.shuffled())
    }

    // Get the current count of remaining letters
    fun getRemainingLetterCount(): Int = letterBag.size

    // Draw a specified number of random letters from the bag
    fun drawLetters(count: Int): List<Char> {
        val drawnLetters = mutableListOf<Char>()

        val drawCount = minOf(count, letterBag.size)
        repeat(drawCount) {
            if (letterBag.isNotEmpty()) {
                val randomIndex = (0 until letterBag.size).random()
                drawnLetters.add(letterBag.removeAt(randomIndex))
            }
        }

        return drawnLetters
    }

    // Get a map of all letters and their values
    fun getLetterValues(): Map<Char, Int> {
        return letterSet.associate { it.letter to it.value }
    }

    // Get a map of all letters and their counts
    fun getLetterCounts(): Map<Char, Int> {
        return letterSet.associate { it.letter to it.count }
    }
}