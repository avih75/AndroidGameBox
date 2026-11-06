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
        lastMessage = "לחץ על שלוף קלף"
        lastMessageType = MessageType.NORMAL
    }

    fun draw(): Boolean {
        if (isGameOver || playerDeck.isEmpty() || cpuDeck.isEmpty()) return false

        val pCard = playerDeck.removeAt(0)
        val cCard = cpuDeck.removeAt(0)
        lastPlayerCard = pCard
        lastCpuCard = cCard

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
                // War – simple version
                if (playerDeck.isNotEmpty() && cpuDeck.isNotEmpty()) {
                    val pExtra = playerDeck.removeAt(0)
                    val cExtra = cpuDeck.removeAt(0)
                    if (pExtra.value >= cExtra.value) {
                        playerDeck.addAll(listOf(pCard, cCard, pExtra, cExtra))
                        lastMessage = "מלחמה! אתה ניצחת"
                        lastMessageType = MessageType.WIN
                    } else {
                        cpuDeck.addAll(listOf(cCard, pCard, cExtra, pExtra))
                        lastMessage = "מלחמה! המחשב ניצח"
                        lastMessageType = MessageType.LOSE
                    }
                } else {
                    playerDeck.add(pCard)
                    playerDeck.add(cCard)
                    lastMessage = "מלחמה (סיום) – אתה לוקח"
                    lastMessageType = MessageType.WIN
                }
            }
        }

        checkGameOver()
        return true
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
}
