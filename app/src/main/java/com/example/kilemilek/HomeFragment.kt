package com.example.kilemilek

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

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
            // Start a new game
            startNewGame()
        }

        // Load active and finished games
        loadActiveGames()
        loadFinishedGames()

        return view
    }

    private fun startNewGame() {
        // TODO: Implement new game creation logic
        // For now, just show a toast
        if (context != null) {
            android.widget.Toast.makeText(context, "Starting a new game...", android.widget.Toast.LENGTH_SHORT).show()

            // You would typically navigate to a game setup activity/fragment here
            // For example:
            // val intent = Intent(activity, GameSetupActivity::class.java)
            // startActivity(intent)
        }
    }

    private fun loadActiveGames() {
        // Show loading state
        emptyActiveGamesText.visibility = View.GONE

        // Query Firestore for active games
        db.collection("games")
            .whereEqualTo("userId", currentUserId)
            .whereEqualTo("status", "active")
            .orderBy("lastPlayedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(10) // Limit to 10 most recent games
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    // No active games
                    emptyActiveGamesText.visibility = View.VISIBLE
                    return@addOnSuccessListener
                }

                // Create list of games
                val activeGames = documents.map { doc ->
                    GameModel(
                        id = doc.id,
                        name = doc.getString("name") ?: "Game",
                        status = doc.getString("status") ?: "active",
                        lastPlayedAt = doc.getLong("lastPlayedAt") ?: 0,
                        score = doc.getLong("score")?.toInt() ?: 0,
                        opponentName = doc.getString("opponentName") ?: "Solo Game"
                    )
                }

                // Set adapter
                activeGamesRecyclerView.adapter = GameAdapter(requireContext(), activeGames, true)
            }
            .addOnFailureListener {
                // Show error state
                emptyActiveGamesText.visibility = View.VISIBLE
                emptyActiveGamesText.text = "Failed to load active games."
            }
    }

    private fun loadFinishedGames() {
        // Show loading state
        emptyFinishedGamesText.visibility = View.GONE

        // Query Firestore for finished games
        db.collection("games")
            .whereEqualTo("userId", currentUserId)
            .whereEqualTo("status", "finished")
            .orderBy("lastPlayedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(10) // Limit to 10 most recent games
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    // No finished games
                    emptyFinishedGamesText.visibility = View.VISIBLE
                    return@addOnSuccessListener
                }

                // Create list of games
                val finishedGames = documents.map { doc ->
                    GameModel(
                        id = doc.id,
                        name = doc.getString("name") ?: "Game",
                        status = doc.getString("status") ?: "finished",
                        lastPlayedAt = doc.getLong("lastPlayedAt") ?: 0,
                        score = doc.getLong("score")?.toInt() ?: 0,
                        opponentName = doc.getString("opponentName") ?: "Solo Game"
                    )
                }

                // Set adapter
                finishedGamesRecyclerView.adapter = GameAdapter(requireContext(), finishedGames, false)
            }
            .addOnFailureListener {
                // Show error state
                emptyFinishedGamesText.visibility = View.VISIBLE
                emptyFinishedGamesText.text = "Failed to load finished games."
            }
    }

    // Model class for games
    data class GameModel(
        val id: String,
        val name: String,
        val status: String,
        val lastPlayedAt: Long,
        val score: Int,
        val opponentName: String
    )

    // Adapter for the RecyclerView
    inner class GameAdapter(
        private val context: android.content.Context,
        private val games: List<GameModel>,
        private val isActive: Boolean
    ) : RecyclerView.Adapter<GameAdapter.GameViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GameViewHolder {
            val view = LayoutInflater.from(context).inflate(R.layout.item_game, parent, false)
            return GameViewHolder(view)
        }

        override fun onBindViewHolder(holder: GameViewHolder, position: Int) {
            val game = games[position]

            // Bind game data to the view
            holder.nameTextView.text = game.name

            // Format last played time
            val lastPlayedTime = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
                .format(java.util.Date(game.lastPlayedAt))
            holder.lastPlayedTextView.text = "Last played: $lastPlayedTime"

            // Set opponent or score based on game type
            if (isActive) {
                holder.detailsTextView.text = "Playing with: ${game.opponentName}"
                holder.actionButton.text = "Continue"
                holder.actionButton.setBackgroundColor(context.resources.getColor(R.color.primary, null))
            } else {
                holder.detailsTextView.text = "Final score: ${game.score}"
                holder.actionButton.text = "View Results"
                holder.actionButton.setBackgroundColor(context.resources.getColor(R.color.accent, null))
            }

            // Set click listener
            holder.actionButton.setOnClickListener {
                if (isActive) {
                    // Continue game
                    android.widget.Toast.makeText(context, "Continuing game: ${game.name}", android.widget.Toast.LENGTH_SHORT).show()
                    // Navigate to game screen
                } else {
                    // View game results
                    android.widget.Toast.makeText(context, "Viewing results for: ${game.name}", android.widget.Toast.LENGTH_SHORT).show()
                    // Navigate to results screen
                }
            }
        }

        override fun getItemCount(): Int = games.size

        inner class GameViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val nameTextView: TextView = itemView.findViewById(R.id.game_name)
            val lastPlayedTextView: TextView = itemView.findViewById(R.id.last_played_text)
            val detailsTextView: TextView = itemView.findViewById(R.id.game_details)
            val actionButton: android.widget.Button = itemView.findViewById(R.id.game_action_button)
        }
    }
}