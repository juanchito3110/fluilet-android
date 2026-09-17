package com.fluilet.app.data.repository

import com.fluilet.app.data.remote.FluiletApi
import com.fluilet.app.domain.model.AuthSession
import com.fluilet.app.domain.repository.AuthRepository
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RetrofitAuthRepository @Inject constructor(
    private val api: FluiletApi
) : AuthRepository {

    override suspend fun login(username: String, password: String): Result<AuthSession> {
        return try {
            val response = api.login(username = username, password = password)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                Result.success(AuthSession(accessToken = body.accessToken, refreshToken = body.refreshToken))
            } else {
                Result.failure(HttpException(response))
            }
        } catch (e: HttpException) {
            Result.failure(e)
        } catch (e: IOException) {
            Result.failure(e)
        }
    }
}