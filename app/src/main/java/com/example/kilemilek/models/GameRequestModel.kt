package com.example.kilemilek.models

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
    val gameData: GameData = GameData()
)

data class GameData(
    val boardState: Map<String, String> = emptyMap(), // Map of "row,col" to letter
    val playerTurn: String = "", // ID of player whose turn it is
    val playerScores: Map<String, Int> = emptyMap(), // Player ID to score
    val lastMove: LastMove = LastMove(),
    val gameBoard: List<List<Int>> = emptyList() // Optional: store the board layout
)

data class LastMove(
    val playerId: String = "",
    val word: String = "",
    val points: Int = 0,
    val timestamp: Long = 0
)