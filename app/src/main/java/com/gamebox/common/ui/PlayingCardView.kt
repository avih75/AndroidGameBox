package com.gamebox.common.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class PlayingCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cardBounds = RectF()
    private var rank: String? = null
    private var suit: String? = null
    private var isRed = false
    private var faceDown = false

    fun showCard(rank: String, suit: String, isRed: Boolean) {
        this.rank = rank
        this.suit = suit
        this.isRed = isRed
        faceDown = false
        invalidate()
    }

    fun showCardBack() {
        rank = null
        suit = null
        faceDown = true
        invalidate()
    }

    fun clearCard() {
        rank = null
        suit = null
        faceDown = false
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val inset = dp(2f)
        cardBounds.set(inset, inset, width - inset, height - inset)
        val corner = dp(10f)

        paint.color = Color.WHITE
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(cardBounds, corner, corner, paint)
        paint.color = CARD_BORDER
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1.2f)
        canvas.drawRoundRect(cardBounds, corner, corner, paint)
        paint.style = Paint.Style.FILL

        if (faceDown) {
            drawBack(canvas, corner)
            return
        }

        val faceRank = rank
        val faceSuit = suit
        if (faceRank == null || faceSuit == null) {
            drawText(canvas, "?", Color.LTGRAY, height * 0.48f, width * 0.5f, height * 0.62f)
            return
        }

        val ink = if (isRed) RED_INK else BLACK_INK
        val cornerText = "$faceRank\n$faceSuit"
        drawText(canvas, cornerText, ink, height * 0.17f, width * 0.16f, height * 0.12f)

        canvas.save()
        canvas.rotate(180f, width / 2f, height / 2f)
        drawText(canvas, cornerText, ink, height * 0.17f, width * 0.16f, height * 0.12f)
        canvas.restore()

        drawText(
            canvas,
            faceSuit,
            ink,
            height * 0.40f,
            width / 2f,
            height * 0.60f
        )
    }

    private fun drawBack(canvas: Canvas, corner: Float) {
        val inner = RectF(cardBounds).apply { inset(dp(5f), dp(5f)) }
        paint.color = BACKGROUND
        canvas.drawRoundRect(inner, corner, corner, paint)
        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(inner, corner, corner, Path.Direction.CW) })
        paint.color = BACK_PATTERN
        paint.strokeWidth = dp(1f)
        val step = dp(9f)
        var offset = -height.toFloat()
        while (offset < width + height) {
            canvas.drawLine(offset, inner.top, offset + height, inner.bottom, paint)
            canvas.drawLine(offset + height, inner.top, offset, inner.bottom, paint)
            offset += step
        }
        canvas.restore()
        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1.5f)
        canvas.drawRoundRect(inner, corner, corner, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawText(
        canvas: Canvas,
        text: String,
        color: Int,
        textSize: Float,
        centerX: Float,
        centerY: Float
    ) {
        paint.color = color
        paint.textSize = textSize
        paint.typeface = android.graphics.Typeface.create(
            "serif",
            android.graphics.Typeface.BOLD
        )
        paint.textAlign = Paint.Align.CENTER
        val lines = text.split('\n')
        val lineHeight = paint.fontSpacing
        val firstBaseline = centerY - (lines.size - 1) * lineHeight / 2f -
            (paint.ascent() + paint.descent()) / 2f
        lines.forEachIndexed { index, line ->
            canvas.drawText(line, centerX, firstBaseline + index * lineHeight, paint)
        }
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    companion object {
        private const val CARD_BORDER = 0xFFD1D5DB.toInt()
        private const val RED_INK = 0xFFB91C1C.toInt()
        private const val BLACK_INK = 0xFF111827.toInt()
        private const val BACKGROUND = 0xFF1E3A8A.toInt()
        private const val BACK_PATTERN = 0xFF93C5FD.toInt()
    }
}
