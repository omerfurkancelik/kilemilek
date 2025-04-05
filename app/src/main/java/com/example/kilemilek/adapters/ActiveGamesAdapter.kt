package com.example.kilemilek.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.kilemilek.R
import com.example.kilemilek.models.GameRequestModel
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.*

class ActiveGamesAdapter(
    private val context: Context,
    private val games: List<GameRequestModel>,
    private val onGameClick: (String) -> Unit
) : RecyclerView.Adapter<ActiveGamesAdapter.GameViewHolder>() {

    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GameViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_game, parent, false)
        return GameViewHolder(view)
    }

    override fun onBindViewHolder(holder: GameViewHolder, position: Int) {
        val game = games[position]

        // Determine if current user is the creator/sender
        val isCreator = game.senderId == currentUserId

        // Get opponent name
        val opponentName = if (isCreator) game.receiverName else game.senderName

        // Set game name
        val gameName = "Game with $opponentName"
        holder.nameTextView.text = gameName

        // Format last updated time
        val lastUpdatedTime = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            .format(Date(game.lastUpdatedAt))
        holder.lastPlayedTextView.text = "Last updated: $lastUpdatedTime"

        // Set details based on game status
        if (game.status == "pending") {
            if (isCreator) {
                // Current user created the game request
                holder.detailsTextView.text = "Waiting for $opponentName to accept"
                holder.actionButton.text = "Cancel Request"
                holder.actionButton.setBackgroundColor(context.resources.getColor(R.color.error, null))
            } else {
                // Current user received the game request
                holder.detailsTextView.text = "Game request from $opponentName"
                holder.actionButton.text = "Accept Request"
                holder.actionButton.setBackgroundColor(context.resources.getColor(R.color.primary, null))
            }
        } else {
            // Game is active (accepted)
            val yourScore = game.gameData.playerScores[currentUserId] ?: 0
            val opponentId = if (isCreator) game.receiverId else game.senderId
            val opponentScore = game.gameData.playerScores[opponentId] ?: 0

            holder.detailsTextView.text = "Score: You $yourScore - $opponentScore $opponentName"

            // Determine whose turn it is
            val isYourTurn = game.gameData.playerTurn == currentUserId
            if (isYourTurn) {
                holder.actionButton.text = "Your Turn"
                holder.actionButton.setBackgroundColor(context.resources.getColor(R.color.primary, null))
            } else {
                holder.actionButton.text = "Waiting for Opponent"
                holder.actionButton.setBackgroundColor(context.resources.getColor(R.color.accent, null))
            }
        }

        // Set click listener for action button
        holder.actionButton.setOnClickListener {
            onGameClick(game.id)
        }

        // Make the entire item clickable
        holder.itemView.setOnClickListener {
            onGameClick(game.id)
        }
    }

    override fun getItemCount(): Int = games.size

    inner class GameViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameTextView: TextView = itemView.findViewById(R.id.game_name)
        val lastPlayedTextView: TextView = itemView.findViewById(R.id.last_played_text)
        val detailsTextView: TextView = itemView.findViewById(R.id.game_details)
        val actionButton: Button = itemView.findViewById(R.id.game_action_button)
    }
}