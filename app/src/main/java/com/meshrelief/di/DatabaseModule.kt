package com.meshrelief.di

import android.content.Context
import androidx.room.Room
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
    ).build()

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