package com.ethossoftworks.land

import android.app.Application

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initDI(DIPlatformContext.Android(this@MainApplication))
    }
}