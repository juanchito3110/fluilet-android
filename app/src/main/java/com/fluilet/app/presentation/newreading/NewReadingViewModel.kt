package com.fluilet.app.presentation.newreading

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.fluilet.app.core.auth.SessionManager
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fluilet.app.core.network.AppError
import com.fluilet.app.core.network.toAppError
import com.fluilet.app.domain.model.ActiveReading
import com.fluilet.app.domain.model.AdaptationResult
import com.fluilet.app.domain.model.AdaptationSource
import com.fluilet.app.domain.model.AgeGroup
import com.fluilet.app.domain.model.ChildProfile
import com.fluilet.app.domain.model.LessonCategory
import com.fluilet.app.domain.model.LessonRequest
import com.fluilet.app.domain.model.NlpLevel
import com.fluilet.app.domain.model.ReadingSettings
import com.fluilet.app.domain.model.SourceType
import com.fluilet.app.domain.session.ActiveReadingHolder
import com.fluilet.app.domain.usecase.AdaptTextUseCase
import com.fluilet.app.domain.usecase.GenerateLessonUseCase
import com.fluilet.app.domain.usecase.GetChildrenUseCase
import com.fluilet.app.domain.usecase.IsTutorPinConfiguredUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class InputMode { PASTE, FILE, GENERATE }

/** Qué hacer al terminar: guardar la lectura para después o empezar a leer ahora. */
enum class NewReadingOutcome { SAVED, START_NOW }

data class NewReadingUiState(
    val mode: InputMode = InputMode.PASTE,
    val text: String = "",
    val fileName: String? = null,
    /** Generar lección: categoría elegida y tema libre opcional (hasta 120 caracteres). */
    val category: LessonCategory? = null,
    val topic: String = "",
    val level: NlpLevel = NlpLevel.DEFAULT,
    val readerName: String = "",
    val isReadingFile: Boolean = false,
    val isAdapting: Boolean = false,
    /** Lectura para un perfil de niño: iniciar exige PIN de tutor (Muro Parental). */
    val forChild: Boolean = false,
    val pinConfigured: Boolean = true,
    val result: AdaptationResult? = null,
    val errorMessage: String? = null
) {
    val hasInput: Boolean
        get() = when (mode) {
            InputMode.PASTE -> text.isNotBlank()
            InputMode.FILE -> fileName != null
            InputMode.GENERATE -> category != null
        }
    val canAdapt: Boolean get() = hasInput && !isAdapting && !isReadingFile
}

/**
 * Cero retención: el archivo o texto se mantiene en memoria solo hasta adaptarlo; el resultado va
 * a [ActiveReadingHolder] (RAM). Nada se escribe a disco.
 */
@HiltViewModel
class NewReadingViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val getChildren: GetChildrenUseCase,
    private val adaptText: AdaptTextUseCase,
    private val generateLesson: GenerateLessonUseCase,
    private val holder: ActiveReadingHolder,
    private val sessionManager: SessionManager,
    private val isPinConfigured: IsTutorPinConfiguredUseCase
) : ViewModel() {

    private var childId: String? = savedStateHandle.get<String>("childId")?.takeIf { it.isNotBlank() }
    private var child: ChildProfile? = null
    private var pendingFile: AdaptationSource? = null

    private val _state = MutableStateFlow(NewReadingUiState(forChild = childId != null, pinConfigured = isPinConfigured()))
    val state: StateFlow<NewReadingUiState> = _state.asStateFlow()

    private val _done = Channel<NewReadingOutcome>(Channel.BUFFERED)
    val done = _done.receiveAsFlow()

    init {
        loadChild()
    }

    private fun loadChild() {
        val id = childId ?: return
        viewModelScope.launch {
            getChildren().onSuccess { list ->
                if (childId != id) return@onSuccess // el selector cambió mientras cargaba
                child = list.firstOrNull { it.id == id }
                child?.let { c -> _state.update { it.copy(level = c.nlpLevel, readerName = c.name) } }
            }
        }
    }

    /**
     * Cambia el perfil para el que se crea la lectura (selector de niño del apartado "Lecturas"):
     * recarga su nivel y su nombre, y descarta cualquier resultado pendiente del perfil anterior.
     */
    fun bindChild(newChildId: String?) {
        val normalized = newChildId?.takeIf { it.isNotBlank() }
        if (normalized == childId && (normalized == null || child != null)) return
        childId = normalized
        child = null
        _state.update {
            it.copy(forChild = normalized != null, readerName = "", result = null, errorMessage = null, level = NlpLevel.DEFAULT)
        }
        loadChild()
    }

    /** Un toque en una sugerencia rellena el tema (el niño también puede escribir cualquier texto). */
    fun onSuggestion(suggestion: String) = onTopic(suggestion)

    fun onMode(mode: InputMode) = _state.update { it.copy(mode = mode, errorMessage = null, result = null) }
    fun onCategory(category: LessonCategory) = _state.update { it.copy(category = category, errorMessage = null, result = null) }
    fun onTopic(value: String) = _state.update { it.copy(topic = value.take(LessonRequest.MAX_TOPIC_LENGTH), errorMessage = null, result = null) }
    fun onText(value: String) = _state.update { it.copy(text = value, errorMessage = null, result = null) }
    fun onLevel(level: NlpLevel) = _state.update { it.copy(level = level, result = null) }
    fun backToEdit() = _state.update { it.copy(result = null) }

    fun onFilePicked(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            _state.update { it.copy(isReadingFile = true, errorMessage = null, result = null) }
            runCatching { readFile(uri) }
                .onSuccess { source ->
                    pendingFile = source
                    _state.update { it.copy(isReadingFile = false, fileName = source.fileName) }
                }
                .onFailure { e ->
                    pendingFile = null
                    _state.update { it.copy(isReadingFile = false, fileName = null, errorMessage = e.toAppError().userMessage) }
                }
        }
    }

    private suspend fun readFile(uri: Uri): AdaptationSource = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: "documento"
        val type = when {
            name.endsWith(".pdf", ignoreCase = true) || resolver.getType(uri) == "application/pdf" -> SourceType.PDF
            name.endsWith(".txt", ignoreCase = true) || resolver.getType(uri)?.startsWith("text/") == true -> SourceType.TXT
            else -> throw AppError.Validation("Solo se admiten archivos .txt o .pdf.")
        }
        val limit = AdaptationSource.MAX_BYTES
        val bytes = resolver.openInputStream(uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > limit) throw AppError.TooLarge()
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        } ?: throw AppError.Validation("No se pudo abrir el archivo.")
        AdaptationSource(name, type, bytes)
    }

    fun adapt() {
        val s = _state.value
        if (!s.canAdapt) return
        if (s.mode == InputMode.GENERATE) return generate(s)
        val source = if (s.mode == InputMode.PASTE) {
            AdaptationSource("Texto pegado", SourceType.PASTED_TEXT, s.text.toByteArray(Charsets.UTF_8))
        } else {
            pendingFile ?: return
        }
        viewModelScope.launch {
            _state.update { it.copy(isAdapting = true, errorMessage = null, result = null) }
            adaptText(source, s.level, childId, activeAgeGroup())
                .onSuccess { result ->
                    // El original solo se conserva en RAM (texto pegado o .txt) para la vista "Original vs. Adaptado".
                    val original = originalTextOf(source)
                    _state.update { it.copy(isAdapting = false, result = result.copy(originalText = result.originalText ?: original)) }
                }
                .onFailure { e -> _state.update { it.copy(isAdapting = false, errorMessage = e.toAppError().userMessage) } }
        }
    }

    /** Edad didáctica del perfil infantil activo (la que fija el backend o, si falta, la de su fecha de nacimiento). */
    private fun activeAgeGroup(): AgeGroup? =
        child?.takeIf { it.id == childId }?.let { AgeGroup.fromCode(it.ageGroup) ?: AgeGroup.fromAge(it.ageYears()) }

    private fun originalTextOf(source: AdaptationSource): String? =
        if (source.type == SourceType.PDF) null
        else String(source.bytes, Charsets.UTF_8).takeIf { it.length <= MAX_ORIGINAL_CHARS }

    /** Pide una lección por categoría/tema al servidor; el resultado se muestra igual que una adaptación. */
    private fun generate(s: NewReadingUiState) {
        val category = s.category ?: return
        viewModelScope.launch {
            _state.update { it.copy(isAdapting = true, errorMessage = null, result = null) }
            generateLesson(LessonRequest(category, s.topic, s.level, activeAgeGroup()), childId)
                .onSuccess { result -> _state.update { it.copy(isAdapting = false, result = result) } }
                .onFailure { e -> _state.update { it.copy(isAdapting = false, errorMessage = e.toAppError().userMessage) } }
        }
    }

    fun refreshPin() = _state.update { it.copy(pinConfigured = isPinConfigured()) }

    /** Deja la lectura lista en RAM para más tarde (vuelve al panel). */
    fun saveForLater() = useReading(NewReadingOutcome.SAVED)

    /** Deja la lectura lista y entra directo al lector (Modo Enfoque si es para un niño). */
    fun startNow() = useReading(NewReadingOutcome.START_NOW)

    /** Deja la lectura adaptada en RAM, con las preferencias visuales del perfil. */
    private fun useReading(outcome: NewReadingOutcome) {
        val result = _state.value.result ?: return
        val settings = child?.toReadingSettings() ?: ReadingSettings()
        val age = child?.let { java.time.Period.between(it.birthDate, java.time.LocalDate.now()).years }
        holder.set(
            ActiveReading(
                childId = childId,
                readerName = _state.value.readerName.ifBlank { "Lector" },
                settings = settings,
                adaptation = result,
                ageYears = age,
                ownerKey = childId ?: "adult:${sessionManager.email.orEmpty()}"
            )
        )
        pendingFile = null
        // La lectura ya está en RAM (ActiveReadingHolder): el formulario queda limpio para la siguiente.
        _state.update { it.copy(text = "", fileName = null, topic = "", result = null) }
        _done.trySend(outcome)
    }

    private companion object {
        /** El original más largo que esto no se retiene (la comparación se desactiva). */
        const val MAX_ORIGINAL_CHARS = 100_000
    }
}
