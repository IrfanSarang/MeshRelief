package com.meshrelief.data.repository

import com.meshrelief.data.db.dao.SOSDao
import com.meshrelief.data.db.entity.SOSEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SOSRepository @Inject constructor(private val dao: SOSDao) {

    fun getActiveAlerts() = dao.getActiveAlerts()

    fun getAllAlerts() = dao.getAllAlerts()

    suspend fun save(sos: SOSEntity) = dao.insert(sos)

    suspend fun markResolved(sosId: String) = dao.markResolved(sosId)
}