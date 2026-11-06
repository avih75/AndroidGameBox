package com.gamebox.taki.ui.phone

import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.gamebox.games.taki.domain.TakiAction
import com.gamebox.games.taki.domain.TakiCard
import com.gamebox.games.taki.domain.TakiColor
import com.gamebox.games.taki.domain.TakiPrivateState
import com.gamebox.games.taki.domain.TakiPublicState

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

    fun render(public: TakiPublicState, private: TakiPrivateState) {
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
        tvTopCard.text = public.topCard.displayName()
        tvCurrentColor.text = "צבע: ${colorName(public.currentColor)}"
        tvPlayersSummary.text = public.players.joinToString(" · ") { "${it.name}: ${it.cardCount}" }
        tvMessage.text = public.lastActionMessage

        // Hand
        handContainer.removeAllViews()
        private.hand.forEach { card ->
            val cardView = TextView(root.context).apply {
                text = card.displayName()
                textSize = 13f
                setTextColor(if (isReddish(card.color)) 0xFFDC2626.toInt() else 0xFF111111.toInt())
                setBackgroundColor(0xFFFFFFFF.toInt())
                gravity = Gravity.CENTER
                width = dp(72)
                height = dp(100)
                setPadding(6, 6, 6, 6)
                val lp = LinearLayout.LayoutParams(dp(72), dp(100)).apply {
                    marginEnd = dp(8)
                }
                layoutParams = lp
                isClickable = private.isMyTurn
                isFocusable = private.isMyTurn
                alpha = if (private.isMyTurn) 1f else 0.55f
                setOnClickListener {
                    if (private.isMyTurn) {
                        // For Change Color we need a color picker later; default RED for now
                        val chosen = if (card.rank.name == "CHANGE_COLOR") TakiColor.RED else null
                        onAction(TakiAction.PlayCard(card, chosen))
                    }
                }
            }
            handContainer.addView(cardView)
        }

        // Buttons
        btnDraw.isEnabled = private.isMyTurn && (private.canDraw || private.mustDraw)
        btnCloseTaki.visibility = if (public.isTakiOpen && private.isMyTurn) View.VISIBLE else View.GONE
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

    private fun isReddish(c: TakiColor?) = c == TakiColor.RED || c == TakiColor.YELLOW
}
