package com.meshrelief.features.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── Data models ──────────────────────────────────────────────────────────────

enum class TriageLevel { GREEN, AMBER, RED, UNKNOWN }

data class AdminPeer(
    val id: String,
    val name: String,
    val triage: TriageLevel,
    val lastSeenMinutesAgo: Int
)

data class AdminSosAlert(
    val id: String,
    val senderName: String,
    val triage: TriageLevel,
    val lat: Double,
    val lon: Double,
    val minutesAgo: Int,
    val acknowledged: Boolean = false
)

data class AdminCamp(
    val id: String,
    val name: String,
    val occupancy: Int,
    val capacity: Int
)

data class BulletinEntry(
    val id: String,
    val message: String,
    val timeLabel: String
)

data class AdminUiState(
    val adminDeviceName: String = "ADMIN-NODE-01",
    val peers: List<AdminPeer> = emptyList(),
    val sosAlerts: List<AdminSosAlert> = emptyList(),
    val camps: List<AdminCamp> = emptyList(),
    val bulletins: List<BulletinEntry> = emptyList(),
    val messagesToday: Int = 0,
    val isScanning: Boolean = false,
    val bulletinDraft: String = ""
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class AdminViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    init {
        loadSampleData()
    }

    private fun loadSampleData() {
        val samplePeers = listOf(
            AdminPeer("p1", "Ravi-Android-7", TriageLevel.GREEN, 2),
            AdminPeer("p2", "Priya-Device-3", TriageLevel.AMBER, 5),
            AdminPeer("p3", "Arjun-Phone-12", TriageLevel.RED, 1),
            AdminPeer("p4", "Meera-Tab-2", TriageLevel.GREEN, 8),
            AdminPeer("p5", "Suresh-Node-5", TriageLevel.UNKNOWN, 15),
            AdminPeer("p6", "Fatima-Phone-9", TriageLevel.GREEN, 3)
        )

        val sampleSos = listOf(
            AdminSosAlert("s1", "Arjun-Phone-12", TriageLevel.RED, 19.0760, 72.8777, 3),
            AdminSosAlert("s2", "Priya-Device-3", TriageLevel.AMBER, 19.0821, 72.8856, 11),
            AdminSosAlert("s3", "Suresh-Node-5", TriageLevel.UNKNOWN, 19.0700, 72.8700, 22)
        )

        val sampleCamps = listOf(
            AdminCamp("c1", "Relief Camp Alpha", 87, 120),
            AdminCamp("c2", "Shelter Block B", 45, 60),
            AdminCamp("c3", "Medical Tent C", 12, 20),
            AdminCamp("c4", "Community Hall D", 200, 300)
        )

        _uiState.update {
            it.copy(
                peers = samplePeers,
                sosAlerts = sampleSos,
                camps = sampleCamps,
                messagesToday = 47,
                bulletins = listOf(
                    BulletinEntry("b0", "Network established. All nodes operational.", "09:14 AM")
                )
            )
        }
    }

    fun acknowledgeSos(alertId: String) {
        _uiState.update { state ->
            state.copy(
                sosAlerts = state.sosAlerts.map { alert ->
                    if (alert.id == alertId) alert.copy(acknowledged = true) else alert
                }
            )
        }
    }

    fun onBulletinDraftChange(text: String) {
        _uiState.update { it.copy(bulletinDraft = text) }
    }

    fun broadcastBulletin() {
        val draft = _uiState.value.bulletinDraft.trim()
        if (draft.isBlank()) return
        val newEntry = BulletinEntry(
            id = "b${System.currentTimeMillis()}",
            message = draft,
            timeLabel = currentTimeLabel()
        )
        _uiState.update { state ->
            state.copy(
                bulletins = state.bulletins + newEntry,
                bulletinDraft = ""
            )
        }
    }

    fun scanNetwork() {
        if (_uiState.value.isScanning) return
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true) }
            delay(2_000)
            val newPeer = AdminPeer(
                id = "p_new_${System.currentTimeMillis()}",
                name = "NewDevice-${(10..99).random()}",
                triage = TriageLevel.UNKNOWN,
                lastSeenMinutesAgo = 0
            )
            _uiState.update { state ->
                state.copy(
                    isScanning = false,
                    peers = state.peers + newPeer
                )
            }
        }
    }

    private fun currentTimeLabel(): String {
        val cal = java.util.Calendar.getInstance()
        val h = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val m = cal.get(java.util.Calendar.MINUTE)
        val ampm = if (h < 12) "AM" else "PM"
        val hour = if (h % 12 == 0) 12 else h % 12
        return "%02d:%02d %s".format(hour, m, ampm)
    }
}