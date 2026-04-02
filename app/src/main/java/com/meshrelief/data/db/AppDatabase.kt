package com.meshrelief.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.meshrelief.data.db.entity.MessageEntity
import com.meshrelief.data.db.entity.PeerEntity
import com.meshrelief.data.db.entity.SOSEntity
import com.meshrelief.data.db.entity.CampEntity
import com.meshrelief.data.db.entity.BulletinEntity

@Database(
    entities = [
        MessageEntity::class,
        PeerEntity::class,
        SOSEntity::class,
        CampEntity::class,
        BulletinEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    // DAOs will be added here as we build each feature
}