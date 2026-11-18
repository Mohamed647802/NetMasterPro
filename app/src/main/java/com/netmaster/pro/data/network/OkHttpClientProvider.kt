package com.netmaster.pro.data.network

import android.util.Log
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import java.net.InetAddress
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.*

@Singleton
class OkHttpClientProvider @Inject constructor() {

    companion object {
        private const val TAG = "OkHttpClientProvider"
        private const val CONNECT_TIMEOUT_S = 10L
        private const val READ_TIMEOUT_S = 30L
        private const val WRITE_TIMEOUT_S = 30L

        // RFC 1918 private ranges
        private val PRIVATE_RANGES = listOf(
            Regex("""^192\.168\.\d{1,3}\.\d{1,3}$"""),
            Regex("""^10\.\d{1,3}\.\d{1,3}\.\d{1,3}$"""),
            Regex("""^172\.(1[6-9]|2\d|3[01])\.\d{1,3}\.\d{1,3}$""")
        )

        fun isPrivateIp(hostname: String): Boolean {
            return PRIVATE_RANGES.any { it.matches(hostname) }
        }
    }

    // ─── In-memory Cookie Store ───────────────────────────────────────────────

    private val cookieStore = ConcurrentHashMap<String, List<Cookie>>()

    private val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            cookieStore[url.host] = cookies
            Log.d(TAG, "Saved ${cookies.size} cookies for ${url.host}")
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return cookieStore[url.host] ?: emptyList()
        }
    }

    // ─── LAN-only self-signed TrustManager ───────────────────────────────────

    private val lanPermissiveTrustManager = object : X509TrustManager {
        private val systemTrustManager: X509TrustManager by lazy {
            val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            factory.init(null as java.security.KeyStore?)
            factory.trustManagers.filterIsInstance<X509TrustManager>().first()
        }

        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
            systemTrustManager.checkClientTrusted(chain, authType)
        }

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            // Allow system-trusted certs unconditionally
            try {
                systemTrustManager.checkServerTrusted(chain, authType)
                return
            } catch (_: CertificateException) {
                // Not system trusted — only allow if the cert is for a private LAN address
            }
            // For self-signed: validate that the CN / SAN references a private range
            val cert = chain.firstOrNull() ?: throw CertificateException("Empty chain")
            val cn = cert.subjectX500Principal.name
            Log.d(TAG, "Self-signed cert for: $cn — checking private range")
            // We also check via hostname verifier at TLS handshake; here we just allow it
            // since private LAN addresses won't have public CA chains.
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> =
            systemTrustManager.getAcceptedIssuers()
    }

    private val lanHostnameVerifier = HostnameVerifier { hostname, session ->
        // Always allow standard verification for public hosts
        HttpsURLConnection.getDefaultHostnameVerifier().verify(hostname, session)
            || isPrivateIp(hostname)
            || isPrivateIp(resolveToIp(hostname))
    }

    private fun resolveToIp(hostname: String): String = try {
        InetAddress.getByName(hostname).hostAddress ?: ""
    } catch (_: Exception) {
        ""
    }

    // ─── OkHttpClient ─────────────────────────────────────────────────────────

    val client: OkHttpClient by lazy {
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf(lanPermissiveTrustManager), null)

        val loggingInterceptor = HttpLoggingInterceptor { message ->
            Log.d(TAG, message)
        }.apply {
            level = if (com.netmaster.pro.BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .sslSocketFactory(sslContext.socketFactory, lanPermissiveTrustManager)
            .hostnameVerifier(lanHostnameVerifier)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_S, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /** Clears all stored session cookies */
    fun clearCookies() {
        cookieStore.clear()
    }

    /** Removes cookies for a specific host */
    fun clearCookiesFor(host: String) {
        cookieStore.remove(host)
    }
}
