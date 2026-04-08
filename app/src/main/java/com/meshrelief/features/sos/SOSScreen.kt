package com.meshrelief.features.sos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.meshrelief.R
import com.meshrelief.core.model.TriageLevel
import com.meshrelief.features.home.MeshDark
import com.meshrelief.features.home.MeshGray
import com.meshrelief.features.home.MeshGreen
import com.meshrelief.features.home.MeshGreenDark
import com.meshrelief.features.home.MeshGreenLight
import com.meshrelief.features.home.MeshMid
import com.meshrelief.features.home.MeshRed

@Composable
fun SOSScreen(
    onBack: () -> Unit,
    viewModel: SOSViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .border(width = 0.5.dp, color = Color(0xFFEEEEEE)),
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
                Text(
                    text = stringResource(R.string.sos_title),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MeshDark
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.sos_triage_label),
                fontSize = 12.sp,
                color = MeshMid
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Triage chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TriageLevel.entries.forEach { triage ->
                    val selected = uiState.selectedTriage == triage
                    val triageColor = Color(triage.color)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (selected) triageColor.copy(alpha = 0.15f)
                                else Color(0xFFF1EFE8)
                            )
                            .border(
                                width = if (selected) 1.5.dp else 0.5.dp,
                                color = if (selected) triageColor else Color(0xFFD3D1C7),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { viewModel.onTriageSelected(triage) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = triage.label,
                            fontSize = 9.sp,
                            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                            color = if (selected) triageColor else MeshMid,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // SOS Ring + Button
            Box(
                modifier = Modifier.size(160.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .clip(CircleShape)
                        .border(width = 2.dp, color = Color(0xFFF7C1C1), shape = CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(124.dp)
                        .clip(CircleShape)
                        .background(
                            if (uiState.cooldownRemainingMs > 0) Color(0xFFB4B2A9) else MeshRed
                        )
                        .clickable(enabled = uiState.cooldownRemainingMs == 0L) {
                            viewModel.onSOSButtonPressed()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.sos_button_label),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = if (uiState.cooldownRemainingMs > 0)
                                stringResource(R.string.sos_button_locked)
                            else
                                stringResource(R.string.sos_button_hold),
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Cooldown timer
            if (uiState.cooldownRemainingMs > 0) {
                val minutes = (uiState.cooldownRemainingMs / 60000).toInt()
                val seconds = ((uiState.cooldownRemainingMs % 60000) / 1000).toInt()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MeshGray)
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.sos_cooldown_label),
                        fontSize = 11.sp,
                        color = MeshMid
                    )
                    Text(
                        text = stringResource(R.string.sos_cooldown_remaining, minutes, seconds),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MeshDark
                    )
                }
            }

            // SOS sent confirmation
            if (uiState.sosSent) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MeshGreenLight)
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.sos_sent_title),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MeshGreenDark
                    )
                    Text(
                        text = stringResource(R.string.sos_sent_sub),
                        fontSize = 11.sp,
                        color = Color(0xFF0F6E56)
                    )
                }
            }
        }

        // Confirmation Dialog
        if (uiState.showConfirmDialog) {
            Dialog(onDismissRequest = { viewModel.onCancelSOS() }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.sos_dialog_title),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MeshDark,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.sos_dialog_body),
                        fontSize = 12.sp,
                        color = MeshMid,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.sos_dialog_countdown, uiState.confirmCountdown),
                        fontSize = 11.sp,
                        color = Color(0xFFAAAAAA)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MeshGray)
                                .clickable { viewModel.onCancelSOS() }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.sos_dialog_cancel),
                                fontSize = 13.sp,
                                color = MeshMid,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MeshRed)
                                .clickable { viewModel.onConfirmSOS() }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.sos_dialog_confirm),
                                fontSize = 13.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}