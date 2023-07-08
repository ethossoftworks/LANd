package com.ethossoftworks.land.common

import org.koin.core.module.Module

expect sealed class DIPlatformContext

expect fun platformModule(platformContext: DIPlatformContext): Module
