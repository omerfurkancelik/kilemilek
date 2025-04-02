package com.example.kilemilek.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.kilemilek.R
import com.example.kilemilek.activities.GameActivity
import com.example.kilemilek.models.GameRequestModel
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

class HomeFragment : Fragment() {

    private lateinit var welcomeText: TextView
    private lateinit var newGameButton: MaterialButton
    private lateinit var activeGamesRecyclerView: RecyclerView
    private lateinit var finishedGamesRecyclerView: RecyclerView
    private lateinit var emptyActiveGamesText: TextView
    private lateinit var emptyFinishedGamesText: TextView

    private lateinit var db: FirebaseFirestore
    private var currentUserId: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        // Initialize Firestore
        db = FirebaseFirestore.getInstance()

        // Get current user ID
        val currentUser = FirebaseAuth.getInstance().currentUser
        currentUser?.let {
            currentUserId = it.uid
        }

        // Initialize views
        welcomeText = view.findViewById(R.id.welcome_text)
        newGameButton = view.findViewById(R.id.new_game_button)
        activeGamesRecyclerView = view.findViewById(R.id.active_games_recycler_view)
        finishedGamesRecyclerView = view.findViewById(R.id.finished_games_recycler_view)
        emptyActiveGamesText = view.findViewById(R.id.empty_active_games_text)
        emptyFinishedGamesText = view.findViewById(R.id.empty_finished_games_text)

        // Set welcome text
        currentUser?.let {
            welcomeText.text = "Welcome to Kilemilek\n${it.email}"
        }

        // Set up RecyclerViews
        activeGamesRecyclerView.layoutManager = LinearLayoutManager(context)
        finishedGamesRecyclerView.layoutManager = LinearLayoutManager(context)

        // Set up new game button
        newGameButton.setOnClickListener {
            // For now, just show a toast
            Toast.makeText(context, "To start a new game, invite a friend from the Friends tab", Toast.LENGTH_LONG).show()
        }

        // Load active game requests and game requests sent to the user
        loadActiveGames()
        loadFinishedGames()

        return view
    }

    override fun onResume() {
        super.onResume()
        // Refresh games when fragment becomes visible
        loadActiveGames()
        loadFinishedGames()
    }

    private fun loadActiveGames() {
        // Show loading state or empty view initially
        emptyActiveGamesText.visibility = View.VISIBLE

        // Query for game requests where the user is either sender or receiver and status is accepted
        db.collection("game_requests")
            .whereIn("status", listOf("pending", "accepted"))
            .get()
            .addOnSuccessListener { documents ->
                val activeGames = mutableListOf<GameRequestModel>()

                // Filter for games involving current user
                for (document in documents) {
                    val game = document.toObject(GameRequestModel::class.java)
                    if (game.senderId == currentUserId || game.receiverId == currentUserId) {
                        activeGames.add(game)
                    }
                }

                if (activeGames.isEmpty()) {
                    emptyActiveGamesText.visibility = View.VISIBLE
                } else {
                    emptyActiveGamesText.visibility = View.GONE

                    // Sort games: accepted first, then by last updated time
                    activeGames.sortWith(compareBy(
                        { if (it.status == "accepted") 0 else 1 },
                        { -it.lastUpdatedAt }
                    ))

                    // Set adapter
                    activeGamesRecyclerView.adapter = GameAdapter(requireContext(), activeGames, true) { gameId ->
                        // Open game activity when clicked
                        val intent = Intent(activity, GameActivity::class.java)
                        intent.putExtra("GAME_ID", gameId)
                        startActivity(intent)
                    }
                }
            }
            .addOnFailureListener { exception ->
                emptyActiveGamesText.visibility = View.VISIBLE
                emptyActiveGamesText.text = "Failed to load active games: ${exception.message}"
            }
    }

    private fun loadFinishedGames() {
        // Show loading state or empty view initially
        emptyFinishedGamesText.visibility = View.VISIBLE

        // Query for completed game requests
        db.collection("game_requests")
            .whereEqualTo("status", "completed")
            .whereIn("status", listOf("completed"))
            .get()
            .addOnSuccessListener { documents ->
                val finishedGames = mutableListOf<GameRequestModel>()

                // Filter for games involving current user
                for (document in documents) {
                    val game = document.toObject(GameRequestModel::class.java)
                    if (game.senderId == currentUserId || game.receiverId == currentUserId) {
                        finishedGames.add(game)
                    }
                }

                if (finishedGames.isEmpty()) {
                    emptyFinishedGamesText.visibility = View.VISIBLE
                } else {
                    emptyFinishedGamesText.visibility = View.GONE

                    // Sort by last updated time (most recent first)
                    finishedGames.sortByDescending { it.lastUpdatedAt }

                    // Set adapter
                    finishedGamesRecyclerView.adapter = GameAdapter(requireContext(), finishedGames, false) { gameId ->
                        // Open game activity in view mode when clicked
                        val intent = Intent(activity, GameActivity::class.java)
                        intent.putExtra("GAME_ID", gameId)
                        intent.putExtra("VIEW_MODE", true)
                        startActivity(intent)
                    }
                }
            }
            .addOnFailureListener { exception ->
                emptyFinishedGamesText.visibility = View.VISIBLE
                emptyFinishedGamesText.text = "Failed to load finished games: ${exception.message}"
            }
    }

    // Adapter for the RecyclerView
    inner class GameAdapter(
        private val context: android.content.Context,
        private val games: List<GameRequestModel>,
        private val isActive: Boolean,
        private val onGameClick: (String) -> Unit
    ) : RecyclerView.Adapter<GameAdapter.GameViewHolder>() {

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

            // Set game name (can be customized later)
            val gameName = "Game with $opponentName"
            holder.nameTextView.text = gameName

            // Format last updated time
            val lastUpdatedTime = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                .format(Date(game.lastUpdatedAt))
            holder.lastPlayedTextView.text = "Last updated: $lastUpdatedTime"

            // Set details based on game type and status
            if (isActive) {
                if (game.status == "pending") {
                    if (isCreator) {
                        // Current user created the game request
                        holder.detailsTextView.text = "Waiting for $opponentName to accept"
                        holder.actionButton.text = "Cancel Request"
                        holder.actionButton.setBackgroundColor(context.resources.getColor(R.color.error, null))
                        holder.actionButton.setOnClickListener {
                            cancelGameRequest(game.id)
                        }
                    } else {
                        // Current user received the game request
                        holder.detailsTextView.text = "Game request from $opponentName"
                        holder.actionButton.text = "Accept Request"
                        holder.actionButton.setBackgroundColor(context.resources.getColor(R.color.primary, null))
                        holder.actionButton.setOnClickListener {
                            acceptGameRequest(game.id)
                        }
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

                    holder.actionButton.setOnClickListener {
                        onGameClick(game.id)
                    }
                }
            } else {
                // Finished game
                val yourScore = game.gameData.playerScores[currentUserId] ?: 0
                val opponentId = if (isCreator) game.receiverId else game.senderId
                val opponentScore = game.gameData.playerScores[opponentId] ?: 0

                // Determine winner
                val result = when {
                    yourScore > opponentScore -> "You won!"
                    yourScore < opponentScore -> "$opponentName won"
                    else -> "It's a tie!"
                }

                holder.detailsTextView.text = "Final score: You $yourScore - $opponentScore $opponentName. $result"
                holder.actionButton.text = "View Game"
                holder.actionButton.setBackgroundColor(context.resources.getColor(R.color.accent, null))
                holder.actionButton.setOnClickListener {
                    onGameClick(game.id)
                }
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

    private fun acceptGameRequest(gameId: String) {
        val progressDialog = android.app.ProgressDialog(context)
        progressDialog.setMessage("Accepting game request...")
        progressDialog.setCancelable(false)
        progressDialog.show()

        // Update game request status to accepted
        db.collection("game_requests").document(gameId)
            .update(
                mapOf(
                    "status" to "accepted",
                    "lastUpdatedAt" to System.currentTimeMillis()
                )
            )
            .addOnSuccessListener {
                progressDialog.dismiss()
                Toast.makeText(context, "Game request accepted", Toast.LENGTH_SHORT).show()
                loadActiveGames() // Refresh the list

                // Open the game
                val intent = Intent(activity, GameActivity::class.java)
                intent.putExtra("GAME_ID", gameId)
                startActivity(intent)
            }
            .addOnFailureListener { exception ->
                progressDialog.dismiss()
                Toast.makeText(context, "Error accepting game request: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun cancelGameRequest(gameId: String) {
        val progressDialog = android.app.ProgressDialog(context)
        progressDialog.setMessage("Cancelling game request...")
        progressDialog.setCancelable(false)
        progressDialog.show()

        // Delete the game request
        db.collection("game_requests").document(gameId)
            .delete()
            .addOnSuccessListener {
                progressDialog.dismiss()
                Toast.makeText(context, "Game request cancelled", Toast.LENGTH_SHORT).show()
                loadActiveGames() // Refresh the list
            }
            .addOnFailureListener { exception ->
                progressDialog.dismiss()
                Toast.makeText(context, "Error cancelling game request: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }
}