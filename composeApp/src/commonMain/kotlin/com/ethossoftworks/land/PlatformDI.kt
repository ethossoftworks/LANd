package com.ethossoftworks.land

import org.koin.core.module.Module

expect class DIPlatformContext

expect fun platformModule(platformContext: DIPlatformContext): Module
