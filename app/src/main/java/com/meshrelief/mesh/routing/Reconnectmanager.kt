package com.meshrelief.mesh.routing

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.meshrelief.mesh.wifi.WifiDirectManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ReconnectManager
 *
 * Owns the exponential-backoff reconnect state for Bug #4
 * (Wi-Fi Direct star topology GO failure).
 *
 * Triggered by [com.meshrelief.mesh.wifi.ConnectionManager] via
 * [MeshForegroundService] when [BroadcastResult.goUnreachable] is true.
 *
 * Backoff schedule: 5s → 10s → 20s → 40s (capped), then stops.
 * Resets immediately on [onReconnected].
 *
 * Usage in MeshForegroundService:
 *
 *   val result = connectionManager.broadcastPacket(packet)
 *   if (result.goUnreachable) reconnectManager.onGoUnreachable()
 *
 *   // When WifiDirectManager reports groupFormed again:
 *   reconnectManager.onReconnected()
 */
@Singleton
class ReconnectManager @Inject constructor(
    private val wifiDirectManager: WifiDirectManager
) {

    private val TAG = "ReconnectManager"

    private val handler = Handler(Looper.getMainLooper())

    /** Current backoff attempt index (0-based). */
    private var attempt = 0

    /** Guards against stacking multiple concurrent reconnect loops. */
    private var isReconnecting = false

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Call when the Group Owner is confirmed unreachable.
     * Starts the exponential-backoff discovery loop if not already running.
     */
    fun onGoUnreachable() {
        if (isReconnecting) {
            Log.d(TAG, "onGoUnreachable: reconnect already in progress — ignoring.")
            return
        }
        Log.w(TAG, "GO unreachable — starting reconnect backoff.")
        isReconnecting = true
        attempt = 0
        scheduleNext()
    }

    /**
     * Call when a P2P group has formed successfully.
     * Cancels any pending reconnect attempts and resets state.
     */
    fun onReconnected() {
        handler.removeCallbacksAndMessages(null)
        isReconnecting = false
        attempt = 0
        Log.d(TAG, "Reconnected — backoff cleared.")
    }

    // ── Internal ──────────────────────────────────────────────────────────

    /**
     * Posts a single delayed [WifiDirectManager.discoverPeers] call.
     * Re-schedules itself until [MAX_ATTEMPTS] is reached.
     */
    private fun scheduleNext() {
        val delayMs = minOf(BASE_DELAY_MS * (1 shl attempt), MAX_DELAY_MS)
        Log.w(TAG, "Reconnect attempt ${attempt + 1}/$MAX_ATTEMPTS in ${delayMs}ms")

        handler.postDelayed({
            attempt++
            Log.d(TAG, "Firing discoverPeers() (attempt $attempt)")
            wifiDirectManager.discoverPeers()

            if (attempt < MAX_ATTEMPTS) {
                scheduleNext()
            } else {
                Log.e(TAG, "Max reconnect attempts ($MAX_ATTEMPTS) reached — giving up.")
                isReconnecting = false
            }
        }, delayMs)
    }

    companion object {
        private const val BASE_DELAY_MS = 5_000L   // 5 s
        private const val MAX_DELAY_MS  = 40_000L  // 40 s cap
        private const val MAX_ATTEMPTS  = 4        // 5s, 10s, 20s, 40s
    }
}