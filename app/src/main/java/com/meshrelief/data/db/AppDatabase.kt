package com.meshrelief.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.meshrelief.data.db.dao.BulletinDao
import com.meshrelief.data.db.dao.CampDao
import com.meshrelief.data.db.dao.MessageDao
import com.meshrelief.data.db.dao.PeerDao
import com.meshrelief.data.db.dao.SOSDao
import com.meshrelief.data.db.entity.BulletinEntity
import com.meshrelief.data.db.entity.CampEntity
import com.meshrelief.data.db.entity.MessageEntity
import com.meshrelief.data.db.entity.PeerEntity
import com.meshrelief.data.db.entity.SOSEntity

@Database(
    entities = [
        MessageEntity::class,
        PeerEntity::class,
        SOSEntity::class,
        CampEntity::class,
        BulletinEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun messageDao(): MessageDao
    abstract fun peerDao(): PeerDao
    abstract fun campDao(): CampDao
    abstract fun sosDao(): SOSDao
    abstract fun bulletinDao(): BulletinDao
}