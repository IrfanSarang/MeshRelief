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
    val selectedCategory: ChatbotCategory? = null,
    val engineMode: String = "keyword"   // "keyword" or "llm" — shown in debug/UI if needed
)

// ── LLM Engine abstraction ────────────────────────────────────────────────────

/**
 * Common interface for all response engines.
 * Implement this to swap in any on-device LLM without touching the ViewModel.
 */
interface LlmEngine {
    /** Returns true if the engine initialised successfully and is ready to use. */
    val isAvailable: Boolean

    /** Generate a response for the given [prompt]. Must be safe to call from any coroutine. */
    suspend fun generate(prompt: String): String
}

// ── Engine: keyword-only (current logic, always available) ───────────────────

class OfflineKeywordEngine : LlmEngine {

    override val isAvailable: Boolean = true

    override suspend fun generate(prompt: String): String {
        val lowerQuery = prompt.lowercase()
        val match = KNOWLEDGE_BASE.firstOrNull { entry ->
            entry.keywords.any { keyword -> lowerQuery.contains(keyword) }
        }
        return if (match != null) {
            match.answer
        } else {
            val fallback = FALLBACK_RESPONSES[fallbackIndex % FALLBACK_RESPONSES.size]
            fallbackIndex++
            fallback
        }
    }

    companion object {
        private var fallbackIndex = 0
    }
}

// ── Engine: MediaPipe LLM Inference (optional, requires model file on device) ─

/**
 * Wraps the MediaPipe LLM Inference API.
 *
 * To activate:
 *  1. Add the dependency in app/build.gradle.kts:
 *       implementation("com.google.mediapipe:tasks-genai:0.10.14")
 *  2. Push a compatible .bin model to the device (e.g. Gemma-2B) and set MODEL_PATH.
 *  3. Change the injection binding in your Hilt module to provide MediaPipeLlmEngine
 *     instead of OfflineKeywordEngine, wrapped in LlmEngineWithFallback.
 *
 * The engine is kept behind a try/catch so that missing model files or API
 * unavailability never crash the app — it simply marks itself unavailable and
 * the ViewModel falls back to keyword matching automatically.
 */
class MediaPipeLlmEngine(
    private val context: android.content.Context
) : LlmEngine {

    // Path to the model file on device storage, e.g.:
    //   "/data/local/tmp/gemma-2b-it-cpu-int4.bin"
    // or an asset path resolved at runtime.
    private val MODEL_PATH = "" // ← set before enabling

    private var inferenceSession: Any? = null   // typed as Any to avoid hard compile dependency

    override val isAvailable: Boolean
        get() = inferenceSession != null

    init {
        if (MODEL_PATH.isNotBlank()) {
            try {
                // Reflectively initialise so the class compiles even when the
                // mediapipe dependency is absent (e.g. in CI / other flavours).
                val optionsClass = Class.forName(
                    "com.google.mediapipe.tasks.genai.llminference.LlmInference\$LlmInferenceOptions"
                )
                val builderClass = Class.forName(
                    "com.google.mediapipe.tasks.genai.llminference.LlmInference\$LlmInferenceOptions\$Builder"
                )
                val builder = builderClass.getDeclaredConstructor().newInstance()
                builderClass.getMethod("setModelPath", String::class.java)
                    .invoke(builder, MODEL_PATH)
                val options = builderClass.getMethod("build").invoke(builder)

                val llmClass = Class.forName(
                    "com.google.mediapipe.tasks.genai.llminference.LlmInference"
                )
                inferenceSession = llmClass
                    .getMethod("createFromOptions", android.content.Context::class.java, optionsClass)
                    .invoke(null, context, options)
            } catch (e: Exception) {
                inferenceSession = null   // model absent or API unavailable — fall back silently
            }
        }
    }

    override suspend fun generate(prompt: String): String {
        val session = inferenceSession ?: return ""
        return try {
            val method = session.javaClass.getMethod("generateResponse", String::class.java)
            method.invoke(session, prompt) as? String ?: ""
        } catch (e: Exception) {
            ""
        }
    }
}

// ── Engine: composite with automatic keyword fallback ────────────────────────

/**
 * Tries [primary] first; if it is unavailable or returns a blank response,
 * delegates to [fallback] (the keyword engine).
 */
class LlmEngineWithFallback(
    private val primary: LlmEngine,
    private val fallback: LlmEngine = OfflineKeywordEngine()
) : LlmEngine {

    override val isAvailable: Boolean = true   // composite is always available

    val usingLlm: Boolean get() = primary.isAvailable

    override suspend fun generate(prompt: String): String {
        if (primary.isAvailable) {
            val result = primary.generate(prompt)
            if (result.isNotBlank()) return result
        }
        return fallback.generate(prompt)
    }
}

// ── Offline knowledge base ────────────────────────────────────────────────────

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
    KnowledgeEntry(
        keywords = listOf("well", "groundwater", "borewell", "spring", "underground water"),
        category = ChatbotCategory.WATER,
        answer = "💧 Well & spring water: After a flood or earthquake, assume all wells are contaminated. Wait for official clearance before using well water. Springs are generally safer than surface water but must still be boiled. Never draw water from a well using a bucket that has touched the ground."
    ),
    KnowledgeEntry(
        keywords = listOf("saltwater", "sea water", "ocean water", "brackish"),
        category = ChatbotCategory.WATER,
        answer = "💧 Saltwater: NEVER drink seawater or brackish water — it accelerates dehydration and causes kidney failure. You can distill it: boil water in a covered pot with a tube leading to a cooler container; the condensed steam collected is salt-free. This requires a heat source and time."
    ),
    KnowledgeEntry(
        keywords = listOf("store water", "container", "water storage", "reserve"),
        category = ChatbotCategory.WATER,
        answer = "💧 Water storage: Use clean, food-grade plastic or metal containers with tight lids. Store in a cool, dark place. Pre-boiled water stays safe for up to 6 months in sealed containers. Label containers with the date filled. Never store water in containers that previously held chemicals or fuel."
    ),
    KnowledgeEntry(
        keywords = listOf("cyclone water", "tsunami water", "flood water", "after disaster water"),
        category = ChatbotCategory.WATER,
        answer = "💧 Post-disaster water: After cyclones, floods, or tsunamis, ALL open water sources are contaminated — treat them as sewage. Rely only on sealed bottled water or water you have boiled yourself. Floodwater may contain cholera, typhoid, leptospirosis, and chemical runoff."
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
    KnowledgeEntry(
        keywords = listOf("diabetic", "diabetes", "blood sugar", "insulin", "medical diet"),
        category = ChatbotCategory.FOOD,
        answer = "🥫 Diabetics in disaster: If insulin is unavailable, minimise carbohydrate intake — avoid rice, sugar, and bread. Prioritise proteins and fats. Watch for signs of hyperglycaemia (excessive thirst, frequent urination, confusion) or hypoglycaemia (shaking, sweating, sudden confusion). Seek medical aid as soon as possible."
    ),
    KnowledgeEntry(
        keywords = listOf("baby food", "infant", "breastfeed", "formula", "toddler"),
        category = ChatbotCategory.FOOD,
        answer = "🥫 Feeding infants in disaster: Breastfeeding is the safest option — breast milk needs no clean water or preparation. If formula is unavoidable, use only boiled, cooled water for mixing. Do not dilute formula — it causes malnutrition. Relief camps usually have priority supplies for infants; register with camp authorities immediately."
    ),
    KnowledgeEntry(
        keywords = listOf("food poisoning", "vomiting", "diarrhea", "stomach pain", "nausea"),
        category = ChatbotCategory.FOOD,
        answer = "🥫 Food poisoning treatment: Stop eating suspect food. Drink boiled water with a pinch of salt + sugar (ORS) to replace lost fluids — 1 litre water, 6 tsp sugar, ½ tsp salt. Rest. Seek medical help if: there is blood in stool, high fever, or vomiting persists beyond 24 hours in adults or 6 hours in children."
    ),
    KnowledgeEntry(
        keywords = listOf("coconut", "sugarcane", "jackfruit", "banana", "local fruit"),
        category = ChatbotCategory.FOOD,
        answer = "🥫 India-specific survival foods: Tender coconut water is sterile, nutritious, and widely available in coastal/tropical regions. Raw green banana and raw jackfruit can be boiled as vegetables. Tamarind provides vitamin C. Sugarcane juice provides quick energy but no lasting nutrition. Always wash and peel fruit before eating."
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
    KnowledgeEntry(
        keywords = listOf("drowning", "near drowning", "pulled from water", "swallowed water"),
        category = ChatbotCategory.FIRST_AID,
        answer = "🩹 Near-drowning: Get person out of water safely. If unconscious and not breathing: start CPR immediately (30 compressions, 2 breaths). Do NOT try to drain water by shaking or pressing the abdomen — it wastes time and causes injury. Place in recovery position once breathing resumes. All near-drowning victims need hospital evaluation even if they seem fine."
    ),
    KnowledgeEntry(
        keywords = listOf("choking", "airway", "blocked throat", "heimlich", "can't breathe"),
        category = ChatbotCategory.FIRST_AID,
        answer = "🩹 Choking adult: Ask 'Are you choking?' — if they can't speak/cough/breathe, act immediately. Stand behind them, give 5 firm back blows between shoulder blades with the heel of your hand. Then 5 abdominal thrusts (Heimlich): hands above navel, thrust sharply inward-upward. Alternate until object dislodges or they lose consciousness. For infants: face-down back blows only."
    ),
    KnowledgeEntry(
        keywords = listOf("infection", "wound infection", "pus", "swollen wound", "fever wound"),
        category = ChatbotCategory.FIRST_AID,
        answer = "🩹 Wound infection signs: Increasing redness, warmth, swelling, pus, red streaks spreading from the wound, or fever. Clean with boiled (cooled) water and cover with a clean cloth. Change dressing twice daily. If red streaks appear (spreading up a limb), this is a medical emergency — seek help immediately; sepsis can be fatal within hours."
    ),
    KnowledgeEntry(
        keywords = listOf("crush injury", "trapped", "rubble", "collapsed building", "buried"),
        category = ChatbotCategory.FIRST_AID,
        answer = "🩹 Crush injury (collapsed building): Do NOT move a person who has been trapped for more than 15 minutes without medical guidance — sudden release can cause fatal cardiac arrest from 'crush syndrome'. Keep them conscious and talking. If they must be moved immediately for safety, do so gently and be prepared for collapse. Prioritise airway and external bleeding control."
    ),
    KnowledgeEntry(
        keywords = listOf("hypothermia", "freezing", "cold exposure", "shivering uncontrollably"),
        category = ChatbotCategory.FIRST_AID,
        answer = "🩹 Hypothermia: Signs — uncontrollable shivering, confusion, slurred speech, stumbling, then shivering stops (severe stage). Move person to shelter. Remove wet clothing. Warm the core first: chest, neck, armpits, groin — not limbs. Use body heat, blankets, or warm (not hot) drinks if conscious. Handle gently — cold hearts are prone to cardiac arrest from sudden movement."
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
    KnowledgeEntry(
        keywords = listOf("cyclone", "hurricane", "typhoon", "strong wind", "storm"),
        category = ChatbotCategory.SHELTER,
        answer = "🏕️ Cyclone shelter: Move to the strongest room in the building — an interior room on the lowest floor, away from windows. Lie flat under a heavy table or mattress. After the eye passes, the storm WILL resume — do not go outside. Avoid coastlines and riverbanks for 24 hours after landfall due to storm surge risk."
    ),
    KnowledgeEntry(
        keywords = listOf("landslide", "mudslide", "hillside", "slope collapse"),
        category = ChatbotCategory.SHELTER,
        answer = "🏕️ Landslide: If you hear a rumble or see trees leaning or cracks in hillside, evacuate immediately to high, flat ground — not the top of a hill. Move perpendicular to the slide path, not up the slope. If escape is impossible, curl into a ball and protect your head. Never shelter below steep slopes during heavy rain."
    ),
    KnowledgeEntry(
        keywords = listOf("relief camp", "refugee camp", "evacuation centre", "camp hygiene"),
        category = ChatbotCategory.SHELTER,
        answer = "🏕️ Relief camp hygiene: Keep sleeping area clean and dry. Use designated toilet areas only — open defecation spreads cholera and typhoid rapidly in camps. Wash hands before eating and after toilet use, even with minimal water. Report illness to camp health workers immediately — disease spreads fast in crowded shelters."
    ),
    KnowledgeEntry(
        keywords = listOf("gas leak", "lpg", "smell gas", "cylinder", "pipeline"),
        category = ChatbotCategory.SHELTER,
        answer = "🏕️ Gas leak: Do NOT switch any lights, fans, or switches on or off — sparks ignite gas. Do NOT use your phone inside the building. Open all doors and windows, leave immediately, and leave the door open behind you. Shut off the gas cylinder valve if safely reachable. Call emergency services from outside."
    ),
    KnowledgeEntry(
        keywords = listOf("electricity", "live wire", "power line", "electrocution", "electric shock"),
        category = ChatbotCategory.SHELTER,
        answer = "🏕️ Downed power lines: Assume all fallen lines are live. Stay at least 10 metres away. If a live wire touches your vehicle, stay inside — the car is safer than the ground. If you must exit, jump clear without touching the car and ground simultaneously, then shuffle away with feet together. Never touch someone being electrocuted — cut the power first."
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
    KnowledgeEntry(
        keywords = listOf("elderly", "disabled", "wheelchair", "mobility", "special needs evacuation"),
        category = ChatbotCategory.EVACUATION,
        answer = "🚨 Evacuating with elderly or disabled persons: Identify neighbours who may need help before a disaster strikes. For wheelchair users: carry chairs down stairs only if lifts are unavailable — use a carry chair or evacuation chair. Pre-register mobility-impaired persons with local disaster authorities so rescue teams are aware. Never leave them alone in a damaged building."
    ),
    KnowledgeEntry(
        keywords = listOf("cattle", "animals", "livestock", "pets", "dog cat"),
        category = ChatbotCategory.EVACUATION,
        answer = "🚨 Evacuating with animals: Move livestock to high ground early — they sense danger. Small pets: place in carriers. Large animals: open gates if you cannot take them — they may survive free better than trapped. Do NOT re-enter dangerous areas to rescue animals. Microchip or tag animals with your contact before disaster season."
    ),
    KnowledgeEntry(
        keywords = listOf("night evacuation", "dark", "no light", "evacuate at night"),
        category = ChatbotCategory.EVACUATION,
        answer = "🚨 Night evacuation: Use a flashlight or phone torch — never use open flame near gas leaks or flood debris. Move slowly and check every step. Mark your path with torn cloth tied to branches if possible. Stay on known roads — unfamiliar terrain at night causes falls and injuries. If possible, wait for first light unless immediate danger is present."
    ),
    KnowledgeEntry(
        keywords = listOf("tsunami", "tidal wave", "sea receding", "ocean wave"),
        category = ChatbotCategory.EVACUATION,
        answer = "🚨 Tsunami warning: If the sea suddenly recedes far from shore, or you feel a strong earthquake near the coast — RUN INLAND AND UPHILL IMMEDIATELY. Do not wait for official warning. A tsunami can arrive within minutes of an offshore quake. Move at least 30m above sea level or 3km inland. Do not return to the coast until authorities declare it safe — multiple waves can strike hours apart."
    ),
    KnowledgeEntry(
        keywords = listOf("children", "school", "kids evacuation", "child alone"),
        category = ChatbotCategory.EVACUATION,
        answer = "🚨 Children during evacuation: Do not send children ahead alone. Assign each child a buddy. Write your phone number on their arm with a marker. Schools have emergency protocols — if separated at school, go to the school's designated assembly point. Teach children to go to a uniformed official (police, NDRF, military) if lost."
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
    ),
    KnowledgeEntry(
        keywords = listOf("night signal", "dark signal", "signal at night", "flashlight signal"),
        category = ChatbotCategory.SOS_SIGNAL,
        answer = "📡 Night signalling: A flashlight or phone torch can signal SOS at night: 3 short flashes, 3 long flashes, 3 short flashes — pause — repeat (Morse SOS). Rescue helicopters use FLIR (thermal cameras) at night — a fire or body heat in an open area is detectable. Stay in the open and keep moving gently to generate heat signature."
    ),
    KnowledgeEntry(
        keywords = listOf("flare", "signal flare", "rescue flare", "pyrotechnic"),
        category = ChatbotCategory.SOS_SIGNAL,
        answer = "📡 Signal flares: Hold flare at arm's length, pointing downwind and slightly away from you. Only fire when you can hear or see a rescue aircraft/vessel — flares last only 30–60 seconds. Keep the second flare for when rescuers are close enough to pinpoint your location. Store flares dry and check expiry dates annually."
    ),
    KnowledgeEntry(
        keywords = listOf("mesh network", "radio", "walkie talkie", "communicate", "no internet"),
        category = ChatbotCategory.SOS_SIGNAL,
        answer = "📡 Off-grid communication: This MeshRelief app communicates directly device-to-device via Wi-Fi Direct — no internet or towers needed. Ensure your mesh network is active and share your location via the Map tab. For longer range: civilian walkie-talkies work on VHF/UHF. NDRF uses 2182 kHz (marine distress) and 121.5 MHz (aviation distress) frequencies."
    ),
    KnowledgeEntry(
        keywords = listOf("ndrf", "sdrf", "rescue team", "emergency number", "helpline", "112"),
        category = ChatbotCategory.SOS_SIGNAL,
        answer = "📡 India emergency contacts: 112 (unified national emergency — police, fire, ambulance). NDRF helpline: 011-24363260. State Disaster Management: contact your State DMA. Red Cross: 1800-180-5378 (toll free). If lines are busy, send a geo-tagged SMS to 112. Post your location on all available mesh channels in this app."
    )
)

// ── Fallback responses ────────────────────────────────────────────────────────

private val FALLBACK_RESPONSES = listOf(
    "I don't have a specific answer for that, but I can help with: Water purification, Food safety, First Aid, Shelter building, Evacuation planning, and SOS signals. Try asking about one of those topics.",
    "I'm an offline survival assistant — I may not cover every scenario. For best results, ask about: water, food, shelter, first aid, evacuation, or SOS signals.",
    "I didn't find a match for your question. You can tap a category below to browse survival tips by topic."
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class ChatbotViewModel @Inject constructor(
    /**
     * Inject an [LlmEngine] implementation.
     *
     * Default binding (no model file): provide [OfflineKeywordEngine] via Hilt.
     * With MediaPipe model: provide [LlmEngineWithFallback](MediaPipeLlmEngine, OfflineKeywordEngine).
     *
     * Example Hilt module (add to your di/ package):
     *
     *   @Module @InstallIn(SingletonComponent::class)
     *   object ChatbotModule {
     *       @Provides @Singleton
     *       fun provideLlmEngine(): LlmEngine = OfflineKeywordEngine()
     *       // To enable MediaPipe:
     *       // fun provideLlmEngine(@ApplicationContext ctx: Context): LlmEngine =
     *       //     LlmEngineWithFallback(MediaPipeLlmEngine(ctx))
     *   }
     */
    private val llmEngine: LlmEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ChatbotUiState(engineMode = if (llmEngine is LlmEngineWithFallback && llmEngine.usingLlm) "llm" else "keyword")
    )
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
            delay(700)
            val responseText = llmEngine.generate(query)
            val category = inferCategory(responseText)
            val botMsg = ChatbotMessage(
                id = "bot_${System.currentTimeMillis()}",
                text = responseText,
                isUser = false,
                category = category
            )
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

    /** Heuristically maps an answer string back to a category (used for LLM responses). */
    private fun inferCategory(text: String): ChatbotCategory? {
        val t = text.lowercase()
        return when {
            t.contains("💧") || t.contains("water") -> ChatbotCategory.WATER
            t.contains("🥫") || t.contains("food") || t.contains("eat") -> ChatbotCategory.FOOD
            t.contains("🩹") || t.contains("first aid") || t.contains("bleeding") -> ChatbotCategory.FIRST_AID
            t.contains("🏕") || t.contains("shelter") -> ChatbotCategory.SHELTER
            t.contains("🚨") || t.contains("evacuat") -> ChatbotCategory.EVACUATION
            t.contains("📡") || t.contains("sos") || t.contains("signal") -> ChatbotCategory.SOS_SIGNAL
            else -> null
        }
    }
}