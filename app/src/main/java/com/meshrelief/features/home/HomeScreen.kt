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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.meshrelief.R

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
    onCampsClick: () -> Unit,
    onFakeIncomingSos: () -> Unit = {},
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
            HomeHeader(
                meshActive = uiState.meshActive,
                peersOnline = uiState.peersOnline
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SOSButton(onClick = onSOSClick)

                // DEV_ONLY — remove before production
                Button(
                    onClick = onFakeIncomingSos,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MeshAmber)
                ) {
                    Text(stringResource(R.string.home_simulate_sos), color = Color.White)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onDiscoveryClick() }
                    ) {
                        StatCard(
                            modifier = Modifier.fillMaxWidth(),
                            label = stringResource(R.string.home_stat_peers_online),
                            value = uiState.peersOnline.toString(),
                            subLabel = stringResource(R.string.home_stat_peers_sublabel, uiState.verifiedPeers)
                        )
                    }

                    StatCard(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.home_stat_nearby_camps),
                        value = uiState.nearbyCamps.toString(),
                        subLabel = stringResource(R.string.home_stat_camps_sublabel, uiState.openCamps)
                    )
                }

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
                text = stringResource(R.string.app_name),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MeshDark
            )

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
                    text = if (meshActive) stringResource(R.string.home_mesh_active)
                    else stringResource(R.string.home_mesh_scanning),
                    fontSize = 11.sp,
                    color = if (meshActive) MeshGreenDark else MeshMid,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = when {
                peersOnline == 1 -> stringResource(R.string.home_peers_connected, peersOnline)
                peersOnline > 1  -> stringResource(R.string.home_peers_connected_plural, peersOnline)
                else             -> stringResource(R.string.home_no_peers)
            },
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
        colors = ButtonDefaults.buttonColors(containerColor = MeshRed)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.home_sos_button),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = stringResource(R.string.home_sos_subtitle),
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
        Text(text = label, fontSize = 10.sp, color = MeshMid)
        Text(text = value, fontSize = 22.sp, fontWeight = FontWeight.Medium, color = MeshDark)
        Text(text = subLabel, fontSize = 10.sp, color = MeshMid)
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
        "EMERGENCY" -> stringResource(R.string.home_bulletin_tag_emergency)
        "RELIEF"    -> stringResource(R.string.home_bulletin_tag_relief)
        "WARNING"   -> stringResource(R.string.home_bulletin_tag_warning)
        else        -> stringResource(R.string.home_bulletin_tag_info)
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
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = tagText, fontSize = 10.sp, color = borderColor, fontWeight = FontWeight.Medium)
                    Text(
                        text = stringResource(R.string.home_bulletin_minutes_ago, bulletin.minutesAgo),
                        fontSize = 10.sp,
                        color = Color(0xFFAAAAAA)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = bulletin.content, fontSize = 12.sp, color = MeshDark, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.home_bulletin_relay, bulletin.senderName, bulletin.relayCount),
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
        Text(text = stringResource(R.string.home_no_bulletins), fontSize = 12.sp, color = MeshMid)
        Text(text = stringResource(R.string.home_no_bulletins_sub), fontSize = 11.sp, color = Color(0xFFAAAAAA))
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
        modifier = Modifier.border(width = 0.5.dp, color = Color(0xFFEEEEEE))
    ) {
        NavigationBarItem(
            selected = currentRoute == "home",
            onClick = onHomeClick,
            icon = { Text(text = "⌂", fontSize = 18.sp) },
            label = { Text(stringResource(R.string.nav_home), fontSize = 10.sp) },
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
            label = { Text(stringResource(R.string.nav_chat), fontSize = 10.sp) },
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
            label = { Text(stringResource(R.string.nav_map), fontSize = 10.sp) },
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
            label = { Text(stringResource(R.string.nav_status), fontSize = 10.sp) },
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
            label = { Text(stringResource(R.string.nav_guide), fontSize = 10.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MeshGreen,
                selectedTextColor = MeshGreen,
                indicatorColor = MeshGreenLight
            )
        )
    }
}