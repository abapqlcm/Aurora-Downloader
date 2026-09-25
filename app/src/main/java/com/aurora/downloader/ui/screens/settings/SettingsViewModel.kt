package com.aurora.downloader.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aurora.downloader.AuroraApp
import com.aurora.downloader.data.datastore.AuroraSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModelFactory(private val app: AuroraApp) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        SettingsViewModel(app) as T
}

class SettingsViewModel(app: AuroraApp) : ViewModel() {

    val settings: StateFlow<AuroraSettings> = app.settings.flow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AuroraSettings()
    )

    fun setConcurrent(value: Int) = viewModelScope.launch {
        app.settings.setConcurrent(value.coerceIn(1, 10))
    }

    fun setParts(value: Int) = viewModelScope.launch {
        app.settings.setParts(value.coerceIn(1, 16))
    }

    fun setWifiOnly(value: Boolean) = viewModelScope.launch {
        app.settings.setWifiOnly(value)
    }

    fun setLimitEnabled(value: Boolean) = viewModelScope.launch {
        val current = kotlinx.coroutines.flow.first(app.settings.flow)
        app.settings.setSpeedLimit(value, current.speedLimitKBps.coerceAtLeast(1024))
    }

    fun setLimitKbps(value: Int) = viewModelScope.launch {
        app.settings.setSpeedLimit(true, value.coerceIn(100, 20_000))
    }
}
