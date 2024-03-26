package com.ethossoftworks.land

import android.app.Application
import com.ethossoftworks.land.common.DIPlatformContext
import com.ethossoftworks.land.common.initDI

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initDI(DIPlatformContext.Android(this@MainApplication))
    }
}