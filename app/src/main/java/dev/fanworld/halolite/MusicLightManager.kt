package dev.fanworld.halolite

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.util.Log

private const val TAG = "MusicLightManager"

/**
 * Управление ритмичной подсветкой по приложениям через AudioManager.
 *
 * Чтение:  audioManager.getParameters("Get_MusicLight_Package_State=")
 *          → "pkg1=true;pkg2=false;..."
 * Запись:  audioManager.setParameters("Set_MusicLight_Package_State=pkg:true|false")
 *          setParameters бросает SecurityException, но запись применяется; исключение игнорируем.
 *
 * Вызывать read/write строго вне главного потока.
 */
object MusicLightManager {

    private const val GET_CMD = "Get_MusicLight_Package_State="
    private const val SET_CMD = "Set_MusicLight_Package_State="

    data class MusicApp(
        val pkg: String,
        val label: String,
        val icon: Drawable
    )

    /**
     * Возвращает список установленных музыкальных приложений.
     * YouTube Music ReVanced имеет приоритет: если он установлен, обычный YTM не добавляется.
     */
    fun resolveApps(ctx: Context): List<MusicApp> {
        val pm = ctx.packageManager
        val result = mutableListOf<MusicApp>()

        fun tryAdd(pkg: String): Boolean {
            return try {
                val ai = pm.getApplicationInfo(pkg, 0)
                result.add(
                    MusicApp(
                        pkg = pkg,
                        label = ai.loadLabel(pm).toString(),
                        icon = pm.getApplicationIcon(pkg)
                    )
                )
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }

        // Spotify
        tryAdd("com.spotify.music")

        // YouTube Music: ReVanced приоритетнее обычного
        val addedRevanced = tryAdd("app.revanced.android.apps.youtube.music")
        if (!addedRevanced) tryAdd("com.google.android.apps.youtube.music")

        // SoundCloud
        tryAdd("com.soundcloud.android")

        return result
    }

    /**
     * Читает текущее состояние всех пакетов из AudioManager.
     * Возвращает map: pkg → enabled. Вызывать вне главного потока.
     */
    fun readStates(ctx: Context): Map<String, Boolean> {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val raw = try {
            am.getParameters(GET_CMD)
        } catch (e: Exception) {
            Log.d(TAG, "getParameters failed: $e")
            null
        }
        if (raw.isNullOrBlank()) return emptyMap()

        val map = mutableMapOf<String, Boolean>()
        raw.split(';').forEach { entry ->
            val parts = entry.trim().split('=')
            if (parts.size == 2) {
                val pkg = parts[0].trim()
                val value = parts[1].trim()
                if (value == "true" || value == "false") {
                    map[pkg] = value == "true"
                }
            }
        }
        return map
    }

    /**
     * Включает/выключает пакет. Возвращает реальное состояние после записи.
     * Вызывать вне главного потока.
     */
    fun setEnabled(ctx: Context, pkg: String, enabled: Boolean): Boolean {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        try {
            am.setParameters("$SET_CMD$pkg:$enabled")
        } catch (e: SecurityException) {
            // SecurityException ожидается на этом устройстве; запись всё равно применяется
            Log.d(TAG, "setParameters SecurityException (expected): $e")
        } catch (e: Exception) {
            Log.d(TAG, "setParameters failed: $e")
        }
        // перечитываем реальное состояние
        val states = readStates(ctx)
        return states[pkg] ?: false
    }
}
