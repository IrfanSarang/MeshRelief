package com.meshrelief.features.notifications

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

// ── Notification Types ────────────────────────────────────────────────────────

enum class NotificationType {
    SOS, PEER, BULLETIN, CAMP, SYSTEM
}

// ── Data Model ────────────────────────────────────────────────────────────────

data class MeshNotification(
    val id: String,
    val type: NotificationType,
    val title: String,
    val body: String,
    val timestampMillis: Long,
    val isRead: Boolean = false
)

// ── Filter Tabs ───────────────────────────────────────────────────────────────

enum class NotificationFilter(val label: String) {
    ALL("All"),
    SOS("SOS"),
    PEERS("Peers"),
    BULLETINS("Bulletins"),
    CAMPS("Camps"),
    SYSTEM("System")
}

// ── UI State ──────────────────────────────────────────────────────────────────

data class NotificationsUiState(
    val notifications: List<MeshNotification> = emptyList(),
    val activeFilter: NotificationFilter = NotificationFilter.ALL
) {
    val filtered: List<MeshNotification>
        get() = when (activeFilter) {
            NotificationFilter.ALL       -> notifications
            NotificationFilter.SOS       -> notifications.filter { it.type == NotificationType.SOS }
            NotificationFilter.PEERS     -> notifications.filter { it.type == NotificationType.PEER }
            NotificationFilter.BULLETINS -> notifications.filter { it.type == NotificationType.BULLETIN }
            NotificationFilter.CAMPS     -> notifications.filter { it.type == NotificationType.CAMP }
            NotificationFilter.SYSTEM    -> notifications.filter { it.type == NotificationType.SYSTEM }
        }

    val unreadCount: Int
        get() = notifications.count { !it.isRead }
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class NotificationsViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(
        NotificationsUiState(notifications = seedNotifications())
    )
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    fun setFilter(filter: NotificationFilter) {
        _uiState.update { it.copy(activeFilter = filter) }
    }

    fun markAsRead(id: String) {
        _uiState.update { state ->
            state.copy(
                notifications = state.notifications.map { n ->
                    if (n.id == id) n.copy(isRead = true) else n
                }
            )
        }
    }

    fun markAllRead() {
        _uiState.update { state ->
            state.copy(notifications = state.notifications.map { it.copy(isRead = true) })
        }
    }

    fun dismiss(id: String) {
        _uiState.update { state ->
            state.copy(notifications = state.notifications.filter { it.id != id })
        }
    }

    fun clearFiltered() {
        _uiState.update { state ->
            val idsToRemove = state.filtered.map { it.id }.toSet()
            state.copy(notifications = state.notifications.filter { it.id !in idsToRemove })
        }
    }

    // ── Seed Data ─────────────────────────────────────────────────────────────

    private fun seedNotifications(): List<MeshNotification> {
        val now = System.currentTimeMillis()
        fun ago(minutes: Long): Long = now - (minutes * 60_000L)

        return listOf(
            MeshNotification(
                id = "n1",
                type = NotificationType.SOS,
                title = "SOS from Rahul Patil",
                body = "Critical injury — bleeding head wound. GPS: 19.0760 N, 72.8777 E. Needs immediate assistance.",
                timestampMillis = ago(2L),
                isRead = false
            ),
            MeshNotification(
                id = "n2",
                type = NotificationType.PEER,
                title = "New peer joined mesh",
                body = "Device \"Riya-Pixel7\" connected via WiFi Direct. Signal: Strong. Relay capable.",
                timestampMillis = ago(5L),
                isRead = false
            ),
            MeshNotification(
                id = "n3",
                type = NotificationType.BULLETIN,
                title = "EVACUATION — Route 4 blocked",
                body = "Admin broadcast: Do NOT use Sion-Panvel Highway. Flooding reported near Turbhe. Use Route 7 via Vashi.",
                timestampMillis = ago(12L),
                isRead = false
            ),
            MeshNotification(
                id = "n4",
                type = NotificationType.CAMP,
                title = "Camp Kurla Relief Base updated",
                body = "Capacity raised to 180. Medical supplies restocked. Water available at Station 3.",
                timestampMillis = ago(20L),
                isRead = true
            ),
            MeshNotification(
                id = "n5",
                type = NotificationType.SOS,
                title = "SOS acknowledged by Admin",
                body = "Your SOS alert has been acknowledged. Help dispatched. Stay at current location.",
                timestampMillis = ago(35L),
                isRead = true
            ),
            MeshNotification(
                id = "n6",
                type = NotificationType.SYSTEM,
                title = "Mesh relay mode activated",
                body = "Your device is now acting as a relay node for 3 peers. Battery drain may increase.",
                timestampMillis = ago(48L),
                isRead = false
            ),
            MeshNotification(
                id = "n7",
                type = NotificationType.PEER,
                title = "Peer disconnected",
                body = "Device \"Amit-Samsung\" dropped off the mesh. Last seen 48 min ago. Signal lost.",
                timestampMillis = ago(60L),
                isRead = true
            ),
            MeshNotification(
                id = "n8",
                type = NotificationType.BULLETIN,
                title = "Medical supply drop — Camp Dharavi",
                body = "First-aid kits, ORS packets, and bandages available. Collect before 6 PM at Gate 2.",
                timestampMillis = ago(90L),
                isRead = true
            ),
            MeshNotification(
                id = "n9",
                type = NotificationType.CAMP,
                title = "Camp Andheri Shelter at capacity",
                body = "Camp has reached 200/200 occupancy. Overflow directed to Goregaon Relief Hub.",
                timestampMillis = ago(130L),
                isRead = true
            ),
            MeshNotification(
                id = "n10",
                type = NotificationType.SYSTEM,
                title = "MeshRelief started offline mode",
                body = "No internet detected. All features operating over WiFi Direct mesh. Peer discovery active.",
                timestampMillis = ago(180L),
                isRead = true
            )
        )
    }

    // ── Relative time helper ──────────────────────────────────────────────────

    companion object {
        fun relativeTime(millis: Long): String {
            val diff = System.currentTimeMillis() - millis
            val mins = diff / 60_000L
            return when {
                mins < 1L    -> "just now"
                mins < 60L   -> "$mins min ago"
                mins < 1440L -> "${mins / 60L} hr ago"
                else         -> "${mins / 1440L} day ago"
            }
        }
    }
}