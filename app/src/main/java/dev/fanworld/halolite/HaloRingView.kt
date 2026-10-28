package dev.fanworld.halolite

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator

/** Логотип в шапке: «линза камеры» с вращающимся радужным кольцом. */
class HaloRingView(context: Context) : View(context) {

    private val d = resources.displayMetrics.density
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lens = Paint(Paint.ANTI_ALIAS_FLAG)
    private val spec = Paint(Paint.ANTI_ALIAS_FLAG)
    private val colors = IntArray(8) { Color.HSVToColor(floatArrayOf((it % 7) * 360f / 7f, 0.8f, 1f)) }
    private var sweep: SweepGradient? = null
    private var lensShader: RadialGradient? = null
    private var angle = 0f
    private var animator: ValueAnimator? = null

    init {
        ring.style = Paint.Style.STROKE
        ring.strokeWidth = 5f * d
        ring.strokeCap = Paint.Cap.ROUND
        glow.style = Paint.Style.STROKE
        glow.strokeWidth = 10f * d
        glow.alpha = 60
        spec.color = Color.argb(90, 255, 255, 255)
    }

    override fun onMeasure(w: Int, h: Int) {
        val s = (48f * d).toInt()
        setMeasuredDimension(s, s)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val cx = w / 2f
        val cy = h / 2f
        val r = minOf(w, h) / 2f
        sweep = SweepGradient(cx, cy, colors, null)
        lensShader = RadialGradient(
            cx - r * 0.2f, cy - r * 0.25f, r,
            Color.parseColor("#34374A"), Color.parseColor("#0A0B10"), Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) / 2f - 7f * d

        lens.shader = lensShader
        c.drawCircle(cx, cy, r - 3f * d, lens)

        c.save()
        c.rotate(angle, cx, cy)
        glow.shader = sweep
        ring.shader = sweep
        c.drawCircle(cx, cy, r, glow)
        c.drawCircle(cx, cy, r, ring)
        c.restore()

        // блик линзы
        c.drawCircle(cx - r * 0.38f, cy - r * 0.42f, 2.6f * d, spec)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 5000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                angle = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    /** Короткий «толчок» — при запуске анимации. */
    fun pulse() {
        animate().cancel()
        animate().scaleX(1.2f).scaleY(1.2f).setDuration(120).withEndAction {
            animate().scaleX(1f).scaleY(1f).setDuration(420)
                .setInterpolator(OvershootInterpolator(3f)).start()
        }.start()
    }
}
