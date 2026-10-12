package com.listeningnotes.app.data.repository

import com.listeningnotes.app.data.model.Segment
import com.listeningnotes.app.data.model.Transcription
import com.listeningnotes.app.data.room.SegmentEntity
import com.listeningnotes.app.data.room.TranscriptionDao
import com.listeningnotes.app.data.room.TranscriptionEntity
import com.listeningnotes.app.data.room.toModel
import com.listeningnotes.app.whisper.RawSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

class TranscriptionRepository(private val dao: TranscriptionDao) {

    /** Lista de la biblioteca; con [query] no vacío filtra por título o por texto de cualquier frase. */
    fun observeLibrary(query: String): Flow<List<Transcription>> {
        val trimmed = query.trim()
        val source = if (trimmed.isEmpty()) dao.observeAll() else dao.search(likePattern(trimmed))
        return source.map { list -> list.map { it.toModel() } }
    }

    fun observeTranscription(id: Long): Flow<Transcription?> = dao.observe(id).map { it?.toModel() }

    fun observeSegments(id: Long): Flow<List<Segment>> =
        dao.observeSegments(id).map { list -> list.map { it.toModel() } }

    suspend fun getSegments(id: Long): List<Segment> = dao.getSegments(id).map { it.toModel() }

    suspend fun getTranscription(id: Long): Transcription? = dao.get(id)?.toModel()

    suspend fun save(
        titulo: String,
        rutaAudio: String,
        duracionMs: Long,
        modelo: String,
        segments: List<RawSegment>,
    ): Long = dao.insertWithSegments(
        TranscriptionEntity(
            titulo = titulo,
            rutaAudio = rutaAudio,
            duracionMs = duracionMs,
            fecha = System.currentTimeMillis(),
            modelo = modelo,
        ),
        segments.map { SegmentEntity(transcriptionId = 0, inicioMs = it.startMs, finMs = it.endMs, texto = it.text) },
    )

    suspend fun rename(id: Long, titulo: String) = dao.rename(id, titulo.trim())

    /** Borra la transcripción (los segmentos caen en cascada) y su copia local del audio. */
    suspend fun delete(transcription: Transcription) {
        dao.delete(transcription.id)
        withContext(Dispatchers.IO) { File(transcription.rutaAudio).delete() }
    }

    suspend fun updateSegmentText(segmentId: Long, texto: String) = dao.updateSegmentText(segmentId, texto.trim())

    suspend fun setFavorite(segmentId: Long, favorita: Boolean) = dao.setFavorite(segmentId, favorita)

    companion object {
        /** Construye el patrón LIKE con comodines escapando % _ y \ (ESCAPE '\'). */
        fun likePattern(query: String): String {
            val escaped = query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
            return "%$escaped%"
        }
    }
}
