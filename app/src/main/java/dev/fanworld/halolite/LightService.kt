package dev.fanworld.halolite

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import android.widget.Toast

/** Играет радугу на кольце: красный → спектр → красный → спектр → красный. */
class LightService : Service() {

    private var thread: HandlerThread? = null
    private var handler: Handler? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()

        if (!LightsBinder.hasPermission()) {
            Toast.makeText(this, "Shizuku не запущен или нет доступа", Toast.LENGTH_SHORT).show()
            finishSelf()
            return START_NOT_STICKY
        }

        cancelAnimation()
        val t = HandlerThread("halolite-anim").also { it.start() }
        val h = Handler(t.looper)
        thread = t
        handler = h

        val durationMs = HaloPrefs.durationMs(this)
        val start = SystemClock.uptimeMillis()
        val pkg = packageName

        h.post(object : Runnable {
            override fun run() {
                val p = (SystemClock.uptimeMillis() - start).toFloat() / durationMs
                if (p >= 1f) {
                    LightsBinder.setColor(0, pkg)
                    finishSelf()
                    return
                }
                val hue = (p * 720f) % 360f
                LightsBinder.setColor(Color.HSVToColor(floatArrayOf(hue, 1f, 1f)), pkg)
                h.postDelayed(this, 25)
            }
        })
        return START_NOT_STICKY
    }

    private fun cancelAnimation() {
        handler?.removeCallbacksAndMessages(null)
        thread?.quitSafely()
        thread = null
        handler = null
    }

    private fun finishSelf() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        cancelAnimation()
        super.onDestroy()
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("halolite", "HaloLite", NotificationManager.IMPORTANCE_LOW)
        )
        val n = Notification.Builder(this, "halolite")
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle("HaloLite")
            .setContentText("Анимация кольца")
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }
}
