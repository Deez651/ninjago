package com.example

import com.example.data.model.AccessKeyItem
import com.example.data.security.SecurityUtils
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class ExampleUnitTest {

    @Test
    fun `super admin verifier hash matches salted amountcare600`() {
        val clientHash = SecurityUtils.sha256Hex("${SecurityUtils.SUPER_ADMIN_SALT}:amountcare600")
        val verifier = SecurityUtils.sha256Hex(clientHash)
        assertEquals(SecurityUtils.SUPER_ADMIN_VERIFIER_HASH, verifier)
        assertFalse(SecurityUtils.sha256Hex("${SecurityUtils.SUPER_ADMIN_SALT}:wrong") == clientHash)
    }

    @Test
    fun `generated access key code matches firestore id rules`() {
        val code = SecurityUtils.generateAccessKeyCode()
        assertTrue(code.startsWith("NJG-"))
        assertTrue(SecurityUtils.isValidFirestoreId(code))
    }

    @Test
    fun `safe url validator blocks unsafe schemes and loopback`() {
        assertTrue(SecurityUtils.isSafeHttpsUrl("https://cdn.example.com/file.zip"))
        assertFalse(SecurityUtils.isSafeHttpsUrl("http://cdn.example.com/file.zip"))
        assertFalse(SecurityUtils.isSafeHttpsUrl("https://127.0.0.1/file.zip"))
        assertFalse(SecurityUtils.isSafeHttpsUrl("https://192.168.1.10/file.zip"))
        assertFalse(SecurityUtils.isSafeHttpsUrl("javascript:alert(1)"))
    }

    @Test
    fun `access key validity checks active limit and expiration`() {
        val future = Timestamp(Date(System.currentTimeMillis() + 60_000L))
        val past = Timestamp(Date(System.currentTimeMillis() - 60_000L))

        val valid = AccessKeyItem(code = "NJG-1111", maxActivations = 3, usedCount = 1, active = true, expiresAt = future)
        assertTrue(valid.isCurrentlyValid())

        val exhausted = valid.copy(usedCount = 3)
        assertFalse(exhausted.isCurrentlyValid())

        val expired = valid.copy(expiresAt = past)
        assertFalse(expired.isCurrentlyValid())

        val paused = valid.copy(active = false)
        assertFalse(paused.isCurrentlyValid())
    }
}
