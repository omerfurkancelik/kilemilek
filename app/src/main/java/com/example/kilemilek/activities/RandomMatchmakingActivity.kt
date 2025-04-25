package com.example.kilemilek.activities

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.kilemilek.R
import com.example.kilemilek.models.GameData
import com.example.kilemilek.models.GameRequestModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class RandomMatchmakingActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var currentUserId: String
    private lateinit var currentUserName: String
    private lateinit var currentUserEmail: String

    private var selectedTimeType = ""
    private var selectedTimeLimit: Long = 0

    private var timeoutHandler: Handler? = null
    private var timeoutRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_random_matchmaking)

        db = FirebaseFirestore.getInstance()
        currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        currentUserEmail = FirebaseAuth.getInstance().currentUser?.email ?: ""

        selectedTimeType = intent.getStringExtra("TIME_TYPE") ?: ""
        selectedTimeLimit = intent.getLongExtra("TIME_LIMIT", 0L)

        db.collection("users").document(currentUserId).get().addOnSuccessListener { doc ->
            currentUserName = doc.getString("name") ?: "Unknown"
            checkOrCreateMatch()

            listenForMatchedGame()
        }
    }

    private fun checkOrCreateMatch() {
        val queueRef = db.collection("game_time_queue")

        // 1. İlk olarak sorguyu dışarıda yap
        queueRef
            .whereEqualTo("timeType", selectedTimeType)
            .whereNotEqualTo("userId", currentUserId)
            .orderBy("timestamp")
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    val opponentDoc = snapshot.documents[0]
                    val opponentRef = opponentDoc.reference
                    val opponentId = opponentDoc.getString("userId") ?: return@addOnSuccessListener
                    val opponentName = opponentDoc.getString("userName") ?: "Opponent"

                    // 2. Transaction başlat (rakibi eşleştir ve sil)
                    db.runTransaction { transaction ->
                        val opponentSnapshot = transaction.get(opponentRef)

                        if (!opponentSnapshot.exists()) {
                            return@runTransaction false // Rakip bu sırada eşleşmiş olabilir
                        }

                        transaction.delete(opponentRef)

                        val currentTime = System.currentTimeMillis()
                        val moveDeadline = currentTime + selectedTimeLimit

                        val newGameRef = db.collection("game_requests").document()

                        val game = GameRequestModel(
                            id = newGameRef.id,
                            senderId = currentUserId,
                            senderName = currentUserName,
                            senderEmail = currentUserEmail,
                            receiverId = opponentId,
                            receiverName = opponentName,
                            receiverEmail = "match@example.com",
                            status = "pending",
                            createdAt = currentTime,
                            lastUpdatedAt = currentTime,
                            gameTimeType = selectedTimeType,
                            moveDeadline = moveDeadline,
                            gameData = GameData(
                                playerTurn = currentUserId,
                                playerScores = mapOf(
                                    currentUserId to 0,
                                    opponentId to 0
                                ),
                                timeLimit = selectedTimeLimit,
                                timeType = selectedTimeType
                            )
                        )

                        transaction.set(newGameRef, game)
                        true
                    }.addOnSuccessListener { matched ->
                        if (matched) {
                            Toast.makeText(this, "🎮 Opponent matched!", Toast.LENGTH_SHORT).show()
                            startActivity(Intent(this, ActiveGamesActivity::class.java))
                            finish()
                        }
                    }.addOnFailureListener { e ->
                        Toast.makeText(this, "❌ Error in transaction: ${e.message}", Toast.LENGTH_LONG).show()
                        finish()
                    }

                } else {
                    // 3. Kuyrukta rakip yoksa, kendini ekle
                    val waitingPlayer = mapOf(
                        "userId" to currentUserId,
                        "userName" to currentUserName,
                        "timeType" to selectedTimeType,
                        "timeLimit" to selectedTimeLimit,
                        "timestamp" to System.currentTimeMillis()
                    )

                    db.collection("game_time_queue")
                        .add(waitingPlayer)
                        .addOnSuccessListener {
                            Toast.makeText(this, "⏳ Added to queue. Waiting...", Toast.LENGTH_SHORT).show()
                            listenForMatchedGame()

                            timeoutHandler = Handler(mainLooper)
                            timeoutRunnable = Runnable {
                                Toast.makeText(this, "❌ No opponent found. Try again later.", Toast.LENGTH_LONG).show()
                                finish()
                            }
                            timeoutHandler?.postDelayed(timeoutRunnable!!, 30000)
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "❌ Failed to join queue: ${e.message}", Toast.LENGTH_LONG).show()
                            finish()
                        }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "❌ Error fetching queue: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }


    private fun listenForMatchedGame() {
        db.collection("game_requests")
            .whereEqualTo("receiverId", currentUserId)
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Toast.makeText(this, "Snapshot error: ${error.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snapshots != null && !snapshots.isEmpty) {
                    val matchedGame = snapshots.documents[0]
                    val gameId = matchedGame.id

                    Toast.makeText(this, "Match found! Starting game...", Toast.LENGTH_SHORT).show()

                    // Oyuna yönlendir
                    val intent = Intent(this, ActiveGamesActivity::class.java)
                    startActivity(intent)
                    finish()

                }
            }
    }


    override fun onDestroy() {
        super.onDestroy()
        timeoutHandler?.removeCallbacks(timeoutRunnable!!)
    }
}
