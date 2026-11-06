package com.gamebox.taki.domain

/**
 * Multiplayer configuration for Taki (matches MULTIPLAYER.md).
 */
object TakiMultiplayerConfig {
    const val MIN_PLAYERS = 2
    const val MAX_PLAYERS = 6

    /** Host can sit out and only moderate? For Taki we want Host to be able to play. */
    const val HOST_CAN_BE_SPECTATOR = false

    /** Host must be an active player in the first version. */
    const val HOST_MUST_PLAY = true
}
