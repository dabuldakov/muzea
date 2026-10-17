package com.example.muzea.data.model

import com.google.gson.annotations.SerializedName

/** Модели единого IdP (auth-service, auth.muzea.su). */

data class AuthServiceRegisterRequest(
    @SerializedName("email") val email: String,
    @SerializedName("username") val username: String,
    @SerializedName("displayName") val displayName: String? = null
)

data class AuthServiceVerifyEmailRequest(
    @SerializedName("email") val email: String,
    @SerializedName("code") val code: String,
    @SerializedName("deviceId") val deviceId: String? = null
)

data class AuthServiceOtpRequest(
    @SerializedName("email") val email: String
)

data class AuthServiceOtpVerifyRequest(
    @SerializedName("email") val email: String,
    @SerializedName("code") val code: String,
    @SerializedName("deviceId") val deviceId: String? = null
)

data class AuthServiceRefreshRequest(
    @SerializedName("refreshToken") val refreshToken: String,
    @SerializedName("deviceId") val deviceId: String? = null
)

data class AuthServiceLogoutRequest(
    @SerializedName("refreshToken") val refreshToken: String
)

data class AuthServiceUser(
    @SerializedName("id") val id: String?,
    @SerializedName("email") val email: String?,
    @SerializedName("username") val username: String?,
    @SerializedName("displayName") val displayName: String?,
    @SerializedName("emailVerified") val emailVerified: Boolean = false
)

data class AuthServiceTokenResponse(
    @SerializedName("accessToken") val accessToken: String,
    @SerializedName("refreshToken") val refreshToken: String,
    @SerializedName("tokenType") val tokenType: String?,
    @SerializedName("expiresIn") val expiresIn: Long = 0,
    @SerializedName("user") val user: AuthServiceUser?
)

data class AuthServiceMessage(
    @SerializedName("message") val message: String?
)

data class AuthServiceError(
    @SerializedName("code") val code: String?,
    @SerializedName("message") val message: String?
)
