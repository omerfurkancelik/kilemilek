package com.example.kilemilek.utils

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A utility class for Turkish word validation
 * This class loads and manages a Turkish dictionary from a text file
 */
class TurkishDictionary(private val context: Context) {

    // Set to hold all valid Turkish words
    private val wordsSet = mutableSetOf<String>()

    // Flag to check if dictionary is loaded
    private var isDictionaryLoaded = false

    /**
     * Asynchronously loads the dictionary from a text file in assets folder
     * @param fileName The name of the dictionary file in assets folder
     * @return Boolean indicating if the loading was successful
     */
    suspend fun loadDictionary(fileName: String = "turkish.txt"): Boolean = withContext(Dispatchers.IO) {
        try {
            if (isDictionaryLoaded) {
                return@withContext true
            }

            Log.d("TurkishDictionary", "Loading dictionary from $fileName")

            // Open file from assets folder
            context.assets.open(fileName).use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var line: String?

                    // Read all words line by line
                    while (reader.readLine().also { line = it } != null) {
                        line?.trim()?.let { word ->
                            if (word.isNotEmpty()) {
                                // Convert to uppercase for case-insensitive matching
                                wordsSet.add(word.uppercase())
                            }
                        }
                    }
                }
            }

            isDictionaryLoaded = true
            Log.d("TurkishDictionary", "Dictionary loaded with ${wordsSet.size} words")
            return@withContext true

        } catch (e: Exception) {
            Log.e("TurkishDictionary", "Error loading dictionary", e)
            return@withContext false
        }
    }

    /**
     * Checks if a word exists in the Turkish dictionary
     * @param word The word to validate
     * @return Boolean indicating if the word is valid
     */
    fun isValidWord(word: String): Boolean {
        if (!isDictionaryLoaded) {
            Log.w("TurkishDictionary", "Dictionary not loaded yet")
            return false
        }

        // Convert to uppercase for case-insensitive matching
        val uppercaseWord = word.uppercase()
        return uppercaseWord.isNotEmpty() && wordsSet.contains(uppercaseWord)
    }

    /**
     * Returns the size of the loaded dictionary
     * @return Number of words in the dictionary
     */
    fun dictionarySize(): Int {
        return wordsSet.size
    }

    /**
     * Clears the dictionary to free up memory
     */
    fun clearDictionary() {
        wordsSet.clear()
        isDictionaryLoaded = false
    }
}