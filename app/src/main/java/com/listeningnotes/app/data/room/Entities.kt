package com.listeningnotes.app.data.room

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.listeningnotes.app.data.model.Segment
import com.listeningnotes.app.data.model.Transcription
import com.listeningnotes.app.data.model.Vocab

@Entity(tableName = "transcriptions")
data class TranscriptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val titulo: String,
    val rutaAudio: String,
    val duracionMs: Long,
    val fecha: Long,
    val modelo: String,
)

@Entity(
    tableName = "segments",
    foreignKeys = [
        ForeignKey(
            entity = TranscriptionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transcriptionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("transcriptionId")],
)
data class SegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transcriptionId: Long,
    val inicioMs: Long,
    val finMs: Long,
    val texto: String,
    val favorita: Boolean = false,
)

fun TranscriptionEntity.toModel() = Transcription(id, titulo, rutaAudio, duracionMs, fecha, modelo)
fun SegmentEntity.toModel() = Segment(id, transcriptionId, inicioMs, finMs, texto, favorita)

/** Palabra guardada en el vocabulario, con la frase donde apareció. */
@Entity(tableName = "vocabulary", indices = [Index(value = ["palabra"], unique = true)])
data class VocabEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val palabra: String,
    val nota: String,
    val contexto: String,
    val transcriptionId: Long,
    val inicioMs: Long,
    val tituloOrigen: String,
    val fecha: Long,
)

fun VocabEntity.toModel() = Vocab(id, palabra, nota, contexto, transcriptionId, inicioMs, tituloOrigen, fecha)
