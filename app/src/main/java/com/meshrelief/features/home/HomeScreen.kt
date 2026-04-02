package com.meshrelief.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

val MeshGreen = Color(0xFF1D9E75)
val MeshGreenLight = Color(0xFFE1F5EE)
val MeshGreenDark = Color(0xFF085041)
val MeshGray = Color(0xFFF1EFE8)
val MeshDark = Color(0xFF2C2C2A)
val MeshMid = Color(0xFF5F5E5A)
val MeshRed = Color(0xFFE24B4A)
val MeshAmber = Color(0xFFEF9F27)

@Composable
fun HomeScreen(
    onSOSClick: () -> Unit,
    onChatClick: () -> Unit,
    onMapClick: () -> Unit,
    onStatusClick: () -> Unit,
    onChatbotClick: () -> Unit,
    onDiscoveryClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    onCampsClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        bottomBar = {
            BottomNavBar(
                onHomeClick = {},
                onChatClick = onChatClick,
                onMapClick = onMapClick,
                onStatusClick = onStatusClick,
                onChatbotClick = onChatbotClick,
                currentRoute = "home"
            )
        },
        containerColor = Color.White
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {

            // ── Header ──────────────────────────────────────
            HomeHeader(
                meshActive = uiState.meshActive,
                peersOnline = uiState.peersOnline
            )

            // ── Content ─────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                // SOS Button
                SOSButton(onClick = onSOSClick)

                // Stats row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Peers online card — tappable, opens Discovery screen
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onDiscoveryClick() }
                    ) {
                        StatCard(
                            modifier = Modifier.fillMaxWidth(),
                            label = "Peers online",
                            value = uiState.peersOnline.toString(),
                            subLabel = "${uiState.verifiedPeers} verified · tap to scan"
                        )
                    }

                    StatCard(
                        modifier = Modifier.weight(1f),
                        label = "Nearby camps",
                        value = uiState.nearbyCamps.toString(),
                        subLabel = "${uiState.openCamps} open"
                    )
                }

                // Bulletin preview
                if (uiState.latestBulletin != null) {
                    BulletinCard(bulletin = uiState.latestBulletin!!)
                } else {
                    EmptyBulletinCard()
                }
            }
        }
    }
}

@Composable
fun HomeHeader(
    meshActive: Boolean,
    peersOnline: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .border(
                width = 0.5.dp,
                color = Color(0xFFEEEEEE),
                shape = RoundedCornerShape(0.dp)
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "MeshRelief",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MeshDark
            )

            // Mesh status badge
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (meshActive) MeshGreenLight else Color(0xFFF1EFE8))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (meshActive) MeshGreen else Color(0xFFB4B2A9))
                )
                Text(
                    text = if (meshActive) "Mesh active" else "Scanning...",
                    fontSize = 11.sp,
                    color = if (meshActive) MeshGreenDark else MeshMid,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = if (peersOnline > 0)
                "$peersOnline device${if (peersOnline > 1) "s" else ""} connected"
            else
                "No peers connected yet",
            fontSize = 11.sp,
            color = MeshMid
        )
    }
}

@Composable
fun SOSButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MeshRed
        )
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "SOS",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Tap to broadcast emergency alert",
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    subLabel: String
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MeshGray)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            color = MeshMid
        )
        Text(
            text = value,
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            color = MeshDark
        )
        Text(
            text = subLabel,
            fontSize = 10.sp,
            color = MeshMid
        )
    }
}

@Composable
fun BulletinCard(bulletin: BulletinPreview) {
    val borderColor = when (bulletin.type) {
        "EMERGENCY" -> MeshRed
        "RELIEF"    -> MeshGreen
        "WARNING"   -> MeshAmber
        else        -> Color(0xFF378ADD)
    }
    val tagText = when (bulletin.type) {
        "EMERGENCY" -> "Emergency"
        "RELIEF"    -> "Relief"
        "WARNING"   -> "Warning"
        else        -> "Info"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 10.dp, bottomEnd = 10.dp))
            .background(Color.White)
            .border(
                width = 0.5.dp,
                color = Color(0xFFEEEEEE),
                shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 10.dp, bottomEnd = 10.dp)
            )
            .padding(start = 0.dp)
    ) {
        Row {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(80.dp)
                    .background(borderColor)
            )
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = tagText,
                        fontSize = 10.sp,
                        color = borderColor,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${bulletin.minutesAgo} min ago",
                        fontSize = 10.sp,
                        color = Color(0xFFAAAAAA)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = bulletin.content,
                    fontSize = 12.sp,
                    color = MeshDark,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${bulletin.senderName} · relayed via ${bulletin.relayCount} hops",
                    fontSize = 10.sp,
                    color = Color(0xFFAAAAAA)
                )
            }
        }
    }
}

@Composable
fun EmptyBulletinCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MeshGray)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No bulletins yet",
            fontSize = 12.sp,
            color = MeshMid
        )
        Text(
            text = "Bulletins from nearby peers will appear here",
            fontSize = 11.sp,
            color = Color(0xFFAAAAAA)
        )
    }
}

@Composable
fun BottomNavBar(
    onHomeClick: () -> Unit,
    onChatClick: () -> Unit,
    onMapClick: () -> Unit,
    onStatusClick: () -> Unit,
    onChatbotClick: () -> Unit,
    currentRoute: String
) {
    NavigationBar(
        containerColor = Color.White,
        tonalElevation = 0.dp,
        modifier = Modifier.border(
            width = 0.5.dp,
            color = Color(0xFFEEEEEE)
        )
    ) {
        NavigationBarItem(
            selected = currentRoute == "home",
            onClick = onHomeClick,
            icon = { Text(text = "⌂", fontSize = 18.sp) },
            label = { Text("Home", fontSize = 10.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MeshGreen,
                selectedTextColor = MeshGreen,
                indicatorColor = MeshGreenLight
            )
        )
        NavigationBarItem(
            selected = currentRoute == "chat",
            onClick = onChatClick,
            icon = { Text(text = "✉", fontSize = 18.sp) },
            label = { Text("Chat", fontSize = 10.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MeshGreen,
                selectedTextColor = MeshGreen,
                indicatorColor = MeshGreenLight
            )
        )
        NavigationBarItem(
            selected = currentRoute == "map",
            onClick = onMapClick,
            icon = { Text(text = "◎", fontSize = 18.sp) },
            label = { Text("Map", fontSize = 10.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MeshGreen,
                selectedTextColor = MeshGreen,
                indicatorColor = MeshGreenLight
            )
        )
        NavigationBarItem(
            selected = currentRoute == "status",
            onClick = onStatusClick,
            icon = { Text(text = "◉", fontSize = 18.sp) },
            label = { Text("Status", fontSize = 10.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MeshGreen,
                selectedTextColor = MeshGreen,
                indicatorColor = MeshGreenLight
            )
        )
        NavigationBarItem(
            selected = currentRoute == "chatbot",
            onClick = onChatbotClick,
            icon = { Text(text = "⊕", fontSize = 18.sp) },
            label = { Text("Guide", fontSize = 10.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MeshGreen,
                selectedTextColor = MeshGreen,
                indicatorColor = MeshGreenLight
            )
        )
    }
}