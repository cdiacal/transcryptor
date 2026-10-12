package com.listeningnotes.app.analysis

import kotlin.math.ln
import kotlin.math.min

/** Palabra clave de un audio. [segmentIndex] = primera frase donde aparece (para el ejemplo). */
data class Keyword(val word: String, val count: Int, val score: Double, val segmentIndex: Int)

/**
 * Extrae el vocabulario característico de un texto en inglés sin diccionario ni conexión:
 * descarta palabras funcionales y básicas, agrupa formas de una misma palabra (travel / travelling /
 * travelled), ignora nombres propios y puntúa por frecuencia y "rareza" (longitud).
 */
object KeywordExtractor {

    fun extract(texts: List<String>, limit: Int = 40): List<Keyword> {
        class Acc(var count: Int = 0, var capitalizedMid: Int = 0, var first: Int = Int.MAX_VALUE) {
            val forms = HashMap<String, Int>()
        }

        val byKey = HashMap<String, Acc>()
        texts.forEachIndexed { index, text ->
            for (sentence in text.split(Regex("(?<=[.!?])\\s+"))) {
                var position = 0
                for (raw in tokens(sentence)) {
                    val word = raw.lowercase()
                    val atStart = position == 0
                    position++
                    if (word.length < MIN_LENGTH || word in IGNORED || word.any { it.isDigit() }) continue
                    val key = stemKey(word)
                    if (key in IGNORED_KEYS) continue
                    val acc = byKey.getOrPut(key) { Acc() }
                    acc.count++
                    acc.forms.merge(word, 1, Int::plus)
                    if (index < acc.first) acc.first = index
                    if (!atStart && raw[0].isUpperCase()) acc.capitalizedMid++
                }
            }
        }

        return byKey.values
            .filter { it.capitalizedMid.toDouble() / it.count < 0.6 } // nombres propios
            .map { acc ->
                val display = acc.forms.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key.length }).first().key
                val rarity = 0.6 + min(display.length, 10) / 10.0
                Keyword(display, acc.count, (1 + ln(acc.count.toDouble())) * rarity, acc.first)
            }
            .sortedWith(compareByDescending<Keyword> { it.score }.thenBy { it.word })
            .take(limit)
    }

    /** Palabras que resumen el tema: prioriza las repetidas. */
    fun topic(keywords: List<Keyword>, size: Int = 4): List<String> {
        val repeated = keywords.filter { it.count >= 2 }
        return (if (repeated.size >= 2) repeated else keywords).take(size).map { it.word }
    }

    /** Palabras del texto, sin posesivos ni contracciones ("don't" -> "don", "John's" -> "John"). */
    private fun tokens(sentence: String): List<String> =
        Regex("[A-Za-z]+(?:['’][A-Za-z]+)*").findAll(sentence).map { m ->
            val t = m.value.replace('’', '\'')
            val apostrophe = t.indexOf('\'')
            if (apostrophe > 0 && t.substring(apostrophe + 1).lowercase() in CONTRACTION_SUFFIXES) t.substring(0, apostrophe) else t
        }.toList()

    /** Clave de agrupación aproximada (sin lematizador): quita sufijos y normaliza e/y/consonante doble. */
    internal fun stemKey(word: String): String {
        var w = word
        when {
            w.length > 4 && w.endsWith("ies") -> w = w.dropLast(3) + "y"
            w.length > 5 && w.endsWith("ing") -> w = w.dropLast(3)
            w.length > 4 && w.endsWith("ed") -> w = w.dropLast(2)
            w.length > 4 && w.endsWith("es") && (w.dropLast(2).let { it.endsWith("s") || it.endsWith("x") || it.endsWith("z") || it.endsWith("ch") || it.endsWith("sh") || it.endsWith("o") }) -> w = w.dropLast(2)
            w.length > 3 && w.endsWith("s") && !w.endsWith("ss") && !w.endsWith("us") && !w.endsWith("is") -> w = w.dropLast(1)
            w.length > 5 && w.endsWith("ly") -> w = w.dropLast(2)
        }
        if (w.endsWith("e") && w.length > 3) w = w.dropLast(1)
        if (w.endsWith("y") && w.length > 3) w = w.dropLast(1) + "i"
        if (w.length > 3 && w[w.length - 1] == w[w.length - 2] && w.last() !in "aeiouls") w = w.dropLast(1)
        if (w.length > 3 && w.endsWith("ll")) w = w.dropLast(1)
        return w
    }

    private const val MIN_LENGTH = 4
    private val CONTRACTION_SUFFIXES = setOf("s", "t", "re", "ve", "ll", "d", "m")

    private val STOP = """
        about above after again against almost also although always another around because been before being below between
        both could does doing down during each either else enough even ever every from further gets getting give given goes
        going gone gonna gotta have having here hers herself himself however into itself just kind let like many maybe might more most
        much must myself neither never none once only other ours ourselves over perhaps quite rather really right said same shall
        should since some somebody someone something sometimes still such than that their theirs them themselves then there these they
        thing things this those though through thus together under until upon very want wanna well were what whatever when where whether which
        while whom whose will with within without would yeah yes okay oh hmm today tomorrow yesterday anyway actually basically
        completely definitely exactly honestly probably simply totally literally already else anything everything nothing
        everyone anyone nobody everybody whatever whenever wherever
    """.trimIndent().split(Regex("\\s+")).toSet()

    /** Vocabulario básico (verbos, nombres y adjetivos muy frecuentes) que no merece destacarse. */
    private val COMMON = """
        able back bad best better big bring call came change child children city come company country course day days different
        door down early easy end enough eye eyes face fact family father feel felt find first follow found friend friends full game
        gave girl give good great group hand hands happen happened hard head hear heard help high home hour house idea important
        include job keep kept knew know known large last later leave left life little live long look looked lost love made make
        makes making man mean meant meet member men mind minute money month mother move much name near need needed never next night
        number often old only open order over own part pass past people person place play point power problem program public
        put question quite read ready real reason remember right room run running said saw say says school seem seemed seen sent
        set several show side small speak spend stand start started state stay still stop story street student study sure take taken
        talk tell thank thanks thought three time times told took town tried true try turn turned two understand use used usually
        wait walk watch water week went while whole woman women word words work world write year years young
        after also back been called came could first from have into last made many most name next only other over some such than
        then they well were when with work year your about again being both down each even good just know like long make more
        most much must over said same than that them then there these they this time very want what when which will would
        better bring clear close deal doing early either enjoy fine fun glad guess hope kind late mean nice own plan point seem
        sort sound sorry start sure taking thing think trying wonderful worth wrong
    """.trimIndent().split(Regex("\\s+")).toSet()

    private val IGNORED: Set<String> = STOP + COMMON
    private val IGNORED_KEYS: Set<String> by lazy { IGNORED.map { stemKey(it) }.toSet() }
}
