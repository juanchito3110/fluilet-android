package com.fluilet.app.domain.model

data class AuthSession(
    val accessToken: String,
    val refreshToken: String?
)