# Reglas R8 de Fluilet para compilaciones release (.apk / .aab de Google Play).
# Las reglas por defecto de Android (proguard-android-optimize) y las que traen Retrofit, OkHttp, Gson, Hilt y
# Compose se aplican además de estas.

# --- Atributos que Gson y Retrofit leen por reflexión -----------------------------------------------------------
# Signature: tipos genéricos (List<LevelPreviewDto>, Map<...>) y los Call<T> / suspend de Retrofit.
# *Annotation*: @SerializedName y las anotaciones HTTP de Retrofit (@POST, @Body, @Field...).
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault, *Annotation*

# --- DTO de red (Gson) -------------------------------------------------------------------------------------------
# Todos los DTO viven en data.remote.dto: auth (login, refresh, registro y recuperación de contraseña:
# forgot-password, verify-reset-code, reset-password), adaptaciones con level_previews y lexical_replacements,
# model-info, admin/overview, niños, progreso y lecturas. Se conservan clase, campos y constructores con su nombre:
# R8 no puede ver que Gson los crea y rellena por reflexión, y en modo completo los eliminaría o vaciaría.
-keep class com.fluilet.app.data.remote.dto.** { *; }
-keepclassmembers enum com.fluilet.app.data.remote.dto.** { *; }

# Cualquier otro campo anotado con @SerializedName (por si un DTO se declara fuera del paquete).
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# TypeToken de Gson con genéricos.
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

# --- Retrofit ----------------------------------------------------------------------------------------------------
# Las interfaces de la API se implementan con un proxy dinámico: se conservan con sus métodos anotados.
-keep interface com.fluilet.app.data.remote.AuthApi { *; }
-keep interface com.fluilet.app.data.remote.FluiletApi { *; }
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# --- TLS ---------------------------------------------------------------------------------------------------------
# Conscrypt (TLS 1.3 en Android 8-9) y los proveedores opcionales que OkHttp detecta en tiempo de ejecución.
-keep class org.conscrypt.** { *; }
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
