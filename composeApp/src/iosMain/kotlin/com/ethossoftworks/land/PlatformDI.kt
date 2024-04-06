package com.ethossoftworks.land

import com.ethossoftworks.land.service.discovery.INSDService
import com.outsidesource.oskitkmp.storage.IKMPStorage
import com.outsidesource.oskitkmp.storage.IOSKMPStorage
import org.koin.dsl.bind
import org.koin.dsl.module

private val koin = initDI(platformContext = DIPlatformContext()).koin

actual class DIPlatformContext

actual fun platformModule(platformContext: DIPlatformContext) = module {
    single { IOSKMPStorage() } bind IKMPStorage::class
}

fun loadKoinSwiftModules(
    nsdService: INSDService,
) {
    koin.loadModules(
        listOf(
            module {
               single { nsdService } bind INSDService::class
            },
        ),
    )
}
