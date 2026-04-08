package com.meshrelief.data.db.dao

import androidx.room.*
import com.meshrelief.data.db.entity.CampEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CampDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(camp: CampEntity)

    @Query("SELECT * FROM camps ORDER BY name ASC")
    fun getAllCamps(): Flow<List<CampEntity>>

    @Query("SELECT * FROM camps WHERE id = :campId")
    suspend fun getCampById(campId: String): CampEntity?

    @Update
    suspend fun update(camp: CampEntity)
}