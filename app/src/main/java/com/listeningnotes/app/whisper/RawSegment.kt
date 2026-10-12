package com.listeningnotes.app.whisper

/** Segmento crudo devuelto por whisper (tiempos en ms). */
data class RawSegment(val startMs: Long, val endMs: Long, val text: String)
