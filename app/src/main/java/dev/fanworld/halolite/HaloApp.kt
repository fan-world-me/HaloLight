package dev.fanworld.halolite

import android.app.Application
import org.lsposed.hiddenapibypass.HiddenApiBypass

class HaloApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // нужен, чтобы Shizuku мог достать системный сервис через скрытые API
        HiddenApiBypass.addHiddenApiExemptions("L")
    }
}
