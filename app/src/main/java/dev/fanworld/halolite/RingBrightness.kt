package dev.fanworld.halolite

import android.content.Context
import android.provider.Settings
import kotlinx.coroutines.delay

/**
 * Системная яркость кольца: Settings.Global lamp_effect_brightness (80..255).
 * Это тот же ключ, что у ползунка «Яркость» в «Эффекты подсветки».
 * Сервис HyperLightsService подставляет значение в альфа-байт цвета, поэтому
 * и наше переливание, и системные ритмичные эффекты идут с этой яркостью.
 *
 * Побочный эффект системы: при каждой смене ключа кольцо на ~5 с вспыхивает белым
 * (это и есть живой предпросмотр яркости, как в настройках) и перебивает текущую
 * анимацию. Во время перетаскивания пишем значение в реальном времени через [set],
 * а когда палец отпущен — [recover] гасит белый и перезапускает ритмичные эффекты.
 */
object RingBrightness {
    private const val KEY = "lamp_effect_brightness"
    const val MIN = 80
    const val MAX = 255

    fun get(ctx: Context): Int =
        Settings.Global.getInt(ctx.contentResolver, KEY, MAX).coerceIn(MIN, MAX)

    fun percent(v: Int): Int = ((v - MIN) * 100f / (MAX - MIN)).toInt().coerceIn(0, 100)

    /** Пишет значение. Сначала напрямую (WRITE_SECURE_SETTINGS), иначе через shell Shizuku. Вне main-потока. */
    fun set(ctx: Context, value: Int): Boolean {
        val v = value.coerceIn(MIN, MAX)
        try {
            if (Settings.Global.putInt(ctx.contentResolver, KEY, v)) return true
        } catch (_: SecurityException) {
        }
        return StripLight.shell(arrayOf("settings", "put", "global", KEY, v.toString()))
    }

    /** Гасит белый предпросмотр и возвращает ритмичные эффекты. Вызывать вне main-потока. */
    suspend fun recover(ctx: Context) {
        // onChange в сервисе зажигает белый и ставит таймер на 5 с — гасим сразу
        delay(300)
        LightsBinder.setColor(0, ctx.packageName)
        restartRhythm(ctx)

        // системный таймер предпросмотра через ~5 с гасит кольцо ещё раз — перезапускаем после него
        delay(5000)
        restartRhythm(ctx)
    }

    private fun restartRhythm(ctx: Context) {
        val enabled = MusicLightManager.readStates(ctx).filterValues { it }.keys
        if (enabled.isEmpty()) return
        MusicLightManager.resolveApps(ctx)
            .map { it.pkg }
            .filter { it in enabled }
            .forEach { pkg ->
                MusicLightManager.setEnabled(ctx, pkg, false)
                MusicLightManager.setEnabled(ctx, pkg, true)
            }
    }
}
