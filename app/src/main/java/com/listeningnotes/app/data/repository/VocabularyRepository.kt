package com.listeningnotes.app.data.repository

import com.listeningnotes.app.data.model.Vocab
import com.listeningnotes.app.data.room.VocabDao
import com.listeningnotes.app.data.room.VocabEntity
import com.listeningnotes.app.data.room.toModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class VocabularyRepository(private val dao: VocabDao) {

    fun observeAll(): Flow<List<Vocab>> = dao.observeAll().map { list -> list.map { it.toModel() } }

    suspend fun save(
        palabra: String,
        nota: String,
        contexto: String,
        transcriptionId: Long,
        inicioMs: Long,
        tituloOrigen: String,
    ) {
        dao.upsert(
            VocabEntity(
                palabra = palabra,
                nota = nota.trim(),
                contexto = contexto,
                transcriptionId = transcriptionId,
                inicioMs = inicioMs,
                tituloOrigen = tituloOrigen,
                fecha = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun remove(palabra: String) = dao.deleteByWord(palabra)
    suspend fun delete(id: Long) = dao.delete(id)

    /** Borra varias palabras; en bloques para no superar el límite de variables de SQLite. */
    suspend fun deleteMany(ids: Collection<Long>) = ids.chunked(500).forEach { dao.deleteByIds(it) }
    suspend fun updateNote(id: Long, nota: String) = dao.updateNote(id, nota.trim())
}
