package com.netmaster.pro.di

import com.netmaster.pro.data.adapter.GreenpacketD2220G1Adapter
import com.netmaster.pro.data.adapter.HuaweiAdapter
import com.netmaster.pro.data.adapter.MikroTikAdapter
import com.netmaster.pro.data.adapter.OpenWrtUbusAdapter
import com.netmaster.pro.data.adapter.TpLinkAdapter
import com.netmaster.pro.data.adapter.UniversalFallbackAdapter
import com.netmaster.pro.data.adapter.ZteAdapter
import com.netmaster.pro.domain.adapter.RouterAdapter
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

}
