package com.gamebox.taki.domain

/**
 * Very simple AI for v1.
 * Priority: play action cards > play numbers > draw.
 */
class TakiAi {

    fun chooseAction(state: TakiState, playerId: String): TakiAction {
        val private = state.privateStateFor(playerId)
        val hand = private.hand

        if (state.pendingDraw > 0) {
            val plus = hand.firstOrNull {
                it.rank == TakiRank.PLUS_TWO || it.rank == TakiRank.PLUS_THREE
            }
            return if (plus != null) TakiAction.PlayCard(plus) else TakiAction.Draw
        }

        if (state.isTakiOpen) {
            val sameColor = hand.filter { it.color == state.takiColor && it.rank != TakiRank.CHANGE_COLOR }
            return if (sameColor.isNotEmpty()) {
                TakiAction.PlayCard(sameColor.first())
            } else {
                TakiAction.CloseTaki
            }
        }

        // Prefer action cards that match
        val legal = hand.filter { isLegal(state, it) }
        if (legal.isEmpty()) return TakiAction.Draw

        val actionCard = legal.firstOrNull { it.isAction() }
        if (actionCard != null) {
            val color = if (actionCard.rank == TakiRank.CHANGE_COLOR) {
                // pick the color the AI has most of
                hand.groupingBy { it.color }.eachCount()
                    .filterKeys { it != null }
                    .maxByOrNull { it.value }?.key ?: TakiColor.RED
            } else null
            return TakiAction.PlayCard(actionCard, color)
        }

        return TakiAction.PlayCard(legal.first())
    }

    private fun isLegal(state: TakiState, card: TakiCard): Boolean {
        if (card.rank == TakiRank.CHANGE_COLOR) return true
        if (card.color == state.currentColor) return true
        if (card.rank == state.topCard.rank) return true
        return false
    }
}
