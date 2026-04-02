package com.meshrelief.features.camps

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

data class CampResource(
    val emoji: String,
    val name: String,
    val status: ResourceStatus
)

enum class ResourceStatus { AVAILABLE, LOW, OUT }

data class CampDetail(
    val id: String,
    val name: String,
    val type: String,
    val currentOccupancy: Int,
    val capacity: Int,
    val established: String,
    val adminContact: String,
    val lastUpdated: String,
    val adminNotes: String,
    val resources: List<CampResource>,
    val latitude: Double,
    val longitude: Double
)

data class CampDetailUiState(
    val camp: CampDetail? = null,
    val showBroadcastSheet: Boolean = false,
    val broadcastMessage: String = "",
    val showSnackbar: Boolean = false,
    val snackbarMessage: String = ""
)

@HiltViewModel
class CampDetailViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(CampDetailUiState())
    val uiState: StateFlow<CampDetailUiState> = _uiState

    fun loadCamp(campId: String) {
        val detail = when (campId) {
            "camp_1" -> CampDetail(
                id = "camp_1",
                name = "Nagpur Relief Hub",
                type = "Mixed",
                currentOccupancy = 142,
                capacity = 200,
                established = "14 Jul 2025",
                adminContact = "Rajan Deshmukh",
                lastUpdated = "2 hrs ago",
                adminNotes = "Gate 2 is open 24hr. Medical tent is at the north end. Water tanker arrives at 08:00 and 18:00 daily. Blanket stock critically low — prioritise children.",
                resources = listOf(
                    CampResource("\uD83D\uDCA7", "Water", ResourceStatus.AVAILABLE),
                    CampResource("\uD83C\uDF5A", "Food", ResourceStatus.LOW),
                    CampResource("\uD83D\uDC8A", "Medicine", ResourceStatus.AVAILABLE),
                    CampResource("\uD83E\uDDF9", "Blankets", ResourceStatus.OUT),
                    CampResource("\u26A1", "Power", ResourceStatus.LOW)
                ),
                latitude = 21.1458,
                longitude = 79.0882
            )
            "camp_2" -> CampDetail(
                id = "camp_2",
                name = "Amravati Shelter Point",
                type = "Shelter",
                currentOccupancy = 89,
                capacity = 150,
                established = "12 Jul 2025",
                adminContact = "Priya Sharma",
                lastUpdated = "45 mins ago",
                adminNotes = "Families with children on ground floor east wing. No open fires near tents. Curfew at 22:00. Report any medical emergency to block coordinator.",
                resources = listOf(
                    CampResource("\uD83D\uDCA7", "Water", ResourceStatus.AVAILABLE),
                    CampResource("\uD83C\uDF5A", "Food", ResourceStatus.AVAILABLE),
                    CampResource("\uD83D\uDC8A", "Medicine", ResourceStatus.LOW),
                    CampResource("\uD83E\uDDF9", "Blankets", ResourceStatus.AVAILABLE),
                    CampResource("\u26A1", "Power", ResourceStatus.OUT)
                ),
                latitude = 20.9333,
                longitude = 77.7500
            )
            "camp_3" -> CampDetail(
                id = "camp_3",
                name = "Wardha Medical Camp",
                type = "Medical",
                currentOccupancy = 197,
                capacity = 210,
                established = "11 Jul 2025",
                adminContact = "Dr. Meena Kulkarni",
                lastUpdated = "15 mins ago",
                adminNotes = "Critical patients in Block C. Do not enter without clearance. Oxygen cylinders on reserve only. Next resupply convoy expected tomorrow at noon.",
                resources = listOf(
                    CampResource("\uD83D\uDCA7", "Water", ResourceStatus.LOW),
                    CampResource("\uD83C\uDF5A", "Food", ResourceStatus.LOW),
                    CampResource("\uD83D\uDC8A", "Medicine", ResourceStatus.OUT),
                    CampResource("\uD83E\uDDF9", "Blankets", ResourceStatus.AVAILABLE),
                    CampResource("\u26A1", "Power", ResourceStatus.AVAILABLE)
                ),
                latitude = 20.7453,
                longitude = 78.6022
            )
            else -> CampDetail(
                id = campId,
                name = "Relief Camp $campId",
                type = "Supply",
                currentOccupancy = 55,
                capacity = 120,
                established = "10 Jul 2025",
                adminContact = "Field Coordinator",
                lastUpdated = "1 hr ago",
                adminNotes = "Supply distribution at 09:00 and 15:00. Bring your allocation slip.",
                resources = listOf(
                    CampResource("\uD83D\uDCA7", "Water", ResourceStatus.AVAILABLE),
                    CampResource("\uD83C\uDF5A", "Food", ResourceStatus.AVAILABLE),
                    CampResource("\uD83D\uDC8A", "Medicine", ResourceStatus.LOW),
                    CampResource("\uD83E\uDDF9", "Blankets", ResourceStatus.AVAILABLE),
                    CampResource("\u26A1", "Power", ResourceStatus.LOW)
                ),
                latitude = 20.5000,
                longitude = 78.0000
            )
        }
        _uiState.value = _uiState.value.copy(camp = detail)
    }

    fun openBroadcastSheet() {
        _uiState.value = _uiState.value.copy(showBroadcastSheet = true)
    }

    fun closeBroadcastSheet() {
        _uiState.value = _uiState.value.copy(showBroadcastSheet = false, broadcastMessage = "")
    }

    fun onBroadcastMessageChange(msg: String) {
        _uiState.value = _uiState.value.copy(broadcastMessage = msg)
    }

    fun sendBroadcast() {
        _uiState.value = _uiState.value.copy(
            showBroadcastSheet = false,
            broadcastMessage = "",
            showSnackbar = true,
            snackbarMessage = "Update broadcast to mesh"
        )
    }

    fun clearSnackbar() {
        _uiState.value = _uiState.value.copy(showSnackbar = false, snackbarMessage = "")
    }
}