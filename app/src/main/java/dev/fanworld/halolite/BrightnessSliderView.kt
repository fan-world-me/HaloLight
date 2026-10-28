package dev.fanworld.halolite

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator

/**
 * Ползунок яркости как в системных настройках: широкая таблетка, заливка до ручки,
 * круглая ручка внутри конца заливки. Заливка и ручка с объёмом (блик сверху, тень снизу).
 * При нажатии ручка «вздувается» с пружиной.
 *
 * onChanged — во время перетаскивания (пишем в систему в реальном времени),
 * onCommit — когда палец отпущен.
 */
class BrightnessSliderView(
    context: Context,
    private val trackColor: Int,
    private val fillStart: Int,
    private val fillEnd: Int,
    private val thumbColor: Int,
    initial: Int
) : View(context) {

    var value: Int = initial.coerceIn(RingBrightness.MIN, RingBrightness.MAX)
        private set
    var onChanged: ((Int) -> Unit)? = null
    var onCommit: ((Int) -> Unit)? = null

    private val d = resources.displayMetrics.density
    private val h = 48f * d
    private val inset = 6f * d

    private var press = 0f
    private var pressAnim: ValueAnimator? = null
    private var lastTick = -1

    private val trackRect = RectF()
    private val fillRect = RectF()
    private val trackPath = Path()
    private val fillPath = Path()
    private val paintTrack = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintGloss = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintShade = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintRim = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintThumb = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintThumbShadow = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        paintRim.style = Paint.Style.STROKE
        paintRim.strokeWidth = d
        paintRim.color = Color.argb(46, 255, 255, 255)
        paintThumbShadow.color = Color.argb(70, 0, 0, 0)
        paintTrack.color = trackColor
    }

    fun fraction(): Float =
        (value - RingBrightness.MIN).toFloat() / (RingBrightness.MAX - RingBrightness.MIN)

    override fun onMeasure(w: Int, hSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(w), h.toInt())
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val cy = height / 2f
        val top = cy - h / 2f
        val bottom = cy + h / 2f
        val r = h / 2f
        val f = fraction()
        val thumbCx = r + (w - h) * f
        val fillRight = thumbCx + r

        // трек
        trackRect.set(0f, top, w, bottom)
        c.drawRoundRect(trackRect, r, r, paintTrack)

        trackPath.reset()
        trackPath.addRoundRect(trackRect, r, r, Path.Direction.CW)
        c.save()
        c.clipPath(trackPath)

        // заливка
        fillRect.set(0f, top, fillRight, bottom)
        paintFill.shader = LinearGradient(
            0f, 0f, maxOf(fillRight, 1f), 0f, fillStart, fillEnd, Shader.TileMode.CLAMP
        )
        c.drawRoundRect(fillRect, r, r, paintFill)

        fillPath.reset()
        fillPath.addRoundRect(fillRect, r, r, Path.Direction.CW)
        c.save()
        c.clipPath(fillPath)
        paintGloss.shader = LinearGradient(
            0f, top, 0f, cy,
            Color.argb((80 + 30 * press).toInt().coerceIn(0, 255), 255, 255, 255),
            Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP
        )
        c.drawRect(0f, top, fillRight, cy, paintGloss)
        paintShade.shader = LinearGradient(
            0f, cy, 0f, bottom, Color.argb(0, 0, 0, 0), Color.argb(56, 0, 0, 0), Shader.TileMode.CLAMP
        )
        c.drawRect(0f, cy, fillRight, bottom, paintShade)
        c.restore()
        c.restore()

        // кромка трека
        val half = d / 2f
        c.drawRoundRect(
            half, top + half, w - half, bottom - half, r - half, r - half, paintRim
        )

        // ручка
        val tr = (r - inset) * (1f + 0.12f * press)
        c.drawCircle(thumbCx, cy + d, tr + d, paintThumbShadow)
        paintThumb.shader = LinearGradient(
            0f, cy - tr, 0f, cy + tr, Color.WHITE, thumbColor, Shader.TileMode.CLAMP
        )
        c.drawCircle(thumbCx, cy, tr, paintThumb)
    }

    private fun animatePress(on: Boolean) {
        pressAnim?.cancel()
        pressAnim = ValueAnimator.ofFloat(press, if (on) 1f else 0f).apply {
            duration = if (on) 160L else 380L
            interpolator = if (on) DecelerateInterpolator() else OvershootInterpolator(2.4f)
            addUpdateListener {
                press = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun update(x: Float) {
        val span = width - h
        if (span <= 0f) return
        val f = ((x - h / 2f) / span).coerceIn(0f, 1f)
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
                animatePress(true)
                update(e.x)
            }
            MotionEvent.ACTION_MOVE -> update(e.x)
            MotionEvent.ACTION_UP -> {
                update(e.x)
                parent?.requestDisallowInterceptTouchEvent(false)
                animatePress(false)
                onCommit?.invoke(value)
            }
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                animatePress(false)
                onCommit?.invoke(value)
            }
        }
        return true
    }
}

/**
 * Солнышко рядом с подписью яркости: лучи удлиняются, ядро растёт и вся иконка
 * поворачивается вместе с ползунком.
 */
class SunView(context: Context, tint: Int) : View(context) {
    private var level = 1f
    private val core = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rays = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        core.color = tint
        rays.color = tint
        rays.style = Paint.Style.STROKE
        rays.strokeCap = Paint.Cap.ROUND
    }

    fun setLevel(f: Float) {
        level = f.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val s = minOf(width, height).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        val a = (150 + 105 * level).toInt()
        core.alpha = a
        rays.alpha = a
        rays.strokeWidth = s * 0.075f
        c.drawCircle(cx, cy, s * (0.19f + 0.06f * level), core)
        val inner = s * 0.33f
        val outer = s * (0.38f + 0.12f * level)
        c.save()
        c.rotate(level * 90f, cx, cy)
        for (i in 0 until 8) {
            c.save()
            c.rotate(i * 45f, cx, cy)
            c.drawLine(cx, cy - inner, cx, cy - outer, rays)
            c.restore()
        }
        c.restore()
    }
}
