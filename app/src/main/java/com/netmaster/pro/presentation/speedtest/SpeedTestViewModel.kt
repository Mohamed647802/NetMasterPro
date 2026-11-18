package com.netmaster.pro.presentation.speedtest

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.netmaster.pro.data.local.AppDatabase
import com.netmaster.pro.data.local.SpeedTestEntity
import com.netmaster.pro.data.model.SpeedTestRecord
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class GamePingResult(
    val name: String,
    val host: String,
    val pingMs: Long,
    val packetLoss: Int,
    val status: String
)

data class SpeedTestUiState(
    val phase: SpeedTestPhase = SpeedTestPhase.IDLE,
    val lanDownloadMbps: Double = 0.0,
    val wanDownloadMbps: Double = 0.0,
    val wanUploadMbps: Double = 0.0,
    val pingMs: Long = 0,
    val jitterMs: Long = 0,
    val bufferbloatGrade: String = "—",
    val diagnosisMessage: String = "",
    val history: List<SpeedTestRecord> = emptyList(),
    val gamePingResults: List<GamePingResult> = emptyList(),
    val progressLan: Float = 0f,
    val progressWan: Float = 0f,
    val errorMessage: String? = null
)

enum class SpeedTestPhase {
    IDLE, LAN_TEST, WAN_DOWNLOAD, WAN_UPLOAD, PING, GAME_PING, DONE
}

@HiltViewModel
class SpeedTestViewModel @Inject constructor(
    application: Application,
    private val db: AppDatabase
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SpeedTestUiState())
    val uiState: StateFlow<SpeedTestUiState> = _uiState.asStateFlow()

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gameServers = listOf(
        "PUBG Mobile ME" to "any.pubgmobile.com",
        "EA FC" to "sea1.eas.eagames.com",
        "Call of Duty" to "cod.prod.demonware.net",
        "Valorant EU" to "valorant.secure.dyn.riotgames.com"
    )

    init {
        loadHistory()
    }

    private fun loadHistory() {
        viewModelScope.launch {
            val entities = db.speedTestDao().getHistory(10)
            val records = entities.map { e ->
                SpeedTestRecord(
                    id = e.id,
                    timestamp = e.timestamp,
                    downloadMbps = e.downloadMbps,
                    uploadMbps = e.uploadMbps,
                    pingMs = e.pingMs.toLong(),
                    jitterMs = e.jitterMs.toLong()
                )
            }
            _uiState.update { it.copy(history = records) }
        }
    }
    fun startTest(gatewayIp: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(phase = SpeedTestPhase.LAN_TEST, progressLan = 0f, progressWan = 0f) }

            // Phase 1: LAN speed
            val lanSpeed = measureLanSpeed(gatewayIp)
            _uiState.update { it.copy(lanDownloadMbps = lanSpeed, progressLan = 1f, phase = SpeedTestPhase.WAN_DOWNLOAD) }

            // Phase 2: WAN download
            val wanDown = measureWanDownload { progress ->
                _uiState.update { it.copy(progressWan = progress * 0.5f) }
            }
            _uiState.update { it.copy(wanDownloadMbps = wanDown, phase = SpeedTestPhase.WAN_UPLOAD) }

            // Phase 3: WAN upload
            val wanUp = measureWanUpload { progress ->
                _uiState.update { it.copy(progressWan = 0.5f + progress * 0.5f) }
            }
            _uiState.update { it.copy(wanUploadMbps = wanUp, progressWan = 1f, phase = SpeedTestPhase.PING) }

            // Phase 4: Ping + bufferbloat
            val pingResults = measurePingAndBufferbloat()
            val grade = bufferbloatGrade(pingResults.first, pingResults.second)
            val diagnosis = buildDiagnosis(lanSpeed, wanDown, pingResults.first)

            _uiState.update {
                it.copy(
                    pingMs = pingResults.first,
                    jitterMs = pingResults.second,
                    bufferbloatGrade = grade,
                    diagnosisMessage = diagnosis,
                    phase = SpeedTestPhase.GAME_PING
                )
            }

            // Phase 5: Game pings
            val gamePings = measureGamePings()
            _uiState.update { it.copy(gamePingResults = gamePings, phase = SpeedTestPhase.DONE) }

            // Save to history
            val entity = SpeedTestEntity(
                downloadMbps = wanDown,
                uploadMbps = wanUp,
                pingMs = pingResults.first.toDouble(),
                jitterMs = pingResults.second.toDouble(),
                bufferbloatGrade = grade,
                testType = "WAN"
            )
            db.speedTestDao().insertResult(entity)
            loadHistory()        }
    }

    private suspend fun measureLanSpeed(gatewayIp: String): Double = withContext(Dispatchers.IO) {
        return@withContext try {
            val url = "http://$gatewayIp/"
            val iterations = 20
            var totalBytes = 0L
            val startTime = System.currentTimeMillis()
            repeat(iterations) {
                val req = Request.Builder().url(url).build()
                try {
                    client.newCall(req).execute().use { resp ->
                        totalBytes += resp.body?.bytes()?.size?.toLong() ?: 0L
                    }
                } catch (_: Exception) {}
            }
            val elapsed = (System.currentTimeMillis() - startTime) / 1000.0
            if (elapsed > 0) (totalBytes * 8.0) / (elapsed * 1_000_000) else 0.0
        } catch (e: Exception) { 0.0 }
    }

    private suspend fun measureWanDownload(onProgress: (Float) -> Unit): Double = withContext(Dispatchers.IO) {
        return@withContext try {
            val url = "https://speed.cloudflare.com/__down?bytes=10000000"
            val req = Request.Builder().url(url).build()
            val startTime = System.currentTimeMillis()
            var bytes = 0L
            client.newCall(req).execute().use { resp ->
                val body = resp.body ?: return@withContext 0.0
                val totalExpected = 10_000_000L
                val buffer = ByteArray(32768)
                val source = body.byteStream()
                var read: Int
                while (source.read(buffer).also { read = it } != -1) {
                    bytes += read
                    onProgress((bytes.toFloat() / totalExpected).coerceIn(0f, 1f))
                }
            }
            val elapsed = (System.currentTimeMillis() - startTime) / 1000.0
            if (elapsed > 0) (bytes * 8.0) / (elapsed * 1_000_000) else 0.0
        } catch (e: Exception) { 0.0 }
    }

    private suspend fun measureWanUpload(onProgress: (Float) -> Unit): Double = withContext(Dispatchers.IO) {
        return@withContext try {
            val uploadSize = 5_000_000
            val body = RequestBody.create("application/octet-stream".toMediaType(), ByteArray(uploadSize))
            val req = Request.Builder()
                .url("https://speed.cloudflare.com/__up")
                .post(body)
                .build()
            val startTime = System.currentTimeMillis()
            onProgress(0.5f)
            client.newCall(req).execute().use { }
            val elapsed = (System.currentTimeMillis() - startTime) / 1000.0
            onProgress(1f)
            if (elapsed > 0) (uploadSize * 8.0) / (elapsed * 1_000_000) else 0.0
        } catch (e: Exception) { 0.0 }
    }

    private suspend fun measurePingAndBufferbloat(): Pair<Long, Long> = withContext(Dispatchers.IO) {
        val pings = mutableListOf<Long>()
        repeat(10) {
            val start = System.currentTimeMillis()
            try {
                val req = Request.Builder()
                    .url("https://1.1.1.1/cdn-cgi/trace")
                    .head()
                    .build()
                client.newCall(req).execute().use { }
                pings.add(System.currentTimeMillis() - start)
            } catch (_: Exception) { pings.add(999L) }
        }
        val avg = pings.average().toLong()
        val jitter = if (pings.size > 1) {
            pings.zipWithNext { a, b -> Math.abs(a - b) }.average().toLong()
        } else 0L
        Pair(avg, jitter)
    }

    private fun bufferbloatGrade(pingMs: Long, jitterMs: Long): String {
        val score = pingMs + jitterMs * 2
        return when {
            score < 50 -> "A"
            score < 100 -> "B"
            score < 200 -> "C"
            score < 400 -> "D"
            else -> "F"
        }
    }

    private fun buildDiagnosis(lanMbps: Double, wanMbps: Double, pingMs: Long): String {
        return when {
            lanMbps < 10 -> "السبب: إشارة Wi-Fi ضعيفة"
            wanMbps < 5 -> "السبب: سرعة الإنترنت الواردة من المزوّد منخفضة"
            pingMs > 200 -> "السبب: تأخر عالٍ — قد يكون هناك اكتظاظ في الشبكة"
            lanMbps > wanMbps * 3 -> "السبب: تباطؤ من جانب المزوّد"
            else -> "الشبكة تعمل بشكل جيد ✓"
        }
    }

    private suspend fun measureGamePings(): List<GamePingResult> = withContext(Dispatchers.IO) {
        gameServers.map { (name, host) ->
            async {
                val pings = mutableListOf<Long>()
                var lost = 0
                repeat(5) {
                    val start = System.currentTimeMillis()
                    try {
                        val req = Request.Builder().url("https://$host/").head().build()
                        OkHttpClient.Builder()
                            .connectTimeout(3, TimeUnit.SECONDS)
                            .readTimeout(3, TimeUnit.SECONDS)
                            .build()
                            .newCall(req).execute().use { }
                        pings.add(System.currentTimeMillis() - start)
                    } catch (_: Exception) {
                        lost++
                    }
                }
                val avg = if (pings.isNotEmpty()) pings.average().toLong() else 999L
                val lossPercent = (lost * 100 / 5)
                val status = when {
                    pings.isEmpty() -> "غير متاح"
                    avg < 50 -> "ممتاز"
                    avg < 100 -> "جيد"
                    avg < 200 -> "متوسط"
                    else -> "سيئ"
                }
                GamePingResult(name, host, avg, lossPercent, status)
            }
        }.map { it.await() }
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }
}
