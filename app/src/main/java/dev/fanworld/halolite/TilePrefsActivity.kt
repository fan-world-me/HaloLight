package dev.fanworld.halolite

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------
// HaloPrefs — настройки приложения
// ---------------------------------------------------------------------------

object HaloPrefs {
    private const val FILE = "halolite"
    private const val KEY_DURATION = "duration_sec"
    private const val KEY_CUSTOM_HISTORY = "custom_history"   // хранится как "10,45,120"
    const val DEFAULT_SEC = 5
    const val GITHUB_URL = "https://github.com/fan-world-me/HaloLight"
    const val MAX_CUSTOM_HISTORY = 3

    fun durationSec(ctx: Context): Int =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt(KEY_DURATION, DEFAULT_SEC)

    fun durationMs(ctx: Context): Long = durationSec(ctx) * 1000L

    fun setDurationSec(ctx: Context, sec: Int) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putInt(KEY_DURATION, sec).apply()
    }

    /** Последние MAX_CUSTOM_HISTORY кастомных значений, новые — в начале. */
    fun customHistory(ctx: Context): List<Int> {
        val raw = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY_CUSTOM_HISTORY, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split(',').mapNotNull { it.trim().toIntOrNull() }
    }

    fun addCustomHistory(ctx: Context, sec: Int) {
        val list = customHistory(ctx).toMutableList()
        list.remove(sec)           // убираем дубли
        list.add(0, sec)           // новое — вперёд
        val trimmed = list.take(MAX_CUSTOM_HISTORY)
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString(KEY_CUSTOM_HISTORY, trimmed.joinToString(",")).apply()
    }
}

// ---------------------------------------------------------------------------
// TilePrefsActivity — bottom sheet: длительность, запуск, GitHub, ритм
// ---------------------------------------------------------------------------

class TilePrefsActivity : Activity() {

    private val presetOptions = listOf(3, 5, 10, 15, 30)

    private val colPanel   = Color.parseColor("#1F2028")
    private val colCard    = Color.parseColor("#12131A")
    private val colAccent  = Color.parseColor("#22D3EE")
    private val colOnAccent = Color.parseColor("#06222A")
    private val colText    = Color.parseColor("#ECEDF5")
    private val colDim     = Color.parseColor("#9A9CB0")
    private val colStroke  = Color.parseColor("#33FFFFFF")

    // все кнопки выбора секунд: пресет + кастомные из истории
    private val durationButtons = mutableMapOf<Int, TextView>()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // контейнер музыкальных строк — нужен для ре-рендера при синхронизации
    private lateinit var musicContainer: LinearLayout

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // -----------------------------------------------------------------------

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
            isClickable = true
        }

        // ручка
        panel.addView(View(this).apply {
            background = GradientDrawable().apply {
                setColor(colStroke); cornerRadius = dp(2).toFloat()
            }
        }, LinearLayout.LayoutParams(dp(40), dp(4)).apply {
            gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(20)
        })

        // заголовок
        panel.addView(text("HaloLight", 26f, colText, bold = true))
        panel.addView(text("RGB-кольцо камеры", 13f, colDim).also {
            (it.layoutParams as LinearLayout.LayoutParams).topMargin = dp(2)
        })

        // ── Длительность анимации ──────────────────────────────────────────
        panel.addView(text("Длительность анимации, сек", 14f, colDim).also {
            (it.layoutParams as LinearLayout.LayoutParams).topMargin = dp(28)
        })

        val current = HaloPrefs.durationSec(this)
        val history = HaloPrefs.customHistory(this)

        // Строка пресетов + «+» для кастом
        val presetRow = buildPresetRow(current)
        panel.addView(presetRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(14) })

        // Строка кастомных значений из истории (если есть)
        val historyRow = buildHistoryRow(current, history)
        panel.addView(historyRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })

        refreshDurationButtons(current)

        panel.addView(text("По умолчанию: ${HaloPrefs.DEFAULT_SEC} сек", 12f, colDim).also {
            (it.layoutParams as LinearLayout.LayoutParams).topMargin = dp(10)
        })

        // ── Кнопка «Запустить анимацию» ───────────────────────────────────
        panel.addView(buildActionButton("Запустить анимацию") {
            // запускаем сервис, НЕ закрываем меню
            startForegroundService(Intent(this, LightService::class.java))
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
        ).apply { topMargin = dp(20) })

        // ── GitHub ────────────────────────────────────────────────────────
        panel.addView(buildGithubButton(), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
        ).apply { topMargin = dp(10) })

        // ── Ритмичная подсветка по приложениям ────────────────────────────
        // Заголовок блока + кнопка «Синхронизировать»
        val rhythmHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        rhythmHeader.addView(
            text("Ритмичная подсветка по приложениям", 14f, colDim),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        val syncBtn = TextView(this).apply {
            text = "↻"
            textSize = 18f
            setTextColor(colAccent)
            gravity = Gravity.CENTER
            setPadding(dp(8), 0, 0, 0)
            setOnClickListener { loadMusicApps(musicContainer) }
        }
        rhythmHeader.addView(syncBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        panel.addView(rhythmHeader, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(28) })

        musicContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        panel.addView(musicContainer, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })

        loadMusicApps(musicContainer)

        root.addView(panel, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        ))
        setContentView(root)

        panel.post {
            panel.translationY = panel.height.toFloat()
            panel.animate().translationY(0f).setDuration(220).start()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // ── Строка пресетов (3/5/10/15/30/[+]) ─────────────────────────────────

    private fun buildPresetRow(current: Int): LinearLayout {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        presetOptions.forEach { sec ->
            row.addView(buildDurationCell(sec, current))
        }
        // кнопка «+» — открыть диалог ввода кастомного значения
        val plusCell = FrameLayout(this)
        val plusBtn = TextView(this).apply {
            text = "+"
            textSize = 18f
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(colAccent)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(colCard)
                setStroke(dp(1), colStroke)
            }
            setOnClickListener { showCustomDialog() }
        }
        plusCell.addView(plusBtn, FrameLayout.LayoutParams(dp(52), dp(52), Gravity.CENTER))
        row.addView(plusCell, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }

    // ── Строка кастомных значений из истории ────────────────────────────────

    private lateinit var historyRowRef: LinearLayout

    private fun buildHistoryRow(current: Int, history: List<Int>): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        historyRowRef = row
        rebuildHistoryRow(row, current, history)
        return row
    }

    private fun rebuildHistoryRow(row: LinearLayout, current: Int, history: List<Int>) {
        row.removeAllViews()
        if (history.isEmpty()) return
        history.forEach { sec ->
            row.addView(buildDurationCell(sec, current))
        }
    }

    // ── Ячейка кнопки длительности ──────────────────────────────────────────

    private fun buildDurationCell(sec: Int, current: Int): FrameLayout {
        val cell = FrameLayout(this)
        val btn = TextView(this).apply {
            text = sec.toString()
            textSize = if (sec >= 100) 13f else 18f
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            setOnClickListener {
                HaloPrefs.setDurationSec(this@TilePrefsActivity, sec)
                refreshDurationButtons(sec)
                animate().scaleX(0.88f).scaleY(0.88f).setDuration(70).withEndAction {
                    animate().scaleX(1f).scaleY(1f).setDuration(110).start()
                }.start()
            }
        }
        durationButtons[sec] = btn
        cell.addView(btn, FrameLayout.LayoutParams(dp(52), dp(52), Gravity.CENTER))
        cell.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        return cell
    }

    // ── Диалог ввода кастомного значения ────────────────────────────────────

    private fun showCustomDialog() {
        val et = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "Секунды (1–3600)"
            setTextColor(colText)
            setHintTextColor(colDim)
            filters = arrayOf(InputFilter.LengthFilter(4))
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        val wrap = FrameLayout(this).apply {
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(et)
        }
        AlertDialog.Builder(this)
            .setTitle("Кастомная длительность")
            .setView(wrap)
            .setPositiveButton("Применить") { _, _ ->
                val v = et.text.toString().toIntOrNull()
                if (v != null && v in 1..3600) {
                    HaloPrefs.addCustomHistory(this, v)
                    HaloPrefs.setDurationSec(this, v)
                    // добавляем кнопку если ещё нет, перестраиваем историю
                    if (v !in presetOptions) {
                        val newHistory = HaloPrefs.customHistory(this)
                        rebuildHistoryRow(historyRowRef, v, newHistory)
                        // регистрируем кнопку в durationButtons если только что создана
                    }
                    refreshDurationButtons(v)
                } else {
                    Toast.makeText(this, "Введите число от 1 до 3600", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    // ── Кнопка-действие (общий стиль) ───────────────────────────────────────

    private fun buildActionButton(label: String, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            textSize = 15f
            setTextColor(colText)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(colCard)
                setStroke(dp(1), colStroke)
                cornerRadius = dp(18).toFloat()
            }
            setOnClickListener { onClick() }
        }

    private fun buildGithubButton(): TextView =
        TextView(this).apply {
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
                } catch (_: ActivityNotFoundException) {}
                finish()
            }
        }

    // ── Логика кнопок длительности ──────────────────────────────────────────

    private fun refreshDurationButtons(selected: Int) {
        durationButtons.forEach { (sec, tv) ->
            val on = sec == selected
            tv.setTextColor(if (on) colOnAccent else colText)
            tv.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                if (on) setColor(colAccent)
                else { setColor(colCard); setStroke(dp(1), colStroke) }
            }
        }
    }

    // ── Музыкальные приложения ───────────────────────────────────────────────

    private fun loadMusicApps(container: LinearLayout) {
        scope.launch {
            val apps = withContext(Dispatchers.IO) {
                MusicLightManager.resolveApps(this@TilePrefsActivity)
            }
            val states = if (apps.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    MusicLightManager.readStates(this@TilePrefsActivity)
                }
            } else emptyMap()

            container.removeAllViews()

            if (apps.isEmpty()) {
                container.addView(text("Подходящие приложения не найдены", 13f, colDim))
                return@launch
            }

            apps.forEach { app ->
                val enabled = states[app.pkg] ?: false
                container.addView(buildMusicRow(app, enabled))
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun buildMusicRow(app: MusicLightManager.MusicApp, initialEnabled: Boolean): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(colCard)
                setStroke(dp(1), colStroke)
                cornerRadius = dp(12).toFloat()
            }
            setPadding(dp(14), dp(10), dp(14), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }

        val icon = ImageView(this).apply {
            setImageDrawable(app.icon)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        row.addView(icon, LinearLayout.LayoutParams(dp(36), dp(36)).apply {
            marginEnd = dp(14)
        })

        row.addView(TextView(this).apply {
            text = app.label
            textSize = 15f
            setTextColor(colText)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val toggle = Switch(this).apply {
            isChecked = initialEnabled
            thumbTintList = android.content.res.ColorStateList.valueOf(colAccent)
            trackTintList = android.content.res.ColorStateList.valueOf(
                Color.argb(80, 0x22, 0xD3, 0xEE)
            )
            setOnCheckedChangeListener { _, checked ->
                isEnabled = false
                applyMusicToggle(app.pkg, checked, this)
            }
        }
        row.addView(toggle, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        return row
    }

    @Suppress("DEPRECATION")
    private fun applyMusicToggle(pkg: String, desired: Boolean, toggle: Switch) {
        scope.launch {
            val actual = withContext(Dispatchers.IO) {
                MusicLightManager.setEnabled(this@TilePrefsActivity, pkg, desired)
            }
            if (actual != desired) {
                toggle.isChecked = actual
                Toast.makeText(this@TilePrefsActivity, "Не удалось переключить", Toast.LENGTH_SHORT).show()
            }
            toggle.isEnabled = true
        }
    }

    // ── Хелпер ──────────────────────────────────────────────────────────────

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
