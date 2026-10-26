package pt.tiago.nightcrush

import android.app.Application
import pt.tiago.nightcrush.crash.CrashLog

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }
}
