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

object LogbookPdfGenerator {

    private const val PW = 595
    private const val PH = 842
    private const val M = 32f  
    private val CW get() = PW - 2 * M  

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

    private fun drawTitle(c: Canvas, startY: Float, log: LogbookEntry): Float {
        var y = startY + 4f
        val tp = titlePaint()

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

    private fun drawTripInfoBlock(
        c: Canvas, startY: Float, log: LogbookEntry,
        d: LogbookDetails, bp: BoatProfileRepository
    ): Float {
        var y = startY
        val rh = 28f  
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

        val r1c1 = CW * 0.38f; val r1c2 = CW * 0.30f; val r1c3 = CW * 0.32f

        val crewMembers = if (d.crew.isBlank()) listOf("\u2013") else
            d.crew.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val crewLineH = 10f
        val row1H = (crewMembers.size * crewLineH + 14f).coerceAtLeast(rh)

        val boatName = bp.boatName.trim()
        val tornLabel = if (boatName.isNotBlank()) "T\u00F6rn von/nach ($boatName):"
                        else "T\u00F6rn von/nach:"
        drawCell(c, M, y, r1c1, row1H, tornLabel, "$from \u2192 $to", lp, vp, brd)

        val crewX = M + r1c1
        c.drawRect(crewX, y, crewX + r1c2, y + row1H, brd)
        c.drawText("Crew:", crewX + 3f, y + 9f, lp)
        val maxCrewChars = ((r1c2 - 8f) / vp.measureText("W")).toInt().coerceAtLeast(10)
        for (i in crewMembers.indices) {
            c.drawText(crewMembers[i].take(maxCrewChars), crewX + 3f, y + 20f + i * crewLineH, vp)
        }

        val tzX = M + r1c1 + r1c2
        c.drawRect(tzX, y, tzX + r1c3, y + row1H, brd)
        c.drawText("Zeitzone:", tzX + 3f, y + 9f, lp)
        c.drawText("UTC + 1 Std.", tzX + 3f, y + 21f, vp)
        y += row1H

        val r2c1 = CW * 0.38f; val r2c2 = CW * 0.30f; val r2c3 = CW * 0.32f
        drawCell(c, M, y, r2c1, rh, "Schiffsf\u00FChrer:", skipper, lp, vp, brd)
        drawCell(c, M + r2c1, y, r2c2, rh, "verwendete Seekarten:", "\u2013", lp, vp, brd)

        val bsX = M + r2c1 + r2c2
        c.drawRect(bsX, y, bsX + r2c3, y + rh, brd)
        c.drawText("Betriebsstd. bei Abfahrt:", bsX + 3f, y + 9f, lp)
        c.drawText(bsA, bsX + r2c3 - 32f, y + 9f, vp)
        c.drawText("bei Ankunft:", bsX + 3f, y + 21f, lp)
        c.drawText(bsB, bsX + r2c3 - 32f, y + 21f, vp)
        y += rh

        val r3c1 = CW * 0.38f; val r3c2 = CW * 0.30f; val r3c3 = CW * 0.32f
        drawCell(c, M, y, r3c1, rh, "Wetter / Wind:", wx.take(45), lp, vp, brd)

        val persAnBord = if (d.crew.isBlank()) "\u2013" else
            d.crew.split(",").map { it.trim() }.filter { it.isNotEmpty() }.size.toString()
        drawCell(c, M + r3c1, y, r3c2, rh, "Pers. an Bord:", persAnBord, lp, vp, brd)

        val tiefgang = "%.2f".format(bp.draft).replace(".", ",")
        val aufbauTeil = if (aufbHoehe == "\u2013") "\u2013" else "$aufbHoehe m"
        val aufbTiefgang = "$aufbauTeil / $tiefgang m"
        drawCell(c, M + r3c1 + r3c2, y, r3c3, rh, "Aufbauh\u00F6he / Tiefgang (m):", aufbTiefgang, lp, vp, brd)
        y += rh

        val r4c1 = CW * 0.38f; val r4c2 = CW * 0.30f; val r4c3 = CW * 0.32f
        drawCell(c, M, y, r4c1, rh, "Wasserst.-Vorhers. (+/- m):", wt, lp, vp, brd)
        drawCell(c, M + r4c1, y, r4c2, rh, "Gezeiten:", tide.take(35), lp, vp, brd)
        drawCell(c, M + r4c1 + r4c2, y, r4c3, rh, "UKC:", ukc, lp, vp, brd)
        y += rh

        return y + 6f
    }

    private fun drawChecklist(c: Canvas, startY: Float, d: LogbookDetails): Float {
        var y = startY
        val hp = headerPaint()
        val sp = smallPaint()
        val chk = checkPaint()
        val brd = borderPaint()

        c.drawText("✓  Checkliste vor Abfahrt:", M, y + 9f, hp)
        y += 14f

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

    private fun parseWindSpeed(weather: String): String {
        val m = Regex("(\\d+)\\s*kn").find(weather) ?: return ""
        return "${m.groupValues[1]}kn"
    }

    private fun parseWindDir(weather: String): String {
        val m = Regex("kn\\s+(N[OW]?|S[OW]?|[OW])\\b").find(weather) ?: return ""

        val sector = m.groupValues[1]
        val deg = mapOf(
            "N" to 0, "NO" to 45, "O" to 90, "SO" to 135,
            "S" to 180, "SW" to 225, "W" to 270, "NW" to 315
        )[sector] ?: return sector
        return "${deg}°"
    }

    private fun drawWaypointTable(
        c: Canvas, startY: Float, log: LogbookEntry, d: LogbookDetails
    ): Float {
        var y = startY
        val hp = headerPaint()
        val lp = labelPaint()
        val vp = valuePaint()
        val brd = borderPaint()
        val tbrd = thinBorderPaint()

        val routeParts = log.routeDesc.split("→", "->", "➔", "nach").map { it.trim() }
        val from = routeParts.firstOrNull() ?: "–"
        val to = routeParts.getOrNull(1) ?: "–"
        val depTime = d.abfahrt.takeLast(5).ifBlank { "–" }
        val arrTime = d.ankunft.takeLast(5).ifBlank { "–" }

        val colX = floatArrayOf(
            0f,           
            CW * 0.22f,   
            CW * 0.28f,   
            CW * 0.35f,   
            CW * 0.42f,   
            CW * 0.52f,   
            CW * 0.62f,   
            CW * 0.72f,   
            CW * 0.82f,   
            CW * 0.91f,   
            CW            
        )
        val rightStart = 4  

        val hdr1H = 12f
        c.drawRect(M, y, M + colX[rightStart], y + hdr1H, brd)
        c.drawRect(M + colX[rightStart], y, M + CW, y + hdr1H, brd)
        val tvTitle = "Törnverlauf"
        val tvW = hp.measureText(tvTitle)
        val rightMid = (colX[rightStart] + CW) / 2f
        c.drawText(tvTitle, M + rightMid - tvW / 2f, y + 9f, hp)
        y += hdr1H

        val hdr2H = 14f
        c.drawRect(M, y, M + CW, y + hdr2H, brd)
        c.drawText("Wegepunkte (WP)", M + 3f, y + 10f, lp)
        c.drawText("Nr.", M + colX[1] + 2f, y + 10f, lp)
        c.drawText("WuK", M + colX[2] + 2f, y + 10f, lp)
        c.drawText("UKW", M + colX[3] + 2f, y + 10f, lp)
        c.drawText("Entf. (sm)", M + colX[4] + 2f, y + 10f, lp)
        c.drawText("Windstärke", M + colX[5] + 2f, y + 10f, lp)
        c.drawText("Windricht.", M + colX[6] + 2f, y + 10f, lp)
        c.drawText("Geschw.(kn)", M + colX[7] + 1f, y + 10f, lp)
        c.drawText("Zeit am WP", M + colX[8] + 1f, y + 10f, lp)
        c.drawText("Fahrzeit", M + colX[9] + 1f, y + 10f, lp)
        for (i in 1 until colX.size - 1) {
            c.drawLine(M + colX[i], y, M + colX[i], y + hdr2H, tbrd)
        }
        c.drawLine(M + colX[rightStart], y, M + colX[rightStart], y + hdr2H, brd)
        y += hdr2H

        val rowH = 20f
        val totalRows = 2  
        val wuK = d.wt.takeIf { it.isNotBlank() }?.replace(" m", "")?.replace("m", "")?.trim() ?: ""
        val ukw = d.ukc.takeIf { it.isNotBlank() }?.replace(" m", "")?.replace("m", "")?.trim() ?: ""
        val windKn = parseWindSpeed(d.wetter)
        val windDir = parseWindDir(d.wetter)

        for (r in 0 until totalRows) {
            val ry = y + r * rowH
            c.drawRect(M, ry, M + CW, ry + rowH, brd)
            for (i in 1 until colX.size - 1) {
                c.drawLine(M + colX[i], ry, M + colX[i], ry + rowH, tbrd)
            }
            c.drawLine(M + colX[rightStart], ry, M + colX[rightStart], ry + rowH, brd)

            val wpText = if (r == 0) from else to
            c.drawText(wpText, M + 3f, ry + 13f, vp)
            c.drawText("${r + 1}", M + colX[1] + 5f, ry + 13f, vp)
            if (wuK.isNotBlank()) c.drawText(wuK, M + colX[2] + 2f, ry + 13f, vp)
            if (ukw.isNotBlank()) c.drawText(ukw, M + colX[3] + 2f, ry + 13f, vp)
            if (windKn.isNotBlank()) c.drawText(windKn, M + colX[5] + 2f, ry + 13f, vp)
            if (windDir.isNotBlank()) c.drawText(windDir, M + colX[6] + 2f, ry + 13f, vp)

            if (r == 0) {
                c.drawText(depTime, M + colX[8] + 2f, ry + 13f, vp)
            }
            if (r == 1) {
                val dist = log.distance.replace(" nm", "").replace("nm", "").trim()
                c.drawText(dist, M + colX[4] + 2f, ry + 13f, vp)
                c.drawText(arrTime, M + colX[8] + 2f, ry + 13f, vp)
                c.drawText(log.duration, M + colX[9] + 2f, ry + 13f, vp)
            }
        }
        y += totalRows * rowH

        c.drawRect(M, y, M + CW, y + rowH, brd)
        for (i in 1 until colX.size - 1) {
            c.drawLine(M + colX[i], y, M + colX[i], y + rowH, tbrd)
        }
        c.drawLine(M + colX[rightStart], y, M + colX[rightStart], y + rowH, brd)
        c.drawText("Gesamt (kumuliert):", M + 3f, y + 13f, hp)
        val distTotal = log.distance.replace(" nm", "").replace("nm", "").trim()
        c.drawText(distTotal, M + colX[4] + 2f, y + 13f, vp)
        c.drawText(log.duration, M + colX[9] + 2f, y + 13f, vp)

        return y + rowH + 8f
    }

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

    private fun drawFooter(c: Canvas, startY: Float): Float {
        var y = startY
        val sp = smallPaint()
        val vp = valuePaint()
        val brd = borderPaint()

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

        val sigX = M + CW * 0.42f
        val sigLineY = y + boxH - 8f

        c.drawLine(sigX, sigLineY, sigX + 80f, sigLineY, brd)
        c.drawText("(Datum)", sigX + 20f, sigLineY + 10f, sp)

        val usigX = M + CW * 0.65f
        c.drawLine(usigX, sigLineY, M + CW, sigLineY, brd)
        c.drawText("(Unterschrift Schiffsführer)", usigX + 10f, sigLineY + 10f, sp)

        return y + boxH + 10f
    }

    private fun drawCell(
        c: Canvas, x: Float, y: Float, w: Float, h: Float,
        label: String, value: String,
        lp: Paint, vp: Paint, brd: Paint
    ) {
        c.drawRect(x, y, x + w, y + h, brd)
        c.drawText(label, x + 3f, y + 9f, lp)

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
