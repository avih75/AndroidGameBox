package com.gamebox.war

data class WarMultiplayerPlayer(
    val id: String,
    val name: String,
    val deck: List<Card>
)

data class WarMultiplayerState(
    val players: List<WarMultiplayerPlayer>,
    val readyPlayerIds: Set<String> = emptySet(),
    val lastCards: Map<String, Card> = emptyMap(),
    val round: Int = 0,
    val isGameOver: Boolean = false,
    val winnerId: String? = null,
    val message: String = "מוכנים? כל השחקנים חושפים קלף כדי להתחיל."
) {
    val activePlayers: List<WarMultiplayerPlayer>
        get() = players.filter { it.deck.isNotEmpty() }
}

class WarMultiplayerEngine {

    fun newGame(playerNames: List<String>): WarMultiplayerState {
        require(playerNames.size in MIN_PLAYERS..MAX_PLAYERS) {
            "War supports $MIN_PLAYERS-$MAX_PLAYERS players"
        }
        val deck = createDeck().shuffled()
        val hands = List(playerNames.size) { mutableListOf<Card>() }
        deck.forEachIndexed { index, card -> hands[index % hands.size].add(card) }
        val players = playerNames.mapIndexed { index, name ->
            WarMultiplayerPlayer("p$index", name, hands[index])
        }
        return WarMultiplayerState(players)
    }

    fun reveal(state: WarMultiplayerState, playerId: String): WarMultiplayerState {
        if (state.isGameOver || playerId !in state.players.map { it.id }) return state
        if (state.players.first { it.id == playerId }.deck.isEmpty()) return state
        if (playerId in state.readyPlayerIds) return state

        val ready = state.readyPlayerIds + playerId
        val activeIds = state.activePlayers.map { it.id }.toSet()
        if (!ready.containsAll(activeIds)) {
            return state.copy(
                readyPlayerIds = ready,
                lastCards = emptyMap(),
                message = "ממתינים לחשיפה של ${activeIds.size - ready.intersect(activeIds).size} שחקנים..."
            )
        }
        return resolveRound(state)
    }

    private fun resolveRound(state: WarMultiplayerState): WarMultiplayerState {
        val decks = state.players.associate { it.id to it.deck.toMutableList() }
        val active = state.players.filter { decks.getValue(it.id).isNotEmpty() }
        if (active.size <= 1) return finish(state, active.singleOrNull()?.id)

        val pot = mutableListOf<Card>()
        val lastCards = linkedMapOf<String, Card>()
        active.forEach { player ->
            val card = decks.getValue(player.id).removeAt(0)
            pot += card
            lastCards[player.id] = card
        }

        var contenders = active
        var roundWinner: WarMultiplayerPlayer? = null
        while (roundWinner == null) {
            val best = contenders.maxOf { lastCards.getValue(it.id).value }
            val tied = contenders.filter { lastCards.getValue(it.id).value == best }
            if (tied.size == 1) {
                roundWinner = tied.single()
                continue
            }

            val nextShowdown = mutableListOf<WarMultiplayerPlayer>()
            tied.forEach { player ->
                val deck = decks.getValue(player.id)
                repeat(WAR_FACE_DOWN_CARDS) {
                    if (deck.isNotEmpty()) pot += deck.removeAt(0)
                }
                if (deck.isNotEmpty()) {
                    val comparisonCard = deck.removeAt(0)
                    pot += comparisonCard
                    lastCards[player.id] = comparisonCard
                    nextShowdown += player
                }
            }
            if (nextShowdown.isEmpty()) {
                roundWinner = tied.first()
                continue
            }
            if (nextShowdown.size == 1) {
                roundWinner = nextShowdown.single()
                continue
            }

            contenders = nextShowdown
        }

        val winnerDeck = decks.getValue(roundWinner.id)
        winnerDeck.addAll(pot)
        val players = state.players.map { player ->
            player.copy(deck = decks.getValue(player.id))
        }
        val remaining = players.filter { it.deck.isNotEmpty() }
        val gameOver = remaining.size <= 1
        val winnerId = remaining.singleOrNull()?.id
        val winnerName = roundWinner.name
        return state.copy(
            players = players,
            readyPlayerIds = emptySet(),
            lastCards = lastCards,
            round = state.round + 1,
            isGameOver = gameOver,
            winnerId = winnerId,
            message = if (gameOver) "$winnerName ניצח במשחק!" else "$winnerName זכה בסיבוב ואסף ${pot.size} קלפים"
        )
    }

    private fun finish(
        state: WarMultiplayerState,
        winnerId: String?
    ): WarMultiplayerState {
        val winner = state.players.firstOrNull { it.id == winnerId }
        return state.copy(
            isGameOver = true,
            winnerId = winnerId,
            readyPlayerIds = emptySet(),
            message = winner?.let { "${it.name} ניצח במשחק!" } ?: "המשחק הסתיים"
        )
    }

    private fun createDeck(): List<Card> {
        val suits = listOf("♠", "♥", "♦", "♣")
        val ranks = listOf(
            "2" to 2, "3" to 3, "4" to 4, "5" to 5, "6" to 6, "7" to 7,
            "8" to 8, "9" to 9, "10" to 10, "J" to 11, "Q" to 12, "K" to 13, "A" to 14
        )
        return suits.flatMap { suit ->
            ranks.map { (rank, value) ->
                Card(rank, suit, value, suit == "♥" || suit == "♦")
            }
        }
    }

    companion object {
        const val MIN_PLAYERS = 2
        const val MAX_PLAYERS = 6
        private const val WAR_FACE_DOWN_CARDS = 2
    }
}
