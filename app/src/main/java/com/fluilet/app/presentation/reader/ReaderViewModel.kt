package com.fluilet.app.presentation.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fluilet.app.core.tts.TtsManager
import com.fluilet.app.domain.model.AccessiblePalette
import com.fluilet.app.domain.model.ReadingSettings
import com.fluilet.app.domain.repository.ReaderSettingsRepository
import com.fluilet.app.domain.usecase.UpdateFontSizeUseCase
import com.fluilet.app.domain.usecase.UpdateLineSpacingUseCase
import com.fluilet.app.domain.usecase.UpdatePaletteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReaderUiState(
    val simplifiedText: String = DEFAULT_TEXT,
    val settings: ReadingSettings = ReadingSettings(),
    val isFocusModeActive: Boolean = false,
    val isTtsPlaying: Boolean = false,
    val spokenRange: IntRange? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    companion object {
        const val DEFAULT_TEXT = "La aplicación transforma textos complejos mediante un motor de " +
                "Inteligencia Artificial (NLP) propio basado en el modelo mT5, permitiendo una lectura " +
                "simplificada que reduce la fatiga visual y el estrés cognitivo."
    }
}

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val settingsRepository: ReaderSettingsRepository,
    private val updateLineSpacing: UpdateLineSpacingUseCase,
    private val updateFontSize: UpdateFontSizeUseCase,
    private val updatePalette: UpdatePaletteUseCase,
    private val ttsManager: TtsManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    init { loadSettings() }

    private fun loadSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            runCatching { settingsRepository.getSettings() }
                .onSuccess { settings -> _uiState.update { it.copy(settings = settings, isLoading = false) } }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, errorMessage = e.message) } }
        }
    }

    fun onLineSpacingChanged(value: Float) {
        updateLineSpacing(_uiState.value.settings, value)
            .onSuccess(::applySettings)
            .onFailure { e -> _uiState.update { it.copy(errorMessage = e.message) } }
    }

    fun onFontSizeChanged(value: Float) {
        updateFontSize(_uiState.value.settings, value)
            .onSuccess(::applySettings)
            .onFailure { e -> _uiState.update { it.copy(errorMessage = e.message) } }
    }

    fun onPaletteSelected(palette: AccessiblePalette) {
        updatePalette(_uiState.value.settings, palette).onSuccess(::applySettings)
    }

    fun onToggleFocusMode() {
        _uiState.update { it.copy(isFocusModeActive = !it.isFocusModeActive) }
    }

    fun onToggleTts() {
        if (_uiState.value.isTtsPlaying) {
            ttsManager.stop()
            _uiState.update { it.copy(isTtsPlaying = false, spokenRange = null) }
            return
        }
        _uiState.update { it.copy(isTtsPlaying = true) }
        ttsManager.speak(
            text = _uiState.value.simplifiedText,
            onRangeStart = { start, end ->
                _uiState.update { it.copy(spokenRange = start until end) }
            },
            onFinished = {
                _uiState.update { it.copy(isTtsPlaying = false, spokenRange = null) }
            }
        )
    }

    private fun applySettings(newSettings: ReadingSettings) {
        _uiState.update { it.copy(settings = newSettings) }
        viewModelScope.launch { settingsRepository.saveSettings(newSettings) }
    }

    fun consumeError() = _uiState.update { it.copy(errorMessage = null) }

    override fun onCleared() {
        super.onCleared()
        ttsManager.stop()
    }
}