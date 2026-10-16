package dev.fanworld.halolite

import android.content.Context
import android.provider.Settings
import rikka.shizuku.Shizuku

/** Главный выключатель подсветки камеры: Settings.Global settings_strip_light_enable (0 = выкл, 2 = вкл). */
object StripLight {
    private const val KEY = "settings_strip_light_enable"
    private const val ON = 2
    private const val OFF = 0

    fun isOn(ctx: Context): Boolean =
        Settings.Global.getInt(ctx.contentResolver, KEY, OFF) != OFF

    /** Сначала пишем напрямую (если выдан WRITE_SECURE_SETTINGS), иначе через shell Shizuku. */
    fun set(ctx: Context, on: Boolean): Boolean {
        val v = if (on) ON else OFF
        try {
            if (Settings.Global.putInt(ctx.contentResolver, KEY, v)) return true
        } catch (_: SecurityException) {
        }
        return shell(arrayOf("settings", "put", "global", KEY, v.toString()))
    }

    /** Выдаёт приложению WRITE_SECURE_SETTINGS через Shizuku (один раз). Вызывать не из main-потока. */
    fun grantSelf(ctx: Context): Boolean =
        shell(arrayOf("pm", "grant", ctx.packageName, "android.permission.WRITE_SECURE_SETTINGS"))

    private fun shell(cmd: Array<String>): Boolean {
        if (!LightsBinder.hasPermission()) return false
        return try {
            val m = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            m.isAccessible = true
            val p = m.invoke(null, cmd, null, null) as Process
            p.waitFor() == 0
        } catch (_: Throwable) {
            false
        }
    }
}
