package dev.fanworld.halolite

import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator

/** Пружинящая реакция на нажатие: быстро сжимаем, при отпускании отпружиниваем с перелётом. */
fun View.bounceOnTouch(down: Float = 0.94f, glass: GlassDrawable? = null) {
    setOnTouchListener { v, e ->
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                v.animate().cancel()
                v.animate().scaleX(down).scaleY(down).setDuration(90)
                    .setInterpolator(DecelerateInterpolator()).start()
                glass?.pressTo(true)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                v.animate().cancel()
                v.animate().scaleX(1f).scaleY(1f).setDuration(380)
                    .setInterpolator(OvershootInterpolator(3.2f)).start()
                glass?.pressTo(false)
            }
        }
        false
    }
}

/** Появление секции снизу вверх с задержкой (каскад). */
fun View.enterFromBelow(delayMs: Long, distancePx: Float) {
    alpha = 0f
    translationY = distancePx
    animate().alpha(1f).translationY(0f).setStartDelay(delayMs).setDuration(460)
        .setInterpolator(DecelerateInterpolator(2.2f)).start()
}

/** «Поп»-появление: из маленького масштаба с перелётом. */
fun View.popIn(delayMs: Long) {
    scaleX = 0.4f
    scaleY = 0.4f
    animate().scaleX(1f).scaleY(1f).setStartDelay(delayMs).setDuration(560)
        .setInterpolator(OvershootInterpolator(2.6f)).start()
}
