package com.example.kilemilek.models

/**
 * Model class representing a friendship between two users.
 * This can represent both a pending friend request and an established friendship.
 */
data class FriendModel(
    /**
     * ID of the user who initiated the friendship/request
     */
    val userId: String = "",

    /**
     * Email of the user who initiated the friendship/request
     */
    val userEmail: String = "",

    /**
     * Display name of the user who initiated the friendship/request
     */
    val userName: String = "",

    /**
     * ID of the friend (recipient of the request)
     */
    val friendId: String = "",

    /**
     * Email of the friend
     */
    val friendEmail: String = "",

    /**
     * Display name of the friend
     */
    val friendName: String = "",

    /**
     * Status of the friendship:
     * - "pending": Friend request has been sent but not yet accepted
     * - "accepted": Friend request has been accepted, friendship established
     * - "rejected": Friend request was rejected
     */
    val status: String = "",

    /**
     * Timestamp when this friendship/request was created or last updated
     */
    val timestamp: Long = 0,

    /**
     * Firestore document ID for this friendship record
     */
    val id: String = ""
)