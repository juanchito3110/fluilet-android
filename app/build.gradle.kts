import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.dagger.hilt)
    alias(libs.plugins.ksp)
}

// Configuración de red por entorno. Se puede sobreescribir desde gradle.properties o -P:
//   fluilet.apiBaseUrl=https://api.midominio.com/
//   fluilet.certPins=sha256/AAAA...=,sha256/BBBB...=
// Los valores por defecto de release son PLACEHOLDERS: con ellos el pinning falla cerrado
// (ninguna conexión TLS de release prosperará hasta reemplazarlos por los hashes SPKI reales).
val releaseApiBaseUrl = providers.gradleProperty("fluilet.apiBaseUrl")
    .orElse("https://api.fluilet.example.com/").get()
val releaseApiHost = URI(releaseApiBaseUrl).host
val releaseCertPins = providers.gradleProperty("fluilet.certPins")
    .orElse(
        "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=," + // TODO: pin SPKI del certificado hoja (DigitalOcean)
            "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB="  // TODO: pin SPKI de respaldo (intermedio / siguiente llave)
    ).get()

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

    buildTypes {
        debug {
            // Emulador Android -> backend local (uvicorn en el host).
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8000/\"")
            buildConfigField("String", "API_HOST", "\"10.0.2.2\"")
            buildConfigField("String", "CERT_PINS", "\"\"")
            buildConfigField("boolean", "ENFORCE_TLS_ONLY", "false")
            // Adaptación y telemetría contra el backend real. false = modo offline explícito (solo desarrollo).
            buildConfigField("boolean", "USE_REMOTE_ADAPTATION", "true")
        }
        release {
            optimization {
                enable = true
            }
            buildConfigField("String", "API_BASE_URL", "\"$releaseApiBaseUrl\"")
            buildConfigField("String", "API_HOST", "\"$releaseApiHost\"")
            buildConfigField("String", "CERT_PINS", "\"$releaseCertPins\"")
            buildConfigField("boolean", "ENFORCE_TLS_ONLY", "true")
            buildConfigField("boolean", "USE_REMOTE_ADAPTATION", "true")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
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
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
