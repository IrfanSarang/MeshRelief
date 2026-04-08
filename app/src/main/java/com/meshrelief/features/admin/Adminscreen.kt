package com.meshrelief.features.admin

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.meshrelief.core.model.TriageStatus
import com.meshrelief.features.home.MeshAmber
import com.meshrelief.features.home.MeshDark
import com.meshrelief.features.home.MeshGray
import com.meshrelief.features.home.MeshGreen
import com.meshrelief.features.home.MeshGreenDark
import com.meshrelief.features.home.MeshGreenLight
import com.meshrelief.features.home.MeshMid
import com.meshrelief.features.home.MeshRed

// ── Helpers ──────────────────────────────────────────────────────────────────

private fun triageColor(level: TriageStatus): Color = when (level) {
    TriageStatus.GREEN   -> Color(0xFF1D9E75)
    TriageStatus.AMBER   -> Color(0xFFEF9F27)
    TriageStatus.RED     -> Color(0xFFE24B4A)
    TriageStatus.UNKNOWN -> Color(0xFF9E9E9E)
}

private fun triageLabel(level: TriageStatus): String = when (level) {
    TriageStatus.GREEN   -> "GREEN"
    TriageStatus.AMBER   -> "AMBER"
    TriageStatus.RED     -> "RED"
    TriageStatus.UNKNOWN -> "UNKNOWN"
}

private fun lastSeenLabel(minutes: Int): String = when {
    minutes == 0 -> "Just now"
    minutes == 1 -> "1 min ago"
    minutes < 60 -> "$minutes mins ago"
    else         -> "${minutes / 60}h ago"
}

// ── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    onBack: () -> Unit,
    onEvacuationClick: () -> Unit,
    onTopologyClick: () -> Unit,                          // ← FIX #13 added
    viewModel: AdminViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    // Scanning pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "scan_pulse")
    val scanAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scan_alpha"
    )

    Scaffold(
        containerColor = MeshGray,
        topBar = {
            AdminTopBar(
                deviceName = state.adminDeviceName,
                onBack = onBack
            )
        },
        floatingActionButton = {
            AdminFab(
                isScanning = state.isScanning,
                scanAlpha = scanAlpha,
                onClick = { viewModel.scanNetwork() }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            // ── Summary cards ──────────────────────────────────────────────
            item {
                SummarySectionHeader()
            }
            item {
                SummaryCardsRow(
                    peerCount   = state.peers.size,
                    sosActive   = state.sosAlerts.count { !it.acknowledged },
                    campsOnline = state.camps.size,
                    msgsToday   = state.messagesToday
                )
            }

            // ── Network Peers ──────────────────────────────────────────────
            item { SectionHeader(title = "Network Peers", icon = Icons.Default.Wifi) }
            items(state.peers, key = { it.id }) { peer ->
                PeerRow(peer = peer)
            }

            // ── Active SOS Alerts ──────────────────────────────────────────
            item { SectionHeader(title = "Active SOS Alerts", icon = Icons.Default.Warning) }
            if (state.sosAlerts.none { !it.acknowledged }) {
                item { EmptyStateRow("No active SOS alerts") }
            } else {
                items(state.sosAlerts.filter { !it.acknowledged }, key = { it.id }) { alert ->
                    SosAlertCard(alert = alert, onAcknowledge = { viewModel.acknowledgeSos(alert.id) })
                }
            }

            // ── Acknowledged (collapsed view) ──────────────────────────────
            val acked = state.sosAlerts.filter { it.acknowledged }
            if (acked.isNotEmpty()) {
                item {
                    Text(
                        text = "${acked.size} acknowledged alert(s)",
                        color = MeshMid,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }

            // ── Camp Status ────────────────────────────────────────────────
            item { SectionHeader(title = "Camp Status", icon = Icons.Default.Home) }
            items(state.camps, key = { it.id }) { camp ->
                CampStatusCard(camp = camp)
            }

            // ── Broadcast Bulletin ─────────────────────────────────────────
            item { SectionHeader(title = "Broadcast Bulletin", icon = Icons.Default.Campaign) }
            item {
                BulletinComposer(
                    draft = state.bulletinDraft,
                    onDraftChange = viewModel::onBulletinDraftChange,
                    onBroadcast = { viewModel.broadcastBulletin() }
                )
            }
            items(state.bulletins.reversed(), key = { it.id }) { entry ->
                BulletinEntryRow(entry = entry)
            }

            // ── Network Topology ───────────────────────────────────────────  ← FIX #13
            item { SectionHeader(title = "Network Topology", icon = Icons.Default.AccountTree) }
            item {
                AdminNavCard(
                    title       = "View Network Topology",
                    subtitle    = "Visual map of all mesh node connections",
                    icon        = Icons.Default.AccountTree,
                    onClick     = onTopologyClick
                )
            }

            // ── Evacuation Route ───────────────────────────────────────────
            item { SectionHeader(title = "Evacuation Route", icon = Icons.Default.AltRoute) }
            item {
                AdminNavCard(
                    title    = "Draw evacuation route",
                    subtitle = "Tap waypoints on map · broadcasts to all peers",
                    icon     = Icons.Default.AltRoute,
                    onClick  = onEvacuationClick
                )
            }
        }
    }
}

// ── Generic admin nav card (replaces the inline Card blocks) ──────────────────

@Composable
private fun AdminNavCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MeshGreenLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MeshGreen,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    color = MeshDark,
                    fontSize = 14.sp
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = MeshMid
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MeshMid,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ── TopBar ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminTopBar(deviceName: String, onBack: () -> Unit) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MeshGreenDark,
            titleContentColor = Color.White,
            navigationIconContentColor = Color.White,
            actionIconContentColor = Color.White
        ),
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
        },
        title = {
            Column {
                Text(
                    text = "Admin Dashboard",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
                Text(
                    text = deviceName,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.75f)
                )
            }
        },
        actions = {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = "Admin",
                tint = MeshAmber,
                modifier = Modifier
                    .padding(end = 16.dp)
                    .size(26.dp)
            )
        }
    )
}

// ── FAB ───────────────────────────────────────────────────────────────────────

@Composable
private fun AdminFab(isScanning: Boolean, scanAlpha: Float, onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        containerColor = MeshGreen,
        contentColor = Color.White,
        icon = {
            if (isScanning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White.copy(alpha = scanAlpha),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(Icons.Default.TrackChanges, contentDescription = null)
            }
        },
        text = {
            Text(if (isScanning) "Scanning…" else "Scan Network")
        }
    )
}

// ── Summary Section ───────────────────────────────────────────────────────────

@Composable
private fun SummarySectionHeader() {
    Text(
        text = "NETWORK OVERVIEW",
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = MeshMid,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp)
    )
}

@Composable
private fun SummaryCardsRow(
    peerCount: Int,
    sosActive: Int,
    campsOnline: Int,
    msgsToday: Int
) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SummaryCard("Peers Online",   peerCount.toString(),  Icons.Default.PeopleAlt,      MeshGreen)
        SummaryCard("SOS Active",     sosActive.toString(),  Icons.Default.Warning,         MeshRed)
        SummaryCard("Camps Online",   campsOnline.toString(),Icons.Default.Home,            MeshAmber)
        SummaryCard("Msgs Today",     msgsToday.toString(),  Icons.Default.ChatBubble,      Color(0xFF5975D6))
    }
}

@Composable
private fun SummaryCard(label: String, value: String, icon: ImageVector, color: Color) {
    Card(
        modifier = Modifier
            .width(130.dp)
            .height(100.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
            Column {
                Text(
                    text = value,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 26.sp,
                    color = color
                )
                Text(
                    text = label,
                    fontSize = 11.sp,
                    color = MeshMid,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ── Section Header ────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String, icon: ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MeshGreen, modifier = Modifier.size(18.dp))
        Text(
            text = title.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MeshMid,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.weight(1f))
        HorizontalDivider(
            modifier = Modifier.width(60.dp),
            color = MeshGreen.copy(alpha = 0.3f),
            thickness = 1.dp
        )
    }
}

// ── Peer Row ──────────────────────────────────────────────────────────────────

@Composable
private fun PeerRow(peer: AdminPeer) {
    val tColor = triageColor(peer.triage)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MeshGreenLight),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = peer.name.take(1).uppercase(),
                    fontWeight = FontWeight.Bold,
                    color = MeshGreenDark,
                    fontSize = 16.sp
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = peer.name,
                    fontWeight = FontWeight.SemiBold,
                    color = MeshDark,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = lastSeenLabel(peer.lastSeenMinutesAgo),
                    fontSize = 11.sp,
                    color = MeshMid
                )
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = tColor.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, tColor.copy(alpha = 0.4f))
            ) {
                Text(
                    text = triageLabel(peer.triage),
                    color = tColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = { /* stub: send message */ }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Message, contentDescription = "Message", tint = MeshMid, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ── SOS Alert Card ────────────────────────────────────────────────────────────

@Composable
private fun SosAlertCard(alert: AdminSosAlert, onAcknowledge: () -> Unit) {
    val tColor = triageColor(alert.triage)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MeshRed.copy(alpha = 0.06f)),
        border = BorderStroke(1.5.dp, MeshRed.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MeshRed, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = alert.senderName,
                    fontWeight = FontWeight.Bold,
                    color = MeshDark,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = tColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, tColor.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = triageLabel(alert.triage),
                        color = tColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = MeshMid, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(text = "%.4f, %.4f".format(alert.lat, alert.lon), fontSize = 12.sp, color = MeshMid)
                Spacer(Modifier.width(12.dp))
                Icon(Icons.Default.AccessTime, contentDescription = null, tint = MeshMid, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(text = lastSeenLabel(alert.minutesAgo), fontSize = 12.sp, color = MeshMid)
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onAcknowledge,
                colors = ButtonDefaults.buttonColors(containerColor = MeshGreen),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Acknowledge", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ── Camp Status Card ──────────────────────────────────────────────────────────

@Composable
private fun CampStatusCard(camp: AdminCamp) {
    val occupancyFraction = (camp.occupancy.toFloat() / camp.capacity.coerceAtLeast(1)).coerceIn(0f, 1f)
    val barColor = when {
        occupancyFraction > 0.85f -> MeshRed
        occupancyFraction > 0.60f -> MeshAmber
        else -> MeshGreen
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = camp.name,
                    fontWeight = FontWeight.SemiBold,
                    color = MeshDark,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${camp.occupancy}/${camp.capacity}",
                    fontWeight = FontWeight.Bold,
                    color = barColor,
                    fontSize = 13.sp
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { occupancyFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = barColor,
                trackColor = barColor.copy(alpha = 0.15f)
            )
        }
    }
}

// ── Bulletin Composer ─────────────────────────────────────────────────────────

@Composable
private fun BulletinComposer(
    draft: String,
    onDraftChange: (String) -> Unit,
    onBroadcast: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                placeholder = { Text("Type a network-wide bulletin…", fontSize = 13.sp) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MeshGreen,
                    unfocusedBorderColor = MeshGray
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onBroadcast() })
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onBroadcast,
                enabled = draft.trim().isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MeshGreenDark),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.align(Alignment.End)
            ) {
                Icon(Icons.Default.Campaign, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Broadcast", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
    }
}

// ── Bulletin Entry Row ────────────────────────────────────────────────────────

@Composable
private fun BulletinEntryRow(entry: BulletinEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .background(MeshGreenLight, shape = RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(Icons.Default.Campaign, contentDescription = null, tint = MeshGreen,
            modifier = Modifier.size(16.dp).padding(top = 2.dp))
        Spacer(Modifier.width(8.dp))
        Text(text = entry.message, fontSize = 13.sp, color = MeshDark, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(text = entry.timeLabel, fontSize = 10.sp, color = MeshMid)
    }
}

// ── Empty State ───────────────────────────────────────────────────────────────

@Composable
private fun EmptyStateRow(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MeshGreen, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text = message, fontSize = 13.sp, color = MeshMid)
    }
}