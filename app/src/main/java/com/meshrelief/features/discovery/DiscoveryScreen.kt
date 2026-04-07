package com.meshrelief.features.discovery

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.meshrelief.core.model.TriageLevel
import com.meshrelief.features.home.MeshAmber
import com.meshrelief.features.home.MeshDark
import com.meshrelief.features.home.MeshGray
import com.meshrelief.features.home.MeshGreen
import com.meshrelief.features.home.MeshGreenDark
import com.meshrelief.features.home.MeshGreenLight
import com.meshrelief.features.home.MeshMid
import com.meshrelief.features.home.MeshRed
import kotlin.math.cos
import kotlin.math.sin

// ---------------------------------------------------------------------------
// Screen entry point
// ---------------------------------------------------------------------------

@Composable
fun DiscoveryScreen(
    onBack: () -> Unit,
    viewModel: DiscoveryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = MeshGray,
        topBar = {
            DiscoveryTopBar(
                peerCount = uiState.peers.size,
                sortMode = uiState.sortMode,
                onBack = onBack,
                onSortChange = viewModel::setSortMode
            )
        }
    ) { padding ->
        if (uiState.scanState == ScanState.IDLE && uiState.peers.isEmpty()) {
            EmptyState(
                modifier = Modifier.padding(padding),
                onRescan = viewModel::startScan
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Scan state banner
                item {
                    ScanStateBanner(
                        scanState = uiState.scanState,
                        lastScanMinutesAgo = uiState.lastScanMinutesAgo,
                        onRescan = viewModel::startScan
                    )
                }

                // Radar widget
                item {
                    RadarWidget(peers = uiState.peers)
                }

                // Section header
                if (uiState.peers.isNotEmpty()) {
                    item {
                        Text(
                            text = "NEARBY PEERS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MeshMid,
                            letterSpacing = 0.08.sp,
                            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                        )
                    }
                }

                // Peer cards
                items(uiState.peers, key = { it.id }) { peer ->
                    PeerCard(
                        peer = peer,
                        onConnect = { viewModel.connectToPeer(peer.id) }
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Top bar
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiscoveryTopBar(
    peerCount: Int,
    sortMode: SortMode,
    onBack: () -> Unit,
    onSortChange: (SortMode) -> Unit
) {
    Surface(color = Color.White, shadowElevation = 0.dp) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back button
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MeshGray)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = MeshMid,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Peer Discovery",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MeshDark
                    )
                    Text(
                        text = if (peerCount == 0) "Searching…" else "$peerCount peers visible",
                        fontSize = 11.sp,
                        color = MeshMid
                    )
                }

                // Sort segmented control
                SortToggle(current = sortMode, onChange = onSortChange)
            }

            Divider(color = Color(0xFFEEEEEE), thickness = 0.5.dp)
        }
    }
}

// ---------------------------------------------------------------------------
// Sort toggle
// ---------------------------------------------------------------------------

@Composable
private fun SortToggle(current: SortMode, onChange: (SortMode) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MeshGray)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        SortMode.entries.forEach { mode ->
            val selected = current == mode
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (selected) MeshGreen else Color.Transparent)
                    .clickable { onChange(mode) }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = mode.name.lowercase().replaceFirstChar { it.uppercase() },
                    fontSize = 10.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) Color.White else MeshMid
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Scan state banner
// ---------------------------------------------------------------------------

@Composable
private fun ScanStateBanner(
    scanState: ScanState,
    lastScanMinutesAgo: Int,
    onRescan: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "scan_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            tween(700, easing = LinearEasing), RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (scanState == ScanState.SCANNING) {
            // Pulsing green dot
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(MeshGreen.copy(alpha = pulseAlpha))
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Scanning for peers…",
                fontSize = 12.sp,
                color = MeshGreenDark,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(MeshMid)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (lastScanMinutesAgo == 0) "Last scan: just now"
                else "Last scan: $lastScanMinutesAgo min ago",
                fontSize = 12.sp,
                color = MeshMid,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MeshGreenLight)
                    .clickable(onClick = onRescan)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "Rescan",
                    fontSize = 11.sp,
                    color = MeshGreenDark,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Radar / sonar animation
// ---------------------------------------------------------------------------

@Composable
private fun RadarWidget(peers: List<DiscoveredPeer>) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val sweepProgress by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2500, easing = LinearEasing)),
        label = "sweep"
    )
    // Ring expansion: 3 offset rings
    val ring1 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearOutSlowInEasing)),
        label = "ring1"
    )
    val ring2 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, 600, LinearOutSlowInEasing)),
        label = "ring2"
    )
    val ring3 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, 1200, LinearOutSlowInEasing)),
        label = "ring3"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "MESH RADAR",
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = MeshMid,
                letterSpacing = 0.08.sp
            )
            Spacer(Modifier.height(6.dp))
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
            ) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val maxR = minOf(cx, cy) * 0.92f

                // Static grid rings
                for (i in 1..4) {
                    drawCircle(
                        color = MeshGreen.copy(alpha = 0.08f),
                        radius = maxR * (i / 4f),
                        center = Offset(cx, cy),
                        style = Stroke(1f)
                    )
                }

                // Expanding rings
                fun drawExpandingRing(progress: Float) {
                    val r = maxR * progress
                    drawCircle(
                        color = MeshGreen.copy(alpha = (1f - progress) * 0.35f),
                        radius = r,
                        center = Offset(cx, cy),
                        style = Stroke(2f)
                    )
                }
                drawExpandingRing(ring1)
                drawExpandingRing(ring2)
                drawExpandingRing(ring3)

                // Cross-hairs
                drawLine(MeshGreen.copy(alpha = 0.12f), Offset(cx, cy - maxR), Offset(cx, cy + maxR), 1f)
                drawLine(MeshGreen.copy(alpha = 0.12f), Offset(cx - maxR, cy), Offset(cx + maxR, cy), 1f)

                // Rotating sweep arc
                val sweepAngleDeg = sweepProgress * 360f
                drawArc(
                    color = MeshGreen.copy(alpha = 0.18f),
                    startAngle = sweepAngleDeg - 60f,
                    sweepAngle = 60f,
                    useCenter = true,
                    topLeft = Offset(cx - maxR, cy - maxR),
                    size = androidx.compose.ui.geometry.Size(maxR * 2, maxR * 2)
                )

                // Centre dot
                drawCircle(MeshGreen, 5f, Offset(cx, cy))

                // Peer dots — evenly distributed angles, distance ∝ signal
                peers.forEachIndexed { index, peer ->
                    val angleRad = (index.toFloat() / peers.size.coerceAtLeast(1)) * 2 * Math.PI
                    val dist = maxR * (1f - peer.signalBars / 5f).coerceIn(0.15f, 0.85f)
                    val px = cx + (dist * cos(angleRad)).toFloat()
                    val py = cy + (dist * sin(angleRad)).toFloat()
                    val dotColor = triageColor(peer.triage)
                    drawCircle(dotColor.copy(alpha = 0.25f), 10f, Offset(px, py))
                    drawCircle(dotColor, 5f, Offset(px, py))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Peer card
// ---------------------------------------------------------------------------

@Composable
private fun PeerCard(peer: DiscoveredPeer, onConnect: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "connecting_${peer.id}")
    val connectingAlpha by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse),
        label = "conn_alpha"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar circle with initials
            val initials = peer.displayName
                .split(" ")
                .filter { it.isNotEmpty() }
                .take(2)
                .joinToString("") { it.first().uppercase() }
            val avatarBg = triageColor(peer.triage)

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(avatarBg.copy(alpha = 0.15f))
                    .border(1.5.dp, avatarBg, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = avatarBg
                )
            }

            Spacer(Modifier.width(12.dp))

            // Middle info
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = peer.displayName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MeshDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "••••${peer.deviceIdSuffix}",
                        fontSize = 10.sp,
                        color = MeshMid
                    )
                }

                Spacer(Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Triage badge
                    TriageBadge(peer.triage)

                    // Hop count
                    Text(
                        text = if (peer.hopCount == 0) "Direct" else "${peer.hopCount} hops",
                        fontSize = 10.sp,
                        color = MeshMid
                    )
                }

                Spacer(Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Signal bars
                    SignalBars(bars = peer.signalBars)

                    // Connection chip
                    ConnectionChip(
                        state = peer.connectionState,
                        connectingAlpha = connectingAlpha
                    )
                }
            }

            // Connect button — only for AVAILABLE peers
            if (peer.connectionState == ConnectionState.AVAILABLE) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MeshGreen)
                        .clickable(onClick = onConnect)
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = "Connect",
                        fontSize = 11.sp,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Triage badge
// ---------------------------------------------------------------------------

@Composable
private fun TriageBadge(triage: TriageLevel) {
    val (label, bg, fg) = when (triage) {
        TriageLevel.SAFE     -> Triple("Safe",     Color(0xFFE1F5EE), Color(0xFF085041))
        TriageLevel.MINOR    -> Triple("Minor",    Color(0xFFFAEEDA), Color(0xFF854F0B))
        TriageLevel.CRITICAL -> Triple("Critical", Color(0xFFFCEBEB), Color(0xFFA32D2D))
        TriageLevel.UNKNOWN  -> Triple("Unknown",  Color(0xFFF1EFE8), Color(0xFF5F5E5A))
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(triageColor(triage))
        )
        Text(text = label, fontSize = 9.sp, color = fg, fontWeight = FontWeight.Medium)
    }
}

// ---------------------------------------------------------------------------
// Signal bars — 4-bar Canvas widget
// ---------------------------------------------------------------------------

@Composable
private fun SignalBars(bars: Int) {
    Canvas(
        modifier = Modifier
            .width(24.dp)
            .height(14.dp)
    ) {
        val barCount = 4
        val barWidth = size.width / (barCount * 2f - 1f)
        val gap = barWidth

        for (i in 0 until barCount) {
            val barH = size.height * ((i + 1) / barCount.toFloat())
            val x = i * (barWidth + gap)
            val y = size.height - barH
            drawRect(
                color = if (i < bars) MeshGreen else Color(0xFFD3D1C7),
                topLeft = Offset(x, y),
                size = androidx.compose.ui.geometry.Size(barWidth, barH)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Connection state chip
// ---------------------------------------------------------------------------

@Composable
private fun ConnectionChip(state: ConnectionState, connectingAlpha: Float) {
    val (label, bg, fg, border) = when (state) {
        ConnectionState.CONNECTED  -> Quad("Connected",  MeshGreenLight, MeshGreenDark, Color.Transparent)
        ConnectionState.CONNECTING -> Quad("Connecting", Color.Transparent, MeshAmber, MeshAmber)
        ConnectionState.AVAILABLE  -> Quad("Available",  Color.Transparent, MeshMid, Color(0xFFD3D1C7))
    }

    val effectiveAlpha = if (state == ConnectionState.CONNECTING) connectingAlpha else 1f

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .then(
                if (border != Color.Transparent)
                    Modifier.border(0.8.dp, border.copy(alpha = effectiveAlpha), RoundedCornerShape(20.dp))
                else Modifier
            )
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            color = fg.copy(alpha = effectiveAlpha),
            fontWeight = FontWeight.Medium
        )
    }
}

private data class Quad(val a: String, val b: Color, val c: Color, val d: Color)

// ---------------------------------------------------------------------------
// Empty state
// ---------------------------------------------------------------------------

@Composable
private fun EmptyState(modifier: Modifier = Modifier, onRescan: () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Canvas illustration: device + wifi arcs
        Canvas(modifier = Modifier.size(80.dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f

            // Device body
            drawRoundRect(
                color = MeshGray,
                topLeft = Offset(cx - 18f, cy - 20f),
                size = androidx.compose.ui.geometry.Size(36f, 44f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f),
                style = Stroke(3f)
            )
            // Screen
            drawRoundRect(
                color = MeshGreenLight,
                topLeft = Offset(cx - 12f, cy - 14f),
                size = androidx.compose.ui.geometry.Size(24f, 22f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f)
            )

            // WiFi arcs above device
            val arcY = cy - 34f
            for (i in 1..3) {
                drawArc(
                    color = MeshGreen.copy(alpha = 0.5f / i),
                    startAngle = 200f,
                    sweepAngle = -40f,
                    useCenter = false,
                    topLeft = Offset(cx - 8f * i, arcY - 8f * i),
                    size = androidx.compose.ui.geometry.Size(16f * i, 16f * i),
                    style = Stroke(2.5f)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "No peers found",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MeshDark
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Make sure WiFi Direct is enabled\nand nearby devices are running MeshRelief.",
            fontSize = 12.sp,
            color = MeshMid,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 18.sp
        )
        Spacer(Modifier.height(20.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MeshGreen)
                .clickable(onClick = onRescan)
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Rescan",
                fontSize = 14.sp,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun triageColor(triage: TriageLevel): Color = when (triage) {
    TriageLevel.SAFE     -> MeshGreen
    TriageLevel.MINOR    -> MeshAmber
    TriageLevel.CRITICAL -> MeshRed
    TriageLevel.UNKNOWN  -> MeshMid
}