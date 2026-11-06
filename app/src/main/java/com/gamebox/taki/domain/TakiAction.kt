package com.gamebox.taki.domain

/**
 * Actions a player (Client or Host-as-player) may send.
 * Only the Host applies them.
 */
sealed class TakiAction {
    data class PlayCard(val card: TakiCard, val chosenColor: TakiColor? = null) : TakiAction()
    object Draw : TakiAction()
    object CloseTaki : TakiAction()          // end the open Taki sequence
    object DeclareLastCard : TakiAction()    // optional "Taki!" call – for later
}
