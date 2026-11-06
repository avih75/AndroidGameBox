package com.gamebox.taki.ui.phone

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.gamebox.taki.domain.TakiAction
import com.gamebox.taki.domain.TakiCard
import com.gamebox.taki.domain.TakiColor
import com.gamebox.taki.domain.TakiPrivateState
import com.gamebox.taki.domain.TakiPublicState
import com.gamebox.taki.domain.TakiRank

/**
 * Binds phone UI to public + private state.
 * Does not own game logic – only renders and emits actions.
 */
class TakiPhoneController(
    private val root: View,
    private val onAction: (TakiAction) -> Unit
) {
    private val tvStatus: TextView = root.findViewById(id("tvStatus"))
    private val tvPendingDraw: TextView = root.findViewById(id("tvPendingDraw"))
    private val tvTopCard: TextView = root.findViewById(id("tvTopCard"))
    private val tvCurrentColor: TextView = root.findViewById(id("tvCurrentColor"))
    private val tvPlayersSummary: TextView = root.findViewById(id("tvPlayersSummary"))
    private val tvWinCounter: TextView = root.findViewById(id("tvWinCounter"))
    private val tvMessage: TextView = root.findViewById(id("tvMessage"))
    private val handContainer: LinearLayout = root.findViewById(id("handContainer"))
    private val btnDraw: Button = root.findViewById(id("btnDraw"))
    private val btnCloseTaki: Button = root.findViewById(id("btnCloseTaki"))

    private var lastPrivate: TakiPrivateState? = null
    private var lastPublic: TakiPublicState? = null

    init {
        btnDraw.setOnClickListener { onAction(TakiAction.Draw) }
        btnCloseTaki.setOnClickListener { onAction(TakiAction.CloseTaki) }
    }

    fun render(
        public: TakiPublicState,
        private: TakiPrivateState,
        winCounts: Map<String, Int>
    ) {
        lastPublic = public
        lastPrivate = private

        // Status
        tvStatus.text = when {
            public.isGameOver -> if (public.winnerId != null) "המשחק נגמר" else "הסתיים"
            private.isMyTurn -> "תורך!"
            else -> "ממתין..."
        }

        if (public.pendingDraw > 0) {
            tvPendingDraw.visibility = View.VISIBLE
            tvPendingDraw.text = "+${public.pendingDraw}"
        } else {
            tvPendingDraw.visibility = View.GONE
        }

        // Top card + color
        tvTopCard.text = "${cardSymbol(public.topCard)}\n${public.topCard.displayName()}"
        tvTopCard.setTextColor(Color.WHITE)
        tvTopCard.textSize = 12f
        tvTopCard.background = cardBackground(public.topCard.color, large = false)
        tvCurrentColor.text = "צבע: ${colorName(public.currentColor)}"
        tvPlayersSummary.text = public.players.joinToString(" · ") { "${it.name}: ${it.cardCount}" }
        tvWinCounter.text = "ניצחונות: " + public.players.joinToString(" · ") {
            "${it.name} ${winCounts[it.id] ?: 0}"
        }
        tvMessage.text = public.lastActionMessage

        // Hand
        handContainer.removeAllViews()
        private.hand.forEach { card ->
            val cardView = makeCardView(card).apply {
                layoutParams = LinearLayout.LayoutParams(dp(82), dp(116)).apply {
                    marginEnd = dp(8)
                }
                isClickable = private.isMyTurn
                isFocusable = private.isMyTurn
                alpha = if (private.isMyTurn) 1f else 0.55f
                setOnClickListener {
                    if (!private.isMyTurn) return@setOnClickListener
                    if (card.rank == TakiRank.CHANGE_COLOR) {
                        chooseColor { chosen -> onAction(TakiAction.PlayCard(card, chosen)) }
                    } else {
                        onAction(TakiAction.PlayCard(card))
                    }
                }
            }
            handContainer.addView(cardView)
        }

        // Buttons
        btnDraw.isEnabled = private.isMyTurn && (private.canDraw || private.mustDraw)
        btnCloseTaki.visibility = if (public.isTakiOpen && private.isMyTurn) View.VISIBLE else View.GONE
    }

    private fun makeCardView(card: TakiCard): FrameLayout {
        val face = FrameLayout(root.context).apply {
            background = cardBackground(card.color, large = true)
            elevation = dp(5).toFloat()
            contentDescription = card.displayName()
        }
        val badge = TextView(root.context).apply {
            text = cardSymbol(card)
            textSize = 13f
            setTextColor(Color.WHITE)
            setPadding(dp(6), dp(4), dp(6), dp(4))
            background = GradientDrawable().apply {
                setColor(0x55000000)
                cornerRadius = dp(8).toFloat()
            }
        }
        face.addView(badge, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.START
        ).apply {
            setMargins(dp(6), dp(6), 0, 0)
        })

        val medallion = TextView(root.context).apply {
            text = cardSymbol(card)
            textSize = if (card.isAction()) 19f else 34f
            setTextColor(inkColor(card.color))
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xEFFFFFFF.toInt())
                setStroke(dp(2), 0xAAFFFFFF.toInt())
            }
        }
        face.addView(medallion, FrameLayout.LayoutParams(dp(58), dp(70), Gravity.CENTER))

        val caption = TextView(root.context).apply {
            text = cardLabel(card)
            textSize = 9f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setShadowLayer(3f, 0f, 1f, 0x99000000.toInt())
        }
        face.addView(caption, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            dp(22),
            Gravity.BOTTOM
        ).apply {
            setMargins(dp(4), 0, dp(4), dp(4))
        })
        return face
    }

    private fun cardBackground(color: TakiColor?, large: Boolean) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        when (color) {
            TakiColor.RED -> intArrayOf(0xFFFF6B6B.toInt(), 0xFFB91C1C.toInt())
            TakiColor.GREEN -> intArrayOf(0xFF4ADE80.toInt(), 0xFF15803D.toInt())
            TakiColor.BLUE -> intArrayOf(0xFF60A5FA.toInt(), 0xFF1D4ED8.toInt())
            TakiColor.YELLOW -> intArrayOf(0xFFFDE047.toInt(), 0xFFD97706.toInt())
            null -> intArrayOf(0xFFA78BFA.toInt(), 0xFF6D28D9.toInt())
        }
    ).apply {
        cornerRadius = dp(if (large) 12 else 6).toFloat()
        setStroke(dp(if (large) 3 else 2), 0xFFF8FAFC.toInt())
    }

    private fun cardSymbol(card: TakiCard): String = when (card.rank) {
        TakiRank.ONE -> "1"
        TakiRank.TWO -> "2"
        TakiRank.THREE -> "3"
        TakiRank.FOUR -> "4"
        TakiRank.FIVE -> "5"
        TakiRank.SIX -> "6"
        TakiRank.SEVEN -> "7"
        TakiRank.EIGHT -> "8"
        TakiRank.NINE -> "9"
        TakiRank.PLUS_TWO -> "+2"
        TakiRank.PLUS_THREE -> "+3"
        TakiRank.STOP -> "⊘"
        TakiRank.CHANGE_DIRECTION -> "↻"
        TakiRank.CHANGE_COLOR -> "✦"
        TakiRank.TAKI -> "T"
    }

    private fun cardLabel(card: TakiCard): String = when (card.rank) {
        TakiRank.STOP -> "עצור"
        TakiRank.CHANGE_DIRECTION -> "החלף כיוון"
        TakiRank.CHANGE_COLOR -> "שנה צבע"
        TakiRank.TAKI -> "טאקי"
        TakiRank.PLUS_TWO -> "משוך 2"
        TakiRank.PLUS_THREE -> "משוך 3"
        else -> "קלף מספר ${cardSymbol(card)}"
    }

    private fun inkColor(color: TakiColor?): Int = when (color) {
        TakiColor.RED -> 0xFF991B1B.toInt()
        TakiColor.GREEN -> 0xFF166534.toInt()
        TakiColor.BLUE -> 0xFF1E40AF.toInt()
        TakiColor.YELLOW -> 0xFF92400E.toInt()
        null -> 0xFF5B21B6.toInt()
    }

    private fun chooseColor(onChosen: (TakiColor) -> Unit) {
        val colors = arrayOf("אדום", "ירוק", "כחול", "צהוב")
        val values = TakiColor.values()
        AlertDialog.Builder(root.context)
            .setTitle("בחר צבע")
            .setItems(colors) { _, index -> onChosen(values[index]) }
            .show()
    }

    private fun id(name: String): Int =
        root.resources.getIdentifier(name, "id", root.context.packageName)

    private fun dp(value: Int): Int =
        (value * root.resources.displayMetrics.density).toInt()

    private fun colorName(c: TakiColor) = when (c) {
        TakiColor.RED -> "אדום"
        TakiColor.GREEN -> "ירוק"
        TakiColor.BLUE -> "כחול"
        TakiColor.YELLOW -> "צהוב"
    }

}
