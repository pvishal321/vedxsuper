package com.vedx.vedxsuper.broker

import dev.turingcomplete.kotlinonetimepassword.GoogleAuthenticator

object TotpGenerator {
    /**
     * Generates a 6-digit TOTP code from the provided secret key.
     */
    fun generate(secret: String): String {
        return try {
            // Remove spaces and make uppercase for Base32 standard
            val cleanSecret = secret.replace("\\s".toRegex(), "").uppercase()
            val generator = GoogleAuthenticator(cleanSecret)
            generator.generate()
        } catch (e: Exception) {
            android.util.Log.e("TOTP_GEN", "Failed to generate TOTP: ${e.message}")
            ""
        }
    }
}
