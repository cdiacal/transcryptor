package com.listeningnotes.app.export

import com.listeningnotes.app.util.formatClock

/** Segmento mínimo para exportar (sin dependencias de Room ni Android). */
data class ExportSegment(val startMs: Long, val endMs: Long, val text: String)

/** Conversión de segmentos a texto exportable (.txt / portapapeles). */
object SegmentExporter {

    /**
     * Una línea por frase: `[mm:ss] texto`. Si hay [title], se antepone seguido de una línea en blanco.
     * Los saltos de línea internos se aplanan y las frases vacías se omiten.
     */
    fun toPlainText(title: String?, segments: List<ExportSegment>, includeTimestamps: Boolean = true): String {
        val lines = segments.mapNotNull { seg ->
            val text = seg.text.replace(Regex("\\s+"), " ").trim()
            if (text.isEmpty()) null
            else if (includeTimestamps) "[${formatClock(seg.startMs)}] $text" else text
        }
        val body = lines.joinToString("\n")
        val header = title?.trim()?.takeIf { it.isNotEmpty() }
        return buildString {
            if (header != null) {
                append(header).append("\n\n")
            }
            append(body)
            if (body.isNotEmpty()) append('\n')
        }
    }

    /** Nombre de fichero seguro para el título (por defecto `.txt`). */
    fun suggestedFileName(title: String, extension: String = "txt"): String {
        val cleaned = title.replace(Regex("[\\\\/:*?\"<>|\\r\\n\\t]"), "_").trim().take(80)
        return (cleaned.ifEmpty { "transcripcion" }) + "." + extension
    }
}
