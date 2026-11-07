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
        val borderWidth = dp(1.2f)
        val inset = borderWidth / 2f
        cardBounds.set(inset, inset, width - inset, height - inset)
        val corner = dp(10f)

        paint.color = Color.WHITE
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(cardBounds, corner, corner, paint)
        paint.color = CARD_BORDER
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = borderWidth
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
        val cornerTextSize = minOf(cardBounds.width() * 0.2f, cardBounds.height() * 0.105f)
        val cornerX = cardBounds.left + cardBounds.width() * 0.18f
        val cornerY = cardBounds.top + cardBounds.height() * 0.17f
        drawText(canvas, cornerText, ink, cornerTextSize, cornerX, cornerY)

        canvas.save()
        canvas.rotate(180f, width / 2f, height / 2f)
        drawText(canvas, cornerText, ink, cornerTextSize, cornerX, cornerY)
        canvas.restore()

        val rankValue = faceRank.toIntOrNull()
        if (rankValue != null && rankValue in 2..10) {
            drawPips(canvas, ink, rankValue)
        } else {
            drawText(
                canvas,
                faceSuit,
                ink,
                height * 0.40f,
                width / 2f,
                height * 0.60f
            )
            if (faceRank in listOf("J", "Q", "K")) {
                drawText(canvas, faceRank, ink, height * 0.23f, width / 2f, height * 0.77f)
            }
        }
    }

    private fun drawPips(canvas: Canvas, color: Int, count: Int) {
        val positions = when (count) {
            2 -> listOf(.5f to .28f, .5f to .72f)
            3 -> listOf(.5f to .22f, .5f to .5f, .5f to .78f)
            4 -> listOf(.32f to .28f, .68f to .28f, .32f to .72f, .68f to .72f)
            5 -> listOf(.32f to .24f, .68f to .24f, .5f to .5f, .32f to .76f, .68f to .76f)
            6 -> listOf(.32f to .2f, .68f to .2f, .32f to .5f, .68f to .5f, .32f to .8f, .68f to .8f)
            7 -> listOf(.32f to .18f, .68f to .18f, .32f to .41f, .68f to .41f, .5f to .59f, .32f to .82f, .68f to .82f)
            8 -> listOf(.32f to .16f, .68f to .16f, .32f to .39f, .68f to .39f, .32f to .61f, .68f to .61f, .32f to .84f, .68f to .84f)
            9 -> listOf(.32f to .14f, .68f to .14f, .32f to .32f, .68f to .32f, .5f to .5f, .32f to .68f, .68f to .68f, .32f to .86f, .68f to .86f)
            else -> listOf(.32f to .12f, .68f to .12f, .32f to .31f, .68f to .31f, .32f to .5f, .68f to .5f, .32f to .69f, .68f to .69f, .32f to .88f, .68f to .88f)
        }
        positions.forEach { (x, y) ->
            val centerX = width * x
            val centerY = height * y
            if (y > .5f) {
                canvas.save()
                canvas.rotate(180f, centerX, centerY)
            }
            drawText(canvas, suit ?: "", color, height * .105f, centerX, centerY)
            if (y > .5f) canvas.restore()
        }
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
