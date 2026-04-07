package com.meshrelief

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshrelief.data.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val userPreferences: UserPreferences
) : ViewModel() {

    /**
     * Nullable so we can distinguish three states:
     *   null  → DataStore read still in-flight (show splash)
     *   false → first-time user (go to setup)
     *   true  → returning user (go to home)
     */
    val setupComplete: StateFlow<Boolean?> = userPreferences.setupComplete
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null          // null = "not yet known"
        )
}