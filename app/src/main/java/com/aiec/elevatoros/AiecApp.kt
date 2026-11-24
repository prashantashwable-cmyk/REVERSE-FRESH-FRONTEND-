package com.aiec.elevatoros

import android.app.Application
import com.aiec.elevatoros.data.CrashLog

class AiecApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }
}
