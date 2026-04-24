package com.meshrelief.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.meshrelief.data.db.AppDatabase
import com.meshrelief.data.db.dao.BulletinDao
import com.meshrelief.data.db.dao.CampDao
import com.meshrelief.data.db.dao.MessageDao
import com.meshrelief.data.db.dao.PeerDao
import com.meshrelief.data.db.dao.SOSDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// v1 → v2: added publicKeyBytes column (Bug 5 / Bug 16)
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE peers ADD COLUMN publicKeyBytes BLOB NOT NULL DEFAULT ''"
        )
    }
}

// v2 → v3: added linkQuality column (Recommendation 5)
private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE peers ADD COLUMN linkQuality INTEGER NOT NULL DEFAULT 0"
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        "meshrelief_db"
    )
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
        .build()

    @Provides
    fun provideMessageDao(db: AppDatabase): MessageDao = db.messageDao()

    @Provides
    fun providePeerDao(db: AppDatabase): PeerDao = db.peerDao()

    @Provides
    fun provideCampDao(db: AppDatabase): CampDao = db.campDao()

    @Provides
    fun provideSosDao(db: AppDatabase): SOSDao = db.sosDao()

    @Provides
    fun provideBulletinDao(db: AppDatabase): BulletinDao = db.bulletinDao()
}