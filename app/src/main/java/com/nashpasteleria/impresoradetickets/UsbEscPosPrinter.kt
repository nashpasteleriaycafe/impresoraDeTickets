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

    fun print(context: Context, template: TicketTemplate): String {
        // Raster text makes line breaks and per-line sizing deterministic across ESC/POS printers.
        val ticket = renderTicketText(template)
        return printBitmap(context, ticket, template.paperWidth, template.feedLines)
    }

    private fun renderTicketText(template: TicketTemplate): Bitmap {
        val width = if (template.paperWidth <= 58) 384 else 576
        val margin = 12f
        val availableWidth = width - margin * 2
        val source = template.content
        val rich = if (source.contains("<b", true) || source.contains("<i", true) || source.contains("<u", true) || source.contains("<span", true) || source.contains("<br", true)) {
            android.text.Html.fromHtml(source, android.text.Html.FROM_HTML_MODE_LEGACY)
        } else android.text.SpannableString(source)
        val text = rich.toString().replace("\r\n", "\n").replace('\r', '\n')
        val lines = text.split("\n")
        val autoFit = template.designMode != "Tradicional"
        val baseSize = 30f
        val baseStyle = (if (template.bold) android.graphics.Typeface.BOLD else 0) or
            (if (template.italic) android.graphics.Typeface.ITALIC else 0)
        val family = when (template.fontFamily) {
            "Serif" -> android.graphics.Typeface.SERIF
            "Monospace" -> android.graphics.Typeface.MONOSPACE
            else -> android.graphics.Typeface.SANS_SERIF
        }
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            typeface = android.graphics.Typeface.create(family, baseStyle)
            textAlign = android.graphics.Paint.Align.LEFT
            isUnderlineText = template.underline
            textSize = if (autoFit) baseSize else template.fontSize.toFloat().coerceIn(10f, 80f)
        }
        val lineStarts = IntArray(lines.size)
        var cursor = 0
        lines.forEachIndexed { i, line -> lineStarts[i] = cursor; cursor += line.length + 1 }
        fun measureLine(line: String, start: Int): Float {
            var total = 0f
            line.forEachIndexed { offset, ch ->
                val index = start + offset
                val charPaint = android.text.TextPaint(paint)
                if (rich is android.text.Spanned && index < rich.length) {
                    rich.getSpans(index, (index + 1).coerceAtMost(rich.length), android.text.style.CharacterStyle::class.java)
                        .forEach { it.updateDrawState(charPaint) }
                }
                if (autoFit) charPaint.textSize = charPaint.textSize.coerceAtLeast(8f)
                total += charPaint.measureText(ch.toString())
            }
            return total
        }
        val sizes = lines.mapIndexed { index, line ->
            if (line.isEmpty()) 24f
            else if (!autoFit) template.fontSize.toFloat().coerceIn(10f, 80f)
            else {
                val measured = measureLine(line, lineStarts[index]).coerceAtLeast(1f)
                (baseSize * availableWidth / measured).coerceAtLeast(8f)
            }
        }
        val heights = sizes.mapIndexed { index, size ->
            val maxTextSize = if (lines[index].isEmpty()) size else {
                var maxSize = size
                lines[index].indices.forEach { offset ->
                    val ix = lineStarts[index] + offset
                    if (rich is android.text.Spanned && ix < rich.length) {
                        rich.getSpans(ix, ix + 1, android.text.style.AbsoluteSizeSpan::class.java).forEach { maxSize = maxOf(maxSize, it.size.toFloat()) }
                        rich.getSpans(ix, ix + 1, android.text.style.RelativeSizeSpan::class.java).forEach { maxSize = maxOf(maxSize, size * it.sizeChange) }
                    }
                }
                maxSize
            }
            maxTextSize * 1.22f
        }
        val height = (heights.sum() + 12f).toInt().coerceAtLeast(24)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)
        var y = 6f
        lines.forEachIndexed { lineIndex, line ->
            val start = lineStarts[lineIndex]
            val lineSize = sizes[lineIndex]
            paint.textSize = lineSize
            val measured = measureLine(line, start)
            val xStart = when (template.alignment) {
                "Centro" -> (width - measured) / 2f
                "Derecha" -> width - margin - measured
                else -> margin
            }.coerceAtLeast(0f)
            val baseline = y - paint.fontMetrics.ascent
            var x = xStart
            line.forEachIndexed { offset, ch ->
                val index = start + offset
                val charPaint = android.text.TextPaint(paint)
                if (rich is android.text.Spanned && index < rich.length) {
                    rich.getSpans(index, (index + 1).coerceAtMost(rich.length), android.text.style.CharacterStyle::class.java)
                        .forEach { it.updateDrawState(charPaint) }
                }
                if (autoFit) {
                    val spanSize = charPaint.textSize
                    charPaint.textSize = (lineSize * (spanSize / baseSize)).coerceAtLeast(8f)
                }
                val piece = ch.toString()
                canvas.drawText(piece, x, baseline, charPaint)
                x += charPaint.measureText(piece)
            }
            y += heights[lineIndex]
        }
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
