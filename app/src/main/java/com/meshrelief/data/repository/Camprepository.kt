package com.meshrelief.data.repository

import com.meshrelief.data.db.dao.CampDao
import com.meshrelief.data.db.entity.CampEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CampRepository @Inject constructor(private val dao: CampDao) {

    fun getAllCamps() = dao.getAllCamps()

    suspend fun upsert(camp: CampEntity) = dao.upsert(camp)

    suspend fun getById(id: String) = dao.getCampById(id)

    suspend fun update(camp: CampEntity) = dao.update(camp)
}