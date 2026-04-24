package com.meshrelief

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.meshrelief.core.event.AppEventBus
import com.meshrelief.features.admin.AdminScreen
import com.meshrelief.features.bulletin.BulletinScreen
import com.meshrelief.features.camps.AddCampScreen
import com.meshrelief.features.camps.CampDetailScreen
import com.meshrelief.features.camps.CampsScreen
import com.meshrelief.features.chat.ChatScreen
import com.meshrelief.features.chatbot.ChatbotScreen
import com.meshrelief.features.discovery.DiscoveryScreen
import com.meshrelief.features.evacuation.EvacuationRouteScreen
import com.meshrelief.features.firstaid.FirstAidScreen
import com.meshrelief.features.home.HomeScreen
import com.meshrelief.features.map.MapScreen
import com.meshrelief.features.setup.LanguageSelectionScreen
import com.meshrelief.features.setup.SetupScreen
import com.meshrelief.features.sos.IncomingSosAlertScreen
import com.meshrelief.features.sos.SOSScreen
import com.meshrelief.mesh.protocol.PacketType
import com.meshrelief.mesh.protocol.MeshPacket
import com.meshrelief.features.status.MyStatusScreen
import com.meshrelief.features.status.StatusScreen
import com.meshrelief.features.topology.TopologyScreen
import com.meshrelief.service.MeshForegroundService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import com.meshrelief.mesh.wifi.WifiDirectManager
import javax.inject.Inject
import com.meshrelief.features.navigate.NavigateToCampScreen

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var wifiDirectManager: WifiDirectManager
    // BUG 1 FIX: inject AppEventBus — no more static object access
    @Inject lateinit var appEventBus: AppEventBus

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val serviceIntent = Intent(this, MeshForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        setContent {
            LocationPermissionGate {
                AppRoot(appEventBus = appEventBus)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        wifiDirectManager.initialize()
    }

    override fun onPause() {
        super.onPause()
        wifiDirectManager.shutdown()
    }
}

@Composable
private fun LocationPermissionGate(content: @Composable () -> Unit) {
    var permissionResolved by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        permissionResolved = true
    }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.NEARBY_WIFI_DEVICES
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }
        launcher.launch(permissions.toTypedArray())
    }

    if (permissionResolved) {
        content()
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

@Composable
fun AppRoot(
    viewModel: MainViewModel = hiltViewModel(),
    appEventBus: AppEventBus                      // BUG 1 FIX: passed from MainActivity
) {
    val setupComplete by viewModel.setupComplete.collectAsState()

    if (setupComplete == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    var currentScreen by remember(setupComplete) {
        mutableStateOf(if (setupComplete == true) "home" else "setup")
    }
    var selectedCampId   by remember { mutableStateOf<String?>(null) }
    var navigateToCampId by remember { mutableStateOf<String?>(null) }
    var incomingSosPacket by remember { mutableStateOf<MeshPacket?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        // BUG 1 FIX: use injected instance, not AppEventBus object
        appEventBus.incomingSos.collect { packet ->
            incomingSosPacket = packet
            currentScreen     = "incomingsos"
        }
    }

    when (currentScreen) {

        "setup" -> SetupScreen(
            onSetupComplete = { currentScreen = "language" }
        )
        "language" -> LanguageSelectionScreen(
            onConfirm = { currentScreen = "home" }
        )

        "home" -> HomeScreen(
            onSOSClick        = { currentScreen = "sos" },
            onChatClick       = { currentScreen = "chat" },
            onMapClick        = { currentScreen = "map" },
            onStatusClick     = { currentScreen = "status" },
            onChatbotClick    = { currentScreen = "chatbot" },
            onBulletinClick   = { currentScreen = "bulletin" },
            onDiscoveryClick  = { currentScreen = "discovery" },
            onFirstAidClick   = { currentScreen = "firstaid" },
            onCampsClick      = { currentScreen = "camps" },
            onAdminClick      = { currentScreen = "admin" },
            onFakeIncomingSos = if (BuildConfig.DEBUG) ({
                scope.launch {
                    // BUG 1 FIX: use injected instance
                    appEventBus.incomingSos.emit(
                        MeshPacket(
                            id          = "fake-sos-001",
                            type        = PacketType.SOS_ALERT,
                            senderId    = "dev-device",
                            senderName  = "Test User",
                            senderPhone = "0000000000",
                            payload     = "DEV: Fake SOS triggered",
                            ttl         = 3,
                            timestamp   = System.currentTimeMillis(),
                            signature   = ""
                        )
                    )
                }
            }) else ({})
        )

        "sos" -> SOSScreen(
            onBack = { currentScreen = "home" }
        )

        "incomingsos" -> IncomingSosAlertScreen(
            packet     = incomingSosPacket,
            onDismiss  = { currentScreen = "home" },
            onNavigate = { currentScreen = "map" }
        )

        "status"   -> StatusScreen(
            onMyStatusClick = { currentScreen = "mystatus" }
        )
        "mystatus" -> MyStatusScreen(
            onBack = { currentScreen = "status" }
        )

        "bulletin" -> BulletinScreen(
            onBack         = { currentScreen = "home" },
            onHomeClick    = { currentScreen = "home" },
            onChatClick    = { currentScreen = "chat" },
            onMapClick     = { currentScreen = "map" },
            onStatusClick  = { currentScreen = "status" },
            onChatbotClick = { currentScreen = "chatbot" }
        )

        "discovery" -> DiscoveryScreen(
            onBack = { currentScreen = "home" }
        )

        "chat" -> ChatScreen(
            onHomeClick    = { currentScreen = "home" },
            onMapClick     = { currentScreen = "map" },
            onStatusClick  = { currentScreen = "status" },
            onChatbotClick = { currentScreen = "chatbot" }
        )

        "camps" -> CampsScreen(
            onHomeClick    = { currentScreen = "home" },
            onChatClick    = { currentScreen = "chat" },
            onMapClick     = { currentScreen = "map" },
            onStatusClick  = { currentScreen = "status" },
            onChatbotClick = { currentScreen = "chatbot" },
            onCampClick    = { id ->
                selectedCampId = id
                currentScreen  = "campdetail"
            },
            onAddCampClick = { currentScreen = "addcamp" }
        )
        "campdetail" -> {
            val id = selectedCampId
            if (id != null) {
                CampDetailScreen(
                    campId          = id,
                    onBack          = { currentScreen = "camps" },
                    onNavigateClick = {
                        navigateToCampId = selectedCampId
                        currentScreen = "navigate"
                    }
                )
            } else {
                currentScreen = "camps"
            }
        }
        "addcamp" -> AddCampScreen(
            onBack      = { currentScreen = "camps" },
            onBroadcast = { currentScreen = "camps" }
        )
        "navigate" -> {
            val id = navigateToCampId
            if (id != null) {
                NavigateToCampScreen(
                    campId = id,
                    onBack = { currentScreen = "campdetail" }
                )
            } else {
                currentScreen = "camps"
            }
        }

        "map" -> MapScreen(
            onHomeClick    = { currentScreen = "home" },
            onChatClick    = { currentScreen = "chat" },
            onStatusClick  = { currentScreen = "status" },
            onChatbotClick = { currentScreen = "chatbot" }
        )

        "chatbot" -> ChatbotScreen(
            onHomeClick   = { currentScreen = "home" },
            onChatClick   = { currentScreen = "chat" },
            onMapClick    = { currentScreen = "map" },
            onStatusClick = { currentScreen = "status" }
        )

        "admin" -> {
            val isAdmin: Boolean? by viewModel.isAdmin.collectAsState()
            when (isAdmin) {
                null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                true -> {
                    AdminScreen(
                        onBack            = { currentScreen = "home" },
                        onEvacuationClick = { currentScreen = "evacuation" },
                        onTopologyClick   = { currentScreen = "topology" }
                    )
                }
                false -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(text = "Access Denied")
                            Button(onClick = { currentScreen = "home" }) {
                                Text(text = "Go Back")
                            }
                        }
                    }
                }
            }
        }

        "firstaid"   -> FirstAidScreen(onBack = { currentScreen = "home" })
        "topology"   -> TopologyScreen(onBack = { currentScreen = "admin" })
        "evacuation" -> EvacuationRouteScreen(onBack = { currentScreen = "admin" })
    }
}