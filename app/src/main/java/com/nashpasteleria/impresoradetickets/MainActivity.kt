package com.nashpasteleria.impresoradetickets

import android.content.Context
import android.net.Uri
import android.os.Bundle
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
    val content: String = "NASH Pastelería & Café\n--------------------------------\nProducto       Cant.     Precio\n--------------------------------\nGracias por tu compra!",
    val paperWidth: Int = 80,
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
                        content = o.optString("content", ""),
                        paperWidth = o.optInt("paperWidth", 80),
                        feedLines = o.optInt("feedLines", 3),
                        cutAfterPrint = o.optBoolean("cutAfterPrint", true)
                    ))
                }
            }
            if (isEmpty()) add(TicketTemplate(name = "Ticket de ejemplo"))
        }
    }
    var selectedId by remember { mutableStateOf(templates.first().id) }
    var editing by remember { mutableStateOf<TicketTemplate?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var deleteCandidate by remember { mutableStateOf<TicketTemplate?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var activeTab by remember { mutableStateOf(AppTab.DESIGN) }
    var selectedImage by remember { mutableStateOf<Uri?>(null) }
    var selectedPdf by remember { mutableStateOf<Uri?>(null) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { selectedImage = it }
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { selectedPdf = it }
    val selected = templates.firstOrNull { it.id == selectedId } ?: templates.firstOrNull()

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
                    item { WelcomeCard() }
                    item { SectionHeading("Tu diseño", "Elegí una plantilla y prepará el ticket") }
                    item {
                        CardBlock {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(selected?.name ?: "Sin plantilla", fontWeight = FontWeight.SemiBold, color = Ink)
                                    Text("${selected?.paperWidth ?: 80} mm · ${selected?.content?.lines()?.size ?: 0} líneas", color = Muted, fontSize = 12.sp)
                                }
                                OutlinedButton(onClick = { activeTab = AppTab.TEMPLATES }, shape = RoundedCornerShape(13.dp)) { Text("Cambiar") }
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                WidthChip("58 mm", selected?.paperWidth == 58) {
                                    selected?.let { updateTemplate(templates, it.copy(paperWidth = 58), ::persist) }
                                }
                                WidthChip("80 mm", selected?.paperWidth != 58) {
                                    selected?.let { updateTemplate(templates, it.copy(paperWidth = 80), ::persist) }
                                }
                                Spacer(Modifier.weight(1f))
                                OutlinedButton(onClick = { editing = selected; showEditor = true }, shape = RoundedCornerShape(13.dp)) {
                                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(5.dp)); Text("Editar")
                                }
                            }
                        }
                    }
                    item { SectionHeading("Vista previa", "Así se verá el contenido impreso") }
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().border(1.dp, Line, RoundedCornerShape(20.dp)),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("VISTA PREVIA DEL TICKET", fontSize = 10.sp, color = Muted, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                                Spacer(Modifier.height(12.dp))
                                Box(Modifier.fillMaxWidth().background(Color(0xFFFFFCFC), RoundedCornerShape(12.dp)).padding(14.dp)) {
                                    Text(selected?.content ?: "Creá una plantilla para empezar", fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 18.sp, color = Ink)
                                }
                                Spacer(Modifier.height(8.dp))
                                Text("Papel térmico · ${selected?.paperWidth ?: 80} mm", fontSize = 11.sp, color = Muted)
                            }
                        }
                    }
                    item {
                        Button(
                            onClick = { notice = selected?.let { UsbEscPosPrinter.print(context, it) } ?: "Primero creá o seleccioná una plantilla." },
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
                        Button(onClick = { editing = null; showEditor = true }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(15.dp)) {
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
                                    Text("${template.paperWidth} mm · ${template.content.lines().size} líneas", color = Muted, fontSize = 12.sp)
                                }
                                IconButton(onClick = { selectedId = template.id; editing = template; showEditor = true }) {
                                    Icon(Icons.Default.Edit, "Editar plantilla", tint = RoseDark)
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
                            Box(Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(16.dp)).background(RosePale), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Image, null, tint = RoseDark, modifier = Modifier.size(46.dp))
                            }
                            Text(if (selectedImage == null) "Todavía no elegiste una imagen" else "Imagen seleccionada", fontWeight = FontWeight.SemiBold, color = Ink)
                            Text(selectedImage?.lastPathSegment ?: "Elegí una imagen desde tu dispositivo.", fontSize = 12.sp, color = Muted)
                            Button(onClick = { imagePicker.launch("image/*") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)) {
                                Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Elegir imagen")
                            }
                            Text("La selección de archivos funciona; incorporarlos al ticket impreso queda para una próxima mejora.", fontSize = 11.sp, color = Muted)
                        }
                    }
                }
                AppTab.PDF -> {
                    item { SectionHeading("Documentos PDF", "Elegí un archivo para tenerlo a mano") }
                    item {
                        CardBlock {
                            Box(Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(16.dp)).background(RosePale), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.PictureAsPdf, null, tint = RoseDark, modifier = Modifier.size(46.dp))
                            }
                            Text(if (selectedPdf == null) "Ningún PDF seleccionado" else "PDF seleccionado", fontWeight = FontWeight.SemiBold, color = Ink)
                            Text(selectedPdf?.lastPathSegment ?: "Seleccioná un documento PDF desde tu dispositivo.", fontSize = 12.sp, color = Muted)
                            Button(onClick = { pdfPicker.launch(arrayOf("application/pdf")) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)) {
                                Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Elegir PDF")
                            }
                            Text("La conversión del PDF a contenido imprimible queda para una próxima mejora.", fontSize = 11.sp, color = Muted)
                        }
                    }
                }
                AppTab.SETTINGS -> {
                    item { SectionHeading("Configuración", "Ajustá el papel y el avance de impresión") }
                    item {
                        CardBlock {
                            Text("Ancho del papel", fontWeight = FontWeight.SemiBold, color = Ink)
                            Text("Seleccioná el tamaño que usás en tu impresora.", color = Muted, fontSize = 12.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                WidthChip("58 mm", selected?.paperWidth == 58) { selected?.let { t -> updateTemplate(templates, t.copy(paperWidth = 58), ::persist) } }
                                WidthChip("80 mm", selected?.paperWidth != 58) { selected?.let { t -> updateTemplate(templates, t.copy(paperWidth = 80), ::persist) } }
                            }
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

    if (showEditor) {
        TemplateEditor(initial = editing, onDismiss = { showEditor = false }) { name, content, width ->
            val old = editing
            if (old == null) {
                val created = TicketTemplate(name = name, content = content, paperWidth = width)
                templates.add(created); selectedId = created.id
            } else {
                val index = templates.indexOfFirst { it.id == old.id }
                if (index >= 0) templates[index] = old.copy(name = name, content = content, paperWidth = width)
                selectedId = old.id
            }
            persist(); showEditor = false
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

@Composable
private fun TemplateEditor(initial: TicketTemplate?, onDismiss: () -> Unit, onSave: (String, String, Int) -> Unit) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name ?: "") }
    var width by remember(initial?.id) { mutableIntStateOf(initial?.paperWidth ?: 80) }
    fun parse(value: String): List<List<String>> = value.split("\n").map { it.split("\t") }.ifEmpty { listOf(listOf("")) }
    val cells = remember(initial?.id) { mutableStateListOf<MutableList<String>>().apply {
        parse(initial?.content ?: "NASH Pastelería & Café\nGracias por tu compra!").forEach { add(it.toMutableList()) }
    } }
    var activeRow by remember(initial?.id) { mutableIntStateOf(0) }
    var activeCol by remember(initial?.id) { mutableIntStateOf(0) }
    var merged by remember(initial?.id) { mutableStateOf(false) }
    var columnWidth by remember(initial?.id) { mutableIntStateOf(12) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nueva plantilla" else "Diseñar plantilla") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Tabla del ticket", fontWeight = FontWeight.Bold, color = RoseDark)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(onClick = { cells.add(MutableList(cells.maxOfOrNull { it.size } ?: 1) { "" }); activeRow = cells.lastIndex }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text("+ Fila") }
                    OutlinedButton(onClick = { cells.forEach { it.add("") }; activeCol = (cells.maxOfOrNull { it.size } ?: 1) - 1 }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text("+ Columna") }
                    OutlinedButton(onClick = { if (cells.size > 1) { cells.removeAt(cells.lastIndex); activeRow = activeRow.coerceAtMost(cells.lastIndex) } }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text("− Fila") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(onClick = { if ((cells.maxOfOrNull { it.size } ?: 1) > 1) cells.forEach { if (it.size > 1) it.removeAt(it.lastIndex) }; activeCol = activeCol.coerceAtMost((cells.maxOfOrNull { it.size } ?: 1) - 1) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text("− Columna") }
                    OutlinedButton(onClick = { val row = cells.getOrNull(activeRow); if (row != null && activeCol < row.lastIndex) { row[activeCol] = listOf(row[activeCol], row[activeCol+1]).filter { it.isNotBlank() }.joinToString(" "); row.removeAt(activeCol+1); merged = true } }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text("Combinar →") }
                    OutlinedButton(onClick = { val row = cells.getOrNull(activeRow); if (row != null && merged) { row.add((activeCol+1).coerceAtMost(row.size), ""); merged = false } }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text("Dividir") }
                }
                Text("Tocá una celda para seleccionarla", fontSize = 12.sp, color = Muted)
                Column(Modifier.fillMaxWidth().heightIn(max = 150.dp).background(Color(0xFFFFFCFC), RoundedCornerShape(10.dp)).padding(4.dp)) {
                    cells.forEachIndexed { ri, row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            row.forEachIndexed { ci, cell ->
                                OutlinedButton(onClick = { activeRow = ri; activeCol = ci }, modifier = Modifier.weight(1f).heightIn(min = 34.dp), contentPadding = PaddingValues(2.dp), colors = ButtonDefaults.outlinedButtonColors(containerColor = if (activeRow == ri && activeCol == ci) RosePale else Color.White)) {
                                    Text(cell.ifBlank { "·" }, maxLines = 2, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
                val current = cells.getOrNull(activeRow)?.getOrNull(activeCol) ?: ""
                OutlinedTextField(value = current, onValueChange = { v -> cells.getOrNull(activeRow)?.let { row -> if (activeCol in row.indices) row[activeCol] = v } }, label = { Text("Editar celda") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                Text("Ancho de columna: " + columnWidth + " caracteres", fontSize = 12.sp, color = Muted)
                Slider(value = columnWidth.toFloat(), onValueChange = { columnWidth = it.toInt() }, valueRange = 4f..32f, steps = 27)
                Text("Ancho del papel", fontSize = 12.sp, color = Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WidthChip("58 mm", width == 58) { width = 58 }
                    WidthChip("80 mm", width == 80) { width = 80 }
                }
            }
        },
        confirmButton = { Button(onClick = {
            val count = cells.maxOfOrNull { it.size } ?: 1
            val output = cells.map { row -> (0 until count).joinToString("\t") { row.getOrElse(it) { "" } } }.joinToString("\n")
            onSave(name.trim().ifBlank { "Plantilla sin nombre" }, output, width)
        }) { Text("Guardar cambios") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
