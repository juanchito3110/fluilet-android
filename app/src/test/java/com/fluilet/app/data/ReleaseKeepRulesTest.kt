package com.fluilet.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Blindaje R8 de la compilación release (.aab de Google Play): los DTO que Gson crea por reflexión deben sobrevivir a
 * la ofuscación y la reducción. Las reglas protegen el paquete `data.remote.dto` completo, así que todo DTO debe vivir ahí.
 */
class ReleaseKeepRulesTest {

    /** Las pruebas unitarias corren desde el directorio del módulo `app/`. */
    private val module: File = listOf(File("."), File("app")).first { File(it, "proguard-rules.pro").exists() }
    private val rules = File(module, "proguard-rules.pro").readText()
    private val sources = File(module, "src/main/java")

    @Test fun releaseBuildsUseTheAppKeepRules() {
        val gradle = File(module, "build.gradle.kts").readText()
        val release = gradle.substringAfter("release {").substringBefore("\n        }")
        assertTrue("R8 activo en release", release.contains("enable = true"))
        assertTrue("proguard-rules.pro registrado en release", release.contains("proguardFiles(\"proguard-rules.pro\")"))
    }

    @Test fun everyNetworkDtoIsKeptByName() {
        assertTrue(rules.contains("-keep class com.fluilet.app.data.remote.dto.** { *; }"))
        assertTrue("genéricos de List<...Dto>", rules.contains("Signature"))
        assertTrue(rules.contains("@com.google.gson.annotations.SerializedName <fields>"))
        listOf("AuthApi", "FluiletApi").forEach { api ->
            assertTrue("interfaz $api de Retrofit", rules.contains("-keep interface com.fluilet.app.data.remote.$api { *; }"))
        }
    }

    @Test fun allSerializedNameClassesLiveInTheProtectedDtoPackage() {
        val offenders = sources.walkTopDown()
            .filter { it.extension == "kt" && it.readText().contains("@SerializedName") }
            .filterNot { it.invariantSeparatorsPath.contains("com/fluilet/app/data/remote/dto/") }
            .map { it.name }
            .toList()
        assertEquals("DTO fuera de data/remote/dto: $offenders", emptyList<String>(), offenders)
    }

    @Test fun theDtosTheBackendContractNeedsAreInsideThatPackage() {
        listOf(
            "LevelPreviewDto", "LexicalReplacementDto", "ModelInfoDto", "AdminOverviewDto",
            "ForgotPasswordRequestDto", "VerifyResetCodeRequestDto", "ResetPasswordRequestDto",
            "AuthMessageDto", "ResetCodeVerificationDto", "TokenDto", "ErrorBodyDto"
        ).forEach { name ->
            val type = Class.forName("com.fluilet.app.data.remote.dto.$name")
            assertEquals(name, "com.fluilet.app.data.remote.dto", type.packageName)
        }
    }
}
