package dev.fanworld.halolite

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import rikka.shizuku.Shizuku

class MainActivity : Activity() {

    private lateinit var status: TextView

    private val permListener = Shizuku.OnRequestPermissionResultListener { _, _ ->
        runOnUiThread { refresh() }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Shizuku готов: запускаем анимацию в фоне и сразу закрываемся, без следа в «недавних»
        if (LightsBinder.hasPermission()) {
            if (checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") != PackageManager.PERMISSION_GRANTED) {
                Thread { StripLight.grantSelf(this) }.start()
            }
            startForegroundService(Intent(this, LightService::class.java))
            finishAndRemoveTask()
            return
        }

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#12131A"))
            setPadding(dp(24), dp(72), dp(24), dp(24))
        }

        root.addView(TextView(this).apply {
            text = "HaloLite"
            textSize = 30f
            setTextColor(Color.parseColor("#22D3EE"))
        })
        status = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.parseColor("#ECEDF5"))
            setPadding(0, dp(12), 0, dp(24))
        }
        root.addView(status)

        fun btn(label: String, onClick: () -> Unit) = Button(this).apply {
            text = label
            setOnClickListener { onClick() }
        }.also {
            root.addView(it, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) })
        }

        btn("Запросить доступ Shizuku") {
            if (LightsBinder.shizukuRunning() && !LightsBinder.hasPermission()) {
                Shizuku.requestPermission(100)
            }
            refresh()
        }
        btn("Запустить анимацию") {
            startForegroundService(Intent(this, LightService::class.java))
        }
        btn("Настройки (длительность)") {
            startActivity(Intent(this, TilePrefsActivity::class.java))
        }
        btn("GitHub") {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(HaloPrefs.GITHUB_URL)))
        }

        root.addView(TextView(this).apply {
            text = "Плитку добавь в шторке: карандаш → HaloLite.\nДолгое нажатие на плитку открывает настройки."
            textSize = 13f
            setTextColor(Color.parseColor("#9A9CB0"))
            setPadding(0, dp(24), 0, 0)
        })

        setContentView(root)
        try { Shizuku.addRequestPermissionResultListener(permListener) } catch (_: Throwable) {}
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onDestroy() {
        try { Shizuku.removeRequestPermissionResultListener(permListener) } catch (_: Throwable) {}
        super.onDestroy()
    }

    private fun refresh() {
        if (!::status.isInitialized) return
        status.text = when {
            !LightsBinder.shizukuRunning() -> "Shizuku не запущен"
            !LightsBinder.hasPermission() -> "Shizuku работает, нужен доступ"
            else -> "Shizuku: доступ есть ✓"
        }
    }
}
