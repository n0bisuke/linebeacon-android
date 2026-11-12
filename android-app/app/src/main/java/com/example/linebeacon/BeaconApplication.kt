package com.example.linebeacon

import android.app.Application

class BeaconApplication : Application() {

    lateinit var controller: BeaconController
        private set

    override fun onCreate() {
        super.onCreate()
        controller = BeaconController(this)
    }
}
