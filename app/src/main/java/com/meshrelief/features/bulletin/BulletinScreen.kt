package com.meshrelief.features.bulletin

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.meshrelief.R
import com.meshrelief.features.home.BottomNavBar
import com.meshrelief.ui.theme.MeshAmber
import com.meshrelief.ui.theme.MeshDark
import com.meshrelief.ui.theme.MeshGray
import com.meshrelief.ui.theme.MeshGreen
import com.meshrelief.ui.theme.MeshGreenDark
import com.meshrelief.ui.theme.MeshGreenLight
import com.meshrelief.ui.theme.MeshMid
import com.meshrelief.ui.theme.MeshRed

private fun BulletinCategory.accentColor() = when (this) {
    BulletinCategory.EVACUATION -> MeshRed
    BulletinCategory.MEDICAL    -> MeshAmber
    BulletinCategory.RESOURCES  -> MeshGreen
    BulletinCategory.GENERAL    -> MeshMid
}

private fun BulletinCategory.bgColor() = when (this) {
    BulletinCategory.EVACUATION -> Color(0xFFFCEBEB)
    BulletinCategory.MEDICAL    -> Color(0xFFFAEEDA)
    BulletinCategory.RESOURCES  -> MeshGreenLight
    BulletinCategory.GENERAL    -> Color(0xFFF4F3F0)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulletinScreen(
    onBack: () -> Unit,
    onHomeClick: () -> Unit,
    onChatClick: () -> Unit,
    onMapClick: () -> Unit,
    onStatusClick: () -> Unit,
    onChatbotClick: () -> Unit,
    viewModel: BulletinViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val displayList = remember(state.bulletins, state.selectedFilter) {
        viewModel.filteredBulletins()
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        containerColor = MeshGray,
        topBar = { BulletinTopBar(onBack = onBack) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.openSheet() },
                containerColor = MeshGreen,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.padding(bottom = 64.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.bulletin_compose_title))
            }
        },
        bottomBar = {
            BottomNavBar(
                onHomeClick = onHomeClick,
                onChatClick = onChatClick,
                onMapClick = onMapClick,
                onStatusClick = onStatusClick,
                onChatbotClick = onChatbotClick,
                currentRoute = "bulletin"
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            BulletinFilterRow(selected = state.selectedFilter, onSelect = { viewModel.setFilter(it) })

            if (displayList.isEmpty()) {
                BulletinEmptyState(modifier = Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(displayList, key = { it.id }) { item ->
                        BulletinCard(item = item, relativeTime = viewModel.relativeTime(item.timestampMillis))
                    }
                }
            }
        }
    }

    if (state.isSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.closeSheet() },
            sheetState = sheetState,
            containerColor = Color.White,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 4.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFFD3D1C7))
                )
            }
        ) {
            ComposeSheet(
                state = state,
                onCategorySelect = viewModel::setComposeCategory,
                onTextChange = viewModel::setComposeText,
                onBroadcast = viewModel::broadcast,
                onDismiss = viewModel::closeSheet
            )
        }
    }
}

@Composable
private fun BulletinTopBar(onBack: () -> Unit) {
    Surface(color = Color.White, shadowElevation = 1.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(MeshGray)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = null, tint = MeshMid, modifier = Modifier.size(18.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.bulletin_title),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MeshDark
                )
                Text(
                    text = stringResource(R.string.bulletin_subtitle),
                    fontSize = 10.sp,
                    color = MeshMid
                )
            }
            MegaphoneIcon(tint = MeshGreen)
        }
    }
}

@Composable
private fun MegaphoneIcon(tint: Color) {
    Canvas(modifier = Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.55f, h * 0.25f)
            lineTo(w * 0.18f, h * 0.38f)
            lineTo(w * 0.18f, h * 0.62f)
            lineTo(w * 0.55f, h * 0.75f)
            close()
        }
        drawPath(path, color = tint)
        drawArc(
            color = tint,
            startAngle = -65f, sweepAngle = 130f, useCenter = false,
            topLeft = Offset(w * 0.48f, h * 0.15f),
            size = androidx.compose.ui.geometry.Size(w * 0.38f, h * 0.70f),
            style = Stroke(width = 2.2f, cap = StrokeCap.Round)
        )
        drawLine(color = tint, start = Offset(w * 0.26f, h * 0.62f), end = Offset(w * 0.26f, h * 0.80f), strokeWidth = 2.2f, cap = StrokeCap.Round)
    }
}

@Composable
private fun BulletinFilterRow(selected: BulletinCategory?, onSelect: (BulletinCategory?) -> Unit) {
    Surface(color = Color.White, shadowElevation = 0.5.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            FilterChip(
                label = stringResource(R.string.bulletin_filter_all),
                isSelected = selected == null,
                onClick = { onSelect(null) }
            )
            BulletinCategory.values().forEach { cat ->
                FilterChip(
                    label = cat.label,
                    isSelected = selected == cat,
                    accentColor = cat.accentColor(),
                    onClick = { onSelect(cat) }
                )
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, isSelected: Boolean, accentColor: Color = MeshGreen, onClick: () -> Unit) {
    val bg = if (isSelected) accentColor.copy(alpha = 0.12f) else Color.White
    val border = if (isSelected) accentColor else Color(0xFFD3D1C7)
    val textColor = if (isSelected) accentColor else MeshMid

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(0.5.dp, border, RoundedCornerShape(20.dp))
    ) {
        TextButton(
            onClick = onClick,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            modifier = Modifier.height(30.dp)
        ) {
            Text(label, fontSize = 11.sp, color = textColor, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}

@Composable
private fun BulletinCard(item: BulletinItem, relativeTime: String) {
    val isPinned = item.category == BulletinCategory.EVACUATION

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .border(0.5.dp, Color(0xFFEAE9E3), RoundedCornerShape(12.dp))
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(item.category.accentColor(), RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
        )
        Column(
            modifier = Modifier.weight(1f).padding(start = 11.dp, end = 11.dp, top = 10.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CategoryTag(category = item.category)
                if (isPinned) PinIcon(tint = MeshRed)
                Spacer(modifier = Modifier.weight(1f))
                if (item.isRelayed) RelayedIcon()
            }
            Text(text = item.message, fontSize = 12.sp, color = MeshDark, lineHeight = 17.sp, maxLines = 5, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val initials = item.senderName.split(" ").take(2).mapNotNull { it.firstOrNull()?.toString() }.joinToString("")
                Box(
                    modifier = Modifier.size(18.dp).clip(CircleShape).background(item.category.accentColor().copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(initials, fontSize = 7.sp, color = item.category.accentColor(), fontWeight = FontWeight.Bold)
                }
                Text(text = item.senderName, fontSize = 10.sp, color = MeshMid, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = relativeTime, fontSize = 9.sp, color = Color(0xFFAAAAAA))
            }
        }
    }
}

@Composable
private fun CategoryTag(category: BulletinCategory) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(category.bgColor())
            .border(0.5.dp, category.accentColor().copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(text = category.label.uppercase(), fontSize = 8.sp, color = category.accentColor(), fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
    }
}

@Composable
private fun PinIcon(tint: Color) {
    Canvas(modifier = Modifier.size(12.dp)) {
        val cx = size.width / 2f; val cy = size.height / 2f
        drawCircle(color = tint, radius = cx * 0.7f, center = Offset(cx, cy))
        drawLine(color = tint, start = Offset(cx, cy + cx * 0.7f), end = Offset(cx, size.height), strokeWidth = 1.5f, cap = StrokeCap.Round)
    }
}

@Composable
private fun RelayedIcon() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Canvas(modifier = Modifier.size(12.dp)) {
            val cx = size.width / 2f; val cy = size.height * 0.65f
            listOf(0.85f, 0.55f, 0.30f).forEachIndexed { i, radius ->
                drawArc(
                    color = MeshGreen.copy(alpha = 0.8f - i * 0.15f),
                    startAngle = 200f, sweepAngle = 140f, useCenter = false,
                    topLeft = Offset(cx - size.width * radius, cy - size.height * radius),
                    size = androidx.compose.ui.geometry.Size(size.width * radius * 2, size.height * radius * 2),
                    style = Stroke(width = 1.2f, cap = StrokeCap.Round)
                )
            }
            drawCircle(color = MeshGreen, radius = 1.2f, center = Offset(cx, cy))
        }
        Text(stringResource(R.string.bulletin_relayed), fontSize = 8.sp, color = MeshGreen)
    }
}

@Composable
private fun BulletinEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Canvas(modifier = Modifier.size(80.dp)) {
            val w = size.width; val h = size.height
            val bodyPath = Path().apply {
                moveTo(w * 0.58f, h * 0.22f); lineTo(w * 0.20f, h * 0.38f)
                lineTo(w * 0.20f, h * 0.65f); lineTo(w * 0.58f, h * 0.78f); close()
            }
            drawPath(bodyPath, color = MeshGreen.copy(alpha = 0.15f))
            drawPath(bodyPath, color = MeshGreen.copy(alpha = 0.5f), style = Stroke(width = 2.5f, cap = StrokeCap.Round))
            drawArc(color = MeshGreen.copy(alpha = 0.5f), startAngle = -70f, sweepAngle = 140f, useCenter = false,
                topLeft = Offset(w * 0.50f, h * 0.12f), size = androidx.compose.ui.geometry.Size(w * 0.40f, h * 0.76f),
                style = Stroke(width = 2.5f, cap = StrokeCap.Round))
            drawLine(color = MeshGreen.copy(alpha = 0.5f), start = Offset(w * 0.28f, h * 0.65f), end = Offset(w * 0.28f, h * 0.84f), strokeWidth = 2.5f, cap = StrokeCap.Round)
            listOf(0.78f, 0.88f).forEach { rx ->
                drawLine(color = MeshGreen.copy(alpha = 0.3f), start = Offset(w * rx, h * 0.40f), end = Offset(w * rx, h * 0.60f), strokeWidth = 2f, cap = StrokeCap.Round)
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(stringResource(R.string.bulletin_none_title), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MeshDark)
        Spacer(modifier = Modifier.height(4.dp))
        Text(stringResource(R.string.bulletin_none_sub), fontSize = 12.sp, color = MeshMid)
    }
}

@Composable
private fun ComposeSheet(
    state: BulletinUiState,
    onCategorySelect: (BulletinCategory) -> Unit,
    onTextChange: (String) -> Unit,
    onBroadcast: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(stringResource(R.string.bulletin_compose_title), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MeshDark)

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.bulletin_compose_category), fontSize = 11.sp, color = MeshMid)
            Row(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                BulletinCategory.values().forEach { cat ->
                    val isSelected = state.composeCategory == cat
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) cat.accentColor() else Color.White)
                            .border(1.dp, cat.accentColor(), RoundedCornerShape(20.dp))
                    ) {
                        TextButton(
                            onClick = { onCategorySelect(cat) },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 5.dp)
                        ) {
                            Text(cat.label, fontSize = 11.sp, color = if (isSelected) Color.White else cat.accentColor(), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.bulletin_compose_message), fontSize = 11.sp, color = MeshMid)
                Text("${state.composeText.length}/280", fontSize = 10.sp, color = if (state.composeText.length > 250) MeshRed else MeshMid)
            }
            BasicTextField(
                value = state.composeText,
                onValueChange = onTextChange,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MeshGray)
                    .border(0.5.dp, Color(0xFFD3D1C7), RoundedCornerShape(10.dp))
                    .padding(12.dp)
                    .heightIn(min = 90.dp),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = MeshDark, lineHeight = 19.sp),
                decorationBox = { innerTextField ->
                    if (state.composeText.isEmpty()) {
                        Text(stringResource(R.string.bulletin_compose_placeholder), fontSize = 13.sp, color = MeshMid.copy(alpha = 0.6f), lineHeight = 19.sp)
                    }
                    innerTextField()
                }
            )
        }

        Button(
            onClick = onBroadcast,
            enabled = state.composeText.trim().isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(46.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MeshGreen, disabledContainerColor = Color(0xFFD3D1C7)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(stringResource(R.string.bulletin_broadcast), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(6.dp))
    }
}