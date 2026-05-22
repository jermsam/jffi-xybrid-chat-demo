package com.yourcompany.testapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uniffi.testapp_core.Core
import uniffi.testapp_core.ChatMessage
import uniffi.testapp_core.ChatCallback

data class AppUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isSending: Boolean = false,
    val isModelLoading: Boolean = false,
    val isModelReady: Boolean = false,
    val error: String? = null
)

class AppViewModel : ViewModel() {
    private val core = Core()

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        refreshMessages()
        warmupModel()
    }

    private fun warmupModel() {
        viewModelScope.launch {
            _uiState.update { it.copy(isModelLoading = true) }
            val success = withContext(Dispatchers.IO) {
                try {
                    uniffi.testapp_core.initModel()
                    true
                } catch (e: Exception) {
                    android.util.Log.e("AppViewModel", "Failed to warmup model", e)
                    false
                }
            }
            _uiState.update { it.copy(isModelLoading = false, isModelReady = success) }
        }
    }

    fun refreshMessages() {
        _uiState.update { currentState ->
            currentState.copy(messages = core.getHistory())
        }
    }

    fun updateInputText(text: String) {
        _uiState.update { currentState ->
            currentState.copy(inputText = text)
        }
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty()) return

        _uiState.update { currentState ->
            currentState.copy(
                isSending = true,
                inputText = "",
                error = null
            )
        }
        
        // Refresh immediately so the user's message appears on screen
        refreshMessages()

        viewModelScope.launch {
            val errorResult = withContext(Dispatchers.IO) {
                try {
                    core.sendMessageStreaming(text, object : ChatCallback {
                        override fun onToken(token: String) {
                            viewModelScope.launch(Dispatchers.Main) {
                                refreshMessages()
                            }
                        }
                    })
                    null
                } catch (e: Exception) {
                    e.message ?: "Unknown error occurred"
                }
            }

            _uiState.update { currentState ->
                currentState.copy(
                    isSending = false,
                    error = errorResult
                )
            }
            refreshMessages()
        }
    }

    fun clearHistory() {
        core.clearHistory()
        refreshMessages()
    }

    override fun onCleared() {
        super.onCleared()
    }
}
