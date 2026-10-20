package com.fluilet.app.core.locale

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

/**
 * La app es solo en español (es-MX, con respaldo a es). Se fuerza el idioma de la aplicación aunque
 * el sistema esté en otro idioma, para que los componentes de Material 3 (selector de fecha, textos
 * de accesibilidad, formato de fechas) también salgan en español. Los diálogos del propio sistema
 * operativo (p. ej. "App is pinned") siguen el idioma del dispositivo: no se pueden cambiar desde la app.
 */
object AppLocale {

    val SPANISH: Locale = Locale.forLanguageTag("es-MX")

    /** Devuelve un contexto cuya configuración usa español y fija también el `Locale` por defecto de la JVM. */
    fun wrap(base: Context): Context {
        Locale.setDefault(SPANISH)
        LocaleList.setDefault(LocaleList(SPANISH))
        val config = Configuration(base.resources.configuration).apply {
            setLocale(SPANISH)
            setLocales(LocaleList(SPANISH))
        }
        return base.createConfigurationContext(config)
    }

    fun isSpanish(locale: Locale): Boolean = locale.language == "es"
}
