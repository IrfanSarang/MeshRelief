package com.meshrelief.features.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

val MeshGreen = Color(0xFF1D9E75)
val MeshGreenLight = Color(0xFFE1F5EE)
val MeshGreenDark = Color(0xFF085041)
val MeshGray = Color(0xFFF1EFE8)
val MeshDark = Color(0xFF2C2C2A)
val MeshMid = Color(0xFF5F5E5A)

data class LanguageOption(
    val code: String,
    val nativeName: String,
    val englishName: String
)

val languages = listOf(
    LanguageOption("EN", "English", "English"),
    LanguageOption("HI", "हिंदी", "Hindi"),
    LanguageOption("MR", "मराठी", "Marathi"),
    LanguageOption("UR", "اردو", "Urdu")
)

@Composable
fun SetupScreen(
    onSetupComplete: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isComplete) {
        if (uiState.isComplete) onSetupComplete()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        // App icon placeholder
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MeshGreen),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "MR",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "MeshRelief",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = MeshGreenDark
        )

        Text(
            text = "Offline emergency communication",
            fontSize = 13.sp,
            color = MeshMid
        )

        Text(
            text = "Works without internet",
            fontSize = 12.sp,
            color = MeshMid
        )

        Spacer(modifier = Modifier.height(36.dp))

        // Name field
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Your name",
                fontSize = 12.sp,
                color = MeshMid,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            OutlinedTextField(
                value = uiState.name,
                onValueChange = viewModel::onNameChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Enter your full name") },
                isError = uiState.nameError != null,
                supportingText = {
                    uiState.nameError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Phone field
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Mobile number",
                fontSize = 12.sp,
                color = MeshMid,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            OutlinedTextField(
                value = uiState.phone,
                onValueChange = { if (it.length <= 10) viewModel.onPhoneChange(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("10-digit mobile number") },
                prefix = { Text("+91 ") },
                isError = uiState.phoneError != null,
                supportingText = {
                    uiState.phoneError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Language selection
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Preferred language",
                fontSize = 12.sp,
                color = MeshMid,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            languages.forEach { lang ->
                val selected = uiState.language == lang.code
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (selected) MeshGreenLight else Color.White
                        )
                        .border(
                            width = if (selected) 1.5.dp else 0.5.dp,
                            color = if (selected) MeshGreen else Color(0xFFD3D1C7),
                            shape = RoundedCornerShape(10.dp)
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
                        colors = RadioButtonDefaults.colors(
                            selectedColor = MeshGreen
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Submit button
        Button(
            onClick = viewModel::onSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            enabled = !uiState.isLoading,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MeshGreen
            )
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = "Get Started",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Your info stays on your device. No server, no account.",
            fontSize = 11.sp,
            color = MeshMid,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}