package com.terekdrive

import android.app.Application
import org.maplibre.android.MapLibre

class TerekDriveApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
    }
}
