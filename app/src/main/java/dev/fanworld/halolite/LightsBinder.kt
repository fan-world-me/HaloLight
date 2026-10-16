package dev.fanworld.halolite

import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Parcel
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

/**
 * Ручной клиент для miui.lights.ILightsManager (без AIDL-класса с тем же именем —
 * иначе будет коллизия загрузчика классов).
 * Порядок методов в интерфейсе: 1=setColorfulLight, 2=setColorCommon, 3=setColorLed, 4=setCustomLight.
 */
object LightsBinder {
    private const val DESCRIPTOR = "miui.lights.ILightsManager"
    private const val TX_SET_COLOR_COMMON = 2
    private const val STYLE_MUSIC_RHYTHM = 3 // зона кольца вокруг камеры

    @Volatile
    private var binder: IBinder? = null

    fun shizukuRunning(): Boolean = try { Shizuku.pingBinder() } catch (_: Throwable) { false }

    fun hasPermission(): Boolean = try {
        shizukuRunning() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    private fun obtain(): IBinder? {
        binder?.let { if (it.isBinderAlive) return it }
        val raw = SystemServiceHelper.getSystemService(DESCRIPTOR) ?: return null
        return ShizukuBinderWrapper(raw).also { binder = it }
    }

    /** color = ARGB; 0 — погасить кольцо */
    fun setColor(color: Int, pkg: String): Boolean {
        val b = obtain() ?: return false
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(DESCRIPTOR)
            data.writeInt(color)
            data.writeString(pkg)
            data.writeInt(STYLE_MUSIC_RHYTHM)
            data.writeInt(0) // userId
            b.transact(TX_SET_COLOR_COMMON, data, reply, 0)
            reply.readException()
            true
        } catch (_: Throwable) {
            binder = null
            false
        } finally {
            data.recycle()
            reply.recycle()
        }
    }
}
