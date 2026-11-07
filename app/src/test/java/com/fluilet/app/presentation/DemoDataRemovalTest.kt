package com.fluilet.app.presentation

import com.fluilet.app.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Cargar Datos Demo" desapareció de Inicio (niño o adulto) y del Panel del Tutor: ni su tarjeta ni sus textos existen
 * en la app. Solo el Panel de Administración Maestro conserva "Restaurar cuentas demo".
 */
class DemoDataRemovalTest {

    private val strings = R.string::class.java.fields.map { it.name }.toSet()

    @Test fun theDemoCardAndItsButtonNoLongerExist() {
        listOf("demo_hint", "demo_load", "demo_loading").forEach { name ->
            assertFalse("el texto $name ya no debe existir", name in strings)
        }
        val demoCard = runCatching { Class.forName("com.fluilet.app.presentation.home.DemoSectionKt") }.getOrNull()
        assertTrue("la tarjeta DemoDataCard se eliminó", demoCard == null)
    }

    @Test fun onlyTheMasterAdminPanelCanRestoreTheDemoAccounts() {
        assertTrue("admin_restore" in strings)
        val admin = Class.forName("com.fluilet.app.presentation.admin.AdminViewModel").methods.map { it.name }
        assertTrue(admin.contains("restoreDemoAccounts"))
    }
}
