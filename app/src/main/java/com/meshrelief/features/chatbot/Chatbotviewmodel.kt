package com.meshrelief.features.chatbot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── Data models ──────────────────────────────────────────────────────────────

enum class ChatbotCategory(val label: String, val emoji: String) {
    WATER("Water", "💧"),
    FOOD("Food", "🥫"),
    FIRST_AID("First Aid", "🩹"),
    SHELTER("Shelter", "🏕️"),
    EVACUATION("Evacuation", "🚨"),
    SOS_SIGNAL("SOS Signals", "📡")
}

data class ChatbotMessage(
    val id: String,
    val text: String,
    val isUser: Boolean,
    val category: ChatbotCategory? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class ChatbotUiState(
    val messages: List<ChatbotMessage> = emptyList(),
    val inputText: String = "",
    val isTyping: Boolean = false,
    val selectedCategory: ChatbotCategory? = null
)

// ── Offline knowledge base (≥5 entries per category) ─────────────────────────

private data class KnowledgeEntry(
    val keywords: List<String>,
    val category: ChatbotCategory,
    val answer: String
)

private val KNOWLEDGE_BASE: List<KnowledgeEntry> = listOf(

    // ── WATER ────────────────────────────────────────────────────────────────
    KnowledgeEntry(
        keywords = listOf("purify", "purification", "make water safe", "drink"),
        category = ChatbotCategory.WATER,
        answer = "💧 To purify water without chemicals: Bring it to a rolling boil for at least 1 minute (3 minutes above 2000m altitude). Let it cool in a covered container before drinking."
    ),
    KnowledgeEntry(
        keywords = listOf("filter", "cloth", "muddy", "dirty water"),
        category = ChatbotCategory.WATER,
        answer = "💧 Field filtering: Pour water through layers of cloth, then sand, then gravel (coarsest on top). This removes sediment. Always boil afterwards — filtering alone does NOT kill bacteria or viruses."
    ),
    KnowledgeEntry(
        keywords = listOf("chlorine", "bleach", "tablet", "iodine"),
        category = ChatbotCategory.WATER,
        answer = "💧 Chemical treatment: Add 2 drops of unscented household bleach (5–6% sodium hypochlorite) per litre of clear water. Mix and wait 30 minutes before drinking. For cloudy water, filter first and double the dose."
    ),
    KnowledgeEntry(
        keywords = listOf("how much water", "daily", "need water", "dehydration", "thirsty"),
        category = ChatbotCategory.WATER,
        answer = "💧 Minimum water need: 3 litres/day per adult in normal conditions — more in heat, exertion, or if injured. Signs of dehydration: dark yellow urine, dizziness, dry mouth, rapid heartbeat. Prioritise water above food."
    ),
    KnowledgeEntry(
        keywords = listOf("collect water", "rain", "dew", "morning", "source"),
        category = ChatbotCategory.WATER,
        answer = "💧 Collecting water: Spread clean plastic or tarpaulin at night to collect dew or rainwater. Tie cloth around vegetation in the early morning — dew wets it, wring it out. Look for low ground, follow animal tracks, and watch for birds flying toward water."
    ),
    KnowledgeEntry(
        keywords = listOf("solar", "sun", "bottle", "disinfect", "sodis"),
        category = ChatbotCategory.WATER,
        answer = "💧 Solar disinfection (SODIS): Fill a clear PET bottle with filtered water, lay it in direct sunlight for 6 hours (or 2 days if cloudy). UV rays kill most pathogens. Only works in clear bottles — not glass or coloured plastic."
    ),

    // ── FOOD ─────────────────────────────────────────────────────────────────
    KnowledgeEntry(
        keywords = listOf("eat", "food", "hungry", "survive without food", "fasting"),
        category = ChatbotCategory.FOOD,
        answer = "🥫 Survival fact: A healthy adult can survive 3 weeks without food but only 3 days without water. Prioritise water and warmth over food. Ration any food you have — small portions prevent energy crashes."
    ),
    KnowledgeEntry(
        keywords = listOf("safe to eat", "is this food ok", "spoiled", "expired", "mold"),
        category = ChatbotCategory.FOOD,
        answer = "🥫 Checking food safety: Discard anything with mold, foul smell, sliminess, or unusual colour. Canned food is safe if the can is not bulging, dented on seams, or spurting when opened. When in doubt, throw it out — food poisoning worsens your situation drastically."
    ),
    KnowledgeEntry(
        keywords = listOf("wild plant", "berries", "leaves", "edible", "jungle", "forest"),
        category = ChatbotCategory.FOOD,
        answer = "🥫 Wild plant rule: Avoid all unknown berries, seeds, or mushrooms — many are deadly. Safe plants in India: tender coconut (widely available), raw banana, jackfruit, wood apple, tamarind pods. Do NOT eat plants with milky white sap, bitter taste, or strong unpleasant smell."
    ),
    KnowledgeEntry(
        keywords = listOf("cook", "fire", "cooking", "boil food"),
        category = ChatbotCategory.FOOD,
        answer = "🥫 Emergency cooking: Use any dry wood, dried dung, or paper. Keep fire small (1 hand-size flame is enough). Stone windbreak around the fire retains heat. Boil grains, lentils, and root vegetables fully — undercooked food causes illness."
    ),
    KnowledgeEntry(
        keywords = listOf("ration", "share", "distribute food", "community"),
        category = ChatbotCategory.FOOD,
        answer = "🥫 Food rationing: Prioritise — children under 5, pregnant women, injured, and elderly first. Healthy adults can tolerate up to 3 days with minimal food. Avoid sharing utensils to reduce infection. Keep a communal food log to track quantities."
    ),
    KnowledgeEntry(
        keywords = listOf("catch fish", "fishing", "protein", "insects"),
        category = ChatbotCategory.FOOD,
        answer = "🥫 Emergency protein: Fish from rivers/ponds using improvised line and hook. Insects like grasshoppers, crickets, and termites are safe to eat after removing wings and legs — boil or roast them. Avoid brightly coloured insects; bright colours signal toxicity."
    ),

    // ── FIRST AID ─────────────────────────────────────────────────────────────
    KnowledgeEntry(
        keywords = listOf("bleeding", "wound", "cut", "blood", "stop bleeding"),
        category = ChatbotCategory.FIRST_AID,
        answer = "🩹 Stop bleeding: Apply firm, direct pressure with a clean cloth for at least 10 minutes — do not lift the cloth to check. Elevate the limb above heart level. If blood soaks through, add more cloth on top. Tourniquets only for life-threatening limb bleeding: 5–8 cm above wound, note time applied."
    ),
    KnowledgeEntry(
        keywords = listOf("broken bone", "fracture", "sprain", "twisted", "splint"),
        category = ChatbotCategory.FIRST_AID,
        answer = "🩹 Splinting a fracture: Do NOT straighten the bone. Immobilise it in the position found. Pad with cloth, then tie rigid objects (sticks, boards) on both sides extending past the joints above and below. Check circulation (warmth, pulse) every 30 minutes. Keep the person still."
    ),
    KnowledgeEntry(
        keywords = listOf("burn", "fire burn", "scalded", "hot water", "heat burn"),
        category = ChatbotCategory.FIRST_AID,
        answer = "🩹 Treating burns: Cool immediately with cool (not ice-cold) running water for 20 minutes. Remove jewellery/tight clothing before swelling. Cover with clean non-fluffy cloth. Never apply oil, toothpaste, or butter. Seek medical help for burns larger than the person's palm or burns on face/hands/genitals."
    ),
    KnowledgeEntry(
        keywords = listOf("unconscious", "unresponsive", "fainted", "passed out", "cpr"),
        category = ChatbotCategory.FIRST_AID,
        answer = "🩹 Unconscious person: Tap shoulders and shout. No response → Tilt head back, lift chin, check breathing for 10 seconds. If not breathing: 30 chest compressions (hard and fast, 5–6 cm deep, 100–120/min) + 2 rescue breaths. Repeat until breathing returns or help arrives. If breathing: place in recovery position (on side)."
    ),
    KnowledgeEntry(
        keywords = listOf("snake bite", "bite", "venom", "scorpion", "insect sting"),
        category = ChatbotCategory.FIRST_AID,
        answer = "🩹 Snake/scorpion bite: Keep the person CALM and STILL — movement spreads venom faster. Immobilise the bitten limb below heart level. Remove rings and tight clothing. Do NOT cut, suck, or tourniquet the wound. Mark the bite time. Reach medical care as fast as possible — anti-venom is the only effective treatment."
    ),
    KnowledgeEntry(
        keywords = listOf("heat stroke", "heat exhaustion", "sunstroke", "overheating", "dizzy heat"),
        category = ChatbotCategory.FIRST_AID,
        answer = "🩹 Heat stroke emergency: Move person to shade immediately. Remove outer clothing. Apply cool water to skin and fan vigorously. Ice packs to neck, armpits, and groin if available. Give cool water if conscious and able to swallow. Heat stroke is life-threatening — cool the person fast, every minute matters."
    ),

    // ── SHELTER ───────────────────────────────────────────────────────────────
    KnowledgeEntry(
        keywords = listOf("shelter", "stay warm", "cold", "survive night", "hypothermia"),
        category = ChatbotCategory.SHELTER,
        answer = "🏕️ Emergency shelter: Prioritise: (1) windbreak, (2) insulation from ground (ground steals heat 25× faster than air), (3) roof. Use tarpaulin, large leaves, or clothing. Huddle with others — shared body heat is critical. Avoid metal surfaces at night — they conduct cold rapidly."
    ),
    KnowledgeEntry(
        keywords = listOf("tarpaulin", "plastic sheet", "tarp", "build shelter"),
        category = ChatbotCategory.SHELTER,
        answer = "🏕️ Tarpaulin shelter: Tie a ridge line between two trees at chest height. Drape tarp over it, stake edges to ground at 45°. This makes an A-frame that sheds rain well. Angle the closed end into the wind. Sleep perpendicular to the ridge, not parallel — more warmth is retained."
    ),
    KnowledgeEntry(
        keywords = listOf("fire", "warmth", "heat", "keep warm", "firewood"),
        category = ChatbotCategory.SHELTER,
        answer = "🏕️ Fire for warmth: Build fire at the mouth of your shelter, never inside (carbon monoxide risk). Dry dead wood burns best — look for wood that snaps cleanly. Tinder: dry grass, dry leaves, paper. Kindling: pencil-thin twigs. Fuel: wrist-thick logs. Use the 'log cabin' layout for long-lasting heat."
    ),
    KnowledgeEntry(
        keywords = listOf("flood", "flooding", "water rising", "shelter flood"),
        category = ChatbotCategory.SHELTER,
        answer = "🏕️ Flood shelter: Move to the highest ground available immediately. Never shelter in basements or low-lying buildings. Rooftop or upper floors are safer. Signal rescuers from height with bright cloth. Avoid floodwater — it may contain sewage, chemicals, live wires, and hidden currents."
    ),
    KnowledgeEntry(
        keywords = listOf("earthquake", "aftershock", "building damaged", "debris"),
        category = ChatbotCategory.SHELTER,
        answer = "🏕️ Post-earthquake shelter: Do NOT re-enter damaged buildings — aftershocks can collapse weakened structures. Open spaces away from buildings and power lines are safest. If outdoors during shaking: drop, cover, hold. After: check for gas leaks (smell), electrical fires, and structural damage before entering."
    ),

    // ── EVACUATION ────────────────────────────────────────────────────────────
    KnowledgeEntry(
        keywords = listOf("evacuate", "leave", "escape", "when to go", "evacuation"),
        category = ChatbotCategory.EVACUATION,
        answer = "🚨 Evacuation decision: Leave immediately if: authorities order evacuation, floodwater is rising, you smell gas, building is structurally damaged, fire is approaching, or you lack clean water for 48+ hours. Never wait 'to see what happens' when official evacuation orders are issued."
    ),
    KnowledgeEntry(
        keywords = listOf("go bag", "emergency bag", "kit", "pack", "supplies"),
        category = ChatbotCategory.EVACUATION,
        answer = "🚨 Essential go-bag items: Water (3L minimum), non-perishable food (3 days), first aid kit, flashlight + batteries, whistle, copies of ID documents, emergency cash, phone charger, basic medicines, extra clothing, and this device fully charged. Pack in under 5 minutes — keep a pre-packed bag ready."
    ),
    KnowledgeEntry(
        keywords = listOf("route", "safe route", "road blocked", "path", "navigate"),
        category = ChatbotCategory.EVACUATION,
        answer = "🚨 Finding safe routes: Use elevation maps (higher ground = safer in floods). Avoid bridges over flooded rivers — they may be structurally compromised. In cities, use wide main roads over narrow lanes. Ask locals — they know collapsed roads, active fires, and blocked areas. Move in daylight if possible."
    ),
    KnowledgeEntry(
        keywords = listOf("family separated", "lost", "find family", "meet", "reunite"),
        category = ChatbotCategory.EVACUATION,
        answer = "🚨 Family separation plan: Designate a pre-agreed meeting point (landmark, school, temple, community hall) before disaster strikes. If separated: go to the nearest relief camp and register your name, or leave a written note at the agreed location. Children: teach them to state full name, parent name, and area of home."
    ),
    KnowledgeEntry(
        keywords = listOf("car", "vehicle", "drive", "flood car", "trapped in car"),
        category = ChatbotCategory.EVACUATION,
        answer = "🚨 Vehicle in flood: Never drive through moving floodwater — 15 cm can sweep a car off the road. If your car stalls in water: exit immediately, move to high ground. If trapped inside a submerged car: wait for water to equalise pressure, open door, swim to surface. Keep a window punch tool in your car."
    ),

    // ── SOS SIGNALS ──────────────────────────────────────────────────────────
    KnowledgeEntry(
        keywords = listOf("sos", "signal", "help signal", "distress", "rescue signal"),
        category = ChatbotCategory.SOS_SIGNAL,
        answer = "📡 Universal SOS: Three of anything = distress signal. 3 whistle blasts, 3 fire flashes, 3 gunshots, 3 horn blasts — pause — repeat. In open terrain, stamp or arrange rocks/debris into large SOS letters visible from the air. Make them at least 3 metres tall."
    ),
    KnowledgeEntry(
        keywords = listOf("mirror", "reflect", "flash", "aircraft", "helicopter"),
        category = ChatbotCategory.SOS_SIGNAL,
        answer = "📡 Mirror signalling: Aim reflected sunlight at aircraft or distant searchers. Use any shiny object: phone screen, CD, metal container. Angle to catch sun and sweep the beam slowly across the target. A mirror signal can be seen over 10 km away in clear conditions — far better than waving."
    ),
    KnowledgeEntry(
        keywords = listOf("smoke", "fire signal", "visible", "dark smoke", "white smoke"),
        category = ChatbotCategory.SOS_SIGNAL,
        answer = "📡 Smoke signals: Light a signal fire on high ground, open area, or rooftop. Dark smoke (from rubber, plastic) is visible against green vegetation. White smoke (from damp green leaves) is visible against dark rock or grey sky. Three smoke columns = international distress signal. Keep fire controlled."
    ),
    KnowledgeEntry(
        keywords = listOf("whistle", "shout", "noise", "audio signal", "sound"),
        category = ChatbotCategory.SOS_SIGNAL,
        answer = "📡 Sound signals: A whistle carries 3× farther than a human voice and uses far less energy. 3 blasts, pause 1 minute, repeat. If no whistle: bang rocks or metal together. Shout toward open water or across valleys — sound travels farther. Stop and listen between signals for a response."
    ),
    KnowledgeEntry(
        keywords = listOf("ground signal", "aerial", "rocks", "pattern", "symbols"),
        category = ChatbotCategory.SOS_SIGNAL,
        answer = "📡 Ground-to-air signals: Lay out these patterns large enough to be seen from aircraft — V = need assistance, X = need medical help, SOS = extreme emergency. Use rocks, logs, dark fabric, or trench in snow. Each letter must be at least 3m tall. Place on the highest, most open ground available."
    ),
    KnowledgeEntry(
        keywords = listOf("phone", "battery", "low battery", "signal phone", "message"),
        category = ChatbotCategory.SOS_SIGNAL,
        answer = "📡 Phone in emergency: Even with no network signal, most phones can still call 112 (India emergency). Send SMS — texts use far less power than calls and may get through on congested networks. Enable airplane mode between check-ins to save battery. Charge via car USB, solar bank, or hand crank if available."
    )
)

// ── Fallback ──────────────────────────────────────────────────────────────────

private val FALLBACK_RESPONSES = listOf(
    "I don't have a specific answer for that, but I can help with: Water purification, Food safety, First Aid, Shelter building, Evacuation planning, and SOS signals. Try asking about one of those topics.",
    "I'm an offline survival assistant — I may not cover every scenario. For best results, ask about: water, food, shelter, first aid, evacuation, or SOS signals.",
    "I didn't find a match for your question. You can tap a category below to browse survival tips by topic."
)

private var fallbackIndex = 0

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class ChatbotViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(ChatbotUiState())
    val uiState: StateFlow<ChatbotUiState> = _uiState.asStateFlow()

    init {
        val welcome = ChatbotMessage(
            id = "welcome_${System.currentTimeMillis()}",
            text = "👋 Hello! I'm your offline survival assistant.\n\nI can answer questions about water safety, food, first aid, shelter, evacuation, and SOS signals — all without any internet connection.\n\nType your question or tap a category below.",
            isUser = false
        )
        _uiState.value = _uiState.value.copy(messages = listOf(welcome))
    }

    fun onInputChanged(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun onSend() {
        val query = _uiState.value.inputText.trim()
        if (query.isBlank()) return

        val userMsg = ChatbotMessage(
            id = "user_${System.currentTimeMillis()}",
            text = query,
            isUser = true
        )

        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + userMsg,
            inputText = "",
            isTyping = true
        )

        viewModelScope.launch {
            delay(700) // Simulate response latency
            val botMsg = generateResponse(query)
            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages + botMsg,
                isTyping = false
            )
        }
    }

    fun onCategorySelected(category: ChatbotCategory) {
        val categoryMsg = ChatbotMessage(
            id = "cat_user_${System.currentTimeMillis()}",
            text = "Tell me about ${category.label}",
            isUser = true,
            category = category
        )

        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + categoryMsg,
            selectedCategory = category,
            isTyping = true
        )

        viewModelScope.launch {
            delay(500)
            val entries = KNOWLEDGE_BASE.filter { it.category == category }
            val overview = entries.joinToString("\n\n") { it.answer }
            val botMsg = ChatbotMessage(
                id = "cat_bot_${System.currentTimeMillis()}",
                text = overview.ifBlank { "No information available for this category." },
                isUser = false,
                category = category
            )
            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages + botMsg,
                isTyping = false
            )
        }
    }

    private fun generateResponse(query: String): ChatbotMessage {
        val lowerQuery = query.lowercase()
        val match = KNOWLEDGE_BASE.firstOrNull { entry ->
            entry.keywords.any { keyword -> lowerQuery.contains(keyword) }
        }

        return if (match != null) {
            ChatbotMessage(
                id = "bot_${System.currentTimeMillis()}",
                text = match.answer,
                isUser = false,
                category = match.category
            )
        } else {
            val fallback = FALLBACK_RESPONSES[fallbackIndex % FALLBACK_RESPONSES.size]
            fallbackIndex++
            ChatbotMessage(
                id = "bot_${System.currentTimeMillis()}",
                text = fallback,
                isUser = false
            )
        }
    }
}