package com.example.data.security

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Base64
import java.net.InetAddress
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object SecurityUtils {
    const val SUPER_ADMIN_LOGIN = "deez"
    const val SUPER_ADMIN_SALT = "njg_deez_salt_2026"
    const val SUPER_ADMIN_VERIFIER_HASH =
        "1b5283b623def13ac124fd4fad07fab104a9537d0e445382baaa8c47c9a38600"

    private val secureRandom = SecureRandom()
    private val keyAlphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private val validIdRegex = Regex("^[a-zA-Z0-9_\\-]+$")

    fun sha256Hex(input: String): String =
        sha256BytesHex(input.toByteArray(Charsets.UTF_8))

    fun sha256BytesHex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun generateSecureHex(byteLength: Int = 16): String {
        val bytes = ByteArray(byteLength)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun generateAccessKeyCode(prefix: String = "NJG"): String {
        val cleanPrefix = prefix.uppercase().replace(Regex("[^A-Z0-9]"), "").take(6).ifEmpty { "NJG" }
        fun segment(len: Int): String = buildString(len) {
            repeat(len) {
                append(keyAlphabet[secureRandom.nextInt(keyAlphabet.length)])
            }
        }
        return "$cleanPrefix-${segment(4)}-${segment(4)}-${segment(4)}"
    }

    fun isValidFirestoreId(value: String): Boolean =
        value.length in 1..128 && validIdRegex.matches(value)

    fun isValidLoginFormat(login: String): Boolean =
        login.length in 2..32 && Regex("^[a-z0-9_\\-]+$").matches(login)

    fun encryptVaultId(vaultId: String, clientHashHex: String): String {
        val keyBytes = hexToBytes(clientHashHex).copyOf(32)
        val iv = ByteArray(12).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(vaultId.toByteArray(Charsets.UTF_8))
        val combined = iv + encrypted
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decryptVaultId(encoded: String, clientHashHex: String): String {
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        require(combined.size > 12) { "Некорректный зашифрованный ключ хранилища" }
        val iv = combined.copyOfRange(0, 12)
        val ciphertext = combined.copyOfRange(12, combined.size)
        val keyBytes = hexToBytes(clientHashHex).copyOf(32)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.trim()
        require(clean.length % 2 == 0) { "Invalid hex string" }
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    fun isSafeHttpsUrl(rawUrl: String): Boolean {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty() || trimmed.length > 1000) return false
        return try {
            val url = URL(trimmed)
            if (!url.protocol.equals("https", ignoreCase = true)) return false
            val host = url.host?.lowercase().orEmpty()
            if (host.isBlank() || host == "localhost" || host == "127.0.0.1" || host == "0.0.0.0") {
                return false
            }
            if (host.endsWith(".local") || host.endsWith(".internal")) return false
            val ipLiteral = Regex("^\\d+\\.\\d+\\.\\d+\\.\\d+$").matches(host)
            if (ipLiteral) {
                val addr = InetAddress.getByName(host)
                if (addr.isLoopbackAddress || addr.isSiteLocalAddress || addr.isLinkLocalAddress) {
                    return false
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun getDeviceLabel(): String {
        val brand = Build.MANUFACTURER.orEmpty().trim().replaceFirstChar { it.uppercase() }
        val model = Build.MODEL.orEmpty().trim()
        val combined = listOf(brand, model).filter { it.isNotBlank() }.distinct().joinToString(" ")
        return combined.ifBlank { "Android Device" }.take(80)
    }

    @SuppressLint("HardwareIds")
    fun getDeviceHash(context: Context): String {
        val androidId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull().orEmpty()
        val raw = "$androidId:${Build.MANUFACTURER}:${Build.MODEL}:${Build.DEVICE}"
        return sha256Hex(raw).take(24)
    }
}
