package com.meshrelief.data.db.dao

import androidx.room.*
import com.meshrelief.data.db.entity.PeerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PeerDao {

    // ── Full-entity upsert ────────────────────────────────────────────────
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(peer: PeerEntity)

    // ── Queries (Flow) ────────────────────────────────────────────────────
    @Query("SELECT * FROM peers ORDER BY lastSeen DESC")
    fun getAllPeers(): Flow<List<PeerEntity>>

    @Query("SELECT * FROM peers WHERE verified = 1")
    fun getVerified(): Flow<List<PeerEntity>>

    // ── Single-row lookups ────────────────────────────────────────────────
    @Query("SELECT * FROM peers WHERE deviceId = :id")
    suspend fun getPeerById(id: String): PeerEntity?

    // ── Maintenance ───────────────────────────────────────────────────────
    @Query("DELETE FROM peers WHERE lastSeen < :cutoff")
    suspend fun deleteStalePeers(cutoff: Long)

    // BUG 5 FIX — full snapshot for public-key map construction
    @Query("SELECT * FROM peers")
    suspend fun getAllSnapshot(): List<PeerEntity>

    // ── Bug #9 FIX — status-only UPDATE ──────────────────────────────────
    @Query("""
        UPDATE peers
        SET    triageStatus = :triage,
               battery      = :battery,
               lastSeen     = :lastSeen
        WHERE  deviceId     = :deviceId
    """)
    suspend fun updateStatus(
        deviceId : String,
        triage   : String,
        battery  : Int,
        lastSeen : Long
    )

    // ── MISSING 4 FIX — trust system persistence ──────────────────────────
    @Query("UPDATE peers SET verified = :v WHERE deviceId = :id")
    suspend fun setVerified(id: String, v: Boolean)

    @Query("UPDATE peers SET flagged = :f WHERE deviceId = :id")
    suspend fun setFlagged(id: String, f: Boolean)

    // ── MISSING 11 FIX — persist real hop distance from router ────────────
    @Query("UPDATE peers SET hopCount = :hops WHERE deviceId = :id")
    suspend fun updateHopCount(id: String, hops: Int)
}