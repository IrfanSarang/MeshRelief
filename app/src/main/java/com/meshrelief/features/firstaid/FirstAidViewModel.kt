package com.meshrelief.features.firstaid

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

enum class Severity { CRITICAL, URGENT, MODERATE }

enum class FirstAidCategory(val label: String) {
    ALL("All"),
    BLEEDING("Bleeding"),
    BURNS("Burns"),
    FRACTURES("Fractures"),
    DROWNING("Drowning"),
    CARDIAC("Cardiac"),
    SHOCK("Shock"),
    POISONING("Poisoning"),
    SNAKE_BITE("Snake Bite")
}

data class FirstAidEntry(
    val id: Int,
    val title: String,
    val category: FirstAidCategory,
    val severity: Severity,
    val keywords: List<String>,
    val steps: List<String>,
    val doNots: List<String> = emptyList()
)

data class FirstAidUiState(
    val searchQuery: String = "",
    val selectedCategory: FirstAidCategory = FirstAidCategory.ALL,
    val expandedEntryId: Int? = null,
    val filteredEntries: List<FirstAidEntry> = emptyList()
)

@HiltViewModel
class FirstAidViewModel @Inject constructor() : ViewModel() {

    private val allEntries: List<FirstAidEntry> = listOf(

        // ── BLEEDING ──────────────────────────────────────────────────
        FirstAidEntry(
            id = 1,
            title = "Severe External Bleeding",
            category = FirstAidCategory.BLEEDING,
            severity = Severity.CRITICAL,
            keywords = listOf("cut", "wound", "blood", "haemorrhage", "hemorrhage", "laceration"),
            steps = listOf(
                "Ensure your own safety — wear gloves if available.",
                "Apply firm, direct pressure on the wound using a clean cloth or bandage.",
                "Do not remove the cloth if it becomes soaked; add more layers on top.",
                "If limb is involved and bleeding is life-threatening, apply a tourniquet 5–7 cm above the wound.",
                "Note the tourniquet application time.",
                "Elevate the injured limb above heart level if no fracture is suspected.",
                "Keep the casualty warm and still. Seek emergency help immediately."
            ),
            doNots = listOf(
                "Do NOT remove objects embedded in the wound.",
                "Do NOT remove a tourniquet once applied.",
                "Do NOT apply a tourniquet over a joint."
            )
        ),
        FirstAidEntry(
            id = 2,
            title = "Nosebleed (Epistaxis)",
            category = FirstAidCategory.BLEEDING,
            severity = Severity.MODERATE,
            keywords = listOf("nose", "nosebleed", "epistaxis", "nasal"),
            steps = listOf(
                "Have the person sit upright and lean slightly forward.",
                "Pinch the soft part of the nose (below the bony bridge) firmly.",
                "Breathe through the mouth. Maintain pressure for 10–15 minutes continuously.",
                "Apply a cold compress to the bridge of the nose.",
                "Release slowly. If bleeding resumes, repeat for another 10–15 minutes.",
                "Seek medical care if bleeding does not stop after 30 minutes."
            ),
            doNots = listOf(
                "Do NOT tilt the head back — blood may flow into the airway.",
                "Do NOT pack the nostril tightly with tissue unless bleeding is heavy."
            )
        ),

        // ── BURNS ─────────────────────────────────────────────────────
        FirstAidEntry(
            id = 3,
            title = "Minor Burns (1st Degree)",
            category = FirstAidCategory.BURNS,
            severity = Severity.MODERATE,
            keywords = listOf("burn", "scald", "hot", "fire", "heat", "blister"),
            steps = listOf(
                "Cool the burn immediately under cool (not ice-cold) running water for at least 10–20 minutes.",
                "Remove jewellery or clothing near the burned area (unless stuck to skin).",
                "Cover loosely with a sterile, non-fluffy dressing or cling film.",
                "Give over-the-counter pain relief if available.",
                "Do not burst any blisters."
            ),
            doNots = listOf(
                "Do NOT apply ice, butter, toothpaste, or any home remedy.",
                "Do NOT use fluffy cotton wool directly on the burn.",
                "Do NOT break blisters — this increases infection risk."
            )
        ),
        FirstAidEntry(
            id = 4,
            title = "Severe Burns (2nd / 3rd Degree)",
            category = FirstAidCategory.BURNS,
            severity = Severity.CRITICAL,
            keywords = listOf("severe burn", "deep burn", "charred", "chemical burn"),
            steps = listOf(
                "Ensure safety — stop the burning process (remove from source, smother flames).",
                "Call for emergency help immediately.",
                "Cool with cool running water for at least 20 minutes.",
                "Do not remove clothing stuck to burned skin.",
                "Cover the area loosely with a clean, non-fluffy material (cling film ideal).",
                "Treat for shock: lay the person flat, raise legs, keep warm.",
                "Monitor breathing continuously until help arrives."
            ),
            doNots = listOf(
                "Do NOT apply any creams, oils, or ice.",
                "Do NOT remove stuck clothing or burst blisters.",
                "Do NOT give the person anything to eat or drink."
            )
        ),
        FirstAidEntry(
            id = 5,
            title = "Chemical Burn",
            category = FirstAidCategory.BURNS,
            severity = Severity.URGENT,
            keywords = listOf("chemical", "acid", "alkali", "caustic", "corrosive"),
            steps = listOf(
                "Protect yourself — wear gloves before touching the casualty.",
                "Remove contaminated clothing carefully, avoiding spreading the chemical.",
                "Flush the affected area with large amounts of cool running water for at least 20 minutes.",
                "If chemical is in the eye, irrigate with water for at least 15 minutes.",
                "Cover with a clean, dry dressing.",
                "Seek emergency medical care immediately."
            ),
            doNots = listOf(
                "Do NOT attempt to neutralise the chemical with another substance.",
                "Do NOT rub the area — this spreads the chemical."
            )
        ),

        // ── FRACTURES ─────────────────────────────────────────────────
        FirstAidEntry(
            id = 6,
            title = "Suspected Broken Bone (Fracture)",
            category = FirstAidCategory.FRACTURES,
            severity = Severity.URGENT,
            keywords = listOf("fracture", "broken bone", "break", "crack", "splint"),
            steps = listOf(
                "Immobilise the injury — do not attempt to straighten the limb.",
                "Splint the fracture in the position found using rigid material (stick, board) padded with cloth.",
                "Secure the splint above and below the fracture, not over it.",
                "Elevate the injured limb gently if possible.",
                "Apply an ice pack wrapped in cloth to reduce swelling.",
                "Monitor circulation below the fracture (check pulse, skin colour, temperature).",
                "Arrange transport to a medical facility."
            ),
            doNots = listOf(
                "Do NOT attempt to realign or manipulate the broken bone.",
                "Do NOT move the person unnecessarily, especially if spinal injury is suspected."
            )
        ),
        FirstAidEntry(
            id = 7,
            title = "Open (Compound) Fracture",
            category = FirstAidCategory.FRACTURES,
            severity = Severity.CRITICAL,
            keywords = listOf("compound fracture", "open fracture", "bone protruding", "exposed bone"),
            steps = listOf(
                "Call for emergency help immediately.",
                "Do not push bone back in or clean the wound.",
                "Cover the wound loosely with a clean, moist dressing.",
                "Control any surrounding bleeding with gentle pressure around (not on) the wound.",
                "Immobilise the limb in the position found.",
                "Treat for shock: keep the person warm and still.",
                "Monitor for signs of infection and blood loss."
            ),
            doNots = listOf(
                "Do NOT attempt to push the bone back or clean the wound.",
                "Do NOT apply direct pressure over the protruding bone."
            )
        ),

        // ── DROWNING ──────────────────────────────────────────────────
        FirstAidEntry(
            id = 8,
            title = "Drowning / Near-Drowning",
            category = FirstAidCategory.DROWNING,
            severity = Severity.CRITICAL,
            keywords = listOf("drowning", "water", "submerged", "rescue", "inhale water"),
            steps = listOf(
                "Ensure your own safety — do not enter fast-moving water without training.",
                "Reach or throw a rope/object to the victim; only enter water as last resort.",
                "Once the person is safe, check for responsiveness and breathing.",
                "If not breathing, begin CPR immediately: 30 chest compressions, then 2 rescue breaths.",
                "Continue CPR until the person breathes or emergency help arrives.",
                "Place in recovery position if breathing resumes.",
                "Seek emergency medical care even if the person appears to recover — secondary drowning risk."
            ),
            doNots = listOf(
                "Do NOT attempt a Heimlich manoeuvre to remove water from lungs.",
                "Do NOT leave the person alone even if conscious."
            )
        ),

        // ── CARDIAC ───────────────────────────────────────────────────
        FirstAidEntry(
            id = 9,
            title = "Cardiac Arrest (No Pulse / Not Breathing)",
            category = FirstAidCategory.CARDIAC,
            severity = Severity.CRITICAL,
            keywords = listOf("cardiac arrest", "heart attack", "cpr", "chest compression", "no pulse", "unconscious"),
            steps = listOf(
                "Check surroundings for safety, then check the person's responsiveness (tap shoulders, shout).",
                "Call loudly for help and send someone to call emergency services.",
                "Place the heel of your hand on the centre of the chest (lower half of sternum).",
                "Give 30 chest compressions: push hard and fast (5–6 cm depth) at ~100–120 per minute.",
                "Give 2 rescue breaths: tilt head, lift chin, seal lips, blow until chest rises.",
                "Continue 30:2 cycle without stopping until AED arrives, help takes over, or person recovers.",
                "Use an AED as soon as available — follow its audio instructions precisely."
            ),
            doNots = listOf(
                "Do NOT stop CPR unless the person shows clear signs of life.",
                "Do NOT delay CPR to look for a pulse for more than 10 seconds."
            )
        ),
        FirstAidEntry(
            id = 10,
            title = "Heart Attack (Conscious, Chest Pain)",
            category = FirstAidCategory.CARDIAC,
            severity = Severity.CRITICAL,
            keywords = listOf("heart attack", "chest pain", "myocardial infarction", "angina", "jaw pain"),
            steps = listOf(
                "Have the person sit or lie in the most comfortable position (W-position: sitting with knees bent).",
                "Loosen any tight clothing.",
                "If the person is not allergic, give 300 mg aspirin to chew slowly (not swallow whole).",
                "Keep the person calm and reassured.",
                "Call emergency services immediately.",
                "Monitor breathing and pulse constantly.",
                "Be ready to start CPR if the person loses consciousness and stops breathing."
            ),
            doNots = listOf(
                "Do NOT give aspirin if the person is allergic or if it is unknown.",
                "Do NOT let the person walk around or exert themselves."
            )
        ),

        // ── SHOCK ─────────────────────────────────────────────────────
        FirstAidEntry(
            id = 11,
            title = "Hypovolaemic Shock",
            category = FirstAidCategory.SHOCK,
            severity = Severity.CRITICAL,
            keywords = listOf("shock", "pale", "cold skin", "rapid pulse", "faint", "collapse", "low blood pressure"),
            steps = listOf(
                "Lay the person flat on their back.",
                "Raise both legs about 30 cm (unless head, neck, spine, or leg injury is suspected).",
                "Keep the person warm with a blanket.",
                "Control any visible bleeding.",
                "Do not give anything by mouth.",
                "Loosen tight clothing at the neck, chest, and waist.",
                "Call emergency services and monitor breathing and pulse continuously."
            ),
            doNots = listOf(
                "Do NOT give food or water.",
                "Do NOT leave the person unattended.",
                "Do NOT raise legs if spinal or leg injury is suspected."
            )
        ),
        FirstAidEntry(
            id = 12,
            title = "Anaphylactic Shock (Severe Allergic Reaction)",
            category = FirstAidCategory.SHOCK,
            severity = Severity.CRITICAL,
            keywords = listOf("anaphylaxis", "allergy", "adrenaline", "epipen", "swelling", "hives", "bee sting"),
            steps = listOf(
                "Identify the trigger and remove it if possible.",
                "Call emergency services immediately.",
                "Use an adrenaline auto-injector (EpiPen) into the outer thigh if available.",
                "Lay the person down; raise legs unless breathing is difficult (then sit up).",
                "If a second EpiPen is available and symptoms do not improve, use it after 5–15 minutes.",
                "Monitor airway, breathing, and circulation continuously.",
                "Be prepared to perform CPR if the person loses consciousness."
            ),
            doNots = listOf(
                "Do NOT stand the person up — this can cause sudden collapse.",
                "Do NOT delay using the EpiPen if available."
            )
        ),

        // ── POISONING ─────────────────────────────────────────────────
        FirstAidEntry(
            id = 13,
            title = "Ingested Poison / Overdose",
            category = FirstAidCategory.POISONING,
            severity = Severity.URGENT,
            keywords = listOf("poison", "overdose", "swallowed", "toxic", "chemical ingested", "drug overdose"),
            steps = listOf(
                "Call emergency services immediately; try to identify the substance.",
                "If conscious and not vomiting, do not induce vomiting unless specifically instructed by poison control.",
                "If the person is unconscious but breathing, place in recovery position.",
                "If not breathing, begin CPR.",
                "Keep any containers, labels, or vomit for identification by medical staff.",
                "Keep the person calm and still.",
                "Do not give food or drink."
            ),
            doNots = listOf(
                "Do NOT induce vomiting — many substances cause more damage coming back up.",
                "Do NOT give milk or water unless instructed by a professional."
            )
        ),

        // ── SNAKE BITE ────────────────────────────────────────────────
        FirstAidEntry(
            id = 14,
            title = "Snake Bite",
            category = FirstAidCategory.SNAKE_BITE,
            severity = Severity.CRITICAL,
            keywords = listOf("snake", "bite", "venom", "envenomation", "fang marks"),
            steps = listOf(
                "Keep the person calm and completely still to slow venom spread.",
                "Remove watches, rings, and tight clothing near the bite site.",
                "Immobilise the bitten limb at or below heart level using a splint or sling.",
                "Apply a pressure immobilisation bandage for neurotoxic snakes (elapids): start at the bite, wrap firmly up the limb.",
                "Mark the edge of any swelling with a pen and note the time.",
                "Transport to a hospital with anti-venom as quickly and calmly as possible.",
                "Note the snake's appearance if possible for identification — do not try to catch it."
            ),
            doNots = listOf(
                "Do NOT cut the wound or attempt to suck out venom.",
                "Do NOT apply a tourniquet.",
                "Do NOT apply ice or immerse in cold water.",
                "Do NOT give alcohol or stimulants."
            )
        ),
        FirstAidEntry(
            id = 15,
            title = "Choking (Adult / Child)",
            category = FirstAidCategory.SHOCK,
            severity = Severity.CRITICAL,
            keywords = listOf("choking", "airway", "obstruction", "heimlich", "cannot breathe", "blocked throat"),
            steps = listOf(
                "Ask 'Are you choking?' If the person can speak or cough, encourage them to cough hard.",
                "If they cannot speak or cough effectively, stand behind them and give 5 firm back blows between the shoulder blades with the heel of your hand.",
                "Give up to 5 abdominal thrusts (Heimlich): wrap arms around waist, make a fist above navel, pull sharply inward and upward.",
                "Alternate 5 back blows and 5 abdominal thrusts.",
                "If the person becomes unconscious, lower them to the ground and begin CPR.",
                "Check the mouth for the object before each rescue breath — remove only if clearly visible.",
                "Seek medical review after choking even if the object is dislodged."
            ),
            doNots = listOf(
                "Do NOT perform blind finger sweeps in the mouth.",
                "Do NOT use abdominal thrusts on infants under 1 year — use back blows and chest thrusts only."
            )
        )
    )

    private val _uiState = MutableStateFlow(
        FirstAidUiState(filteredEntries = allEntries)
    )
    val uiState: StateFlow<FirstAidUiState> = _uiState.asStateFlow()

    fun onSearchQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        applyFilters()
    }

    fun onCategorySelected(category: FirstAidCategory) {
        _uiState.value = _uiState.value.copy(selectedCategory = category)
        applyFilters()
    }

    fun onEntryToggle(entryId: Int) {
        val current = _uiState.value.expandedEntryId
        _uiState.value = _uiState.value.copy(
            expandedEntryId = if (current == entryId) null else entryId
        )
    }

    private fun applyFilters() {
        val state = _uiState.value
        val query = state.searchQuery.trim().lowercase()
        val category = state.selectedCategory

        val filtered = allEntries.filter { entry ->
            val matchesCategory = category == FirstAidCategory.ALL || entry.category == category
            val matchesSearch = query.isEmpty() ||
                    entry.title.lowercase().contains(query) ||
                    entry.keywords.any { it.lowercase().contains(query) }
            matchesCategory && matchesSearch
        }

        _uiState.value = state.copy(filteredEntries = filtered)
    }
}