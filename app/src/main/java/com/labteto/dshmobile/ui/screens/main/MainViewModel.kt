package com.labteto.dshmobile.ui.screens.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.labteto.dshmobile.connection.ConnectionManager
import com.labteto.dshmobile.connection.HostConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MainUiState(
    val host: HostConfig? = null,
    val drawerOpen: Boolean = false,
    val selectedSessionId: String? = null,
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val connectionManager: ConnectionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            connectionManager.state.collect { conn ->
                _state.update { it.copy(host = conn.host) }
            }
        }
    }

    fun selectSession(sessionId: String) {
        _state.update { it.copy(selectedSessionId = sessionId) }
    }

    fun disconnect() = connectionManager.disconnect()
}
