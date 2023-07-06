package com.ethossoftworks.land.common

import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

fun initKoin(
    appDeclaration: KoinAppDeclaration = {},
    extraModules: Array<Module> = emptyArray()
): KoinApplication {
    stopKoin()

    return startKoin {
        appDeclaration()
        modules(commonModule(), platformModule(), *extraModules)
    }
}

fun commonModule() = module {

}