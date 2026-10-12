package com.listeningnotes.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.listeningnotes.app.AppContainer
import com.listeningnotes.app.data.repository.AppSettings
import com.listeningnotes.app.data.repository.ThemeMode
import com.listeningnotes.app.whisper.ModelSpec
import com.listeningnotes.app.whisper.ModelState
import com.listeningnotes.app.whisper.WhisperNative
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    val settings: StateFlow<AppSettings> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val modelStates: StateFlow<Map<String, ModelState>> = container.modelManager.states

    /** Elige el modelo; si no está instalado, empieza a descargarlo (descarga bajo demanda). */
    fun selectModel(spec: ModelSpec) {
        viewModelScope.launch { container.settings.setModel(spec.id) }
        container.modelManager.download(spec)
    }

    fun download(spec: ModelSpec) = container.modelManager.download(spec)
    fun pause(spec: ModelSpec) = container.modelManager.cancel(spec)
    fun delete(spec: ModelSpec) = container.modelManager.delete(spec)

    fun setThreads(n: Int) {
        viewModelScope.launch { container.settings.setThreads(n) }
    }

    fun setSplitSentences(value: Boolean) {
        viewModelScope.launch { container.settings.setSplitSentences(value) }
    }

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { container.settings.setTheme(mode) }
    }

    /** Información del motor (NEON, fp16...) para diagnosticar el rendimiento. */
    fun engineInfo(): String = try {
        WhisperNative.systemInfo()
    } catch (t: Throwable) {
        "No se pudo cargar la librería nativa: ${t.message}"
    }
}
