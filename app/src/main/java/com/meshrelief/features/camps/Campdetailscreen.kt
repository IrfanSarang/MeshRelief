package com.meshrelief.features.camps

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampDetailScreen(
    campId: String,
    onBack: () -> Unit,
    onNavigateClick: () -> Unit,
    viewModel: CampDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(campId) {
        viewModel.loadCamp(campId)
    }

    LaunchedEffect(uiState.showSnackbar) {
        if (uiState.showSnackbar) {
            snackbarHostState.showSnackbar(uiState.snackbarMessage)
            viewModel.clearSnackbar()
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.camp?.name ?: "Camp Detail",
                        fontWeight = FontWeight.Bold,
                        color = MeshDark,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = MeshDark
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = MeshGray
    ) { paddingValues ->

        uiState.camp?.let { camp ->

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
            ) {

                // ── Hero Canvas ──────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCampHero(this, camp.type)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color(0xCC000000)),
                                    startY = 200f,
                                    endY = 600f
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = camp.name,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                        Text(
                            text = camp.type + " Camp",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ── Occupancy Card ────────────────────────────────────────────
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Occupancy",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = MeshDark
                            )
                            val pct = (camp.currentOccupancy.toFloat() / camp.capacity * 100).toInt()
                            val badgeColor = when {
                                pct > 90 -> MeshRed
                                pct >= 70 -> MeshAmber
                                else -> MeshGreen
                            }
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = badgeColor.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = "$pct%",
                                    color = badgeColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "${camp.currentOccupancy} / ${camp.capacity} people",
                            color = MeshMid,
                            fontSize = 13.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        val fraction = camp.currentOccupancy.toFloat() / camp.capacity
                        val barColor = when {
                            fraction > 0.90f -> MeshRed
                            fraction >= 0.70f -> MeshAmber
                            else -> MeshGreen
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(MeshGray)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(barColor)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ── Info Grid ─────────────────────────────────────────────────
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Camp Info",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = MeshDark
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            InfoCell(
                                label = "Type",
                                value = camp.type,
                                modifier = Modifier.weight(1f)
                            )
                            InfoCell(
                                label = "Established",
                                value = camp.established,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            InfoCell(
                                label = "Admin Contact",
                                value = camp.adminContact,
                                modifier = Modifier.weight(1f)
                            )
                            InfoCell(
                                label = "Last Updated",
                                value = camp.lastUpdated,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ── Resources ─────────────────────────────────────────────────
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Resources",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = MeshDark
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        camp.resources.forEach { resource ->
                            ResourceRow(resource = resource)
                            if (resource != camp.resources.last()) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    color = MeshGray,
                                    thickness = 1.dp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ── Admin Notes ───────────────────────────────────────────────
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MeshGreenLight),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "\uD83D\uDCCB", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Admin Notes",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = MeshGreenDark
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = camp.adminNotes,
                            color = MeshDark,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ── Action Buttons ────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onNavigateClick,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MeshGreen),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, MeshGreen)
                    ) {
                        Text(
                            text = "\uD83D\uDDFA  Navigate on Map",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                    Button(
                        onClick = { viewModel.openBroadcastSheet() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MeshGreen)
                    ) {
                        Text(
                            text = "\uD83D\uDCE1  Broadcast Update",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // ── Broadcast Bottom Sheet ─────────────────────────────────────
            if (uiState.showBroadcastSheet) {
                ModalBottomSheet(
                    onDismissRequest = { viewModel.closeBroadcastSheet() },
                    sheetState = sheetState,
                    containerColor = Color.White,
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 32.dp)
                    ) {
                        Text(
                            text = "Broadcast Update",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MeshDark
                        )
                        Text(
                            text = "Message will be sent to all peers on the mesh",
                            color = MeshMid,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                        )
                        OutlinedTextField(
                            value = uiState.broadcastMessage,
                            onValueChange = { viewModel.onBroadcastMessageChange(it) },
                            placeholder = {
                                Text(
                                    text = "Type your update for ${camp.name}...",
                                    color = MeshMid
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MeshGreen,
                                unfocusedBorderColor = MeshGray
                            ),
                            maxLines = 5
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.sendBroadcast() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MeshGreen),
                            enabled = uiState.broadcastMessage.isNotBlank()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = null,
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Send to Mesh",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Composable Helpers ────────────────────────────────────────────────────────

@Composable
private fun InfoCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            color = MeshMid,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            color = MeshDark,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ResourceRow(resource: CampResource) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = resource.emoji, fontSize = 20.sp)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = resource.name,
            color = MeshDark,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        val (label, color) = when (resource.status) {
            ResourceStatus.AVAILABLE -> Pair("Available", MeshGreen)
            ResourceStatus.LOW       -> Pair("Low", MeshAmber)
            ResourceStatus.OUT       -> Pair("Out", MeshRed)
        }
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = color.copy(alpha = 0.12f)
        ) {
            Text(
                text = label,
                color = color,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

// ── Canvas Hero Drawing ───────────────────────────────────────────────────────

private fun drawCampHero(scope: DrawScope, type: String) {
    val w = scope.size.width
    val h = scope.size.height

    // Sky gradient
    scope.drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF1B4F3A), Color(0xFF2E7D57)),
            startY = 0f,
            endY = h
        ),
        size = Size(w, h)
    )

    // Ground
    scope.drawRect(
        color = Color(0xFF8B6914),
        topLeft = Offset(0f, h * 0.72f),
        size = Size(w, h * 0.28f)
    )

    // Trees (left side)
    drawTree(scope, Offset(w * 0.08f, h * 0.55f), h * 0.35f)
    drawTree(scope, Offset(w * 0.18f, h * 0.62f), h * 0.28f)

    // Trees (right side)
    drawTree(scope, Offset(w * 0.85f, h * 0.58f), h * 0.32f)
    drawTree(scope, Offset(w * 0.93f, h * 0.65f), h * 0.25f)

    // Main tent
    drawTent(scope, Offset(w * 0.5f, h * 0.72f), w * 0.38f, h * 0.32f, Color(0xFFE8D5B0))

    // Small tent
    drawTent(scope, Offset(w * 0.25f, h * 0.72f), w * 0.22f, h * 0.20f, Color(0xFFCBE8D5))

    // Flag pole
    scope.drawLine(
        color = Color(0xFF8B6914),
        start = Offset(w * 0.5f - 2f, h * 0.40f),
        end = Offset(w * 0.5f - 2f, h * 0.72f),
        strokeWidth = 4f
    )
    // Flag
    val flagPath = Path().apply {
        moveTo(w * 0.5f - 2f, h * 0.40f)
        lineTo(w * 0.5f + w * 0.07f, h * 0.44f)
        lineTo(w * 0.5f - 2f, h * 0.48f)
        close()
    }
    scope.drawPath(flagPath, color = MeshRed)
}

private fun drawTree(scope: DrawScope, base: Offset, height: Float) {
    // Trunk
    scope.drawRect(
        color = Color(0xFF5C3D11),
        topLeft = Offset(base.x - 4f, base.y - height * 0.18f),
        size = Size(8f, height * 0.18f)
    )
    // Foliage layers
    listOf(0f, height * 0.18f, height * 0.34f).forEachIndexed { i, offset ->
        val layerWidth = height * 0.55f - i * (height * 0.1f)
        val layerPath = Path().apply {
            moveTo(base.x, base.y - height * 0.22f - offset)
            lineTo(base.x - layerWidth / 2f, base.y - offset)
            lineTo(base.x + layerWidth / 2f, base.y - offset)
            close()
        }
        scope.drawPath(layerPath, color = Color(0xFF1E6E3C).copy(alpha = 0.9f - i * 0.1f))
    }
}

private fun drawTent(scope: DrawScope, base: Offset, width: Float, height: Float, color: Color) {
    val tentPath = Path().apply {
        moveTo(base.x, base.y - height)
        lineTo(base.x - width / 2f, base.y)
        lineTo(base.x + width / 2f, base.y)
        close()
    }
    scope.drawPath(tentPath, color = color)
    scope.drawPath(
        tentPath,
        color = MeshGreenDark.copy(alpha = 0.4f),
        style = Stroke(width = 2f)
    )
    // Door flap
    val doorPath = Path().apply {
        moveTo(base.x, base.y - height)
        lineTo(base.x - width * 0.12f, base.y)
        lineTo(base.x + width * 0.12f, base.y)
        close()
    }
    scope.drawPath(doorPath, color = Color(0xFF6B4F1A).copy(alpha = 0.5f))
}