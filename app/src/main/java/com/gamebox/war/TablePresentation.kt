package com.gamebox.war

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.view.Display
import android.view.View
import android.view.ViewGroup
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.HorizontalScrollView
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
    private var warCardsContainer: LinearLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.presentation_table)
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        tvPlayerCount = findViewById(R.id.tvPlayerCount)
        tvCpuCount = findViewById(R.id.tvCpuCount)
        cardPlayer = findViewById(R.id.cardPlayer)
        cardCpu = findViewById(R.id.cardCpu)
        tvMessage = findViewById(R.id.tvMessage)
        tvSeriesScore = findViewById(R.id.tvSeriesScore)
        tvPublicMessage = findViewById(R.id.tvPublicMessage)
        warCardsContainer = findViewById(R.id.warCardsContainer)
        findViewById<TextView>(R.id.tvAppVersion).text =
            context.getString(R.string.app_version_label, BuildConfig.VERSION_NAME)
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
        publicMessage: String?,
        playerWarCards: List<Card>,
        cpuWarCards: List<Card>
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
        renderWarCards(playerWarCards, cpuWarCards)
    }

    private fun renderWarCards(playerCards: List<Card>, cpuCards: List<Card>) {
        val container = warCardsContainer ?: return
        container.removeAllViews()
        if (playerCards.isEmpty() && cpuCards.isEmpty()) {
            container.visibility = View.GONE
            return
        }
        container.visibility = View.VISIBLE
        listOf("אתה" to playerCards, "מחשב" to cpuCards).forEach { (name, cards) ->
            val row = LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                orientation = LinearLayout.HORIZONTAL
            }
            row.addView(TextView(context).apply {
                text = name
                textSize = 14f
                setTextColor(0xFFE0F2FE.toInt())
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(dp(58), ViewGroup.LayoutParams.WRAP_CONTENT))
            val cardsRow = LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                orientation = LinearLayout.HORIZONTAL
            }
            cards.forEachIndexed { index, card ->
                val cardView = PlayingCardView(context)
                if (index == 0 || index == cards.lastIndex) {
                    cardView.showCard(card.rank, card.suit, card.isRed)
                } else {
                    cardView.showCardBack()
                }
                cardsRow.addView(cardView, LinearLayout.LayoutParams(dp(42), dp(58)).apply {
                    setMargins(dp(2), dp(2), dp(2), dp(2))
                })
            }
            row.addView(HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                addView(cardsRow)
            }, LinearLayout.LayoutParams(0, dp(62), 1f))
            container.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
    }

    private fun renderCard(view: PlayingCardView?, card: Card?) {
        if (view == null) return
        if (card == null) {
            view.showCardBack()
        } else {
            view.showCard(card.rank, card.suit, card.isRed)
        }
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
