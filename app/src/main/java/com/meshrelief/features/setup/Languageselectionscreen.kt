package com.meshrelief.features.setup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.meshrelief.features.home.MeshDark
import com.meshrelief.features.home.MeshGray
import com.meshrelief.features.home.MeshGreen
import com.meshrelief.features.home.MeshGreenLight
import com.meshrelief.features.home.MeshMid

private data class Languageselectionscreen(
    val displayLabel: String,
    val subLabel: String,
    val code: String,
    val isRtl: Boolean = false
)

private val languageOptions = listOf(
    Languageselectionscreen("English", "English", "en"),
    Languageselectionscreen("\u0939\u093F\u0902\u0926\u0940", "Hindi", "hi"),
    Languageselectionscreen("\u092E\u0930\u093E\u0920\u0940", "Marathi", "mr"),
    Languageselectionscreen("\u0627\u0631\u062F\u0648", "Urdu", "ur", isRtl = true)
)

@Composable
fun LanguageSelectionScreen(
    onConfirm: () -> Unit,
    onBack: () -> Unit = {},
    viewModel: LanguageSelectionViewModel = hiltViewModel()
) {
    val selectedLanguage = viewModel.selectedLanguage
    val isSaving = viewModel.isSaving

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // Top bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(top = 12.dp, bottom = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = MeshDark
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Choose language",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MeshDark
                )
            }
            Text(
                text = "Step 2 of 2 \u00B7 You can change this later in settings",
                fontSize = 12.sp,
                color = MeshMid,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Helper text
            Text(
                text = "Select your preferred language for the app and survival chatbot",
                fontSize = 12.sp,
                color = MeshMid,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Language cards
            languageOptions.forEach { option ->
                val isSelected = selectedLanguage == option.code
                LanguageCard(
                    option = option,
                    isSelected = isSelected,
                    onClick = { viewModel.selectLanguage(option.code) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Info box
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MeshGray, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "\u2139\uFE0F",
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "The survival chatbot will respond in your chosen language. UI labels will also switch automatically.",
                    fontSize = 12.sp,
                    color = MeshMid,
                    lineHeight = 17.sp,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // CTA button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Button(
                onClick = {
                    if (!isSaving) {
                        viewModel.confirmAndSave(onDone = onConfirm)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MeshGreen,
                    contentColor = Color.White
                ),
                enabled = !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "Confirm & Start",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageCard(
    option: Languageselectionscreen,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) MeshGreenLight else Color.White
    val borderColor = if (isSelected) MeshGreen else Color(0xFFD3D1C7)
    val borderWidth = if (isSelected) 2.dp else 1.5.dp

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = BorderStroke(borderWidth, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Radio circle
            RadioCircle(isSelected = isSelected)

            Spacer(modifier = Modifier.width(14.dp))

            // Text column — RTL only for Urdu
            if (option.isRtl) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = option.displayLabel,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MeshDark
                        )
                        Text(
                            text = option.subLabel,
                            fontSize = 12.sp,
                            color = MeshMid
                        )
                    }
                }
            } else {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = option.displayLabel,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MeshDark
                    )
                    Text(
                        text = option.subLabel,
                        fontSize = 12.sp,
                        color = MeshMid
                    )
                }
            }
        }
    }
}

@Composable
private fun RadioCircle(isSelected: Boolean) {
    if (isSelected) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(Color.Transparent, CircleShape)
            )
            // Outer ring
            androidx.compose.foundation.Canvas(modifier = Modifier.size(22.dp)) {
                drawCircle(
                    color = MeshGreen,
                    radius = size.minDimension / 2f,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                )
            }
            // Inner filled dot
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(MeshGreen, CircleShape)
            )
        }
    } else {
        androidx.compose.foundation.Canvas(modifier = Modifier.size(22.dp)) {
            drawCircle(
                color = MeshMid,
                radius = size.minDimension / 2f,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
            )
        }
    }
}