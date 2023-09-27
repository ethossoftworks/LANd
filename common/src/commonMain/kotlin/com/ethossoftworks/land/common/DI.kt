package com.ethossoftworks.land.common

import com.ethossoftworks.land.common.coordinator.AppCoordinator
import com.ethossoftworks.land.common.interactor.discovery.DiscoveryInteractor
import com.ethossoftworks.land.common.interactor.filetransfer.FileTransferInteractor
import com.ethossoftworks.land.common.interactor.preferences.AppPreferencesInteractor
import com.ethossoftworks.land.common.service.discovery.INSDService
import com.ethossoftworks.land.common.service.discovery.NSDService
import com.ethossoftworks.land.common.service.filetransfer.FileTransferService
import com.ethossoftworks.land.common.service.filetransfer.IFileTransferService
import com.ethossoftworks.land.common.ui.home.*
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.bind
import org.koin.dsl.module

fun initDI(
    platformContext: DIPlatformContext,
    appDeclaration: KoinAppDeclaration = {},
    extraModules: Array<Module> = emptyArray()
): KoinApplication {
    stopKoin()

    return startKoin {
        appDeclaration()
        modules(commonModule(), platformModule(platformContext), *extraModules)
    }
}

fun commonModule() = module {
    single { AppCoordinator() }

    single { DiscoveryInteractor(get()) }
    single { AppPreferencesInteractor(get()) }
    single { FileTransferInteractor(get(), get(), get(), get()) }

    single {
        val appPreferencesInteractor: AppPreferencesInteractor = get()
        val nsdService: INSDService = get()
        FileTransferService(
            getServerDeviceName = { appPreferencesInteractor.state.displayName },
            getLocalIpAddress = nsdService::getLocalIpAddress,
        )
    } bind IFileTransferService::class

    factory { HomeScreenViewInteractor(get(), get(), get(), get(), get()) }
    factory { SettingsBottomSheetViewInteractor(get(), get(), get(), get()) }
    factory { AboutModalViewInteractor() }
    factory { params -> DiscoveredDeviceViewInteractor(params[0], get(), get(), get()) }
    factory { params -> AddDeviceModalViewInteractor(get(), get(), params[0]) }
    factory { TransferMessageViewInteractor(get()) }
}