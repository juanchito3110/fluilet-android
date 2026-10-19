package com.fluilet.app.domain

import com.fluilet.app.domain.validation.Validators
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ValidatorsTest {

    @Test fun strongPassword_matchesBackendPolicy() {
        assertNull(Validators.strongPassword("Lectura#2026x"))
        assertNotNull(Validators.strongPassword("corta1!A"))              // < 10
        assertNotNull(Validators.strongPassword("sinmayuscula1!"))
        assertNotNull(Validators.strongPassword("SINMINUSCULA1!"))
        assertNotNull(Validators.strongPassword("SinDigitoss!!"))
        assertNotNull(Validators.strongPassword("SinSimbolo1234"))
        assertNotNull(Validators.strongPassword("Con Espacio1!x"))
    }

    @Test fun email() {
        assertNull(Validators.email("tutor@fluilet.com"))
        assertNotNull(Validators.email("sin-arroba.com"))
    }

    @Test fun tutorPin() {
        assertNull(Validators.tutorPin("4821"))
        assertNull(Validators.tutorPin("937415"))
        assertNotNull(Validators.tutorPin("123"))       // corto
        assertNotNull(Validators.tutorPin("1234567"))   // largo
        assertNotNull(Validators.tutorPin("12a4"))      // no numérico
        assertNotNull(Validators.tutorPin("1111"))      // repetido
        assertNotNull(Validators.tutorPin("1234"))      // secuencia
        assertNotNull(Validators.tutorPin("4321"))      // secuencia
    }

    @Test fun childBirthDate_only6to11() {
        val today = LocalDate.of(2026, 6, 15)
        assertNull(Validators.childBirthDate(LocalDate.of(2018, 6, 15), today))   // 8 años
        assertNull(Validators.childBirthDate(LocalDate.of(2015, 6, 16), today))   // 10 años y 364 días
        assertNotNull(Validators.childBirthDate(LocalDate.of(2022, 1, 1), today)) // 4 años
        assertNotNull(Validators.childBirthDate(LocalDate.of(2010, 1, 1), today)) // 16 años
        assertNotNull(Validators.childBirthDate(LocalDate.of(2030, 1, 1), today)) // futura
    }
}
