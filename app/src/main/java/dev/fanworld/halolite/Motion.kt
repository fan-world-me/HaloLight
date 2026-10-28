package dev.fanworld.halolite

import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator

/** MD3 Expressive spring: fast compress, bouncy release with overshoot. */
fun View.bounceOnTouch(down: Float = 0.92f, glass: GlassDrawable? = null) {
    setOnTouchListener { v, e ->
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                v.animate().cancel()
                v.animate().scaleX(down).scaleY(down).setDuration(80)
                    .setInterpolator(DecelerateInterpolator()).start()
                glass?.pressTo(true)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                v.animate().cancel()
                v.animate().scaleX(1f).scaleY(1f).setDuration(420)
                    .setInterpolator(OvershootInterpolator(3.6f)).start()
                glass?.pressTo(false)
            }
        }
        false
    }
}

/** Cascaded entrance from below — expressive ease-out. */
fun View.enterFromBelow(delayMs: Long, distancePx: Float) {
    alpha = 0f
    translationY = distancePx
    animate().alpha(1f).translationY(0f).setStartDelay(delayMs).setDuration(520)
        .setInterpolator(PathInterpolator(0.2f, 0.9f, 0.2f, 1f)).start()
}

/** Pop-in with strong overshoot (MD3 Expressive). */
fun View.popIn(delayMs: Long) {
    scaleX = 0.35f
    scaleY = 0.35f
    animate().scaleX(1f).scaleY(1f).setStartDelay(delayMs).setDuration(620)
        .setInterpolator(OvershootInterpolator(2.9f)).start()
}
