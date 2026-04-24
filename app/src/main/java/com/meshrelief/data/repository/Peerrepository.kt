package com.meshrelief.data.repository

import com.meshrelief.data.db.dao.PeerDao
import com.meshrelief.data.db.entity.PeerEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PeerRepository @Inject constructor(private val dao: PeerDao) {

    // ── Existing reads ────────────────────────────────────────────────────
    fun getAllPeers() = dao.getAllPeers()
    fun getVerified() = dao.getVerified()

    // ── Existing writes ───────────────────────────────────────────────────
    suspend fun upsert(peer: PeerEntity) = dao.upsert(peer)

    // ── Existing lookups ──────────────────────────────────────────────────
    suspend fun getById(id: String) = dao.getPeerById(id)

    suspend fun pruneStale(cutoff: Long) = dao.deleteStalePeers(cutoff)

    // BUG 5 FIX — snapshot for public-key map in MeshForegroundService
    suspend fun getAllPeersSnapshot(): List<PeerEntity> = dao.getAllSnapshot()

    // BUG 9 FIX — targeted status UPDATE (triage, battery, lastSeen only)
    suspend fun updateStatusFromPacket(
        deviceId : String,
        triage   : String,
        battery  : Int,
        lastSeen : Long
    ) {
        dao.updateStatus(
            deviceId = deviceId,
            triage   = triage,
            battery  = battery,
            lastSeen = lastSeen
        )
    }

    // ── MISSING 4 FIX — trust system ─────────────────────────────────────
    suspend fun setVerified(id: String, v: Boolean) = dao.setVerified(id, v)
    suspend fun setFlagged(id: String, f: Boolean)  = dao.setFlagged(id, f)
}