package com.netmaster.pro.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.netmaster.pro.data.local.*
import com.netmaster.pro.data.local.security.EncryptedPreferencesManager
import com.netmaster.pro.data.network.OkHttpClientProvider
import com.netmaster.pro.data.network.OuiDatabase
import com.netmaster.pro.domain.adapter.fingerprint.GatewayDetector
import com.netmaster.pro.domain.adapter.fingerprint.RouterFingerprinter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "netmaster_settings"
)

private val json = Json { ignoreUnknownKeys = true }

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ─── OkHttp ──────────────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideOkHttpClientProvider(): OkHttpClientProvider = OkHttpClientProvider()

    @Provides
    @Singleton
    fun provideOkHttpClient(provider: OkHttpClientProvider): OkHttpClient = provider.client

    // ─── Retrofit (generic base — adapters build their own instances) ─────────

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl("http://192.168.1.1/") // placeholder; each adapter overrides this
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    // ─── Room Database ────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration()
            .build()

    // ─── DAOs ─────────────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideConnectedDeviceDao(db: AppDatabase): ConnectedDeviceDao =
        db.connectedDeviceDao()

    @Provides
    @Singleton
    fun provideTrafficLogDao(db: AppDatabase): TrafficLogDao =
        db.trafficLogDao()

    @Provides
    @Singleton
    fun provideSignalSnapshotDao(db: AppDatabase): SignalSnapshotDao =
        db.signalSnapshotDao()

    @Provides
    @Singleton
    fun provideSpeedTestDao(db: AppDatabase): SpeedTestDao =
        db.speedTestDao()

    @Provides
    @Singleton
    fun provideRouterProfileDao(db: AppDatabase): RouterProfileDao =
        db.routerProfileDao()

    // ─── DataStore ────────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.dataStore

    // ─── Security ─────────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideEncryptedPreferencesManager(
        @ApplicationContext context: Context
    ): EncryptedPreferencesManager = EncryptedPreferencesManager(context)

    // ─── OUI Database ─────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideOuiDatabase(@ApplicationContext context: Context): OuiDatabase =
        OuiDatabase(context)

    // ─── Fingerprinting ───────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideGatewayDetector(@ApplicationContext context: Context): GatewayDetector =
        GatewayDetector(context)

    @Provides
    @Singleton
    fun provideRouterFingerprinter(
        okHttpClientProvider: OkHttpClientProvider,
        gatewayDetector: GatewayDetector
    ): RouterFingerprinter = RouterFingerprinter(okHttpClientProvider, gatewayDetector)
}
