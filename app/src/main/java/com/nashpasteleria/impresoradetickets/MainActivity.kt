package com.nashpasteleria.impresoradetickets

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Canvas as AndroidCanvas
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Rose = Color(0xFFE96B8E)
private val RoseDark = Color(0xFFB94467)
private val RosePale = Color(0xFFFFEEF2)
private val Ink = Color(0xFF30262A)
private val Muted = Color(0xFF82757A)
private val Canvas = Color(0xFFFFF9FA)
private val Line = Color(0xFFF0DDE2)

data class TicketTemplate(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val content: String = "Nombre del producto\n.\n" + SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()),
    val paperWidth: Int = 58,
    val feedLines: Int = 3,
    val cutAfterPrint: Boolean = true
)

private enum class AppTab(val title: String) {
    DESIGN("Diseñador"), TEMPLATES("Plantillas"), IMAGES("Imágenes"), PDF("PDF"), SETTINGS("Ajustes")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(
                primary = RoseDark, onPrimary = Color.White, secondary = Rose,
                background = Canvas, surface = Color.White, onSurface = Ink, onBackground = Ink
            )) {
                Surface(Modifier.fillMaxSize(), color = Canvas) { TicketApp(this@MainActivity) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TicketApp(context: Context) {
    val prefs = remember { context.getSharedPreferences("ticket_templates", Context.MODE_PRIVATE) }
    val templates = remember {
        mutableStateListOf<TicketTemplate>().apply {
            val raw = prefs.getString("items", null)
            if (raw != null) runCatching {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(TicketTemplate(
                        id = o.optString("id", UUID.randomUUID().toString()),
                        name = o.optString("name", "Plantilla"),
                        content = o.optString("content", "").let { saved ->
                            if (saved.isBlank() || (o.optString("name") == "Ticket de ejemplo" && saved.contains("NASH Pastelería"))) {
                                "Nombre del producto\n.\n" + SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
                            } else saved
                        },
                        paperWidth = o.optInt("paperWidth", 58),
                        feedLines = o.optInt("feedLines", 3),
                        cutAfterPrint = o.optBoolean("cutAfterPrint", true)
                    ))
                }
            }
            if (isEmpty()) add(TicketTemplate(name = "Ticket de ejemplo"))
        }
    }
    var selectedId by remember { mutableStateOf(templates.first().id) }
    var deleteCandidate by remember { mutableStateOf<TicketTemplate?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var activeTab by remember { mutableStateOf(AppTab.DESIGN) }
    var paperWidth by remember { mutableIntStateOf(prefs.getInt("paper_width", 58).coerceIn(40, 120)) }
    var paperWidthText by remember { mutableStateOf(paperWidth.toString()) }
    var selectedImage by remember { mutableStateOf<Uri?>(null) }
    var selectedPdf by remember { mutableStateOf<Uri?>(null) }
    var imageRotation by remember { mutableIntStateOf(0) }
    var imagePreview by remember { mutableStateOf<Bitmap?>(null) }
    var pdfPages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    val selectedPdfPages = remember { mutableStateListOf<Int>() }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { selectedImage = it }
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { selectedPdf = it }
    val selected = templates.firstOrNull { it.id == selectedId } ?: templates.firstOrNull()

    LaunchedEffect(selectedImage, imageRotation) {
        imagePreview = selectedImage?.let { uri -> runCatching { loadMonochromeImage(context, uri, imageRotation) }.getOrNull() }
    }
    LaunchedEffect(selectedPdf, paperWidth) {
        pdfPages = selectedPdf?.let { uri -> runCatching { renderPdfPages(context, uri, paperWidth) }.getOrDefault(emptyList()) } ?: emptyList()
        selectedPdfPages.clear()
        selectedPdfPages.addAll(pdfPages.indices)
    }

    fun persist() {
        val arr = JSONArray()
        templates.forEach { t ->
            arr.put(JSONObject().apply {
                put("id", t.id); put("name", t.name); put("content", t.content)
                put("paperWidth", t.paperWidth); put("feedLines", t.feedLines); put("cutAfterPrint", t.cutAfterPrint)
            })
        }
        prefs.edit().putString("items", arr.toString()).apply()
    }

    Scaffold(
        containerColor = Canvas,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(RosePale), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.ReceiptLong, null, tint = RoseDark)
                        }
                        Column {
                            Text("Nash · Tickets", fontWeight = FontWeight.Bold, fontSize = 19.sp, color = Ink)
                            Text("Diseñá. Guardá. Imprimí.", fontSize = 11.sp, color = Muted)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Canvas)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 8.dp) {
                AppTab.values().forEach { tab ->
                    val icon = when (tab) {
                        AppTab.DESIGN -> Icons.Default.ReceiptLong
                        AppTab.TEMPLATES -> Icons.Default.ViewList
                        AppTab.IMAGES -> Icons.Default.Image
                        AppTab.PDF -> Icons.Default.PictureAsPdf
                        AppTab.SETTINGS -> Icons.Default.Settings
                    }
                    NavigationBarItem(
                        selected = activeTab == tab,
                        onClick = { activeTab = tab },
                        icon = { Icon(icon, contentDescription = tab.title) },
                        label = { Text(tab.title, fontSize = 10.sp, maxLines = 1) },
                        alwaysShowLabel = true
                    )
                }
            }
        }
    ) { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(insets).padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (activeTab) {
                AppTab.DESIGN -> {
                    item { SectionHeading("Diseñá tu ticket", "Tocá una celda para seleccionarla y editá su contenido") }
                    item {
                        CardBlock {
                            OutlinedTextField(
                                value = selected?.name ?: "",
                                onValueChange = { value -> selected?.let { updateTemplate(templates, it.copy(name = value), ::persist) } },
                                label = { Text("Nombre de la plantilla") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            selected?.let { template ->
                                TicketTableEditor(template = template, onContentChange = { content ->
                                    updateTemplate(templates, template.copy(content = content), ::persist)
                                })
                            }
                        }
                    }
                    item {
                        CardBlock {
                            Text("Vista previa · papel de ${paperWidth} mm", fontWeight = FontWeight.SemiBold, color = Ink)
                            TicketContentPreview(selected?.content ?: "")
                        }
                    }
                    item {
                        Button(
                            onClick = { notice = selected?.let { UsbEscPosPrinter.print(context, it.copy(paperWidth = paperWidth)) } ?: "Primero creá o seleccioná una plantilla." },
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            shape = RoundedCornerShape(17.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = RoseDark)
                        ) {
                            Icon(Icons.Default.Print, null); Spacer(Modifier.width(9.dp))
                            Text("Imprimir ticket", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                    item { Text("Conexión USB · 3nSTAR RPT001", color = Muted, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
                }
                AppTab.TEMPLATES -> {
                    item { SectionHeading("Tus plantillas", "Guardá distintos diseños para cada necesidad") }
                    item {
                        Button(onClick = {
                            val created = TicketTemplate(name = "Plantilla ${templates.size + 1}", paperWidth = paperWidth)
                            templates.add(created); selectedId = created.id; persist(); activeTab = AppTab.DESIGN
                        }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(15.dp)) {
                            Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Crear nueva plantilla")
                        }
                    }
                    items(templates, key = { it.id }) { template ->
                        CardBlock {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(RosePale), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.ReceiptLong, null, tint = RoseDark)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(template.name, fontWeight = FontWeight.SemiBold, color = Ink)
                                    Text("${paperWidth} mm · ${template.content.lines().size} filas", color = Muted, fontSize = 12.sp)
                                }
                                IconButton(onClick = { selectedId = template.id; activeTab = AppTab.DESIGN }) {
                                    Icon(Icons.Default.Edit, "Diseñar plantilla", tint = RoseDark)
                                }
                                IconButton(onClick = { deleteCandidate = template }) {
                                    Icon(Icons.Default.Delete, "Eliminar plantilla", tint = Muted)
                                }
                            }
                            OutlinedButton(onClick = { selectedId = template.id; activeTab = AppTab.DESIGN }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                                Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(7.dp)); Text(if (selectedId == template.id) "Seleccionada · Ver diseño" else "Usar esta plantilla")
                            }
                        }
                    }
                }
                AppTab.IMAGES -> {
                    item { SectionHeading("Biblioteca de imágenes", "Prepará recursos para personalizar tus tickets") }
                    item {
                        CardBlock {
                            Box(Modifier.fillMaxWidth().heightIn(min = 110.dp, max = 260.dp).clip(RoundedCornerShape(16.dp)).background(RosePale), contentAlignment = Alignment.Center) {
                                val preview = imagePreview
                                if (preview == null) Icon(Icons.Default.Image, null, tint = RoseDark, modifier = Modifier.size(46.dp))
                                else androidx.compose.foundation.Image(bitmap = preview.asImageBitmap(), contentDescription = "Vista previa en blanco y negro", modifier = Modifier.fillMaxWidth().padding(8.dp), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
                            }
                            Text(if (selectedImage == null) "Todavía no elegiste una imagen" else "Imagen seleccionada", fontWeight = FontWeight.SemiBold, color = Ink)
                            Text(selectedImage?.lastPathSegment ?: "Elegí una imagen desde tu dispositivo.", fontSize = 12.sp, color = Muted)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { imageRotation = (imageRotation + 90) % 360 }, enabled = selectedImage != null, modifier = Modifier.weight(1f)) { Text("Rotar 90°") }
                                Text("Rotación: ${imageRotation}°", modifier = Modifier.align(Alignment.CenterVertically), color = Muted, fontSize = 12.sp)
                            }
                            Button(onClick = { imagePicker.launch("image/*") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)) {
                                Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Elegir imagen")
                            }
                            Button(onClick = {
                                val uri = selectedImage
                                notice = if (uri == null) "Primero elegí una imagen." else runCatching {
                                    val bitmap = imagePreview ?: loadMonochromeImage(context, uri, imageRotation)
                                    UsbEscPosPrinter.printBitmap(context, bitmap, paperWidth, selected?.feedLines ?: 3)
                                }.getOrElse { "No se pudo preparar la imagen: " + (it.message ?: "error desconocido") }
                            }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)) {
                                Icon(Icons.Default.Print, null); Spacer(Modifier.width(8.dp)); Text("Imprimir imagen por USB")
                            }
                            Text("La imagen se convierte a blanco y negro y se envía como gráfico ESC/POS.", fontSize = 11.sp, color = Muted)
                        }
                    }
                }
                AppTab.PDF -> {
                    item { SectionHeading("Documentos PDF", "Elegí un archivo para tenerlo a mano") }
                    item {
                        CardBlock {
                            Box(Modifier.fillMaxWidth().height(70.dp).clip(RoundedCornerShape(16.dp)).background(RosePale), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.PictureAsPdf, null, tint = RoseDark, modifier = Modifier.size(36.dp))
                            }
                            Text(if (selectedPdf == null) "Ningún PDF seleccionado" else "PDF seleccionado", fontWeight = FontWeight.SemiBold, color = Ink)
                            Text(selectedPdf?.lastPathSegment ?: "Seleccioná un documento PDF desde tu dispositivo.", fontSize = 12.sp, color = Muted)
                            Button(onClick = { pdfPicker.launch(arrayOf("application/pdf")) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)) {
                                Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Elegir PDF")
                            }
                            Button(onClick = {
                                val uri = selectedPdf
                                notice = if (uri == null) "Primero elegí un PDF." else runCatching {
                                    val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: error("No se pudo abrir el PDF.")
                                    descriptor.use { pfd ->
                                        PdfRenderer(pfd).use { renderer ->
                                            if (renderer.pageCount == 0) error("El PDF no contiene páginas.")
                                            renderer.openPage(0).use { page ->
                                                val targetWidth = if ((paperWidth) == 58) 384 else 576
                                                val targetHeight = (page.height * targetWidth.toFloat() / page.width).toInt().coerceAtLeast(1)
                                                val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                                                bitmap.eraseColor(android.graphics.Color.WHITE)
                                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                                                UsbEscPosPrinter.printBitmap(context, bitmap, selected?.paperWidth ?: 80, selected?.feedLines ?: 3)
                                            }
                                        }
                                    }
                                }.getOrElse { "No se pudo imprimir la primera página del PDF: " + (it.message ?: "error desconocido") }
                            }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)) {
                                Icon(Icons.Default.Print, null); Spacer(Modifier.width(8.dp)); Text("Imprimir primera página por USB")
                            }
                            if (pdfPages.isNotEmpty()) {
                                Text("Seleccioná las páginas que querés conservar e imprimir", fontSize = 12.sp, color = Muted)
                                pdfPages.forEachIndexed { index, pageBitmap ->
                                    CardBlock {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(checked = index in selectedPdfPages, onCheckedChange = { checked ->
                                                if (checked && index !in selectedPdfPages) selectedPdfPages.add(index)
                                                if (!checked) selectedPdfPages.remove(index)
                                            })
                                            Text("Página ${index + 1}", fontWeight = FontWeight.SemiBold)
                                        }
                                        androidx.compose.foundation.Image(bitmap = pageBitmap.asImageBitmap(), contentDescription = "Vista previa de página ${index + 1}", modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
                                    }
                                }
                                Text("${selectedPdfPages.size} de ${pdfPages.size} páginas seleccionadas. Se imprimirán una debajo de otra.", fontSize = 11.sp, color = Muted)
                            }
                            Button(onClick = {
                                notice = if (pdfPages.isEmpty()) "Primero elegí un PDF válido." else if (selectedPdfPages.isEmpty()) "Seleccioná al menos una página." else runCatching {
                                    val chosen = selectedPdfPages.sorted().map { pdfPages[it] }
                                    val width = chosen.maxOf { it.width }
                                    val height = chosen.sumOf { it.height }
                                    val combined = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                    combined.eraseColor(android.graphics.Color.WHITE)
                                    val canvas = AndroidCanvas(combined)
                                    var y = 0f
                                    chosen.forEach { page ->
                                        canvas.drawBitmap(page, 0f, y, null)
                                        y += page.height.toFloat()
                                    }
                                    UsbEscPosPrinter.printBitmap(context, combined, paperWidth, selected?.feedLines ?: 3)
                                }.getOrElse { "No se pudo imprimir el PDF: " + (it.message ?: "error desconocido") }
                            }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)) {
                                Icon(Icons.Default.Print, null); Spacer(Modifier.width(8.dp)); Text("Imprimir páginas seleccionadas")
                            }
                            Text("Las páginas seleccionadas se rasterizan en blanco y negro y se imprimen en orden, una debajo de otra.", fontSize = 11.sp, color = Muted)
                        }
                    }
                }
                AppTab.SETTINGS -> {
                    item { SectionHeading("Configuración", "Ajustá el papel y el avance de impresión") }
                    item {
                        CardBlock {
                            Text("Ancho del papel", fontWeight = FontWeight.SemiBold, color = Ink)
                            Text("Seleccioná el tamaño que usás en tu impresora.", color = Muted, fontSize = 12.sp)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = {
                                    paperWidth = (paperWidth - 1).coerceAtLeast(40)
                                    paperWidthText = paperWidth.toString()
                                    prefs.edit().putInt("paper_width", paperWidth).apply()
                                }, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) { Text("−", fontSize = 20.sp) }
                                OutlinedTextField(
                                    value = paperWidthText,
                                    onValueChange = { raw ->
                                        paperWidthText = raw.filter(Char::isDigit).take(3)
                                        paperWidthText.toIntOrNull()?.takeIf { it in 40..120 }?.let { value ->
                                            paperWidth = value
                                            prefs.edit().putInt("paper_width", paperWidth).apply()
                                        }
                                    },
                                    label = { Text("Milímetros") },
                                    suffix = { Text("mm") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedButton(onClick = {
                                    paperWidth = (paperWidth + 1).coerceAtMost(120)
                                    paperWidthText = paperWidth.toString()
                                    prefs.edit().putInt("paper_width", paperWidth).apply()
                                }, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) { Text("+", fontSize = 20.sp) }
                            }
                            Text("Ancho editable de 40 a 120 mm. Valor predeterminado: 58 mm.", fontSize = 11.sp, color = Muted)
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Avance al final", fontWeight = FontWeight.SemiBold, color = Ink)
                                    Text("Líneas en blanco después del ticket", color = Muted, fontSize = 12.sp)
                                }
                                Text("${selected?.feedLines ?: 3}", color = RoseDark, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                            Slider(
                                value = (selected?.feedLines ?: 3).toFloat(),
                                onValueChange = { value -> selected?.let { t -> updateTemplate(templates, t.copy(feedLines = value.toInt()), ::persist) } },
                                valueRange = 0f..8f,
                                steps = 7
                            )
                        }
                    }
                    item {
                        CardBlock {
                            Text("Impresora", fontWeight = FontWeight.SemiBold, color = Ink)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(Rose))
                                Spacer(Modifier.width(9.dp))
                                Column {
                                    Text("3nSTAR RPT001", fontWeight = FontWeight.Medium, color = Ink)
                                    Text("USB · ESC/POS", fontSize = 12.sp, color = Muted)
                                }
                            }
                            Text("Conectá la impresora con un adaptador OTG compatible y aceptá el permiso USB cuando Android lo solicite.", fontSize = 12.sp, color = Muted)
                        }
                    }
                }
            }
        }
    }

    deleteCandidate?.let { template ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Eliminar plantilla") },
            text = { Text("¿Querés eliminar «${template.name}»? Esta acción no se puede deshacer.") },
            confirmButton = {
                Button(onClick = {
                    templates.removeAll { it.id == template.id }
                    if (templates.isEmpty()) templates.add(TicketTemplate(name = "Ticket de ejemplo"))
                    if (selectedId == template.id) selectedId = templates.first().id
                    persist(); deleteCandidate = null
                }) { Text("Eliminar") }
            },
            dismissButton = { OutlinedButton(onClick = { deleteCandidate = null }) { Text("Cancelar") } }
        )
    }
    notice?.let { message ->
        AlertDialog(onDismissRequest = { notice = null }, title = { Text("Impresión USB") }, text = { Text(message) },
            confirmButton = { Button(onClick = { notice = null }) { Text("Aceptar") } })
    }
}

private fun updateTemplate(templates: MutableList<TicketTemplate>, updated: TicketTemplate, persist: () -> Unit) {
    val index = templates.indexOfFirst { it.id == updated.id }
    if (index >= 0) { templates[index] = updated; persist() }
}

private fun loadMonochromeImage(context: Context, uri: Uri, rotation: Int): Bitmap {
    val source = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        ?: error("No se pudo abrir la imagen.")
    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    val result = Bitmap.createBitmap(rotated.width, rotated.height, Bitmap.Config.ARGB_8888)
    for (y in 0 until rotated.height) for (x in 0 until rotated.width) {
        val p = rotated.getPixel(x, y)
        val l = (android.graphics.Color.red(p) * 299 + android.graphics.Color.green(p) * 587 + android.graphics.Color.blue(p) * 114) / 1000
        result.setPixel(x, y, if (l < 160) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    }
    return result
}

private fun renderPdfPages(context: Context, uri: Uri, paperWidth: Int): List<Bitmap> {
    val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: error("No se pudo abrir el PDF.")
    descriptor.use { pfd ->
        PdfRenderer(pfd).use { renderer ->
            val targetWidth = if (paperWidth <= 58) 384 else 576
            return (0 until renderer.pageCount).map { index ->
                renderer.openPage(index).use { page ->
                    val targetHeight = (page.height * targetWidth.toFloat() / page.width).toInt().coerceAtLeast(1)
                    val color = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                    color.eraseColor(android.graphics.Color.WHITE)
                    page.render(color, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                    val mono = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                    for (y in 0 until targetHeight) for (x in 0 until targetWidth) {
                        val p = color.getPixel(x, y)
                        val l = (android.graphics.Color.red(p) * 299 + android.graphics.Color.green(p) * 587 + android.graphics.Color.blue(p) * 114) / 1000
                        mono.setPixel(x, y, if (l < 160) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                    }
                    mono
                }
            }
        }
    }
}

@Composable
private fun TicketTableEditor(template: TicketTemplate, onContentChange: (String) -> Unit) {
    fun parse(value: String): List<List<String>> =
        value.split("\n").map { it.split("\t") }.ifEmpty { listOf(listOf("")) }
    var grid by remember(template.id) { mutableStateOf(parse(template.content)) }
    val selectedCells = remember(template.id) { mutableStateListOf<Pair<Int, Int>>() }
    var showSplitDialog by remember { mutableStateOf(false) }
    var splitRows by remember { mutableStateOf("2") }
    var splitColumns by remember { mutableStateOf("2") }

    fun publish(next: List<List<String>>) {
        val count = next.maxOfOrNull { it.size } ?: 1
        val normalized = next.map { row -> (0 until count).map { row.getOrElse(it) { "" } } }
        grid = normalized
        onContentChange(normalized.joinToString("\n") { it.joinToString("\t") })
    }
    fun toggleCell(row: Int, col: Int) {
        val key = row to col
        if (selectedCells.isEmpty()) selectedCells.add(key)
        else if (key in selectedCells) selectedCells.remove(key) else selectedCells.add(key)
    }
    val columnCount = grid.maxOfOrNull { it.size } ?: 1
    val selectedRows = (0 until grid.size).filter { row -> (0 until columnCount).all { (row to it) in selectedCells } }
    val selectedColumns = (0 until columnCount).filter { col -> (0 until grid.size).all { (it to col) in selectedCells } }
    val horizontalPair = selectedCells.size == 2 && selectedCells[0].first == selectedCells[1].first &&
        kotlin.math.abs(selectedCells[0].second - selectedCells[1].second) == 1
    val verticalPair = selectedCells.size == 2 && selectedCells[0].second == selectedCells[1].second &&
        kotlin.math.abs(selectedCells[0].first - selectedCells[1].first) == 1

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = {
            val insertAt = (selectedRows.maxOrNull() ?: (grid.size - 1)) + 1
            val next = grid.toMutableList()
            next.add(insertAt.coerceIn(0, next.size), List(columnCount) { "" })
            publish(next); selectedCells.clear()
        }, enabled = selectedRows.isNotEmpty(), modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text("+ Fila", fontSize = 12.sp) }
        OutlinedButton(onClick = {
            if (selectedRows.isNotEmpty() && selectedRows.size < grid.size) {
                publish(grid.filterIndexed { index, _ -> index !in selectedRows }); selectedCells.clear()
            }
        }, enabled = selectedRows.isNotEmpty() && selectedRows.size < grid.size, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text("− Fila", fontSize = 12.sp) }
        OutlinedButton(onClick = {
            val insertAt = (selectedColumns.maxOrNull() ?: (columnCount - 1)) + 1
            publish(grid.map { row -> row.toMutableList().apply { add(insertAt.coerceIn(0, size), "") } }); selectedCells.clear()
        }, enabled = selectedColumns.isNotEmpty(), modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text("+ Col.", fontSize = 12.sp) }
        OutlinedButton(onClick = {
            if (selectedColumns.isNotEmpty() && selectedColumns.size < columnCount) {
                publish(grid.map { row -> row.filterIndexed { index, _ -> index !in selectedColumns } }); selectedCells.clear()
            }
        }, enabled = selectedColumns.isNotEmpty() && selectedColumns.size < columnCount, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text("− Col.", fontSize = 12.sp) }
    }
    if (selectedCells.size == 1) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showSplitDialog = true }, modifier = Modifier.weight(1f)) { Text("Dividir celda") }
        }
    }
    if (horizontalPair || verticalPair) {
        OutlinedButton(onClick = {
            val first = selectedCells[0]
            val second = selectedCells[1]
            val topLeft = if (first.first < second.first || (first.first == second.first && first.second < second.second)) first else second
            val next = grid.map { it.toMutableList() }.toMutableList()
            val a = next[topLeft.first][topLeft.second]
            val b = if (first == topLeft) next[second.first][second.second] else next[first.first][first.second]
            next[topLeft.first][topLeft.second] = listOf(a, b).filter { it.isNotBlank() }.joinToString(" ")
            if (horizontalPair) {
                val row = next[topLeft.first]
                row.removeAt(maxOf(first.second, second.second))
            } else {
                next[maxOf(first.first, second.first)][first.second] = ""
            }
            publish(next); selectedCells.clear()
        }, modifier = Modifier.fillMaxWidth()) { Text("Combinar celdas seleccionadas") }
    }
    Text("Mantené presionada una celda para seleccionarla. Tocá otras celdas para sumar o quitar selección.", fontSize = 11.sp, color = Muted)
    Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        grid.forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEachIndexed { colIndex, text ->
                    val selected = (rowIndex to colIndex) in selectedCells
                    var editValue by remember(template.id, rowIndex, colIndex, text) {
                        mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(text))
                    }
                    Column(
                        Modifier.widthIn(min = 110.dp).weight(1f, fill = true)
                            .border(if (selected) 2.dp else 1.dp, if (selected) RoseDark else Line, RoundedCornerShape(8.dp))
                            .background(if (selected) RosePale else Color.White, RoundedCornerShape(8.dp))
                            .pointerInput(rowIndex, colIndex, selectedCells.size) {
                                androidx.compose.foundation.gestures.detectTapGestures(
                                    onLongPress = { toggleCell(rowIndex, colIndex) },
                                    onTap = { if (selectedCells.isNotEmpty()) toggleCell(rowIndex, colIndex) },
                                    onDoubleTap = { editValue = androidx.compose.ui.text.input.TextFieldValue(text, androidx.compose.ui.text.TextRange(0, text.length)) }
                                )
                            }
                            .padding(5.dp)
                    ) {
                        androidx.compose.foundation.text.BasicTextField(
                            value = editValue,
                            onValueChange = { newValue ->
                                editValue = newValue
                                val next = grid.map { it.toMutableList() }.toMutableList()
                                next[rowIndex][colIndex] = newValue.text
                                publish(next)
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = Ink,
                                fontSize = (24 - text.length / 4).coerceIn(10, 24).sp,
                                fontWeight = if (rowIndex == 0) FontWeight.SemiBold else FontWeight.Normal,
                                textAlign = TextAlign.Center
                            ),
                            decorationBox = { inner ->
                                Box(Modifier.fillMaxWidth().heightIn(min = 44.dp), contentAlignment = Alignment.Center) {
                                    if (editValue.text.isEmpty()) Text("Escribí aquí", color = Muted, fontSize = 11.sp)
                                    inner()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
    if (showSplitDialog) {
        AlertDialog(
            onDismissRequest = { showSplitDialog = false },
            title = { Text("Dividir celda") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = splitRows, onValueChange = { splitRows = it.filter(Char::isDigit).take(2) }, label = { Text("Cantidad de filas") }, singleLine = true)
                    OutlinedTextField(value = splitColumns, onValueChange = { splitColumns = it.filter(Char::isDigit).take(2) }, label = { Text("Cantidad de columnas") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val cell = selectedCells.single()
                    val rCount = (splitRows.toIntOrNull() ?: 2).coerceIn(1, 10)
                    val cCount = (splitColumns.toIntOrNull() ?: 2).coerceIn(1, 10)
                    val next = grid.map { it.toMutableList() }.toMutableList()
                    val original = next[cell.first][cell.second]
                    next[cell.first][cell.second] = original
                    repeat(cCount - 1) { next[cell.first].add(cell.second + 1, "") }
                    repeat(rCount - 1) { offset ->
                        val row = MutableList(next.maxOfOrNull { it.size } ?: cCount) { "" }
                        next.add(cell.first + offset + 1, row)
                    }
                    publish(next); selectedCells.clear(); showSplitDialog = false
                }, enabled = (splitRows.toIntOrNull() ?: 0) in 1..10 && (splitColumns.toIntOrNull() ?: 0) in 1..10) { Text("Dividir") }
            },
            dismissButton = { OutlinedButton(onClick = { showSplitDialog = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun TicketContentPreview(content: String) {
    val rows = remember(content) { content.split("\n").map { it.split("\t") } }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        rows.forEachIndexed { ri, cells ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                cells.forEach { cell ->
                    Box(Modifier.weight(1f).border(0.5.dp, Color(0xFFE9D6DC)).padding(horizontal = 4.dp, vertical = 5.dp)) {
                        Text(cell, fontFamily = FontFamily.Monospace, fontSize = (24 - cell.length / 4).coerceIn(10, 24).sp, lineHeight = (28 - cell.length / 4).coerceIn(12, 28).sp, color = Ink, maxLines = 3)
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeCard() {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = RosePale)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("TU NEGOCIO, A TU ESTILO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = RoseDark, letterSpacing = 1.2.sp)
                Text("Tickets lindos,\nlistos para imprimir.", fontSize = 23.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text("Creá diseños para Nash Pastelería & Café.", fontSize = 12.sp, color = Muted)
            }
            Box(Modifier.size(72.dp).clip(RoundedCornerShape(22.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.ReceiptLong, null, tint = RoseDark, modifier = Modifier.size(38.dp))
            }
        }
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text(subtitle, fontSize = 12.sp, color = Muted)
    }
}

@Composable
private fun CardBlock(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
private fun WidthChip(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick, shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 15.dp, vertical = 9.dp)) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 15.dp, vertical = 9.dp)) { Text(label) }
    }
}

