package com.meshrelief.features.notifications

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import kotlin.math.abs

// ─── Colour helpers per type ─────────────────────────────────────────────────

private fun typeColor(type: NotificationType): Color = when (type) {
    NotificationType.SOS -> MeshRed
    NotificationType.PEER -> MeshGreen
    NotificationType.BULLETIN -> MeshAmber
    NotificationType.CAMP -> Color(0xFF5B8CDE)
    NotificationType.SYSTEM -> MeshMid
}

private fun typeEmoji(type: NotificationType): String = when (type) {
    NotificationType.SOS -> "🆘"
    NotificationType.PEER -> "👤"
    NotificationType.BULLETIN -> "📢"
    NotificationType.CAMP -> "🏕"
    NotificationType.SYSTEM -> "⚙"
}

// ─── Timestamp formatting ─────────────────────────────────────────────────────

private fun relativeTime(millis: Long): String =
    NotificationsViewModel.relativeTime(millis)

// ─── Screen ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    viewModel: NotificationsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val filtered = state.filtered

    Scaffold(
        containerColor = MeshGray,
        topBar = {
            NotificationsTopBar(
                unreadCount = state.unreadCount,
                onBack = onBack,
                onMarkAllRead = { viewModel.markAllRead() }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Filter chip row
            FilterChipRow(
                activeFilter = state.activeFilter,
                onFilterSelected = { viewModel.setFilter(it) }
            )

            Spacer(modifier = Modifier.height(4.dp))

            if (filtered.isEmpty()) {
                // Empty state
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyBellState()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = filtered,
                        key = { it.id }
                    ) { notification ->
                        SwipeableNotificationItem(
                            notification = notification,
                            onTap = { viewModel.markAsRead(notification.id) },
                            onDismiss = { viewModel.dismiss(notification.id) }
                        )
                    }

                    item {
                        // Clear all button at bottom
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            TextButton(onClick = { viewModel.clearFiltered() }) {
                                Text(
                                    text = "Clear all",
                                    color = MeshRed,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

// ─── Top Bar ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationsTopBar(
    unreadCount: Int,
    onBack: () -> Unit,
    onMarkAllRead: () -> Unit
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MeshGreenDark,
            titleContentColor = Color.White,
            navigationIconContentColor = Color.White,
            actionIconContentColor = Color.White
        ),
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
        },
        title = {
            Column {
                Text(
                    text = "Notifications",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    text = if (unreadCount > 0) "$unreadCount unread" else "All caught up",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.75f)
                )
            }
        },
        actions = {
            if (unreadCount > 0) {
                TextButton(onClick = onMarkAllRead) {
                    Text(
                        text = "Mark all read",
                        color = Color.White,
                        fontSize = 13.sp
                    )
                }
            }
        }
    )
}

// ─── Filter Chip Row ─────────────────────────────────────────────────────────

@Composable
private fun FilterChipRow(
    activeFilter: NotificationFilter,
    onFilterSelected: (NotificationFilter) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(NotificationFilter.entries.toTypedArray()) { filter ->
            val selected = filter == activeFilter
            FilterChip(
                selected = selected,
                onClick = { onFilterSelected(filter) },
                label = {
                    Text(
                        text = filter.label,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MeshGreen,
                    selectedLabelColor = Color.White,
                    containerColor = Color.White,
                    labelColor = MeshDark
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selected,
                    selectedBorderColor = MeshGreen,
                    borderColor = Color.LightGray,
                    borderWidth = 1.dp,
                    selectedBorderWidth = 0.dp
                )
            )
        }
    }
}

// ─── Swipeable Notification Item ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableNotificationItem(
    notification: MeshNotification,
    onTap: () -> Unit,
    onDismiss: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDismiss()
                true
            } else false
        },
        positionalThreshold = { totalDistance -> totalDistance * 0.40f }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            // Red delete background revealed on swipe
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MeshRed),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = Color.White,
                    modifier = Modifier.padding(end = 20.dp)
                )
            }
        }
    ) {
        NotificationCard(
            notification = notification,
            onTap = onTap
        )
    }
}

// ─── Notification Card ────────────────────────────────────────────────────────

@Composable
private fun NotificationCard(
    notification: MeshNotification,
    onTap: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (!notification.isRead) MeshGreenLight else Color.White,
        animationSpec = tween(durationMillis = 400),
        label = "bg_color"
    )
    val accent = typeColor(notification.type)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable { onTap() }
            .padding(0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Colored left strip + icon
        Box(
            modifier = Modifier
                .width(44.dp)
                .fillMaxHeight()
                .background(accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Coloured vertical bar on the very left edge
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(72.dp)
                        .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                        .background(accent)
                )
            }
        }

        // Type icon + content
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp, top = 12.dp, bottom = 12.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Type emoji badge
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = typeEmoji(notification.type), fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Text content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = notification.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MeshDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = notification.body,
                    fontSize = 12.sp,
                    color = MeshMid,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = relativeTime(notification.timestampMillis),
                    fontSize = 11.sp,
                    color = MeshMid.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Unread dot
            if (!notification.isRead) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(MeshGreen)
                )
            } else {
                Spacer(modifier = Modifier.size(9.dp))
            }
        }
    }
}

// ─── Empty State ─────────────────────────────────────────────────────────────

@Composable
private fun EmptyBellState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(32.dp)
    ) {
        Canvas(
            modifier = Modifier
                .size(120.dp)
                .padding(8.dp)
        ) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val bellColor = MeshGreen.copy(alpha = 0.25f)
            val strokeColor = MeshGreen.copy(alpha = 0.6f)
            val strokeW = 5f

            // Bell body (rounded rectangle bell silhouette)
            drawRoundRect(
                color = bellColor,
                topLeft = Offset(cx - w * 0.32f, h * 0.15f),
                size = Size(w * 0.64f, h * 0.55f),
                cornerRadius = CornerRadius(w * 0.18f, w * 0.18f)
            )
            drawRoundRect(
                color = strokeColor,
                topLeft = Offset(cx - w * 0.32f, h * 0.15f),
                size = Size(w * 0.64f, h * 0.55f),
                cornerRadius = CornerRadius(w * 0.18f, w * 0.18f),
                style = Stroke(width = strokeW)
            )

            // Bell stem / handle at top
            drawLine(
                color = strokeColor,
                start = Offset(cx, h * 0.06f),
                end = Offset(cx, h * 0.16f),
                strokeWidth = strokeW
            )

            // Bell base / clapper
            drawRoundRect(
                color = bellColor,
                topLeft = Offset(cx - w * 0.38f, h * 0.67f),
                size = Size(w * 0.76f, h * 0.08f),
                cornerRadius = CornerRadius(8f, 8f)
            )
            drawRoundRect(
                color = strokeColor,
                topLeft = Offset(cx - w * 0.38f, h * 0.67f),
                size = Size(w * 0.76f, h * 0.08f),
                cornerRadius = CornerRadius(8f, 8f),
                style = Stroke(width = strokeW)
            )

            // Clapper dot
            drawCircle(
                color = strokeColor,
                radius = w * 0.06f,
                center = Offset(cx, h * 0.83f)
            )

            // Small checkmark lines inside bell to hint "all clear"
            val checkCx = cx
            val checkCy = h * 0.44f
            drawLine(
                color = MeshGreen,
                start = Offset(checkCx - w * 0.10f, checkCy),
                end = Offset(checkCx - w * 0.02f, checkCy + h * 0.08f),
                strokeWidth = strokeW * 1.2f
            )
            drawLine(
                color = MeshGreen,
                start = Offset(checkCx - w * 0.02f, checkCy + h * 0.08f),
                end = Offset(checkCx + w * 0.14f, checkCy - h * 0.09f),
                strokeWidth = strokeW * 1.2f
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "All caught up",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = MeshDark
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "No notifications here.\nNew mesh events will appear when they arrive.",
            fontSize = 13.sp,
            color = MeshMid,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 18.sp
        )
    }
}