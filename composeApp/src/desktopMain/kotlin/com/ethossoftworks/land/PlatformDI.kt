package com.ethossoftworks.land

import com.ethossoftworks.land.service.discovery.DesktopNSDService
import com.ethossoftworks.land.service.discovery.INSDService
import com.ethossoftworks.land.service.preferences.PreferencesService
import com.ethossoftworks.land.service.preferences.IPreferencesService
import com.ethossoftworks.land.ui.DesktopAppViewInteractor
import com.outsidesource.oskitkmp.storage.DesktopKMPStorage
import com.outsidesource.oskitkmp.storage.IKMPStorage
import org.koin.dsl.bind
import org.koin.dsl.module

actual class DIPlatformContext

actual fun platformModule(platformContext: DIPlatformContext) = module {
    factory { DesktopAppViewInteractor(get()) }

    single { DesktopNSDService() } bind INSDService::class
    single { DesktopKMPStorage("com.ethossoftworks.LANd") } bind IKMPStorage::class
}