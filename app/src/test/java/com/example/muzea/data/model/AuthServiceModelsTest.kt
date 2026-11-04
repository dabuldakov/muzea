package com.example.muzea.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@Suppress("UNUSED_EXPRESSION")
class AuthServiceModelsTest {

    private fun <T> assertDataClassContract(first: T, second: T, different: T) {
        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertNotEquals(first, different)
        assertFalse(first == null)
        assertTrue(first.toString().isNotEmpty())
    }

    @Test
    fun `register request data class contract`() {
        val first = AuthServiceRegisterRequest("a@b.c", "user", "Display")
        val different = first.copy(displayName = null)

        assertDataClassContract(first, AuthServiceRegisterRequest("a@b.c", "user", "Display"), different)
        first.component1(); first.component2(); first.component3()
    }

    @Test
    fun `verify email request data class contract`() {
        val first = AuthServiceVerifyEmailRequest("a@b.c", "1234", "device")
        val different = first.copy(deviceId = null)

        assertDataClassContract(first, AuthServiceVerifyEmailRequest("a@b.c", "1234", "device"), different)
        first.component1(); first.component2(); first.component3()
    }

    @Test
    fun `otp request data class contract`() {
        val first = AuthServiceOtpRequest("a@b.c")
        val different = first.copy(email = "x@y.z")

        assertDataClassContract(first, AuthServiceOtpRequest("a@b.c"), different)
        first.component1()
    }

    @Test
    fun `otp verify request data class contract`() {
        val first = AuthServiceOtpVerifyRequest("a@b.c", "1234", "device")
        val different = first.copy(code = "0000")

        assertDataClassContract(first, AuthServiceOtpVerifyRequest("a@b.c", "1234", "device"), different)
        first.component1(); first.component2(); first.component3()
    }

    @Test
    fun `refresh request data class contract`() {
        val first = AuthServiceRefreshRequest("refresh", "device")
        val different = first.copy(deviceId = null)

        assertDataClassContract(first, AuthServiceRefreshRequest("refresh", "device"), different)
        first.component1(); first.component2()
    }

    @Test
    fun `logout request data class contract`() {
        val first = AuthServiceLogoutRequest("refresh")
        val different = first.copy(refreshToken = "other")

        assertDataClassContract(first, AuthServiceLogoutRequest("refresh"), different)
        first.component1()
    }

    @Test
    fun `user data class contract`() {
        val first = AuthServiceUser("id", "a@b.c", "user", "Display", true)
        val different = first.copy(emailVerified = false)

        assertDataClassContract(first, AuthServiceUser("id", "a@b.c", "user", "Display", true), different)
        first.component1(); first.component2(); first.component3(); first.component4(); first.component5()
    }

    @Test
    fun `token response data class contract`() {
        val user = AuthServiceUser("id", "a@b.c", "user", "Display", true)
        val first = AuthServiceTokenResponse("access", "refresh", "Bearer", 3600L, user)
        val different = first.copy(expiresIn = 0L)

        assertDataClassContract(first, AuthServiceTokenResponse("access", "refresh", "Bearer", 3600L, user), different)
        first.component1(); first.component2(); first.component3(); first.component4(); first.component5()
    }

    @Test
    fun `message data class contract`() {
        val first = AuthServiceMessage("hello")
        val different = first.copy(message = null)

        assertDataClassContract(first, AuthServiceMessage("hello"), different)
        first.component1()
    }

    @Test
    fun `error data class contract`() {
        val first = AuthServiceError("CODE", "message")
        val different = first.copy(code = "OTHER")

        assertDataClassContract(first, AuthServiceError("CODE", "message"), different)
        first.component1(); first.component2()
    }
}
