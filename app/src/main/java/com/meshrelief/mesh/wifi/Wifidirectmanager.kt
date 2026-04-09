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
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WifiDirectManager
 *
 * Manages WiFi P2P lifecycle: initialization, peer discovery, connection,
 * and disconnection. Exposes discovered peers as a [StateFlow].
 *
 * Must call [initialize] once (e.g. in MainActivity.onResume) and
 * [shutdown] once (e.g. in MainActivity.onPause) to correctly tie
 * the BroadcastReceiver to the activity lifecycle.
 */
@Singleton
class WifiDirectManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val TAG = "WifiDirectManager"

    // ── WiFi P2P system services ──────────────────────────────────────────
    private val wifiP2pManager: WifiP2pManager =
        context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager

    private var channel: Channel? = null

    // ── Peer list state ───────────────────────────────────────────────────
    private val _peerList = MutableStateFlow<List<WifiP2pDevice>>(emptyList())
    val peerList: StateFlow<List<WifiP2pDevice>> = _peerList.asStateFlow()

    // ── Connected peer IPs ────────────────────────────────────────────────
    /**
     * Emits the list of actual IP addresses of currently connected peers.
     *
     * - If this device is the **Group Owner**: reads the ARP table from
     *   /proc/net/arp to find IPs of connected clients.
     * - If this device is a **client**: emits just the group owner's IP,
     *   since that is the only directly reachable peer.
     *
     * Updated every time [WIFI_P2P_CONNECTION_CHANGED_ACTION] fires.
     */
    private val _connectedPeerIPs = MutableStateFlow<List<String>>(emptyList())
    val connectedPeerIPs: StateFlow<List<String>> = _connectedPeerIPs.asStateFlow()

    // ── Connection info ───────────────────────────────────────────────────
    /** True when this device is the Group Owner in an active P2P group. */
    private var isGroupOwner: Boolean = false

    /** IP address of the group owner (set when connection info arrives). */
    private var groupOwnerAddress: String? = null

    // ── BroadcastReceiver ─────────────────────────────────────────────────
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
                    // Peer list has changed — request the updated list
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
                                isGroupOwner = info.isGroupOwner
                                groupOwnerAddress = info.groupOwnerAddress?.hostAddress
                                Log.d(
                                    TAG,
                                    "Group formed. isOwner=$isGroupOwner " +
                                            "ownerAddr=$groupOwnerAddress"
                                )

                                // ── Populate connectedPeerIPs ──────────────
                                if (info.isGroupOwner) {
                                    // We are the GO: connected clients are
                                    // listed in the ARP table after DHCP lease.
                                    _connectedPeerIPs.value = getConnectedClientIPs()
                                    Log.d(
                                        TAG,
                                        "GO mode: found ${_connectedPeerIPs.value.size} client(s) " +
                                                "via ARP: ${_connectedPeerIPs.value}"
                                    )
                                } else {
                                    // We are a client: only reachable peer is the GO.
                                    val ownerIp = info.groupOwnerAddress?.hostAddress
                                    _connectedPeerIPs.value = listOfNotNull(ownerIp)
                                    Log.d(TAG, "Client mode: GO IP = $ownerIp")
                                }
                                // ──────────────────────────────────────────

                            } else {
                                isGroupOwner = false
                                groupOwnerAddress = null
                                _connectedPeerIPs.value = emptyList()
                                Log.d(TAG, "Group dissolved — peer IP list cleared.")
                            }
                        }
                    }
                }

                WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                    // This device's own details changed (name, status, etc.)
                    Log.d(TAG, "This device changed.")
                }
            }
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    /**
     * Call from MainActivity.onResume (or a foreground Service).
     * Initializes the P2P channel, registers the receiver, and
     * triggers the first peer discovery pass.
     */
    @SuppressLint("MissingPermission")
    fun initialize() {
        channel = wifiP2pManager.initialize(context, context.mainLooper) {
            // Called if the framework kills the channel — reinitialize
            Log.w(TAG, "P2P channel disconnected; reinitializing.")
            initialize()
        }
        context.registerReceiver(receiver, intentFilter)
        Log.d(TAG, "WifiDirectManager initialized.")
        discoverPeers()
    }

    /**
     * Call from MainActivity.onPause to unregister the receiver
     * and avoid leaking it across the activity lifecycle.
     */
    fun shutdown() {
        try {
            context.unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Receiver was not registered: ${e.message}")
        }
        Log.d(TAG, "WifiDirectManager shut down.")
    }

    // ── Discovery ─────────────────────────────────────────────────────────

    /**
     * Starts WiFi P2P peer discovery. Results are delivered via the
     * [WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION] broadcast, which
     * triggers a [peerList] update automatically.
     */
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

    // ── Connect / Disconnect ──────────────────────────────────────────────

    /**
     * Initiates a WiFi P2P connection to the device at [deviceAddress].
     * [deviceAddress] is the MAC address from a [WifiP2pDevice].
     */
    @SuppressLint("MissingPermission")
    fun connectToPeer(deviceAddress: String) {
        val ch = channel ?: run {
            Log.w(TAG, "connectToPeer: channel not initialized.")
            return
        }
        val config = WifiP2pConfig().apply {
            this.deviceAddress = deviceAddress
        }
        wifiP2pManager.connect(ch, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Connect initiated to $deviceAddress")
            }
            override fun onFailure(reason: Int) {
                Log.w(TAG, "Connect to $deviceAddress failed. Reason: $reason")
            }
        })
    }

    /**
     * Removes the current P2P group (disconnects all peers).
     */
    fun disconnect() {
        val ch = channel ?: run {
            Log.w(TAG, "disconnect: channel not initialized.")
            return
        }
        wifiP2pManager.removeGroup(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Group removed (disconnected).")
                isGroupOwner = false
                groupOwnerAddress = null
                _connectedPeerIPs.value = emptyList()
            }
            override fun onFailure(reason: Int) {
                Log.w(TAG, "removeGroup failed. Reason: $reason")
            }
        })
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /** Returns the group owner IP if a group is formed, null otherwise. */
    fun getGroupOwnerAddress(): String? = groupOwnerAddress

    /** True if this device is currently the group owner. */
    fun isGroupOwner(): Boolean = isGroupOwner

    /**
     * Reads /proc/net/arp to find IP addresses of devices that have received
     * a DHCP lease from this device (only meaningful when we are the Group Owner).
     *
     * The ARP table format is:
     *   IP address  HW type  Flags  HW address  Mask  Device
     *
     * We skip incomplete/empty entries (Flags == 0x0) to avoid returning
     * stale or unresolved entries.
     */
    private fun getConnectedClientIPs(): List<String> {
        return try {
            java.io.File("/proc/net/arp")
                .readLines()
                .drop(1)                          // skip header line
                .mapNotNull { line ->
                    val parts = line.trim().split("\\s+".toRegex())
                    // parts[0] = IP, parts[2] = flags ("0x2" means complete/valid)
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