package com.meshrelief.mesh.wifi

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pManager.Channel
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WifiDirectManager @Inject constructor(
    @ApplicationContext private val context: Context
) : MeshTransport {

    private val TAG = "WifiDirectManager"

    private val wifiP2pManager: WifiP2pManager =
        context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager

    private var channel: Channel? = null

    private var isInitialized = false
    private var isReceiverRegistered = false

    private var wasConnected = false
    private var reconnectAttempt = 0
    private val reconnectHandler = Handler(Looper.getMainLooper())

    private val _peerList = MutableStateFlow<List<WifiP2pDevice>>(emptyList())
    val peerList: StateFlow<List<WifiP2pDevice>> = _peerList.asStateFlow()

    private val _connectedPeerIPs = MutableStateFlow<List<String>>(emptyList())
    override val connectedPeerIPs: StateFlow<List<String>> = _connectedPeerIPs.asStateFlow()

    private var _isGroupOwner: Boolean = false
    private var groupOwnerAddr: String? = null

    // ── Scheduled discovery (battery-saver mode) ──────────────────────────
    private var discoveryIntervalMs: Long = 0L
    private val discoveryHandler = Handler(Looper.getMainLooper())
    private val discoveryRunnable: Runnable = object : Runnable {
        override fun run() {
            discoverPeers()
            if (discoveryIntervalMs > 0) {
                discoveryHandler.postDelayed(this, discoveryIntervalMs)
            }
        }
    }

    private val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }

    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {

                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    val enabled = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                    Log.d(TAG, "WiFi Direct enabled: $enabled")
                }

                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    channel?.let { ch ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            wifiP2pManager.requestPeers(ch) { peerListResult ->
                                _peerList.value = peerListResult.deviceList.toList()
                                Log.d(TAG, "Peers updated: ${_peerList.value.size} found")
                            }
                        } else {
                            @Suppress("DEPRECATION")
                            wifiP2pManager.requestPeers(ch) { peerListResult ->
                                _peerList.value = peerListResult.deviceList.toList()
                                Log.d(TAG, "Peers updated: ${_peerList.value.size} found")
                            }
                        }
                    }
                }

                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    channel?.let { ch ->
                        wifiP2pManager.requestConnectionInfo(ch) { info ->
                            if (info.groupFormed) {
                                reconnectAttempt = 0
                                reconnectHandler.removeCallbacksAndMessages(null)

                                _isGroupOwner = info.isGroupOwner
                                groupOwnerAddr = info.groupOwnerAddress?.hostAddress
                                Log.d(
                                    TAG,
                                    "Group formed. isOwner=$_isGroupOwner " +
                                            "ownerAddr=$groupOwnerAddr"
                                )

                                if (info.isGroupOwner) {
                                    _connectedPeerIPs.value = getConnectedClientIPs()
                                    Log.d(
                                        TAG,
                                        "GO mode: found ${_connectedPeerIPs.value.size} client(s) " +
                                                "via ARP: ${_connectedPeerIPs.value}"
                                    )
                                } else {
                                    val ownerIp = info.groupOwnerAddress?.hostAddress
                                    _connectedPeerIPs.value = listOfNotNull(ownerIp)
                                    Log.d(TAG, "Client mode: GO IP = $ownerIp")
                                }

                            } else {
                                _isGroupOwner = false
                                groupOwnerAddr = null
                                _connectedPeerIPs.value = emptyList()
                                Log.d(TAG, "Group dissolved — peer IP list cleared.")

                                if (wasConnected) {
                                    scheduleReconnect()
                                }
                            }

                            wasConnected = info.groupFormed
                        }
                    }
                }

                WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                    Log.d(TAG, "This device changed.")
                }
            }
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    fun initialize() {
        if (isInitialized) {
            Log.d(TAG, "initialize() called but already initialized — skipping.")
            return
        }

        channel = wifiP2pManager.initialize(context, context.mainLooper) {
            Log.w(TAG, "P2P channel disconnected; reinitializing.")
            isInitialized = false
            isReceiverRegistered = false
            initialize()
        }

        if (!isReceiverRegistered) {
            context.registerReceiver(receiver, intentFilter)
            isReceiverRegistered = true
        }

        isInitialized = true
        Log.d(TAG, "WifiDirectManager initialized.")
        discoverPeers()
    }

    fun shutdown() {
        reconnectHandler.removeCallbacksAndMessages(null)
        discoveryHandler.removeCallbacks(discoveryRunnable)
        discoveryIntervalMs = 0L
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(receiver)
                isReceiverRegistered = false
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Receiver was not registered: ${e.message}")
            }
        }
        isInitialized = false
        wasConnected = false
        Log.d(TAG, "WifiDirectManager shut down.")
    }

    // ── Discovery ─────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    fun discoverPeers() {
        val ch = channel ?: run {
            Log.w(TAG, "discoverPeers: channel not initialized.")
            return
        }
        wifiP2pManager.discoverPeers(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Peer discovery started.")
            }
            override fun onFailure(reason: Int) {
                Log.w(TAG, "Peer discovery failed. Reason code: $reason")
            }
        })
    }

    /**
     * Sets a repeating discovery interval for battery-saver mode.
     * Pass intervalMs > 0 to enable scheduled discovery at that cadence.
     * Pass 0 to cancel scheduled discovery and revert to event-driven behaviour.
     */
    fun setDiscoveryInterval(intervalMs: Long) {
        discoveryHandler.removeCallbacks(discoveryRunnable)
        discoveryIntervalMs = intervalMs
        if (intervalMs > 0 && isInitialized) {
            Log.d(TAG, "Scheduled discovery interval set to ${intervalMs}ms")
            discoveryHandler.postDelayed(discoveryRunnable, intervalMs)
        } else {
            Log.d(TAG, "Scheduled discovery disabled — reverting to event-driven discovery.")
        }
    }

    // ── Reconnect ─────────────────────────────────────────────────────────

    private fun scheduleReconnect() {
        val delayMs = minOf(5_000L * (1 shl reconnectAttempt), 40_000L)
        Log.w(TAG, "Scheduling reconnect attempt ${reconnectAttempt + 1} in ${delayMs}ms")
        reconnectHandler.postDelayed({
            reconnectAttempt++
            Log.d(TAG, "Reconnect attempt $reconnectAttempt — calling discoverPeers()")
            discoverPeers()
        }, delayMs)
    }

    // ── Connect / Disconnect ──────────────────────────────────────────────

    /**
     * Connects to a peer using WiFi Direct.
     *
     * @param deviceAddress MAC address of the target peer.
     * @param groupOwnerIntent 0–15; higher value = stronger preference to become Group Owner.
     *   Pass (batteryPercent / 10).coerceIn(0, 15) from the caller so that
     *   the device with more battery naturally becomes the Group Owner.
     *   Defaults to 7 (neutral / system-decided).
     */
    @SuppressLint("MissingPermission")
    fun connectToPeer(deviceAddress: String, groupOwnerIntent: Int = 7) {
        val ch = channel ?: run {
            Log.w(TAG, "connectToPeer: channel not initialized.")
            return
        }
        val config = WifiP2pConfig().apply {
            this.deviceAddress = deviceAddress
            this.groupOwnerIntent = groupOwnerIntent.coerceIn(0, 15)
        }
        Log.d(TAG, "Connecting to $deviceAddress with groupOwnerIntent=${config.groupOwnerIntent}")
        wifiP2pManager.connect(ch, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Connect initiated to $deviceAddress")
            }
            override fun onFailure(reason: Int) {
                Log.w(TAG, "Connect to $deviceAddress failed. Reason: $reason")
            }
        })
    }

    fun disconnect() {
        val ch = channel ?: run {
            Log.w(TAG, "disconnect: channel not initialized.")
            return
        }
        wifiP2pManager.removeGroup(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Group removed (disconnected).")
                _isGroupOwner = false
                groupOwnerAddr = null
                wasConnected = false
                reconnectHandler.removeCallbacksAndMessages(null)
                _connectedPeerIPs.value = emptyList()
            }
            override fun onFailure(reason: Int) {
                Log.w(TAG, "removeGroup failed. Reason: $reason")
            }
        })
    }

    // ── MeshTransport overrides ───────────────────────────────────────────

    override fun getGroupOwnerAddress(): String? = groupOwnerAddr

    override fun isGroupOwner(): Boolean = _isGroupOwner

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun getConnectedClientIPs(): List<String> {
        return try {
            java.io.File("/proc/net/arp")
                .readLines()
                .drop(1)
                .mapNotNull { line ->
                    val parts = line.trim().split("\\s+".toRegex())
                    val ip    = parts.getOrNull(0)
                    val flags = parts.getOrNull(2)
                    if (ip != null && flags != null && flags != "0x0") ip else null
                }
                .filter { it.isNotBlank() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read ARP table: ${e.message}")
            emptyList()
        }
    }
}