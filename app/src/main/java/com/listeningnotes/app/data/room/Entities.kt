package com.listeningnotes.app.data.room

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.listeningnotes.app.data.model.Segment
import com.listeningnotes.app.data.model.Transcription

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
