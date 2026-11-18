package com.netmaster.pro.di

import com.netmaster.pro.data.adapter.GreenpacketD2220G1Adapter
import com.netmaster.pro.data.adapter.HuaweiAdapter
import com.netmaster.pro.data.adapter.MikroTikAdapter
import com.netmaster.pro.data.adapter.OpenWrtUbusAdapter
import com.netmaster.pro.data.adapter.RouterAdapterRegistry
import com.netmaster.pro.data.adapter.TpLinkAdapter
import com.netmaster.pro.data.adapter.UniversalFallbackAdapter
import com.netmaster.pro.data.adapter.ZteAdapter
import com.netmaster.pro.data.model.ConnectedDevice
import com.netmaster.pro.data.model.LteSignalData
import com.netmaster.pro.data.model.RouterBrand
import com.netmaster.pro.data.model.RouterCapability
import com.netmaster.pro.data.model.SmsMessage
import com.netmaster.pro.data.model.TrafficStats
import com.netmaster.pro.domain.adapter.ApnProfile
import com.netmaster.pro.domain.adapter.DslStats
import com.netmaster.pro.domain.adapter.LteBand
import com.netmaster.pro.domain.adapter.MacFilterMode
import com.netmaster.pro.domain.adapter.NetworkMode
import com.netmaster.pro.domain.adapter.PortForwardRule
import com.netmaster.pro.domain.adapter.QosRule
import com.netmaster.pro.domain.adapter.RouterAdapter
import com.netmaster.pro.domain.adapter.RouterSecurityInfo
import com.netmaster.pro.domain.adapter.WifiSettings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AdapterModule {

    @Provides
    @Singleton
    fun provideGreenpacketAdapter(okHttpClient: OkHttpClient): GreenpacketD2220G1Adapter =
        GreenpacketD2220G1Adapter(okHttpClient)

    @Provides
    @Singleton
    fun provideHuaweiAdapter(okHttpClient: OkHttpClient): HuaweiAdapter =
        HuaweiAdapter(okHttpClient)

    @Provides
    @Singleton
    fun provideZteAdapter(okHttpClient: OkHttpClient): ZteAdapter =
        ZteAdapter(okHttpClient)

    @Provides
    @Singleton
    fun provideTpLinkAdapter(okHttpClient: OkHttpClient): TpLinkAdapter =
        TpLinkAdapter(okHttpClient)

    @Provides
    @Singleton
    fun provideOpenWrtAdapter(okHttpClient: OkHttpClient): OpenWrtUbusAdapter =
        OpenWrtUbusAdapter(okHttpClient)

    @Provides
    @Singleton
    fun provideMikroTikAdapter(okHttpClient: OkHttpClient): MikroTikAdapter =
        MikroTikAdapter(okHttpClient)

    @Provides
    @Singleton
    fun provideUniversalFallbackAdapter(okHttpClient: OkHttpClient): UniversalFallbackAdapter =
        UniversalFallbackAdapter(okHttpClient)

    @Provides
    @Singleton
    fun provideRouterAdapters(
        greenpacket: GreenpacketD2220G1Adapter,
        huawei: HuaweiAdapter,
        zte: ZteAdapter,
        tpLink: TpLinkAdapter,
        openWrt: OpenWrtUbusAdapter,
        mikrotik: MikroTikAdapter,
        fallback: UniversalFallbackAdapter
    ): List<@JvmSuppressWildcards RouterAdapter> = listOf(
        greenpacket,
        huawei,
        zte,
        tpLink,
        openWrt,
        mikrotik,
        fallback
    )

    @Provides
    @Singleton
    fun provideRouterAdapter(registry: RouterAdapterRegistry): RouterAdapter =
        object : RouterAdapter {
            override val brand: RouterBrand
                get() = registry.activeAdapter.brand
            override val supportedCapabilities: Set<RouterCapability>
                get() = registry.activeAdapter.supportedCapabilities

            override suspend fun fingerprint(ip: String, port: Int): Boolean =
                registry.activeAdapter.fingerprint(ip, port)

            override suspend fun login(
                ip: String,
                port: Int,
                username: String,
                password: String
            ): Result<Unit> = registry.activeAdapter.login(ip, port, username, password)

            override suspend fun logout() = registry.activeAdapter.logout()

            override suspend fun getLteSignal(): Result<LteSignalData> =
                registry.activeAdapter.getLteSignal()

            override suspend fun getConnectedDevices(): Result<List<ConnectedDevice>> =
                registry.activeAdapter.getConnectedDevices()

            override suspend fun blockDevice(mac: String): Result<Unit> =
                registry.activeAdapter.blockDevice(mac)

            override suspend fun unblockDevice(mac: String): Result<Unit> =
                registry.activeAdapter.unblockDevice(mac)

            override suspend fun getWifiSettings(): Result<WifiSettings> =
                registry.activeAdapter.getWifiSettings()

            override suspend fun setWifiSettings(settings: WifiSettings): Result<Unit> =
                registry.activeAdapter.setWifiSettings(settings)

            override suspend fun setGuestWifi(
                enabled: Boolean,
                ssid: String,
                password: String
            ): Result<Unit> = registry.activeAdapter.setGuestWifi(enabled, ssid, password)

            override suspend fun reboot(): Result<Unit> =
                registry.activeAdapter.reboot()

            override suspend fun downloadBackup(): Result<ByteArray> =
                registry.activeAdapter.downloadBackup()

            override suspend fun restoreBackup(data: ByteArray): Result<Unit> =
                registry.activeAdapter.restoreBackup(data)

            override suspend fun getSmsMessages(): Result<List<SmsMessage>> =
                registry.activeAdapter.getSmsMessages()

            override suspend fun sendSms(to: String, body: String): Result<Unit> =
                registry.activeAdapter.sendSms(to, body)

            override suspend fun deleteSms(id: String): Result<Unit> =
                registry.activeAdapter.deleteSms(id)

            override suspend fun sendUssd(code: String): Result<String> =
                registry.activeAdapter.sendUssd(code)

            override suspend fun getTrafficStats(): Result<TrafficStats> =
                registry.activeAdapter.getTrafficStats()

            override suspend fun getDslStats(): Result<DslStats> =
                registry.activeAdapter.getDslStats()

            override suspend fun setDns(primary: String, secondary: String): Result<Unit> =
                registry.activeAdapter.setDns(primary, secondary)

            override suspend fun setNetworkMode(mode: NetworkMode): Result<Unit> =
                registry.activeAdapter.setNetworkMode(mode)

            override suspend fun setBandSelection(bands: List<LteBand>): Result<Unit> =
                registry.activeAdapter.setBandSelection(bands)

            override suspend fun getSecurityInfo(): Result<RouterSecurityInfo> =
                registry.activeAdapter.getSecurityInfo()

            override suspend fun setMacFilter(mode: MacFilterMode, macs: List<String>): Result<Unit> =
                registry.activeAdapter.setMacFilter(mode, macs)

            override suspend fun setQos(rules: List<QosRule>): Result<Unit> =
                registry.activeAdapter.setQos(rules)

            override suspend fun addPortForwarding(rule: PortForwardRule): Result<Unit> =
                registry.activeAdapter.addPortForwarding(rule)

            override suspend fun getPortForwardRules(): Result<List<PortForwardRule>> =
                registry.activeAdapter.getPortForwardRules()

            override suspend fun setApn(apn: ApnProfile): Result<Unit> =
                registry.activeAdapter.setApn(apn)

            override suspend fun getApnProfiles(): Result<List<ApnProfile>> =
                registry.activeAdapter.getApnProfiles()
        }

}
