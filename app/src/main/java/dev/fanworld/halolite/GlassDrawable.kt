package dev.fanworld.halolite

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator

/**
 * Объёмное «стекло»: полупрозрачная заливка с вертикальным градиентом, зеркальный блик сверху,
 * затемнение снизу, светлая кромка и свечение у выбранного состояния.
 *
 * sel   0..1 — состояние «выбрано»: стекло плавно превращается в акцентную заливку,
 *              а форма морфится из radiusRest в radiusSelected (с перелётом).
 * press 0..1 — подсветка при нажатии.
 * pad        — отступ тела от границ view (место под свечение).
 */
class GlassDrawable(
    private val density: Float,
    private val accent: Int,
    private val radiusRest: Float,
    private val radiusSelected: Float = radiusRest,
    private val pad: Float = 0f,
    selected: Boolean = false
) : Drawable() {

    var sel: Float = if (selected) 1f else 0f
        private set
    var press: Float = 0f
        private set

    private val eval = ArgbEvaluator()
    private val body = RectF()
    private val glowRect = RectF()
    private val path = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var selAnim: ValueAnimator? = null
    private var pressAnim: ValueAnimator? = null

    fun selectTo(on: Boolean, animate: Boolean = true) {
        selAnim?.cancel()
        val to = if (on) 1f else 0f
        if (!animate) {
            sel = to
            invalidateSelf()
            return
        }
        selAnim = ValueAnimator.ofFloat(sel, to).apply {
            duration = 420
            interpolator = OvershootInterpolator(1.9f)
            addUpdateListener {
                sel = it.animatedValue as Float
                invalidateSelf()
            }
            start()
        }
    }

    fun pressTo(on: Boolean) {
        pressAnim?.cancel()
        pressAnim = ValueAnimator.ofFloat(press, if (on) 1f else 0f).apply {
            duration = if (on) 90L else 260L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                press = it.animatedValue as Float
                invalidateSelf()
            }
            start()
        }
    }

    private fun mix(a: Int, b: Int, t: Float): Int = eval.evaluate(t.coerceIn(0f, 1f), a, b) as Int

    private fun withAlpha(color: Int, a: Int): Int =
        (color and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)

    override fun draw(c: Canvas) {
        val b = bounds
        if (b.width() <= 0 || b.height() <= 0) return
        body.set(b.left + pad, b.top + pad, b.right - pad, b.bottom - pad)

        val s = sel.coerceIn(0f, 1f)
        val maxR = minOf(body.width(), body.height()) / 2f
        val r = (radiusRest + (radiusSelected - radiusRest) * sel).coerceIn(0f, maxR)

        // свечение под выбранным (концентрические слои, от большего к меньшему)
        if (s > 0.01f) {
            for (i in 4 downTo 1) {
                val g = i * density
                glowRect.set(body.left - g, body.top - g, body.right + g, body.bottom + g)
                paint.reset()
                paint.isAntiAlias = true
                paint.color = withAlpha(accent, (s * (46 - i * 9)).toInt())
                c.drawRoundRect(glowRect, r + g, r + g, paint)
            }
        }

        // заливка: стекло -> акцент
        val glassTop = Color.argb(0x30, 255, 255, 255)
        val glassBot = Color.argb(0x0E, 255, 255, 255)
        val accTop = mix(accent, Color.WHITE, 0.28f)
        val accBot = mix(accent, Color.BLACK, 0.22f)
        paint.reset()
        paint.isAntiAlias = true
        paint.shader = LinearGradient(
            0f, body.top, 0f, body.bottom,
            mix(glassTop, accTop, s), mix(glassBot, accBot, s), Shader.TileMode.CLAMP
        )
        c.drawRoundRect(body, r, r, paint)

        path.reset()
        path.addRoundRect(body, r, r, Path.Direction.CW)
        c.save()
        c.clipPath(path)

        // зеркальный блик сверху
        val hiBottom = body.top + body.height() * 0.55f
        paint.reset()
        paint.isAntiAlias = true
        paint.shader = LinearGradient(
            0f, body.top, 0f, hiBottom,
            Color.argb((70 + 30 * s).toInt(), 255, 255, 255), Color.argb(0, 255, 255, 255),
            Shader.TileMode.CLAMP
        )
        c.drawRect(body.left, body.top, body.right, hiBottom, paint)

        // затемнение снизу — объём
        val shTop = body.top + body.height() * 0.5f
        paint.reset()
        paint.isAntiAlias = true
        paint.shader = LinearGradient(
            0f, shTop, 0f, body.bottom,
            Color.argb(0, 0, 0, 0), Color.argb((46 + 20 * s).toInt(), 0, 0, 0),
            Shader.TileMode.CLAMP
        )
        c.drawRect(body.left, shTop, body.right, body.bottom, paint)

        // нажатие
        if (press > 0.01f) {
            paint.reset()
            paint.isAntiAlias = true
            paint.color = Color.argb((press * 36).toInt().coerceIn(0, 255), 255, 255, 255)
            c.drawRect(body, paint)
        }
        c.restore()

        // кромка
        val half = density / 2f
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = density
        paint.shader = LinearGradient(
            0f, body.top, 0f, body.bottom,
            Color.argb((110 + 60 * s).toInt(), 255, 255, 255),
            Color.argb((18 + 20 * s).toInt(), 255, 255, 255),
            Shader.TileMode.CLAMP
        )
        val rr = (r - half).coerceAtLeast(0f)
        c.drawRoundRect(
            body.left + half, body.top + half, body.right - half, body.bottom - half,
            rr, rr, paint
        )
    }

    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
