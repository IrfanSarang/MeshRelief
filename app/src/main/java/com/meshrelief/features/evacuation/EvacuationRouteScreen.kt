package com.meshrelief.features.evacuation

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.meshrelief.features.home.MeshAmber
import com.meshrelief.features.home.MeshDark
import com.meshrelief.features.home.MeshGray
import com.meshrelief.features.home.MeshGreen
import com.meshrelief.features.home.MeshGreenDark
import com.meshrelief.features.home.MeshGreenLight
import com.meshrelief.features.home.MeshMid
import com.meshrelief.features.home.MeshRed
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun EvacuationRouteScreen(
    onBack: () -> Unit,
    viewModel: EvacuationRouteViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var routeLabelText by remember { mutableStateOf("") }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }

    Scaffold(
        containerColor = MeshGray,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Evacuation Route",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MeshDark
                        )
                        Text(
                            text = "Admin \u00b7 Draw safe path \u00b7 broadcasts to mesh",
                            fontSize = 10.sp,
                            color = MeshMid
                        )
                    }
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
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Admin mode banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFAEEDA))
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "\u26a0\ufe0f  Admin mode \u2014 Tap map to place waypoints",
                    fontSize = 11.sp,
                    color = Color(0xFF854F0B),
                    fontWeight = FontWeight.Medium
                )
            }

            // Waypoint count bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${uiState.waypoints.size} waypoint${if (uiState.waypoints.size != 1) "s" else ""} set",
                    fontSize = 11.sp,
                    color = if (uiState.waypoints.size >= 2) MeshGreen else MeshMid,
                    fontWeight = FontWeight.Medium
                )
                if (uiState.waypoints.size < 2) {
                    Text(
                        text = "Tap map to start",
                        fontSize = 10.sp,
                        color = MeshMid
                    )
                } else {
                    Text(
                        text = "Route ready",
                        fontSize = 10.sp,
                        color = MeshGreen,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 0.5.dp)

            // Map
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                AndroidView(
                    factory = { ctx ->
                        MapView(ctx).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                            controller.setZoom(16.0)
                            controller.setCenter(GeoPoint(19.0760, 72.8777))
                            mapViewRef = this
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInteropFilter { event ->
                            if (event.action == MotionEvent.ACTION_UP) {
                                val map = mapViewRef
                                if (map != null) {
                                    val proj = map.projection
                                    val geoPoint = proj.fromPixels(
                                        event.x.toInt(),
                                        event.y.toInt()
                                    ) as GeoPoint
                                    viewModel.addWaypoint(geoPoint.latitude, geoPoint.longitude)
                                }
                            }
                            false
                        },
                    update = { map ->
                        map.overlays.clear()
                        val pts = uiState.waypoints
                        if (pts.size >= 2) {
                            val polyline = Polyline().apply {
                                setPoints(pts.map { GeoPoint(it.lat, it.lng) })
                                outlinePaint.color = android.graphics.Color.parseColor("#E24B4A")
                                outlinePaint.strokeWidth = 6f
                            }
                            map.overlays.add(polyline)
                        }
                        pts.forEachIndexed { index, wp ->
                            val marker = Marker(map).apply {
                                position = GeoPoint(wp.lat, wp.lng)
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                title = if (index == 0) "Start" else if (index == pts.lastIndex) "End" else "Point ${index + 1}"
                            }
                            map.overlays.add(marker)
                        }
                        map.invalidate()
                    }
                )

                // Undo button overlay
                if (uiState.waypoints.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                    ) {
                        TextButton(
                            onClick = { viewModel.undoLast() },
                            colors = ButtonDefaults.textButtonColors(
                                containerColor = Color.White.copy(alpha = 0.95f)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.border(
                                0.5.dp,
                                Color(0xFFF09595),
                                RoundedCornerShape(8.dp)
                            )
                        ) {
                            Text(
                                text = "Undo last",
                                fontSize = 11.sp,
                                color = MeshRed
                            )
                        }
                    }
                }

                // Waypoint counter badge
                if (uiState.waypoints.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp)
                            .background(Color.White.copy(alpha = 0.95f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${uiState.waypoints.size} / 10 waypoints",
                            fontSize = 10.sp,
                            color = MeshDark
                        )
                    }
                }
            }

            // Bottom panel
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "Route label", fontSize = 11.sp, color = MeshMid)

                OutlinedTextField(
                    value = routeLabelText,
                    onValueChange = {
                        routeLabelText = it
                        viewModel.setRouteLabel(it)
                    },
                    placeholder = {
                        Text(
                            text = "e.g. Safe route to Xavier School camp",
                            fontSize = 11.sp,
                            color = Color(0xFFAAAAAA)
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MeshGreen,
                        unfocusedBorderColor = Color(0xFFD3D1C7),
                        focusedContainerColor = MeshGray,
                        unfocusedContainerColor = MeshGray,
                        focusedTextColor = MeshDark,
                        unfocusedTextColor = MeshDark
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Clear all
                    OutlinedButton(
                        onClick = { viewModel.clearAll() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color(0xFFFCEBEB),
                            contentColor = Color(0xFFA32D2D)
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            0.5.dp,
                            Color(0xFFF09595)
                        )
                    ) {
                        Text(text = "Clear all", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }

                    // Broadcast
                    Button(
                        onClick = {
                            viewModel.broadcastRoute(onDone = onBack)
                        },
                        enabled = uiState.waypoints.size >= 2 && !uiState.isBroadcasting,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MeshGreen,
                            contentColor = Color.White,
                            disabledContainerColor = Color(0xFFB0B0A8),
                            disabledContentColor = Color.White
                        )
                    ) {
                        if (uiState.isBroadcasting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(text = "Broadcasting...", fontSize = 12.sp)
                        } else if (uiState.broadcastSuccess) {
                            Text(text = "\u2713 Sent!", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        } else {
                            Text(text = "Broadcast route", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                if (uiState.waypoints.size < 2) {
                    Text(
                        text = "Add at least 2 waypoints to broadcast",
                        fontSize = 10.sp,
                        color = MeshMid,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}