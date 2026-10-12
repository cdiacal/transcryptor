package com.listeningnotes.app.whisper

/**
 * Divide los segmentos de whisper (a veces varias frases juntas) en frases más cortas.
 * whisper no da tiempos por frase, así que el intervalo del segmento se reparte entre las frases
 * proporcionalmente a su longitud en caracteres (aproximación suficiente para estudiar).
 */
object SentenceSplitter {

    private val abbreviations = setOf(
        "mr", "mrs", "ms", "dr", "prof", "sr", "jr", "st", "vs", "etc", "inc", "ltd", "co",
        "no", "mt", "gen", "sen", "rep", "e.g", "i.e", "u.s", "u.k", "a.m", "p.m",
    )
    private const val CLOSERS = "\"'”’)]"
    private const val TERMINATORS = ".!?…"

    fun split(segment: RawSegment, maxChars: Int = 140, minChars: Int = 12): List<RawSegment> {
        val text = segment.text.trim()
        var pieces = sentences(text).flatMap { splitLong(it, maxChars, minChars) }
        pieces = mergeShort(pieces, minChars)
        if (pieces.size <= 1) return listOf(segment.copy(text = text))
        return distribute(segment.startMs, segment.endMs, pieces)
    }

    /** Parte por puntuación final (. ! ? …) evitando abreviaturas, iniciales y decimales. */
    internal fun sentences(text: String): List<String> {
        val result = ArrayList<String>()
        var start = 0
        var i = 0
        while (i < text.length) {
            if (text[i] in TERMINATORS) {
                var end = i + 1
                while (end < text.length && (text[end] in TERMINATORS || text[end] in CLOSERS)) end++
                if (end < text.length && text[end].isWhitespace()) {
                    var next = end
                    while (next < text.length && text[next].isWhitespace()) next++
                    val boundary = next < text.length && !(text[i] == '.' && (isAbbreviation(text, start, i) || text[next].isLowerCase()))
                    if (boundary) {
                        result.add(text.substring(start, end).trim())
                        start = next
                    }
                }
                i = end
            } else {
                i++
            }
        }
        if (start < text.length) result.add(text.substring(start).trim())
        return result.filter { it.isNotEmpty() }
    }

    private fun isAbbreviation(text: String, sentenceStart: Int, dotIndex: Int): Boolean {
        var s = dotIndex
        while (s > sentenceStart && !text[s - 1].isWhitespace()) s--
        val word = text.substring(s, dotIndex).trim('"', '\'', '(', '“', '‘').lowercase()
        // Inicial suelta ("J. Smith") o abreviatura conocida.
        return (word.length == 1 && word[0].isLetter()) || word in abbreviations
    }

    /** Las frases muy largas se parten en la coma/punto y coma/dos puntos más cercana al centro. */
    private fun splitLong(sentence: String, maxChars: Int, minChars: Int): List<String> {
        if (sentence.length <= maxChars) return listOf(sentence)
        val mid = sentence.length / 2
        var best = -1
        var bestDistance = Int.MAX_VALUE
        for (i in sentence.indices) {
            if (sentence[i] in ",;:" && i + 1 < sentence.length && sentence[i + 1] == ' ') {
                val left = i + 1
                val right = sentence.length - left
                if (left >= minChars && right >= minChars && kotlin.math.abs(i - mid) < bestDistance) {
                    best = i
                    bestDistance = kotlin.math.abs(i - mid)
                }
            }
        }
        if (best < 0) return listOf(sentence)
        val left = sentence.substring(0, best + 1).trim()
        val right = sentence.substring(best + 1).trim()
        return splitLong(left, maxChars, minChars) + splitLong(right, maxChars, minChars)
    }

    /** Une los trozos demasiado cortos ("Yes.", "Okay.") con el anterior (o el siguiente si son los primeros). */
    private fun mergeShort(pieces: List<String>, minChars: Int): List<String> {
        val out = ArrayList<String>()
        var carry = ""
        for (p in pieces) {
            val current = if (carry.isEmpty()) p else "$carry $p"
            carry = ""
            if (current.length < minChars && out.isEmpty()) {
                carry = current
            } else if (current.length < minChars) {
                out[out.size - 1] = out.last() + " " + current
            } else {
                out.add(current)
            }
        }
        if (carry.isNotEmpty()) {
            if (out.isEmpty()) out.add(carry) else out[out.size - 1] = out.last() + " " + carry
        }
        return out
    }

    private fun distribute(startMs: Long, endMs: Long, pieces: List<String>): List<RawSegment> {
        val total = (endMs - startMs).coerceAtLeast(0)
        val weights = pieces.map { it.length + 1 }
        val sum = weights.sum().toDouble()
        var acc = 0
        var previousEnd = startMs
        return pieces.mapIndexed { index, text ->
            acc += weights[index]
            val end = if (index == pieces.lastIndex) endMs else startMs + (total * (acc / sum)).toLong()
            RawSegment(previousEnd, end, text).also { previousEnd = end }
        }
    }
}
