package com.gamebox.taki.ui.external

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.view.Display
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.gamebox.taki.domain.Direction
import com.gamebox.taki.domain.TakiColor
import com.gamebox.taki.domain.TakiPublicState

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
        tvTopCard?.text = public.topCard.displayName()
        tvTopCard?.setTextColor(if (public.topCard.color?.let { isRed(it) } == true) 0xFFDC2626.toInt() else 0xFF111111.toInt())

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

    private fun isRed(c: TakiColor) = c == TakiColor.RED || c == TakiColor.YELLOW
}
