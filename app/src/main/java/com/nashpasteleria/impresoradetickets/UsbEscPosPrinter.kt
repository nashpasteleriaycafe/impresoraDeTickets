package com.nashpasteleria.impresoradetickets

import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.content.Intent
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import java.nio.charset.Charset

/** USB-host ESC/POS output for compatible thermal receipt printers. */
object UsbEscPosPrinter {
    private const val ACTION_USB_PERMISSION = "com.nashpasteleria.impresoradetickets.USB_PERMISSION"

    private fun normalizeRichHtml(source: String): String {
        // Normalize legacy <font> markup to CSS spans with explicit pixel sizes.
        // This prevents relative HTML font sizes from collapsing in StaticLayout.
        val prepared = Regex("""<font\b([^>]*)>""", RegexOption.IGNORE_CASE).replace(source) { match ->
            val attrs = match.groupValues[1]
            val sizeToken = Regex("""\bsize\s*=\s*["']?([1-7])["']?""", RegexOption.IGNORE_CASE)
                .find(attrs)?.groupValues?.get(1)?.toIntOrNull()
            val face = Regex("""\bface\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                .find(attrs)?.groupValues?.get(1)
            val px = when (sizeToken) {
                1 -> 10f; 2 -> 13f; 3 -> 16f; 4 -> 18f
                5 -> 24f; 6 -> 32f; 7 -> 42f; else -> null
            }
            val css = buildString {
                if (px != null) append("font-size:").append(px).append("px;")
                if (!face.isNullOrBlank()) append("font-family:").append(face).append(";")
            }
            "<span style=\"$css\">"
        }.replace(Regex("""</font\s*>""", RegexOption.IGNORE_CASE), "</span>")
        // Android's Html.fromHtml does not reliably honor CSS inside <span>.
        // Convert each span to explicit HTML tags while preserving its own style.
        val spanClosures = mutableListOf<String>()
        var html = Regex("""<span\b([^>]*)>|</span\s*>""", RegexOption.IGNORE_CASE).replace(prepared) { match ->
            if (match.value.startsWith("</", ignoreCase = true)) {
                if (spanClosures.isNotEmpty()) spanClosures.removeAt(spanClosures.lastIndex) else ""
            } else {
                val attributes = match.groupValues[1]
                val style = Regex("""style\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
                    .find(attributes)?.groupValues?.get(1).orEmpty()
                val family = Regex("""font-family\s*:\s*([^;]+)""", RegexOption.IGNORE_CASE)
                    .find(style)?.groupValues?.get(1)?.trim()?.trim('"', '\'')
                val pxSize = Regex("""font-size\s*:\s*([0-9.]+)\s*(px|pt|sp)?""", RegexOption.IGNORE_CASE)
                    .find(style)?.groupValues?.get(1)?.toFloatOrNull()
                val htmlSize = pxSize?.let { size ->
                    when {
                        size <= 10f -> 1
                        size <= 13f -> 2
                        size <= 16f -> 3
                        size <= 18f -> 4
                        size <= 24f -> 5
                        size <= 32f -> 6
                        else -> 7
                    }
                }
                val bold = Regex("""font-weight\s*:\s*(bold|[6-9]00)""", RegexOption.IGNORE_CASE).containsMatchIn(style)
                val italic = Regex("""font-style\s*:\s*italic""", RegexOption.IGNORE_CASE).containsMatchIn(style)
                val underline = Regex("""text-decoration(?:-line)?\s*:\s*[^;]*underline""", RegexOption.IGNORE_CASE).containsMatchIn(style)
                val opening = buildString {
                    if (bold) append("<b>")
                    if (italic) append("<i>")
                    if (underline) append("<u>")
                    append("<font")
                    if (!family.isNullOrBlank()) append(" face=\"").append(family.replace("\"", "")).append("\"")
                    if (htmlSize != null) append(" size=\"").append(htmlSize).append("\"")
                    if (pxSize != null) append(" data-nash-size=\"").append(pxSize).append("\"")
                    append(">")
                }
                val closing = buildString {
                    append("</font>")
                    if (underline) append("</u>")
                    if (italic) append("</i>")
                    if (bold) append("</b>")
                }
                spanClosures.add(closing)
                opening
            }
        }
        html = Regex("""<(p|div|h[1-6])\b([^>]*)>""", RegexOption.IGNORE_CASE).replace(html) { match ->
            val tag = match.groupValues[1].lowercase()
            val attributes = match.groupValues[2]
            val alignment = Regex("""text-align\s*:\s*(left|center|right|justify)""", RegexOption.IGNORE_CASE)
                .find(attributes)?.groupValues?.get(1)?.lowercase()
            val hasAlign = Regex("""\balign\s*=""", RegexOption.IGNORE_CASE).containsMatchIn(attributes)
            val normalizedTag = if (tag == "div" || tag.startsWith("h")) "p" else tag
            if (alignment != null && !hasAlign) {
                "<" + normalizedTag + attributes + " align=\"" + alignment + "\">"
            } else if (normalizedTag != tag) {
                "<" + normalizedTag + attributes + ">"
            } else match.value
        }
        html = Regex("""</(div|h[1-6])\s*>""", RegexOption.IGNORE_CASE).replace(html, "</p>")
        return html
    }

    fun print(context: Context, template: TicketTemplate): String {
        // Raster text makes line breaks and per-line sizing deterministic across ESC/POS printers.
        val ticket = renderTicketText(template)
        return printBitmap(context, ticket, template.paperWidth, template.feedLines)
    }

    private fun renderTicketText(template: TicketTemplate): Bitmap =
        renderRichTicketText(template, automatic = template.designMode != "Tradicional")

    /** Uses Android's paragraph layout engine in both modes so wrapped lines never overlap or clip. */
    private fun renderTraditionalTicketText(template: TicketTemplate): Bitmap =
        renderRichTicketText(template, automatic = false)

    private fun renderRichTicketText(template: TicketTemplate, automatic: Boolean): Bitmap {
        val width = if (template.paperWidth <= 58) 384 else 576
        val margin = 12
        val availableWidth = (width - margin * 2).coerceAtLeast(1)
        val source = template.content
        val containsHtml = Regex("""<[a-zA-Z][^>]*>""").containsMatchIn(source)
        val normalizedHtml = if (containsHtml) normalizeRichHtml(source) else ""
        val parsed = if (containsHtml) {
            android.text.Html.fromHtml(normalizedHtml, android.text.Html.FROM_HTML_MODE_LEGACY)
        } else {
            android.text.SpannableStringBuilder(source.replace("\r\n", "\n").replace('\r', '\n'))
        }
        val rich = android.text.SpannableStringBuilder(parsed)
        if (containsHtml) {
            // HTML font sizes are coarse; restore the original CSS pixel size on each converted span.
            val exactSizeTag = Regex("""<font\b([^>]*)data-nash-size="([0-9.]+)"([^>]*)>(.*?)</font>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            var searchFrom = 0
            exactSizeTag.findAll(normalizedHtml).forEach { match ->
                val size = match.groupValues[2].toFloatOrNull()
                val spanText = android.text.Html.fromHtml(match.groupValues[4], android.text.Html.FROM_HTML_MODE_LEGACY).toString()
                if (size != null && spanText.isNotEmpty() && searchFrom <= rich.length) {
                    val start = rich.indexOf(spanText, searchFrom)
                    if (start >= 0) {
                        val end = (start + spanText.length).coerceAtMost(rich.length)
                        rich.setSpan(android.text.style.AbsoluteSizeSpan(size.toInt().coerceAtLeast(1), false), start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        searchFrom = end
                    }
                }
            }
        }
        while (rich.isNotEmpty() && rich.last() == '\n') rich.delete(rich.length - 1, rich.length)

        val family = when (template.fontFamily) {
            "Serif" -> android.graphics.Typeface.SERIF
            "Monospace" -> android.graphics.Typeface.MONOSPACE
            else -> android.graphics.Typeface.SANS_SERIF
        }
        val baseStyle = if (containsHtml) android.graphics.Typeface.NORMAL else
            (if (template.bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL) or
            (if (template.italic) android.graphics.Typeface.ITALIC else 0)
        val baseSize = if (automatic) 28f else template.fontSize.toFloat().coerceIn(8f, 80f)
        val paint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            textSize = baseSize
            typeface = android.graphics.Typeface.create(family, baseStyle)
            isUnderlineText = !containsHtml && template.underline
        }

        // Automatic mode sizes each explicit paragraph independently, matching the
        // preview behavior. Preserve explicitly formatted HTML sizes when present.
        if (automatic && rich.isNotEmpty()) {
            // Automatic mode derives a size per paragraph from the actual text width.
            // A hard upper cap on each line made nearly every short line hit the same
            // maximum size, which is why the previous version looked uniform.
            rich.getSpans(0, rich.length, android.text.style.AbsoluteSizeSpan::class.java)
                .forEach { rich.removeSpan(it) }
            rich.getSpans(0, rich.length, android.text.style.RelativeSizeSpan::class.java)
                .forEach { rich.removeSpan(it) }

            val lineRanges = mutableListOf<Pair<Int, Int>>()
            var lineStart = 0
            while (lineStart < rich.length) {
                val newline = rich.indexOf("\n", lineStart)
                val lineEnd = if (newline < 0) rich.length else newline
                if (lineEnd > lineStart) lineRanges.add(lineStart to lineEnd)
                if (newline < 0) break
                lineStart = newline + 1
            }
            val widestLine = lineRanges.maxOfOrNull { (start, end) ->
                paint.measureText(rich, start, end)
            }?.coerceAtLeast(1f) ?: 1f
            val widthFit = ((availableWidth - 20f).coerceAtLeast(1f) / widestLine).coerceAtMost(1f)
            lineRanges.forEach { (start, end) ->
                val measured = paint.measureText(rich, start, end).coerceAtLeast(1f)
                // Longest line remains at the base size (or shrinks to fit). Shorter
                // paragraphs grow progressively, rather than all being clamped to one size.
                val relativeScale = kotlin.math.sqrt(widestLine / measured)
                val lineSizePx = (baseSize * widthFit * relativeScale).toInt().coerceIn(8, 72)
                rich.setSpan(
                    android.text.style.AbsoluteSizeSpan(lineSizePx, false),
                    start, end,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        if (rich.isEmpty()) {
            return Bitmap.createBitmap(width, 24, Bitmap.Config.ARGB_8888).also { bitmap ->
                android.graphics.Canvas(bitmap).drawColor(android.graphics.Color.WHITE)
            }
        }

        val fallbackAlignment = when (template.alignment) {
            "Centro" -> android.text.Layout.Alignment.ALIGN_CENTER
            "Derecha" -> android.text.Layout.Alignment.ALIGN_OPPOSITE
            else -> android.text.Layout.Alignment.ALIGN_NORMAL
        }
        val builder = android.text.StaticLayout.Builder.obtain(rich, 0, rich.length, paint, availableWidth)
            .setAlignment(fallbackAlignment)
            .setIncludePad(false)
            .setLineSpacing(0f, 1.10f)
            .setBreakStrategy(android.text.Layout.BREAK_STRATEGY_SIMPLE)
            .setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NONE)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            builder.setJustificationMode(
                if (template.alignment == "Justificado") android.text.Layout.JUSTIFICATION_MODE_INTER_WORD
                else android.text.Layout.JUSTIFICATION_MODE_NONE
            )
        }
        val layout = builder.build()
        val bitmapHeight = (layout.height + 12).coerceAtLeast(24)
        val bitmap = Bitmap.createBitmap(width, bitmapHeight, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)
        canvas.save()
        canvas.translate(margin.toFloat(), 6f)
        layout.draw(canvas)
        canvas.restore()
        return bitmap
    }

    private fun hasBulkOut(device: UsbDevice): Boolean = findBulkOut(device) != null

    private fun findBulkOut(device: UsbDevice): Pair<UsbInterface, UsbEndpoint>? {
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            for (e in 0 until intf.endpointCount) {
                val ep = intf.getEndpoint(e)
                if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK &&
                    ep.direction == UsbConstants.USB_DIR_OUT) return intf to ep
            }
        }
        return null
    }

    private fun buildEscPos(template: TicketTemplate): ByteArray {
        val out = ArrayList<Byte>()
        fun add(vararg bytes: Int) = bytes.forEach { out.add(it.toByte()) }

        // ESC/POS initialize, select Western European code page, left alignment.
        add(0x1B, 0x40)
        add(0x1B, 0x74, 0x10)
        add(0x1B, 0x61, 0x00)
        add(0x1B, 0x21, 0x00)
        val charset = Charset.forName("windows-1252")
        val source = template.content
        val printable = if (source.contains("<html", true) || source.contains("<p", true) || source.contains("<table", true) || source.contains("<div", true)) {
            val tableAware = source.replace(Regex("(?i)</td>\\s*<td[^>]*>"), "\\t")
                .replace(Regex("(?i)</t[dh]\\s*>"), "")
                .replace(Regex("(?i)</tr\\s*>"), "\\n")
                .replace(Regex("(?i)<br\\s*/?>"), "\\n")
                .replace(Regex("(?i)</p\\s*>|</div\\s*>"), "\\n")
                .replace(Regex("(?i)<p[^>]*>|<div[^>]*>|<tr[^>]*>|<td[^>]*>|<th[^>]*>"), "")
            android.text.Html.fromHtml(tableAware, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
        } else source
        val lines = printable.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val maxChars = if (template.paperWidth <= 58) 32 else 48
        lines.forEach { line ->
            val cells = line.split('\t')
            if (cells.size <= 1) {
                // Use double-width ESC/POS text for short lines so headings occupy more of the roll.
                val scale = if (line.length in 1..15) 2 else 1
                add(0x1D, 0x21, if (scale == 2) 0x01 else 0x00)
                out.addAll(line.toByteArray(charset).toList())
                add(0x1D, 0x21, 0x00)
                add(0x0A)
            } else {
                val colWidth = (maxChars / cells.size).coerceAtLeast(1)
                val rendered = cells.mapIndexed { index, cell ->
                    val trimmed = cell.take(colWidth)
                    if (index == cells.lastIndex) trimmed else trimmed.padEnd(colWidth, ' ')
                }.joinToString("").take(maxChars)
                out.addAll(rendered.toByteArray(charset).toList())
                add(0x0A)
            }
        }
        repeat(template.feedLines.coerceIn(0, 10)) { add(0x0A) }
        return out.toByteArray()
    }
    /** Prints a monochrome raster bitmap using ESC/POS GS v 0. */
    fun printBitmap(context: Context, bitmap: Bitmap, paperWidth: Int = 80, feedLines: Int = 3): String {
        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val device = manager.deviceList.values.firstOrNull { hasBulkOut(it) }
            ?: return "No se detectó una impresora USB compatible."
        if (!manager.hasPermission(device)) {
            val permissionIntent = PendingIntent.getBroadcast(context, 0,
                Intent(ACTION_USB_PERMISSION).setPackage(context.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            manager.requestPermission(device, permissionIntent)
            return "Aceptá el permiso USB y volvé a tocar Imprimir."
        }
        val target = findBulkOut(device) ?: return "No se encontró el canal USB de impresión."
        val (intf, endpoint) = target
        val connection = manager.openDevice(device) ?: return "No se pudo abrir la impresora USB."
        if (!connection.claimInterface(intf, true)) { connection.close(); return "No se pudo reservar la interfaz USB." }
        try {
            val maxWidth = if (paperWidth <= 58) 384 else 576
            val scaledHeight = (bitmap.height * maxWidth.toFloat() / bitmap.width).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(bitmap, maxWidth, scaledHeight, true)
            val bytesPerRow = (scaled.width + 7) / 8
            val out = ArrayList<Byte>()
            fun add(vararg values: Int) = values.forEach { out.add(it.toByte()) }
            add(0x1B, 0x40, 0x1B, 0x61, 0x00)
            var y = 0
            while (y < scaled.height) {
                val bandHeight = minOf(255, scaled.height - y)
                add(0x1D, 0x76, 0x30, 0x00, bytesPerRow and 0xFF, (bytesPerRow shr 8) and 0xFF, bandHeight and 0xFF, (bandHeight shr 8) and 0xFF)
                for (row in y until y + bandHeight) {
                    for (bx in 0 until bytesPerRow) {
                        var bits = 0
                        for (bit in 0..7) {
                            val x = bx * 8 + bit
                            if (x < scaled.width) {
                                val pixel = scaled.getPixel(x, row)
                                val luminance = (android.graphics.Color.red(pixel) * 299 + android.graphics.Color.green(pixel) * 587 + android.graphics.Color.blue(pixel) * 114) / 1000
                                if (luminance < 160) bits = bits or (0x80 shr bit)
                            }
                        }
                        out.add(bits.toByte())
                    }
                }
                y += bandHeight
            }
            repeat(feedLines.coerceIn(0, 10)) { add(0x0A) }
            val payload = out.toByteArray()
            var offset = 0
            while (offset < payload.size) {
                val count = minOf(2048, payload.size - offset)
                val sent = connection.bulkTransfer(endpoint, payload, offset, count, 10000)
                if (sent <= 0) return "La impresora no aceptó la imagen. Revisá papel y conexión USB."
                offset += sent
            }
            return "Imagen enviada a la impresora USB."
        } finally {
            connection.releaseInterface(intf)
            connection.close()
        }
    }

}
