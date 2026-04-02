package com.meshrelief.features.bulletin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── Data models ──────────────────────────────────────────────────────────────

enum class BulletinCategory(val label: String) {
    EVACUATION("Evacuation"),
    MEDICAL("Medical"),
    RESOURCES("Resources"),
    GENERAL("General")
}

data class BulletinItem(
    val id: String,
    val senderName: String,
    val category: BulletinCategory,
    val message: String,
    val timestampMillis: Long,
    val isRelayed: Boolean          // true = received over mesh, false = posted locally
)

data class BulletinUiState(
    val bulletins: List<BulletinItem> = emptyList(),
    val selectedFilter: BulletinCategory? = null,   // null = All
    val isSheetOpen: Boolean = false,
    val composeCategory: BulletinCategory = BulletinCategory.GENERAL,
    val composeText: String = ""
)

// ─── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class BulletinViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(BulletinUiState())
    val uiState: StateFlow<BulletinUiState> = _uiState.asStateFlow()

    private val now = System.currentTimeMillis()
    private fun minsAgo(m: Long) = now - m * 60_000L

    init {
        loadSeedData()
    }

    private fun loadSeedData() {
        val seed = listOf(
            BulletinItem(
                id = "b1",
                senderName = "Admin – Relief Team",
                category = BulletinCategory.EVACUATION,
                message = "Dharavi Zone 4 residents: evacuate immediately via LBS Road. Flooding expected within 2 hours. Move to Camp B (Community Hall).",
                timestampMillis = minsAgo(3),
                isRelayed = false
            ),
            BulletinItem(
                id = "b2",
                senderName = "Ravi Kumar",
                category = BulletinCategory.MEDICAL,
                message = "Insulin and BP medication urgently needed near Station Road. Please relay to any medic or first-aid camp.",
                timestampMillis = minsAgo(11),
                isRelayed = true
            ),
            BulletinItem(
                id = "b3",
                senderName = "Priya Nair",
                category = BulletinCategory.RESOURCES,
                message = "Water packets available at Temple Square — enough for ~80 people. First come first served. Bring your own containers if possible.",
                timestampMillis = minsAgo(18),
                isRelayed = true
            ),
            BulletinItem(
                id = "b4",
                senderName = "Admin – Relief Team",
                category = BulletinCategory.EVACUATION,
                message = "North bridge is structurally unsafe. Do NOT cross under any circumstances. Use the southern flyover instead.",
                timestampMillis = minsAgo(25),
                isRelayed = false
            ),
            BulletinItem(
                id = "b5",
                senderName = "Sameer Patil",
                category = BulletinCategory.GENERAL,
                message = "Generator running at Block 7. Phone charging available 6–8 PM daily. Bring your own cable.",
                timestampMillis = minsAgo(47),
                isRelayed = true
            ),
            BulletinItem(
                id = "b6",
                senderName = "Fatima Sheikh",
                category = BulletinCategory.MEDICAL,
                message = "Two trained nurses at Camp A (School Ground). Minor injuries and wound dressing – no appointment needed. Waiting time ~10 mins.",
                timestampMillis = minsAgo(62),
                isRelayed = true
            )
        )
        _uiState.update { it.copy(bulletins = seed) }
    }

    // ── Filter ────────────────────────────────────────────────────────────────

    fun setFilter(category: BulletinCategory?) {
        _uiState.update { it.copy(selectedFilter = category) }
    }

    fun filteredBulletins(): List<BulletinItem> {
        val state = _uiState.value
        val base = if (state.selectedFilter == null) {
            state.bulletins
        } else {
            state.bulletins.filter { it.category == state.selectedFilter }
        }
        // EVACUATION items pinned to top
        return base.sortedWith(
            compareByDescending<BulletinItem> { it.category == BulletinCategory.EVACUATION }
                .thenByDescending { it.timestampMillis }
        )
    }

    // ── Compose sheet ─────────────────────────────────────────────────────────

    fun openSheet() = _uiState.update { it.copy(isSheetOpen = true) }

    fun closeSheet() = _uiState.update {
        it.copy(isSheetOpen = false, composeText = "", composeCategory = BulletinCategory.GENERAL)
    }

    fun setComposeCategory(cat: BulletinCategory) = _uiState.update { it.copy(composeCategory = cat) }

    fun setComposeText(text: String) {
        if (text.length <= 280) _uiState.update { it.copy(composeText = text) }
    }

    fun broadcast() {
        val state = _uiState.value
        val trimmed = state.composeText.trim()
        if (trimmed.isBlank()) return

        val newItem = BulletinItem(
            id = "b${System.currentTimeMillis()}",
            senderName = "You",
            category = state.composeCategory,
            message = trimmed,
            timestampMillis = System.currentTimeMillis(),
            isRelayed = false
        )

        viewModelScope.launch {
            _uiState.update { it.copy(bulletins = listOf(newItem) + it.bulletins) }
            closeSheet()
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    fun relativeTime(millis: Long): String {
        val diff = System.currentTimeMillis() - millis
        val mins = (diff / 60_000).toInt()
        return when {
            mins < 1 -> "just now"
            mins == 1 -> "1 min ago"
            mins < 60 -> "$mins mins ago"
            mins < 120 -> "1 hr ago"
            else -> "${mins / 60} hrs ago"
        }
    }
}