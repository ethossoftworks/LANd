package com.ethossoftworks.land

import android.content.Context
import com.ethossoftworks.land.service.discovery.AndroidNSDService
import com.ethossoftworks.land.service.discovery.INSDService
import com.outsidesource.oskitkmp.storage.AndroidKMPStorage
import com.outsidesource.oskitkmp.storage.IKMPStorage
import org.koin.dsl.bind
import org.koin.dsl.module

actual class DIPlatformContext(val applicationContext: Context)

actual fun platformModule(platformContext: DIPlatformContext) = module {
    val context = platformContext.applicationContext

    single { AndroidNSDService(context) } bind INSDService::class
    single { AndroidKMPStorage(context) } bind IKMPStorage::class
}