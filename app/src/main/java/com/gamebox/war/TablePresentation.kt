package com.gamebox.war

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.view.Display
import android.view.View
import android.widget.TextView
import com.gamebox.common.ui.PlayingCardView
import androidx.core.content.ContextCompat

class TablePresentation(
    context: Context,
    display: Display
) : Presentation(context, display) {

    private var tvPlayerCount: TextView? = null
    private var tvCpuCount: TextView? = null
    private var cardPlayer: PlayingCardView? = null
    private var cardCpu: PlayingCardView? = null
    private var tvMessage: TextView? = null
    private var tvSeriesScore: TextView? = null
    private var tvPublicMessage: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.presentation_table)

        tvPlayerCount = findViewById(R.id.tvPlayerCount)
        tvCpuCount = findViewById(R.id.tvCpuCount)
        cardPlayer = findViewById(R.id.cardPlayer)
        cardCpu = findViewById(R.id.cardCpu)
        tvMessage = findViewById(R.id.tvMessage)
        tvSeriesScore = findViewById(R.id.tvSeriesScore)
        tvPublicMessage = findViewById(R.id.tvPublicMessage)
    }

    fun update(
        playerCount: Int,
        cpuCount: Int,
        playerCard: Card?,
        cpuCard: Card?,
        message: String,
        messageType: GameEngine.MessageType,
        playerSeriesScore: Int,
        cpuSeriesScore: Int,
        publicMessage: String?
    ) {
        tvPlayerCount?.text = "אתה: $playerCount"
        tvCpuCount?.text = "מחשב: $cpuCount"
        tvMessage?.text = message
        tvSeriesScore?.text = context.getString(
            R.string.series_score,
            playerSeriesScore,
            cpuSeriesScore
        )
        tvPublicMessage?.text = publicMessage.orEmpty()
        tvPublicMessage?.visibility =
            if (publicMessage.isNullOrBlank()) View.GONE else View.VISIBLE

        val color = when (messageType) {
            GameEngine.MessageType.WIN -> ContextCompat.getColor(context, R.color.win)
            GameEngine.MessageType.LOSE -> ContextCompat.getColor(context, R.color.lose)
            GameEngine.MessageType.WAR -> ContextCompat.getColor(context, R.color.war)
            else -> ContextCompat.getColor(context, R.color.text)
        }
        tvMessage?.setTextColor(color)

        renderCard(cardPlayer, playerCard)
        renderCard(cardCpu, cpuCard)
    }

    private fun renderCard(view: PlayingCardView?, card: Card?) {
        if (view == null) return
        if (card == null) {
            view.showCardBack()
        } else {
            view.showCard(card.rank, card.suit, card.isRed)
        }
    }
}
