package com.example.kilemilek.activities

import android.app.ProgressDialog
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.kilemilek.R
import com.example.kilemilek.models.FriendModel
import com.example.kilemilek.models.GameData
import com.example.kilemilek.models.GameRequestModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import de.hdodenhof.circleimageview.CircleImageView
import android.view.LayoutInflater

class SelectFriendActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var progressBar: ProgressBar

    private lateinit var db: FirebaseFirestore
    private lateinit var currentUserId: String
    private lateinit var currentUserName: String
    private lateinit var currentUserEmail: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_select_friend)

        // Enable back button in action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Select Friend for Game"

        // Initialize Firestore
        db = FirebaseFirestore.getInstance()

        // Get current user ID and info
        val currentUser = FirebaseAuth.getInstance().currentUser
        currentUser?.let {
            currentUserId = it.uid
            currentUserEmail = it.email ?: ""

            // Get current user name from Firestore
            db.collection("users").document(currentUserId)
                .get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        currentUserName = document.getString("name") ?: ""
                    }
                }
        }

        // Initialize views
        recyclerView = findViewById(R.id.friends_recycler_view)
        emptyView = findViewById(R.id.empty_view)
        progressBar = findViewById(R.id.progress_bar)

        // Set up RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Load friends
        loadFriends()
    }

    private fun loadFriends() {
        // Show progress
        progressBar.visibility = View.VISIBLE
        emptyView.visibility = View.GONE

        db.collection("friends")
            .whereEqualTo("userId", currentUserId)
            .whereEqualTo("status", "accepted")
            .get()
            .addOnSuccessListener { documents ->
                val friendsList = mutableListOf<FriendModel>()

                for (document in documents) {
                    val friend = document.toObject(FriendModel::class.java)
                    friendsList.add(friend)
                }

                // Hide progress
                progressBar.visibility = View.GONE

                if (friendsList.isEmpty()) {
                    emptyView.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                    emptyView.text = "No friends found. Add friends to play!"
                    Toast.makeText(this, "You don't have any friends yet. Please add friends to play.", Toast.LENGTH_LONG).show()
                }
                else {
                    emptyView.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE

                    // Set adapter
                    recyclerView.adapter = SelectFriendAdapter(this, friendsList) { friend ->
                        sendGameRequest(friend)
                    }
                }
            }
            .addOnFailureListener { exception ->
                // Hide progress
                progressBar.visibility = View.GONE
                emptyView.visibility = View.VISIBLE
                emptyView.text = "Failed to load friends: ${exception.message}"
                recyclerView.visibility = View.GONE

                Toast.makeText(this, "Error loading friends: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun sendGameRequest(friend: FriendModel) {
        // Show progress dialog
        val progressDialog = ProgressDialog(this)
        progressDialog.setMessage("Sending game request...")
        progressDialog.setCancelable(false)
        progressDialog.show()

        // Intent'ten zaman tipini ve limitini al
        val timeType = intent.getStringExtra("TIME_TYPE") ?: ""
        val timeLimit = intent.getLongExtra("TIME_LIMIT", 0L)

        // Şu anki zaman
        val currentTime = System.currentTimeMillis()

        // Hamle son tarihini belirle
        val moveDeadline = currentTime + timeLimit

        // Create a new game request
        val gameRequest = GameRequestModel(
            senderId = currentUserId,
            senderName = currentUserName,
            senderEmail = currentUserEmail,
            receiverId = friend.friendId,
            receiverName = friend.friendName,
            receiverEmail = friend.friendEmail,
            status = "pending",
            createdAt = currentTime,
            lastUpdatedAt = currentTime,
            gameTimeType = timeType,
            moveDeadline = moveDeadline,
            gameData = GameData(
                playerTurn = currentUserId,
                playerScores = mapOf(
                    currentUserId to 0,
                    friend.friendId to 0
                )
            )
        )

        // Add to Firestore
        db.collection("game_requests")
            .add(gameRequest)
            .addOnSuccessListener { documentReference ->
                // Update the game request with its ID
                db.collection("game_requests")
                    .document(documentReference.id)
                    .update("id", documentReference.id)
                    .addOnSuccessListener {
                        progressDialog.dismiss()
                        Toast.makeText(this, "Game request sent to ${friend.friendName}", Toast.LENGTH_SHORT).show()
                        finish() // Return to previous screen
                    }
                    .addOnFailureListener {
                        progressDialog.dismiss()
                        Toast.makeText(this, "Error updating game request: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                progressDialog.dismiss()
                Toast.makeText(this, "Failed to send game request: ${it.message}", Toast.LENGTH_SHORT).show()
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

    // Adapter for selecting friends
    inner class SelectFriendAdapter(
        private val context: android.content.Context,
        private val friends: List<FriendModel>,
        private val onFriendSelected: (FriendModel) -> Unit
    ) : RecyclerView.Adapter<SelectFriendAdapter.FriendViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FriendViewHolder {
            val view = LayoutInflater.from(context).inflate(R.layout.item_select_friend, parent, false)
            return FriendViewHolder(view)
        }

        override fun onBindViewHolder(holder: FriendViewHolder, position: Int) {
            val friend = friends[position]

            // Set friend name and email
            holder.nameTextView.text = friend.friendName.ifEmpty { "User" }
            holder.emailTextView.text = friend.friendEmail

            // Set online status (randomly for now)
            val isOnline = (position % 2 == 0)
            holder.statusTextView.text = if (isOnline) "Online" else "Offline"
            holder.statusTextView.setTextColor(context.resources.getColor(
                if (isOnline) R.color.online_green else R.color.text_secondary, null))

            // Set click listener for select button
            holder.selectButton.setOnClickListener {
                onFriendSelected(friend)
            }

            // Make the entire item clickable
            holder.itemView.setOnClickListener {
                onFriendSelected(friend)
            }
        }

        override fun getItemCount(): Int = friends.size

        inner class FriendViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val avatarImageView: CircleImageView = itemView.findViewById(R.id.friend_avatar)
            val nameTextView: TextView = itemView.findViewById(R.id.friend_name)
            val emailTextView: TextView = itemView.findViewById(R.id.friend_email)
            val statusTextView: TextView = itemView.findViewById(R.id.friend_status)
            val selectButton: Button = itemView.findViewById(R.id.select_button)
        }
    }
}