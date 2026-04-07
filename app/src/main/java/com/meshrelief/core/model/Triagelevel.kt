package com.meshrelief.core.model

/**
 * Patient-facing clinical triage level.
 * Used by DiscoveryViewModel and SOSViewModel when a person self-reports
 * or is assessed at point-of-care.
 *
 * Each entry carries a human-readable [label] and an ARGB [color] so UI
 * layers can render badges without duplicating this data.
 *
 * For the network/map colour indicator (GREEN / AMBER / RED / UNKNOWN)
 * used in Map and Admin flows, see [TriageStatus].
 */
enum class TriageLevel(val label: String, val color: Long) {
    SAFE("Safe",              0xFF1D9E75),
    MINOR("Minor injury",     0xFFEF9F27),
    CRITICAL("Critical",      0xFFE24B4A),
    UNKNOWN("Unknown",        0xFF888780)
}