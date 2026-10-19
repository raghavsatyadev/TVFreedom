package com.gathin.tvgo

import android.app.Application
import com.gathin.tvgo.store.Storage

class TvGoApp : Application() {
    lateinit var storage: Storage
        private set

    override fun onCreate() {
        super.onCreate()
        storage = Storage(this)
    }
}
