package com.gamebox.taki.domain

/**
 * Full authoritative game state (lives only on the Host).
 */
data class TakiState(
    val players: List<TakiPlayer>,
    val currentPlayerIndex: Int,
    val direction: Direction = Direction.CLOCKWISE,
    val topCard: TakiCard,
    val currentColor: TakiColor,          // after Change Color this can differ from topCard.color
    val drawPile: List<TakiCard>,
    val discardPile: List<TakiCard>,      // includes topCard as last element
    val pendingDraw: Int = 0,             // accumulated +2 / +3
    val isTakiOpen: Boolean = false,      // currently inside a Taki sequence
    val takiColor: TakiColor? = null,     // color of the open Taki
    val winnerId: String? = null,
    val isGameOver: Boolean = false,
    val lastActionMessage: String = ""
) {
    val currentPlayer: TakiPlayer get() = players[currentPlayerIndex]

    fun publicState(): TakiPublicState = TakiPublicState(
        players = players.map { TakiPublicPlayer(it.id, it.name, it.cardCount, it.isHost) },
        currentPlayerId = currentPlayer.id,
        direction = direction,
        topCard = topCard,
        currentColor = currentColor,
        pendingDraw = pendingDraw,
        isTakiOpen = isTakiOpen,
        winnerId = winnerId,
        isGameOver = isGameOver,
        lastActionMessage = lastActionMessage
    )

    fun privateStateFor(playerId: String): TakiPrivateState {
        val player = players.first { it.id == playerId }
        return TakiPrivateState(
            hand = player.hand,
            canDraw = pendingDraw == 0 && !isTakiOpen,
            mustDraw = pendingDraw > 0,
            isMyTurn = currentPlayer.id == playerId && !isGameOver
        )
    }
}

enum class Direction { CLOCKWISE, COUNTER_CLOCKWISE }

/** What every client + external display may see */
data class TakiPublicState(
    val players: List<TakiPublicPlayer>,
    val currentPlayerId: String,
    val direction: Direction,
    val topCard: TakiCard,
    val currentColor: TakiColor,
    val pendingDraw: Int,
    val isTakiOpen: Boolean,
    val winnerId: String?,
    val isGameOver: Boolean,
    val lastActionMessage: String
)

data class TakiPublicPlayer(
    val id: String,
    val name: String,
    val cardCount: Int,
    val isHost: Boolean
)

/** What only one player may see */
data class TakiPrivateState(
    val hand: List<TakiCard>,
    val canDraw: Boolean,
    val mustDraw: Boolean,
    val isMyTurn: Boolean
)
