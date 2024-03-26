package com.ethossoftworks.land

import android.content.Context
import com.ethossoftworks.land.service.discovery.AndroidNSDService
import com.ethossoftworks.land.service.discovery.INSDService
import com.ethossoftworks.land.service.preferences.AndroidPreferencesService
import com.ethossoftworks.land.service.preferences.IPreferencesService
import org.koin.dsl.bind
import org.koin.dsl.module

actual sealed class DIPlatformContext {
    data class Android(val applicationContext: Context): DIPlatformContext()
}

actual fun platformModule(platformContext: DIPlatformContext) = module {
    val context = (platformContext as DIPlatformContext.Android).applicationContext

    single { AndroidNSDService(context) } bind INSDService::class
    single { AndroidPreferencesService(context) } bind IPreferencesService::class
}