package com.netcatty.mobile

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class NetcattyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Global initialization
    }
}
