package com.ethossoftworks.land.ui

import com.outsidesource.oskitkmp.router.IRoute

sealed class Route: IRoute {
    data object Home : Route()
    data object OpenSourceLicenses : Route()
}