package com.netmaster.pro.presentation.dsl

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.netmaster.pro.domain.adapter.RouterAdapter
import com.netmaster.pro.domain.adapter.getDslStatsData
import com.netmaster.pro.domain.adapter.DslStatsCompat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DslStats(
    val snrDownDb: Double = 0.0,
    val snrUpDb: Double = 0.0,
    val attenuationDownDb: Double = 0.0,
    val attenuationUpDb: Double = 0.0,
    val attainableDownKbps: Long = 0,
    val attainableUpKbps: Long = 0,
    val crcErrorsDown: Long = 0,
    val crcErrorsUp: Long = 0,
    val lineMode: String = "",
    val syncDownKbps: Long = 0,
    val syncUpKbps: Long = 0
)

data class DslAdvice(val icon: String, val messageAr: String, val severity: Severity)
enum class Severity { OK, WARNING, CRITICAL }

data class DslUiState(
    val stats: DslStats = DslStats(),
    val adviceCards: List<DslAdvice> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val lastUpdated: Long = 0L
)

@HiltViewModel
class DslAnalyzerViewModel @Inject constructor(
    application: Application,
    private val routerAdapter: RouterAdapter
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(DslUiState())
    val uiState: StateFlow<DslUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val dsl = routerAdapter.getDslStatsData()
                val stats = DslStats(
                    snrDownDb = dsl.snrDownDb,
                    snrUpDb = dsl.snrUpDb,
                    attenuationDownDb = dsl.attenuationDownDb,
                    attenuationUpDb = dsl.attenuationUpDb,
                    attainableDownKbps = dsl.attainableDownKbps,
                    attainableUpKbps = dsl.attainableUpKbps,
                    crcErrorsDown = dsl.crcErrorsDown,
                    crcErrorsUp = dsl.crcErrorsUp,
                    lineMode = dsl.lineMode,
                    syncDownKbps = dsl.syncDownKbps,
                    syncUpKbps = dsl.syncUpKbps
                )
                val advice = buildAdvice(stats)
                _uiState.update { it.copy(stats = stats, adviceCards = advice, isLoading = false, lastUpdated = System.currentTimeMillis()) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "فشل تحميل بيانات DSL: ${e.message}") }
            }
        }
    }

    private fun buildAdvice(s: DslStats): List<DslAdvice> {
        val list = mutableListOf<DslAdvice>()

        // SNR analysis
        when {
            s.snrDownDb >= 15 -> list.add(DslAdvice("✅", "هامش الضجيج (SNR) ممتاز — الخط مستقر", Severity.OK))
            s.snrDownDb >= 6 -> list.add(DslAdvice("⚠️", "هامش الضجيج (${s.snrDownDb}dB) مقبول — الحد الأدنى 6dB", Severity.WARNING))
            else -> list.add(DslAdvice("🔴", "هامش الضجيج منخفض جداً (${s.snrDownDb}dB) — قد تنقطع المزامنة", Severity.CRITICAL))
        }

        // Attenuation analysis
        when {
            s.attenuationDownDb < 20 -> list.add(DslAdvice("✅", "تخفيف الخط ممتاز (${s.attenuationDownDb}dB) — المسافة قصيرة", Severity.OK))
            s.attenuationDownDb < 40 -> list.add(DslAdvice("⚠️", "تخفيف الخط متوسط (${s.attenuationDownDb}dB)", Severity.WARNING))
            else -> list.add(DslAdvice("🔴", "تخفيف الخط مرتفع (${s.attenuationDownDb}dB) — قد تؤثر على السرعة", Severity.CRITICAL))
        }

        // CRC errors
        when {
            s.crcErrorsDown == 0L -> list.add(DslAdvice("✅", "لا يوجد خطأ CRC — جودة ممتازة", Severity.OK))
            s.crcErrorsDown < 100 -> list.add(DslAdvice("⚠️", "خطأ CRC منخفض (${s.crcErrorsDown}) — مراقبة مستمرة موصى بها", Severity.WARNING))
            else -> list.add(DslAdvice("🔴", "خطأ CRC مرتفع (${s.crcErrorsDown}) — تحقق من كابل الهاتف والمقبس", Severity.CRITICAL))
        }

        // Attainable vs sync comparison
        if (s.attainableDownKbps > 0 && s.syncDownKbps > 0) {
            val efficiency = s.syncDownKbps.toDouble() / s.attainableDownKbps * 100
            when {
                efficiency >= 80 -> list.add(DslAdvice("✅", "السرعة الفعلية ${efficiency.toInt()}% من السرعة النظرية — ممتاز", Severity.OK))
                efficiency >= 50 -> list.add(DslAdvice("⚠️", "السرعة الفعلية ${efficiency.toInt()}% من المتاح — يمكن التحسين", Severity.WARNING))
                else -> list.add(DslAdvice("🔴", "السرعة الفعلية أقل من 50% من المتاح — تحقق من الخط", Severity.CRITICAL))
            }
        }

        return list
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }
}
