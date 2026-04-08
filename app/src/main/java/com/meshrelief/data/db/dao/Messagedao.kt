package com.meshrelief.data.db.dao

import androidx.room.*
import com.meshrelief.data.db.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    @Query("SELECT * FROM messages ORDER BY timestamp DESC")
    fun getAllMessages(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE receiverId = :peerId OR senderId = :peerId ORDER BY timestamp ASC")
    fun getMessagesWithPeer(peerId: String): Flow<List<MessageEntity>>

    @Query("DELETE FROM messages WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}