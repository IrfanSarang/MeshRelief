package com.meshrelief.data.repository

import com.meshrelief.data.db.dao.PeerDao
import com.meshrelief.data.db.entity.PeerEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PeerRepository @Inject constructor(private val dao: PeerDao) {

    fun getAllPeers() = dao.getAllPeers()
    fun getVerified() = dao.getVerified()

    suspend fun upsert(peer: PeerEntity) = dao.upsert(peer)

    suspend fun getById(id: String) = dao.getPeerById(id)

    suspend fun pruneStale(cutoff: Long) = dao.deleteStalePeers(cutoff)
}