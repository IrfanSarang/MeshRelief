package com.meshrelief.features.camps

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.meshrelief.features.home.MeshAmber
import com.meshrelief.features.home.MeshDark
import com.meshrelief.features.home.MeshGray
import com.meshrelief.features.home.MeshGreen
import com.meshrelief.features.home.MeshGreenDark
import com.meshrelief.features.home.MeshGreenLight
import com.meshrelief.features.home.MeshMid
import com.meshrelief.features.home.MeshRed
import kotlinx.coroutines.launch
import androidx.compose.foundation.BorderStroke

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCampScreen(
    onBack: () -> Unit,
    onBroadcast: () -> Unit,
    viewModel: AddCampViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(uiState.showSnackbar) {
        if (uiState.showSnackbar) {
            scope.launch {
                snackbarHostState.showSnackbar(uiState.snackbarMessage)
                viewModel.onSnackbarDismissed()
            }
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    containerColor = MeshGreenDark,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(data.visuals.message, fontSize = 13.sp)
                }
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Register Camp",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MeshDark
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = MeshMid
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        },
        containerColor = MeshGray
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {

            // ── Decorative tent banner ──
            TentBannerCanvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .background(Color.White)
            )

            Spacer(Modifier.height(12.dp))

            // ── Basic Info Card ──
            FormCard(title = "Basic Information") {
                // Camp Name
                FormLabel("Camp Name *")
                OutlinedTextField(
                    value = uiState.campName,
                    onValueChange = viewModel::onCampNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("e.g. Nagpur Relief Camp A", color = MeshMid, fontSize = 13.sp) },
                    singleLine = true,
                    isError = uiState.campNameError != null,
                    colors = meshTextFieldColors(),
                    shape = RoundedCornerShape(10.dp)
                )
                if (uiState.campNameError != null) {
                    Text(
                        uiState.campNameError!!,
                        color = MeshRed,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Camp Type chips
                FormLabel("Camp Type")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CampType.values().forEach { type ->
                        val selected = uiState.campType == type
                        TypeChip(
                            label = type.label,
                            selected = selected,
                            onClick = { viewModel.onCampTypeChange(type) }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Capacity
                FormLabel("Capacity *")
                OutlinedTextField(
                    value = uiState.capacity,
                    onValueChange = viewModel::onCapacityChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("e.g. 200", color = MeshMid, fontSize = 13.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = uiState.capacityError != null,
                    colors = meshTextFieldColors(),
                    shape = RoundedCornerShape(10.dp)
                )
                if (uiState.capacityError != null) {
                    Text(
                        uiState.capacityError!!,
                        color = MeshRed,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Admin Contact
                FormLabel("Admin Contact Name")
                OutlinedTextField(
                    value = uiState.adminContact,
                    onValueChange = viewModel::onAdminContactChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("e.g. Raj Patil", color = MeshMid, fontSize = 13.sp) },
                    singleLine = true,
                    colors = meshTextFieldColors(),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(Modifier.height(12.dp))

                // Admin Notes
                FormLabel("Admin Notes (optional)")
                OutlinedTextField(
                    value = uiState.adminNotes,
                    onValueChange = viewModel::onAdminNotesChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 90.dp),
                    placeholder = { Text("Any special instructions or notes...", color = MeshMid, fontSize = 13.sp) },
                    minLines = 3,
                    colors = meshTextFieldColors(),
                    shape = RoundedCornerShape(10.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            // ── Resources Card ──
            FormCard(title = "Resource Availability") {
                uiState.resources.forEachIndexed { index, resource ->
                    ResourceStatusRow(
                        resource = resource,
                        onStatusChange = { status -> viewModel.onResourceStatusChange(index, status) }
                    )
                    if (index < uiState.resources.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = MeshGray
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Location Card ──
            FormCard(title = "Location") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        FormLabel("Latitude")
                        OutlinedTextField(
                            value = uiState.latitude,
                            onValueChange = viewModel::onLatitudeChange,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = meshTextFieldColors(),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        FormLabel("Longitude")
                        OutlinedTextField(
                            value = uiState.longitude,
                            onValueChange = viewModel::onLongitudeChange,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = meshTextFieldColors(),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { viewModel.fetchGpsLocation() },
                    enabled = !uiState.isLocating,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MeshGreen,
                        disabledContentColor = MeshMid
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (uiState.isLocating) MeshMid else MeshGreen
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (uiState.isLocating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = MeshMid,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Locating...", fontSize = 13.sp)
                    } else {
                        Text("📍  Use my location", fontSize = 13.sp)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Submit button ──
            Button(
                onClick = { viewModel.submitCamp(onSuccess = onBroadcast) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(50.dp),
                enabled = !uiState.isSubmitting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MeshGreen,
                    contentColor = Color.White,
                    disabledContainerColor = MeshGreen.copy(alpha = 0.5f),
                    disabledContentColor = Color.White.copy(alpha = 0.7f)
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (uiState.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Registering...", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                } else {
                    Text("Register + Broadcast", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ── Composables ──────────────────────────────────────────────────────────────

@Composable
private fun FormCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MeshGreenDark
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun FormLabel(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = MeshMid,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
private fun TypeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) MeshGreenLight else Color.White,
        animationSpec = tween(150),
        label = "chip_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) MeshGreen else Color(0xFFD3D1C7),
        animationSpec = tween(150),
        label = "chip_border"
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) MeshGreenDark else MeshMid,
        animationSpec = tween(150),
        label = "chip_text"
    )

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .border(0.5.dp, borderColor, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(label, fontSize = 12.sp, color = textColor, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ResourceStatusRow(
    resource: ResourceRow,
    onStatusChange: (ResourceStatus) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(resource.emoji, fontSize = 18.sp)
        Spacer(Modifier.width(8.dp))
        Text(
            resource.name,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MeshDark,
            modifier = Modifier.weight(1f)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            StatusChip(
                label = "Available",
                selected = resource.status == ResourceStatus.AVAILABLE,
                selectedColor = MeshGreen,
                onClick = { onStatusChange(ResourceStatus.AVAILABLE) }
            )
            StatusChip(
                label = "Low",
                selected = resource.status == ResourceStatus.LOW,
                selectedColor = MeshAmber,
                onClick = { onStatusChange(ResourceStatus.LOW) }
            )
            StatusChip(
                label = "Out",
                selected = resource.status == ResourceStatus.OUT,
                selectedColor = MeshRed,
                onClick = { onStatusChange(ResourceStatus.OUT) }
            )
        }
    }
}

@Composable
private fun StatusChip(
    label: String,
    selected: Boolean,
    selectedColor: Color,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) selectedColor.copy(alpha = 0.15f) else Color.Transparent,
        animationSpec = tween(120),
        label = "status_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) selectedColor else Color(0xFFD3D1C7),
        animationSpec = tween(120),
        label = "status_border"
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) selectedColor else MeshMid,
        animationSpec = tween(120),
        label = "status_text"
    )

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(0.5.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(label, fontSize = 10.sp, color = textColor, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun TentBannerCanvas(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val green = MeshGreen
        val greenLight = MeshGreenLight
        val greenDark = MeshGreenDark

        // Background fill
        drawRect(color = Color.White, size = size)

        // Ground line
        drawLine(
            color = greenLight,
            start = Offset(0f, h * 0.85f),
            end = Offset(w, h * 0.85f),
            strokeWidth = 2f
        )

        // Draw 3 tents evenly spaced
        val tentPositions = listOf(w * 0.2f, w * 0.5f, w * 0.8f)
        val tentSizes = listOf(80f, 100f, 80f)

        tentPositions.forEachIndexed { i, cx ->
            val tw = tentSizes[i]
            drawTent(cx, h * 0.85f, tw, green, greenLight, greenDark)
        }

        // Subtle dots (stars / sky feel)
        listOf(
            Offset(w * 0.1f, h * 0.2f),
            Offset(w * 0.35f, h * 0.15f),
            Offset(w * 0.65f, h * 0.25f),
            Offset(w * 0.9f, h * 0.18f)
        ).forEach { pos ->
            drawCircle(color = greenLight, radius = 3f, center = pos)
        }
    }
}

private fun DrawScope.drawTent(
    cx: Float,
    groundY: Float,
    width: Float,
    bodyColor: Color,
    lightColor: Color,
    darkColor: Color
) {
    val halfW = width / 2f
    val tentH = width * 0.65f
    val peakY = groundY - tentH
    val roofOverhang = width * 0.1f

    // Tent body (triangle)
    val bodyPath = Path().apply {
        moveTo(cx, peakY)
        lineTo(cx + halfW, groundY)
        lineTo(cx - halfW, groundY)
        close()
    }
    drawPath(bodyPath, color = bodyColor)

    // Tent roof highlight (slightly lighter top strip)
    val highlightPath = Path().apply {
        moveTo(cx, peakY)
        lineTo(cx + halfW * 0.4f, peakY + tentH * 0.35f)
        lineTo(cx - halfW * 0.4f, peakY + tentH * 0.35f)
        close()
    }
    drawPath(highlightPath, color = lightColor)

    // Tent door (small dark rectangle at base center)
    val doorW = width * 0.22f
    val doorH = tentH * 0.35f
    drawRect(
        color = darkColor,
        topLeft = Offset(cx - doorW / 2f, groundY - doorH),
        size = Size(doorW, doorH)
    )

    // Roof edge line
    val roofPath = Path().apply {
        moveTo(cx, peakY - 4f)
        lineTo(cx + halfW + roofOverhang, groundY)
        lineTo(cx - halfW - roofOverhang, groundY)
        close()
    }
    drawPath(roofPath, color = darkColor.copy(alpha = 0.25f))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun meshTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MeshGreen,
    unfocusedBorderColor = Color(0xFFD3D1C7),
    focusedContainerColor = Color.White,
    unfocusedContainerColor = MeshGray,
    cursorColor = MeshGreen,
    focusedTextColor = MeshDark,
    unfocusedTextColor = MeshDark,
    errorBorderColor = MeshRed,
    errorContainerColor = Color(0xFFFFF0F0)
)