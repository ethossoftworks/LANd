package com.ethossoftworks.land.common

import com.ethossoftworks.land.common.interactor.discovery.DiscoveryInteractor
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration
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
    single { DiscoveryInteractor(get()) }
}