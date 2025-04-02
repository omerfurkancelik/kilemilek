package com.example.kilemilek.activities

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.kilemilek.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class RegisterActivity : AppCompatActivity() {
    private lateinit var nameLayout: TextInputLayout
    private lateinit var emailLayout: TextInputLayout
    private lateinit var passwordLayout: TextInputLayout
    private lateinit var confirmPasswordLayout: TextInputLayout
    private lateinit var nameEditText: TextInputEditText
    private lateinit var emailEditText: TextInputEditText
    private lateinit var passwordEditText: TextInputEditText
    private lateinit var confirmPasswordEditText: TextInputEditText
    private lateinit var registerButton: MaterialButton
    private lateinit var loginTextView: android.widget.TextView
    private lateinit var progressBar: android.widget.ProgressBar
    private lateinit var mAuth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        // Initialize Firebase Auth and Firestore
        mAuth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Initialize UI elements
        nameLayout = findViewById(R.id.name_input_layout)
        emailLayout = findViewById(R.id.email_input_layout)
        passwordLayout = findViewById(R.id.password_input_layout)
        confirmPasswordLayout = findViewById(R.id.confirm_password_input_layout)
        nameEditText = findViewById(R.id.name_edit_text)
        emailEditText = findViewById(R.id.email_edit_text)
        passwordEditText = findViewById(R.id.password_edit_text)
        confirmPasswordEditText = findViewById(R.id.confirm_password_edit_text)
        registerButton = findViewById(R.id.register_button)
        loginTextView = findViewById(R.id.login_text_view)
        progressBar = findViewById(R.id.progress_bar)

        // Set up register button click listener
        registerButton.setOnClickListener {
            registerUser()
        }

        // Set up login text view click listener
        loginTextView.setOnClickListener {
            onBackPressed()
        }
    }

    private fun registerUser() {
        // Get input values
        val name = nameEditText.text.toString().trim()
        val email = emailEditText.text.toString().trim()
        val password = passwordEditText.text.toString().trim()
        val confirmPassword = confirmPasswordEditText.text.toString().trim()

        // Validate input
        if (TextUtils.isEmpty(name)) {
            nameLayout.error = "Name is required"
            return
        } else {
            nameLayout.error = null
        }

        if (TextUtils.isEmpty(email)) {
            emailLayout.error = "Email is required"
            return
        } else {
            emailLayout.error = null
        }

        if (TextUtils.isEmpty(password)) {
            passwordLayout.error = "Password is required"
            return
        } else {
            passwordLayout.error = null
        }

        if (password.length < 6) {
            passwordLayout.error = "Password must be at least 6 characters"
            return
        } else {
            passwordLayout.error = null
        }

        if (TextUtils.isEmpty(confirmPassword)) {
            confirmPasswordLayout.error = "Confirm password is required"
            return
        } else {
            confirmPasswordLayout.error = null
        }

        if (password != confirmPassword) {
            confirmPasswordLayout.error = "Passwords do not match"
            return
        } else {
            confirmPasswordLayout.error = null
        }

        // Show progress bar
        progressBar.visibility = View.VISIBLE

        // Create user with email and password
        mAuth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Get the current user ID
                    val userId = mAuth.currentUser?.uid

                    if (userId != null) {
                        // Create user profile in Firestore
                        val user = hashMapOf(
                            "userId" to userId,
                            "email" to email,
                            "name" to name,
                            "profileImageUrl" to "",
                            "gamesPlayed" to 0,
                            "wordsFound" to 0,
                            "createdAt" to System.currentTimeMillis()
                        )

                        // Add user to Firestore
                        db.collection("users").document(userId)
                            .set(user)
                            .addOnSuccessListener {
                                // Hide progress bar
                                progressBar.visibility = View.GONE

                                // Registration and profile creation successful
                                Toast.makeText(this@RegisterActivity, "Registration successful", Toast.LENGTH_SHORT).show()
                                val intent = Intent(this@RegisterActivity, MainActivity::class.java)
                                startActivity(intent)
                                finish()
                            }
                            .addOnFailureListener { e ->
                                // Hide progress bar
                                progressBar.visibility = View.GONE

                                // If profile creation fails, display a message to the user
                                Toast.makeText(
                                    this@RegisterActivity,
                                    "Profile creation failed: " + e.message,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                    } else {
                        // Hide progress bar
                        progressBar.visibility = View.GONE

                        // If user ID is null, display a message
                        Toast.makeText(
                            this@RegisterActivity,
                            "Registration error: User ID is null",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    // Hide progress bar
                    progressBar.visibility = View.GONE

                    // If sign up fails, display a message to the user
                    Toast.makeText(
                        this@RegisterActivity,
                        "Registration failed: " + task.exception?.message,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }
}