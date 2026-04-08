package com.meshrelief.data.db.dao

import androidx.room.*
import com.meshrelief.data.db.entity.PeerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PeerDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(peer: PeerEntity)

    @Query("SELECT * FROM peers ORDER BY lastSeen DESC")
    fun getAllPeers(): Flow<List<PeerEntity>>

    @Query("SELECT * FROM peers WHERE deviceId = :id")
    suspend fun getPeerById(id: String): PeerEntity?

    @Query("DELETE FROM peers WHERE lastSeen < :cutoff")
    suspend fun deleteStalePeers(cutoff: Long)
}