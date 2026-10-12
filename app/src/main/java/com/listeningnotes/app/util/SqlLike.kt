package com.listeningnotes.app.util

/** Construye el patrón de un `LIKE ... ESCAPE '\\'` con comodines, escapando `%`, `_` y `\\` del texto del usuario. */
fun likePattern(query: String): String {
    val escaped = query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    return "%$escaped%"
}
