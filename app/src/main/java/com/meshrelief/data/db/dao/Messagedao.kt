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

    // Issue #3 — group messages are stored with receiverId = "GROUP"
    @Query("SELECT * FROM messages WHERE receiverId = 'GROUP' ORDER BY timestamp ASC")
    fun getGroupMessages(): Flow<List<MessageEntity>>

    // Issue #3 — P2P thread between this device and a specific peer
    @Query("""
        SELECT * FROM messages
        WHERE (senderId = :myDeviceId AND receiverId = :peerId)
           OR (senderId = :peerId   AND receiverId = :myDeviceId)
        ORDER BY timestamp ASC
    """)
    fun getP2pMessages(myDeviceId: String, peerId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE receiverId = :peerId OR senderId = :peerId ORDER BY timestamp ASC")
    fun getMessagesWithPeer(peerId: String): Flow<List<MessageEntity>>

    @Query("DELETE FROM messages WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}