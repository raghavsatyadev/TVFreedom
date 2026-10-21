package com.gathin.tvgo

import android.app.Activity
import android.app.Application
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class TvGoApp : Application() {
    lateinit var platform: AndroidPlatform
        private set

    private val started = MutableStateFlow(0)

    /** True while tvgo is on screen. Gemini Nano only answers then. */
    val open: StateFlow<Int> get() = started

    override fun onCreate() {
        super.onCreate()
        platform = AndroidPlatform(this, open)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(a: Activity) { started.value++ }
            override fun onActivityStopped(a: Activity) { started.value-- }
            override fun onActivityCreated(a: Activity, b: Bundle?) {}
            override fun onActivityResumed(a: Activity) {}
            override fun onActivityPaused(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })
    }
}
