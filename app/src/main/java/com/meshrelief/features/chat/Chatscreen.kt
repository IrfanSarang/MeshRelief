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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.meshrelief.R
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
            BottomNavBar(
                onHomeClick = onHomeClick,
                onChatClick = {},
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
                        text = stringResource(R.string.chat_title),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MeshDark
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ChatTab(
                        label = stringResource(R.string.chat_tab_group),
                        unread = unreadGroup,
                        selected = selectedTab == 0,
                        modifier = Modifier.weight(1f)
                    ) { selectedTab = 0 }

                    ChatTab(
                        label = stringResource(R.string.chat_tab_p2p),
                        unread = unreadP2p,
                        selected = selectedTab == 1,
                        modifier = Modifier.weight(1f)
                    ) { selectedTab = 1 }
                }

                HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 0.5.dp)
            }

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
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupChatContent(viewModel: ChatViewModel) {
    val messages by viewModel.groupMessages.collectAsState()
    val input by viewModel.groupInput.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(modifier = Modifier.fillMaxSize()) {
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
                    text = stringResource(R.string.chat_group_context),
                    fontSize = 9.sp,
                    color = MeshGreenDark
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().background(MeshGray),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                MessageBubble(message = message)
            }
        }

        ChatInputBar(
            value = input,
            onValueChange = viewModel::onGroupInputChange,
            onSend = viewModel::sendGroupMessage,
            placeholder = stringResource(R.string.chat_input_group_placeholder)
        )
    }
}

@Composable
private fun P2PPeerListContent(viewModel: ChatViewModel) {
    val peers by viewModel.peers.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(MeshGray)) {
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
                    text = stringResource(R.string.chat_p2p_hint),
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
                PeerListItem(peer = peer, onClick = { viewModel.selectPeer(peer) })
            }
        }
    }
}

@Composable
private fun PeerListItem(peer: ChatPeer, onClick: () -> Unit) {
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
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape).background(MeshGreenLight),
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

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${peer.name} (••••${peer.idSuffix})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MeshDark
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(triageColor))
            }
            Text(
                text = if (peer.hopCount == 1)
                    stringResource(R.string.status_hop_one, peer.hopCount)
                else
                    stringResource(R.string.status_hop_other, peer.hopCount),
                fontSize = 10.sp,
                color = MeshMid
            )
        }

        if (peer.unreadCount > 0) {
            Box(
                modifier = Modifier.size(20.dp).clip(CircleShape).background(MeshRed),
                contentAlignment = Alignment.Center
            ) {
                Text(text = peer.unreadCount.toString(), fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
        } else {
            Text(text = "›", fontSize = 18.sp, color = Color(0xFFCCCCCC))
        }
    }
}

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

    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
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
                    contentDescription = null,
                    tint = MeshMid,
                    modifier = Modifier.size(14.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Box(
                modifier = Modifier.size(32.dp).clip(CircleShape).background(MeshGreenLight),
                contentAlignment = Alignment.Center
            ) {
                Text(text = peer.name.take(2).uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MeshGreenDark)
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
                    Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(triageColor))
                }
                Text(
                    text = if (peer.hopCount == 1)
                        stringResource(R.string.chat_private_channel, peer.hopCount)
                    else
                        stringResource(R.string.chat_private_channel_plural, peer.hopCount),
                    fontSize = 9.sp,
                    color = MeshGreen
                )
            }
        }

        HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 0.5.dp)

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().background(MeshGray),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                MessageBubble(message = message)
            }
        }

        ChatInputBar(
            value = input,
            onValueChange = viewModel::onP2pInputChange,
            onSend = viewModel::sendP2pMessage,
            placeholder = "Message ${peer.name}…"
        )
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    if (message.isSystemMessage) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.05f))
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text(text = message.text, fontSize = 9.sp, color = Color(0xFF888888))
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
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(max = 260.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 10.dp, bottomEnd = 2.dp))
                        .background(MeshGreen)
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    Text(text = message.text, fontSize = 11.sp, color = Color.White, lineHeight = 16.sp)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = timeStr, fontSize = 8.sp, color = Color.Black.copy(alpha = 0.35f))
            }
        } else {
            Column(horizontalAlignment = Alignment.Start, modifier = Modifier.widthIn(max = 260.dp)) {
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
                            Text(text = "${message.hopCount}↗", fontSize = 8.sp, color = MeshGreen)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 10.dp, bottomStart = 10.dp, bottomEnd = 10.dp))
                        .background(Color.White)
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    Text(text = message.text, fontSize = 11.sp, color = MeshDark, lineHeight = 16.sp)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = timeStr, fontSize = 8.sp, color = Color.Black.copy(alpha = 0.35f))
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
                Text(text = placeholder, fontSize = 11.sp, color = Color(0xFFBBBBBB))
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(fontSize = 11.sp, color = MeshDark),
                cursorBrush = SolidColor(MeshGreen),
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

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
                contentDescription = null,
                tint = if (value.isNotBlank()) Color.White else MeshMid,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}