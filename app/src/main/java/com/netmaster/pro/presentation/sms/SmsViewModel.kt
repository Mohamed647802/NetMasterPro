package com.netmaster.pro.presentation.sms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netmaster.pro.data.model.SmsMessage
import com.netmaster.pro.domain.repository.RouterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class SmsUiState {
    object Loading : SmsUiState()
    data class Success(val messages: List<SmsMessage>) : SmsUiState()
    data class Error(val message: String) : SmsUiState()
    object Empty : SmsUiState()
}

sealed class UssdResult {
    object Idle : UssdResult()
    object Loading : UssdResult()
    data class Response(val code: String, val reply: String) : UssdResult()
    data class Error(val message: String) : UssdResult()
}

@HiltViewModel
class SmsViewModel @Inject constructor(
    private val routerRepository: RouterRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<SmsUiState>(SmsUiState.Loading)
    val uiState: StateFlow<SmsUiState> = _uiState.asStateFlow()

    private val _ussdResult = MutableStateFlow<UssdResult>(UssdResult.Idle)
    val ussdResult: StateFlow<UssdResult> = _ussdResult.asStateFlow()

    private val _pendingDeleteId = MutableStateFlow<String?>(null)
    val pendingDeleteId: StateFlow<String?> = _pendingDeleteId.asStateFlow()

    init {
        loadMessages()
    }

    fun loadMessages() {
        viewModelScope.launch {
            _uiState.value = SmsUiState.Loading
            routerRepository.getSmsMessages().fold(
                onSuccess = { messages ->
                    _uiState.value = if (messages.isEmpty()) SmsUiState.Empty
                    else SmsUiState.Success(messages)
                },
                onFailure = { _uiState.value = SmsUiState.Error(it.message ?: "Unknown error") }
            )
        }
    }

    fun requestDelete(id: String) {
        _pendingDeleteId.value = id
    }

    fun cancelDelete() {
        _pendingDeleteId.value = null
    }

    fun confirmDelete(id: String) {
        _pendingDeleteId.value = null
        viewModelScope.launch {
            routerRepository.deleteSms(id)
            loadMessages()
        }
    }

    fun sendSms(to: String, body: String) {
        viewModelScope.launch {
            routerRepository.sendSms(to, body).fold(
                onSuccess = { loadMessages() },
                onFailure = { _uiState.value = SmsUiState.Error(it.message ?: "Send failed") }
            )
        }
    }

    fun sendUssd(code: String) {
        viewModelScope.launch {
            _ussdResult.value = UssdResult.Loading
            routerRepository.sendUssd(code).fold(
                onSuccess = { reply -> _ussdResult.value = UssdResult.Response(code, reply) },
                onFailure = { _ussdResult.value = UssdResult.Error(it.message ?: "USSD failed") }
            )
        }
    }

    fun clearUssdResult() {
        _ussdResult.value = UssdResult.Idle
    }
}
