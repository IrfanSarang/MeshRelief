package com.meshrelief.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "camps")
data class CampEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val lat: Double,
    val lng: Double,
    val capacity: Int,
    val currentCount: Int = 0,
    val status: String = "OPEN",
    val notes: String = "",
    val adminId: String,
    val updatedAt: Long
)