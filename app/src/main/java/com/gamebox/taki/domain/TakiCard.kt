package com.gamebox.taki.domain

/**
 * Simplified Taki card model (first version – no Super Taki).
 */
enum class TakiColor {
    RED, GREEN, BLUE, YELLOW
}

enum class TakiRank {
    // Number cards
    ONE, TWO, THREE, FOUR, FIVE, SIX, SEVEN, EIGHT, NINE,

    // Action cards
    PLUS_TWO,       // +2
    STOP,           // Stop / Skip
    CHANGE_DIRECTION,
    CHANGE_COLOR,
    TAKI,           // Open Taki sequence
    PLUS_THREE      // King (+3)
}

data class TakiCard(
    val color: TakiColor?,   // null only for CHANGE_COLOR (wild)
    val rank: TakiRank
) {
    fun displayName(): String {
        val colorName = color?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Wild"
        return when (rank) {
            TakiRank.PLUS_TWO -> "+2 $colorName"
            TakiRank.STOP -> "Stop $colorName"
            TakiRank.CHANGE_DIRECTION -> "Reverse $colorName"
            TakiRank.CHANGE_COLOR -> "Change Color"
            TakiRank.TAKI -> "Taki $colorName"
            TakiRank.PLUS_THREE -> "+3 $colorName"
            else -> "${rank.name.removePrefix("ONE").ifEmpty { "1" }.lowercase().replaceFirstChar { it.uppercase() }} $colorName"
                .replace("One", "1")
                .replace("Two", "2")
                .replace("Three", "3")
                .replace("Four", "4")
                .replace("Five", "5")
                .replace("Six", "6")
                .replace("Seven", "7")
                .replace("Eight", "8")
                .replace("Nine", "9")
        }
    }

    fun isAction(): Boolean = rank in listOf(
        TakiRank.PLUS_TWO, TakiRank.STOP, TakiRank.CHANGE_DIRECTION,
        TakiRank.CHANGE_COLOR, TakiRank.TAKI, TakiRank.PLUS_THREE
    )
}
