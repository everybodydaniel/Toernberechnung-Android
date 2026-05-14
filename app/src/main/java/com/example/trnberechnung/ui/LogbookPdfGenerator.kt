package com.example.trnberechnung.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.widget.Toast
import com.example.trnberechnung.model.BoatProfileRepository
import com.example.trnberechnung.model.LogbookEntry
import java.io.File
import java.io.FileOutputStream

/**
 * Generates a PDF that visually matches the real paper-based
 * "Schiffstagebuch (Törnverlauf)" nautical logbook form.
 */
object LogbookPdfGenerator {

    // A4 in points (72 dpi)
    private const val PW = 595
    private const val PH = 842
    private const val M = 32f  // page margin
    private val CW get() = PW - 2 * M  // content width

    // ── Paint factory helpers ──────────────────────────
    private fun titlePaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 15f; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    }
    private fun headerPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 9f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private fun labelPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 7f; color = android.graphics.Color.DKGRAY
    }
    private fun valuePaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 8.5f
    }
    private fun smallPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 6.5f; color = android.graphics.Color.DKGRAY
    }
    private fun borderPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 0.7f
        color = android.graphics.Color.BLACK
    }
    private fun thinBorderPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 0.4f
        color = android.graphics.Color.DKGRAY
    }
    private fun checkPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 0.5f
        color = android.graphics.Color.BLACK
    }

    // ── Main entry point ──────────────────────────────
    fun generate(context: Context, log: LogbookEntry) {
        try {
            val bp = BoatProfileRepository(context)
            val details = LogbookDetails.parse(log.details)
            val doc = PdfDocument()
            val page = doc.startPage(PdfDocument.PageInfo.Builder(PW, PH, 1).create())
            val c = page.canvas

            var y = M
            y = drawTitle(c, y, log)
            y = drawTripInfoBlock(c, y, log, details, bp)
            y = drawChecklist(c, y, details)
            y = drawWaypointTable(c, y, log, details)
            y = drawEvents(c, y, details)
            y = drawFooter(c, y)

            doc.finishPage(page)
            saveAndNotify(context, doc, log)
        } catch (e: Exception) {
            Toast.makeText(context, "PDF-Export fehlgeschlagen: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // ══════════════════════════════════════════════════
    //  TITLE
    // ══════════════════════════════════════════════════
    private fun drawTitle(c: Canvas, startY: Float, log: LogbookEntry): Float {
        var y = startY + 4f
        val tp = titlePaint()
        // Date box top-right, above the title line
        val vp = valuePaint()
        val boxW = 130f; val boxH = 24f
        val boxX = M + CW - boxW
        c.drawRect(boxX, y, boxX + boxW, y + boxH, borderPaint())
        c.drawText("Datum:", boxX + 4f, y + 9f, labelPaint())
        c.drawText(log.date, boxX + 4f, y + 20f, vp)

        y += boxH + 4f
        c.drawText("Schiffstagebuch (T\u00F6rnverlauf)", M, y, tp)
        y += 6f
        return y
    }

    // ══════════════════════════════════════════════════
    //  TRIP INFO (4 rows of bordered cells)
    // ══════════════════════════════════════════════════
    private fun drawTripInfoBlock(
        c: Canvas, startY: Float, log: LogbookEntry,
        d: LogbookDetails, bp: BoatProfileRepository
    ): Float {
        var y = startY
        val rh = 28f  // row height
        val lp = labelPaint(); val vp = valuePaint()
        val brd = borderPaint()

        val routeParts = log.routeDesc.split("→", "->", "➔", "nach").map { it.trim() }
        val from = routeParts.firstOrNull() ?: "–"
        val to = routeParts.getOrNull(1) ?: "–"
        val crew = d.crew.ifBlank { "–" }
        val skipper = crew.split(",").firstOrNull()?.trim()?.split("(")?.firstOrNull()?.trim() ?: "–"
        val wx = d.wetter.ifBlank { "–" }
        val tide = d.gezeiten.ifBlank { "–" }
        val wt = d.wt.ifBlank { "–" }
        val ukc = d.ukc.ifBlank { "–" }
        val aufbHoehe = if (d.aufbauhoeheActive && d.aufbauhoehe.isNotBlank()) d.aufbauhoehe else "–"
        val bsA = d.bsAbfahrt.ifBlank { "–" }
        val bsB = d.bsAnkunft.ifBlank { "–" }

        // Row 1: Törn von/nach | Crew | Zeitzone
        val r1c1 = CW * 0.38f; val r1c2 = CW * 0.30f; val r1c3 = CW * 0.32f
        drawCell(c, M, y, r1c1, rh, "T\u00F6rn von/nach:", "$from \u2192 $to", lp, vp, brd)
        drawCell(c, M + r1c1, y, r1c2, rh, "Crew:", crew.take(30), lp, vp, brd)
        // Zeitzone: two-line label
        val tzX = M + r1c1 + r1c2
        c.drawRect(tzX, y, tzX + r1c3, y + rh, brd)
        c.drawText("Zeitzone:", tzX + 3f, y + 9f, lp)
        c.drawText("UTC + 1 Std.", tzX + 3f, y + 21f, vp)
        y += rh

        // Row 2: Schiffsführer | Seekarten | Betriebsstd.
        val r2c1 = CW * 0.38f; val r2c2 = CW * 0.30f; val r2c3 = CW * 0.32f
        drawCell(c, M, y, r2c1, rh, "Schiffsf\u00FChrer:", skipper, lp, vp, brd)
        drawCell(c, M + r2c1, y, r2c2, rh, "verwendete Seekarten:", "\u2013", lp, vp, brd)
        // Betriebsstd: two-line label
        val bsX = M + r2c1 + r2c2
        c.drawRect(bsX, y, bsX + r2c3, y + rh, brd)
        c.drawText("Betriebsstd. bei Abfahrt:", bsX + 3f, y + 9f, lp)
        c.drawText(bsA, bsX + r2c3 - 32f, y + 9f, vp)
        c.drawText("bei Ankunft:", bsX + 3f, y + 21f, lp)
        c.drawText(bsB, bsX + r2c3 - 32f, y + 21f, vp)
        y += rh

        // Row 3: Wetter/Wind | Tiefgang | Aufbauhöhe — wider weather cell
        val r3c1 = CW * 0.38f; val r3c2 = CW * 0.30f; val r3c3 = CW * 0.32f
        drawCell(c, M, y, r3c1, rh, "Wetter / Wind:", wx.take(45), lp, vp, brd)
        val tiefgang = "%.2f".format(bp.draft)
        drawCell(c, M + r3c1, y, r3c2, rh, "Tiefgang d. Bootes (m):", tiefgang, lp, vp, brd)
        drawCell(c, M + r3c1 + r3c2, y, r3c3, rh, "Aufbauh\u00F6he (m):", aufbHoehe, lp, vp, brd)
        y += rh

        // Row 4: Wasserst.-Vorhers. | Tiefgang | (empty)
        val r4c1 = CW * 0.38f; val r4c2 = CW * 0.30f; val r4c3 = CW * 0.32f
        drawCell(c, M, y, r4c1, rh, "Wasserst.-Vorhers. (+/- m):", wt, lp, vp, brd)
        drawCell(c, M + r4c1, y, r4c2, rh, "Gezeiten:", tide.take(35), lp, vp, brd)
        drawCell(c, M + r4c1 + r4c2, y, r4c3, rh, "UKC:", ukc, lp, vp, brd)
        y += rh

        return y + 6f
    }

    // ══════════════════════════════════════════════════
    //  CHECKLIST
    // ══════════════════════════════════════════════════
    private fun drawChecklist(c: Canvas, startY: Float, d: LogbookDetails): Float {
        var y = startY
        val hp = headerPaint()
        val sp = smallPaint()
        val chk = checkPaint()
        val brd = borderPaint()

        c.drawText("✓  Checkliste vor Abfahrt:", M, y + 9f, hp)
        y += 14f

        // Linke Spalte: CHECKLIST_CREW (Index 0..5)
        // Mittlere Spalte: CHECKLIST_TECH (Index 6..11)
        // Rechte Spalte: CHECKLIST_NAV (Index 12..14)
        val leftItems = CHECKLIST_CREW
        val rightItems = CHECKLIST_TECH
        val extraItems = CHECKLIST_NAV

        val rowH = 12f
        val col1X = M; val col2X = M + CW * 0.35f; val col3X = M + CW * 0.68f
        val boxS = 7f

        val checkH = leftItems.size * rowH + 4f
        c.drawRect(M, y - 2f, M + CW, y + checkH, brd)
        c.drawLine(col2X - 6f, y - 2f, col2X - 6f, y + checkH, thinBorderPaint())
        c.drawLine(col3X - 6f, y - 2f, col3X - 6f, y + checkH, thinBorderPaint())

        fun drawBox(x: Float, yTop: Float, checked: Boolean) {
            c.drawRect(x, yTop, x + boxS, yTop + boxS, chk)
            if (checked) {
                // Häkchen als kleines V
                c.drawLine(x + 1f, yTop + boxS * 0.55f,
                           x + boxS * 0.4f, yTop + boxS - 1f, chk)
                c.drawLine(x + boxS * 0.4f, yTop + boxS - 1f,
                           x + boxS - 0.5f, yTop + 1f, chk)
            }
        }

        for (i in leftItems.indices) {
            val cy = y + i * rowH
            drawBox(col1X + 4f, cy + 1f, d.checklist.getOrNull(i) == true)
            c.drawText(leftItems[i], col1X + 16f, cy + 7.5f, sp)
            if (i < rightItems.size) {
                drawBox(col2X, cy + 1f, d.checklist.getOrNull(i + 6) == true)
                c.drawText(rightItems[i], col2X + 12f, cy + 7.5f, sp)
            }
            if (i < extraItems.size) {
                drawBox(col3X, cy + 1f, d.checklist.getOrNull(i + 12) == true)
                c.drawText(extraItems[i], col3X + 12f, cy + 7.5f, sp)
            }
        }

        return y + checkH + 8f
    }

    // ══════════════════════════════════════════════════
    //  WAYPOINT TABLE  (Törnverlauf)
    // ══════════════════════════════════════════════════
    private fun drawWaypointTable(
        c: Canvas, startY: Float, log: LogbookEntry, d: LogbookDetails
    ): Float {
        var y = startY
        val hp = headerPaint()
        val lp = labelPaint()
        val vp = valuePaint()
        val sp = smallPaint()
        val brd = borderPaint()
        val tbrd = thinBorderPaint()

        val routeParts = log.routeDesc.split("\u2192", "->", "\u2794", "nach").map { it.trim() }
        val from = routeParts.firstOrNull() ?: "\u2013"
        val to = routeParts.getOrNull(1) ?: "\u2013"
        val depTime = d.abfahrt.takeLast(5).ifBlank { "\u2013" }
        val arrTime = d.ankunft.takeLast(5).ifBlank { "\u2013" }

        // Column positions (absolute from M) matching Excel columns B..N
        // B=WP, C=Nr, D=WuK, E=UKW, F=Entf, G=Kurs, H=Boot, I=Wind, J=Strom, K=Geschw, L=Uhrz, M=Fz.Ber, N=Fz.Echt
        val colX = floatArrayOf(
            0f,           // 0: WP start
            CW * 0.20f,   // 1: Nr
            CW * 0.26f,   // 2: WuK
            CW * 0.32f,   // 3: UKW
            CW * 0.38f,   // 4: Entf (sm) — right side starts
            CW * 0.46f,   // 5: Kurs
            CW * 0.52f,   // 6: Boot (Richtungspfeil)
            CW * 0.60f,   // 7: Wind
            CW * 0.68f,   // 8: Strom
            CW * 0.74f,   // 9: Geschw (kn)
            CW * 0.82f,   // 10: Uhrzeit am WP
            CW * 0.90f,   // 11: Fahrzeit Berechnet
            CW * 0.95f,   // 12: Fahrzeit Echtzeit
            CW            // 13: end
        )
        val rightStart = 4  // index where Törnverlauf columns begin

        // ── Header row 1: left labels + "Törnverlauf" title ──
        val hdr1H = 12f
        // Left header block
        c.drawRect(M, y, M + colX[rightStart], y + hdr1H, brd)
        // Right header block with "Törnverlauf" centered
        c.drawRect(M + colX[rightStart], y, M + CW, y + hdr1H, brd)
        val tvTitle = "T\u00F6rnverlauf"
        val tvW = hp.measureText(tvTitle)
        val rightMid = (colX[rightStart] + CW) / 2f
        c.drawText(tvTitle, M + rightMid - tvW / 2f, y + 9f, hp)
        y += hdr1H

        // ── Header row 2: column names ──
        val hdr2H = 12f
        c.drawRect(M, y, M + CW, y + hdr2H, brd)
        c.drawText("Wegepunkte (WP)", M + 3f, y + 9f, lp)
        c.drawText("Nr.", M + colX[1] + 2f, y + 9f, lp)
        c.drawText("WuK", M + colX[2] + 2f, y + 9f, lp)
        c.drawText("UKW", M + colX[3] + 2f, y + 9f, lp)
        c.drawText("Entf. (sm)", M + colX[4] + 2f, y + 9f, lp)
        c.drawText("Kurs", M + colX[5] + 2f, y + 9f, lp)
        c.drawText("Richtungspfeil", M + colX[6] + 2f, y + 9f, lp)
        c.drawText("Geschw.(kn)", M + colX[9] + 1f, y + 9f, lp)
        c.drawText("Uhrzeit am WP", M + colX[10] + 1f, y + 9f, lp)
        c.drawText("Fahrzeit", M + colX[11] + 1f, y + 9f, lp)
        // Vertical lines for all columns
        for (i in 1 until colX.size - 1) {
            c.drawLine(M + colX[i], y, M + colX[i], y + hdr2H, tbrd)
        }
        // Thick divider at right-side start
        c.drawLine(M + colX[rightStart], y, M + colX[rightStart], y + hdr2H, brd)
        y += hdr2H

        // ── Header row 3: sub-labels (Boot/Wind/Strom, Berechnet/Echtzeit) ──
        val hdr3H = 10f
        c.drawRect(M, y, M + CW, y + hdr3H, brd)
        c.drawText("Boot", M + colX[6] + 2f, y + 8f, sp)
        c.drawText("Wind", M + colX[7] + 2f, y + 8f, sp)
        c.drawText("Strom", M + colX[8] + 2f, y + 8f, sp)
        c.drawText("Berechnet", M + colX[11] + 1f, y + 8f, sp)
        c.drawText("Echtzeit", M + colX[12] + 1f, y + 8f, sp)
        for (i in 1 until colX.size - 1) {
            c.drawLine(M + colX[i], y, M + colX[i], y + hdr3H, tbrd)
        }
        c.drawLine(M + colX[rightStart], y, M + colX[rightStart], y + hdr3H, brd)
        y += hdr3H

        // ── Data rows ──
        val rowH = 22f
        val totalRows = 5
        val wpLabels = listOf("Starthafen:", "nach:", "nach:", "nach:", "nach:")
        // Directional arrows for Richtungspfeil (Boot column)
        val arrows = listOf("\u2191", "\u2193", "\u2190", "\u2192", "\u2197") // N S W E NE

        for (r in 0 until totalRows) {
            val ry = y + r * rowH
            c.drawRect(M, ry, M + CW, ry + rowH, brd)
            // All vertical lines
            for (i in 1 until colX.size - 1) {
                c.drawLine(M + colX[i], ry, M + colX[i], ry + rowH, tbrd)
            }
            c.drawLine(M + colX[rightStart], ry, M + colX[rightStart], ry + rowH, brd)

            // WP name
            val wpText = when (r) {
                0 -> from
                1 -> to
                else -> wpLabels.getOrElse(r) { "nach:" }
            }
            c.drawText(wpText, M + 3f, ry + 14f, vp)
            c.drawText("${r + 1}", M + colX[1] + 5f, ry + 14f, vp)

            // Fill data
            if (r == 0) {
                // Departure row: time + arrow
                c.drawText(depTime, M + colX[10] + 2f, ry + 14f, vp)
                c.drawText(arrows[0], M + colX[6] + 8f, ry + 14f, vp)
            }
            if (r == 1) {
                // Arrival row: distance, time, duration, arrow
                val dist = log.distance.replace(" nm", "").replace("nm", "")
                c.drawText(dist, M + colX[4] + 2f, ry + 14f, vp)
                c.drawText(arrTime, M + colX[10] + 2f, ry + 14f, vp)
                c.drawText(log.duration, M + colX[11] + 2f, ry + 14f, vp)
                c.drawText(arrows[1], M + colX[6] + 8f, ry + 14f, vp)
            }
        }

        y += totalRows * rowH

        // ── Totals row ──
        c.drawRect(M, y, M + CW, y + rowH, brd)
        for (i in 1 until colX.size - 1) {
            c.drawLine(M + colX[i], y, M + colX[i], y + rowH, tbrd)
        }
        c.drawLine(M + colX[rightStart], y, M + colX[rightStart], y + rowH, brd)
        c.drawText("Gesamt (kumuliert):", M + 3f, y + 14f, hp)
        val distTotal = log.distance.replace(" nm", "").replace("nm", "")
        c.drawText(distTotal, M + colX[4] + 2f, y + 14f, vp)
        c.drawText(log.duration, M + colX[11] + 2f, y + 14f, vp)

        return y + rowH + 8f
    }

    // ══════════════════════════════════════════════════
    //  EVENTS / NOTES
    // ══════════════════════════════════════════════════
    private fun drawEvents(c: Canvas, startY: Float, d: LogbookDetails): Float {
        var y = startY
        val hp = headerPaint()
        val vp = valuePaint()
        val brd = borderPaint()
        val tbrd = thinBorderPaint()

        c.drawText("Ereignisse:", M, y + 9f, hp)
        y += 14f

        val boxH = 100f
        c.drawRect(M, y, M + CW, y + boxH, brd)

        val lineSpacing = 14f
        // Bemerkungstext zeilenweise umbrechen
        val bem = d.bemerkungen.trim()
        val maxCharsPerLine = ((CW - 10f) / vp.measureText("n")).toInt().coerceAtLeast(20)
        val lines = mutableListOf<String>()
        bem.split("\n").forEach { paragraph ->
            var rest = paragraph
            while (rest.length > maxCharsPerLine) {
                val cut = rest.lastIndexOf(' ', maxCharsPerLine).let { if (it <= 0) maxCharsPerLine else it }
                lines += rest.substring(0, cut).trim()
                rest = rest.substring(cut).trim()
            }
            if (rest.isNotBlank()) lines += rest
        }

        var ly = y + lineSpacing
        var idx = 0
        while (ly < y + boxH) {
            c.drawLine(M + 2f, ly, M + CW - 2f, ly, tbrd)
            if (idx < lines.size) {
                c.drawText(lines[idx], M + 4f, ly - 2f, vp)
                idx++
            }
            ly += lineSpacing
        }

        return y + boxH + 10f
    }

    // ══════════════════════════════════════════════════
    //  FOOTER (DGzRS + Signature)
    // ══════════════════════════════════════════════════
    private fun drawFooter(c: Canvas, startY: Float): Float {
        var y = startY
        val sp = smallPaint()
        val vp = valuePaint()
        val brd = borderPaint()

        // DGzRS info box
        val boxH = 55f
        c.drawRect(M, y, M + CW * 0.40f, y + boxH, brd)
        val dgLines = listOf(
            "DGzRS  Tel.: 0421 53687-0",
            "Funk: \u201EBremen Rescue\u201C",
            "Handy: 124 124",
            "Kanal 16 oder 70 (DSC)"
        )
        for (i in dgLines.indices) {
            c.drawText(dgLines[i], M + 4f, y + 11f + i * 10f, sp)
        }

        // Datum + Unterschrift area
        val sigX = M + CW * 0.42f
        val sigLineY = y + boxH - 8f
        // (Datum)
        c.drawLine(sigX, sigLineY, sigX + 80f, sigLineY, brd)
        c.drawText("(Datum)", sigX + 20f, sigLineY + 10f, sp)

        // (Unterschrift Schiffsführer)
        val usigX = M + CW * 0.65f
        c.drawLine(usigX, sigLineY, M + CW, sigLineY, brd)
        c.drawText("(Unterschrift Schiffsführer)", usigX + 10f, sigLineY + 10f, sp)

        return y + boxH + 10f
    }

    // ══════════════════════════════════════════════════
    //  HELPERS
    // ══════════════════════════════════════════════════
    private fun drawCell(
        c: Canvas, x: Float, y: Float, w: Float, h: Float,
        label: String, value: String,
        lp: Paint, vp: Paint, brd: Paint
    ) {
        c.drawRect(x, y, x + w, y + h, brd)
        c.drawText(label, x + 3f, y + 9f, lp)
        // Truncate value to fit cell
        val maxChars = ((w - 8f) / vp.measureText("W")).toInt().coerceAtLeast(1)
        c.drawText(value.take(maxChars), x + 3f, y + 21f, vp)
    }

    private fun saveAndNotify(context: Context, doc: PdfDocument, log: LogbookEntry) {
        val sanitized = log.routeDesc.replace(" ", "-").replace("→", "--")
            .replace(Regex("[^a-zA-Z0-9\\-äöüÄÖÜ]"), "").take(30)
        val fileName = "Toern-${sanitized}-${log.date.replace(".", "-")}.pdf"
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(dir, fileName)
        doc.writeTo(FileOutputStream(file))
        doc.close()
        Toast.makeText(context, "PDF gespeichert: Downloads/$fileName", Toast.LENGTH_LONG).show()
    }
}
