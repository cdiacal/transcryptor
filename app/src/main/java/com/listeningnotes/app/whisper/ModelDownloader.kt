package com.listeningnotes.app.whisper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

class ModelDownloadException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Descarga un modelo a `filesDir/models/` con reanudación (cabecera Range), seguimiento manual de
 * redirecciones (Hugging Face redirige a un CDN) y comprobación de tamaño.
 * Los datos parciales se guardan en `<modelo>.part`, que se reutiliza al reintentar.
 */
class ModelDownloader(private val modelsDir: File) {

    fun partFile(spec: ModelSpec) = File(modelsDir, spec.fileName + ".part")

    /** @param onProgress (bytes descargados, bytes totales o -1 si se desconoce) */
    suspend fun download(spec: ModelSpec, onProgress: (Long, Long) -> Unit) =
        withContext(Dispatchers.IO) { downloadInternal(spec, onProgress, allowRestart = true) }

    private suspend fun downloadInternal(
        spec: ModelSpec,
        onProgress: (Long, Long) -> Unit,
        allowRestart: Boolean,
    ) {
        if (!modelsDir.exists() && !modelsDir.mkdirs()) {
            throw ModelDownloadException("No se pudo crear la carpeta de modelos")
        }
        val part = partFile(spec)
        val target = spec.file(modelsDir)
        val existing = if (part.exists()) part.length() else 0L

        val conn = try {
            connect(spec.url, existing)
        } catch (e: IOException) {
            throw ModelDownloadException("Sin conexión con el servidor de modelos", e)
        }
        try {
            val code = try {
                conn.responseCode
            } catch (e: IOException) {
                throw ModelDownloadException("Sin conexión con el servidor de modelos", e)
            }

            val append: Boolean
            var total: Long
            when (code) {
                HttpURLConnection.HTTP_PARTIAL -> {
                    append = true
                    total = parseContentRangeTotal(conn.getHeaderField("Content-Range"))
                        ?: conn.contentLengthLong.takeIf { it > 0 }?.plus(existing) ?: -1L
                }
                HttpURLConnection.HTTP_OK -> {
                    append = false // el servidor ignoró Range: empezamos de cero
                    total = conn.contentLengthLong
                }
                416 -> {
                    // Range fuera de rango: o el .part ya estaba completo o está corrupto.
                    val serverTotal = parseContentRangeTotal(conn.getHeaderField("Content-Range"))
                    if (existing > 0 && serverTotal == existing) {
                        finish(spec, part, target, existing)
                        onProgress(existing, existing)
                        return
                    }
                    part.delete()
                    if (allowRestart) return downloadInternal(spec, onProgress, allowRestart = false)
                    throw ModelDownloadException("La descarga previa estaba corrupta; vuelve a intentarlo")
                }
                else -> throw ModelDownloadException("El servidor respondió con HTTP $code")
            }

            var downloaded = if (append) existing else 0L
            onProgress(downloaded, total)

            val buffer = ByteArray(64 * 1024)
            var lastReport = 0L
            try {
                RandomAccessFile(part, "rw").use { out ->
                    if (append) out.seek(existing) else out.setLength(0)
                    conn.inputStream.use { input ->
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            downloaded += n
                            val now = System.nanoTime()
                            if (now - lastReport > 200_000_000L) {
                                lastReport = now
                                onProgress(downloaded, total)
                            }
                        }
                    }
                }
            } catch (e: IOException) {
                // Conservamos el .part para poder reanudar.
                throw ModelDownloadException("Descarga interrumpida (${e.message ?: "error de red"})", e)
            }

            if (total <= 0) total = downloaded
            finish(spec, part, target, total)
            onProgress(total, total)
        } finally {
            conn.disconnect()
        }
    }

    /** Comprueba el tamaño y mueve el .part al nombre definitivo. */
    private fun finish(spec: ModelSpec, part: File, target: File, expectedTotal: Long) {
        val size = part.length()
        if (size != expectedTotal || !spec.hasPlausibleSize(size)) {
            part.delete()
            throw ModelDownloadException(
                "El tamaño del modelo descargado no es correcto ($size bytes). Inténtalo de nuevo."
            )
        }
        if (target.exists()) target.delete()
        if (!part.renameTo(target)) {
            throw ModelDownloadException("No se pudo guardar el modelo descargado")
        }
    }

    private fun connect(url: String, rangeStart: Long): HttpURLConnection {
        var current = url
        repeat(MAX_REDIRECTS) {
            val conn = URL(current).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("Accept-Encoding", "identity") // para que Content-Length sea fiable
            conn.setRequestProperty("User-Agent", "ListeningNotes/1.0")
            if (rangeStart > 0) conn.setRequestProperty("Range", "bytes=$rangeStart-")
            val code = conn.responseCode
            if (code in intArrayOf(301, 302, 303, 307, 308)) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) throw IOException("Redirección sin destino")
                current = URL(URL(current), location).toString()
            } else {
                return conn
            }
        }
        throw IOException("Demasiadas redirecciones")
    }

    companion object {
        private const val MAX_REDIRECTS = 6

        // "bytes 100-199/1000" o "bytes asterisco/1000" -> 1000
        fun parseContentRangeTotal(header: String?): Long? =
            header?.substringAfterLast('/')?.trim()?.toLongOrNull()
    }
}
