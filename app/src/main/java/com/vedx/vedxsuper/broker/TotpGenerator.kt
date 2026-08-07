package com.vedx.vedxsuper.broker

import java.nio.ByteBuffer
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object TotpGenerator {
    /**
     * Generates a 6-digit TOTP code for the given Base32 secret.
     */
    fun generate(secret: String): String {
        val cleanSecret = secret.replace(" ", "").replace("-", "").uppercase().trimEnd('=')
        if (cleanSecret.isBlank()) return ""
        
        try {
            val key = decodeBase32(cleanSecret)
            // TOTP uses 30-second time steps by default (RFC 6238)
            val time = System.currentTimeMillis() / 1000 / 30
            
            // Encode the time as an 8-byte big-endian integer
            val data = ByteBuffer.allocate(8).putLong(time).array()
            
            val mac = Mac.getInstance("HmacSHA1")
            mac.init(SecretKeySpec(key, "HmacSHA1"))
            val hash = mac.doFinal(data)
            
            // Dynamic Truncation (RFC 4226)
            val offset = hash[hash.size - 1].toInt() and 0xf
            val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
                         ((hash[offset + 1].toInt() and 0xff) shl 16) or
                         ((hash[offset + 2].toInt() and 0xff) shl 8) or
                         (hash[offset + 3].toInt() and 0xff)
            
            val otp = binary % 1_000_000
            return String.format(Locale.US, "%06d", otp)
        } catch (e: Exception) {
            // Audit Fix 41: Removed printStackTrace, should log securely if needed
            return ""
        }
    }

    private fun decodeBase32(base32: String): ByteArray {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val out = ByteArray((base32.length * 5) / 8)
        var buffer = 0
        var bitsLeft = 0
        var index = 0
        for (c in base32) {
            val val32 = alphabet.indexOf(c)
            if (val32 == -1) continue
            buffer = (buffer shl 5) or val32
            bitsLeft += 5
            if (bitsLeft >= 8) {
                if (index < out.size) {
                    out[index++] = (buffer shr (bitsLeft - 8)).toByte()
                    bitsLeft -= 8
                    // Optional: buffer = buffer and ((1 shl bitsLeft) - 1)
                }
            }
        }
        return out
    }
}
