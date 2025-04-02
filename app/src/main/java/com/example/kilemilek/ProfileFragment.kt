package com.example.kilemilek

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProfileFragment : Fragment() {

    private lateinit var emailTextView: TextView
    private lateinit var usernameTextView: TextView
    private lateinit var logoutButton: Button

    private lateinit var mAuth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_profile, container, false)

        // Initialize Firebase Auth and Firestore
        mAuth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Initialize views
        emailTextView = view.findViewById(R.id.email_text_view)
        usernameTextView = view.findViewById(R.id.username_text_view)
        logoutButton = view.findViewById(R.id.logout_button)

        // Set user info
        val currentUser = mAuth.currentUser
        currentUser?.let {
            emailTextView.text = it.email

            // Fetch user data from Firestore
            db.collection("users").document(it.uid)
                .get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val username = document.getString("name")
                        if (!username.isNullOrEmpty()) {
                            usernameTextView.text = username
                        } else {
                            usernameTextView.text = "User"
                        }
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(context, "Failed to load user data", Toast.LENGTH_SHORT).show()
                }
        }

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
}