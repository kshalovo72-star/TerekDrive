package com.terekdrive

import android.app.Application
import android.content.Context
import ru.dgis.sdk.DGis

class TerekDriveApplication : Application() {
    var sdkContext: Context? = null
        private set

    override fun onCreate() {
        super.onCreate()
        val hasKey = runCatching {
            assets.open("dgissdk.key").use { true }
        }.getOrDefault(false)
        if (hasKey) sdkContext = DGis.initialize(this)
    }
}
