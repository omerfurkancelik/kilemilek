package com.example.kilemilek.fragments

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.kilemilek.activities.LoginActivity
import com.example.kilemilek.R
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import de.hdodenhof.circleimageview.CircleImageView

class ProfileFragment : Fragment() {

    private lateinit var profileImageView: CircleImageView
    private lateinit var editProfileButton: MaterialButton
    private lateinit var usernameTextView: TextView
    private lateinit var emailTextView: TextView
    private lateinit var gamesCountTextView: TextView
    private lateinit var wordsCountTextView: TextView
    private lateinit var friendsCountTextView: TextView
    private lateinit var logoutButton: Button

    private lateinit var mAuth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private var selectedImageUri: Uri? = null
    private var currentUsername: String = ""

    // Activity result launcher for image selection


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_profile, container, false)

        // Initialize Firebase Auth, Firestore and Storage
        mAuth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Initialize views
        profileImageView = view.findViewById(R.id.profile_image)
        editProfileButton = view.findViewById(R.id.edit_profile_button)
        usernameTextView = view.findViewById(R.id.username_text_view)
        emailTextView = view.findViewById(R.id.email_text_view)
        gamesCountTextView = view.findViewById(R.id.games_count)
        wordsCountTextView = view.findViewById(R.id.words_count)
        friendsCountTextView = view.findViewById(R.id.friends_count)
        logoutButton = view.findViewById(R.id.logout_button)

        // Set up profile image click listener for image selection


        // Set up edit profile button
        editProfileButton.setOnClickListener {
            showEditProfileDialog()
        }

        // Set user info
        loadUserProfile()

        // Set up logout button
        logoutButton.setOnClickListener {
            mAuth.signOut()
            Toast.makeText(context, "Logged out successfully", Toast.LENGTH_SHORT).show()

            // Redirect to login screen
            val intent = Intent(activity, LoginActivity::class.java)
            startActivity(intent)
            activity?.finish()
        }

        return view
    }

    private fun loadUserProfile() {
        val currentUser = mAuth.currentUser
        currentUser?.let { user ->
            emailTextView.text = user.email

            // Fetch user data from Firestore
            db.collection("users").document(user.uid)
                .get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        // Get user data
                        val username = document.getString("name")
                        val profileImageUrl = document.getString("profileImageUrl")
                        val gamesPlayed = document.getLong("gamesPlayed") ?: 0
                        val wordsFound = document.getLong("wordsFound") ?: 0

                        // Set username
                        if (!username.isNullOrEmpty()) {
                            usernameTextView.text = username
                            currentUsername = username
                        } else {
                            usernameTextView.text = "User"
                            currentUsername = "User"
                        }

                        // Set profile image
                        if (!profileImageUrl.isNullOrEmpty()) {
                            // Load image with Glide or similar library
                            // For example:
                            // Glide.with(this).load(profileImageUrl).into(profileImageView)

                            // Or use Firebase UI's ImageLoader
                            com.bumptech.glide.Glide.with(requireContext())
                                .load(profileImageUrl)
                                .placeholder(R.drawable.ic_profile)
                                .into(profileImageView)
                        }

                        // Set stats
                        gamesCountTextView.text = gamesPlayed.toString()
                        wordsCountTextView.text = wordsFound.toString()

                        // Get friends count
                        db.collection("friends")
                            .whereEqualTo("userId", user.uid)
                            .whereEqualTo("status", "accepted")
                            .get()
                            .addOnSuccessListener { friendsDocuments ->
                                friendsCountTextView.text = friendsDocuments.size().toString()
                            }
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(context, "Failed to load user data", Toast.LENGTH_SHORT).show()
                }
        }
    }


    private fun showEditProfileDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit_profile, null)
        val usernameEditText = dialogView.findViewById<EditText>(R.id.username_edit_text)

        // Pre-fill with current username
        usernameEditText.setText(currentUsername)

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Edit Profile")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newUsername = usernameEditText.text.toString().trim()

                if (newUsername.isNotEmpty()) {
                    updateUsername(newUsername)
                } else {
                    Toast.makeText(context, "Username cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()
    }

    private fun updateUsername(newUsername: String) {
        val currentUser = mAuth.currentUser ?: return

        // Update user profile in Firestore
        db.collection("users").document(currentUser.uid)
            .update("name", newUsername)
            .addOnSuccessListener {
                // Update UI
                usernameTextView.text = newUsername
                currentUsername = newUsername
                Toast.makeText(context, "Profile updated", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Failed to update profile: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}