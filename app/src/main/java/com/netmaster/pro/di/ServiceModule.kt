package com.netmaster.pro.di

import com.netmaster.pro.data.model.ConnectedDevice
import com.netmaster.pro.domain.repository.DeviceRepository
import com.netmaster.pro.service.DeviceRepositoryProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {

    /**
     * Bridges [DeviceRepository] (which returns Result<List>) into the
     * [DeviceRepositoryProvider] interface (which returns List directly).
     * This keeps the service layer independent of the repository Result wrapper.
     */
    @Provides
    @Singleton
    fun provideDeviceRepositoryProvider(
        deviceRepository: DeviceRepository
    ): DeviceRepositoryProvider = object : DeviceRepositoryProvider {
        override suspend fun getConnectedDevices(): List<ConnectedDevice> =
            deviceRepository.getConnectedDevices().getOrDefault(emptyList())
    }
}
