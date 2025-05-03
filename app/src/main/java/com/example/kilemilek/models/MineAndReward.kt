package com.example.kilemilek.models

import android.util.Log
import com.example.kilemilek.objects.GameBoardMatrix
import kotlin.random.Random

// Mine and Reward types
enum class MinePowerupType {
    // Mines
    SCORE_SPLIT,       // Player gets only 30% of points
    POINT_TRANSFER,    // Points go to opponent
    LETTER_LOSS,       // Player loses current letters and gets 7 new ones
    EXTRA_MOVE_BARRIER,// Cancels special tile multipliers
    WORD_CANCELLATION, // Player gets 0 points

    // Rewards
    REGION_BAN,        // Opponent can only place on half the board
    LETTER_BAN,        // Freezes 2 of opponent's letters for 1 turn
    EXTRA_MOVE         // Player gets extra turn
}

// Direction for Region Ban
enum class RegionBanDirection {
    LEFT_BANNED,       // Right side only allowed
    RIGHT_BANNED       // Left side only allowed
}

// Class to represent a mine or reward at a specific position
data class BoardPowerup(
    val type: MinePowerupType,
    val position: Pair<Int, Int>, // row, col
    val isVisible: Boolean = false,
    val isActive: Boolean = true
)

// Class to manage all mines and rewards on the board
class PowerupManager {
    companion object {
        private const val TAG = "PowerupManager"

        // Quantities of different mine/reward types
        private val POWERUP_QUANTITIES = mapOf(
            MinePowerupType.SCORE_SPLIT to 5,
            MinePowerupType.POINT_TRANSFER to 4,
            MinePowerupType.LETTER_LOSS to 3,
            MinePowerupType.EXTRA_MOVE_BARRIER to 2,
            MinePowerupType.WORD_CANCELLATION to 2,
            MinePowerupType.REGION_BAN to 2,
            MinePowerupType.LETTER_BAN to 3,
            MinePowerupType.EXTRA_MOVE to 2
        )
    }

    // List of all powerups on the board
    private val powerups = mutableListOf<BoardPowerup>()

    // Map of positions to powerups for quick lookup
    private val powerupPositions = mutableMapOf<Pair<Int, Int>, BoardPowerup>()

    // List of active player powerups (rewards that can be used)
    private val playerPowerups = mutableMapOf<String, MutableList<MinePowerupType>>()

    // List of banned letters for each player
    private val bannedLetters = mutableMapOf<String, MutableList<Char>>()

    // Region ban status for each player
    private val regionBans = mutableMapOf<String, RegionBanDirection?>()

    /**
     * Generate random mines and rewards on the board
     * @return List of BoardPowerup objects
     */
    fun generatePowerups(): List<BoardPowerup> {
        // Clear existing powerups
        powerups.clear()
        powerupPositions.clear()

        // Get all possible positions (avoid center and special tiles)
        val validPositions = mutableListOf<Pair<Int, Int>>()
        for (row in 0 until GameBoardMatrix.BOARD_SIZE) {
            for (col in 0 until GameBoardMatrix.BOARD_SIZE) {
                val tileType = GameBoardMatrix.getTileType(row, col)
                val isCenter = (row == GameBoardMatrix.BOARD_SIZE / 2 && col == GameBoardMatrix.BOARD_SIZE / 2)

                // Skip center and special tiles
                if (!isCenter && tileType == GameBoardMatrix.NORMAL) {
                    validPositions.add(Pair(row, col))
                }
            }
        }

        // Shuffle positions to randomize
        validPositions.shuffle()

        // Generate powerups based on quantities
        var positionIndex = 0
        POWERUP_QUANTITIES.forEach { (type, quantity) ->
            repeat(quantity) {
                if (positionIndex < validPositions.size) {
                    val position = validPositions[positionIndex++]
                    val powerup = BoardPowerup(type, position)
                    powerups.add(powerup)
                    powerupPositions[position] = powerup
                }
            }
        }

        return powerups.toList()
    }

    /**
     * Check if a position has a powerup
     * @param row Row position
     * @param col Column position
     * @return BoardPowerup if present, null otherwise
     */
    fun getPowerupAt(row: Int, col: Int): BoardPowerup? {
        return powerupPositions[Pair(row, col)]
    }

    /**
     * Process a mine effect when a player places a letter on it
     * @param powerup The powerup that was triggered
     * @param playerId ID of the player who triggered it
     * @param opponentId ID of the opponent
     * @param word The word that was played
     * @param basePoints The base points of the word (before any multipliers)
     * @param currentLetters Current letters in player's rack
     * @return PowerupResult containing the updated score and any side effects
     */
    fun processMineEffect(
        powerup: BoardPowerup,
        playerId: String,
        opponentId: String,
        word: String,
        basePoints: Int,
        currentLetters: List<Char>
    ): PowerupResult {
        if (!powerup.isActive) {
            return PowerupResult(basePoints, emptyList(), null, false)
        }

        // Mark powerup as used
        powerupPositions[powerup.position] = powerup.copy(isActive = false)

        // Process effect based on mine type
        return when (powerup.type) {
            MinePowerupType.SCORE_SPLIT -> {
                val reducedPoints = (basePoints * 0.3).toInt()
                PowerupResult(reducedPoints, emptyList(), "Score split! You only get 30% of points (${reducedPoints})", false)
            }

            MinePowerupType.POINT_TRANSFER -> {
                PowerupResult(0, emptyList(), "Point transfer! Your ${basePoints} points go to opponent", true, basePoints)
            }

            MinePowerupType.LETTER_LOSS -> {
                PowerupResult(basePoints, emptyList(), "Letter loss! All your letters will be replaced", false, 0, true)
            }

            MinePowerupType.EXTRA_MOVE_BARRIER -> {
                PowerupResult(basePoints, emptyList(), "Extra move barrier! Special tile multipliers ignored", false, 0, false, true)
            }

            MinePowerupType.WORD_CANCELLATION -> {
                PowerupResult(0, emptyList(), "Word cancellation! You get 0 points for this word", false)
            }

            // If it's a reward, add it to player's inventory
            MinePowerupType.REGION_BAN, MinePowerupType.LETTER_BAN, MinePowerupType.EXTRA_MOVE -> {
                val playerRewards = playerPowerups.getOrPut(playerId) { mutableListOf() }
                playerRewards.add(powerup.type)
                PowerupResult(basePoints, listOf(powerup.type), "You found a powerup: ${powerup.type.name}!", false)
            }
        }
    }

    /**
     * Use a player's powerup
     * @param powerupType Type of powerup to use
     * @param playerId ID of the player using the powerup
     * @param opponentId ID of the opponent
     * @param opponentLetters Opponent's current letters (for letter ban)
     * @return PowerupUseResult containing the effect of using the powerup
     */
    fun usePlayerPowerup(
        powerupType: MinePowerupType,
        playerId: String,
        opponentId: String,
        opponentLetters: List<Char>
    ): PowerupUseResult {
        val playerRewards = playerPowerups.getOrPut(playerId) { mutableListOf() }

        // Check if player has this powerup
        if (!playerRewards.contains(powerupType)) {
            return PowerupUseResult(false, "You don't have this powerup")
        }

        // Remove one instance of this powerup from player's inventory
        playerRewards.remove(powerupType)

        // Apply powerup effect
        when (powerupType) {
            MinePowerupType.REGION_BAN -> {
                // Randomly choose left or right ban
                val direction = if (Random.nextBoolean())
                    RegionBanDirection.LEFT_BANNED else RegionBanDirection.RIGHT_BANNED

                // Apply region ban to opponent
                regionBans[opponentId] = direction

                return PowerupUseResult(
                    true,
                    "Region ban applied! Opponent can only place letters on the " +
                            if (direction == RegionBanDirection.LEFT_BANNED) "right side" else "left side"
                )
            }

            MinePowerupType.LETTER_BAN -> {
                if (opponentLetters.size < 2) {
                    // Return powerup if opponent doesn't have enough letters
                    playerRewards.add(powerupType)
                    return PowerupUseResult(false, "Opponent doesn't have enough letters to ban")
                }

                // Randomly select 2 letters to ban
                val lettersToBan = opponentLetters.shuffled().take(2)
                val opponentBannedLetters = bannedLetters.getOrPut(opponentId) { mutableListOf() }
                opponentBannedLetters.addAll(lettersToBan)

                return PowerupUseResult(true, "Letter ban applied! Opponent's letters ${lettersToBan.joinToString()} are frozen for 1 turn")
            }

            MinePowerupType.EXTRA_MOVE -> {
                return PowerupUseResult(true, "Extra move granted! You get another turn after this one")
            }

            else -> {
                // Should never happen - return the powerup
                playerRewards.add(powerupType)
                return PowerupUseResult(false, "Cannot use this type of powerup")
            }
        }
    }

    /**
     * Check if a letter is banned for a player
     * @param playerId ID of the player
     * @param letter Letter to check
     * @return True if the letter is banned, false otherwise
     */
    fun isLetterBanned(playerId: String, letter: Char): Boolean {
        return bannedLetters[playerId]?.contains(letter) ?: false
    }

    /**
     * Check if a position is banned due to region ban
     * @param playerId ID of the player
     * @param col Column position to check
     * @return True if the position is banned, false otherwise
     */
    fun isPositionBanned(playerId: String, col: Int): Boolean {
        val ban = regionBans[playerId] ?: return false
        val centerCol = GameBoardMatrix.BOARD_SIZE / 2

        return when (ban) {
            RegionBanDirection.LEFT_BANNED -> col <= centerCol
            RegionBanDirection.RIGHT_BANNED -> col > centerCol
        }
    }

    /**
     * Clear banned letters for a player after their turn
     * @param playerId ID of the player
     */
    fun clearBannedLetters(playerId: String) {
        bannedLetters[playerId]?.clear()
    }

    /**
     * Clear region ban for a player after their turn
     * @param playerId ID of the player
     */
    fun clearRegionBan(playerId: String) {
        regionBans.remove(playerId)
    }

    /**
     * Check if a player has an extra move powerup
     * @param playerId ID of the player
     * @return True if the player has an extra move, false otherwise
     */
    fun hasExtraMove(playerId: String): Boolean {
        return playerPowerups[playerId]?.contains(MinePowerupType.EXTRA_MOVE) ?: false
    }

    /**
     * Get the list of powerups a player has
     * @param playerId ID of the player
     * @return List of powerup types the player has
     */
    fun getPlayerPowerups(playerId: String): List<MinePowerupType> {
        return playerPowerups[playerId]?.toList() ?: emptyList()
    }

    /**
     * Convert the powerup data to a map for Firestore storage
     * @return Map representation of powerups data
     */
    fun toFirestoreMap(): Map<String, Any> {
        val powerupsData = powerups.map {
            mapOf(
                "type" to it.type.name,
                "position" to "${it.position.first},${it.position.second}",
                "isVisible" to it.isVisible,
                "isActive" to it.isActive
            )
        }

        val playerPowerupsData = playerPowerups.mapValues { entry ->
            entry.value.map { it.name }
        }

        val bannedLettersData = bannedLetters.mapValues { entry ->
            entry.value.map { it.toString() }
        }

        val regionBansData = regionBans.mapValues { entry ->
            entry.value?.name
        }

        return mapOf(
            "powerups" to powerupsData,
            "playerPowerups" to playerPowerupsData,
            "bannedLetters" to bannedLettersData,
            "regionBans" to regionBansData
        )
    }

    /**
     * Load powerup data from Firestore
     * @param data Map representation of powerups data
     */
    fun loadFromFirestore(data: Map<String, Any>) {
        // Clear existing data
        powerups.clear()
        powerupPositions.clear()
        playerPowerups.clear()
        bannedLetters.clear()
        regionBans.clear()

        // Load powerups
        val powerupsData = data["powerups"] as? List<Map<String, Any>> ?: return
        for (powerupData in powerupsData) {
            try {
                val typeStr = powerupData["type"] as String
                val positionStr = powerupData["position"] as String
                val isVisible = powerupData["isVisible"] as Boolean
                val isActive = powerupData["isActive"] as Boolean

                val type = MinePowerupType.valueOf(typeStr)
                val positionParts = positionStr.split(",")
                val position = Pair(positionParts[0].toInt(), positionParts[1].toInt())

                val powerup = BoardPowerup(type, position, isVisible, isActive)
                powerups.add(powerup)
                powerupPositions[position] = powerup
            } catch (e: Exception) {
                Log.e(TAG, "Error loading powerup: ${e.message}")
            }
        }

        // Load player powerups
        val playerPowerupsData = data["playerPowerups"] as? Map<String, List<String>> ?: return
        for ((playerId, powerupTypes) in playerPowerupsData) {
            playerPowerups[playerId] = powerupTypes.mapNotNull {
                try {
                    MinePowerupType.valueOf(it)
                } catch (e: Exception) {
                    null
                }
            }.toMutableList()
        }

        // Load banned letters
        val bannedLettersData = data["bannedLetters"] as? Map<String, List<String>> ?: return
        for ((playerId, letters) in bannedLettersData) {
            bannedLetters[playerId] = letters.mapNotNull {
                if (it.isNotEmpty()) it[0] else null
            }.toMutableList()
        }

        // Load region bans
        val regionBansData = data["regionBans"] as? Map<String, String?> ?: return
        for ((playerId, banDirectionStr) in regionBansData) {
            regionBans[playerId] = banDirectionStr?.let { RegionBanDirection.valueOf(it) }
        }
    }
}

/**
 * Result of triggering a mine or picking up a reward
 */
data class PowerupResult(
    val updatedPoints: Int,                // Updated points after applying effect
    val rewardsGained: List<MinePowerupType>, // Rewards gained (if any)
    val message: String?,                  // Message to display to player
    val transferPointsToOpponent: Boolean, // Whether to transfer points to opponent
    val pointsToTransfer: Int = 0,         // Points to transfer to opponent
    val replaceLetters: Boolean = false,   // Whether to replace player's letters
    val ignoreMultipliers: Boolean = false // Whether to ignore special tile multipliers
)

/**
 * Result of using a powerup
 */
data class PowerupUseResult(
    val success: Boolean,  // Whether the powerup was used successfully
    val message: String    // Message to display to player
)