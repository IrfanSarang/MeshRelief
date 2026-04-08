package com.meshrelief.features.setup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.meshrelief.R
import com.meshrelief.features.home.MeshDark
import com.meshrelief.features.home.MeshGray
import com.meshrelief.features.home.MeshGreen
import com.meshrelief.features.home.MeshGreenLight
import com.meshrelief.features.home.MeshMid

private data class LanguageSelectionOption(
    val displayLabel: String,
    val subLabel: String,
    val code: String,
    val isRtl: Boolean = false
)

private val languageOptions = listOf(
    LanguageSelectionOption("English", "English", "en"),
    LanguageSelectionOption("\u0939\u093F\u0902\u0926\u0940", "Hindi", "hi"),
    LanguageSelectionOption("\u092E\u0930\u093E\u0920\u0940", "Marathi", "mr"),
    LanguageSelectionOption("\u0627\u0631\u062F\u0648", "Urdu", "ur", isRtl = true)
)

@Composable
fun LanguageSelectionScreen(
    onConfirm: () -> Unit,
    onBack: () -> Unit = {},
    viewModel: LanguageSelectionViewModel = hiltViewModel()
) {
    val selectedLanguage = viewModel.selectedLanguage
    val isSaving = viewModel.isSaving

    // Wrap the entire screen in RTL layout direction when Urdu is selected
    val layoutDirection = if (selectedLanguage == "ur") LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
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
                            contentDescription = stringResource(R.string.nav_home),
                            tint = MeshDark
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.lang_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MeshDark
                    )
                }
                Text(
                    text = stringResource(R.string.lang_step),
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

                Text(
                    text = stringResource(R.string.lang_helper),
                    fontSize = 12.sp,
                    color = MeshMid,
                    lineHeight = 17.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

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

                // Info box — always LTR so the emoji doesn't flip
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MeshGray, RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(text = "\u2139\uFE0F", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.lang_info),
                            fontSize = 12.sp,
                            color = MeshMid,
                            lineHeight = 17.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
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
                    onClick = { if (!isSaving) viewModel.confirmAndSave(onDone = onConfirm) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
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
                            text = stringResource(R.string.lang_confirm),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguageCard(
    option: LanguageSelectionOption,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) MeshGreenLight else Color.White
    val borderColor = if (isSelected) MeshGreen else Color(0xFFD3D1C7)
    val borderWidth = if (isSelected) 2.dp else 1.5.dp

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
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
            RadioCircle(isSelected = isSelected)
            Spacer(modifier = Modifier.width(14.dp))

            // Text column — RTL only for Urdu label text
            if (option.isRtl) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = option.displayLabel, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MeshDark)
                        Text(text = option.subLabel, fontSize = 12.sp, color = MeshMid)
                    }
                }
            } else {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = option.displayLabel, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MeshDark)
                    Text(text = option.subLabel, fontSize = 12.sp, color = MeshMid)
                }
            }
        }
    }
}

@Composable
private fun RadioCircle(isSelected: Boolean) {
    if (isSelected) {
        Box(
            modifier = Modifier.size(22.dp).background(Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(modifier = Modifier.size(22.dp).background(Color.Transparent, CircleShape))
            androidx.compose.foundation.Canvas(modifier = Modifier.size(22.dp)) {
                drawCircle(
                    color = MeshGreen,
                    radius = size.minDimension / 2f,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                )
            }
            Box(modifier = Modifier.size(12.dp).background(MeshGreen, CircleShape))
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