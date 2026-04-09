package com.meshrelief.data.repository

import com.meshrelief.data.db.dao.BulletinDao
import com.meshrelief.data.db.entity.BulletinEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BulletinRepository @Inject constructor(private val dao: BulletinDao) {

    fun getAllBulletins() = dao.getAllBulletins()

    fun getLatest(n: Int) = dao.getLatest(n)

    suspend fun save(bulletin: BulletinEntity) = dao.insert(bulletin)

    suspend fun pruneOlderThan(cutoff: Long) = dao.deleteOlderThan(cutoff)
}