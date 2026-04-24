package com.meshrelief.core.crypto

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "DeviceIdentity"
private const val KEYSTORE_ALIAS = "meshrelief_identity"
private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val SIGN_ALGORITHM = "SHA256withECDSA"

@Singleton
class DeviceIdentity @Inject constructor(
    @ApplicationContext private val context: Context
) {

    // ── Keypair ───────────────────────────────────────────────────────────

    /**
     * Loads the ECDSA keypair from Android Keystore if it already exists,
     * otherwise generates a new secp256r1 keypair and stores it under
     * [KEYSTORE_ALIAS]. The private key never leaves the Keystore.
     */
    fun generateOrLoadKeyPair(): KeyPair {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

        if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
            Log.d(TAG, "Loading existing keypair from Keystore")
            val privateKey = (keyStore.getKey(KEYSTORE_ALIAS, null)
                    as? java.security.PrivateKey)
                ?: error("Key '$KEYSTORE_ALIAS' exists but is not a PrivateKey")
            val publicKey = keyStore.getCertificate(KEYSTORE_ALIAS).publicKey
            return KeyPair(publicKey, privateKey)
        }

        Log.d(TAG, "Generating new ECDSA keypair in Keystore")
        val keyGenSpec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .build()

        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            ANDROID_KEYSTORE
        )
        keyPairGenerator.initialize(keyGenSpec)
        return keyPairGenerator.generateKeyPair()
    }

    /**
     * Returns the device's public key bytes (X.509 encoded) so it can be
     * shared with peers for signature verification.
     */
    fun getPublicKeyBytes(): ByteArray {
        return generateOrLoadKeyPair().public.encoded
    }

    // ── Sign ──────────────────────────────────────────────────────────────

    /**
     * Signs the UTF-8 bytes of [data] using the device's private key
     * (SHA256withECDSA) and returns a Base64-encoded signature string.
     */
    fun sign(data: String): String {
        return try {
            val privateKey = generateOrLoadKeyPair().private
            val signer = Signature.getInstance(SIGN_ALGORITHM).apply {
                initSign(privateKey)
                update(data.toByteArray(Charsets.UTF_8))
            }
            Base64.encodeToString(signer.sign(), Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Signing failed: ${e.message}")
            ""
        }
    }

    // ── Verify ────────────────────────────────────────────────────────────

    /**
     * Verifies [signature] (Base64-encoded) against [data] using the
     * X.509-encoded [publicKeyBytes] from the sender peer.
     * Returns `true` if valid, `false` otherwise.
     */
    fun verify(data: String, signature: String, publicKeyBytes: ByteArray): Boolean {
        return try {
            val publicKey: PublicKey = java.security.KeyFactory
                .getInstance(KeyProperties.KEY_ALGORITHM_EC)
                .generatePublic(java.security.spec.X509EncodedKeySpec(publicKeyBytes))

            val sigBytes = Base64.decode(signature, Base64.NO_WRAP)
            Signature.getInstance(SIGN_ALGORITHM).apply {
                initVerify(publicKey)
                update(data.toByteArray(Charsets.UTF_8))
            }.verify(sigBytes)
        } catch (e: Exception) {
            Log.e(TAG, "Verification failed: ${e.message}")
            false
        }
    }

    // ── Existing helpers ──────────────────────────────────────────────────

    @SuppressLint("HardwareIds")
    fun generateDeviceId(phoneNumber: String): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown"

        val raw = "$androidId$phoneNumber"
        return sha256(raw)
    }

    fun sha256(input: String): String {
        val bytes = MessageDigest
            .getInstance("SHA-256")
            .digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun getLastFourDigits(phone: String): String {
        return if (phone.length >= 4) phone.takeLast(4) else phone
    }
}