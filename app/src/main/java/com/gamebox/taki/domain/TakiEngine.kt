package com.gamebox.taki.domain

import kotlin.random.Random

/**
 * Pure Taki rules engine (no Android).
 * Host runs this. Clients never call it.
 *
 * Simplified rules (v1):
 * - Numbers 1-9, +2, +, Stop, Reverse, Change Color, Taki
 * - No Super Taki
 * - Taki opens a sequence of same color; player closes it when done
 * - +2 stacks and passes the draw penalty to the next player
 * - Must draw when pendingDraw > 0 (or stack another +2)
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
            lastActionMessage = "המשחק התחיל",
            actionHistory = listOf("המשחק התחיל")
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
        if (card.rank == TakiRank.PLUS && !player.hand.any {
                it != card && it.color == card.color && it.rank != TakiRank.CHANGE_COLOR
            } && player.hand.count { it == card } < 2
        ) return state

        // A draw penalty can only be passed on with another +2.
        if (state.pendingDraw > 0) {
            if (card.rank != TakiRank.PLUS_TWO) return state
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
                isTakiOpen = false,
                takiColor = null,
                isPlusOpen = false,
                plusColor = null
            ).withMessage("${player.name} שם +2 — ${newState.pendingDraw} קלפים ממתינים לתור הבא")
                .advanceTurn()
            TakiRank.STOP -> newState.copy(
                currentColor = card.color!!,
                isTakiOpen = false,
                takiColor = null,
                isPlusOpen = false,
                plusColor = null
            ).withMessage("${player.name} שם עצור — השחקן הבא מדלג על תורו")
                .advanceTurn().advanceTurn()
            TakiRank.CHANGE_DIRECTION -> {
                val newDir = if (newState.direction == Direction.CLOCKWISE)
                    Direction.COUNTER_CLOCKWISE else Direction.CLOCKWISE
                newState.copy(
                    direction = newDir,
                    currentColor = card.color!!,
                    isTakiOpen = false,
                    takiColor = null,
                    isPlusOpen = false,
                    plusColor = null
                ).withMessage("${player.name} שינה כיוון").advanceTurn()
            }
            TakiRank.CHANGE_COLOR -> {
                val color = chosenColor ?: TakiColor.RED
                newState.copy(
                    currentColor = color,
                    isTakiOpen = false,
                    takiColor = null,
                    isPlusOpen = false,
                    plusColor = null
                ).withMessage("${player.name} שינה צבע ל-${colorName(color)}").advanceTurn()
            }
            TakiRank.TAKI -> newState.copy(
                isTakiOpen = true,
                takiColor = card.color,
                isPlusOpen = false,
                plusColor = null,
                currentColor = card.color!!
            ).withMessage("${player.name} פתח טאקי ${colorName(card.color!!)}")
            TakiRank.PLUS -> newState.copy(
                isPlusOpen = true,
                plusColor = card.color,
                currentColor = card.color!!
            ).withMessage("${player.name} שם + — שחק עוד קלף ${colorName(card.color!!)}")
            // number cards
            else -> {
                val next = newState.copy(
                    currentColor = card.color!!,
                    isPlusOpen = false,
                    plusColor = null
                ).withMessage("${player.name} שם ${card.displayName()}")
                if (newState.isPlusOpen) {
                    if (newState.isTakiOpen) next else next.advanceTurn()
                } else if (newState.isTakiOpen) {
                    next
                } else {
                    next.advanceTurn()
                }
            }
        }

        // Win check
        val updatedPlayer = newState.players.first { it.id == player.id }
        if (updatedPlayer.hand.isEmpty()) {
            return newState.withMessage("${player.name} ניצח!").copy(
                isGameOver = true,
                winnerId = player.id
            )
        }

        return newState
    }

    private fun draw(state: TakiState): TakiState {
        if (state.isTakiOpen) return state
        if (state.isPlusOpen && state.currentPlayer.hand.any {
                it.color == state.plusColor && it.rank != TakiRank.CHANGE_COLOR
            }
        ) return state

        val amount = if (state.pendingDraw > 0) state.pendingDraw else 1
        val (newDrawPile, drawn) = drawCards(state.drawPile, state.discardPile, amount)

        val newPlayers = state.players.map { p ->
            if (p.id == state.currentPlayer.id) p.copy(hand = p.hand + drawn) else p
        }

        return state.copy(
            players = newPlayers,
            drawPile = newDrawPile,
            pendingDraw = 0,
            isTakiOpen = false,
            takiColor = null,
            isPlusOpen = false,
            plusColor = null
        ).withMessage(
            if (state.pendingDraw > 0) {
                "${state.currentPlayer.name} משך $amount קלפים אחרי +2"
            } else {
                "${state.currentPlayer.name} משך קלף"
            }
        ).advanceTurn()
    }

    private fun closeTaki(state: TakiState): TakiState {
        if (!state.isTakiOpen || state.isPlusOpen) return state
        return state.copy(
            isTakiOpen = false,
            takiColor = null
        ).withMessage("${state.currentPlayer.name} סגר טאקי").advanceTurn()
    }

    private fun isLegalPlay(state: TakiState, card: TakiCard): Boolean {
        if (state.pendingDraw > 0) {
            return card.rank == TakiRank.PLUS_TWO
        }
        if (card.rank == TakiRank.PLUS &&
            state.currentPlayer.hand.count { it == card } < 2 &&
            state.currentPlayer.hand.none {
                it != card && it.color == card.color && it.rank != TakiRank.CHANGE_COLOR
            }
        ) return false
        if (state.isPlusOpen) {
            return card.color == state.plusColor && card.rank != TakiRank.CHANGE_COLOR
        }
        if (state.isTakiOpen) {
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

    private fun TakiState.withMessage(message: String): TakiState =
        copy(
            lastActionMessage = message,
            actionHistory = (actionHistory + message).takeLast(ACTION_HISTORY_LIMIT)
        )

    private fun colorName(color: TakiColor): String = when (color) {
        TakiColor.RED -> "אדום"
        TakiColor.GREEN -> "ירוק"
        TakiColor.BLUE -> "כחול"
        TakiColor.YELLOW -> "צהוב"
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
            // +2, stop, reverse, +, and Taki actions
            cards += TakiCard(color, TakiRank.PLUS_TWO)
            cards += TakiCard(color, TakiRank.PLUS_TWO)
            cards += TakiCard(color, TakiRank.STOP)
            cards += TakiCard(color, TakiRank.STOP)
            cards += TakiCard(color, TakiRank.CHANGE_DIRECTION)
            cards += TakiCard(color, TakiRank.PLUS)
            cards += TakiCard(color, TakiRank.TAKI)
        }
        // Color wilds
        repeat(4) { cards += TakiCard(null, TakiRank.CHANGE_COLOR) }

        return cards.shuffled(random)
    }

    companion object {
        private const val ACTION_HISTORY_LIMIT = 6
    }
}
