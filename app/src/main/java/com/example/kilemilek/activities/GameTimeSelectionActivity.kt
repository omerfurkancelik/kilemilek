package com.example.kilemilek.activities

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.RadioButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.example.kilemilek.R
import com.google.android.material.button.MaterialButton

class GameTimeSelectionActivity : AppCompatActivity() {

    private var selectedTimeType: String = ""
    private var selectedTimeLimit: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game_time_selection)

        // Enable back button in action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Select Game Time"

        // Radio buttons
        val radio2min = findViewById<RadioButton>(R.id.radio_2min)
        val radio5min = findViewById<RadioButton>(R.id.radio_5min)
        val radio12hour = findViewById<RadioButton>(R.id.radio_12hour)
        val radio24hour = findViewById<RadioButton>(R.id.radio_24hour)

        // Cards (for better touch area)
        val card2min = findViewById<CardView>(R.id.card_2min)
        val card5min = findViewById<CardView>(R.id.card_5min)
        val card12hour = findViewById<CardView>(R.id.card_12hour)
        val card24hour = findViewById<CardView>(R.id.card_24hour)

        // Set up click listeners for cards
        card2min.setOnClickListener {
            selectTimeOption(radio2min, radio5min, radio12hour, radio24hour, "QUICK_2MIN", 2 * 60 * 1000L)
        }

        card5min.setOnClickListener {
            selectTimeOption(radio2min, radio5min, radio12hour, radio24hour, "QUICK_5MIN", 5 * 60 * 1000L)
        }

        card12hour.setOnClickListener {
            selectTimeOption(radio2min, radio5min, radio12hour, radio24hour, "EXTENDED_12HOUR", 12 * 60 * 60 * 1000L)
        }

        card24hour.setOnClickListener {
            selectTimeOption(radio2min, radio5min, radio12hour, radio24hour, "EXTENDED_24HOUR", 24 * 60 * 60 * 1000L)
        }

        // Set up click listeners for radio buttons as well
        radio2min.setOnClickListener { card2min.performClick() }
        radio5min.setOnClickListener { card5min.performClick() }
        radio12hour.setOnClickListener { card12hour.performClick() }
        radio24hour.setOnClickListener { card24hour.performClick() }

        // Continue button
        findViewById<MaterialButton>(R.id.button_play_with_friend).setOnClickListener {
            if (selectedTimeType.isEmpty()) {
                Toast.makeText(this, "Please select a game duration", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val intent = Intent(this, SelectFriendActivity::class.java)
            intent.putExtra("TIME_TYPE", selectedTimeType)
            intent.putExtra("TIME_LIMIT", selectedTimeLimit)
            startActivity(intent)
        }

        findViewById<MaterialButton>(R.id.button_random_match).setOnClickListener {
            if (selectedTimeType.isEmpty()) {
                Toast.makeText(this, "Please select a game duration", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val intent = Intent(this, RandomMatchmakingActivity::class.java)
            intent.putExtra("TIME_TYPE", selectedTimeType)
            intent.putExtra("TIME_LIMIT", selectedTimeLimit)
            startActivity(intent)
        }


    }

    private fun selectTimeOption(
        radio2min: RadioButton,
        radio5min: RadioButton,
        radio12hour: RadioButton,
        radio24hour: RadioButton,
        timeType: String,
        timeLimit: Long
    ) {
        // Reset all radio buttons
        radio2min.isChecked = false
        radio5min.isChecked = false
        radio12hour.isChecked = false
        radio24hour.isChecked = false

        // Set the selected one
        when (timeType) {
            "QUICK_2MIN" -> radio2min.isChecked = true
            "QUICK_5MIN" -> radio5min.isChecked = true
            "EXTENDED_12HOUR" -> radio12hour.isChecked = true
            "EXTENDED_24HOUR" -> radio24hour.isChecked = true
        }

        // Update selected values
        selectedTimeType = timeType
        selectedTimeLimit = timeLimit
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}