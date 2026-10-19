package com.fluilet.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class ChildDto(
    @SerializedName("id") val id: String,
    @SerializedName("tutor_id") val tutorId: String? = null,
    @SerializedName("name") val name: String,
    @SerializedName("birth_date") val birthDate: String,
    @SerializedName("tipo_dificultad") val difficulty: String?,
    @SerializedName("nivel_simplificacion_nlp") val nlpLevel: Int?,
    @SerializedName("paleta_visual") val palette: String?,
    @SerializedName("tamano_fuente") val fontSize: Int?,
    @SerializedName("interlineado") val lineSpacing: Double?,
    @SerializedName("is_active") val isActive: Boolean = true,
    @SerializedName("created_at") val createdAt: String? = null
)

data class ChildCreateDto(
    @SerializedName("name") val name: String,
    @SerializedName("birth_date") val birthDate: String,
    @SerializedName("tipo_dificultad") val difficulty: String,
    @SerializedName("nivel_simplificacion_nlp") val nlpLevel: Int,
    @SerializedName("paleta_visual") val palette: String,
    @SerializedName("tamano_fuente") val fontSize: Int,
    @SerializedName("interlineado") val lineSpacing: Double
)

/** PATCH: los campos `null` se omiten (Gson no serializa nulls) y el backend no los modifica. */
data class ChildUpdateDto(
    @SerializedName("name") val name: String? = null,
    @SerializedName("tipo_dificultad") val difficulty: String? = null,
    @SerializedName("nivel_simplificacion_nlp") val nlpLevel: Int? = null,
    @SerializedName("paleta_visual") val palette: String? = null,
    @SerializedName("tamano_fuente") val fontSize: Int? = null,
    @SerializedName("interlineado") val lineSpacing: Double? = null
)
