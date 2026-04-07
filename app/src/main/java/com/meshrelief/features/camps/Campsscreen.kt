package com.meshrelief.features.camps

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.meshrelief.features.home.BottomNavBar
import com.meshrelief.features.home.MeshAmber
import com.meshrelief.features.home.MeshDark
import com.meshrelief.features.home.MeshGray
import com.meshrelief.features.home.MeshGreen
import com.meshrelief.features.home.MeshMid
import com.meshrelief.features.home.MeshRed

enum class CampFilter(val label: String) {
    ALL("All"),
    ACTIVE("Active"),
    FULL("Full"),
    NEARBY("Nearby")
}

@Composable
fun CampFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 12.sp) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MeshGreen,
            selectedLabelColor = Color.White
        )
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampsScreen(
    onHomeClick: () -> Unit,
    onChatClick: () -> Unit,
    onMapClick: () -> Unit,
    onStatusClick: () -> Unit,
    onChatbotClick: () -> Unit,
    onCampClick: (String) -> Unit,
    onAddCampClick: () -> Unit,
    viewModel: CampDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = MeshGray,
        topBar = { CampsTopBar() },
        bottomBar = {
            BottomNavBar(
                onHomeClick = onHomeClick,
                onChatClick = onChatClick,
                onMapClick = onMapClick,
                onStatusClick = onStatusClick,
                onChatbotClick = onChatbotClick,
                currentRoute = "camps"
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddCampClick,
                containerColor = MeshGreen,
                contentColor = Color.White,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add camp", modifier = Modifier.size(22.dp))
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(CampFilter.entries.toTypedArray()) { filter ->
                    CampFilterChip(label = filter.label, selected = uiState.filter == filter, onClick = { viewModel.setFilter(filter) })
                }
            }
            HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 0.5.dp)
            val filtered = uiState.filtered
            Text(
                text = "${filtered.size} camp${if (filtered.size != 1) "s" else ""} nearby",
                fontSize = 11.sp, color = MeshMid,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
            )
            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No camps found", fontSize = 14.sp, color = MeshMid, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(4.dp))
                        Text("Try a different filter", fontSize = 11.sp, color = Color(0xFFAAAAAA))
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { it.id }) { camp ->
                        CampCard(camp = camp, onClick = { onCampClick(camp.id) })
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampsTopBar() {
    TopAppBar(
        title = { Text("Camps", fontWeight = FontWeight.Bold) },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.White,
            titleContentColor = MeshDark
        )
    )
}

@Composable
fun CampCard(camp: CampDetail, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            // Left: colored bar showing occupancy level
            val fraction = camp.currentOccupancy.toFloat() / camp.capacity
            val barColor = when {
                fraction > 0.90f -> MeshRed
                fraction >= 0.70f -> MeshAmber
                else -> MeshGreen
            }
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(56.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(barColor)
            )

            Spacer(Modifier.width(12.dp))

            // Middle: camp name, type, occupancy, last updated
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = camp.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MeshDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${camp.type} · ${camp.currentOccupancy}/${camp.capacity} people",
                    fontSize = 12.sp,
                    color = MeshMid
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Updated ${camp.lastUpdated}",
                    fontSize = 11.sp,
                    color = Color(0xFFAAAAAA)
                )
            }

            Spacer(Modifier.width(10.dp))

            // Right: occupancy % badge
            val pct = (fraction * 100).toInt()
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = barColor.copy(alpha = 0.12f)
            ) {
                Text(
                    text = "$pct%",
                    color = barColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}