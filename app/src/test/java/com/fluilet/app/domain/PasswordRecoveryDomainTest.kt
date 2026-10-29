package com.fluilet.app.domain

import com.fluilet.app.domain.validation.PasswordRequirement
import com.fluilet.app.domain.validation.ResetCode
import com.fluilet.app.domain.validation.Validators
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Código de 6 dígitos (tecleado o pegado) y reglas de la contraseña nueva, espejo del backend. */
class PasswordRecoveryDomainTest {

    @Test fun aValidCodeIsExactlySixAsciiDigitsIncludingLeadingZeros() {
        assertTrue(ResetCode.isValid("000123"))
        assertTrue(ResetCode.isValid("048213"))
        assertFalse(ResetCode.isValid("48213"))
        assertFalse(ResetCode.isValid("0482130"))
        assertFalse(ResetCode.isValid("04821a"))
        assertFalse("dígitos no ASCII", ResetCode.isValid("٠١٢٣٤٥"))
        assertNull(Validators.resetCode("004821"))
        assertNotNull(Validators.resetCode("4821"))
    }

    @Test fun theCodeIsFoundInsideTextCopiedFromTheEmail() {
        assertEquals("048213", ResetCode.find("048213"))
        assertEquals("048213", ResetCode.find("Tu código de Fluilet es 048213. Vence en 10 minutos."))
        assertEquals("048213", ResetCode.find("Código: 048 213"))
        assertEquals("048213", ResetCode.find("0-4-8-2-1-3"))
        assertEquals("048213", ResetCode.find("\n  048213  \n"))
        assertNull(ResetCode.find("https://fluilet.com/ayuda"))
        assertNull("un teléfono no es un código", ResetCode.find("Llámanos al 300 123 4567"))
        assertNull("siete dígitos seguidos tampoco", ResetCode.find("1234567"))
        assertNull(ResetCode.find(""))
    }

    @Test fun mergingKeyboardChangesHandlesTypingDeletingAndPasting() {
        assertEquals("0", ResetCode.merge("", "0"))
        assertEquals("04", ResetCode.merge("0", "04"))
        assertEquals("borrar", "0", ResetCode.merge("04", "0"))
        assertEquals("letras fuera", "04", ResetCode.merge("0", "04x"))
        assertEquals("nunca más de 6", "123456", ResetCode.merge("123456", "1234567"))
        assertEquals("pegar sobre casillas vacías", "654321", ResetCode.merge("", "654321"))
        assertEquals("pegar al final de casillas escritas", "654321", ResetCode.merge("12", "12654321"))
        assertEquals("pegar en medio", "654321", ResetCode.merge("12", "1654 3212"))
        assertEquals("pegar el texto del correo", "048213", ResetCode.merge("", "Tu código es 048213"))
        assertEquals("seleccionar todo y escribir", "7", ResetCode.merge("123456", "7"))
    }

    @Test fun passwordRequirementsMirrorTheBackendRules() {
        val good = "UnaClaveNueva2026!"
        assertTrue(PasswordRequirement.entries.all { it.isMet(good) })
        assertEquals(1f, PasswordRequirement.progress(good))
        assertNull(Validators.strongPassword(good))

        assertFalse(PasswordRequirement.LENGTH.isMet("Corta1!"))
        assertFalse("[A-Z] del backend es ASCII", PasswordRequirement.UPPERCASE.isMet("ñandú#2026lectura"))
        assertNotNull(Validators.strongPassword("Ñandúlectura#2026"))
        assertFalse(PasswordRequirement.LOWERCASE.isMet("SOLOMAYUSCULAS1!"))
        assertFalse(PasswordRequirement.DIGIT.isMet("SinNumeros!!!x"))
        assertFalse("el guion bajo no es símbolo", PasswordRequirement.SYMBOL.isMet("Sin_Simbolo2026"))
        assertFalse("ningún espacio, tampoco tabuladores", PasswordRequirement.NO_SPACES.isMet("Clave\tNueva2026!"))
        assertNotNull(Validators.strongPassword("Clave\tNueva2026!"))
        assertEquals(0f, PasswordRequirement.progress(""))
    }

    @Test fun seventyTwoCharactersCanStillExceedSeventyTwoBytes() {
        val accented = "Ááá#2026" + "é".repeat(40) // 48 caracteres, 92 bytes UTF-8
        assertTrue(accented.length in 10..72)
        assertFalse(PasswordRequirement.LENGTH.isMet(accented))
        assertTrue(Validators.strongPassword(accented)!!.contains("72 bytes"))
    }
}
