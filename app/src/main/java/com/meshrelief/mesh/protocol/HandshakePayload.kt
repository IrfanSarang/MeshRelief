package com.meshrelief.mesh.protocol

import android.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class HandshakePayload(
    val deviceId: String,
    val name: String,
    val phone4: String,
    val publicKeyBase64: String,
    val triage: String,
    val battery: Int,
    val lat: Double,
    val lng: Double
)

fun HandshakePayload.encode(): String = Json.encodeToString(this)

fun MeshPacket.decodeHandshake(): HandshakePayload =
    Json { ignoreUnknownKeys = true }.decodeFromString(this.payload)