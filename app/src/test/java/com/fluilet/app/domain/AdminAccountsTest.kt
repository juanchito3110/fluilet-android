package com.fluilet.app.domain

import com.fluilet.app.domain.model.AccountType
import com.fluilet.app.domain.model.AdminOverview
import com.fluilet.app.domain.model.AdminProfile
import com.fluilet.app.domain.model.AdminUser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminAccountsTest {
    @Test
    fun allRegisteredAccountsAreVisibleWithoutChildrenAndKeepTheirRealRole() {
        val users = listOf(
            AdminUser("master", "admin@example.com", "master_admin", true, true, 0),
            AdminUser("tutor", "tutor@example.com", "TUTOR", false, true, 0),
            AdminUser("adult", "adult@example.com", "ADULTO_INDEPENDIENTE", false, true, 0),
            AdminUser("child", "child@example.com", "NINO", false, true, 1),
            AdminUser("inactive", "inactive@example.com", "TUTOR", false, false, 0)
        )
        val overview = AdminOverview.EMPTY.copy(users = users)
        assertEquals(listOf("tutor", "adult", "child", "inactive"), overview.tutors.map { it.userId })
        assertEquals("ADULTO_INDEPENDIENTE", overview.tutors.first { it.userId == "adult" }.role)
        assertFalse(users.first().canBeDeleted)
    }

    private fun profile(id: String, owner: String, ownerEmail: String, name: String) =
        AdminProfile(id, name, owner, ownerEmail, true, "12+", 2, 30, 0, 1, "Núcleo", 0, 0, 0)

    @Test fun everyAccountGetsAClearTypeLabelFromItsRole() {
        assertEquals(AccountType.TUTOR, AccountType.of("TUTOR"))
        assertEquals(AccountType.INDEPENDENT_ADULT, AccountType.of("ADULTO_INDEPENDIENTE"))
        assertEquals(AccountType.INDEPENDENT_ADULT, AccountType.of("adulto_independiente"))
        assertEquals(AccountType.CHILD, AccountType.of("NINO"))
        assertEquals(AccountType.MASTER, AccountType.of("TUTOR", isMasterAdmin = true))
        assertEquals(AccountType.OTHER, AccountType.of("ROL_NUEVO"))
        assertEquals("Adulto independiente", AccountType.INDEPENDENT_ADULT.label)
        assertEquals("Tutor", AccountType.TUTOR.label)
        AccountType.entries.forEach { assertTrue(it.label.isNotBlank()) }
    }

    @Test fun aNewIndependentAdultIsListedWithItsFullEmailTypeAndReadingProfile() {
        val adult = AdminUser("adult-1", "axel.lector.independiente@example.com", "ADULTO_INDEPENDIENTE", false, true, 0)
        val newTutor = AdminUser("tutor-2", "nueva.tutora@example.com", "TUTOR", false, true, 0)
        val overview = AdminOverview.EMPTY.copy(
            users = listOf(adult, newTutor),
            profiles = listOf(profile("p-axel", "adult-1", adult.email, "Axel"))
        )
        // La cuenta nueva aparece aunque el servidor diga 0 perfiles; conserva el correo completo y su tipo.
        assertEquals(listOf("adult-1", "tutor-2"), overview.tutors.map { it.userId })
        assertEquals("axel.lector.independiente@example.com", overview.tutors.first().email)
        assertEquals(AccountType.INDEPENDENT_ADULT, overview.tutors.first().accountType)
        // Su perfil lector se encuentra para gestionarlo (gemas, nivel, etapa) desde su tarjeta.
        assertEquals(listOf("Axel"), overview.profilesOf("adult-1").map { it.name })
        assertEquals(adult, overview.ownerOf(overview.profiles.single()))
        // Un tutor recién creado, sin niños, también se ve y se puede administrar.
        assertTrue(overview.profilesOf("tutor-2").isEmpty())
        assertTrue(newTutor.canBeDeleted)
        assertNull(overview.ownerOf(profile("p-x", "desconocido", "x@example.com", "X")))
    }
}
