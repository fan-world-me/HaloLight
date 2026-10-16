package dev.fanworld.halolite

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * Кнопка «↻» с анимацией вращения.
 * Рисует символ через canvas.rotate() вокруг точного центра View.
 * Никаких pivot-проблем — rotation применяется к canvas, а не к View.
 */
class SyncButtonView @JvmOverloads constructor(
    ctx: Context,
    attrs: AttributeSet? = null
) : View(ctx, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
        color = Color.parseColor("#22D3EE")
    }

    private var angle = 0f
    private var spinning = false
    private var animator: ValueAnimator? = null

    var onClickAction: (() -> Unit)? = null

    init {
        setOnClickListener { onClickAction?.invoke() }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        // размер шрифта = 55% высоты View
        paint.textSize = h * 0.55f
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f

        canvas.save()
        canvas.rotate(angle, cx, cy)

        // вертикальное центрирование: смещаем на половину высоты текста
        val textY = cy - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText("↻", cx, textY, paint)

        canvas.restore()
    }

    fun startSpin() {
        if (spinning) return
        spinning = true
        animator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 700
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { va ->
                angle = va.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun stopSpin() {
        spinning = false
        animator?.cancel()
        animator = null
        angle = 0f
        invalidate()
    }
}
