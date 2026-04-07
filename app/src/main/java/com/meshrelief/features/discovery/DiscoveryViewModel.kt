package com.meshrelief.features.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshrelief.core.model.TriageLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ---------------------------------------------------------------------------
// Data models
// ---------------------------------------------------------------------------

// The local "enum class TriageStatus { SAFE, MINOR, CRITICAL, UNKNOWN }" has
// been removed. This file now uses com.meshrelief.core.model.TriageLevel which
// carries the same four values plus label/color metadata.
//
// Note: DiscoveryViewModel intentionally uses TriageLevel (patient-facing clinical
// scale) rather than TriageStatus (network colour indicator). See core/model/ for
// the distinction.

enum class ConnectionState { CONNECTED, CONNECTING, AVAILABLE }

enum class SortMode { SIGNAL, TRIAGE }

data class DiscoveredPeer(
    val id: String,
    val displayName: String,
    val deviceIdSuffix: String,      // last 4 chars, e.g. "3821"
    val triage: TriageLevel,         // was: local TriageStatus
    val signalBars: Int,             // 1–4
    val hopCount: Int,               // 0 = direct
    val connectionState: ConnectionState
)

enum class ScanState { IDLE, SCANNING }

data class DiscoveryUiState(
    val scanState: ScanState = ScanState.IDLE,
    val lastScanMinutesAgo: Int = 0,
    val peers: List<DiscoveredPeer> = emptyList(),
    val sortMode: SortMode = SortMode.SIGNAL
)

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

@HiltViewModel
class DiscoveryViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(DiscoveryUiState())
    val uiState: StateFlow<DiscoveryUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null

    // Seed data — 6 varied peers
    private val seedPeers = listOf(
        DiscoveredPeer("p1", "Ravi Kumar",    "4401", TriageLevel.CRITICAL, 4, 0, ConnectionState.CONNECTED),
        DiscoveredPeer("p2", "Anjali Singh",  "2893", TriageLevel.SAFE,     3, 0, ConnectionState.CONNECTED),
        DiscoveredPeer("p3", "Mohammed Faiz", "7712", TriageLevel.MINOR,    2, 1, ConnectionState.AVAILABLE),
        DiscoveredPeer("p4", "Priya Nair",    "5530", TriageLevel.UNKNOWN,  1, 2, ConnectionState.AVAILABLE),
        DiscoveredPeer("p5", "Suresh Patil",  "3380", TriageLevel.SAFE,     3, 1, ConnectionState.CONNECTING),
        DiscoveredPeer("p6", "Deepa Menon",   "9921", TriageLevel.CRITICAL, 2, 2, ConnectionState.AVAILABLE),
    )

    init {
        // Start with a scan on launch
        startScan()
    }

    fun startScan() {
        if (_uiState.value.scanState == ScanState.SCANNING) return
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _uiState.update { it.copy(scanState = ScanState.SCANNING, peers = emptyList()) }
            delay(3_000L) // simulated 3-second scan
            _uiState.update {
                it.copy(
                    scanState = ScanState.IDLE,
                    lastScanMinutesAgo = 0,
                    peers = sortedPeers(seedPeers, it.sortMode)
                )
            }
            // Tick the "last scan X min ago" counter
            tickLastScan()
        }
    }

    private fun tickLastScan() {
        viewModelScope.launch {
            var minutes = 0
            while (true) {
                delay(60_000L)
                minutes++
                _uiState.update { it.copy(lastScanMinutesAgo = minutes) }
            }
        }
    }

    fun setSortMode(mode: SortMode) {
        _uiState.update {
            it.copy(
                sortMode = mode,
                peers = sortedPeers(it.peers, mode)
            )
        }
    }

    fun connectToPeer(peerId: String) {
        // Transition: AVAILABLE → CONNECTING → CONNECTED
        _uiState.update { state ->
            state.copy(peers = state.peers.map { p ->
                if (p.id == peerId) p.copy(connectionState = ConnectionState.CONNECTING) else p
            })
        }
        viewModelScope.launch {
            delay(2_000L)
            _uiState.update { state ->
                state.copy(peers = state.peers.map { p ->
                    if (p.id == peerId) p.copy(connectionState = ConnectionState.CONNECTED) else p
                })
            }
        }
    }

    private fun sortedPeers(peers: List<DiscoveredPeer>, mode: SortMode): List<DiscoveredPeer> =
        when (mode) {
            SortMode.SIGNAL -> peers.sortedByDescending { it.signalBars }
            SortMode.TRIAGE -> peers.sortedWith(
                compareBy {
                    when (it.triage) {
                        TriageLevel.CRITICAL -> 0
                        TriageLevel.MINOR    -> 1
                        TriageLevel.SAFE     -> 2
                        TriageLevel.UNKNOWN  -> 3
                    }
                }
            )
        }
}