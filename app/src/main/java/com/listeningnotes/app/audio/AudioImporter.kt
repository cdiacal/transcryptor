package com.listeningnotes.app.audio

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.UUID

class AudioImportException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class ImportedAudio(val file: File, val title: String)

/** Copia el audio elegido/compartido al almacenamiento interno (`filesDir/audio`) para poder reproducirlo después. */
class AudioImporter(private val context: Context) {

    val audioDir = File(context.filesDir, "audio")

    suspend fun import(uri: Uri): ImportedAudio = withContext(Dispatchers.IO) {
        if (!audioDir.exists() && !audioDir.mkdirs()) {
            throw AudioImportException("No se pudo crear la carpeta de audios")
        }
        val displayName = queryDisplayName(uri)
        val extension = displayName?.substringAfterLast('.', "")?.takeIf { it.length in 1..5 }
            ?: MimeTypeMap.getSingleton().getExtensionFromMimeType(context.contentResolver.getType(uri))
            ?: "audio"
        val dest = File(audioDir, "${UUID.randomUUID()}.$extension")
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw AudioImportException("No se pudo abrir el archivo de audio")
            input.use { src -> dest.outputStream().use { dst -> src.copyTo(dst) } }
        } catch (e: AudioImportException) {
            dest.delete(); throw e
        } catch (e: FileNotFoundException) {
            dest.delete(); throw AudioImportException("El archivo ya no está disponible", e)
        } catch (e: SecurityException) {
            dest.delete(); throw AudioImportException("No hay permiso para leer ese archivo", e)
        } catch (e: IOException) {
            dest.delete(); throw AudioImportException("Error al copiar el audio: ${e.message}", e)
        }
        if (dest.length() == 0L) {
            dest.delete()
            throw AudioImportException("El archivo de audio está vacío")
        }
        val title = displayName?.substringBeforeLast('.')?.ifBlank { null } ?: "Audio ${dest.nameWithoutExtension.take(4)}"
        ImportedAudio(dest, title)
    }

    private fun queryDisplayName(uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment
    } catch (e: Exception) {
        uri.lastPathSegment
    }
}
