package com.gamebox.war

class GameEngine {

    private val suits = listOf("♠", "♥", "♦", "♣")
    private val ranks = listOf("2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A")
    private val values = mapOf(
        "2" to 2, "3" to 3, "4" to 4, "5" to 5, "6" to 6, "7" to 7, "8" to 8,
        "9" to 9, "10" to 10, "J" to 11, "Q" to 12, "K" to 13, "A" to 14
    )

    var playerDeck = mutableListOf<Card>()
        private set
    var cpuDeck = mutableListOf<Card>()
        private set
    var isGameOver = false
        private set
    var lastPlayerCard: Card? = null
        private set
    var lastCpuCard: Card? = null
        private set
    var lastWarCards: Map<String, List<Card>> = emptyMap()
        private set
    var lastMessage: String = "לחץ על שלוף קלף"
        private set
    var lastMessageType: MessageType = MessageType.NORMAL
        private set

    enum class MessageType { NORMAL, WIN, LOSE, WAR }

    fun newGame() {
        val deck = createShuffledDeck()
        playerDeck = deck.take(26).toMutableList()
        cpuDeck = deck.drop(26).toMutableList()
        isGameOver = false
        lastPlayerCard = null
        lastCpuCard = null
        lastWarCards = emptyMap()
        lastMessage = "לחץ על שלוף קלף"
        lastMessageType = MessageType.NORMAL
    }

    fun draw(): Boolean {
        if (isGameOver || playerDeck.isEmpty() || cpuDeck.isEmpty()) return false

        val pCard = playerDeck.removeAt(0)
        val cCard = cpuDeck.removeAt(0)
        lastPlayerCard = pCard
        lastCpuCard = cCard
        lastWarCards = emptyMap()

        when {
            pCard.value > cCard.value -> {
                playerDeck.add(pCard)
                playerDeck.add(cCard)
                lastMessage = "אתה ניצחת בתור!"
                lastMessageType = MessageType.WIN
            }
            cCard.value > pCard.value -> {
                cpuDeck.add(cCard)
                cpuDeck.add(pCard)
                lastMessage = "המחשב ניצח בתור"
                lastMessageType = MessageType.LOSE
            }
            else -> {
                resolveWar(pCard, cCard)
            }
        }

        checkGameOver()
        return true
    }

    private fun resolveWar(firstPlayerCard: Card, firstCpuCard: Card) {
        val pot = mutableListOf(firstPlayerCard, firstCpuCard)
        val playerWarCards = mutableListOf(firstPlayerCard)
        val cpuWarCards = mutableListOf(firstCpuCard)
        var contenders = setOf(PLAYER, CPU)
        var playerComparison = firstPlayerCard
        var cpuComparison = firstCpuCard
        var winner: String? = null

        while (winner == null) {
            val nextContenders = linkedSetOf<String>()
            for (contender in contenders) {
                val deck = if (contender == PLAYER) playerDeck else cpuDeck
                val warCards = if (contender == PLAYER) playerWarCards else cpuWarCards
                repeat(WAR_FACE_DOWN_CARDS) {
                    if (deck.isNotEmpty()) {
                        val card = deck.removeAt(0)
                        pot += card
                        warCards += card
                    }
                }
                if (deck.isNotEmpty()) {
                    val comparison = deck.removeAt(0)
                    pot += comparison
                    warCards += comparison
                    if (contender == PLAYER) playerComparison = comparison
                    else cpuComparison = comparison
                    nextContenders += contender
                }
            }

            winner = when {
                nextContenders.isEmpty() -> PLAYER
                nextContenders.size == 1 -> nextContenders.first()
                playerComparison.value > cpuComparison.value -> PLAYER
                cpuComparison.value > playerComparison.value -> CPU
                else -> null
            }
            contenders = nextContenders
        }

        if (winner == PLAYER) {
            playerDeck.addAll(pot)
            lastMessage = "מלחמה! הקלף השלישי הכריע — ניצחת ואספת ${pot.size} קלפים"
            lastMessageType = MessageType.WIN
        } else {
            cpuDeck.addAll(pot)
            lastMessage = "מלחמה! הקלף השלישי הכריע — המחשב אסף ${pot.size} קלפים"
            lastMessageType = MessageType.LOSE
        }
        lastWarCards = mapOf(PLAYER to playerWarCards, CPU to cpuWarCards)
    }

    private fun checkGameOver() {
        when {
            playerDeck.isEmpty() -> {
                isGameOver = true
                lastMessage = "המחשב ניצח במשחק!"
                lastMessageType = MessageType.LOSE
            }
            cpuDeck.isEmpty() -> {
                isGameOver = true
                lastMessage = "ניצחת במשחק! 🎉"
                lastMessageType = MessageType.WIN
            }
        }
    }

    private fun createShuffledDeck(): List<Card> {
        val deck = mutableListOf<Card>()
        for (suit in suits) {
            for (rank in ranks) {
                deck.add(
                    Card(
                        rank = rank,
                        suit = suit,
                        value = values[rank]!!,
                        isRed = suit == "♥" || suit == "♦"
                    )
                )
            }
        }
        deck.shuffle()
        return deck
    }

    companion object {
        private const val PLAYER = "player"
        private const val CPU = "cpu"
        private const val WAR_FACE_DOWN_CARDS = 2
    }
}
