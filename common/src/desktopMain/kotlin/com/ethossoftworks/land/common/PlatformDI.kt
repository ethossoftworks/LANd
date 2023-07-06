package com.ethossoftworks.land.common

import com.ethossoftworks.land.common.service.discovery.INSDService
import com.ethossoftworks.land.common.service.discovery.JVMNSDService
import org.koin.dsl.bind
import org.koin.dsl.module

actual fun platformModule() = module {
    single { JVMNSDService() } bind INSDService::class
}