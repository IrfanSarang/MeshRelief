package com.meshrelief.features.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshrelief.data.preferences.UserPreferences
import com.meshrelief.data.repository.BulletinRepository
import com.meshrelief.data.repository.CampRepository
import com.meshrelief.data.repository.PeerRepository
import com.meshrelief.mesh.wifi.WifiDirectManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val userName: String = "",
    val meshActive: Boolean = false,
    val peersOnline: Int = 0,
    val verifiedPeers: Int = 0,
    val nearbyCamps: Int = 0,
    val openCamps: Int = 0,
    val latestBulletin: BulletinPreview? = null
)

data class BulletinPreview(
    val type: String,
    val content: String,
    val senderName: String,
    val minutesAgo: Int,
    val relayCount: Int
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val peerRepository: PeerRepository,
    private val bulletinRepository: BulletinRepository,
    private val campRepository: CampRepository,
    private val wifiDirectManager: WifiDirectManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    init {
        // Collect userName separately — it's a different preferences flow
        viewModelScope.launch {
            userPreferences.userName.collect { name ->
                _uiState.value = _uiState.value.copy(userName = name)
            }
        }

        // Combine all live data sources into one uiState update
        viewModelScope.launch {
            combine(
                wifiDirectManager.peerList,       // List<WifiP2pDevice>
                peerRepository.getAllPeers(),      // Flow<List<PeerEntity>>
                campRepository.getAllCamps(),      // Flow<List<CampEntity>>
                bulletinRepository.getAllBulletins() // Flow<List<BulletinEntity>>
            ) { wifiPeers, dbPeers, camps, bulletins ->

                val meshActive = wifiPeers.isNotEmpty()
                val peersOnline = wifiPeers.size
                val verifiedPeers = dbPeers.count { it.verified }
                val nearbyCamps = camps.size
                val openCamps = camps.count { it.currentCount < it.capacity }

                val latestBulletin = bulletins
                    .maxByOrNull { it.timestamp }
                    ?.let { b ->
                        val minutesAgo = ((System.currentTimeMillis() - b.timestamp) / 60_000).toInt()
                        BulletinPreview(
                            type = b.type,
                            content = b.content,
                            senderName = b.senderName,
                            minutesAgo = minutesAgo,
                            relayCount = b.relayCount
                        )
                    }

                // Preserve userName — it's managed by its own collector above
                _uiState.value.copy(
                    meshActive = meshActive,
                    peersOnline = peersOnline,
                    verifiedPeers = verifiedPeers,
                    nearbyCamps = nearbyCamps,
                    openCamps = openCamps,
                    latestBulletin = latestBulletin
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    fun onMeshToggle() {
        if (_uiState.value.meshActive) {
            wifiDirectManager.disconnect()
        } else {
            wifiDirectManager.discoverPeers()
        }
    }
}