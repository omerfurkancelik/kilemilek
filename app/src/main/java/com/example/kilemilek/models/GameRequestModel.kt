package com.example.kilemilek.models

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class GameRequestModel(
    val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderEmail: String = "",
    val receiverId: String = "",
    val receiverName: String = "",
    val receiverEmail: String = "",
    val status: String = "pending", // pending, accepted, rejected, completed
    val createdAt: Long = System.currentTimeMillis(),
    val lastUpdatedAt: Long = System.currentTimeMillis(),
    val gameData: GameData = GameData(),
    val gameTimeType: String = "REGULAR", // "QUICK_2MIN", "QUICK_5MIN", "EXTENDED_12HOUR", "EXTENDED_24HOUR"
    val moveDeadline: Long = 0, // Milisaniye cinsinden hamle için son tarih

    // Firestore’da bulunup modelde olmayan alanlar artık modelde isteğe bağlı olarak tanımlandı:
    val matchmakingId: String? = null,
    val playerIds: List<String>? = null
)

data class GameData(
    val boardState: Map<String, String> = emptyMap(), // Map of "row,col" to letter
    val playerTurn: String = "", // ID of player whose turn it is
    val playerScores: Map<String, Int> = emptyMap(), // Player ID to score
    val playerLetters: Map<String, List<String>> = emptyMap(), // Player ID to their letters
    val lastMove: LastMove = LastMove(),
    val gameBoard: List<List<Int>> = emptyList(), // Optional: store the board layout
    val timeLimit: Long = 0,  // Milisaniye cinsinden süre limiti
    val timeType: String = "" // "QUICK_2MIN", "QUICK_5MIN", "EXTENDED_12HOUR", "EXTENDED_24HOUR"
)

data class LastMove(
    val playerId: String = "",
    val word: String = "",
    val points: Int = 0,
    val timestamp: Long = 0
)