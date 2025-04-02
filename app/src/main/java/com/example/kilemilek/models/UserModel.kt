package com.example.kilemilek.models

data class UserModel(
    val userId: String = "",
    val email: String = "",
    val name: String = "",
    val profileImageUrl: String = "",
    val gamesPlayed: Int = 0,
    val wordsFound: Int = 0,
    val createdAt: Long = 0
)