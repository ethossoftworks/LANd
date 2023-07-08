package com.ethossoftworks.land.common.ui

import com.outsidesource.oskitkmp.router.IRoute

sealed class Route: IRoute {
    object Home : Route()
}