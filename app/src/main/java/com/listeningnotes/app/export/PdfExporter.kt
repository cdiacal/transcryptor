package com.listeningnotes.app.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import android.text.TextUtils
import com.listeningnotes.app.util.formatClock
import java.io.OutputStream
import java.text.DateFormat
import java.util.Date
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private typealias Op = (Canvas) -> Unit

enum class PdfMode { CON_TIEMPOS, SOLO_TEXTO }

data class PdfSegment(val startMs: Long, val endMs: Long, val text: String, val favorita: Boolean)

data class PdfData(
    val title: String,
    val durationMs: Long,
    val dateMs: Long,
    val model: String,
    val segments: List<PdfSegment>,
    /** Palabras guardadas del audio: (palabra, traducción o nota). */
    val vocabulary: List<Pair<String, String>>,
)

/**
 * Genera el PDF de una transcripción con `android.graphics.pdf.PdfDocument` (sin dependencias).
 * Diseño: cabecera azul marino con degradado, tarjetas con borde fino y pie con número de página.
 * Se maqueta primero todo (para conocer el total de páginas) y luego se dibuja.
 */
object PdfExporter {
    private const val W = 595 // A4 en puntos
    private const val H = 842
    private const val M = 40f
    private const val BOTTOM = 56f

    private val NAVY1 = Color.parseColor("#152C52")
    private val NAVY2 = Color.parseColor("#1E4B87")
    private val PRIMARY = Color.parseColor("#1E4B87")
    private val PRIMARY_CONTAINER = Color.parseColor("#E3EAF5")
    private val BORDER = Color.parseColor("#E6E9F0")
    private val TEXT = Color.parseColor("#111420")
    private val TEXT2 = Color.parseColor("#767C90")
    private val AMBER = Color.parseColor("#D9932B")

    fun write(out: OutputStream, data: PdfData, mode: PdfMode) {
        val pages = layout(data, mode)
        val doc = PdfDocument()
        try {
            pages.forEachIndexed { index, ops ->
                val page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, index + 1).create())
                ops.forEach { it(page.canvas) }
                drawFooter(page.canvas, index + 1, pages.size)
                doc.finishPage(page)
            }
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }

    // --- Maquetación ---

    private class PdfFlow(private val data: PdfData) {
        val pages = ArrayList<ArrayList<Op>>()
        var y = 0f

        fun add(op: Op) { pages.last().add(op) }

        fun newPage() {
            pages.add(ArrayList())
            if (pages.size == 1) {
                y = drawHero(this, data)
            } else {
                y = slimHeader(this, data.title)
            }
        }

        /** Garantiza [height] puntos libres; si no caben, salta de página. */
        fun ensure(height: Float) {
            if (y + height > H - BOTTOM) newPage()
        }
    }

    private fun layout(data: PdfData, mode: PdfMode): List<List<Op>> {
        val flow = PdfFlow(data)
        flow.newPage()

        sectionTitle(flow, if (mode == PdfMode.CON_TIEMPOS) "Frases" else "Texto completo")
        when (mode) {
            PdfMode.CON_TIEMPOS -> data.segments.filter { it.text.isNotBlank() }.forEach { cardRow(flow, it) }
            PdfMode.SOLO_TEXTO -> plainText(flow, data)
        }
        if (data.vocabulary.isNotEmpty()) vocabulary(flow, data.vocabulary)
        return flow.pages
    }

    private fun paint(color: Int, size: Float, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }

    private fun stroke(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; style = Paint.Style.STROKE; strokeWidth = width
    }

    private fun drawHero(flow: PdfFlow, data: PdfData): Float {
        val heroHeight = 150f
        val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(data.dateMs))
        val favorites = data.segments.count { it.favorita }
        flow.add { c ->
            // El rectángulo sobresale por arriba de la página: solo se ven redondeadas las esquinas inferiores.
            val path = Path().apply { addRoundRect(RectF(0f, -40f, W.toFloat(), heroHeight), 24f, 24f, Path.Direction.CW) }
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, 0f, W.toFloat(), heroHeight, NAVY1, NAVY2, Shader.TileMode.CLAMP)
            }
            c.drawPath(path, bg)

            val label = paint(Color.WHITE, 9f).apply { alpha = 210 }
            c.drawText("LISTENING NOTES · TRANSCRIPCIÓN", M, 34f, label)

            val titlePaint = paint(Color.WHITE, 19f, bold = true)
            val title = TextUtils.ellipsize(data.title, titlePaint, W - 2 * M, TextUtils.TruncateAt.END).toString()
            c.drawText(title, M, 58f, titlePaint)

            val big = paint(Color.WHITE, 32f, bold = true)
            val count = data.segments.size.toString()
            c.drawText(count, M, 96f, big)
            c.drawText("frases", M + big.measureText(count) + 8f, 96f, paint(Color.WHITE, 10f).apply { alpha = 210 })

            var x = M
            listOf(
                "Duración" to formatClock(data.durationMs),
                "Fecha" to date,
                "Modelo" to data.model,
                "Favoritas" to favorites.toString(),
            ).forEach { (name, value) ->
                c.drawText(name, x, 123f, paint(Color.WHITE, 7.5f).apply { alpha = 205 })
                c.drawText(value, x, 138f, paint(Color.WHITE, 11f, bold = true))
                x += 105f
            }
        }
        return heroHeight + 22f
    }

    private fun slimHeader(flow: PdfFlow, title: String): Float {
        flow.add { c ->
            c.drawRoundRect(RectF(M, 24f, W - M, 52f), 10f, 10f, fill(NAVY1))
            val p = paint(Color.WHITE, 10f, bold = true)
            c.drawText(TextUtils.ellipsize(title, p, W - 2 * M - 28f, TextUtils.TruncateAt.END).toString(), M + 14f, 42f, p)
        }
        return 52f + 20f
    }

    private fun sectionTitle(flow: PdfFlow, text: String) {
        flow.ensure(30f)
        val y = flow.y
        flow.add { c -> c.drawText(text, M, y + 4f, paint(TEXT, 11f, bold = true)) }
        flow.y += 22f
    }

    private fun cardRow(flow: PdfFlow, seg: PdfSegment) {
        val body = paint(TEXT, 10.5f)
        val lines = wrapText(seg.text, W - 2 * M - 112f) { body.measureText(it) }
        val height = maxOf(34f, 16f + 14f * lines.size)
        flow.ensure(height + 7f)
        val top = flow.y
        flow.add { c ->
            val card = RectF(M, top, W - M, top + height)
            c.drawRoundRect(card, 10f, 10f, fill(Color.WHITE))
            c.drawRoundRect(card, 10f, 10f, stroke(BORDER, 0.8f))
            // Píldora con el tiempo
            c.drawRoundRect(RectF(M + 10f, top + 7f, M + 54f, top + 24f), 8.5f, 8.5f, fill(PRIMARY_CONTAINER))
            val tp = paint(PRIMARY, 8.5f, bold = true).apply { textAlign = Paint.Align.CENTER }
            c.drawText("[${formatClock(seg.startMs)}]", M + 32f, top + 19f, tp)
            lines.forEachIndexed { i, line -> c.drawText(line, M + 66f, top + 20f + 14f * i, body) }
            if (seg.favorita) drawStar(c, W - M - 20f, top + 17f, 6f)
        }
        flow.y += height + 7f
    }

    /** Texto corrido, sin tiempos, en párrafos. */
    private fun plainText(flow: PdfFlow, data: PdfData) {
        val body = paint(TEXT, 11f)
        val lineHeight = 17f
        val paragraphs = buildParagraphs(data.segments.map { ExportSegment(it.startMs, it.endMs, it.text) })
        for (paragraph in paragraphs) {
            for (line in wrapText(paragraph, W - 2 * M) { body.measureText(it) }) {
                flow.ensure(lineHeight)
                val baseline = flow.y + 12f
                flow.add { c -> c.drawText(line, M, baseline, body) }
                flow.y += lineHeight
            }
            flow.y += 10f
        }
    }

    private fun vocabulary(flow: PdfFlow, words: List<Pair<String, String>>) {
        flow.y += 10f
        sectionTitle(flow, "Vocabulario guardado")
        val rowHeight = 22f
        var index = 0
        while (index < words.size) {
            flow.ensure(12f + rowHeight)
            val available = ((H - BOTTOM - flow.y - 12f) / rowHeight).toInt().coerceAtLeast(1)
            val chunk = words.subList(index, minOf(words.size, index + available))
            val top = flow.y
            val height = 12f + rowHeight * chunk.size
            flow.add { c ->
                val card = RectF(M, top, W - M, top + height)
                c.drawRoundRect(card, 10f, 10f, fill(Color.WHITE))
                c.drawRoundRect(card, 10f, 10f, stroke(BORDER, 0.8f))
                chunk.forEachIndexed { i, (word, note) ->
                    val baseline = top + 22f + rowHeight * i
                    c.drawText(word, M + 14f, baseline, paint(TEXT, 10.5f, bold = true))
                    val notePaint = paint(TEXT2, 10.5f)
                    val shown = TextUtils.ellipsize(note.ifBlank { "—" }, notePaint, W - 2 * M - 164f, TextUtils.TruncateAt.END)
                    c.drawText(shown.toString(), M + 150f, baseline, notePaint)
                    if (i < chunk.lastIndex) c.drawLine(M + 14f, baseline + 9f, W - M - 14f, baseline + 9f, stroke(BORDER, 0.6f))
                }
            }
            flow.y += height + 7f
            index += chunk.size
        }
    }

    private fun drawStar(c: Canvas, cx: Float, cy: Float, r: Float) {
        val path = Path()
        for (i in 0 until 10) {
            val angle = PI / 2 + i * PI / 5
            val radius = if (i % 2 == 0) r else r * 0.45f
            val px = (cx + radius * cos(angle)).toFloat()
            val py = (cy - radius * sin(angle)).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        c.drawPath(path, fill(AMBER))
    }

    private fun drawFooter(c: Canvas, page: Int, total: Int) {
        c.drawLine(M, H - 38f, W - M, H - 38f, stroke(BORDER, 0.6f))
        val p = paint(TEXT2, 8f)
        c.drawText("Generado con Listening Notes", M, H - 24f, p)
        p.textAlign = Paint.Align.RIGHT
        c.drawText("Página $page de $total", W - M, H - 24f, p)
    }
}
