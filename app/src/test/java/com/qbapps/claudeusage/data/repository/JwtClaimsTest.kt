package com.qbapps.claudeusage.data.repository

import java.time.Instant
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JwtClaimsTest {
    @Test
    fun `reads nested account id from access token`() {
        val token = jwt(
            """{"exp":2000,"https://api.openai.com/auth":{"chatgpt_account_id":"account-123"}}"""
        )

        assertEquals("account-123", JwtClaims.accountId(null, token))
    }

    @Test
    fun `detects token inside refresh window`() {
        val now = Instant.ofEpochSecond(1_000L)

        assertTrue(JwtClaims.isExpiringSoon(jwt("""{"exp":1200}"""), now))
        assertFalse(JwtClaims.isExpiringSoon(jwt("""{"exp":2000}"""), now))
    }

    private fun jwt(payload: String): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        return listOf("{}", payload, "signature")
            .joinToString(".") { encoder.encodeToString(it.toByteArray()) }
    }
}
