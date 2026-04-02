package com.meshrelief.features.firstaid

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.meshrelief.features.home.MeshAmber
import com.meshrelief.features.home.MeshDark
import com.meshrelief.features.home.MeshGray
import com.meshrelief.features.home.MeshGreen
import com.meshrelief.features.home.MeshGreenDark
import com.meshrelief.features.home.MeshGreenLight
import com.meshrelief.features.home.MeshMid
import com.meshrelief.features.home.MeshRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirstAidScreen(
    onBack: () -> Unit,
    viewModel: FirstAidViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LocalHospital,
                            contentDescription = null,
                            tint = MeshGreen,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "First Aid Guide",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MeshDark
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MeshDark
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        },
        containerColor = MeshGray
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {

            // ── Search bar ────────────────────────────────────────────
            Surface(
                color = Color.White,
                shadowElevation = 2.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {

                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = viewModel::onSearchQueryChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        placeholder = {
                            Text(
                                text = "Search symptoms, conditions…",
                                color = MeshMid,
                                fontSize = 14.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = null,
                                tint = MeshMid
                            )
                        },
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Clear",
                                        tint = MeshMid
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MeshGreen,
                            unfocusedBorderColor = Color(0xFFDDDDD8),
                            cursorColor = MeshGreen
                        ),
                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, color = MeshDark)
                    )

                    // ── Category chips ────────────────────────────────
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FirstAidCategory.entries.forEach { cat ->
                            val selected = uiState.selectedCategory == cat
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.onCategorySelected(cat) },
                                label = {
                                    Text(
                                        text = cat.label,
                                        fontSize = 12.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MeshGreen,
                                    selectedLabelColor = Color.White,
                                    containerColor = Color.White,
                                    labelColor = MeshMid
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selected,
                                    selectedBorderColor = MeshGreen,
                                    borderColor = Color(0xFFDDDDD8)
                                )
                            )
                        }
                    }
                }
            }

            // ── Entry list ────────────────────────────────────────────
            if (uiState.filteredEntries.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.SearchOff,
                            contentDescription = null,
                            tint = MeshMid,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No results found",
                            color = MeshMid,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Try a different keyword or category",
                            color = MeshMid.copy(alpha = 0.7f),
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.filteredEntries, key = { it.id }) { entry ->
                        FirstAidCard(
                            entry = entry,
                            isExpanded = uiState.expandedEntryId == entry.id,
                            onToggle = { viewModel.onEntryToggle(entry.id) }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }
            }
        }
    }
}

@Composable
private fun FirstAidCard(
    entry: FirstAidEntry,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onToggle() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            // ── Collapsed header ──────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Severity dot
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            color = entry.severity.badgeColor(),
                            shape = RoundedCornerShape(50)
                        )
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = MeshDark,
                        maxLines = if (isExpanded) Int.MAX_VALUE else 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SeverityBadge(severity = entry.severity)
                        Text(
                            text = "• ${entry.category.label}",
                            fontSize = 11.sp,
                            color = MeshMid
                        )
                    }
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MeshMid,
                    modifier = Modifier.size(22.dp)
                )
            }

            // ── Expanded content ──────────────────────────────────────
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(animationSpec = tween(200)) + fadeIn(tween(200)),
                exit = shrinkVertically(animationSpec = tween(180)) + fadeOut(tween(180))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
                ) {

                    HorizontalDivider(color = Color(0xFFEEEDE8), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(10.dp))

                    // Steps
                    Text(
                        text = "STEPS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MeshGreenDark,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    entry.steps.forEachIndexed { index, step ->
                        StepRow(number = index + 1, text = step)
                        if (index < entry.steps.lastIndex) {
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }

                    // Do NOTs block
                    if (entry.doNots.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        DoNotBlock(doNots = entry.doNots)
                    }
                }
            }
        }
    }
}

@Composable
private fun StepRow(number: Int, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(MeshGreenLight, RoundedCornerShape(50)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number.toString(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MeshGreenDark
            )
        }
        Text(
            text = text,
            fontSize = 13.sp,
            color = MeshDark,
            lineHeight = 19.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun DoNotBlock(doNots: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MeshRed.copy(alpha = 0.07f), RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(bottom = 6.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = MeshRed,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = "DO NOT",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MeshRed,
                letterSpacing = 1.sp
            )
        }
        doNots.forEach { warning ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Top,
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                Text(text = "•", fontSize = 13.sp, color = MeshRed)
                Text(
                    text = warning.removePrefix("Do NOT ").removePrefix("Do Not ").let { "Do NOT $it" },
                    fontSize = 12.sp,
                    color = MeshRed.copy(alpha = 0.85f),
                    lineHeight = 17.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SeverityBadge(severity: Severity) {
    Box(
        modifier = Modifier
            .background(
                color = severity.badgeColor().copy(alpha = 0.12f),
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = severity.name,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = severity.badgeColor(),
            letterSpacing = 0.5.sp
        )
    }
}

private fun Severity.badgeColor(): Color = when (this) {
    Severity.CRITICAL -> MeshRed
    Severity.URGENT   -> MeshAmber
    Severity.MODERATE -> MeshGreen
}