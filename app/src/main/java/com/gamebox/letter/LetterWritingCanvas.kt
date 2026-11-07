package com.gamebox.letter

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator

class LetterWritingCanvas(context: Context) : View(context) {
    private val strokes = mutableListOf<MutableList<PointF>>()
    private val inkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = INK
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = dp(8f)
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = GUIDE_LINE
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val letterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = LETTER_GUIDE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        isFakeBoldText = true
    }
    private val demoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = DEMO_COLOR
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = dp(7f)
    }
    private var guideLetter: String? = null
    private var showLetter = false
    private var demoProgress = 0f
    private var demoAnimator: ValueAnimator? = null
    private val letterPath = Path()

    init {
        setBackgroundColor(PAPER)
        contentDescription = "Handwriting area"
    }

    fun clear() {
        demoAnimator?.cancel()
        demoAnimator = null
        strokes.clear()
        invalidate()
    }

    fun hasWriting(): Boolean = strokes.any { it.size > 1 }

    fun showLetter(letter: String) {
        demoAnimator?.cancel()
        guideLetter = letter
        showLetter = true
        demoProgress = 0f
        invalidate()
    }

    fun showWritingDemo(letter: String) {
        demoAnimator?.cancel()
        guideLetter = letter
        showLetter = false
        demoProgress = 0f
        buildLetterPath(letter)
        demoAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = DEMO_DURATION_MS
            interpolator = LinearInterpolator()
            addUpdateListener {
                demoProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val padding = dp(18f)
        val baseline = height - dp(28f)
        val topLine = padding
        val middleLine = height * 0.53f
        canvas.drawLine(padding, topLine, width - padding, topLine, linePaint)
        canvas.drawLine(padding, middleLine, width - padding, middleLine, linePaint)
        canvas.drawLine(padding, baseline, width - padding, baseline, linePaint)

        val letter = guideLetter
        if (showLetter && letter != null) {
            letterPaint.textSize = minOf(width * 0.62f, height * 0.74f)
            canvas.drawText(letter, width / 2f, height * 0.72f, letterPaint)
        }
        if (demoProgress > 0f && letterPath.isEmpty.not()) {
            drawDemoPath(canvas)
        }
        strokes.forEach { stroke ->
            if (stroke.size > 1) canvas.drawPath(pathFrom(stroke), inkPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                strokes += mutableListOf(normalize(event.x, event.y))
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val stroke = strokes.lastOrNull() ?: return false
                for (index in 0 until event.historySize) {
                    stroke += normalize(event.getHistoricalX(index), event.getHistoricalY(index))
                }
                stroke += normalize(event.x, event.y)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
                invalidate()
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun normalize(x: Float, y: Float) = PointF(
        (x / width.coerceAtLeast(1)).coerceIn(0f, 1f),
        (y / height.coerceAtLeast(1)).coerceIn(0f, 1f)
    )

    private fun pathFrom(points: List<PointF>) = Path().apply {
        points.forEachIndexed { index, point ->
            val x = point.x * width
            val y = point.y * height
            if (index == 0) moveTo(x, y) else lineTo(x, y)
        }
    }

    private fun buildLetterPath(letter: String) {
        letterPath.reset()
        letterPaint.textSize = minOf(width * 0.62f, height * 0.74f)
        val bounds = RectF()
        letterPaint.getTextPath(
            letter,
            0,
            letter.length,
            width / 2f,
            height * 0.72f,
            letterPath
        )
        letterPath.computeBounds(bounds, true)
        val scale = minOf(
            (width * 0.58f) / bounds.width().coerceAtLeast(1f),
            (height * 0.65f) / bounds.height().coerceAtLeast(1f)
        )
        letterPath.transform(
            android.graphics.Matrix().apply {
                postScale(scale, scale, bounds.centerX(), bounds.centerY())
                postTranslate(width / 2f - bounds.centerX(), height * 0.54f - bounds.centerY())
            }
        )
    }

    private fun drawDemoPath(canvas: Canvas) {
        val measure = PathMeasure(letterPath, false)
        val contours = mutableListOf<Pair<Path, Float>>()
        var totalLength = 0f
        do {
            val length = measure.length
            if (length > 0f) {
                val contour = Path()
                measure.getSegment(0f, length, contour, true)
                contours += contour to length
                totalLength += length
            }
        } while (measure.nextContour())

        var remaining = totalLength * demoProgress
        contours.forEach { (contour, length) ->
            if (remaining <= 0f) return
            val segment = Path()
            PathMeasure(contour, false).getSegment(0f, minOf(length, remaining), segment, true)
            canvas.drawPath(segment, demoPaint)
            remaining -= length
        }
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density

    override fun onDetachedFromWindow() {
        demoAnimator?.cancel()
        super.onDetachedFromWindow()
    }

    companion object {
        private const val DEMO_DURATION_MS = 2200L
        private const val PAPER = Color.WHITE
        private const val INK = 0xFF0F172A.toInt()
        private const val GUIDE_LINE = 0xFFCBD5E1.toInt()
        private const val LETTER_GUIDE = 0x303B82F6
        private const val DEMO_COLOR = 0xFF2563EB.toInt()
    }
}
