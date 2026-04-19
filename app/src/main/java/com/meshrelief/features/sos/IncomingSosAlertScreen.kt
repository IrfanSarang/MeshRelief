package com.meshrelief.features.sos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.meshrelief.ui.theme.MeshAmber
import com.meshrelief.ui.theme.MeshDark
import com.meshrelief.ui.theme.MeshGray
import com.meshrelief.ui.theme.MeshGreen
import com.meshrelief.ui.theme.MeshGreenDark
import com.meshrelief.ui.theme.MeshGreenLight
import com.meshrelief.ui.theme.MeshMid
import com.meshrelief.ui.theme.MeshRed

private val SosDark = Color(0xFFA32D2D)
private val SosLight = Color(0xFFFCEBEB)
private val SosBorder = Color(0xFFF09595)

private fun triageLabelIncoming(level: IncomingTriageLevel): String = when (level) {
    IncomingTriageLevel.SAFE         -> "Safe \u2014 no injury"
    IncomingTriageLevel.MINOR        -> "Minor injury"
    IncomingTriageLevel.CRITICAL     -> "Critical injury"
    IncomingTriageLevel.UNRESPONSIVE -> "Unresponsive"
}

private fun triageColorIncoming(level: IncomingTriageLevel): Color = when (level) {
    IncomingTriageLevel.SAFE         -> Color(0xFF1D9E75)
    IncomingTriageLevel.MINOR        -> Color(0xFFEF9F27)
    IncomingTriageLevel.CRITICAL     -> Color(0xFFE24B4A)
    IncomingTriageLevel.UNRESPONSIVE -> Color(0xFF888888)
}

@Composable
fun IncomingSosAlertScreen(
    packet: com.meshrelief.mesh.protocol.MeshPacket?,
    onDismiss: () -> Unit,
    onNavigate: () -> Unit,
    viewModel: IncomingSosAlertViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isDismissed) {
        if (uiState.isDismissed) onDismiss()
    }

    // Load from real packet if available, else fall back to demo data
    LaunchedEffect(packet) {
        if (packet != null) {
            viewModel.loadFromMeshPacket(packet)
        } else if (uiState.senderName == "Unknown") {
            // DEV_ONLY fallback — unchanged
            viewModel.loadFromPacket(
                senderName = "Ravi K.", senderIdSuffix = "4401",
                isVerified = false, hopCount = 2,
                triage = IncomingTriageLevel.CRITICAL,
                lat = 19.0760, lng = 72.8777,
                distanceKm = 0.8f, directionLabel = "NE",
                message = "Trapped under debris near station road. Leg injury. Need help urgently.",
                receivedTimeLabel = "9:40 AM"
            )
        }
    }

    val triage = uiState.triage
    val triageColor = triageColorIncoming(triage)
    val isCriticalOrUnresponsive = triage == IncomingTriageLevel.CRITICAL ||
            triage == IncomingTriageLevel.UNRESPONSIVE

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // ── Red header bar ────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isCriticalOrUnresponsive) MeshRed else Color(0xFFEF9F27)
                )
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(
                text = "Incoming SOS alert",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.8f)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${uiState.senderName} needs help",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "\u2022\u2022\u2022\u2022${uiState.senderIdSuffix} \u00b7 " +
                        (if (uiState.isVerified) "verified \u2713" else "unverified") +
                        " \u00b7 ${uiState.hopCount} hop${if (uiState.hopCount != 1) "s" else ""} away",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.8f)
            )
        }

        // ── Scrollable body ───────────────────────────────────────────────
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Triage card
            SosInfoCard(borderColor = triageColor.copy(alpha = 0.5f)) {
                Text(
                    text = "Triage status",
                    fontSize = 10.sp,
                    color = MeshMid
                )
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(triageColor, shape = RoundedCornerShape(6.dp))
                    )
                    Text(
                        text = triageLabelIncoming(triage),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isCriticalOrUnresponsive) SosDark else MeshDark
                    )
                }
            }

            // Location card
            SosInfoCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Location", fontSize = 10.sp, color = MeshMid)
                    Text(
                        text = "Approx. ${uiState.distanceKm}km \u00b7 ${uiState.directionLabel}",
                        fontSize = 10.sp,
                        color = MeshMid
                    )
                }
                Spacer(Modifier.height(5.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = MeshMid,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "${"%.4f".format(uiState.latRaw)}\u00b0 N, " +
                                "${"%.4f".format(uiState.lngRaw)}\u00b0 E",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MeshDark
                    )
                }
                Spacer(Modifier.height(8.dp))
                // Mini map placeholder
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .background(MeshGreenLight, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.Map,
                            contentDescription = null,
                            tint = Color(0xFF0F6E56),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Tap Navigate to open on map",
                            fontSize = 10.sp,
                            color = Color(0xFF0F6E56)
                        )
                    }
                }
            }

            // Message card (only if non-empty)
            if (uiState.message.isNotBlank()) {
                SosInfoCard {
                    Text(text = "Message", fontSize = 10.sp, color = MeshMid)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = "\u201c${uiState.message}\u201d",
                        fontSize = 12.sp,
                        color = MeshDark,
                        lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Received ${uiState.receivedTimeLabel} \u00b7 relayed via ${uiState.hopCount} hop${if (uiState.hopCount != 1) "s" else ""}",
                        fontSize = 10.sp,
                        color = Color(0xFFAAAAAA)
                    )
                }
            }
        }

        // ── Bottom action buttons ─────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Dismiss + Navigate row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.dismiss() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = MeshGray,
                        contentColor = MeshMid
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        0.5.dp, Color(0xFFD3D1C7)
                    )
                ) {
                    Text(text = "Dismiss", fontSize = 13.sp)
                }

                Button(
                    onClick = onNavigate,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MeshGreen,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        Icons.Default.Map,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(text = "Navigate", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }

            // Relay SOS button
            Button(
                onClick = { viewModel.relayFurther() },
                enabled = !uiState.isRelaying && !uiState.relaySuccess,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (uiState.relaySuccess) MeshGreen else MeshRed,
                    contentColor = Color.White,
                    disabledContainerColor = MeshGreen,
                    disabledContentColor = Color.White
                )
            ) {
                if (uiState.isRelaying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(text = "Relaying...", fontSize = 13.sp)
                } else if (uiState.relaySuccess) {
                    Text(text = "\u2713 SOS relayed to further peers", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                } else {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(text = "Relay SOS further", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

// ── Reusable card shell ───────────────────────────────────────────────────────

@Composable
private fun SosInfoCard(
    borderColor: Color = Color(0xFFEEEEEE),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, borderColor),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            content = content
        )
    }
}