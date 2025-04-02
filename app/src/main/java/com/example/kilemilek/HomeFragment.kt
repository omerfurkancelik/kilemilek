package com.example.kilemilek

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth

class HomeFragment : Fragment() {

    private lateinit var welcomeText: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        welcomeText = view.findViewById(R.id.welcome_text)

        // Get current user email
        val currentUser = FirebaseAuth.getInstance().currentUser
        currentUser?.let {
            welcomeText.text = "Welcome to Kilemilek\n${it.email}"
        }

        return view
    }
}