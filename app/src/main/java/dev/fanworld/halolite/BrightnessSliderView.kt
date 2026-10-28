package dev.fanworld.halolite

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/**
 * Ползунок яркости в стиле Material 3 Expressive.
 *
 * - толстый трек (56dp) с большим скруглением снаружи и маленьким (2dp) у ручки;
 * - ручка — отдельная вертикальная «таблетка» (4dp x 68dp) с зазором 6dp от обоих треков,
 *   при нажатии сужается до 2dp;
 * - иконка и процент лежат внутри трека и перекрашиваются в зависимости от того,
 *   над активной или неактивной частью они находятся;
 * - stop-индикатор (точка) в конце неактивного трека;
 * - тактильный отклик на каждые 5% значения.
 *
 * onChanged — во время перетаскивания (пишем в систему в реальном времени),
 * onCommit — когда палец отпущен.
 */
class BrightnessSliderView(
    context: Context,
    private val activeTrack: Int,
    private val inactiveTrack: Int,
    private val handleColor: Int,
    private val iconOnActive: Int,
    private val iconOnInactive: Int,
    private val textColor: Int,
    initial: Int
) : View(context) {

    var value: Int = initial.coerceIn(RingBrightness.MIN, RingBrightness.MAX)
        private set
    var onChanged: ((Int) -> Unit)? = null
    var onCommit: ((Int) -> Unit)? = null

    private val d = resources.displayMetrics.density
    private val trackH = 56f * d
    private val handleH = 68f * d
    private val handleWRest = 4f * d
    private val handleWPressed = 2f * d
    private val gap = 6f * d
    private val innerR = 2f * d

    private var press = 0f
    private var pressAnim: ValueAnimator? = null
    private var lastTick = -1

    private val rect = RectF()
    private val path = Path()
    private val paintActive = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = activeTrack }
    private val paintInactive = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = inactiveTrack }
    private val paintHandle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = handleColor }
    private val paintStop = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = handleColor }
    private val paintIcon = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f * d; strokeCap = Paint.Cap.ROUND
    }
    private val paintIconFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 15f * d; isFakeBoldText = true; textAlign = Paint.Align.LEFT
    }

    fun setValue(v: Int) {
        value = v.coerceIn(RingBrightness.MIN, RingBrightness.MAX)
        invalidate()
    }

    private fun fraction() =
        (value - RingBrightness.MIN).toFloat() / (RingBrightness.MAX - RingBrightness.MIN)

    private fun minX() = gap + handleWRest / 2f
    private fun maxX() = width - gap - handleWRest / 2f

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(MeasureSpec.getSize(w), handleH.toInt())
    }

    private fun roundedPath(r: RectF, tl: Float, tr: Float, br: Float, bl: Float): Path {
        path.reset()
        path.addRoundRect(r, floatArrayOf(tl, tl, tr, tr, br, br, bl, bl), Path.Direction.CW)
        return path
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val cy = height / 2f
        val top = cy - trackH / 2f
        val bottom = cy + trackH / 2f
        val outer = trackH / 2f

        val hw = handleWRest + (handleWPressed - handleWRest) * press
        val hx = minX() + (maxX() - minX()) * fraction()
        val activeRight = hx - hw / 2f - gap
        val inactiveLeft = hx + hw / 2f + gap

        // активный трек
        if (activeRight > 1f) {
            rect.set(0f, top, activeRight, bottom)
            c.drawPath(roundedPath(rect, outer, innerR, innerR, outer), paintActive)
            drawContent(c, 0f, activeRight, top, bottom, iconOnActive)
        }

        // неактивный трек
        if (w - inactiveLeft > 1f) {
            rect.set(inactiveLeft, top, w, bottom)
            c.drawPath(roundedPath(rect, innerR, outer, outer, innerR), paintInactive)
            drawContent(c, inactiveLeft, w, top, bottom, iconOnInactive)

            // stop-индикатор
            if (w - inactiveLeft > 28f * d) {
                c.drawCircle(w - outer / 2f - 2f * d, cy, 2f * d, paintStop)
            }
        }

        // ручка
        rect.set(hx - hw / 2f, cy - handleH / 2f, hx + hw / 2f, cy + handleH / 2f)
        c.drawRoundRect(rect, hw / 2f, hw / 2f, paintHandle)
    }

    /** Иконка и процент, обрезанные по [left, right] и окрашенные в [color]. */
    private fun drawContent(c: Canvas, left: Float, right: Float, top: Float, bottom: Float, color: Int) {
        c.save()
        c.clipRect(left, top, right, bottom)

        val cx = 28f * d
        val cy = height / 2f
        paintIcon.color = color
        paintIconFill.color = color
        c.drawCircle(cx, cy, 4.5f * d, paintIconFill)
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0)
            val x1 = cx + (8f * d) * cos(a).toFloat()
            val y1 = cy + (8f * d) * sin(a).toFloat()
            val x2 = cx + (11f * d) * cos(a).toFloat()
            val y2 = cy + (11f * d) * sin(a).toFloat()
            c.drawLine(x1, y1, x2, y2, paintIcon)
        }

        paintText.color = color
        c.drawText(
            "${RingBrightness.percent(value)}%",
            52f * d,
            cy - (paintText.descent() + paintText.ascent()) / 2f,
            paintText
        )
        c.restore()
    }

    private fun animatePress(to: Float) {
        pressAnim?.cancel()
        pressAnim = ValueAnimator.ofFloat(press, to).apply {
            duration = 150
            addUpdateListener { press = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    private fun update(x: Float) {
        val span = maxX() - minX()
        if (span <= 0f) return
        val f = ((x - minX()) / span).coerceIn(0f, 1f)
        val v = (RingBrightness.MIN + f * (RingBrightness.MAX - RingBrightness.MIN)).toInt()

        val bucket = (f * 20f).toInt()
        if (bucket != lastTick) {
            lastTick = bucket
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }

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
                animatePress(1f)
                update(e.x)
            }
            MotionEvent.ACTION_MOVE -> update(e.x)
            MotionEvent.ACTION_UP -> {
                update(e.x)
                parent?.requestDisallowInterceptTouchEvent(false)
                animatePress(0f)
                onCommit?.invoke(value)
            }
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                animatePress(0f)
                onCommit?.invoke(value)
            }
        }
        return true
    }
}
