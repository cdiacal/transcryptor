package com.listeningnotes.app.util

/** Formatea milisegundos como "mm:ss" (o "h:mm:ss" si dura una hora o más). */
fun formatClock(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0)) / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
