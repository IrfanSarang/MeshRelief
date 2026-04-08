package com.meshrelief.data.db.dao

import androidx.room.*
import com.meshrelief.data.db.entity.SOSEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SOSDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sos: SOSEntity)

    @Query("SELECT * FROM sos_alerts WHERE resolved = 0 ORDER BY timestamp DESC")
    fun getActiveAlerts(): Flow<List<SOSEntity>>

    @Query("UPDATE sos_alerts SET resolved = 1 WHERE id = :sosId")
    suspend fun markResolved(sosId: String)

    @Query("SELECT * FROM sos_alerts ORDER BY timestamp DESC")
    fun getAllAlerts(): Flow<List<SOSEntity>>
}