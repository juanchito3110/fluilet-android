package com.fluilet.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class AuthResponseDto(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String? = null,
    @SerializedName("token_type") val tokenType: String? = null
)