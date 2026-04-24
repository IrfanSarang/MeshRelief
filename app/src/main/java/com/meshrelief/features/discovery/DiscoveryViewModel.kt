package com.meshrelief.features.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshrelief.core.model.TriageLevel
import com.meshrelief.data.db.entity.PeerEntity
import com.meshrelief.data.repository.PeerRepository
import com.meshrelief.mesh.wifi.WifiDirectManager
import android.net.wifi.p2p.WifiP2pDevice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ConnectionState { CONNECTED, CONNECTING, AVAILABLE }
enum class SortMode { SIGNAL, TRIAGE }
enum class ScanState { IDLE, SCANNING }

data class DiscoveredPeer(
    val id: String,
    val displayName: String,
    val deviceIdSuffix: String,
    val triage: TriageLevel,
    val signalBars: Int,
    val hopCount: Int,
    val connectionState: ConnectionState
)

data class DiscoveryUiState(
    val scanState: ScanState = ScanState.IDLE,
    val lastScanMinutesAgo: Int = 0,
    val peers: List<DiscoveredPeer> = emptyList(),
    val sortMode: SortMode = SortMode.SIGNAL
)

@HiltViewModel
class DiscoveryViewModel @Inject constructor(
    private val wifiDirectManager: WifiDirectManager,
    private val peerRepository: PeerRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiscoveryUiState())
    val uiState: StateFlow<DiscoveryUiState> = _uiState.asStateFlow()

    private var tickJob: Job? = null

    init {
        observePeerList()
        observeConnectedPeerIPs()
        startScan()
    }

    // ── Collect real peer list from WifiDirectManager ─────────────────────
    //
    // Fires on WIFI_P2P_PEERS_CHANGED_ACTION (discovery results).
    // Each device is mapped → DiscoveredPeer and upserted into Room so that
    // the Map and Status screens see fresh data without re-scanning.
    private fun observePeerList() {
        viewModelScope.launch {
            wifiDirectManager.peerList.collect { devices ->
                val mapped = devices.map { it.toDiscoveredPeer() }

                // ── Persist every discovered peer into Room ────────────────
                devices.forEach { device ->
                    peerRepository.upsert(device.toPeerEntity())
                }

                _uiState.update { state ->
                    state.copy(
                        scanState          = ScanState.IDLE,
                        lastScanMinutesAgo = 0,
                        // Preserve CONNECTING / CONNECTED states that were set
                        // by connectToPeer() so they are not wiped by a fresh
                        // discovery emission arriving while connecting.
                        peers = sortedPeers(
                            mergeConnectionStates(mapped, state.peers),
                            state.sortMode
                        )
                    )
                }
                restartTickCounter()
            }
        }
    }

    // ── Watch connectedPeerIPs to flip UI state → CONNECTED ───────────────
    //
    // WifiDirectManager emits connectedPeerIPs on WIFI_P2P_CONNECTION_CHANGED_ACTION.
    // When IPs appear it means a group is formed; the peer whose connectionState
    // is currently CONNECTING is the one we just called connectToPeer() on —
    // that is the peer we flip to CONNECTED here.
    private fun observeConnectedPeerIPs() {
        viewModelScope.launch {
            wifiDirectManager.connectedPeerIPs.collect { ips ->
                if (ips.isEmpty()) {
                    // Group dissolved — revert any CONNECTED/CONNECTING peer
                    _uiState.update { state ->
                        state.copy(peers = state.peers.map { p ->
                            if (p.connectionState != ConnectionState.AVAILABLE)
                                p.copy(connectionState = ConnectionState.AVAILABLE)
                            else p
                        })
                    }
                } else {
                    // Group formed — the peer we were CONNECTING to is now CONNECTED
                    _uiState.update { state ->
                        state.copy(peers = state.peers.map { p ->
                            if (p.connectionState == ConnectionState.CONNECTING)
                                p.copy(connectionState = ConnectionState.CONNECTED)
                            else p
                        })
                    }
                }
            }
        }
    }

    // ── Start a real WiFi Direct scan ─────────────────────────────────────
    fun startScan() {
        if (_uiState.value.scanState == ScanState.SCANNING) return
        _uiState.update { it.copy(scanState = ScanState.SCANNING, peers = emptyList()) }
        wifiDirectManager.discoverPeers()
        // ScanState.IDLE is set inside observePeerList() when devices arrive
    }

    // ── Tick counter (reset on every new scan result) ─────────────────────
    private fun restartTickCounter() {
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            var minutes = 0
            while (true) {
                kotlinx.coroutines.delay(60_000L)
                minutes++
                _uiState.update { it.copy(lastScanMinutesAgo = minutes) }
            }
        }
    }

    // ── Connect using real device address (MAC) ───────────────────────────
    //
    // peerId        == WifiP2pDevice.deviceAddress (MAC address).
    // batteryPercent == current battery level (0–100) of THIS device.
    //
    // groupOwnerIntent is derived as (batteryPercent / 10).coerceIn(0, 15).
    // A device with more battery gets a higher intent value, making it more
    // likely to become the Group Owner (which consumes more power) — so the
    // stronger battery naturally hosts the group.
    //
    // CONNECTED state is set in observeConnectedPeerIPs() when the framework
    // fires WIFI_P2P_CONNECTION_CHANGED_ACTION — no fake delay needed.
    fun connectToPeer(peerId: String, batteryPercent: Int) {
        val groupOwnerIntent = (batteryPercent / 10).coerceIn(0, 15)

        _uiState.update { state ->
            state.copy(peers = state.peers.map { p ->
                if (p.id == peerId) p.copy(connectionState = ConnectionState.CONNECTING) else p
            })
        }

        wifiDirectManager.connectToPeer(
            deviceAddress    = peerId,
            groupOwnerIntent = groupOwnerIntent
        )
    }

    fun setSortMode(mode: SortMode) {
        _uiState.update { it.copy(sortMode = mode, peers = sortedPeers(it.peers, mode)) }
    }

    // ── Mapping WifiP2pDevice → DiscoveredPeer ────────────────────────────
    private fun WifiP2pDevice.toDiscoveredPeer(): DiscoveredPeer {
        val suffix = deviceAddress.takeLast(4).replace(":", "")
        val connState = when (status) {
            WifiP2pDevice.CONNECTED -> ConnectionState.CONNECTED
            WifiP2pDevice.INVITED   -> ConnectionState.CONNECTING
            else                    -> ConnectionState.AVAILABLE
        }
        return DiscoveredPeer(
            id              = deviceAddress,
            displayName     = deviceName.ifBlank { "Unknown Device" },
            deviceIdSuffix  = suffix,
            triage          = TriageLevel.UNKNOWN,
            signalBars      = rssiToSignalBars(),
            hopCount        = 0,
            connectionState = connState
        )
    }

    // ── Mapping WifiP2pDevice → PeerEntity (for Room persistence) ─────────
    //
    // Only fields knowable from a WifiP2pDevice at discovery time are set.
    // All mesh-layer fields (phone4, triage, battery, lat/lng, hopCount)
    // are left at their defaults and will be overwritten when the peer
    // sends a handshake packet over the established P2P link.
    private fun WifiP2pDevice.toPeerEntity(): PeerEntity = PeerEntity(
        deviceId     = deviceAddress,
        name         = deviceName.ifBlank { "Unknown Device" },
        phone4       = deviceAddress.takeLast(4).replace(":", ""),
        verified     = false,
        flagged      = false,
        triageStatus = "NONE",
        battery      = 100,
        lat          = 0.0,
        lng          = 0.0,
        lastSeen     = System.currentTimeMillis(),
        hopCount     = 0
    )

    /**
     * WifiP2pDevice has no public RSSI field on most Android versions.
     * Derive signal bars from device status as a fallback; replace with
     * real RSSI once your app receives signal strength via a handshake packet.
     */
    private fun WifiP2pDevice.rssiToSignalBars(): Int = when (status) {
        WifiP2pDevice.CONNECTED -> 4
        WifiP2pDevice.INVITED   -> 3
        WifiP2pDevice.FAILED    -> 1
        else                    -> 2
    }

    // ── Preserve in-progress connection states across peer list refreshes ──
    //
    // When WifiDirectManager emits a fresh peerList during an active connection
    // attempt, the new emission would reset CONNECTING → AVAILABLE because the
    // framework hasn't yet reported the device as CONNECTED/INVITED.
    // This helper keeps whichever state is "more advanced".
    private fun mergeConnectionStates(
        fresh: List<DiscoveredPeer>,
        previous: List<DiscoveredPeer>
    ): List<DiscoveredPeer> {
        val previousById = previous.associateBy { it.id }
        return fresh.map { peer ->
            val prev = previousById[peer.id]
            if (prev != null &&
                prev.connectionState != ConnectionState.AVAILABLE &&
                peer.connectionState == ConnectionState.AVAILABLE
            ) {
                peer.copy(connectionState = prev.connectionState)
            } else {
                peer
            }
        }
    }

    private fun sortedPeers(peers: List<DiscoveredPeer>, mode: SortMode) =
        when (mode) {
            SortMode.SIGNAL -> peers.sortedByDescending { it.signalBars }
            SortMode.TRIAGE -> peers.sortedWith(compareBy {
                when (it.triage) {
                    TriageLevel.CRITICAL -> 0
                    TriageLevel.MINOR    -> 1
                    TriageLevel.SAFE     -> 2
                    TriageLevel.UNKNOWN  -> 3
                }
            })
        }
}