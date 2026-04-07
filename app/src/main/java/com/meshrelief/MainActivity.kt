package com.meshrelief

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import com.meshrelief.features.admin.AdminScreen
import dagger.hilt.android.AndroidEntryPoint

import com.meshrelief.features.home.HomeScreen
import com.meshrelief.features.sos.SOSScreen
import com.meshrelief.features.status.StatusScreen
import com.meshrelief.features.status.MyStatusScreen
import com.meshrelief.features.bulletin.BulletinScreen
import com.meshrelief.features.discovery.DiscoveryScreen
import com.meshrelief.features.chat.ChatScreen
import com.meshrelief.features.camps.AddCampScreen
import com.meshrelief.features.camps.CampDetailScreen
import com.meshrelief.features.camps.CampsScreen
import com.meshrelief.features.chatbot.ChatbotScreen
import com.meshrelief.features.evacuation.EvacuationRouteScreen
import com.meshrelief.features.firstaid.FirstAidScreen
import com.meshrelief.features.map.MapScreen
import com.meshrelief.features.setup.SetupScreen
import com.meshrelief.features.setup    .LanguageSelectionScreen
import com.meshrelief.features.setup.LanguageSelectionScreen
import com.meshrelief.features.sos.IncomingSosAlertScreen
import com.meshrelief.features.topology.TopologyScreen

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppRoot()
        }
    }
}

@Composable
fun AppRoot(
    viewModel: MainViewModel = hiltViewModel()
) {
    // null = still loading, false = setup needed, true = go to home
    val setupComplete by viewModel.setupComplete.collectAsState()

    // Block rendering until DataStore has emitted its first value
    if (setupComplete == null) {
        Box(modifier = androidx.compose.ui.Modifier.fillMaxSize(),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    var currentScreen by remember(setupComplete) {
        // setupComplete is non-null here; route accordingly
        mutableStateOf(if (setupComplete == true) "home" else "setup")
    }
    var selectedCampId by remember { mutableStateOf<String?>(null) }

    when (currentScreen) {

        // ── Onboarding ──────────────────────────────────────────────────────
        "setup" -> SetupScreen(
            onSetupComplete = { currentScreen = "language" }
        )
        "language" -> LanguageSelectionScreen(
            onConfirm = { currentScreen = "home" }
        )

        // ── Main app ─────────────────────────────────────────────────────────
        "home" -> HomeScreen(
            onSOSClick       = { currentScreen = "sos" },
            onChatClick      = { currentScreen = "chat" },
            onMapClick       = { },
            onStatusClick    = { currentScreen = "status" },
            onChatbotClick   = { },
            onDiscoveryClick = { currentScreen = "discovery" },
            onCampsClick     = { currentScreen = "camps" }
        )
        "sos"      -> SOSScreen(onBack = { currentScreen = "home" })
        "status"   -> StatusScreen(onMyStatusClick = { currentScreen = "mystatus" })
        "mystatus" -> MyStatusScreen(onBack = { currentScreen = "status" })
        "bulletin" -> BulletinScreen(
            onBack        = { currentScreen = "home" },
            onHomeClick   = { currentScreen = "home" },
            onChatClick   = { currentScreen = "chat" },
            onMapClick    = { currentScreen = "map" },
            onStatusClick = { currentScreen = "status" },
            onChatbotClick = { currentScreen = "chatbot" }
        )
        "discovery" -> DiscoveryScreen(onBack = { currentScreen = "home" })
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
                currentScreen = "campdetail"
            },
            onAddCampClick = { currentScreen = "addcamp" }
        )
        "campdetail" -> {
            val id = selectedCampId
            if (id != null) {
                CampDetailScreen(
                    campId          = id,
                    onBack          = { currentScreen = "camps" },
                    onNavigateClick = { /* TODO: open map with route to camp */ }
                )
            } else {
                currentScreen = "camps"
            }
        }
        "addcamp" -> AddCampScreen(
            onBack      = { currentScreen = "camps" },
            onBroadcast = { currentScreen = "camps" }
        )
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
        "admin" -> AdminScreen(
            onBack            = { currentScreen = "home" },
            onEvacuationClick = { currentScreen = "evacuation" }
        )
        "firstaid"  -> FirstAidScreen(onBack = { currentScreen = "home" })
        "topology"  -> TopologyScreen(onBack = { currentScreen = "home" })
        "evacuation" -> EvacuationRouteScreen(onBack = { currentScreen = "admin" })
        "incomingsos" -> IncomingSosAlertScreen(
            onDismiss  = { currentScreen = "home" },
            onNavigate = { currentScreen = "map" }
        )
    }
}