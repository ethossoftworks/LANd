package com.ethossoftworks.land

import com.ethossoftworks.land.coordinator.AppCoordinator
import com.ethossoftworks.land.interactor.discovery.DiscoveryInteractor
import com.ethossoftworks.land.interactor.filetransfer.FileTransferInteractor
import com.ethossoftworks.land.interactor.openSourceLicenses.OpenSourceLicensesInteractor
import com.ethossoftworks.land.interactor.preferences.AppPreferencesInteractor
import com.ethossoftworks.land.service.discovery.INSDService
import com.ethossoftworks.land.service.filetransfer.FileTransferService
import com.ethossoftworks.land.service.filetransfer.IFileTransferService
import com.ethossoftworks.land.service.openSourceLicenses.IOpenSourceLicensesService
import com.ethossoftworks.land.service.openSourceLicenses.OpenSourceLicensesService
import com.ethossoftworks.land.service.preferences.IPreferencesService
import com.ethossoftworks.land.service.preferences.PreferencesService
import com.ethossoftworks.land.ui.home.*
import com.ethossoftworks.land.ui.openSourceLicenses.OpenSourceLicensesViewInteractor
import com.outsidesource.oskitcompose.systemui.KMPAppLifecycleObserver
import com.outsidesource.oskitkmp.file.IKMPFileHandler
import com.outsidesource.oskitkmp.file.KMPFileHandler
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

    single { DiscoveryInteractor(get(), KMPAppLifecycleObserver) }
    single { AppPreferencesInteractor(get(), get()) }
    single { OpenSourceLicensesInteractor(get()) }
    single {
        val discoveryInteractor: DiscoveryInteractor = get()

        FileTransferInteractor(get(), get(), get(), get()) {
            discoveryInteractor.stopServiceBroadcasting()
        }
    }
    single { KMPFileHandler() } bind IKMPFileHandler::class

    single {
        val appPreferencesInteractor: AppPreferencesInteractor = get()
        val nsdService: INSDService = get()

        FileTransferService(
            getServerDeviceName = { appPreferencesInteractor.state.displayName },
            getLocalIpAddress = nsdService::getLocalIpAddress,
            getUseEncryption = { appPreferencesInteractor.state.useEncryption }
        )
    } bind IFileTransferService::class
    single { PreferencesService(get()) } bind IPreferencesService::class
    single { OpenSourceLicensesService() } bind IOpenSourceLicensesService::class

    factory { HomeScreenViewInteractor(get(), get(), get(), get(), get()) }
    factory { SettingsBottomSheetViewInteractor(get(), get(), get(), get()) }
    factory { params -> DiscoveredDeviceViewInteractor(params[0], get(), get(), get()) }
    factory { params -> AddDeviceModalViewInteractor(get(), get(), params[0]) }
    factory { TransferMessageViewInteractor(get()) }
    factory { OpenSourceLicensesViewInteractor(get(), get()) }
}