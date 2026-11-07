package com.gamebox.taki.ui.external

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.view.Display
import android.view.Gravity
import android.view.ViewGroup
import android.graphics.drawable.GradientDrawable
import android.widget.LinearLayout
import android.widget.TextView
import com.gamebox.taki.domain.Direction
import com.gamebox.taki.domain.TakiColor
import com.gamebox.taki.domain.TakiPublicState
import com.gamebox.taki.domain.TakiRank

/**
 * External display (TV) – shows only public state.
 */
class TakiTablePresentation(
    context: Context,
    display: Display
) : Presentation(context, display) {

    private var tvTopCard: TextView? = null
    private var tvCurrentColor: TextView? = null
    private var tvDirection: TextView? = null
    private var tvPendingDraw: TextView? = null
    private var tvMessage: TextView? = null
    private var playersRow: LinearLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Layout must be available as R.layout.presentation_taki_table in the real project
        setContentView(context.resources.getIdentifier(
            "presentation_taki_table", "layout", context.packageName
        ).let { if (it != 0) it else android.R.layout.simple_list_item_1 })
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        // In real project use ViewBinding or findViewById with R.id
        // Here we keep it resilient for copy-paste
        try {
            val root = window?.decorView
            tvTopCard = root?.findViewById(context.resources.getIdentifier("tvTopCard", "id", context.packageName))
            tvCurrentColor = root?.findViewById(context.resources.getIdentifier("tvCurrentColor", "id", context.packageName))
            tvDirection = root?.findViewById(context.resources.getIdentifier("tvDirection", "id", context.packageName))
            tvPendingDraw = root?.findViewById(context.resources.getIdentifier("tvPendingDraw", "id", context.packageName))
            tvMessage = root?.findViewById(context.resources.getIdentifier("tvMessage", "id", context.packageName))
            playersRow = root?.findViewById(context.resources.getIdentifier("playersRow", "id", context.packageName))
        } catch (_: Exception) {}
    }

    fun update(public: TakiPublicState) {
        tvTopCard?.text = "${cardSymbol(public.topCard.rank)}\n${public.topCard.displayName()}"
        tvTopCard?.setTextColor(0xFFFFFFFF.toInt())
        tvTopCard?.background = cardBackground(public.topCard.color)

        tvCurrentColor?.text = "צבע נוכחי: ${colorName(public.currentColor)}"
        tvCurrentColor?.setTextColor(colorInt(public.currentColor))

        tvDirection?.text = if (public.direction == Direction.CLOCKWISE) "כיוון: ← עם השעון" else "כיוון: → נגד השעון"

        if (public.pendingDraw > 0) {
            tvPendingDraw?.visibility = android.view.View.VISIBLE
            tvPendingDraw?.text = "חובה למשוך ${public.pendingDraw} קלפים"
        } else {
            tvPendingDraw?.visibility = android.view.View.GONE
        }

        tvMessage?.text = public.lastActionMessage.ifEmpty {
            if (public.isGameOver) "המשחק נגמר" else "תור של ${public.players.firstOrNull { it.id == public.currentPlayerId }?.name ?: ""}"
        }
        window?.decorView
            ?.findViewById<TextView>(context.resources.getIdentifier("tvAppVersion", "id", context.packageName))
            ?.text = context.getString(
                com.gamebox.war.R.string.app_version_label,
                com.gamebox.war.BuildConfig.VERSION_NAME
            )

        // Players summary
        playersRow?.removeAllViews()
        public.players.forEach { p ->
            val tv = TextView(context).apply {
                text = "${p.name}\n${p.cardCount}"
                textSize = 16f
                setTextColor(if (p.id == public.currentPlayerId) 0xFF4ADE80.toInt() else 0xFFE0F2FE.toInt())
                gravity = Gravity.CENTER
                setPadding(24, 8, 24, 8)
            }
            playersRow?.addView(tv)
        }
    }

    private fun colorName(c: TakiColor) = when (c) {
        TakiColor.RED -> "אדום"
        TakiColor.GREEN -> "ירוק"
        TakiColor.BLUE -> "כחול"
        TakiColor.YELLOW -> "צהוב"
    }

    private fun colorInt(c: TakiColor) = when (c) {
        TakiColor.RED -> 0xFFF87171.toInt()
        TakiColor.GREEN -> 0xFF4ADE80.toInt()
        TakiColor.BLUE -> 0xFF60A5FA.toInt()
        TakiColor.YELLOW -> 0xFFFBBF24.toInt()
    }

    private fun cardSymbol(rank: TakiRank) = when (rank) {
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
        TakiRank.PLUS -> "+"
        TakiRank.STOP -> "⊘"
        TakiRank.CHANGE_DIRECTION -> "↻"
        TakiRank.CHANGE_COLOR -> "✦"
        TakiRank.TAKI -> "T"
    }

    private fun cardBackground(color: TakiColor?) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        when (color) {
            TakiColor.RED -> intArrayOf(0xFFFF6B6B.toInt(), 0xFFB91C1C.toInt())
            TakiColor.GREEN -> intArrayOf(0xFF4ADE80.toInt(), 0xFF15803D.toInt())
            TakiColor.BLUE -> intArrayOf(0xFF60A5FA.toInt(), 0xFF1D4ED8.toInt())
            TakiColor.YELLOW -> intArrayOf(0xFFFDE047.toInt(), 0xFFD97706.toInt())
            null -> intArrayOf(0xFFA78BFA.toInt(), 0xFF6D28D9.toInt())
        }
    ).apply {
        cornerRadius = 18f
        setStroke(4, 0xFFF8FAFC.toInt())
    }
}
