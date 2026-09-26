package dev.flowpilot.app

import android.app.Application
import dev.flowpilot.app.data.AppGraph
import dev.flowpilot.app.data.CrashLog

class FlowPilotApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        graph = AppGraph(this)
    }
}
