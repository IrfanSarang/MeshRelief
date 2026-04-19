package com.meshrelief.mesh.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// ── JSON instance (shared, lenient) ──────────────────────────────────────────

internal val MeshJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults    = true
    isLenient         = true
}

// ── Sealed payload hierarchy ──────────────────────────────────────────────────

@Serializable
sealed class MeshPayload

@Serializable
@SerialName("sos")
data class SosPayload(
    val triage  : String,
    val lat     : Double,
    val lng     : Double,
    val message : String = ""
) : MeshPayload()

@Serializable
@SerialName("camp")
data class CampPayload(
    val campId      : String,
    val name        : String,
    val type        : String,
    val capacity    : Int,
    val occupancy   : Int,
    val lat         : Double,
    val lng         : Double,
    val adminNotes  : String = ""
) : MeshPayload()

@Serializable
@SerialName("status")
data class StatusPayload(
    val triage  : String,
    val battery : Int,
    val message : String = ""
) : MeshPayload()

@Serializable
@SerialName("bulletin")
data class BulletinPayload(
    val category : String,
    val text     : String
) : MeshPayload()

@Serializable
@SerialName("headcount_response")
data class HeadcountResponsePayload(
    val deviceId : String,
    val name     : String,
    val triage   : String,
    val lat      : Double,
    val lng      : Double
) : MeshPayload()

// ── Encode helpers ────────────────────────────────────────────────────────────

fun SosPayload.encode()               : String = MeshJson.encodeToString(SosPayload.serializer(),               this)
fun CampPayload.encode()              : String = MeshJson.encodeToString(CampPayload.serializer(),              this)
fun StatusPayload.encode()            : String = MeshJson.encodeToString(StatusPayload.serializer(),            this)
fun BulletinPayload.encode()          : String = MeshJson.encodeToString(BulletinPayload.serializer(),          this)
fun HeadcountResponsePayload.encode() : String = MeshJson.encodeToString(HeadcountResponsePayload.serializer(), this)

// ── Decode extension functions on MeshPacket ──────────────────────────────────

fun MeshPacket.decodeSos()               : SosPayload               = MeshJson.decodeFromString(SosPayload.serializer(),               payload)
fun MeshPacket.decodeCamp()              : CampPayload               = MeshJson.decodeFromString(CampPayload.serializer(),              payload)
fun MeshPacket.decodeStatus()            : StatusPayload             = MeshJson.decodeFromString(StatusPayload.serializer(),            payload)
fun MeshPacket.decodeBulletin()          : BulletinPayload           = MeshJson.decodeFromString(BulletinPayload.serializer(),          payload)
fun MeshPacket.decodeHeadcountResponse() : HeadcountResponsePayload  = MeshJson.decodeFromString(HeadcountResponsePayload.serializer(), payload)