package com.listeningnotes.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.listeningnotes.app.whisper.DEFAULT_MODEL_ID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

enum class ThemeMode(val label: String) {
    SYSTEM("Sistema"),
    LIGHT("Claro"),
    DARK("Oscuro"),
}

data class AppSettings(
    val modelId: String = DEFAULT_MODEL_ID,
    val threads: Int = defaultThreads(),
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** Divide los segmentos de whisper en frases más cortas al transcribir. */
    val splitSentences: Boolean = true,
) {
    companion object {
        fun maxThreads(): Int = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        fun defaultThreads(): Int = minOf(4, maxThreads())
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val MODEL = stringPreferencesKey("model_id")
        val THREADS = intPreferencesKey("threads")
        val THEME = stringPreferencesKey("theme")
        val SPLIT = booleanPreferencesKey("split_sentences")
    }

    val settings: Flow<AppSettings> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw e }
        .map { p ->
            AppSettings(
                modelId = p[Keys.MODEL] ?: DEFAULT_MODEL_ID,
                threads = (p[Keys.THREADS] ?: AppSettings.defaultThreads()).coerceIn(1, AppSettings.maxThreads()),
                theme = p[Keys.THEME]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: ThemeMode.SYSTEM,
                splitSentences = p[Keys.SPLIT] ?: true,
            )
        }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setModel(id: String) = context.dataStore.edit { it[Keys.MODEL] = id }
    suspend fun setThreads(n: Int) = context.dataStore.edit { it[Keys.THREADS] = n }
    suspend fun setTheme(mode: ThemeMode) = context.dataStore.edit { it[Keys.THEME] = mode.name }
    suspend fun setSplitSentences(value: Boolean) = context.dataStore.edit { it[Keys.SPLIT] = value }
}
