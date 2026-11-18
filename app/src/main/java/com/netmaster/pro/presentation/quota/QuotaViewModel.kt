package com.netmaster.pro.presentation.quota

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.netmaster.pro.domain.adapter.RouterAdapter
import com.netmaster.pro.domain.adapter.getTrafficStatsData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class QuotaUiState(
    val bundleSizeGb: Double = 100.0,
    val renewalDay: Int = 1,
    val usedGb: Double = 0.0,
    val alertAt50: Boolean = true,
    val alertAt80: Boolean = true,
    val alertAt95: Boolean = true,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    val remainingGb: Double get() = (bundleSizeGb - usedGb).coerceAtLeast(0.0)
    val usedPercent: Float get() = if (bundleSizeGb > 0) (usedGb / bundleSizeGb).toFloat().coerceIn(0f, 1f) else 0f
    val daysUntilRenewal: Int get() {
        val today = LocalDate.now()
        val renewal = today.withDayOfMonth(renewalDay.coerceIn(1, 28))
        return if (renewal.isBefore(today) || renewal == today)
            ChronoUnit.DAYS.between(today, renewal.plusMonths(1)).toInt()
        else
            ChronoUnit.DAYS.between(today, renewal).toInt()
    }
    val dailyAllowanceGb: Double get() = if (daysUntilRenewal > 0) remainingGb / daysUntilRenewal else remainingGb
    val estimatedDaysLeft: Double get() = if (usedGb > 0 && daysUntilRenewal > 0) {
        val dailyUsage = usedGb / (30 - daysUntilRenewal).coerceAtLeast(1)
        if (dailyUsage > 0) remainingGb / dailyUsage else 999.0
    } else 999.0
}

@HiltViewModel
class QuotaViewModel @Inject constructor(
    application: Application,
    private val routerAdapter: RouterAdapter
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(QuotaUiState())
    val uiState: StateFlow<QuotaUiState> = _uiState.asStateFlow()

    init {
        loadTrafficStats()
    }

    fun loadTrafficStats() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val stats = routerAdapter.getTrafficStatsData()
                val usedGb = (stats.rxBytes + stats.txBytes) / (1024.0 * 1024.0 * 1024.0)
                _uiState.update { it.copy(usedGb = usedGb, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "فشل تحميل إحصائيات الاستهلاك: ${e.message}") }
            }
        }
    }

    fun setBundleSize(gb: Double) = _uiState.update { it.copy(bundleSizeGb = gb) }
    fun setRenewalDay(day: Int) = _uiState.update { it.copy(renewalDay = day.coerceIn(1, 28)) }
    fun setAlert50(enabled: Boolean) = _uiState.update { it.copy(alertAt50 = enabled) }
    fun setAlert80(enabled: Boolean) = _uiState.update { it.copy(alertAt80 = enabled) }
    fun setAlert95(enabled: Boolean) = _uiState.update { it.copy(alertAt95 = enabled) }
    fun clearError() = _uiState.update { it.copy(errorMessage = null) }
}
