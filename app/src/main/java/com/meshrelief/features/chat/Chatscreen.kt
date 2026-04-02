package com.meshrelief.features.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.meshrelief.features.home.BottomNavBar
import com.meshrelief.features.home.MeshAmber
import com.meshrelief.features.home.MeshDark
import com.meshrelief.features.home.MeshGray
import com.meshrelief.features.home.MeshGreen
import com.meshrelief.features.home.MeshGreenDark
import com.meshrelief.features.home.MeshGreenLight
import com.meshrelief.features.home.MeshMid
import com.meshrelief.features.home.MeshRed
import java.text.SimpleDateFormat
import java.util.*

// ─────────────────────────────────────────────────────────────────────────────
// Entry point — routes between peer list and active P2P conversation
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ChatScreen(
    onHomeClick: () -> Unit,
    onMapClick: () -> Unit,
    onStatusClick: () -> Unit,
    onChatbotClick: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val selectedPeer by viewModel.selectedPeer.collectAsState()

    if (selectedPeer != null) {
        // P2P conversation — hide bottom nav (immersive chat)
        P2PChatConversationScreen(
            peer = selectedPeer!!,
            viewModel = viewModel,
            onBack = { viewModel.clearSelectedPeer() }
        )
    } else {
        ChatTabsScreen(
            viewModel = viewModel,
            onHomeClick = onHomeClick,
            onMapClick = onMapClick,
            onStatusClick = onStatusClick,
            onChatbotClick = onChatbotClick
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tab host: Group Chat | P2P Chat
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ChatTabsScreen(
    viewModel: ChatViewModel,
    onHomeClick: () -> Unit,
    onMapClick: () -> Unit,
    onStatusClick: () -> Unit,
    onChatbotClick: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val unreadGroup by viewModel.unreadGroup.collectAsState()
    val unreadP2p by viewModel.unreadP2p.collectAsState()

    LaunchedEffect(selectedTab) {
        if (selectedTab == 0) viewModel.clearGroupUnread()
    }

    Scaffold(
        bottomBar = {
            com.meshrelief.features.home.BottomNavBar(
                onHomeClick = onHomeClick,
                onChatClick = {},          // already on Chat
                onMapClick = onMapClick,
                onStatusClick = onStatusClick,
                onChatbotClick = onChatbotClick,
                currentRoute = "chat"
            )
        },
        containerColor = Color.White
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.White)
        ) {
            // ── Header ────────────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Mesh Chat",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MeshDark
                    )
                }

                // Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ChatTab(
                        label = "Group",
                        unread = unreadGroup,
                        selected = selectedTab == 0,
                        modifier = Modifier.weight(1f)
                    ) { selectedTab = 0 }

                    ChatTab(
                        label = "P2P",
                        unread = unreadP2p,
                        selected = selectedTab == 1,
                        modifier = Modifier.weight(1f)
                    ) { selectedTab = 1 }
                }

                HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 0.5.dp)
            }

            // ── Content ───────────────────────────────────────────────────────
            when (selectedTab) {
                0 -> GroupChatContent(viewModel = viewModel)
                1 -> P2PPeerListContent(viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun ChatTab(
    label: String,
    unread: Int,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) MeshGreen else MeshGray)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (selected) Color.White else MeshMid
            )
            if (unread > 0) {
                Spacer(modifier = Modifier.width(5.dp))
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(if (selected) Color.White.copy(alpha = 0.3f) else MeshRed),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (unread > 9) "9+" else unread.toString(),
                        fontSize = 8.sp,
                        color = if (selected) Color.White else Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Group Chat content
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun GroupChatContent(viewModel: ChatViewModel) {
    val messages by viewModel.groupMessages.collectAsState()
    val input by viewModel.groupInput.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // System context pill
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(MeshGreenLight)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Group · All peers on mesh · relayed to everyone",
                    fontSize = 9.sp,
                    color = MeshGreenDark
                )
            }
        }

        // Messages
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MeshGray),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                MessageBubble(message = message)
            }
        }

        // Input bar
        ChatInputBar(
            value = input,
            onValueChange = viewModel::onGroupInputChange,
            onSend = viewModel::sendGroupMessage,
            placeholder = "Message all peers…"
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// P2P peer list
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun P2PPeerListContent(viewModel: ChatViewModel) {
    val peers by viewModel.peers.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MeshGray)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFFF1EFE8))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Tap a peer to open a private channel",
                    fontSize = 9.sp,
                    color = MeshMid
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(peers, key = { it.deviceId }) { peer ->
                PeerListItem(
                    peer = peer,
                    onClick = { viewModel.selectPeer(peer) }
                )
            }
        }
    }
}

@Composable
private fun PeerListItem(
    peer: ChatPeer,
    onClick: () -> Unit
) {
    val triageColor = when (peer.triageColor) {
        "green" -> MeshGreen
        "yellow" -> MeshAmber
        "red" -> MeshRed
        else -> Color(0xFF888888)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MeshGreenLight),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = peer.name.take(2).uppercase(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MeshGreenDark
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Name + meta
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${peer.name} (••••${peer.idSuffix})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MeshDark
                )
                Spacer(modifier = Modifier.width(6.dp))
                // Triage dot
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(triageColor)
                )
            }
            Text(
                text = "${peer.hopCount} hop${if (peer.hopCount != 1) "s" else ""} away",
                fontSize = 10.sp,
                color = MeshMid
            )
        }

        // Unread badge
        if (peer.unreadCount > 0) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(MeshRed),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = peer.unreadCount.toString(),
                    fontSize = 9.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            // Chevron hint
            Text(text = "›", fontSize = 18.sp, color = Color(0xFFCCCCCC))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// P2P Conversation screen (opened after selecting a peer)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun P2PChatConversationScreen(
    peer: ChatPeer,
    viewModel: ChatViewModel,
    onBack: () -> Unit
) {
    val messages by viewModel.p2pMessages.collectAsState()
    val input by viewModel.p2pInput.collectAsState()
    val listState = rememberLazyListState()

    val triageColor = when (peer.triageColor) {
        "green" -> MeshGreen
        "yellow" -> MeshAmber
        "red" -> MeshRed
        else -> Color(0xFF888888)
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MeshGray)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = MeshMid,
                    modifier = Modifier.size(14.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MeshGreenLight),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = peer.name.take(2).uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MeshGreenDark
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${peer.name} (••••${peer.idSuffix})",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MeshDark
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(triageColor)
                    )
                }
                Text(
                    text = "${peer.hopCount} hop${if (peer.hopCount != 1) "s" else ""} · Private channel",
                    fontSize = 9.sp,
                    color = MeshGreen
                )
            }
        }

        HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 0.5.dp)

        // Messages
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MeshGray),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                MessageBubble(message = message)
            }
        }

        // Input bar
        ChatInputBar(
            value = input,
            onValueChange = viewModel::onP2pInputChange,
            onSend = viewModel::sendP2pMessage,
            placeholder = "Message ${peer.name}…"
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared components
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MessageBubble(message: ChatMessage) {
    if (message.isSystemMessage) {
        // System / join notification pill
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.05f))
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text(
                    text = message.text,
                    fontSize = 9.sp,
                    color = Color(0xFF888888)
                )
            }
        }
        return
    }

    val timeStr = remember(message.timestampMs) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestampMs))
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isOutgoing) Arrangement.End else Arrangement.Start
    ) {
        if (message.isOutgoing) {
            // Outgoing bubble (green, right-aligned)
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.widthIn(max = 260.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(
                            RoundedCornerShape(
                                topStart = 10.dp, topEnd = 10.dp,
                                bottomStart = 10.dp, bottomEnd = 2.dp
                            )
                        )
                        .background(MeshGreen)
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = message.text,
                        fontSize = 11.sp,
                        color = Color.White,
                        lineHeight = 16.sp
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = timeStr,
                    fontSize = 8.sp,
                    color = Color.Black.copy(alpha = 0.35f)
                )
            }
        } else {
            // Incoming bubble (white, left-aligned)
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.widthIn(max = 260.dp)
            ) {
                // Sender name + hop badge row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = "${message.senderName} (••••${message.senderIdSuffix})",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        color = MeshGreenDark
                    )
                    if (message.hopCount > 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(MeshGreenLight)
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "${message.hopCount}↗",
                                fontSize = 8.sp,
                                color = MeshGreen
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .clip(
                            RoundedCornerShape(
                                topStart = 2.dp, topEnd = 10.dp,
                                bottomStart = 10.dp, bottomEnd = 10.dp
                            )
                        )
                        .background(Color.White)
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = message.text,
                        fontSize = 11.sp,
                        color = MeshDark,
                        lineHeight = 16.sp
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = timeStr,
                    fontSize = 8.sp,
                    color = Color.Black.copy(alpha = 0.35f)
                )
            }
        }
    }
}

@Composable
private fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    placeholder: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Text field
        Box(
            modifier = Modifier
                .weight(1f)
                .height(38.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MeshGray)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    fontSize = 11.sp,
                    color = Color(0xFFBBBBBB)
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(
                    fontSize = 11.sp,
                    color = MeshDark
                ),
                cursorBrush = SolidColor(MeshGreen),
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Send button
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(if (value.isNotBlank()) MeshGreen else MeshGray)
                .clickable(enabled = value.isNotBlank()) { onSend() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Send,
                contentDescription = "Send",
                tint = if (value.isNotBlank()) Color.White else MeshMid,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}