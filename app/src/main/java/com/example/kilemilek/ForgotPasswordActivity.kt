package com.example.kilemilek

import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth

class ForgotPasswordActivity : AppCompatActivity() {
    private lateinit var emailLayout: TextInputLayout
    private lateinit var emailEditText: TextInputEditText
    private lateinit var resetPasswordButton: MaterialButton
    private lateinit var backToLoginTextView: android.widget.TextView
    private lateinit var progressBar: android.widget.ProgressBar
    private lateinit var mAuth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance()

        // Initialize UI elements
        emailLayout = findViewById(R.id.email_input_layout)
        emailEditText = findViewById(R.id.email_edit_text)
        resetPasswordButton = findViewById(R.id.reset_password_button)
        backToLoginTextView = findViewById(R.id.back_to_login_text_view)
        progressBar = findViewById(R.id.progress_bar)

        // Set up reset password button click listener
        resetPasswordButton.setOnClickListener {
            resetPassword()
        }

        // Set up back to login text view click listener
        backToLoginTextView.setOnClickListener {
            onBackPressed()
        }
    }

    private fun resetPassword() {
        // Get input values
        val email = emailEditText.text.toString().trim()

        // Validate input
        if (TextUtils.isEmpty(email)) {
            emailLayout.error = "Email is required"
            return
        } else {
            emailLayout.error = null
        }

        // Show progress bar
        progressBar.visibility = View.VISIBLE

        // Send password reset email
        mAuth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                // Hide progress bar
                progressBar.visibility = View.GONE

                if (task.isSuccessful) {
                    Toast.makeText(this@ForgotPasswordActivity, "Password reset email sent", Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    Toast.makeText(
                        this@ForgotPasswordActivity,
                        "Failed to send password reset email: " + task.exception?.message,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }
}