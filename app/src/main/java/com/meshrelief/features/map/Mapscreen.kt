package com.meshrelief.features.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.WifiOff              // ← NEW
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.meshrelief.core.model.TriageStatus
import com.meshrelief.features.home.BottomNavBar
import com.meshrelief.ui.theme.MeshAmber
import com.meshrelief.ui.theme.MeshDark
import com.meshrelief.ui.theme.MeshGray
import com.meshrelief.ui.theme.MeshGreen
import com.meshrelief.ui.theme.MeshGreenDark
import com.meshrelief.ui.theme.MeshGreenLight
import com.meshrelief.ui.theme.MeshMid
import com.meshrelief.ui.theme.MeshRed
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

// ── Programmatic drawable icons (no assets needed) ───────────────────────────

/** Filled circle — used for user dot and peer dots */
private fun circleDotDrawable(context: Context, colorArgb: Int, radiusDp: Float = 14f): Drawable {
    val density = context.resources.displayMetrics.density
    val radiusPx = (radiusDp * density).toInt()
    val size = radiusPx * 2 + 4

    return object : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorArgb }
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3f * density
        }

        override fun draw(canvas: Canvas) {
            val cx = bounds.exactCenterX()
            val cy = bounds.exactCenterY()
            canvas.drawCircle(cx, cy, radiusPx.toFloat(), paint)
            canvas.drawCircle(cx, cy, radiusPx.toFloat(), strokePaint)
        }

        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(cf: android.graphics.ColorFilter?) { paint.colorFilter = cf }
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
        override fun getIntrinsicWidth() = size
        override fun getIntrinsicHeight() = size
    }
}

/** Filled square pin (flat-bottom) — used for camp markers */
private fun campPinDrawable(context: Context): Drawable {
    val density = context.resources.displayMetrics.density
    val squareSide = (18f * density).toInt()
    val stemHeight = (8f * density).toInt()
    val totalH = squareSide + stemHeight
    val totalW = squareSide + 4

    val campColor = android.graphics.Color.parseColor("#1D9E75")

    return object : Drawable() {
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = campColor }
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * density
        }
        private val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            strokeWidth = 2.5f * density
            strokeCap = Paint.Cap.ROUND
        }

        override fun draw(canvas: Canvas) {
            val left = (bounds.width() - squareSide) / 2f
            val top = 0f
            val right = left + squareSide
            val bottom = squareSide.toFloat()
            val cx = bounds.exactCenterX()

            canvas.drawRect(left, top, right, bottom, fillPaint)
            canvas.drawRect(left, top, right, bottom, strokePaint)

            val stemPath = Path().apply {
                moveTo(cx - 4 * density, bottom)
                lineTo(cx, totalH.toFloat())
                lineTo(cx + 4 * density, bottom)
                close()
            }
            canvas.drawPath(stemPath, fillPaint)

            val pad = 5 * density
            canvas.drawLine(cx, top + pad, cx, bottom - pad, crossPaint)
            canvas.drawLine(left + pad, (top + bottom) / 2, right - pad, (top + bottom) / 2, crossPaint)
        }

        override fun setAlpha(alpha: Int) { fillPaint.alpha = alpha }
        override fun setColorFilter(cf: android.graphics.ColorFilter?) { fillPaint.colorFilter = cf }
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
        override fun getIntrinsicWidth() = totalW
        override fun getIntrinsicHeight() = totalH
    }
}

private fun triageColor(triage: TriageStatus): Int = when (triage) {
    TriageStatus.GREEN   -> android.graphics.Color.parseColor("#1D9E75")
    TriageStatus.AMBER   -> android.graphics.Color.parseColor("#EF9F27")
    TriageStatus.RED     -> android.graphics.Color.parseColor("#E24B4A")
    TriageStatus.UNKNOWN -> android.graphics.Color.parseColor("#5F5E5A")
}

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun MapScreen(
    onHomeClick: () -> Unit,
    onChatClick: () -> Unit,
    onStatusClick: () -> Unit,
    onChatbotClick: () -> Unit,
    viewModel: MapViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // ── NEW: observe tile download flag ───────────────────────────────────────
    val mapTilesDownloaded by viewModel.mapTilesDownloaded.collectAsState()

    var mapViewRef by remember { mutableStateOf<MapView?>(null) }

    // ── CHANGED: replaced plain userAgentValue init with full OSMDroid config ─
    LaunchedEffect(Unit) {
        viewModel.configureOsmDroid(context)
    }

    Scaffold(
        bottomBar = {
            BottomNavBar(
                onHomeClick = onHomeClick,
                onChatClick = onChatClick,
                onMapClick = { /* already here */ },
                onStatusClick = onStatusClick,
                onChatbotClick = onChatbotClick,
                currentRoute = "map"
            )
        },
        containerColor = MeshGray
    ) { padding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {

            // ── OSMDroid MapView ───────────────────────────────────────────
            AndroidView(
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        controller.setZoom(15.5)
                        controller.setCenter(
                            uiState.userLocation ?: GeoPoint(19.0760, 72.8777)
                        )
                        // ── NEW: disable live network tile fetching ────────
                        // OSMDroid will only read from the local tile cache.
                        // Tiles downloaded during setup will still render;
                        // uncached areas show as blank until tiles are fetched.
                        setUseDataConnection(false)

                        mapViewRef = this
                    }
                },
                update = { mapView ->
                    mapView.overlays.clear()

                    // User location marker (blue dot)
                    uiState.userLocation?.let { pos ->
                        val marker = Marker(mapView).apply {
                            position = pos
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            icon = circleDotDrawable(context, Color.parseColor("#2979FF"), 16f)
                            title = "You are here"
                            snippet = null
                            setInfoWindow(null)
                        }
                        mapView.overlays.add(marker)
                    }

                    // Peer markers
                    uiState.peers.forEach { peer ->
                        val marker = Marker(mapView).apply {
                            position = peer.position
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            icon = circleDotDrawable(context, triageColor(peer.triage), 11f)
                            title = peer.name
                            snippet = "Last seen ${peer.lastSeenMinutesAgo}m ago • ${peer.triage.name}"
                        }
                        mapView.overlays.add(marker)
                    }

                    // Camp markers
                    uiState.camps.forEach { camp ->
                        val marker = Marker(mapView).apply {
                            position = camp.position
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            icon = campPinDrawable(context)
                            title = camp.name
                            snippet = "Occupancy: ${camp.currentOccupancy}/${camp.capacity}"
                        }
                        mapView.overlays.add(marker)
                    }

                    mapView.invalidate()
                },
                modifier = Modifier.fillMaxSize()
            )

            // ── Top bar overlay ────────────────────────────────────────────
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                color = MeshGreenDark.copy(alpha = 0.93f),
                shadowElevation = 4.dp
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Mesh Map",
                                color = androidx.compose.ui.graphics.Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Offline • OpenStreetMap",
                                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f),
                                fontSize = 11.sp
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MeshGreen
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(
                                                androidx.compose.ui.graphics.Color.White,
                                                CircleShape
                                            )
                                    )
                                    Text(
                                        text = "${uiState.peers.size} peers",
                                        color = androidx.compose.ui.graphics.Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            IconButton(
                                onClick = { viewModel.toggleLegend() },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(MeshGreen.copy(alpha = 0.3f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Legend",
                                    tint = androidx.compose.ui.graphics.Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // ── NEW: offline tiles warning banner ──────────────────
                    // Shown only when MAP_TILES_DOWNLOADED == false.
                    // Sits just below the title row, inside the same Surface.
                    if (!mapTilesDownloaded) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MeshAmber.copy(alpha = 0.92f))
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.WifiOff,
                                contentDescription = null,
                                tint = androidx.compose.ui.graphics.Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Map tiles not downloaded. Connect to WiFi to download offline maps.",
                                color = androidx.compose.ui.graphics.Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // ── FAB — Re-centre ────────────────────────────────────────────
            FloatingActionButton(
                onClick = {
                    viewModel.onRecenterRequested()
                    uiState.userLocation?.let { loc ->
                        mapViewRef?.controller?.animateTo(loc, 15.5, 800)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp),
                containerColor = MeshGreenDark,
                contentColor = androidx.compose.ui.graphics.Color.White,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Re-centre map",
                    modifier = Modifier.size(22.dp)
                )
            }

            // ── Legend overlay ─────────────────────────────────────────────
            AnimatedVisibility(
                visible = uiState.showLegend,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 16.dp)
            ) {
                LegendCard()
            }
        }
    }
}

// ── Legend card ───────────────────────────────────────────────────────────────

@Composable
private fun LegendCard() {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MeshDark.copy(alpha = 0.92f),
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "MAP LEGEND",
                color = MeshGreenLight,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            LegendRow(color = androidx.compose.ui.graphics.Color(0xFF2979FF), label = "Your location",        shape = "circle")
            LegendRow(color = MeshGreen,                                       label = "Peer — Stable (Green)", shape = "circle")
            LegendRow(color = MeshAmber,                                       label = "Peer — Caution (Amber)",shape = "circle")
            LegendRow(color = MeshRed,                                         label = "Peer — Critical (Red)", shape = "circle")
            LegendRow(color = androidx.compose.ui.graphics.Color(0xFF5F5E5A), label = "Peer — Unknown",        shape = "circle")
            LegendRow(color = MeshGreen,                                       label = "Relief camp",           shape = "square")
        }
    }
}

@Composable
private fun LegendRow(
    color: androidx.compose.ui.graphics.Color,
    label: String,
    shape: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(
                    color,
                    if (shape == "circle") CircleShape else RoundedCornerShape(2.dp)
                )
        )
        Text(
            text = label,
            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.88f),
            fontSize = 12.sp
        )
    }
}