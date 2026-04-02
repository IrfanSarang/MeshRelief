package com.meshrelief.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    companion object {
        val USER_NAME = stringPreferencesKey("user_name")
        val USER_PHONE = stringPreferencesKey("user_phone")
        val USER_DEVICE_ID = stringPreferencesKey("user_device_id")
        val USER_LANGUAGE = stringPreferencesKey("user_language")
        val IS_ADMIN = booleanPreferencesKey("is_admin")
        val SETUP_COMPLETE = booleanPreferencesKey("setup_complete")
        val MY_TRIAGE_STATUS = stringPreferencesKey("my_triage_status")
        val MY_STATUS_MESSAGE = stringPreferencesKey("my_status_message")
        val BATTERY_SAVER_MODE = booleanPreferencesKey("battery_saver_mode")
        val MAP_TILES_DOWNLOADED = booleanPreferencesKey("map_tiles_downloaded")
    }

    // ── Read flows ──────────────────────────────────────────

    val userName: Flow<String> = dataStore.data.map {
        it[USER_NAME] ?: ""
    }

    val userPhone: Flow<String> = dataStore.data.map {
        it[USER_PHONE] ?: ""
    }

    val userDeviceId: Flow<String> = dataStore.data.map {
        it[USER_DEVICE_ID] ?: ""
    }

    val userLanguage: Flow<String> = dataStore.data.map {
        it[USER_LANGUAGE] ?: "EN"
    }

    val isAdmin: Flow<Boolean> = dataStore.data.map {
        it[IS_ADMIN] ?: false
    }

    val setupComplete: Flow<Boolean> = dataStore.data.map {
        it[SETUP_COMPLETE] ?: false
    }

    val myTriageStatus: Flow<String> = dataStore.data.map {
        it[MY_TRIAGE_STATUS] ?: "SAFE"
    }

    val myStatusMessage: Flow<String> = dataStore.data.map {
        it[MY_STATUS_MESSAGE] ?: ""
    }

    val batterySaverMode: Flow<Boolean> = dataStore.data.map {
        it[BATTERY_SAVER_MODE] ?: false
    }

    val mapTilesDownloaded: Flow<Boolean> = dataStore.data.map {
        it[MAP_TILES_DOWNLOADED] ?: false
    }

    // ── Write functions ─────────────────────────────────────

    suspend fun saveUserName(name: String) {
        dataStore.edit { it[USER_NAME] = name }
    }

    suspend fun saveUserPhone(phone: String) {
        dataStore.edit { it[USER_PHONE] = phone }
    }

    suspend fun saveDeviceId(deviceId: String) {
        dataStore.edit { it[USER_DEVICE_ID] = deviceId }
    }

    suspend fun saveLanguage(language: String) {
        dataStore.edit { it[USER_LANGUAGE] = language }
    }

    suspend fun setAdmin(isAdmin: Boolean) {
        dataStore.edit { it[IS_ADMIN] = isAdmin }
    }

    suspend fun setSetupComplete(complete: Boolean) {
        dataStore.edit { it[SETUP_COMPLETE] = complete }
    }

    suspend fun saveTriageStatus(status: String) {
        dataStore.edit { it[MY_TRIAGE_STATUS] = status }
    }

    suspend fun saveStatusMessage(message: String) {
        dataStore.edit { it[MY_STATUS_MESSAGE] = message }
    }

    suspend fun setBatterySaverMode(enabled: Boolean) {
        dataStore.edit { it[BATTERY_SAVER_MODE] = enabled }
    }

    suspend fun setMapTilesDownloaded(downloaded: Boolean) {
        dataStore.edit { it[MAP_TILES_DOWNLOADED] = downloaded }
    }
}