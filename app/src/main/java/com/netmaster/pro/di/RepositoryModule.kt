package com.netmaster.pro.di

import com.netmaster.pro.data.repository.*
import com.netmaster.pro.domain.repository.*
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindRouterRepository(impl: RouterRepositoryImpl): RouterRepository

    @Binds
    @Singleton
    abstract fun bindDeviceRepository(impl: DeviceRepositoryImpl): DeviceRepository

    @Binds
    @Singleton
    abstract fun bindTrafficRepository(impl: TrafficRepositoryImpl): TrafficRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
}
