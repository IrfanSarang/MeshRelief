package com.meshrelief.data.db.dao

import androidx.room.*
import com.meshrelief.data.db.entity.BulletinEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BulletinDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bulletin: BulletinEntity)

    @Query("SELECT * FROM bulletins ORDER BY timestamp DESC")
    fun getAllBulletins(): Flow<List<BulletinEntity>>

    @Query("DELETE FROM bulletins WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    @Query("SELECT * FROM bulletins ORDER BY timestamp DESC LIMIT :n")
    fun getLatest(n: Int): Flow<List<BulletinEntity>>
}