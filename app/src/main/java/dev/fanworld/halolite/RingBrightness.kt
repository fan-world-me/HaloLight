package dev.fanworld.halolite

import android.content.Context
import android.provider.Settings

/**
 * Системная яркость кольца: Settings.Global lamp_effect_brightness (80..255).
 * Это тот же ключ, что у ползунка «Яркость» в «Эффекты подсветки».
 * Сервис HyperLightsService подставляет значение в альфа-байт цвета, поэтому
 * и наше переливание, и системные ритмичные эффекты идут с этой яркостью.
 *
 * Побочный эффект системы: при смене ключа кольцо на ~5 с вспыхивает белым и
 * перебивает текущую анимацию. [applyAndRecover] гасит белый сразу и
 * перезапускает ритмичные эффекты.
 */
object RingBrightness {
    private const val KEY = "lamp_effect_brightness"
    const val MIN = 80
    const val MAX = 255

    fun get(ctx: Context): Int =
        Settings.Global.getInt(ctx.contentResolver, KEY, MAX).coerceIn(MIN, MAX)

    fun percent(v: Int): Int = ((v - MIN) * 100f / (MAX - MIN)).toInt().coerceIn(0, 100)

    /** Пишет значение. Сначала напрямую (WRITE_SECURE_SETTINGS), иначе через shell Shizuku. */
    private fun write(ctx: Context, v: Int): Boolean {
        try {
            if (Settings.Global.putInt(ctx.contentResolver, KEY, v)) return true
        } catch (_: SecurityException) {
        }
        return StripLight.shell(arrayOf("settings", "put", "global", KEY, v.toString()))
    }

    /** Вызывать вне главного потока. */
    fun applyAndRecover(ctx: Context, value: Int) {
        val v = value.coerceIn(MIN, MAX)
        val changed = v != get(ctx)
        if (!write(ctx, v) || !changed) return

        // onChange в сервисе зажигает белый и ставит таймер на 5 с — гасим сразу
        Thread.sleep(300)
        LightsBinder.setColor(0, ctx.packageName)

        // ритмичные эффекты сбиваются — перезапускаем включённые приложения
        val enabled = MusicLightManager.readStates(ctx).filterValues { it }.keys
        MusicLightManager.resolveApps(ctx)
            .map { it.pkg }
            .filter { it in enabled }
            .forEach { pkg ->
                MusicLightManager.setEnabled(ctx, pkg, false)
                MusicLightManager.setEnabled(ctx, pkg, true)
            }
    }
}
