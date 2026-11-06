package com.gamebox.war

data class Card(
    val rank: String,
    val suit: String,
    val value: Int,
    val isRed: Boolean
) {
    fun display(): String = "$rank$suit"
}
