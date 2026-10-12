package com.listeningnotes.app.export

/** Utilidades de maquetación sin dependencias de Android (probadas con tests unitarios). */

/**
 * Ajuste de línea voraz por palabras. [measure] devuelve el ancho de un texto.
 * Las palabras más anchas que [maxWidth] se parten por caracteres.
 */
fun wrapText(text: String, maxWidth: Float, measure: (String) -> Float): List<String> {
    val words = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return emptyList()
    val lines = ArrayList<String>()
    var current = ""
    for (word in words) {
        var w = word
        // Palabra gigante: se trocea.
        while (measure(w) > maxWidth && w.length > 1) {
            if (current.isNotEmpty()) { lines.add(current); current = "" }
            var cut = w.length - 1
            while (cut > 1 && measure(w.substring(0, cut)) > maxWidth) cut--
            lines.add(w.substring(0, cut))
            w = w.substring(cut)
        }
        val candidate = if (current.isEmpty()) w else "$current $w"
        if (current.isEmpty() || measure(candidate) <= maxWidth) {
            current = candidate
        } else {
            lines.add(current)
            current = w
        }
    }
    if (current.isNotEmpty()) lines.add(current)
    return lines
}

/**
 * Agrupa las frases en párrafos para el PDF «solo texto»: un párrafo nuevo tras un silencio largo
 * ([gapMs] entre el fin de una frase y el inicio de la siguiente) o al llegar a [maxSegments] frases.
 */
fun buildParagraphs(segments: List<ExportSegment>, gapMs: Long = 1_500L, maxSegments: Int = 6): List<String> {
    val paragraphs = ArrayList<String>()
    val current = StringBuilder()
    var count = 0
    var lastEnd = Long.MIN_VALUE
    for (seg in segments) {
        val text = seg.text.replace(Regex("\\s+"), " ").trim()
        if (text.isEmpty()) continue
        val newParagraph = count >= maxSegments || (lastEnd != Long.MIN_VALUE && seg.startMs - lastEnd > gapMs)
        if (newParagraph && current.isNotEmpty()) {
            paragraphs.add(current.toString())
            current.setLength(0)
            count = 0
        }
        if (current.isNotEmpty()) current.append(' ')
        current.append(text)
        count++
        lastEnd = seg.endMs
    }
    if (current.isNotEmpty()) paragraphs.add(current.toString())
    return paragraphs
}
