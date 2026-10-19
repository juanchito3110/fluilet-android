package com.fluilet.app.presentation.child

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fluilet.app.core.network.toAppError
import com.fluilet.app.domain.model.AccessiblePalette
import com.fluilet.app.domain.model.ChildDraft
import com.fluilet.app.domain.model.DifficultyType
import com.fluilet.app.domain.model.NlpLevel
import com.fluilet.app.domain.model.ReadingSettings
import com.fluilet.app.domain.usecase.GetChildrenUseCase
import com.fluilet.app.domain.usecase.SaveChildUseCase
import com.fluilet.app.domain.validation.Validators
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class ChildFormUiState(
    val isEditing: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val name: String = "",
    val birthDate: LocalDate? = null,
    val difficulty: DifficultyType = DifficultyType.COMBINADO,
    val nlpLevel: NlpLevel = NlpLevel.DEFAULT,
    val palette: AccessiblePalette = AccessiblePalette.DEFAULT,
    val fontSizeSp: Float = ReadingSettings.DEFAULT_FONT_SIZE,
    val lineSpacing: Float = ReadingSettings.DEFAULT_LINE_SPACING,
    val errorMessage: String? = null
) {
    val birthDateError: String? get() = birthDate?.let { Validators.childBirthDate(it) }
    val canSave: Boolean
        get() = Validators.childName(name) == null && !isSaving &&
            (isEditing || (birthDate != null && birthDateError == null))
}

@HiltViewModel
class ChildFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getChildren: GetChildrenUseCase,
    private val saveChild: SaveChildUseCase
) : ViewModel() {

    private val childId: String? = savedStateHandle.get<String>("childId")?.takeIf { it.isNotBlank() }

    private val _state = MutableStateFlow(ChildFormUiState(isEditing = childId != null, isLoading = childId != null))
    val state: StateFlow<ChildFormUiState> = _state.asStateFlow()

    private val _saved = Channel<Unit>(Channel.BUFFERED)
    val saved = _saved.receiveAsFlow()

    init {
        if (childId != null) {
            viewModelScope.launch {
                getChildren()
                    .onSuccess { list ->
                        val child = list.firstOrNull { it.id == childId }
                        if (child == null) {
                            _state.update { it.copy(isLoading = false, errorMessage = "No encontramos este perfil.") }
                        } else {
                            _state.update {
                                it.copy(
                                    isLoading = false,
                                    name = child.name,
                                    birthDate = child.birthDate,
                                    difficulty = child.difficulty,
                                    nlpLevel = child.nlpLevel,
                                    palette = child.palette,
                                    fontSizeSp = child.fontSizeSp,
                                    lineSpacing = child.lineSpacing
                                )
                            }
                        }
                    }
                    .onFailure { e -> _state.update { it.copy(isLoading = false, errorMessage = e.toAppError().userMessage) } }
            }
        }
    }

    fun onName(v: String) = _state.update { it.copy(name = v.take(100), errorMessage = null) }
    fun onBirthDate(v: LocalDate) = _state.update { it.copy(birthDate = v, errorMessage = null) }
    fun onDifficulty(v: DifficultyType) = _state.update { it.copy(difficulty = v) }
    fun onNlp(v: NlpLevel) = _state.update { it.copy(nlpLevel = v) }
    fun onPalette(v: AccessiblePalette) = _state.update { it.copy(palette = v) }
    fun onFontSize(v: Float) = _state.update { it.copy(fontSizeSp = v) }
    fun onLineSpacing(v: Float) = _state.update { it.copy(lineSpacing = v) }

    fun save() {
        val s = _state.value
        val birth = s.birthDate ?: return
        if (!s.canSave) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, errorMessage = null) }
            val draft = ChildDraft(s.name, birth, s.difficulty, s.nlpLevel, s.palette, s.fontSizeSp, s.lineSpacing)
            saveChild(childId, draft)
                .onSuccess { _saved.trySend(Unit) }
                .onFailure { e -> _state.update { it.copy(errorMessage = e.toAppError().userMessage) } }
            _state.update { it.copy(isSaving = false) }
        }
    }
}
