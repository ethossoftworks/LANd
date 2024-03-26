package com.ethossoftworks.land

import com.ethossoftworks.land.service.discovery.DesktopNSDService
import com.ethossoftworks.land.service.discovery.INSDService
import com.ethossoftworks.land.service.preferences.DesktopPreferencesService
import com.ethossoftworks.land.service.preferences.IPreferencesService
import com.ethossoftworks.land.ui.DesktopAppViewInteractor
import org.koin.dsl.bind
import org.koin.dsl.module

actual sealed class DIPlatformContext {
    data object Desktop: DIPlatformContext()
}

actual fun platformModule(platformContext: DIPlatformContext) = module {
    factory { DesktopAppViewInteractor(get()) }

    single { DesktopNSDService() } bind INSDService::class
    single { DesktopPreferencesService() } bind IPreferencesService::class
}