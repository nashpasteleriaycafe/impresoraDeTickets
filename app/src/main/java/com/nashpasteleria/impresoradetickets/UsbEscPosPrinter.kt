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
        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val devices = manager.deviceList.values.toList()
        if (devices.isEmpty()) {
            return "No se detectó ninguna impresora USB. Conectá la RPT001 al Android con un cable OTG compatible y asegurate de que esté encendida."
        }

        val device = devices.firstOrNull { hasBulkOut(it) } ?: devices.first()
        if (!manager.hasPermission(device)) {
            val permissionIntent = PendingIntent.getBroadcast(
                context, 0,
                Intent(ACTION_USB_PERMISSION).setPackage(context.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            manager.requestPermission(device, permissionIntent)
            return "Aceptá el permiso USB que muestra Android y después tocá Imprimir ticket por USB otra vez."
        }

        val connection = manager.openDevice(device)
            ?: return "Android detectó el USB, pero no pudo abrirlo. Desconectá y reconectá el cable OTG y probá otra vez."

        val target = findBulkOut(device)
        if (target == null) {
            connection.close()
            return "Se detectó el USB, pero no se encontró un canal de impresión compatible."
        }
        val (intf, endpoint) = target
        if (!connection.claimInterface(intf, true)) {
            connection.close()
            return "No se pudo reservar la interfaz USB de la impresora."
        }
        try {
            val payload = buildEscPos(template)
            var offset = 0
            while (offset < payload.size) {
                val count = minOf(4096, payload.size - offset)
                val sent = connection.bulkTransfer(endpoint, payload, offset, count, 5000)
                if (sent <= 0) {
                    return "La impresora no aceptó los datos. Revisá que esté encendida, con papel y sin luces de error."
                }
                offset += sent
            }
            return "Ticket enviado a la impresora USB. Si no salió papel, revisá el cable OTG, el estado de la impresora y el permiso USB."
        } finally {
            connection.releaseInterface(intf)
            connection.close()
        }
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
        val lines = template.content.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val maxChars = if (template.paperWidth == 58) 32 else 48
        lines.forEach { line ->
            val cells = line.split('\t')
            if (cells.size <= 1) {
                out.addAll(line.toByteArray(charset).toList())
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
            val maxWidth = if (paperWidth == 58) 384 else 576
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
