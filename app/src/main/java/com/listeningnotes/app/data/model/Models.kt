package com.listeningnotes.app.data.model

/** Modelos de dominio que consume la UI (independientes de Room). */
data class Transcription(
    val id: Long,
    val titulo: String,
    val rutaAudio: String,
    val duracionMs: Long,
    val fecha: Long,
    val modelo: String,
)

data class Segment(
    val id: Long,
    val transcriptionId: Long,
    val inicioMs: Long,
    val finMs: Long,
    val texto: String,
    val favorita: Boolean,
)
