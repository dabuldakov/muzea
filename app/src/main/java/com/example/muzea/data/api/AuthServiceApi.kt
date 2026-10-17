package com.example.muzea.data.api

import com.example.muzea.data.model.AuthServiceLogoutRequest
import com.example.muzea.data.model.AuthServiceMessage
import com.example.muzea.data.model.AuthServiceOtpRequest
import com.example.muzea.data.model.AuthServiceOtpVerifyRequest
import com.example.muzea.data.model.AuthServiceRefreshRequest
import com.example.muzea.data.model.AuthServiceRegisterRequest
import com.example.muzea.data.model.AuthServiceTokenResponse
import com.example.muzea.data.model.AuthServiceUser
import com.example.muzea.data.model.AuthServiceVerifyEmailRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/** API единого IdP (auth-service, auth.muzea.su). */
interface AuthServiceApi {

    @POST("/auth/register")
    suspend fun register(@Body body: AuthServiceRegisterRequest): Response<AuthServiceMessage>

    @POST("/auth/verify-email")
    suspend fun verifyEmail(
        @Body body: AuthServiceVerifyEmailRequest
    ): Response<AuthServiceTokenResponse>

    @POST("/auth/resend-verification")
    suspend fun resendVerification(@Body body: AuthServiceOtpRequest): Response<AuthServiceMessage>

    @POST("/auth/otp/request")
    suspend fun requestLoginCode(@Body body: AuthServiceOtpRequest): Response<AuthServiceMessage>

    @POST("/auth/otp/verify")
    suspend fun verifyLoginCode(
        @Body body: AuthServiceOtpVerifyRequest
    ): Response<AuthServiceTokenResponse>

    @POST("/auth/token/refresh")
    suspend fun refresh(
        @Body body: AuthServiceRefreshRequest
    ): Response<AuthServiceTokenResponse>

    @POST("/auth/logout")
    suspend fun logout(@Body body: AuthServiceLogoutRequest): Response<AuthServiceMessage>

    @GET("/auth/me")
    suspend fun me(): Response<AuthServiceUser>

    /** Полное удаление аккаунта в IdP. */
    @retrofit2.http.DELETE("/auth/me")
    suspend fun deleteMe(): Response<AuthServiceMessage>
}
