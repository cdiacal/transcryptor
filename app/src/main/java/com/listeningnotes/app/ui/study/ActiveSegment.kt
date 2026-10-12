package com.listeningnotes.app.ui.study

import com.listeningnotes.app.data.model.Segment

/**
 * Devuelve el id de la frase que suena en [positionMs]: la última cuyo inicio ya pasó, siempre que la posición
 * no se aleje más de [gapToleranceMs] de su final (así entre frases separadas por silencio no queda iluminada
 * la anterior para siempre). Null si ninguna encaja.
 */
fun findActiveSegmentId(segments: List<Segment>, positionMs: Long, gapToleranceMs: Long = 400L): Long? {
    if (segments.isEmpty() || positionMs < 0) return null
    // Búsqueda binaria de la última frase con inicioMs <= posición (la lista está ordenada por inicio).
    var lo = 0
    var hi = segments.size - 1
    var found = -1
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        if (segments[mid].inicioMs <= positionMs) {
            found = mid
            lo = mid + 1
        } else {
            hi = mid - 1
        }
    }
    if (found < 0) return null
    val seg = segments[found]
    return if (positionMs <= seg.finMs + gapToleranceMs) seg.id else null
}
