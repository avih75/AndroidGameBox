package com.gamebox.taki.domain

import kotlin.random.Random

/**
 * Pure Taki rules engine (no Android).
 * Host runs this. Clients never call it.
 *
 * Simplified rules (v1):
 * - Numbers 1-9, +2, Stop, Reverse, Change Color, Taki, +3
 * - No Super Taki
 * - Taki opens a sequence of same color; player closes it when done
 * - +2 / +3 stack
 * - Must draw when pendingDraw > 0 (or play another +2/+3)
 */
class TakiEngine(
    private val random: Random = Random.Default
) {

    fun newGame(
        playerNames: List<String>,
        humanPlayerIds: Set<String> = emptySet(),
        hostPlayerId: String
    ): TakiState {
        require(playerNames.size in 2..6) { "Taki supports 2-6 players" }

        val deck = createShuffledDeck()
        val hands = mutableListOf<List<TakiCard>>()
        var remaining = deck

        // Deal 8 cards each (common Israeli house rule; can be changed later)
        repeat(playerNames.size) {
            hands += remaining.take(8)
            remaining = remaining.drop(8)
        }

        // First non-action card as starting top card
        var top: TakiCard
        do {
            top = remaining.first()
            remaining = remaining.drop(1)
        } while (top.isAction())

        val players = playerNames.mapIndexed { index, name ->
            val id = "p$index"
            TakiPlayer(
                id = id,
                name = name,
                isHuman = id in humanPlayerIds || (humanPlayerIds.isEmpty() && index == 0),
                isHost = id == hostPlayerId,
                hand = hands[index]
            )
        }

        return TakiState(
            players = players,
            currentPlayerIndex = 0,
            direction = Direction.CLOCKWISE,
            topCard = top,
            currentColor = top.color!!,
            drawPile = remaining,
            discardPile = listOf(top),
            lastActionMessage = "המשחק התחיל"
        )
    }

    fun apply(state: TakiState, playerId: String, action: TakiAction): TakiState {
        if (state.isGameOver) return state
        if (state.currentPlayer.id != playerId) return state // not your turn

        return when (action) {
            is TakiAction.PlayCard -> playCard(state, action.card, action.chosenColor)
            TakiAction.Draw -> draw(state)
            TakiAction.CloseTaki -> closeTaki(state)
            TakiAction.DeclareLastCard -> state // later
        }
    }

    // ---------- Rules ----------

    private fun playCard(state: TakiState, card: TakiCard, chosenColor: TakiColor?): TakiState {
        val player = state.currentPlayer
        if (card !in player.hand) return state

        // Must respond to pending draw with another +2 / +3 or draw
        if (state.pendingDraw > 0) {
            val isPlus = card.rank == TakiRank.PLUS_TWO || card.rank == TakiRank.PLUS_THREE
            if (!isPlus) return state
        }

        if (!isLegalPlay(state, card)) return state

        var newState = state
            .removeCardFromCurrentPlayer(card)
            .addToDiscard(card)

        // Resolve card effect
        newState = when (card.rank) {
            TakiRank.PLUS_TWO -> newState.copy(
                pendingDraw = newState.pendingDraw + 2,
                currentColor = card.color!!,
                lastActionMessage = "${player.name} שם +2"
            )
            TakiRank.PLUS_THREE -> newState.copy(
                pendingDraw = newState.pendingDraw + 3,
                currentColor = card.color!!,
                lastActionMessage = "${player.name} שם +3"
            )
            TakiRank.STOP -> newState.copy(
                currentColor = card.color!!,
                lastActionMessage = "${player.name} שם עצור"
            ).advanceTurn().advanceTurn() // skip next
            TakiRank.CHANGE_DIRECTION -> {
                val newDir = if (newState.direction == Direction.CLOCKWISE)
                    Direction.COUNTER_CLOCKWISE else Direction.CLOCKWISE
                newState.copy(
                    direction = newDir,
                    currentColor = card.color!!,
                    lastActionMessage = "${player.name} שינה כיוון"
                ).advanceTurn()
            }
            TakiRank.CHANGE_COLOR -> {
                val color = chosenColor ?: TakiColor.RED
                newState.copy(
                    currentColor = color,
                    lastActionMessage = "${player.name} שינה צבע ל-$color"
                ).advanceTurn()
            }
            TakiRank.TAKI -> newState.copy(
                isTakiOpen = true,
                takiColor = card.color,
                currentColor = card.color!!,
                lastActionMessage = "${player.name} פתח טאקי"
            )
            // number cards
            else -> {
                if (newState.isTakiOpen) {
                    // still inside Taki – same color only, turn does not advance yet
                    newState.copy(
                        currentColor = card.color!!,
                        lastActionMessage = "${player.name} שם ${card.displayName()}"
                    )
                } else {
                    newState.copy(
                        currentColor = card.color!!,
                        lastActionMessage = "${player.name} שם ${card.displayName()}"
                    ).advanceTurn()
                }
            }
        }

        // Win check
        val updatedPlayer = newState.players.first { it.id == player.id }
        if (updatedPlayer.hand.isEmpty()) {
            return newState.copy(
                isGameOver = true,
                winnerId = player.id,
                lastActionMessage = "${player.name} ניצח!"
            )
        }

        return newState
    }

    private fun draw(state: TakiState): TakiState {
        if (state.isTakiOpen) return state // must close or play

        val amount = if (state.pendingDraw > 0) state.pendingDraw else 1
        var (newDrawPile, drawn) = drawCards(state.drawPile, state.discardPile, amount)

        val newPlayers = state.players.map { p ->
            if (p.id == state.currentPlayer.id) p.copy(hand = p.hand + drawn) else p
        }

        return state.copy(
            players = newPlayers,
            drawPile = newDrawPile,
            pendingDraw = 0,
            lastActionMessage = "${state.currentPlayer.name} משך $amount קלפים"
        ).advanceTurn()
    }

    private fun closeTaki(state: TakiState): TakiState {
        if (!state.isTakiOpen) return state
        return state.copy(
            isTakiOpen = false,
            takiColor = null,
            lastActionMessage = "${state.currentPlayer.name} סגר טאקי"
        ).advanceTurn()
    }

    private fun isLegalPlay(state: TakiState, card: TakiCard): Boolean {
        if (state.isTakiOpen) {
            // only same color as the open Taki
            return card.color == state.takiColor && card.rank != TakiRank.CHANGE_COLOR
        }

        if (card.rank == TakiRank.CHANGE_COLOR) return true
        if (card.color == state.currentColor) return true
        if (card.rank == state.topCard.rank && card.rank != TakiRank.CHANGE_COLOR) return true
        return false
    }

    // ---------- Helpers ----------

    private fun TakiState.removeCardFromCurrentPlayer(card: TakiCard): TakiState {
        val newPlayers = players.map { p ->
            if (p.id == currentPlayer.id) {
                val idx = p.hand.indexOfFirst { it == card }
                if (idx >= 0) p.copy(hand = p.hand.toMutableList().apply { removeAt(idx) })
                else p
            } else p
        }
        return copy(players = newPlayers)
    }

    private fun TakiState.addToDiscard(card: TakiCard): TakiState =
        copy(topCard = card, discardPile = discardPile + card)

    private fun TakiState.advanceTurn(): TakiState {
        val delta = if (direction == Direction.CLOCKWISE) 1 else -1
        val next = (currentPlayerIndex + delta + players.size) % players.size
        return copy(currentPlayerIndex = next)
    }

    private fun drawCards(
        drawPile: List<TakiCard>,
        discardPile: List<TakiCard>,
        amount: Int
    ): Pair<List<TakiCard>, List<TakiCard>> {
        var pile = drawPile
        val drawn = mutableListOf<TakiCard>()
        repeat(amount) {
            if (pile.isEmpty()) {
                // reshuffle discard (leave top card)
                val top = discardPile.last()
                pile = discardPile.dropLast(1).shuffled(random)
            }
            if (pile.isNotEmpty()) {
                drawn += pile.first()
                pile = pile.drop(1)
            }
        }
        return pile to drawn
    }

    private fun createShuffledDeck(): List<TakiCard> {
        val cards = mutableListOf<TakiCard>()
        val colors = TakiColor.entries

        for (color in colors) {
            // numbers 1-9 (two of each is common; we use one for simplicity in v1)
            for (r in listOf(
                TakiRank.ONE, TakiRank.TWO, TakiRank.THREE, TakiRank.FOUR,
                TakiRank.FIVE, TakiRank.SIX, TakiRank.SEVEN, TakiRank.EIGHT, TakiRank.NINE
            )) {
                cards += TakiCard(color, r)
                cards += TakiCard(color, r) // two copies
            }
            // actions
            cards += TakiCard(color, TakiRank.PLUS_TWO)
            cards += TakiCard(color, TakiRank.PLUS_TWO)
            cards += TakiCard(color, TakiRank.STOP)
            cards += TakiCard(color, TakiRank.STOP)
            cards += TakiCard(color, TakiRank.CHANGE_DIRECTION)
            cards += TakiCard(color, TakiRank.TAKI)
            cards += TakiCard(color, TakiRank.PLUS_THREE)
        }
        // wilds
        repeat(4) { cards += TakiCard(null, TakiRank.CHANGE_COLOR) }

        return cards.shuffled(random)
    }
}
