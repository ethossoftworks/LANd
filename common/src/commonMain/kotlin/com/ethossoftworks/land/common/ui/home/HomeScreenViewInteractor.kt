package com.ethossoftworks.land.common.ui.home

import com.ethossoftworks.land.common.coordinator.AppCoordinator
import com.ethossoftworks.land.common.interactor.discovery.DiscoveryInteractor
import com.ethossoftworks.land.common.model.device.Device
import com.outsidesource.oskitkmp.interactor.Interactor
import kotlinx.coroutines.launch

data class HomeViewState(
    val discoveredDevices: Map<String, Device> = emptyMap()
)

class HomeScreenViewInteractor(
    private val discoveryInteractor: DiscoveryInteractor,
    private val appCoordinator: AppCoordinator,
): Interactor<HomeViewState>(
    initialState = HomeViewState(),
    dependencies = listOf(discoveryInteractor)
) {
    override fun computed(state: HomeViewState): HomeViewState {
        return state.copy(
            discoveredDevices = discoveryInteractor.state.discoveredDevices,
        )
    }

    fun viewMounted() {
        interactorScope.launch {
            discoveryInteractor.startDeviceDiscovery()
            discoveryInteractor.startServiceBroadcasting()
        }
    }
}