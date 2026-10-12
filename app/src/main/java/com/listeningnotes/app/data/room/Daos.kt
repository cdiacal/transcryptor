package com.listeningnotes.app.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class TranscriptionDao {

    @Insert
    abstract suspend fun insertTranscription(entity: TranscriptionEntity): Long

    @Insert
    abstract suspend fun insertSegments(segments: List<SegmentEntity>)

    /** Guarda la transcripción y todos sus segmentos de forma atómica. */
    @Transaction
    open suspend fun insertWithSegments(entity: TranscriptionEntity, segments: List<SegmentEntity>): Long {
        val id = insertTranscription(entity)
        insertSegments(segments.map { it.copy(transcriptionId = id) })
        return id
    }

    @Query("SELECT * FROM transcriptions ORDER BY fecha DESC")
    abstract fun observeAll(): Flow<List<TranscriptionEntity>>

    /** [pattern] ya debe venir con comodines y con % y _ escapados con '\'. */
    @Query(
        """
        SELECT * FROM transcriptions
        WHERE titulo LIKE :pattern ESCAPE '\'
           OR id IN (SELECT transcriptionId FROM segments WHERE texto LIKE :pattern ESCAPE '\')
        ORDER BY fecha DESC
        """
    )
    abstract fun search(pattern: String): Flow<List<TranscriptionEntity>>

    @Query("SELECT * FROM transcriptions WHERE id = :id")
    abstract fun observe(id: Long): Flow<TranscriptionEntity?>

    @Query("SELECT * FROM transcriptions WHERE id = :id")
    abstract suspend fun get(id: Long): TranscriptionEntity?

    @Query("UPDATE transcriptions SET titulo = :titulo WHERE id = :id")
    abstract suspend fun rename(id: Long, titulo: String)

    @Query("DELETE FROM transcriptions WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query("SELECT * FROM segments WHERE transcriptionId = :id ORDER BY inicioMs ASC, id ASC")
    abstract fun observeSegments(id: Long): Flow<List<SegmentEntity>>

    @Query("SELECT * FROM segments WHERE transcriptionId = :id ORDER BY inicioMs ASC, id ASC")
    abstract suspend fun getSegments(id: Long): List<SegmentEntity>

    @Query("UPDATE segments SET texto = :texto WHERE id = :segmentId")
    abstract suspend fun updateSegmentText(segmentId: Long, texto: String)

    @Query("UPDATE segments SET favorita = :favorita WHERE id = :segmentId")
    abstract suspend fun setFavorite(segmentId: Long, favorita: Boolean)
}
