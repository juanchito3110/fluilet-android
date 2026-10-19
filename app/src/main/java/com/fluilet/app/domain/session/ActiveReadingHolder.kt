package com.fluilet.app.domain.session

import com.fluilet.app.domain.model.ActiveReading
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cero retención local: la lectura adaptada vive ÚNICAMENTE en RAM, dentro de este contenedor,
 * mientras dura la sesión. No hay Room, archivos ni SharedPreferences con texto.
 */
@Singleton
class ActiveReadingHolder @Inject constructor() {
    private val _current = MutableStateFlow<ActiveReading?>(null)
    val current: StateFlow<ActiveReading?> = _current.asStateFlow()

    fun set(reading: ActiveReading) {
        _current.value = reading
    }

    fun clear() {
        _current.value = null
    }
}
