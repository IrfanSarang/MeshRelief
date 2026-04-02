package com.meshrelief.core.crypto

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceIdentity @Inject constructor(
    @ApplicationContext private val context: Context
) {

    @SuppressLint("HardwareIds")
    fun generateDeviceId(phoneNumber: String): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown"

        val raw = "$androidId$phoneNumber${System.currentTimeMillis()}"
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