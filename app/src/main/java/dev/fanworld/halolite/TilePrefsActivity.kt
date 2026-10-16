package dev.fanworld.halolite

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

object HaloPrefs {
    private const val FILE = "halolite"
    private const val KEY_DURATION = "duration_sec"
    const val DEFAULT_SEC = 5
    const val GITHUB_URL = "https://github.com/fan-world-me/HyperOS-ColorLightManager-Research"

    fun durationSec(ctx: Context): Int =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt(KEY_DURATION, DEFAULT_SEC)

    fun durationMs(ctx: Context): Long = durationSec(ctx) * 1000L

    fun setDurationSec(ctx: Context, sec: Int) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putInt(KEY_DURATION, sec).apply()
    }
}

class TilePrefsActivity : Activity() {

    private val options = listOf(3, 5, 10, 15, 30)

    private val colPanel = Color.parseColor("#1F2028")
    private val colCard = Color.parseColor("#12131A")
    private val colAccent = Color.parseColor("#22D3EE")
    private val colOnAccent = Color.parseColor("#06222A")
    private val colText = Color.parseColor("#ECEDF5")
    private val colDim = Color.parseColor("#9A9CB0")
    private val colStroke = Color.parseColor("#33FFFFFF")

    private val buttons = mutableMapOf<Int, TextView>()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#88000000"))
            setOnClickListener { finish() }
        }

        val r = dp(28).toFloat()
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(12), dp(24), dp(32))
            background = GradientDrawable().apply {
                setColor(colPanel)
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
            isClickable = true // тап по панели не закрывает окно
        }

        // ручка сверху
        panel.addView(View(this).apply {
            background = GradientDrawable().apply {
                setColor(colStroke)
                cornerRadius = dp(2).toFloat()
            }
        }, LinearLayout.LayoutParams(dp(40), dp(4)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(20)
        })

        // заголовок
        panel.addView(text("HaloLite", 26f, colText, bold = true))
        panel.addView(text("RGB-кольцо камеры", 13f, colDim).also {
            (it.layoutParams as LinearLayout.LayoutParams).topMargin = dp(2)
        })

        // выбор длительности
        panel.addView(text("Длительность анимации, сек", 14f, colDim).also {
            (it.layoutParams as LinearLayout.LayoutParams).topMargin = dp(28)
        })

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val current = HaloPrefs.durationSec(this)
        options.forEach { sec ->
            val cell = FrameLayout(this)
            val btn = TextView(this).apply {
                text = sec.toString()
                textSize = 18f
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                gravity = Gravity.CENTER
                setOnClickListener {
                    HaloPrefs.setDurationSec(this@TilePrefsActivity, sec)
                    refresh(sec)
                    animate().scaleX(0.88f).scaleY(0.88f).setDuration(70).withEndAction {
                        animate().scaleX(1f).scaleY(1f).setDuration(110).start()
                    }.start()
                }
            }
            buttons[sec] = btn
            cell.addView(btn, FrameLayout.LayoutParams(dp(52), dp(52), Gravity.CENTER))
            row.addView(cell, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        panel.addView(row, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(14) })
        refresh(current)

        panel.addView(text("По умолчанию: ${HaloPrefs.DEFAULT_SEC} сек", 12f, colDim).also {
            (it.layoutParams as LinearLayout.LayoutParams).topMargin = dp(12)
        })

        // кнопка GitHub
        val gh = TextView(this).apply {
            text = "Исходный код на GitHub  ↗"
            textSize = 15f
            setTextColor(colAccent)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(colCard)
                setStroke(dp(1), colStroke)
                cornerRadius = dp(18).toFloat()
            }
            setOnClickListener {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(HaloPrefs.GITHUB_URL)))
                } catch (_: ActivityNotFoundException) {
                }
                finish()
            }
        }
        panel.addView(gh, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(56)
        ).apply { topMargin = dp(28) })

        root.addView(panel, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        ))
        setContentView(root)

        // выезд снизу
        panel.post {
            panel.translationY = panel.height.toFloat()
            panel.animate().translationY(0f).setDuration(220).start()
        }
    }

    private fun refresh(selected: Int) {
        buttons.forEach { (sec, tv) ->
            val on = sec == selected
            tv.setTextColor(if (on) colOnAccent else colText)
            tv.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                if (on) {
                    setColor(colAccent)
                } else {
                    setColor(colCard)
                    setStroke(dp(1), colStroke)
                }
            }
        }
    }

    private fun text(s: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = s
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
}
