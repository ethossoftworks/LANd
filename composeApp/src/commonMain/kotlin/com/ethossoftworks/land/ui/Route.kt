package com.ethossoftworks.land.ui

import com.outsidesource.oskitkmp.router.IRoute

sealed class Route: IRoute {
    object Home : com.ethossoftworks.land.ui.Route()
}