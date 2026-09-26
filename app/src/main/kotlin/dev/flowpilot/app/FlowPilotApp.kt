package dev.flowpilot.app

import android.app.Application
import dev.flowpilot.app.data.AppGraph

class FlowPilotApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }
}
