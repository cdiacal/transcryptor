package com.listeningnotes.app.util

/** Palabras de una frase tal y como se muestran (conservan la puntuación pegada). */
fun splitTokens(sentence: String): List<String> =
    sentence.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

/** Clave de vocabulario: minúsculas, sin puntuación en los extremos y con apóstrofo normalizado. */
fun normalizeWord(token: String): String =
    token.replace('’', '\'').trim { !it.isLetterOrDigit() }.lowercase()
