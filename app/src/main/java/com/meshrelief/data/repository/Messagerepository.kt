package com.meshrelief.data.repository

import com.meshrelief.data.db.dao.MessageDao
import com.meshrelief.data.db.entity.MessageEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepository @Inject constructor(private val dao: MessageDao) {

    fun getAllMessages() = dao.getAllMessages()

    fun getMessagesWithPeer(peerId: String) = dao.getMessagesWithPeer(peerId)

    suspend fun save(message: MessageEntity) = dao.insert(message)

    suspend fun pruneOlderThan(cutoff: Long) = dao.deleteOlderThan(cutoff)
}