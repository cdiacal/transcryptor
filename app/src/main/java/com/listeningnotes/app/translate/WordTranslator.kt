package com.listeningnotes.app.translate

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class TranslationException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Traducción inglés -> español con ML Kit. El modelo (~30 MB) se descarga una sola vez; después
 * traduce en el propio móvil, sin conexión.
 */
class WordTranslator {

    private val translator by lazy {
        Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(TranslateLanguage.SPANISH)
                .build(),
        )
    }

    /** Descarga el modelo si hace falta (no hace nada si ya está). */
    suspend fun ensureModel() {
        try {
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
        } catch (e: Exception) {
            throw TranslationException(
                "No se pudo descargar el modelo de traducción. Necesita conexión solo la primera vez.", e,
            )
        }
    }

    suspend fun translate(text: String): String {
        val result = try {
            translator.translate(text).await()
        } catch (e: Exception) {
            throw TranslationException("No se pudo traducir \"$text\"", e)
        }
        // Una palabra suelta en minúsculas se devuelve en minúsculas.
        return if (text.firstOrNull()?.isLowerCase() == true && !text.contains(' ')) result.replaceFirstChar { it.lowercase() } else result
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
        addOnCanceledListener { cont.cancel() }
    }
}
