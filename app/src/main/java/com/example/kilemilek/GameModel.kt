package com.example.kilemilek

data class GameModel(
    val id: String = "",
    val userId: String = "",
    val opponentId: String = "",
    val name: String = "",
    val opponentName: String = "",
    val status: String = "active", // active, finished
    val createdAt: Long = 0,
    val lastPlayedAt: Long = 0,
    val score: Int = 0,
    val board: List<String> = emptyList(),
    val foundWords: List<String> = emptyList(),
    val turn: String = "" // userId of whose turn it is
)