package com.meshrelief.features.navigate

import android.graphics.Paint
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.DashPathEffect
import android.graphics.Path
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.meshrelief.core.util.Constants
import com.meshrelief.ui.theme.MeshAmber
import com.meshrelief.ui.theme.MeshDark
import com.meshrelief.ui.theme.MeshGray
import com.meshrelief.ui.theme.MeshGreen
import com.meshrelief.ui.theme.MeshGreenDark
import com.meshrelief.ui.theme.MeshGreenLight
import com.meshrelief.ui.theme.MeshMid
import com.meshrelief.ui.theme.MeshRed
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigateToCampScreen(
    campId: String,
    onBack: () -> Unit,
    viewModel: NavigateToCampViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(campId) {
        viewModel.loadCamp(campId)
    }

    // ── Configure OSMDroid local cache (same paths as MapScreen/SetupViewModel) ─
    LaunchedEffect(Unit) {
        Configuration.getInstance().apply {
            userAgentValue              = context.packageName
            tileFileSystemCacheMaxBytes =
                Constants.MAP_TILE_CACHE_MB * 1024L * 1024L           // 200 MB
            osmdroidBasePath            = File(context.filesDir, "osmdroid")
            osmdroidTileCache           = File(context.filesDir, "osmdroid/tiles")
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.registerCompass()
                Lifecycle.Event.ON_PAUSE  -> viewModel.unregisterCompass()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val animatedNeedle by animateFloatAsState(
        targetValue = state.compassHeadingDeg,
        animationSpec = tween(durationMillis = 200),
        label = "compassNeedle"
    )

    val distanceText = if (state.distanceKm < 1.0) {
        "${(state.distanceKm * 1000).toInt()} m"
    } else {
        "${"%.1f".format(state.distanceKm)} km"
    }

    val walkLabel = if (state.estimatedMinutes < 1) "< 1 min" else "~${state.estimatedMinutes} min"

    val ctaLabel = if (state.isNavigating) {
        "Navigating... heading ${state.directionLabel}"
    } else {
        "Start walking"
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(bottom = 8.dp)
            ) {
                Spacer(Modifier.height(40.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = MeshDark
                        )
                    }
                    Column {
                        Text(
                            text = if (state.isLoaded) state.campName else "Navigate to camp",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MeshDark
                        )
                        if (state.isLoaded) {
                            Text(
                                text = "${state.campType} \u00B7 $distanceText ${state.directionLabel}",
                                fontSize = 12.sp,
                                color = MeshMid
                            )
                        }
                    }
                }
                Divider(color = Color(0xFFEEEEEE), thickness = 0.5.dp)
            }
        },
        containerColor = MeshGray
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.55f)
            ) {
                if (state.isLoaded) {
                    OsmNavigationMap(
                        userLat  = state.userLatitude,
                        userLng  = state.userLongitude,
                        campLat  = state.campLatitude,
                        campLng  = state.campLongitude,
                        campName = state.campName
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MeshGreenLight),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MeshGreen)
                    }
                }

                // Compass rose
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.95f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "\u2191",
                        fontSize = 22.sp,
                        color = Color(0xFFE24B4A),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.rotate(-animatedNeedle),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "N",
                        fontSize = 8.sp,
                        color = MeshDark,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 4.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.45f)
                    .background(Color.White)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatBox(value = distanceText,        label = "km away",   modifier = Modifier.weight(1f))
                    StatBox(value = state.directionLabel, label = "direction", modifier = Modifier.weight(1f))
                    StatBox(value = walkLabel,            label = "min walk",  modifier = Modifier.weight(1f))
                }

                Spacer(Modifier.height(4.dp))

                if (state.isLoaded) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MeshGreenLight)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = "\uD83E\uDDED", fontSize = 14.sp)
                        Text(
                            text = "Head ${state.directionLabel} from your position. " +
                                    "Camp is ${distanceText} away.",
                            fontSize = 12.sp,
                            color = MeshGreenDark,
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                Button(
                    onClick = { viewModel.startWalking() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.isNavigating) MeshGreenDark else MeshGreen,
                        contentColor   = Color.White
                    )
                ) {
                    if (state.isNavigating) {
                        Icon(
                            imageVector     = Icons.Default.ArrowBack,
                            contentDescription = null,
                            modifier        = Modifier.size(16.dp).rotate(90f),
                            tint            = Color.White
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(text = ctaLabel, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ── Stat box ──────────────────────────────────────────────────────────────────

@Composable
private fun StatBox(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MeshGray)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
            color = MeshDark, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(2.dp))
        Text(text = label, fontSize = 10.sp, color = MeshMid, textAlign = TextAlign.Center)
    }
}

// ── OSMDroid map composable ───────────────────────────────────────────────────

@Composable
private fun OsmNavigationMap(
    userLat: Double,
    userLng: Double,
    campLat: Double,
    campLng: Double,
    campName: String
) {
    val context       = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            // ── Offline-only: read from local cache, never hit the network ──
            setUseDataConnection(false)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE  -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = Modifier.fillMaxSize(),
        update = { map ->
            map.overlays.clear()

            val userPoint = GeoPoint(userLat, userLng)
            val campPoint = GeoPoint(campLat, campLng)

            // Dashed polyline
            val polyline = Polyline(map).apply {
                setPoints(listOf(userPoint, campPoint))
                outlinePaint.color       = MeshGreen.toArgb()
                outlinePaint.strokeWidth = 6f
                outlinePaint.pathEffect  = DashPathEffect(floatArrayOf(18f, 12f), 0f)
                outlinePaint.isAntiAlias = true
                outlinePaint.style       = Paint.Style.STROKE
            }
            map.overlays.add(polyline)

            // User marker
            map.overlays.add(Marker(map).apply {
                position = userPoint
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                title = "You"
                icon  = buildBlueDotDrawable(context)
            })

            // Camp marker
            map.overlays.add(Marker(map).apply {
                position = campPoint
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title   = campName
                snippet = "Relief camp"
                icon    = buildGreenPinDrawable(context)
            })

            // Zoom to fit both points
            val boundingBox = BoundingBox.fromGeoPoints(listOf(userPoint, campPoint))
            map.post { map.zoomToBoundingBox(boundingBox, true, 80) }
        }
    )
}

// ── Marker builders ───────────────────────────────────────────────────────────

private fun buildBlueDotDrawable(context: android.content.Context): android.graphics.drawable.Drawable {
    val size   = 36
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val cx = size / 2f; val cy = size / 2f
    canvas.drawCircle(cx, cy, cx, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE; style = Paint.Style.FILL
    })
    canvas.drawCircle(cx, cy, cx - 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.parseColor("#378ADD"); style = Paint.Style.FILL
    })
    return BitmapDrawable(context.resources, bitmap)
}

private fun buildGreenPinDrawable(context: android.content.Context): android.graphics.drawable.Drawable {
    val w = 40; val h = 50
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)

    val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.parseColor("#1D9E75"); style = Paint.Style.FILL
    }
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.parseColor("#085041")
        style = Paint.Style.STROKE; strokeWidth = 2f
    }
    val rect = android.graphics.RectF(2f, 2f, w - 2f, h - 14f)
    canvas.drawRoundRect(rect, 10f, 10f, bodyPaint)
    canvas.drawRoundRect(rect, 10f, 10f, borderPaint)

    canvas.drawPath(Path().apply {
        moveTo(w / 2f - 8f, h - 14f)
        lineTo(w / 2f + 8f, h - 14f)
        lineTo(w / 2f, h.toFloat())
        close()
    }, bodyPaint)

    canvas.drawCircle(w / 2f, (h - 14f) / 2f, 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE; style = Paint.Style.FILL
    })

    return BitmapDrawable(context.resources, bitmap)
}