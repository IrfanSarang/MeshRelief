package com.meshrelief.features.setup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LanguageSelectionViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : ViewModel() {

    companion object {
        val LANGUAGE_KEY = stringPreferencesKey("preferred_language")
    }

    var selectedLanguage by mutableStateOf("en")
        private set

    var isSaving by mutableStateOf(false)
        private set

    fun selectLanguage(code: String) {
        selectedLanguage = code
    }

    fun confirmAndSave(onDone: () -> Unit) {
        isSaving = true
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[LANGUAGE_KEY] = selectedLanguage
            }
            isSaving = false
            onDone()
        }
    }
}