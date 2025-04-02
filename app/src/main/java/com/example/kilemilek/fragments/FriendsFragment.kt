package com.example.kilemilek.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.example.kilemilek.R
import com.example.kilemilek.models.GameData
import com.example.kilemilek.models.GameRequestModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

class FriendsFragment : Fragment() {

    private lateinit var tabLayout: TabLayout
    private lateinit var friendsListView: ListView
    private lateinit var requestsListView: ListView
    private lateinit var addFriendLayout: LinearLayout
    private lateinit var emailInputLayout: TextInputLayout
    private lateinit var emailEditText: TextInputEditText
    private lateinit var addFriendButton: MaterialButton
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyFriendsView: TextView
    private lateinit var emptyRequestsView: TextView

    private lateinit var db: FirebaseFirestore
    private var currentUserId: String = ""
    private var currentUserName: String = ""
    private var currentUserEmail: String = ""

    private val friendsList = mutableListOf<FriendModel>()
    private val requestsList = mutableListOf<FriendModel>()
    private lateinit var friendsAdapter: FriendsAdapter
    private lateinit var requestsAdapter: RequestsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_friends, container, false)

        // Initialize Firestore
        db = FirebaseFirestore.getInstance()

        // Get current user ID
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
        tabLayout = view.findViewById(R.id.tab_layout)
        friendsListView = view.findViewById(R.id.friends_list_view)
        requestsListView = view.findViewById(R.id.requests_list_view)
        addFriendLayout = view.findViewById(R.id.add_friend_layout)
        emailInputLayout = view.findViewById(R.id.email_input_layout)
        emailEditText = view.findViewById(R.id.email_edit_text)
        addFriendButton = view.findViewById(R.id.add_friend_button)
        progressBar = view.findViewById(R.id.progress_bar)
        emptyFriendsView = view.findViewById(R.id.empty_friends_view)
        emptyRequestsView = view.findViewById(R.id.empty_requests_view)

        // Set up adapters
        friendsAdapter = FriendsAdapter(requireContext(), friendsList)
        requestsAdapter = RequestsAdapter(requireContext(), requestsList)

        friendsListView.adapter = friendsAdapter
        requestsListView.adapter = requestsAdapter

        // Set up tab layout
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> {
                        friendsListView.visibility = View.VISIBLE
                        requestsListView.visibility = View.GONE
                        addFriendLayout.visibility = View.VISIBLE

                        if (friendsList.isEmpty()) {
                            emptyFriendsView.visibility = View.VISIBLE
                        } else {
                            emptyFriendsView.visibility = View.GONE
                        }
                        emptyRequestsView.visibility = View.GONE
                    }
                    1 -> {
                        friendsListView.visibility = View.GONE
                        requestsListView.visibility = View.VISIBLE
                        addFriendLayout.visibility = View.GONE

                        if (requestsList.isEmpty()) {
                            emptyRequestsView.visibility = View.VISIBLE
                        } else {
                            emptyRequestsView.visibility = View.GONE
                        }
                        emptyFriendsView.visibility = View.GONE
                    }
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}

            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        // Add friend button click listener
        addFriendButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()

            if (email.isEmpty()) {
                emailInputLayout.error = "Email is required"
                return@setOnClickListener
            }

            sendFriendRequest(email)
        }

        // Load friends and requests
        loadFriends()
        loadFriendRequests()

        return view
    }

    private fun loadFriends() {
        // Show progress
        progressBar.visibility = View.VISIBLE

        db.collection("friends")
            .whereEqualTo("userId", currentUserId)
            .whereEqualTo("status", "accepted")
            .get()
            .addOnSuccessListener { documents ->
                friendsList.clear()

                for (document in documents) {
                    val friend = document.toObject(FriendModel::class.java)
                    friendsList.add(friend)
                }

                friendsAdapter.notifyDataSetChanged()

                // Update empty view visibility
                if (friendsList.isEmpty() && tabLayout.selectedTabPosition == 0) {
                    emptyFriendsView.visibility = View.VISIBLE
                } else {
                    emptyFriendsView.visibility = View.GONE
                }

                // Hide progress
                progressBar.visibility = View.GONE
            }
            .addOnFailureListener {
                // Hide progress
                progressBar.visibility = View.GONE
                Toast.makeText(context, "Failed to load friends: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadFriendRequests() {
        // Show progress
        progressBar.visibility = View.VISIBLE

        db.collection("friends")
            .whereEqualTo("friendId", currentUserId)
            .whereEqualTo("status", "pending")
            .get()
            .addOnSuccessListener { documents ->
                requestsList.clear()

                for (document in documents) {
                    val request = document.toObject(FriendModel::class.java)
                    requestsList.add(request)
                }

                requestsAdapter.notifyDataSetChanged()

                // Update empty view visibility
                if (requestsList.isEmpty() && tabLayout.selectedTabPosition == 1) {
                    emptyRequestsView.visibility = View.VISIBLE
                } else {
                    emptyRequestsView.visibility = View.GONE
                }

                // Hide progress
                progressBar.visibility = View.GONE
            }
            .addOnFailureListener {
                // Hide progress
                progressBar.visibility = View.GONE
                Toast.makeText(context, "Failed to load friend requests: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun sendFriendRequest(email: String) {
        // Show progress
        progressBar.visibility = View.VISIBLE

        // First, find the user with this email
        db.collection("users")
            .whereEqualTo("email", email)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    progressBar.visibility = View.GONE
                    emailInputLayout.error = "User not found"
                    return@addOnSuccessListener
                }

                val user = documents.documents[0]
                val friendId = user.id
                val friendEmail = user.getString("email") ?: ""
                val friendName = user.getString("name") ?: ""

                // Check if it's the current user
                if (friendId == currentUserId) {
                    progressBar.visibility = View.GONE
                    emailInputLayout.error = "You cannot add yourself as a friend"
                    return@addOnSuccessListener
                }

                // Check if a friend request already exists
                db.collection("friends")
                    .whereEqualTo("userId", currentUserId)
                    .whereEqualTo("friendId", friendId)
                    .get()
                    .addOnSuccessListener { friendDocs ->
                        if (!friendDocs.isEmpty) {
                            progressBar.visibility = View.GONE
                            emailInputLayout.error = "Friend request already sent or already friends"
                            return@addOnSuccessListener
                        }

                        // Create a friend request
                        val friendRequest = FriendModel(
                            userId = currentUserId,
                            userEmail = currentUserEmail,
                            userName = currentUserName,
                            friendId = friendId,
                            friendEmail = friendEmail,
                            friendName = friendName,
                            status = "pending",
                            timestamp = System.currentTimeMillis()
                        )

                        // Add to Firestore
                        db.collection("friends")
                            .add(friendRequest)
                            .addOnSuccessListener {
                                progressBar.visibility = View.GONE
                                Toast.makeText(context, "Friend request sent", Toast.LENGTH_SHORT).show()
                                emailEditText.text?.clear()
                            }
                            .addOnFailureListener {
                                progressBar.visibility = View.GONE
                                Toast.makeText(context, "Failed to send friend request: ${it.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                    .addOnFailureListener {
                        progressBar.visibility = View.GONE
                        Toast.makeText(context, "Error checking friend status: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                progressBar.visibility = View.GONE
                Toast.makeText(context, "Error finding user: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun sendGameRequest(friend: FriendModel) {
        // Show progress dialog
        val progressDialog = android.app.ProgressDialog(context)
        progressDialog.setMessage("Sending game request...")
        progressDialog.setCancelable(false)
        progressDialog.show()

        // Create a new game request
        val gameRequest = GameRequestModel(
            senderId = currentUserId,
            senderName = currentUserName,
            senderEmail = currentUserEmail,
            receiverId = friend.friendId,
            receiverName = friend.friendName,
            receiverEmail = friend.friendEmail,
            status = "pending",
            createdAt = System.currentTimeMillis(),
            lastUpdatedAt = System.currentTimeMillis(),
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
                        Toast.makeText(context, "Game request sent to ${friend.friendName}", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        progressDialog.dismiss()
                        Toast.makeText(context, "Error updating game request: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                progressDialog.dismiss()
                Toast.makeText(context, "Failed to send game request: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // Model class for friends
    data class FriendModel(
        val userId: String = "",
        val userEmail: String = "",
        val userName: String = "",
        val friendId: String = "",
        val friendEmail: String = "",
        val friendName: String = "",
        val status: String = "",
        val timestamp: Long = 0,
        val id: String = "" // Document ID
    )

    // Adapter for friends list
    inner class FriendsAdapter(context: android.content.Context, private val friends: List<FriendModel>) :
        ArrayAdapter<FriendModel>(context, R.layout.item_friend, friends) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_friend, parent, false)

            val friend = friends[position]

            val nameTextView = view.findViewById<TextView>(R.id.friend_name)
            val emailTextView = view.findViewById<TextView>(R.id.friend_email)
            val statusTextView = view.findViewById<TextView>(R.id.friend_status)
            val inviteButton = view.findViewById<Button>(R.id.game_invite_button)

            nameTextView.text = friend.friendName.ifEmpty { "User" }
            emailTextView.text = friend.friendEmail

            // Online status would come from a real-time database
            // For now, just randomly show some as online and some as offline
            val isOnline = (position % 2 == 0)
            statusTextView.text = if (isOnline) "Online" else "Offline"
            statusTextView.setTextColor(resources.getColor(
                if (isOnline) R.color.online_green else R.color.text_secondary, null))

            // Set up invite button
            inviteButton.setOnClickListener {
                // Show confirmation dialog
                MaterialAlertDialogBuilder(context)
                    .setTitle("Send Game Request")
                    .setMessage("Do you want to send a game request to ${friend.friendName}?")
                    .setPositiveButton("Send") { _, _ ->
                        sendGameRequest(friend)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }

            return view
        }
    }

    // Adapter for friend requests
    inner class RequestsAdapter(context: android.content.Context, private val requests: List<FriendModel>) :
        ArrayAdapter<FriendModel>(context, R.layout.item_friend_request, requests) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_friend_request, parent, false)

            val request = requests[position]

            val nameTextView = view.findViewById<TextView>(R.id.request_name)
            val emailTextView = view.findViewById<TextView>(R.id.request_email)
            val acceptButton = view.findViewById<Button>(R.id.accept_button)
            val declineButton = view.findViewById<Button>(R.id.decline_button)

            nameTextView.text = request.userName.ifEmpty { "User" }
            emailTextView.text = request.userEmail

            acceptButton.setOnClickListener {
                acceptFriendRequest(request, position)
            }

            declineButton.setOnClickListener {
                declineFriendRequest(request, position)
            }

            return view
        }
    }

    private fun acceptFriendRequest(request: FriendModel, position: Int) {
        // Show progress
        progressBar.visibility = View.VISIBLE

        // Update the request to accepted
        db.collection("friends")
            .whereEqualTo("userId", request.userId)
            .whereEqualTo("friendId", currentUserId)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(context, "Friend request not found", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val requestDoc = documents.documents[0]

                // Update status to accepted
                db.collection("friends").document(requestDoc.id)
                    .update("status", "accepted")
                    .addOnSuccessListener {
                        // Create a reciprocal friend entry
                        val reciprocalFriend = FriendModel(
                            userId = currentUserId,
                            userEmail = currentUserEmail,
                            userName = currentUserName,
                            friendId = request.userId,
                            friendEmail = request.userEmail,
                            friendName = request.userName,
                            status = "accepted",
                            timestamp = System.currentTimeMillis()
                        )

                        db.collection("friends")
                            .add(reciprocalFriend)
                            .addOnSuccessListener {
                                progressBar.visibility = View.GONE
                                Toast.makeText(context, "Friend request accepted", Toast.LENGTH_SHORT).show()

                                // Remove from requests list and update UI
                                requestsList.removeAt(position)
                                requestsAdapter.notifyDataSetChanged()

                                // Update empty view visibility
                                if (requestsList.isEmpty() && tabLayout.selectedTabPosition == 1) {
                                    emptyRequestsView.visibility = View.VISIBLE
                                }

                                // Reload friends list
                                loadFriends()
                            }
                            .addOnFailureListener {
                                progressBar.visibility = View.GONE
                                Toast.makeText(context, "Error creating reciprocal friend: ${it.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                    .addOnFailureListener {
                        progressBar.visibility = View.GONE
                        Toast.makeText(context, "Error accepting friend request: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                progressBar.visibility = View.GONE
                Toast.makeText(context, "Error finding friend request: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun declineFriendRequest(request: FriendModel, position: Int) {
        // Show progress
        progressBar.visibility = View.VISIBLE

        // Delete the friend request
        db.collection("friends")
            .whereEqualTo("userId", request.userId)
            .whereEqualTo("friendId", currentUserId)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(context, "Friend request not found", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val requestDoc = documents.documents[0]

                // Delete the request
                db.collection("friends").document(requestDoc.id)
                    .delete()
                    .addOnSuccessListener {
                        progressBar.visibility = View.GONE
                        Toast.makeText(context, "Friend request declined", Toast.LENGTH_SHORT).show()

                        // Remove from requests list and update UI
                        requestsList.removeAt(position)
                        requestsAdapter.notifyDataSetChanged()

                        // Update empty view visibility
                        if (requestsList.isEmpty() && tabLayout.selectedTabPosition == 1) {
                            emptyRequestsView.visibility = View.VISIBLE
                        }
                    }
                    .addOnFailureListener {
                        progressBar.visibility = View.GONE
                        Toast.makeText(context, "Error declining friend request: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                progressBar.visibility = View.GONE
                Toast.makeText(context, "Error finding friend request: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }
}