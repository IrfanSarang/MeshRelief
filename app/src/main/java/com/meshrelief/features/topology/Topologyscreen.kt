package com.meshrelief.features.topology

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import com.meshrelief.features.home.MeshAmber
import com.meshrelief.features.home.MeshDark
import com.meshrelief.features.home.MeshGray
import com.meshrelief.features.home.MeshGreen
import com.meshrelief.features.home.MeshGreenDark
import com.meshrelief.features.home.MeshGreenLight
import com.meshrelief.features.home.MeshMid
import com.meshrelief.features.home.MeshRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopologyScreen(
    onBack: () -> Unit,
    viewModel: TopologyViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Canvas transform state
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(0.4f, 4f)
        offset += panChange
    }

    // Pulse animation for "ME" node
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = EaseOutCubic),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseRadius"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = EaseOutCubic),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    val textMeasurer = rememberTextMeasurer()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Hub,
                            contentDescription = null,
                            tint = MeshGreen,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Mesh Network",
                            fontWeight = FontWeight.Bold,
                            color = MeshDark
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MeshDark)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.refresh() },
                containerColor = MeshGreen,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                if (uiState.isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh scan")
                }
            }
        },
        containerColor = MeshGray
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Stats bar
            StatsBar(uiState)

            // Canvas area
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .transformable(state = transformableState)
                ) {
                    val canvasW = size.width
                    val canvasH = size.height

                    withTransform({
                        translate(offset.x, offset.y)
                        scale(scale, scale, pivot = Offset(canvasW / 2f, canvasH / 2f))
                    }) {
                        // Draw background subtle grid
                        drawGrid(canvasW, canvasH)

                        // Map normalised node positions to canvas coords
                        val nodePositions = uiState.nodes.associateBy(
                            { it.id },
                            { Offset(it.x * canvasW, it.y * canvasH) }
                        )

                        // Draw edges first (under nodes)
                        uiState.edges.forEach { edge ->
                            val from = nodePositions[edge.fromId] ?: return@forEach
                            val to = nodePositions[edge.toId] ?: return@forEach
                            drawEdge(from, to, edge)
                        }

                        // Draw nodes
                        uiState.nodes.forEach { node ->
                            val pos = nodePositions[node.id] ?: return@forEach
                            val nodeColor = triageColor(node.triage)
                            val radius = if (node.isSelf) 28f else 20f

                            // Pulse ring for self
                            if (node.isSelf) {
                                drawCircle(
                                    color = MeshGreen.copy(alpha = pulseAlpha),
                                    radius = radius + 14f + pulseRadius * 28f,
                                    center = pos,
                                    style = Stroke(width = 3f)
                                )
                            }

                            // Shadow
                            drawCircle(
                                color = Color.Black.copy(alpha = 0.12f),
                                radius = radius + 3f,
                                center = pos + Offset(2f, 3f)
                            )

                            // Node fill
                            drawCircle(color = nodeColor, radius = radius, center = pos)

                            // White border
                            drawCircle(
                                color = Color.White,
                                radius = radius,
                                center = pos,
                                style = Stroke(width = 3f)
                            )

                            // Node label (name) — below circle
                            val nameLayout = textMeasurer.measure(
                                text = node.name,
                                style = TextStyle(
                                    color = MeshDark,
                                    fontSize = if (node.isSelf) 11.sp else 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            drawText(
                                textLayoutResult = nameLayout,
                                topLeft = Offset(
                                    pos.x - nameLayout.size.width / 2f,
                                    pos.y + radius + 5f
                                )
                            )

                            // Hop badge — above circle (skip for self)
                            if (!node.isSelf) {
                                val hopText = "H${node.hopCount}"
                                val hopLayout = textMeasurer.measure(
                                    text = hopText,
                                    style = TextStyle(
                                        color = Color.White,
                                        fontSize = 7.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                val badgePadH = 6f
                                val badgePadV = 3f
                                val badgeW = hopLayout.size.width + badgePadH * 2
                                val badgeH = hopLayout.size.height + badgePadV * 2
                                val badgeTop = pos.y - radius - badgeH - 3f
                                val badgeLeft = pos.x - badgeW / 2f

                                drawRoundRect(
                                    color = MeshGreenDark,
                                    topLeft = Offset(badgeLeft, badgeTop),
                                    size = androidx.compose.ui.geometry.Size(badgeW, badgeH),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f)
                                )
                                drawText(
                                    textLayoutResult = hopLayout,
                                    topLeft = Offset(badgeLeft + badgePadH, badgeTop + badgePadV)
                                )
                            }
                        }
                    }
                }

                // Legend overlay (bottom-left)
                LegendOverlay(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────
// Sub-composables
// ─────────────────────────────────────────────

@Composable
private fun StatsBar(uiState: TopologyUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatChip(label = "Peers", value = uiState.totalPeers.toString(), modifier = Modifier.weight(1f))
        StatChip(label = "Direct", value = uiState.directConnections.toString(), modifier = Modifier.weight(1f))
        StatChip(label = "Max Hops", value = uiState.maxHopCount.toString(), modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StatChip(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MeshGreenLight)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = MeshGreenDark)
        Text(label, fontSize = 11.sp, color = MeshMid)
    }
}

@Composable
private fun LegendOverlay(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.92f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text("Legend", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MeshDark)
        LegendDot(color = MeshGreen, label = "Safe")
        LegendDot(color = MeshAmber, label = "Injured")
        LegendDot(color = MeshRed, label = "Critical")
        LegendDot(color = MeshMid, label = "Unknown")
        Spacer(Modifier.height(2.dp))
        LegendLine(dashed = false, label = "Direct link")
        LegendLine(dashed = true, label = "Relayed link")
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 9.sp, color = MeshDark)
    }
}

@Composable
private fun LegendLine(dashed: Boolean, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(width = 22.dp, height = 10.dp)) {
            val color = if (dashed) MeshMid else MeshGreenDark
            if (dashed) {
                val dashLen = 4.dp.toPx()
                val gapLen = 3.dp.toPx()
                var x = 0f
                val y = size.height / 2f
                while (x < size.width) {
                    drawLine(color, Offset(x, y), Offset((x + dashLen).coerceAtMost(size.width), y), strokeWidth = 2f)
                    x += dashLen + gapLen
                }
            } else {
                drawLine(color, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), strokeWidth = 2.5f)
            }
        }
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 9.sp, color = MeshDark)
    }
}

// ─────────────────────────────────────────────
// DrawScope helpers
// ─────────────────────────────────────────────

private fun DrawScope.drawGrid(w: Float, h: Float) {
    val step = 60f
    val gridColor = Color(0xFFDDDDD5)
    var x = 0f
    while (x <= w) {
        drawLine(gridColor, Offset(x, 0f), Offset(x, h), strokeWidth = 0.8f)
        x += step
    }
    var y = 0f
    while (y <= h) {
        drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 0.8f)
        y += step
    }
}

private fun DrawScope.drawEdge(from: Offset, to: Offset, edge: TopologyEdge) {
    val strokeW = (edge.signalStrength * 5f + 1.5f)
    val color = if (edge.isDirect) MeshGreen.copy(alpha = 0.75f) else MeshMid.copy(alpha = 0.55f)

    if (edge.isDirect) {
        drawLine(color = color, start = from, end = to, strokeWidth = strokeW)
    } else {
        // Dashed line via path effect approximation (manual segments)
        val dx = to.x - from.x
        val dy = to.y - from.y
        val dist = kotlin.math.sqrt(dx * dx + dy * dy)
        val dashLen = 14f
        val gapLen = 8f
        val ux = dx / dist
        val uy = dy / dist
        var t = 0f
        while (t < dist) {
            val segStart = Offset(from.x + ux * t, from.y + uy * t)
            val segEnd = Offset(from.x + ux * (t + dashLen).coerceAtMost(dist), from.y + uy * (t + dashLen).coerceAtMost(dist))
            drawLine(color = color, start = segStart, end = segEnd, strokeWidth = strokeW)
            t += dashLen + gapLen
        }
    }
}

private fun triageColor(triage: TriageStatus): Color = when (triage) {
    TriageStatus.CRITICAL -> MeshRed
    TriageStatus.INJURED  -> MeshAmber
    TriageStatus.SAFE     -> MeshGreen
    TriageStatus.UNKNOWN  -> MeshMid
}