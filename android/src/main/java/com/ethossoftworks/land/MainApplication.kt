package com.ethossoftworks.land

import android.app.Application
import com.ethossoftworks.land.common.initKoin

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin()
    }
}