package com.patrick.lrcreader.exo

import android.app.Application
import com.patrick.lrcreader.core.EditionConfig

class StageMusicPlayerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        EditionConfig.initialize(this)
    }
}
