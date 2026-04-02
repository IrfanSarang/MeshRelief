package com.meshrelief.features.topology

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

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
class TopologyViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(TopologyUiState())
    val uiState: StateFlow<TopologyUiState> = _uiState.asStateFlow()

    init {
        generateTopology()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            kotlinx.coroutines.delay(600)
            generateTopology()
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    private fun generateTopology() {
        val rng = Random(System.currentTimeMillis())

        // Place nodes in a rough circular layout with randomised jitter
        val rawNodes = mutableListOf<TopologyNode>()
        val selfNode = TopologyNode(
            id = "self",
            name = "ME",
            triage = TriageStatus.SAFE,
            hopCount = 0,
            isSelf = true,
            x = 0.5f,
            y = 0.5f
        )
        rawNodes.add(selfNode)

        val peerData = listOf(
            Triple("ARJUN",  TriageStatus.SAFE,     1),
            Triple("PRIYA",  TriageStatus.INJURED,  1),
            Triple("RAHUL",  TriageStatus.CRITICAL, 2),
            Triple("SNEHA",  TriageStatus.SAFE,     1),
            Triple("VIKRAM", TriageStatus.UNKNOWN,  3),
            Triple("MEENA",  TriageStatus.INJURED,  2),
            Triple("ROHAN",  TriageStatus.SAFE,     3)
        )

        peerData.forEachIndexed { i, (name, triage, hop) ->
            val angle = (2 * Math.PI * i / peerData.size).toFloat()
            val radius = if (hop == 1) 0.22f else if (hop == 2) 0.33f else 0.42f
            val jitterX = rng.nextFloat() * 0.07f - 0.035f
            val jitterY = rng.nextFloat() * 0.07f - 0.035f
            rawNodes.add(
                TopologyNode(
                    id = "peer_$i",
                    name = name,
                    triage = triage,
                    hopCount = hop,
                    isSelf = false,
                    x = (0.5f + cos(angle) * radius + jitterX).coerceIn(0.08f, 0.92f),
                    y = (0.5f + sin(angle) * radius + jitterY).coerceIn(0.08f, 0.92f)
                )
            )
        }

        // Build edges
        val edges = mutableListOf<TopologyEdge>()

        // Self → direct peers (hop 1)
        rawNodes.filter { it.hopCount == 1 }.forEach { peer ->
            edges.add(
                TopologyEdge(
                    fromId = "self",
                    toId = peer.id,
                    signalStrength = rng.nextFloat() * 0.5f + 0.5f,
                    isDirect = true
                )
            )
        }

        // Hop-2 peers connect to a hop-1 peer relay
        val hop1Ids = rawNodes.filter { it.hopCount == 1 }.map { it.id }
        rawNodes.filter { it.hopCount == 2 }.forEach { peer ->
            val relay = hop1Ids.random(rng)
            edges.add(
                TopologyEdge(
                    fromId = relay,
                    toId = peer.id,
                    signalStrength = rng.nextFloat() * 0.4f + 0.2f,
                    isDirect = false
                )
            )
        }

        // Hop-3 peers connect to a hop-2 peer
        val hop2Ids = rawNodes.filter { it.hopCount == 2 }.map { it.id }
        rawNodes.filter { it.hopCount == 3 }.forEach { peer ->
            val relay = hop2Ids.random(rng)
            edges.add(
                TopologyEdge(
                    fromId = relay,
                    toId = peer.id,
                    signalStrength = rng.nextFloat() * 0.3f + 0.1f,
                    isDirect = false
                )
            )
        }

        _uiState.update { TopologyUiState(nodes = rawNodes, edges = edges) }
    }
}