import java.io.StringReader
import java.net.URI
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.dagger.hilt)
    alias(libs.plugins.ksp)
}

// Configuración de red por entorno.
//
// URL del backend: UN solo parámetro apunta el APK a otro servidor (p. ej. un Droplet de DigitalOcean), sin editar Kotlin:
//   ./gradlew assembleDebug -PFLUILET_API_BASE_URL=http://203.0.113.10:8000     (IP directa, HTTP)
//   ./gradlew assembleDebug -PFLUILET_API_BASE_URL=https://api.midominio.com     (dominio con TLS)
//   FLUILET_API_BASE_URL=https://api.midominio.com ./gradlew assembleRelease
// Prioridad: -P > gradle.properties > variable de entorno > fluilet.apiBaseUrl (nombre anterior) > valor por defecto.
// gradle.properties apunta debug y release al Droplet de producción (http://67.205.150.186/); sin esa línea, debug
// usaría http://10.0.2.2:8000/ (backend local visto desde el emulador) y release el placeholder de abajo.
// Con una URL http:// fuera del emulador se usa network_security_config_http.xml, que permite HTTP en claro SOLO
// hacia los hosts que declara; si el host no está en esa lista, la compilación se detiene con un aviso.
//
// Release con https (DigitalOcean + certificado de una CA pública, el caso estándar para Google Play) funciona SIN pines:
// confía en las autoridades certificadoras del sistema Android (network_security_config.xml) con TLS 1.3. El certificate
// pinning es opcional y solo se activa al pasar pines SPKI explícitos (mínimo 2: activo + respaldo):
//   ./gradlew bundleRelease -PFLUILET_API_BASE_URL=https://api.midominio.com -Pfluilet.certPins=sha256/AAAA...=,sha256/BBBB...=
//   (o la variable de entorno FLUILET_CERT_PINS). Un pin mal escrito detiene la compilación en lugar de romper la app.
data class ApiEndpoint(val baseUrl: String, val host: String, val cleartext: Boolean)

fun apiEndpoint(raw: String): ApiEndpoint {
    val url = raw.trim().let { if (it.endsWith("/")) it else "$it/" }
    val uri = URI(url)
    require(uri.scheme == "http" || uri.scheme == "https") { "FLUILET_API_BASE_URL debe empezar por http:// o https:// (recibido: $raw)" }
    val host = requireNotNull(uri.host) { "FLUILET_API_BASE_URL no tiene un host válido (recibido: $raw)" }
    return ApiEndpoint(url, host, uri.scheme == "http")
}

val apiBaseUrlOverride: String? = providers.gradleProperty("FLUILET_API_BASE_URL")
    .orElse(providers.environmentVariable("FLUILET_API_BASE_URL"))
    .orElse(providers.gradleProperty("fluilet.apiBaseUrl"))
    .orNull?.trim()?.takeIf { it.isNotEmpty() }
val debugApi = apiEndpoint(apiBaseUrlOverride ?: "http://10.0.2.2:8000/")
val releaseApi = apiEndpoint(apiBaseUrlOverride ?: "https://api.fluilet.example.com/")
/** Pines SPKI opcionales: vacío = sin pinning (CAs del sistema). Cada pin debe ser `sha256/` + 44 caracteres base64. */
fun certPins(raw: String): String {
    val pins = raw.split(',').map(String::trim).filter(String::isNotEmpty)
    pins.forEach { pin ->
        require(Regex("^sha256/[A-Za-z0-9+/]{43}=$").matches(pin)) { "fluilet.certPins: pin inválido \"$pin\" (formato sha256/<44 base64>)" }
    }
    require(pins.size != 1) { "fluilet.certPins: usa al menos 2 pines (certificado activo + respaldo) para poder rotar la llave" }
    return pins.joinToString(",")
}

val releaseCertPins = certPins(
    providers.gradleProperty("fluilet.certPins").orElse(providers.environmentVariable("FLUILET_CERT_PINS")).orElse("").get()
)

// Firma de release (APK y .aab para Google Play). El keystore y sus contraseñas viven FUERA de los repositorios, en
// ../release-keys/keystore.properties (storeFile, storePassword, keyAlias, keyPassword; storeFile relativo a ese
// archivo). Otra ubicación: -Pfluilet.keystoreProperties=/ruta/keystore.properties o FLUILET_KEYSTORE_PROPERTIES.
// Sin ese archivo, release se compila sin firmar y Gradle lo avisa.
val keystorePropertiesFile = rootProject.layout.projectDirectory.file(
    providers.gradleProperty("fluilet.keystoreProperties")
        .orElse(providers.environmentVariable("FLUILET_KEYSTORE_PROPERTIES"))
        .getOrElse("../release-keys/keystore.properties")
)
val releaseSigning: Properties? = providers.fileContents(keystorePropertiesFile).asText.orNull
    ?.let { text -> Properties().apply { load(StringReader(text)) } }
    ?.also { props ->
        listOf("storeFile", "storePassword", "keyAlias", "keyPassword").forEach { key ->
            require(!props.getProperty(key).isNullOrBlank()) { "Falta '$key' en ${keystorePropertiesFile.asFile}" }
        }
    }
if (releaseSigning == null) {
    logger.warn("Fluilet: no se encontró ${keystorePropertiesFile.asFile}; las variantes release saldrán SIN firmar.")
}

// Hosts con HTTP que ya admite la configuración de red de debug (emulador y `adb reverse`).
val debugCleartextHosts = setOf("10.0.2.2", "127.0.0.1", "localhost")

// Hosts a los que network_security_config_http.xml permite HTTP en claro (se leen del propio XML).
val httpConfigHosts: Set<String> = Regex("<domain[^>]*>\\s*([^<\\s]+)\\s*</domain>")
    .findAll(providers.fileContents(layout.projectDirectory.file("src/main/res/xml/network_security_config_http.xml")).asText.get())
    .map { it.groupValues[1] }.toSet()

/** Configuración de red del manifiesto: la estricta salvo que el backend elegido sea HTTP fuera de esos hosts. */
fun networkSecurityConfig(api: ApiEndpoint, debug: Boolean): String =
    if (api.cleartext && !(debug && api.host in debugCleartextHosts)) {
        require(api.host in httpConfigHosts) {
            "El backend http://${api.host} no está permitido en src/main/res/xml/network_security_config_http.xml " +
                "(hosts con HTTP: $httpConfigHosts). Añádelo ahí o usa https://."
        }
        "@xml/network_security_config_http"
    } else "@xml/network_security_config"

android {
    namespace = "com.fluilet.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.fluilet.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        releaseSigning?.let { props ->
            create("release") {
                storeFile = keystorePropertiesFile.asFile.parentFile.resolve(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Por defecto: emulador Android -> backend local (uvicorn en el host). Ver FLUILET_API_BASE_URL arriba.
            buildConfigField("String", "API_BASE_URL", "\"${debugApi.baseUrl}\"")
            buildConfigField("String", "API_HOST", "\"${debugApi.host}\"")
            buildConfigField("String", "CERT_PINS", "\"\"")
            buildConfigField("boolean", "ENFORCE_TLS_ONLY", "false")
            manifestPlaceholders["networkSecurityConfig"] = networkSecurityConfig(debugApi, debug = true)
            // Adaptación y telemetría contra el backend real. false = modo offline explícito (solo desarrollo).
            buildConfigField("boolean", "USE_REMOTE_ADAPTATION", "true")
        }
        release {
            // R8 (minify + ofuscación + optimización) con las reglas por defecto y las propias de la app:
            // proguard-rules.pro protege los DTO de red (Gson) y las interfaces de Retrofit.
            optimization {
                enable = true
            }
            proguardFiles("proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
            buildConfigField("String", "API_BASE_URL", "\"${releaseApi.baseUrl}\"")
            buildConfigField("String", "API_HOST", "\"${releaseApi.host}\"")
            buildConfigField("String", "CERT_PINS", "\"$releaseCertPins\"")
            // Sin pines: CAs del sistema. Solo TLS 1.3; una URL http:// (IP de prueba sin dominio) desactiva la restricción.
            buildConfigField("boolean", "ENFORCE_TLS_ONLY", "${!releaseApi.cleartext}")
            manifestPlaceholders["networkSecurityConfig"] = networkSecurityConfig(releaseApi, debug = false)
            buildConfigField("boolean", "USE_REMOTE_ADAPTATION", "true")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    // Solo recursos en español (más los de idioma por defecto): descarta traducciones de librerías que la app nunca muestra.
    androidResources {
        localeFilters += listOf("es")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.coroutines.android)
    // Seguridad (OWASP MSTG): almacenamiento cifrado y TLS 1.3 en Android 8-9 (API 26-28).
    implementation(libs.androidx.security.crypto)
    implementation(libs.conscrypt.android)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.okhttp.mockwebserver)
    // Certificados de prueba (HeldCertificate) para comprobar el cliente HTTPS de release contra un servidor TLS real.
    testImplementation(libs.okhttp.tls)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
