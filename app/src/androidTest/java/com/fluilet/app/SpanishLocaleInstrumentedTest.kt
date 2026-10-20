package com.fluilet.app

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * Con el dispositivo en inglés, los recursos de la Activity (incluidos los de Material 3, como el
 * selector de fecha) deben resolverse en español.
 */
@RunWith(AndroidJUnit4::class)
class SpanishLocaleInstrumentedTest {

    @Test fun activityResourcesAreSpanishEvenWhenTheSystemIsEnglish() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals("es", activity.resources.configuration.locales[0].language)
                assertEquals("es", Locale.getDefault().language)

                val id = activity.resources.getIdentifier("m3c_date_picker_title", "string", activity.packageName)
                assertTrue("recurso de Material 3 no encontrado", id != 0)
                val title = activity.getString(id)
                assertFalse("el selector de fecha sigue en inglés: $title", title.equals("Select date", ignoreCase = true))
                assertTrue("título inesperado: $title", title.contains("fecha", ignoreCase = true))
            }
        }
    }
}
