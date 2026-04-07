package com.meshrelief.features.setup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshrelief.data.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LanguageSelectionViewModel @Inject constructor(
    private val userPreferences: UserPreferences
) : ViewModel() {

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
            userPreferences.saveLanguage(selectedLanguage)
            isSaving = false
            onDone()
        }
    }
}