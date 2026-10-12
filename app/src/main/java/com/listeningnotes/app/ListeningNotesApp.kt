package com.listeningnotes.app

import android.app.Application
import android.content.Context
import com.listeningnotes.app.audio.AudioDecoder
import com.listeningnotes.app.audio.AudioImporter
import com.listeningnotes.app.data.repository.SettingsRepository
import com.listeningnotes.app.data.repository.TranscriptionRepository
import com.listeningnotes.app.data.repository.VocabularyRepository
import com.listeningnotes.app.data.room.AppDatabase
import com.listeningnotes.app.transcription.TranscriptionManager
import com.listeningnotes.app.transcription.TranscriptionService
import com.listeningnotes.app.whisper.ModelManager
import com.listeningnotes.app.whisper.WhisperEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import android.net.Uri
import com.listeningnotes.app.whisper.ModelSpec
import com.listeningnotes.app.whisper.ModelState
import com.listeningnotes.app.whisper.Models

class ListeningNotesApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        TranscriptionService.createChannel(this)
    }
}

/** Inyección manual de dependencias (sin Hilt): un único contenedor con los singletons de la app. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext

    /** Ámbito de la aplicación para trabajos que deben sobrevivir a las pantallas. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: AppDatabase by lazy { AppDatabase.create(appContext) }
    val settings = SettingsRepository(appContext)
    val repository: TranscriptionRepository by lazy { TranscriptionRepository(database.transcriptionDao()) }
    val vocabulary: VocabularyRepository by lazy { VocabularyRepository(database.vocabDao()) }
    val modelManager = ModelManager(appContext, appScope)
    val importer = AudioImporter(appContext)

    val transcriptionManager: TranscriptionManager by lazy {
        TranscriptionManager(
            context = appContext,
            scope = appScope,
            importer = importer,
            decoder = AudioDecoder(),
            whisper = WhisperEngine(appContext),
            models = modelManager,
            settings = settings,
            repository = repository,
        )
    }

    /** Audio recibido por ACTION_SEND / ACTION_VIEW pendiente de procesar por la UI. */
    val sharedAudio = MutableStateFlow<Uri?>(null)
}

/** Modelo seleccionado en ajustes junto con su estado actual. */
data class ModelStatus(val spec: ModelSpec, val state: ModelState)

fun AppContainer.selectedModelStatus(): Flow<ModelStatus> =
    combine(settings.settings, modelManager.states) { cfg, states ->
        val spec = Models.byId(cfg.modelId)
        ModelStatus(spec, states[spec.id] ?: ModelState.NotInstalled())
    }.distinctUntilChanged()
