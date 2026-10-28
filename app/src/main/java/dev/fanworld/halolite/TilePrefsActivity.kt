package dev.fanworld.halolite

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------
// HaloPrefs
// ---------------------------------------------------------------------------

object HaloPrefs {
    private const val FILE = "halolite"
    private const val KEY_DURATION = "duration_sec"
    private const val KEY_CUSTOM_HISTORY = "custom_history"
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

    fun customHistory(ctx: Context): List<Int> {
        val raw = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY_CUSTOM_HISTORY, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split(',').mapNotNull { it.trim().toIntOrNull() }
    }

    fun addCustomHistory(ctx: Context, sec: Int) {
        val list = customHistory(ctx).toMutableList()
        list.remove(sec)
        list.add(0, sec)
        val trimmed = list.take(MAX_CUSTOM_HISTORY)
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString(KEY_CUSTOM_HISTORY, trimmed.joinToString(",")).apply()
    }
}

/** Запрос на запись яркости: recover = true, когда палец отпущен. */
private class BrightnessReq(val value: Int, val recover: Boolean)

/** Кнопка длительности: число, её «стекло» и текущее состояние выбора. */
private class Chip(val sec: Int, val tv: TextView, val glass: GlassDrawable) {
    var on = false
}

// ---------------------------------------------------------------------------
// TilePrefsActivity
// ---------------------------------------------------------------------------

class TilePrefsActivity : Activity() {

    private val presetOptions = listOf(3, 5, 10, 15, 30)

    private val colCard     = Color.parseColor("#12131A")
    private val colAccent   = Color.parseColor("#22D3EE")
    private val colOnAccent = Color.parseColor("#06222A")
    private val colText     = Color.parseColor("#ECEDF5")
    private val colDim      = Color.parseColor("#9A9CB0")
    private val colStroke   = Color.parseColor("#33FFFFFF")

    private val matchP = ViewGroup.LayoutParams.MATCH_PARENT
    private val wrapC = ViewGroup.LayoutParams.WRAP_CONTENT

    private val presetChips = mutableListOf<Chip>()
    private val historyChips = mutableListOf<Chip>()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Яркость пишем в IO-скоупе, который не отменяется в onDestroy: если меню закрыли сразу
    // после отпускания пальца, сброс белой вспышки всё равно должен доработать.
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val brightnessQueue = Channel<BrightnessReq>(Channel.CONFLATED)

    private lateinit var musicContainer: LinearLayout
    private lateinit var historyRowRef: LinearLayout
    private lateinit var panelRef: View
    private lateinit var scrimRef: View
    private lateinit var ringRef: HaloRingView
    private var dismissing = false

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun dpf(v: Float) = v * resources.displayMetrics.density
    private fun den() = resources.displayMetrics.density

    // -----------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this)

        val scrim = View(this).apply {
            setBackgroundColor(Color.parseColor("#99000000"))
            alpha = 0f
            setOnClickListener { dismiss() }
        }
        scrimRef = scrim
        root.addView(scrim, FrameLayout.LayoutParams(matchP, matchP))

        // фон панели: вертикальный градиент + бирюзовое свечение сверху
        val rad = dpf(32f)
        val radii = floatArrayOf(rad, rad, rad, rad, 0f, 0f, 0f, 0f)
        val base = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#2A2C3E"), Color.parseColor("#191A25"))
        )
        base.cornerRadii = radii
        base.setStroke(dp(1), Color.parseColor("#26FFFFFF"))
        val halo = GradientDrawable()
        halo.setGradientType(GradientDrawable.RADIAL_GRADIENT)
        halo.setGradientRadius(dpf(320f))
        halo.setGradientCenter(0.5f, 0f)
        halo.setColors(intArrayOf(Color.parseColor("#3322D3EE"), Color.TRANSPARENT))
        halo.cornerRadii = radii

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(14), dp(24), dp(28))
            background = LayerDrawable(arrayOf<Drawable>(base, halo))
            isClickable = true
        }

        val sections = mutableListOf<View>()
        fun addSection(v: View, top: Int) {
            panel.addView(v, LinearLayout.LayoutParams(matchP, wrapC).apply { topMargin = top })
            sections.add(v)
        }

        // ручка
        panel.addView(View(this).apply {
            background = GradientDrawable().apply {
                setColor(colStroke); cornerRadius = dp(2).toFloat()
            }
        }, LinearLayout.LayoutParams(dp(40), dp(4)).apply {
            gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(18)
        })

        // ── Шапка ──────────────────────────────────────────────────────────
        val ring = HaloRingView(this)
        ringRef = ring
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(ring, LinearLayout.LayoutParams(dp(48), dp(48)))
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(text("HaloLight", 26f, colText, bold = true))
        titles.addView(text("RGB-кольцо камеры", 13f, colDim))
        header.addView(titles, LinearLayout.LayoutParams(wrapC, wrapC).apply { marginStart = dp(14) })
        addSection(header, 0)

        // ── Яркость кольца ─────────────────────────────────────────────────
        val bSection = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val bRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val sun = SunView(this, colAccent)
        bRow.addView(sun, LinearLayout.LayoutParams(dp(28), dp(28)))
        bRow.addView(TextView(this).apply {
            text = "Яркость кольца"
            textSize = 14f
            setTextColor(colDim)
        }, LinearLayout.LayoutParams(0, wrapC, 1f).apply { marginStart = dp(10) })
        val percentTv = TextView(this).apply {
            textSize = 16f
            setTextColor(colAccent)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        }
        bRow.addView(percentTv, LinearLayout.LayoutParams(wrapC, wrapC))
        bSection.addView(bRow, LinearLayout.LayoutParams(matchP, wrapC))

        val slider = BrightnessSliderView(
            this,
            trackColor = Color.parseColor("#16323B"),
            fillStart = Color.parseColor("#1798B0"),
            fillEnd = colAccent,
            thumbColor = Color.parseColor("#CFF6FF"),
            initial = RingBrightness.get(this)
        )
        fun showBrightness(v: Int) {
            percentTv.text = "${RingBrightness.percent(v)}%"
            sun.setLevel((v - RingBrightness.MIN).toFloat() / (RingBrightness.MAX - RingBrightness.MIN))
        }
        showBrightness(slider.value)
        startBrightnessWriter()
        // в реальном времени, пока тянем
        slider.onChanged = { v ->
            showBrightness(v)
            brightnessQueue.trySend(BrightnessReq(v, false))
        }
        // палец отпущен — финальное значение и сброс белой вспышки
        slider.onCommit = { v -> brightnessQueue.trySend(BrightnessReq(v, true)) }
        bSection.addView(slider, LinearLayout.LayoutParams(matchP, wrapC).apply {
            topMargin = dp(12)
            marginStart = dp(10)
            marginEnd = dp(10)
        })
        addSection(bSection, dp(26))

        // ── Длительность анимации ──────────────────────────────────────────
        val current = HaloPrefs.durationSec(this)
        val history = HaloPrefs.customHistory(this)

        val dSection = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        dSection.addView(text("Длительность анимации, сек", 14f, colDim))
        dSection.addView(buildPresetRow(), LinearLayout.LayoutParams(matchP, wrapC).apply {
            topMargin = dp(10)
        })
        historyRowRef = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        dSection.addView(historyRowRef, LinearLayout.LayoutParams(matchP, wrapC))
        dSection.addView(text("По умолчанию: ${HaloPrefs.DEFAULT_SEC} сек", 12f, colDim).also {
            (it.layoutParams as LinearLayout.LayoutParams).topMargin = dp(6)
        })
        rebuildHistoryRow(history, current)
        refreshDurationButtons(current, true)
        addSection(dSection, dp(24))

        // ── Кнопки ─────────────────────────────────────────────────────────
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        buttons.addView(buildPrimaryButton("Запустить анимацию") {
            ringRef.pulse()
            startForegroundService(Intent(this, LightService::class.java))
        }, LinearLayout.LayoutParams(matchP, dp(60)))
        buttons.addView(buildGithubButton(), LinearLayout.LayoutParams(matchP, dp(60)))
        addSection(buttons, dp(14))

        // ── Ритмичная подсветка ────────────────────────────────────────────
        val syncBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_sync_anim)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            isClickable = true
            isFocusable = true
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener {
                android.util.Log.d("HALO_SYNC", "CLICK OK")
                loadMusicApps(musicContainer, this)
            }
            bounceOnTouch(0.85f)
        }

        val rhythm = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val rhythmHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        rhythmHeader.addView(
            text("Ритмичная подсветка по приложениям", 14f, colDim),
            LinearLayout.LayoutParams(0, wrapC, 1f)
        )
        rhythmHeader.addView(syncBtn, LinearLayout.LayoutParams(dp(48), dp(48)).apply {
            marginStart = dp(10)
        })
        rhythm.addView(rhythmHeader, LinearLayout.LayoutParams(matchP, wrapC))
        musicContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        rhythm.addView(musicContainer, LinearLayout.LayoutParams(matchP, wrapC).apply {
            topMargin = dp(8)
        })
        addSection(rhythm, dp(22))

        loadMusicApps(musicContainer, syncBtn)

        // ── Сборка и вход ──────────────────────────────────────────────────
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        scroll.addView(panel, FrameLayout.LayoutParams(matchP, wrapC))
        panelRef = scroll
        root.addView(scroll, FrameLayout.LayoutParams(matchP, wrapC, Gravity.BOTTOM))
        setContentView(root)

        sections.forEach { it.alpha = 0f }
        scroll.post {
            scroll.translationY = scroll.height.toFloat()
            scroll.animate().translationY(0f).setDuration(520)
                .setInterpolator(OvershootInterpolator(0.8f)).start()
            scrim.animate().alpha(1f).setDuration(300).start()
            sections.forEachIndexed { i, v -> v.enterFromBelow(140L + i * 60L, dpf(26f)) }
            ring.popIn(220)
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        dismiss()
    }

    private fun dismiss() {
        if (dismissing) return
        dismissing = true
        scrimRef.animate().alpha(0f).setDuration(240).start()
        panelRef.animate().translationY(panelRef.height.toFloat()).setDuration(240)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                finish()
                overridePendingTransition(0, 0)
            }.start()
    }

    override fun onDestroy() {
        scope.cancel()
        // закрываем очередь: уже лежащий в ней последний запрос ещё будет обработан
        brightnessQueue.close()
        super.onDestroy()
    }

    // ── Запись яркости ──────────────────────────────────────────────────────

    private fun startBrightnessWriter() {
        val ctx = applicationContext
        ioScope.launch {
            var dirty = false
            var recoverJob: Job? = null
            for (req in brightnessQueue) {
                if (RingBrightness.get(ctx) != req.value) {
                    // новая запись снова зажжёт белый — отложенный сброс устарел
                    recoverJob?.cancel()
                    if (RingBrightness.set(ctx, req.value)) dirty = true
                }
                if (req.recover && dirty) {
                    dirty = false
                    recoverJob?.cancel()
                    recoverJob = launch { RingBrightness.recover(ctx) }
                }
            }
        }
    }

    // ── Строка пресетов ─────────────────────────────────────────────────────

    private fun buildPresetRow(): LinearLayout {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        presetChips.clear()
        presetOptions.forEach { sec -> row.addView(buildDurationCell(sec, presetChips)) }

        // «+» — кастомное значение
        val plusCell = FrameLayout(this)
        val glass = GlassDrawable(den(), colAccent, dpf(24f), dpf(24f), dpf(4f))
        val plusBtn = TextView(this).apply {
            text = "+"
            textSize = 22f
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(colAccent)
            background = glass
            setOnClickListener { showCustomDialog() }
            bounceOnTouch(0.9f, glass)
        }
        plusCell.addView(plusBtn, FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER))
        row.addView(plusCell, LinearLayout.LayoutParams(0, wrapC, 1f))
        return row
    }

    private fun rebuildHistoryRow(history: List<Int>, current: Int) {
        historyRowRef.removeAllViews()
        historyChips.clear()
        history.forEach { sec -> historyRowRef.addView(buildDurationCell(sec, historyChips)) }
        refreshDurationButtons(current, true)
    }

    private fun buildDurationCell(sec: Int, into: MutableList<Chip>): FrameLayout {
        val cell = FrameLayout(this)
        // круг -> при выборе морфится в скруглённый квадрат со свечением
        val glass = GlassDrawable(den(), colAccent, dpf(24f), dpf(16f), dpf(4f))
        val btn = TextView(this).apply {
            text = sec.toString()
            textSize = if (sec >= 100) 13f else 18f
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(colText)
            background = glass
            setOnClickListener {
                HaloPrefs.setDurationSec(this@TilePrefsActivity, sec)
                refreshDurationButtons(sec, true)
            }
            bounceOnTouch(0.88f, glass)
        }
        into.add(Chip(sec, btn, glass))
        cell.addView(btn, FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER))
        cell.layoutParams = LinearLayout.LayoutParams(0, wrapC, 1f)
        return cell
    }

    private fun refreshDurationButtons(selected: Int, animate: Boolean) {
        (presetChips + historyChips).forEach { chip ->
            val on = chip.sec == selected
            chip.tv.setTextColor(if (on) colOnAccent else colText)
            if (chip.on != on || !animate) {
                chip.on = on
                chip.glass.selectTo(on, animate)
            }
        }
    }

    // ── Диалог кастомной длительности ───────────────────────────────────────

    private fun showCustomDialog() {
        val et = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "Секунды (1–3600)"
            setTextColor(colText)
            setHintTextColor(colDim)
            filters = arrayOf(InputFilter.LengthFilter(4))
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        AlertDialog.Builder(this)
            .setTitle("Кастомная длительность")
            .setView(FrameLayout(this).apply { setPadding(dp(20), dp(8), dp(20), 0); addView(et) })
            .setPositiveButton("Применить") { _, _ ->
                val v = et.text.toString().toIntOrNull()
                if (v != null && v in 1..3600) {
                    HaloPrefs.addCustomHistory(this, v)
                    HaloPrefs.setDurationSec(this, v)
                    rebuildHistoryRow(HaloPrefs.customHistory(this), v)
                } else {
                    Toast.makeText(this, "Введите число от 1 до 3600", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    // ── Кнопки действий ─────────────────────────────────────────────────────

    /** Главная кнопка: акцентная заливка с объёмом и свечением. */
    private fun buildPrimaryButton(label: String, onClick: () -> Unit): TextView {
        val glass = GlassDrawable(den(), colAccent, dpf(22f), dpf(22f), dpf(4f), selected = true)
        return TextView(this).apply {
            text = label
            textSize = 16f
            setTextColor(colOnAccent)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = glass
            setOnClickListener { onClick() }
            bounceOnTouch(0.95f, glass)
        }
    }

    /** Вторичная кнопка: прозрачное стекло. */
    private fun buildGlassButton(label: String, color: Int, onClick: () -> Unit): TextView {
        val glass = GlassDrawable(den(), colAccent, dpf(22f), dpf(22f), dpf(4f))
        return TextView(this).apply {
            text = label
            textSize = 15f
            setTextColor(color)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = glass
            setOnClickListener { onClick() }
            bounceOnTouch(0.95f, glass)
        }
    }

    private fun buildGithubButton(): TextView =
        buildGlassButton("Исходный код на GitHub  ↗", colAccent) {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(HaloPrefs.GITHUB_URL))) }
            catch (_: ActivityNotFoundException) {}
            dismiss()
        }

    // ── Музыкальные приложения ───────────────────────────────────────────────

    private var syncInProgress = false
    private var syncAnimator: ObjectAnimator? = null

    private fun startSyncRotation(syncBtn: ImageView) {
        android.util.Log.d("HALO_SYNC", "startSyncRotation called")
        syncAnimator?.cancel()
        syncAnimator = ObjectAnimator.ofFloat(syncBtn, "rotation", 0f, 360f).apply {
            duration = 700
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            start()
        }
        android.util.Log.d("HALO_SYNC", "animator started: ${syncAnimator?.isRunning}")
    }

    private fun stopSyncRotation(syncBtn: ImageView) {
        syncAnimator?.cancel()
        syncAnimator = null
        syncBtn.rotation = 0f
    }

    private fun loadMusicApps(container: LinearLayout, syncBtn: ImageView) {
        if (syncInProgress) return
        syncInProgress = true

        startSyncRotation(syncBtn)

        scope.launch {
            val apps = withContext(Dispatchers.IO) {
                MusicLightManager.resolveApps(this@TilePrefsActivity)
            }
            val states = if (apps.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    MusicLightManager.readStates(this@TilePrefsActivity)
                }
            } else emptyMap()

            // минимум 800мс чтобы анимация была видна
            kotlinx.coroutines.delay(800)

            syncInProgress = false
            stopSyncRotation(syncBtn)

            container.removeAllViews()
            if (apps.isEmpty()) {
                container.addView(text("Подходящие приложения не найдены", 13f, colDim))
                return@launch
            }
            apps.forEachIndexed { i, app ->
                val row = buildMusicRow(app, states[app.pkg] ?: false)
                container.addView(row)
                row.enterFromBelow(i * 70L, dpf(14f))
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun buildMusicRow(app: MusicLightManager.MusicApp, initialEnabled: Boolean): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GlassDrawable(den(), colAccent, dpf(18f), dpf(18f), 0f)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            layoutParams = LinearLayout.LayoutParams(matchP, wrapC).apply { bottomMargin = dp(8) }
        }

        row.addView(ImageView(this).apply {
            setImageDrawable(app.icon)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(14) })

        row.addView(TextView(this).apply {
            text = app.label
            textSize = 15f
            setTextColor(colText)
        }, LinearLayout.LayoutParams(0, wrapC, 1f))

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
        row.addView(toggle, LinearLayout.LayoutParams(wrapC, wrapC))
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
            layoutParams = LinearLayout.LayoutParams(matchP, wrapC)
        }
}
