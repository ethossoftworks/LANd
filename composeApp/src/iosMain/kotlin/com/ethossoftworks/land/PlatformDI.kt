package com.ethossoftworks.land

import com.ethossoftworks.land.service.discovery.INSDService
import com.ethossoftworks.land.service.discovery.IOSNSDService
import com.outsidesource.oskitkmp.storage.IKMPStorage
import com.outsidesource.oskitkmp.storage.IOSKMPStorage
import org.koin.dsl.bind
import org.koin.dsl.module

private val koin = initDI(platformContext = DIPlatformContext()).koin

actual class DIPlatformContext

actual fun platformModule(platformContext: DIPlatformContext) = module {
    single { IOSKMPStorage() } bind IKMPStorage::class
    single { IOSNSDService() } bind INSDService::class
}

fun loadKoinSwiftModules() {
    koin.loadModules(
        listOf(
            module {
            },
        ),
    )
}
