package com.meshrelief.features.status

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.meshrelief.data.db.entity.PeerEntity
import com.meshrelief.features.home.*

@Composable
fun StatusScreen(
    onMyStatusClick: () -> Unit,
    viewModel: StatusViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {

        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = stringResource(R.string.status_title),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MeshDark
            )
            Text(
                text = stringResource(R.string.status_subtitle),
                fontSize = 10.sp,
                color = MeshMid
            )
        }

        HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 0.5.dp)

        Button(
            onClick = onMyStatusClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .height(44.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MeshGreen)
        ) {
            Text(
                text = stringResource(R.string.status_set_my_status),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }

        if (uiState.peers.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.status_no_peers_title),
                        fontSize = 14.sp,
                        color = MeshMid,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.status_no_peers_sub),
                        fontSize = 12.sp,
                        color = Color(0xFFAAAAAA)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(uiState.peers) { peer ->
                    PeerStatusCard(peer = peer)
                }
            }
        }
    }
}

@Composable
fun PeerStatusCard(peer: PeerEntity) {
    val triageColor = when (peer.triageStatus) {
        "SAFE"     -> Color(0xFF1D9E75)
        "MINOR"    -> Color(0xFFEF9F27)
        "CRITICAL" -> Color(0xFFE24B4A)
        else       -> Color(0xFF888780)
    }
    val batteryColor = when {
        peer.battery < 15 -> Color(0xFFE24B4A)
        peer.battery < 30 -> Color(0xFFEF9F27)
        else              -> Color(0xFF1D9E75)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White)
            .border(width = 0.5.dp, color = Color(0xFFEEEEEE), shape = RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(triageColor))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "${peer.name} (••••${peer.phone4})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MeshDark
                )
                if (peer.flagged) {
                    Text(text = "⚠", fontSize = 11.sp, color = Color(0xFFE24B4A))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = peer.triageStatus.lowercase().replaceFirstChar { it.uppercase() },
                    fontSize = 10.sp,
                    color = triageColor
                )
                Text(text = "·", fontSize = 10.sp, color = Color(0xFFAAAAAA))
                Text(
                    text = if (peer.verified)
                        stringResource(R.string.status_verified)
                    else
                        stringResource(R.string.status_unverified),
                    fontSize = 10.sp,
                    color = if (peer.verified) Color(0xFF1D9E75) else Color(0xFFAAAAAA)
                )
                Text(text = "·", fontSize = 10.sp, color = Color(0xFFAAAAAA))
                Text(
                    text = if (peer.hopCount == 1)
                        stringResource(R.string.status_hop_one, peer.hopCount)
                    else
                        stringResource(R.string.status_hop_other, peer.hopCount),
                    fontSize = 10.sp,
                    color = Color(0xFFAAAAAA)
                )
            }
        }

        // Battery indicator
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .height(10.dp)
                        .border(width = 1.dp, color = Color(0xFFD3D1C7), shape = RoundedCornerShape(2.dp))
                        .clip(RoundedCornerShape(2.dp))
                ) {
                    Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(peer.battery / 100f).background(batteryColor))
                }
                Text(text = "${peer.battery}%", fontSize = 10.sp, color = batteryColor, fontWeight = FontWeight.Medium)
            }
            if (peer.battery < 15) {
                Text(
                    text = stringResource(R.string.status_low_battery),
                    fontSize = 9.sp,
                    color = Color(0xFFE24B4A),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}