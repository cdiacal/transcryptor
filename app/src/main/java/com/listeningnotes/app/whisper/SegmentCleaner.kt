package com.listeningnotes.app.whisper

/** Limpieza del texto de whisper: espacios y marcadores sin contenido. */
object SegmentCleaner {
    private val noise = Regex("""^\[(BLANK_AUDIO|SILENCE|silence)]$""")

    /** Devuelve null si el segmento no aporta texto. */
    fun clean(raw: String): String? {
        val text = raw.replace(Regex("\\s+"), " ").trim()
        if (text.isEmpty() || noise.matches(text)) return null
        return text
    }
}
