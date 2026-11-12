package com.example.concessionaria.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

data class AppSettings(
    val token: String = "",
    val userName: String = "",
    val userRole: String = "",
    val displayName: String = "",
    val darkTheme: Boolean = false,
    val notifications: Boolean = true,
    val vehicleSort: String = "recentes"
)

class UserPreferences(context: Context) {
    private val dataStore = context.settingsDataStore

    val settings: Flow<AppSettings> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
            else throw error
        }
        .map { preferences ->
            AppSettings(
                token = preferences[TOKEN].orEmpty(),
                userName = preferences[USER_NAME].orEmpty(),
                userRole = preferences[USER_ROLE].orEmpty(),
                displayName = preferences[DISPLAY_NAME].orEmpty(),
                darkTheme = preferences[DARK_THEME] ?: false,
                notifications = preferences[NOTIFICATIONS] ?: true,
                vehicleSort = preferences[VEHICLE_SORT] ?: "recentes"
            )
        }

    suspend fun saveSession(token: String, userName: String, userRole: String) {
        dataStore.edit {
            it[TOKEN] = token
            it[USER_NAME] = userName
            it[USER_ROLE] = userRole
        }
    }

    suspend fun clearSession() {
        dataStore.edit {
            it.remove(TOKEN)
            it.remove(USER_NAME)
            it.remove(USER_ROLE)
        }
    }

    suspend fun updateSettings(
        displayName: String? = null,
        darkTheme: Boolean? = null,
        notifications: Boolean? = null,
        vehicleSort: String? = null
    ) {
        dataStore.edit {
            displayName?.let { value -> it[DISPLAY_NAME] = value }
            darkTheme?.let { value -> it[DARK_THEME] = value }
            notifications?.let { value -> it[NOTIFICATIONS] = value }
            vehicleSort?.let { value -> it[VEHICLE_SORT] = value }
        }
    }

    private companion object {
        val TOKEN = stringPreferencesKey("token")
        val USER_NAME = stringPreferencesKey("user_name")
        val USER_ROLE = stringPreferencesKey("user_role")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val NOTIFICATIONS = booleanPreferencesKey("notifications")
        val VEHICLE_SORT = stringPreferencesKey("vehicle_sort")
    }
}
