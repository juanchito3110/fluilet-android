package com.fluilet.app.domain

import com.fluilet.app.domain.model.AdminOverview
import com.fluilet.app.domain.model.AdminUser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
