package com.meshrelief.features.topology

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshrelief.data.repository.PeerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.cos
import kotlin.math.sin

enum class TriageStatus { CRITICAL, INJURED, SAFE, UNKNOWN }

data class TopologyNode(
    val id: String,
    val name: String,
    val triage: TriageStatus,
    val hopCount: Int,       // 0 = this device
    val isSelf: Boolean = false,
    // Normalized positions [0..1] — mapped to canvas at render time
    val x: Float,
    val y: Float
)

data class TopologyEdge(
    val fromId: String,
    val toId: String,
    val signalStrength: Float,   // 0f..1f  (1 = strongest)
    val isDirect: Boolean        // false = relayed (hop > 1)
)

data class TopologyUiState(
    val nodes: List<TopologyNode> = emptyList(),
    val edges: List<TopologyEdge> = emptyList(),
    val isRefreshing: Boolean = false
) {
    val totalPeers: Int get() = nodes.count { !it.isSelf }
    val directConnections: Int get() = edges.count { it.isDirect }
    val maxHopCount: Int get() = nodes.maxOfOrNull { it.hopCount } ?: 0
}

@HiltViewModel
class TopologyViewModel @Inject constructor(
    private val peerRepository: PeerRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TopologyUiState())
    val uiState: StateFlow<TopologyUiState> = _uiState.asStateFlow()

    init {
        observePeers()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            // Brief visual feedback; the Flow will push updated data automatically
            kotlinx.coroutines.delay(400)
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    // ── MISSING 11 FIX ───────────────────────────────────────────────────
    // Collect real peer rows from the database and build the topology graph.
    // hopCount on each PeerEntity is now kept current by PeerRepository which
    // calls PeerDao.updateHopCount() whenever AppEventBus.peerHopCount fires.
    private fun observePeers() {
        viewModelScope.launch {
            peerRepository.getAllPeers().collectLatest { peers ->
                val nodes = mutableListOf<TopologyNode>()

                // Self node always sits at the centre
                nodes.add(
                    TopologyNode(
                        id       = "self",
                        name     = "ME",
                        triage   = TriageStatus.SAFE,
                        hopCount = 0,
                        isSelf   = true,
                        x        = 0.5f,
                        y        = 0.5f
                    )
                )

                // Place peers in concentric rings by hop distance
                val byHop = peers.groupBy { it.hopCount.coerceAtLeast(1) }
                byHop.forEach { (hop, group) ->
                    val radius = when {
                        hop == 1 -> 0.22f
                        hop == 2 -> 0.33f
                        else     -> 0.40f + (hop - 3) * 0.06f
                    }.coerceAtMost(0.44f)

                    group.forEachIndexed { idx, peer ->
                        val angle = (2.0 * Math.PI * idx / group.size).toFloat()
                        nodes.add(
                            TopologyNode(
                                id       = peer.deviceId,
                                name     = peer.name.take(6).uppercase(),
                                triage   = triageFromString(peer.triageStatus),
                                hopCount = peer.hopCount,
                                isSelf   = false,
                                x        = (0.5f + cos(angle) * radius).coerceIn(0.08f, 0.92f),
                                y        = (0.5f + sin(angle) * radius).coerceIn(0.08f, 0.92f)
                            )
                        )
                    }
                }

                // Build edges — each peer connects to the closest known relay
                // (the peer with hopCount = this.hopCount - 1 closest in angle)
                val edges = mutableListOf<TopologyEdge>()
                val nodeById = nodes.associateBy { it.id }

                nodes.filter { !it.isSelf }.forEach { peer ->
                    val parentHop = peer.hopCount - 1
                    val parentId = if (parentHop == 0) {
                        "self"
                    } else {
                        // Find the node at parentHop whose angle is nearest
                        nodes
                            .filter { !it.isSelf && it.hopCount == parentHop }
                            .minByOrNull { relay ->
                                val dx = relay.x - peer.x
                                val dy = relay.y - peer.y
                                dx * dx + dy * dy
                            }?.id
                    }

                    if (parentId != null && nodeById.containsKey(parentId)) {
                        edges.add(
                            TopologyEdge(
                                fromId          = parentId,
                                toId            = peer.id,
                                // Use battery as a rough signal-quality proxy (0..100 → 0f..1f)
                                signalStrength  = (peers.find { it.deviceId == peer.id }
                                    ?.battery ?: 50) / 100f,
                                isDirect        = peer.hopCount == 1
                            )
                        )
                    }
                }

                _uiState.update { TopologyUiState(nodes = nodes, edges = edges) }
            }
        }
    }

    private fun triageFromString(status: String): TriageStatus = when (status.uppercase()) {
        "CRITICAL" -> TriageStatus.CRITICAL
        "INJURED"  -> TriageStatus.INJURED
        "SAFE"     -> TriageStatus.SAFE
        else       -> TriageStatus.UNKNOWN
    }
}