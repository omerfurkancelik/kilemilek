package com.example.kilemilek.activities

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.kilemilek.R
import com.example.kilemilek.adapters.ActiveGamesAdapter
import com.example.kilemilek.models.GameRequestModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ActiveGamesActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var progressBar: ProgressBar

    private lateinit var db: FirebaseFirestore
    private lateinit var currentUserId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_active_games)

        // Enable back button in action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Active Games"

        // Initialize Firestore
        db = FirebaseFirestore.getInstance()

        // Get current user ID
        val currentUser = FirebaseAuth.getInstance().currentUser
        currentUser?.let {
            currentUserId = it.uid
        }

        // Initialize views
        recyclerView = findViewById(R.id.active_games_recycler_view)
        emptyView = findViewById(R.id.empty_view)
        progressBar = findViewById(R.id.progress_bar)

        // Set up RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Load active games
        loadActiveGames()
    }

    private fun loadActiveGames() {
        // Show progress
        progressBar.visibility = View.VISIBLE
        emptyView.visibility = View.GONE

        // Query for game requests where the user is either sender or receiver and status is accepted or pending
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

                // Hide progress
                progressBar.visibility = View.GONE

                if (activeGames.isEmpty()) {
                    emptyView.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    emptyView.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE

                    // Sort games: accepted first, then by last updated time
                    activeGames.sortWith(compareBy(
                        { if (it.status == "accepted") 0 else 1 },
                        { -it.lastUpdatedAt }
                    ))

                    // Set adapter
                    recyclerView.adapter = ActiveGamesAdapter(this, activeGames) { gameId ->
                        // Open game activity when clicked
                        val intent = Intent(this, GameActivity::class.java)
                        intent.putExtra("GAME_ID", gameId)
                        startActivity(intent)
                    }
                }
            }
            .addOnFailureListener { exception ->
                // Hide progress
                progressBar.visibility = View.GONE
                emptyView.visibility = View.VISIBLE
                emptyView.text = "Failed to load active games: ${exception.message}"
                recyclerView.visibility = View.GONE

                Toast.makeText(this, "Error loading games: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            // Handle back button
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}