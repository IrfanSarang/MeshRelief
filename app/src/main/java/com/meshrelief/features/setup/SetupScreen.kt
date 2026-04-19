package com.meshrelief.features.setup

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.meshrelief.R
import com.meshrelief.ui.theme.MeshDark
import com.meshrelief.ui.theme.MeshGreen
import com.meshrelief.ui.theme.MeshGreenDark
import com.meshrelief.ui.theme.MeshGreenLight
import com.meshrelief.ui.theme.MeshMid

data class LanguageOption(
    val code: String,
    val nativeName: String,
    val englishName: String
)

val languages = listOf(
    LanguageOption("EN", "English", "English"),
    LanguageOption("HI", "हिंदी",   "Hindi"),
    LanguageOption("MR", "मराठी",   "Marathi"),
    LanguageOption("UR", "اردو",    "Urdu")
)

@Composable
fun SetupScreen(
    onSetupComplete: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel()
) {
    val uiState     by viewModel.uiState.collectAsState()
    val tileUiState by viewModel.tileDownloadState.collectAsState()

    LaunchedEffect(uiState.isComplete) {
        if (uiState.isComplete) onSetupComplete()
    }

    val animatedProgress by animateFloatAsState(
        targetValue = tileUiState.progress,
        label = "tile_download_progress"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MeshGreen),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "MR", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.app_name),
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = MeshGreenDark
        )
        Text(text = stringResource(R.string.setup_tagline),       fontSize = 13.sp, color = MeshMid)
        Text(text = stringResource(R.string.setup_works_offline),  fontSize = 12.sp, color = MeshMid)

        Spacer(modifier = Modifier.height(36.dp))

        // ── Name field ─────────────────────────────────────────────────────
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.setup_name_label),
                fontSize = 12.sp, color = MeshMid,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            OutlinedTextField(
                value = uiState.name,
                onValueChange = viewModel::onNameChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.setup_name_placeholder)) },
                isError = uiState.nameError != null,
                supportingText = {
                    uiState.nameError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                },
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Phone field ────────────────────────────────────────────────────
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.setup_phone_label),
                fontSize = 12.sp, color = MeshMid,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            OutlinedTextField(
                value = uiState.phone,
                onValueChange = { if (it.length <= 10) viewModel.onPhoneChange(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.setup_phone_placeholder)) },
                prefix = { Text("+91 ") },
                isError = uiState.phoneError != null,
                supportingText = {
                    uiState.phoneError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── Language selection ─────────────────────────────────────────────
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.setup_language_label),
                fontSize = 12.sp, color = MeshMid,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            languages.forEach { lang ->
                val selected = uiState.language == lang.code
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) MeshGreenLight else Color.White)
                        .border(
                            width = if (selected) 1.5.dp else 0.5.dp,
                            color  = if (selected) MeshGreen else Color(0xFFD3D1C7),
                            shape  = RoundedCornerShape(10.dp)
                        )
                        .clickable { viewModel.onLanguageChange(lang.code) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = lang.nativeName,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (selected) MeshGreenDark else MeshDark
                        )
                        Text(
                            text = lang.englishName,
                            fontSize = 11.sp,
                            color = if (selected) Color(0xFF0F6E56) else MeshMid
                        )
                    }
                    RadioButton(
                        selected = selected,
                        onClick = { viewModel.onLanguageChange(lang.code) },
                        colors = RadioButtonDefaults.colors(selectedColor = MeshGreen)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Offline Map Tiles download card ───────────────────────────────
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFFF4FAF7),
            tonalElevation = 1.dp,
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (tileUiState.isDone)
                            Icons.Default.CheckCircle
                        else
                            Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = if (tileUiState.isDone) MeshGreen else MeshGreenDark,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = "Offline Map Tiles",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MeshGreenDark
                        )
                        Text(
                            text = "Download ~200 MB of map data for use without internet.",
                            fontSize = 11.sp,
                            color = MeshMid
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ── FIX: snapshot error into a local val so Kotlin can smart-cast ──
                // tileUiState is a delegated property (by collectAsState); the
                // compiler cannot guarantee it won't change between the null-check
                // and the usage inside a lambda, so smart cast is disallowed.
                // Capturing it in a local val makes the value stable.
                val tileError = tileUiState.error

                when {
                    tileUiState.isDone -> {
                        Text(
                            text = "✓ Tiles ready — map will work fully offline",
                            fontSize = 12.sp,
                            color = MeshGreen,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    tileUiState.isDownloading -> {
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MeshGreen,
                            trackColor = Color(0xFFD3EDE4)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (tileUiState.progress < 1f)
                                "Downloading… ${(tileUiState.progress * 100).toInt()}%"
                            else
                                "Finalising…",
                            fontSize = 11.sp,
                            color = MeshMid
                        )
                    }

                    tileError != null -> {
                        // tileError is now a plain String — smart cast works fine
                        Text(
                            text = tileError,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.startTileDownload() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Retry", color = MeshGreenDark)
                        }
                    }

                    else -> {
                        Button(
                            onClick = { viewModel.startTileDownload() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MeshGreen)
                        ) {
                            Text("Download Offline Maps", fontSize = 13.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Recommended: connect to WiFi before downloading.",
                            fontSize = 11.sp,
                            color = MeshMid
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── Submit button ──────────────────────────────────────────────────
        Button(
            onClick = viewModel::onSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            enabled = !uiState.isLoading,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MeshGreen)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = stringResource(R.string.setup_submit),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.setup_privacy_note),
            fontSize = 11.sp,
            color = MeshMid,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}