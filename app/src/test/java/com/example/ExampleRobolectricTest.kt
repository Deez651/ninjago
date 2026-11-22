package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.security.SecurityUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Ninjago Hub", appName)
    }

    @Test
    fun `vault encryption roundtrip succeeds with matching client hash`() {
        val clientHash = SecurityUtils.sha256Hex("sample_salt:secret")
        val vaultId = SecurityUtils.generateSecureHex(32)
        val encrypted = SecurityUtils.encryptVaultId(vaultId, clientHash)
        val decrypted = SecurityUtils.decryptVaultId(encrypted, clientHash)
        assertEquals(vaultId, decrypted)
    }

    @Test
    fun `safe url validator blocks unsafe schemes and loopback`() {
        assertTrue(SecurityUtils.isSafeHttpsUrl("https://cdn.example.com/file.zip"))
        assertFalse(SecurityUtils.isSafeHttpsUrl("http://cdn.example.com/file.zip"))
        assertFalse(SecurityUtils.isSafeHttpsUrl("https://127.0.0.1/file.zip"))
        assertFalse(SecurityUtils.isSafeHttpsUrl("javascript:alert(1)"))
    }
}
