package com.meshrelief.core.util

object Constants {

    // Mesh routing
    const val DEFAULT_TTL = 7
    const val SOS_TTL = 10
    const val SEEN_PACKET_CACHE_SIZE = 500
    const val WIFI_DIRECT_PORT = 8888

    // SOS cooldowns (milliseconds)
    const val SOS_COOLDOWN_DEFAULT_MS = 600_000L
    const val SOS_COOLDOWN_VERIFIED_MS = 300_000L
    const val SOS_COOLDOWN_CRITICAL_MS = 180_000L
    const val SOS_COOLDOWN_FLAGGED_MS = 1_800_000L
    const val SOS_CONFIRM_AUTO_CANCEL_S = 10

    // Status broadcast
    const val STATUS_BROADCAST_INTERVAL_MS = 60_000L
    const val HEADCOUNT_RESPONSE_TIMEOUT_S = 30

    // Battery
    const val BATTERY_CRITICAL_THRESHOLD = 15

    // Text limits
    const val MAX_BULLETIN_LENGTH = 280
    const val MAX_STATUS_MESSAGE_LENGTH = 100

    // Map
    const val MAP_TILE_CACHE_MB = 200

    // Chatbot
    //const val MODEL_FILE_NAME = "survival_qwen_q4.gguf"

    // Battery saver
    const val SAVER_MODE_DISCOVERY_INTERVAL_S = 120
}