package dev.fanworld.halolite

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/**
 * Ползунок яркости в стиле шторки уведомлений: скруглённая плашка с заливкой,
 * солнышко слева, проценты справа. onChanged — во время перетаскивания,
 * onCommit — когда палец отпущен (там пишем в систему, чтобы вспышка была одна).
 */
class BrightnessSliderView(
    context: Context,
    private val track: Int,
    private val fill: Int,
    private val stroke: Int,
    private val iconOn: Int,
    private val iconOff: Int,
    private val textColor: Int,
    initial: Int
) : View(context) {

    var value: Int = initial.coerceIn(RingBrightness.MIN, RingBrightness.MAX)
        private set
    var onChanged: ((Int) -> Unit)? = null
    var onCommit: ((Int) -> Unit)? = null

    private val d = resources.displayMetrics.density
    private val rect = RectF()
    private val clip = Path()
    private val paintTrack = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = track }
    private val paintFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill }
    private val paintStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = stroke; style = Paint.Style.STROKE; strokeWidth = d
    }
    private val paintIcon = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f * d; strokeCap = Paint.Cap.ROUND
    }
    private val paintIconFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor; textSize = 14f * d; isFakeBoldText = true; textAlign = Paint.Align.RIGHT
    }

    fun setValue(v: Int) {
        value = v.coerceIn(RingBrightness.MIN, RingBrightness.MAX)
        invalidate()
    }

    private fun fraction() = (value - RingBrightness.MIN).toFloat() / (RingBrightness.MAX - RingBrightness.MIN)

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(MeasureSpec.getSize(w), (56 * d).toInt())
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = h / 2f
        rect.set(d, d, w - d, h - d)

        c.drawRoundRect(rect, r, r, paintTrack)

        // заливка обрезана по скруглённой плашке
        clip.reset()
        clip.addRoundRect(rect, r, r, Path.Direction.CW)
        c.save()
        c.clipPath(clip)
        c.drawRect(0f, 0f, w * fraction(), h, paintFill)
        c.restore()

        c.drawRoundRect(rect, r, r, paintStroke)

        // солнышко
        val cx = 28f * d
        val cy = h / 2f
        val iconColor = if (w * fraction() > cx + 8f * d) iconOn else iconOff
        paintIcon.color = iconColor
        paintIconFill.color = iconColor
        c.drawCircle(cx, cy, 4.5f * d, paintIconFill)
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0)
            val x1 = cx + (8f * d) * cos(a).toFloat()
            val y1 = cy + (8f * d) * sin(a).toFloat()
            val x2 = cx + (11f * d) * cos(a).toFloat()
            val y2 = cy + (11f * d) * sin(a).toFloat()
            c.drawLine(x1, y1, x2, y2, paintIcon)
        }

        c.drawText(
            "${RingBrightness.percent(value)}%",
            w - 20f * d,
            cy - (paintText.descent() + paintText.ascent()) / 2f,
            paintText
        )
    }

    private fun update(x: Float) {
        val f = (x / width).coerceIn(0f, 1f)
        val v = (RingBrightness.MIN + f * (RingBrightness.MAX - RingBrightness.MIN)).toInt()
        if (v != value) {
            value = v
            invalidate()
            onChanged?.invoke(v)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                update(e.x)
            }
            MotionEvent.ACTION_MOVE -> update(e.x)
            MotionEvent.ACTION_UP -> {
                update(e.x)
                parent?.requestDisallowInterceptTouchEvent(false)
                onCommit?.invoke(value)
            }
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                onCommit?.invoke(value)
            }
        }
        return true
    }
}
