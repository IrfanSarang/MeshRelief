package com.meshrelief.features.status

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
import com.meshrelief.features.home.*
import com.meshrelief.ui.theme.MeshAmber
import com.meshrelief.ui.theme.MeshDark
import com.meshrelief.ui.theme.MeshGray
import com.meshrelief.ui.theme.MeshGreen
import com.meshrelief.ui.theme.MeshGreenDark
import com.meshrelief.ui.theme.MeshGreenLight
import com.meshrelief.ui.theme.MeshMid
import com.meshrelief.ui.theme.MeshRed

data class TriageOption(
    val code: String,
    val label: String,
    val sublabel: String,
    val color: Color
)

val triageOptions = listOf(
    TriageOption("SAFE", "Safe", "No injuries, no help needed", Color(0xFF1D9E75)),
    TriageOption("MINOR", "Minor injury", "Injured but stable", Color(0xFFEF9F27)),
    TriageOption("CRITICAL", "Critical — need help", "Urgent assistance required", Color(0xFFE24B4A)),
    TriageOption("UNRESPONSIVE", "Unresponsive", "Set by proxy if needed", Color(0xFF888780))
)

@Composable
fun MyStatusScreen(
    onBack: () -> Unit,
    viewModel: MyStatusViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
    ) {

        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(MeshGray)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Text("←", fontSize = 14.sp, color = MeshMid)
            }
            Column {
                Text(
                    text = "My Status",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MeshDark
                )
                Text(
                    text = "Visible to all peers on the mesh",
                    fontSize = 10.sp,
                    color = MeshMid
                )
            }
        }

        HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 0.5.dp)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // User identity card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MeshGreenLight)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MeshGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = uiState.userName.take(2).uppercase(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                }
                Column {
                    Text(
                        text = uiState.userName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MeshGreenDark
                    )
                    Text(
                        text = "••••${uiState.userPhone.takeLast(4)}",
                        fontSize = 11.sp,
                        color = Color(0xFF0F6E56)
                    )
                }
            }

            // Triage selection
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "My triage status",
                    fontSize = 12.sp,
                    color = MeshMid
                )
                triageOptions.forEach { option ->
                    val selected = uiState.selectedTriage == option.code
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (selected) option.color.copy(alpha = 0.08f)
                                else Color.White
                            )
                            .border(
                                width = if (selected) 1.5.dp else 0.5.dp,
                                color = if (selected) option.color else Color(0xFFD3D1C7),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { viewModel.onTriageSelected(option.code) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(option.color)
                            )
                            Column {
                                Text(
                                    text = option.label,
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.Medium
                                    else FontWeight.Normal,
                                    color = if (selected) option.color else MeshDark
                                )
                                Text(
                                    text = option.sublabel,
                                    fontSize = 10.sp,
                                    color = MeshMid
                                )
                            }
                        }
                        RadioButton(
                            selected = selected,
                            onClick = { viewModel.onTriageSelected(option.code) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = option.color
                            )
                        )
                    }
                }
            }

            // Optional message
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Broadcast message (optional)",
                    fontSize = 12.sp,
                    color = MeshMid
                )
                OutlinedTextField(
                    value = uiState.statusMessage,
                    onValueChange = viewModel::onMessageChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            "Add a short note for rescue teams...",
                            fontSize = 12.sp
                        )
                    },
                    supportingText = {
                        Text(
                            "${uiState.statusMessage.length}/100",
                            fontSize = 10.sp,
                            color = MeshMid
                        )
                    },
                    maxLines = 3,
                    shape = RoundedCornerShape(10.dp)
                )
            }

            // Auto broadcast info
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MeshGray)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "ℹ", fontSize = 13.sp, color = MeshMid)
                Text(
                    text = "Your status is auto-broadcast every 60 seconds to all connected peers.",
                    fontSize = 11.sp,
                    color = MeshMid,
                    modifier = Modifier.weight(1f)
                )
            }

            // Success message
            if (uiState.broadcastSuccess) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MeshGreenLight)
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = "✓", fontSize = 13.sp, color = MeshGreen)
                    Text(
                        text = "Status saved and broadcast to mesh",
                        fontSize = 11.sp,
                        color = MeshGreenDark,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Save button
            Button(
                onClick = viewModel::onSaveAndBroadcast,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                enabled = !uiState.isBroadcasting,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MeshGreen
                )
            ) {
                if (uiState.isBroadcasting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "Save & broadcast now",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}