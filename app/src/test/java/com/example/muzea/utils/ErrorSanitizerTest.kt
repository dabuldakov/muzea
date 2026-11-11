package com.example.muzea.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorSanitizerTest {

    @Test
    fun `email is masked`() {
        val out = ErrorSanitizer.sanitize("user dabuldakov@mail.ru failed")
        assertEquals("user [email] failed", out)
    }

    @Test
    fun `phone numbers are masked`() {
        assertFalse(ErrorSanitizer.sanitize("call +7 (912) 345-67-89").contains("345"))
        assertFalse(ErrorSanitizer.sanitize("call 89123456789").contains("8912"))
    }

    @Test
    fun `tokens are masked`() {
        val jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.abcdefghijklmnopqrstuv"
        val out = ErrorSanitizer.sanitize("Authorization: Bearer $jwt")
        assertFalse(out.contains(jwt))
        assertFalse(out.contains("eyJhbGci"))
    }

    @Test
    fun `key value secrets are hidden`() {
        val out = ErrorSanitizer.sanitize("token=abc123 password: hunter2")
        assertTrue(out.contains("token=[hidden]"))
        assertTrue(out.contains("password=[hidden]"))
    }

    @Test
    fun `plain stack text stays readable`() {
        val text = "java.io.IOException: HTTP 500 for attachment 1a2b"
        assertEquals(text, ErrorSanitizer.sanitize(text))
    }
}
