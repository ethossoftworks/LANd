package com.ethossoftworks.land.common

import com.ethossoftworks.land.common.service.discovery.INSDService
import com.ethossoftworks.land.common.service.discovery.DesktopNSDService
import com.ethossoftworks.land.common.service.file.DesktopFileHandler
import com.ethossoftworks.land.common.service.file.IFileHandler
import org.koin.dsl.bind
import org.koin.dsl.module

actual sealed class DIPlatformContext {
    object Desktop: DIPlatformContext()
}

actual fun platformModule(platformContext: DIPlatformContext) = module {
    single { DesktopNSDService() } bind INSDService::class
    single { DesktopFileHandler() } bind IFileHandler::class
}