package com.gamebox.taki.domain

/**
 * Represents a player in a Taki game.
 * id is stable for the session (Host assigns it).
 */
data class TakiPlayer(
    val id: String,
    val name: String,
    val isHuman: Boolean,
    val isHost: Boolean = false,
    val hand: List<TakiCard> = emptyList()
) {
    val cardCount: Int get() = hand.size
}
