package com.ethossoftworks.land.common

import com.ethossoftworks.land.common.service.discovery.DesktopNSDService
import com.ethossoftworks.land.common.service.discovery.INSDService
import com.ethossoftworks.land.common.service.preferences.DesktopPreferencesService
import com.ethossoftworks.land.common.service.preferences.IPreferencesService
import com.ethossoftworks.land.common.ui.DesktopAppViewInteractor
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