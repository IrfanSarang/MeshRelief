package com.meshrelief.core.model

/**
 * Network / map-layer triage indicator.
 * Used by MapViewModel and AdminViewModel when reporting peer status
 * across the mesh. Stored as a String in Room.
 *
 * For the patient-facing clinical scale (SAFE / MINOR / CRITICAL / UNKNOWN)
 * used in Discovery and SOS flows, see [TriageLevel].
 */
enum class TriageStatus { GREEN, AMBER, RED, UNKNOWN }