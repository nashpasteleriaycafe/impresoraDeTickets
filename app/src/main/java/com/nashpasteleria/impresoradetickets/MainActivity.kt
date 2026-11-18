package com.nashpasteleria.impresoradetickets

import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.JavascriptInterface
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
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
    val content: String = "",
    val paperWidth: Int = 58,
    val feedLines: Int = 3,
    val cutAfterPrint: Boolean = true,
    val designMode: String = "Ajuste automático", val fontSize: Int = 24, val fontFamily: String = "Sans Serif",
    val alignment: String = "Izquierda", val bold: Boolean = false, val underline: Boolean = false, val italic: Boolean = false
)

private enum class AppTab(val title: String) {
    DESIGN("Diseño"), TEMPLATES("Plantillas"), IMAGES("Imágenes"), PDF("PDF"), SETTINGS("Ajustes")
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
                        name = o.optString("name", "Plantilla").let { savedName ->
                            if (savedName == "Ticket de ejemplo") "Plantilla en blanco" else savedName
                        },
                        content = o.optString("content", "").let { saved ->
                            val wasDefaultExample = o.optString("name") == "Ticket de ejemplo" &&
                                (saved.contains("Nombre del producto") || saved.contains("NASH Pastelería"))
                            if (wasDefaultExample) "" else saved
                        },
                        paperWidth = o.optInt("paperWidth", 58),
                        feedLines = o.optInt("feedLines", 3),
                        cutAfterPrint = o.optBoolean("cutAfterPrint", true),
                        designMode = o.optString("designMode", "Ajuste automático"), fontSize = o.optInt("fontSize", 24), fontFamily = o.optString("fontFamily", "Sans Serif"),
                        alignment = o.optString("alignment", "Izquierda"), bold = o.optBoolean("bold", false), underline = o.optBoolean("underline", false), italic = o.optBoolean("italic", false)
                    ))
                }
            }
            if (isEmpty()) {
                add(TicketTemplate(name = "Plantilla en blanco", content = ""))
            } else if (none { it.name == "Plantilla en blanco" && it.content.isBlank() }) {
                // Always open a fresh, empty ticket on launch without replacing saved user templates.
                add(0, TicketTemplate(name = "Plantilla en blanco", content = ""))
            }
        }
    }
    var selectedId by remember { mutableStateOf(templates.first().id) }
    var deleteCandidate by remember { mutableStateOf<TicketTemplate?>(null) }
    var showSaveTemplateDialog by remember { mutableStateOf(false) }
    var newTemplateName by remember { mutableStateOf("") }
    var pdfSelectionText by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf<String?>(null) }
    var activeTab by remember { mutableStateOf(AppTab.DESIGN) }
    var paperWidth by remember { mutableIntStateOf(prefs.getInt("paper_width", 58).coerceIn(40, 120)) }
    var paperWidthText by remember { mutableStateOf(paperWidth.toString()) }
    var selectedImage by remember { mutableStateOf<Uri?>(null) }
    var selectedPdf by remember { mutableStateOf<Uri?>(null) }
    var imageRotation by remember { mutableIntStateOf(0) }
    var imageBrightness by remember { mutableFloatStateOf(0f) }
    var imageContrast by remember { mutableFloatStateOf(100f) }
    var brightnessControl by remember { mutableFloatStateOf(0f) }
    var contrastControl by remember { mutableFloatStateOf(100f) }
    var imageLoading by remember { mutableStateOf(false) }
    var pdfLoading by remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val previewHeight = (configuration.screenHeightDp.dp - if (isLandscape) 470.dp else 520.dp).coerceAtLeast(120.dp)
    var imagePreview by remember { mutableStateOf<Bitmap?>(null) }
    var pdfPages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    val selectedPdfPages = remember { mutableStateListOf<Int>() }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) { selectedImage = uri; imageRotation = 0 } }
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) selectedPdf = uri }
    val selected = templates.firstOrNull { it.id == selectedId } ?: templates.firstOrNull()
    val ticketPreviewScale = minOf(if (paperWidth <= 58) 240 else 320, configuration.screenWidthDp - 32).coerceAtLeast(180).toFloat() / (if (paperWidth <= 58) 384f else 576f)
    var designValue by remember(selected?.id) { mutableStateOf(TextFieldValue(selected?.content?.let { ticketContentToAnnotatedString(it, ticketPreviewScale) } ?: AnnotatedString(""))) }
    val designText = designValue.text
    var designMode by remember(selected?.id) { mutableStateOf(selected?.designMode ?: "Ajuste automático") }
    var traditionalFontSize by remember(selected?.id) { mutableIntStateOf(selected?.fontSize ?: 24) }
    var traditionalFont by remember(selected?.id) { mutableStateOf(selected?.fontFamily ?: "Sans Serif") }
    var traditionalAlignment by remember(selected?.id) { mutableStateOf(selected?.alignment ?: "Izquierda") }
    var traditionalBold by remember(selected?.id) { mutableStateOf(selected?.bold ?: false) }
    var traditionalUnderline by remember(selected?.id) { mutableStateOf(selected?.underline ?: false) }
    var traditionalItalic by remember(selected?.id) { mutableStateOf(selected?.italic ?: false) }

    fun selectedLineRanges(): List<Pair<Int, Int>> {
        val text = designValue.text
        if (text.isEmpty()) return emptyList()
        val selection = designValue.selection
        val first = selection.min.coerceIn(0, text.length)
        val last = (if (selection.collapsed) first else (selection.max - 1).coerceAtLeast(first)).coerceIn(0, text.length)
        val ranges = mutableListOf<Pair<Int, Int>>()
        var lineStart = if (first == 0) 0 else text.lastIndexOf('\n', first - 1) + 1
        while (lineStart <= last) {
            val nextBreak = text.indexOf('\n', lineStart)
            val lineEnd = if (nextBreak < 0) text.length else nextBreak
            if (lineStart < lineEnd) ranges.add(lineStart to lineEnd)
            if (nextBreak < 0 || nextBreak >= last) break
            lineStart = nextBreak + 1
        }
        return ranges
    }

    fun applyLineFormatting(kind: String, value: String) {
        val annotated = designValue.annotatedString
        val ranges = selectedLineRanges()
        if (ranges.isEmpty()) return
        val builder = AnnotatedString.Builder(annotated)
        when (kind) {
            "alignment" -> {
                val alignment = when (value) {
                    "Centro" -> TextAlign.Center
                    "Derecha" -> TextAlign.End
                    else -> TextAlign.Start
                }
                ranges.forEach { range ->
                    val paragraphEnd = if (range.second < annotated.length) range.second + 1 else range.second
                    builder.addStyle(ParagraphStyle(textAlign = alignment), range.first, paragraphEnd)
                }
                traditionalAlignment = value
            }
            "size" -> ranges.forEach { range ->
                builder.addStyle(SpanStyle(fontSize = (traditionalFontSize * ticketPreviewScale).sp), range.first, range.second)
            }
            "font" -> {
                val family = when (value) { "Serif" -> FontFamily.Serif; "Monospace" -> FontFamily.Monospace; else -> FontFamily.SansSerif }
                ranges.forEach { range -> builder.addStyle(SpanStyle(fontFamily = family), range.first, range.second) }
            }
        }
        designValue = TextFieldValue(builder.toAnnotatedString(), designValue.selection)
    }

    fun applyFormatting(kind: String) {
        val annotated = designValue.annotatedString
        val text = annotated.text
        if (text.isEmpty()) return
        val selection = designValue.selection
        var start = selection.min
        var end = selection.max
        if (start == end) {
            start = start.coerceIn(0, text.length)
            end = start
            while (start > 0 && !text[start - 1].isWhitespace()) start--
            while (end < text.length && !text[end].isWhitespace()) end++
            if (start == end) return
        }
        val builder = AnnotatedString.Builder(annotated)
        val existing = annotated.spanStyles.filter { it.start < end && it.end > start }
        val selectedSpan = existing.lastOrNull { it.start <= start && it.end >= end }?.item ?: SpanStyle()
        val style = when (kind) {
            "bold" -> selectedSpan.copy(fontWeight = if (selectedSpan.fontWeight == FontWeight.Bold) FontWeight.Normal else FontWeight.Bold)
            "italic" -> selectedSpan.copy(fontStyle = if (selectedSpan.fontStyle == androidx.compose.ui.text.font.FontStyle.Italic) androidx.compose.ui.text.font.FontStyle.Normal else androidx.compose.ui.text.font.FontStyle.Italic)
            "underline" -> selectedSpan.copy(textDecoration = if (selectedSpan.textDecoration == androidx.compose.ui.text.style.TextDecoration.Underline) androidx.compose.ui.text.style.TextDecoration.None else androidx.compose.ui.text.style.TextDecoration.Underline)
            else -> selectedSpan
        }
        builder.addStyle(style, start, end)
        designValue = TextFieldValue(builder.toAnnotatedString(), TextRange(start, end))
    }

    LaunchedEffect(selectedImage, imageRotation, imageBrightness, imageContrast) {
        val uri = selectedImage
        if (uri == null) { imagePreview = null; imageLoading = false } else {
            imageLoading = true
            imagePreview = null
            try { imagePreview = withContext(Dispatchers.IO) { loadMonochromeImage(context, uri, imageRotation, imageBrightness, imageContrast) } }
            catch (_: Exception) { notice = "No se pudo cargar la imagen. Probá con otro archivo."; imagePreview = null }
            finally { imageLoading = false }
        }
    }
    LaunchedEffect(selectedPdf, paperWidth) {
        val uri = selectedPdf
        if (uri == null) { pdfPages = emptyList(); pdfLoading = false } else {
            pdfLoading = true
            pdfPages = emptyList()
            try { pdfPages = withContext(Dispatchers.IO) { renderPdfPages(context, uri, paperWidth) } }
            catch (_: Exception) { notice = "No se pudo cargar el PDF. Probá con otro archivo."; pdfPages = emptyList() }
            finally {
                selectedPdfPages.clear()
                selectedPdfPages.addAll(pdfPages.indices.toList())
                pdfSelectionText = formatPageRanges(selectedPdfPages.toList())
                pdfLoading = false
            }
        }
    }

    fun persist() {
        val arr = JSONArray()
        templates.forEach { t ->
            arr.put(JSONObject().apply {
                put("id", t.id); put("name", t.name); put("content", t.content)
                put("paperWidth", t.paperWidth); put("feedLines", t.feedLines); put("cutAfterPrint", t.cutAfterPrint)
                put("designMode", t.designMode); put("fontSize", t.fontSize); put("fontFamily", t.fontFamily); put("alignment", t.alignment)
                put("bold", t.bold); put("underline", t.underline); put("italic", t.italic)
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
        if (activeTab == AppTab.DESIGN) {
            val template = selected ?: templates.first()
            val traditional = designMode == "Tradicional"
            Column(Modifier.fillMaxSize().padding(insets).padding(horizontal = 10.dp).padding(top = 4.dp, bottom = 2.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("Diseño", fontWeight = FontWeight.SemiBold, color = Ink, fontSize = 13.sp)
                    Spacer(Modifier.weight(1f))
                    FilterChip(selected = !traditional, onClick = { designMode = "Ajuste automático" }, label = { Text("Automático", fontSize = 10.sp) })
                    FilterChip(selected = traditional, onClick = { designMode = "Tradicional" }, label = { Text("Tradicional", fontSize = 10.sp) })
                }
                if (traditional) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                        var sizeMenu by remember { mutableStateOf(false) }
                        Box {
                            TextButton(onClick = { sizeMenu = true }, contentPadding = PaddingValues(horizontal = 5.dp, vertical = 2.dp)) {
                                Text("${traditionalFontSize}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Tamaño de fuente", modifier = Modifier.size(18.dp))
                            }
                            DropdownMenu(expanded = sizeMenu, onDismissRequest = { sizeMenu = false }) {
                                listOf(10, 12, 14, 16, 18, 20, 22, 24, 28, 32, 36, 40).forEach { size ->
                                    DropdownMenuItem(text = { Text("${size} sp") }, onClick = { traditionalFontSize = size; applyLineFormatting("size", size.toString()); sizeMenu = false })
                                }
                            }
                        }
                        var fontMenu by remember { mutableStateOf(false) }
                        Box {
                            TextButton(onClick = { fontMenu = true }, contentPadding = PaddingValues(horizontal = 5.dp, vertical = 2.dp)) {
                                Text(when (traditionalFont) { "Serif" -> "Serif"; "Monospace" -> "Mono"; else -> "Sans" }, fontSize = 12.sp)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Estilo de fuente", modifier = Modifier.size(18.dp))
                            }
                            DropdownMenu(expanded = fontMenu, onDismissRequest = { fontMenu = false }) {
                                listOf("Sans Serif", "Serif", "Monospace").forEach { font ->
                                    DropdownMenuItem(text = { Text(font) }, onClick = { traditionalFont = font; applyLineFormatting("font", font); fontMenu = false })
                                }
                            }
                        }
                        VerticalDivider(Modifier.height(26.dp), color = Line)
                        IconButton(onClick = { applyLineFormatting("alignment", "Izquierda") }, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.FormatAlignLeft, "Alinear a la izquierda", tint = if (traditionalAlignment == "Izquierda") RoseDark else Ink, modifier = Modifier.size(20.dp)) }
                        IconButton(onClick = { applyLineFormatting("alignment", "Centro") }, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.FormatAlignCenter, "Centrar", tint = if (traditionalAlignment == "Centro") RoseDark else Ink, modifier = Modifier.size(20.dp)) }
                        IconButton(onClick = { applyLineFormatting("alignment", "Derecha") }, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.FormatAlignRight, "Alinear a la derecha", tint = if (traditionalAlignment == "Derecha") RoseDark else Ink, modifier = Modifier.size(20.dp)) }
                        VerticalDivider(Modifier.height(26.dp), color = Line)
                        IconButton(onClick = { applyFormatting("bold") }, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.FormatBold, "Negrita", tint = Ink, modifier = Modifier.size(20.dp)) }
                        IconButton(onClick = { applyFormatting("italic") }, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.FormatItalic, "Cursiva", tint = Ink, modifier = Modifier.size(20.dp)) }
                        IconButton(onClick = { applyFormatting("underline") }, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.FormatUnderlined, "Subrayado", tint = Ink, modifier = Modifier.size(20.dp)) }
                        VerticalDivider(Modifier.height(26.dp), color = Line)
                        IconButton(onClick = {
                            val updated = template.copy(content = serializeRichText(designValue.annotatedString, ticketPreviewScale), designMode = designMode, fontSize = traditionalFontSize, fontFamily = traditionalFont, alignment = traditionalAlignment, paperWidth = paperWidth)
                            updateTemplate(templates, updated, ::persist)
                            notice = UsbEscPosPrinter.print(context, updated)
                        }, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Print, "Imprimir", tint = RoseDark, modifier = Modifier.size(20.dp)) }
                        IconButton(onClick = { newTemplateName = ""; showSaveTemplateDialog = true }, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Save, "Guardar plantilla", tint = RoseDark, modifier = Modifier.size(20.dp)) }
                    }
                }
                if (traditional) {
                    val printerDots = if (paperWidth <= 58) 384f else 576f
                    val paperDisplayWidth = minOf(if (paperWidth <= 58) 240 else 320, configuration.screenWidthDp - 32).coerceAtLeast(180).dp
                    val editorFontSize = (24f * (paperDisplayWidth.value / printerDots)).coerceAtLeast(8f).sp
                    val editorFontFamily = when (traditionalFont) { "Serif" -> FontFamily.Serif; "Monospace" -> FontFamily.Monospace; else -> FontFamily.SansSerif }
                    val editorAlignment = when (traditionalAlignment) {
                        "Centro" -> TextAlign.Center
                        "Derecha" -> TextAlign.End
                        "Justificado" -> TextAlign.Justify
                        else -> TextAlign.Start
                    }
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.TopCenter) {
                        Card(
                            Modifier.width(paperDisplayWidth).fillMaxHeight(),
                            shape = RoundedCornerShape(3.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                        ) {
                            BasicTextField(
                                value = designValue,
                                onValueChange = { next ->
                                    // Accept Compose's edit result directly to avoid stale annotated text at line breaks.
                                    designValue = next
                                },
                                modifier = Modifier.fillMaxSize()
                                    .onPreviewKeyEvent { event ->
                                        if (event.type == KeyEventType.KeyDown && event.isCtrlPressed) {
                                            when (event.key) {
                                                Key.B -> { applyFormatting("bold"); true }
                                                Key.I -> { applyFormatting("italic"); true }
                                                Key.U -> { applyFormatting("underline"); true }
                                                else -> false
                                            }
                                        } else false
                                    },
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    color = Ink,
                                    fontSize = editorFontSize,
                                    fontFamily = FontFamily.SansSerif,
                                    textAlign = TextAlign.Start,
                                    lineHeight = (editorFontSize.value * 1.22f).sp
                                ),
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(RoseDark),
                                decorationBox = { inner ->
                                    Box(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 7.dp)) {
                                        if (designValue.text.isEmpty()) Text("Escribí el ticket…", color = Muted, fontSize = editorFontSize)
                                        inner()
                                    }
                                }
                            )
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CardBlock(Modifier.weight(1f).fillMaxHeight()) {
                            Text("Texto del ticket", fontWeight = FontWeight.SemiBold, color = Ink, fontSize = 12.sp)
                            OutlinedTextField(
                                value = designText,
                                onValueChange = { designValue = TextFieldValue(it) },
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                placeholder = { Text("Escribí aquí el texto del ticket…") },
                                minLines = 1,
                                maxLines = Int.MAX_VALUE
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                IconButton(onClick = {
                                    val updated = template.copy(content = designText, designMode = designMode, paperWidth = paperWidth)
                                    updateTemplate(templates, updated, ::persist)
                                    notice = UsbEscPosPrinter.print(context, updated)
                                }) { Icon(Icons.Default.Print, "Imprimir", tint = RoseDark) }
                                IconButton(onClick = { newTemplateName = ""; showSaveTemplateDialog = true }) { Icon(Icons.Default.Save, "Guardar plantilla", tint = RoseDark) }
                            }
                        }
                        CardBlock(Modifier.weight(1f).fillMaxHeight()) {
                            Text("Vista previa", fontWeight = FontWeight.SemiBold, color = Ink, fontSize = 12.sp)
                            TicketTextPreview(designText, paperWidth, Modifier.weight(1f).fillMaxWidth(), true, alignment = traditionalAlignment)
                        }
                    }
                }
            }
        } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(insets).padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (activeTab) {
                AppTab.DESIGN -> {
                    item {
                        val template = selected ?: templates.first()
                        val traditional = designMode == "Tradicional"
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = if (isLandscape) 10.dp else 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Diseño", fontWeight = FontWeight.SemiBold, color = Ink, fontSize = 14.sp)
                                Spacer(Modifier.weight(1f))
                                FilterChip(selected = !traditional, onClick = { designMode = "Ajuste automático" }, label = { Text("Automático", fontSize = 11.sp) })
                                FilterChip(selected = traditional, onClick = { designMode = "Tradicional" }, label = { Text("Tradicional", fontSize = 11.sp) })
                            }

                            if (traditional) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    var sizeMenu by remember { mutableStateOf(false) }
                                    Box {
                                        TextButton(onClick = { sizeMenu = true }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                                            Text("${traditionalFontSize} sp", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Tamaño de fuente")
                                        }
                                        DropdownMenu(expanded = sizeMenu, onDismissRequest = { sizeMenu = false }) {
                                            listOf(10, 12, 14, 16, 18, 20, 22, 24, 28, 32, 36, 40).forEach { size ->
                                                DropdownMenuItem(text = { Text("${size} sp") }, onClick = { traditionalFontSize = size; sizeMenu = false })
                                            }
                                        }
                                    }
                                    var fontMenu by remember { mutableStateOf(false) }
                                    Box {
                                        TextButton(onClick = { fontMenu = true }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                                            Text(when (traditionalFont) { "Serif" -> "Serif"; "Monospace" -> "Mono"; else -> "Sans" }, fontSize = 13.sp)
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Estilo de fuente")
                                        }
                                        DropdownMenu(expanded = fontMenu, onDismissRequest = { fontMenu = false }) {
                                            listOf("Sans Serif", "Serif", "Monospace").forEach { font ->
                                                DropdownMenuItem(text = { Text(font) }, onClick = { traditionalFont = font; fontMenu = false })
                                            }
                                        }
                                    }
                                    VerticalDivider(Modifier.height(28.dp), color = Line)
                                    @Composable fun formatButton(selectedState: Boolean, description: String, onClick: () -> Unit, icon: androidx.compose.ui.graphics.vector.ImageVector) {
                                        IconButton(onClick = onClick, modifier = Modifier.size(38.dp)) {
                                            Icon(icon, contentDescription = description, tint = if (selectedState) RoseDark else Ink)
                                        }
                                    }
                                    formatButton(traditionalAlignment == "Izquierda", "Alinear a la izquierda", { traditionalAlignment = "Izquierda" }, Icons.Default.FormatAlignLeft)
                                    formatButton(traditionalAlignment == "Centro", "Centrar", { traditionalAlignment = "Centro" }, Icons.Default.FormatAlignCenter)
                                    formatButton(traditionalAlignment == "Derecha", "Alinear a la derecha", { traditionalAlignment = "Derecha" }, Icons.Default.FormatAlignRight)
                                    VerticalDivider(Modifier.height(28.dp), color = Line)
                                    formatButton(traditionalBold, "Negrita", { traditionalBold = !traditionalBold }, Icons.Default.FormatBold)
                                    formatButton(traditionalItalic, "Cursiva", { traditionalItalic = !traditionalItalic }, Icons.Default.FormatItalic)
                                    formatButton(traditionalUnderline, "Subrayado", { traditionalUnderline = !traditionalUnderline }, Icons.Default.FormatUnderlined)
                                    VerticalDivider(Modifier.height(28.dp), color = Line)
                                    IconButton(onClick = {
                                        val updated = template.copy(content = serializeRichText(designValue.annotatedString, ticketPreviewScale), designMode = designMode, fontSize = traditionalFontSize, fontFamily = traditionalFont, alignment = traditionalAlignment, bold = traditionalBold, underline = traditionalUnderline, italic = traditionalItalic, paperWidth = paperWidth)
                                        updateTemplate(templates, updated, ::persist)
                                        notice = UsbEscPosPrinter.print(context, updated)
                                    }, modifier = Modifier.size(38.dp)) { Icon(Icons.Default.Print, contentDescription = "Imprimir", tint = RoseDark) }
                                    IconButton(onClick = { newTemplateName = ""; showSaveTemplateDialog = true }, modifier = Modifier.size(38.dp)) {
                                        Icon(Icons.Default.Save, contentDescription = "Guardar plantilla", tint = RoseDark)
                                    }
                                }
                            }

                            if (isLandscape) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().height((configuration.screenHeightDp - 238).coerceAtLeast(260).dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CardBlock(Modifier.weight(1f).fillMaxHeight()) {
                                        Text("Texto del ticket", fontWeight = FontWeight.SemiBold, color = Ink, fontSize = 12.sp)
                                        OutlinedTextField(
                                            value = designValue,
                                            onValueChange = { designValue = it },
                                            modifier = Modifier.weight(1f).fillMaxWidth(),
                                            placeholder = { Text("Escribí aquí el texto del ticket…") },
                                            minLines = 1,
                                            maxLines = Int.MAX_VALUE
                                        )
                                    }
                                    CardBlock(Modifier.weight(1f).fillMaxHeight()) {
                                        Text("Vista previa", fontWeight = FontWeight.SemiBold, color = Ink, fontSize = 12.sp)
                                        TicketTextPreview(designText, paperWidth, Modifier.weight(1f).fillMaxWidth(), !traditional, traditionalFontSize, traditionalFont, traditionalAlignment, traditionalBold, traditionalUnderline, traditionalItalic)
                                    }
                                }
                                if (!traditional) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                        IconButton(onClick = {
                                            val updated = template.copy(content = designText, designMode = designMode, fontSize = traditionalFontSize, fontFamily = traditionalFont, alignment = traditionalAlignment, bold = traditionalBold, underline = traditionalUnderline, italic = traditionalItalic, paperWidth = paperWidth)
                                            updateTemplate(templates, updated, ::persist)
                                            notice = UsbEscPosPrinter.print(context, updated)
                                        }) { Icon(Icons.Default.Print, contentDescription = "Imprimir", tint = RoseDark) }
                                        IconButton(onClick = { newTemplateName = ""; showSaveTemplateDialog = true }) { Icon(Icons.Default.Save, contentDescription = "Guardar plantilla", tint = RoseDark) }
                                    }
                                }
                            } else {
                                CardBlock {
                                    OutlinedTextField(value = designValue, onValueChange = { designValue = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp), placeholder = { Text("Escribí aquí el texto del ticket…") }, label = { Text("Texto del ticket") }, minLines = 5, maxLines = 12)
                                    Text("Vista previa", fontWeight = FontWeight.SemiBold, color = Ink)
                                    TicketTextPreview(designText, paperWidth, Modifier.fillMaxWidth(), !traditional, traditionalFontSize, traditionalFont, traditionalAlignment, traditionalBold, traditionalUnderline, traditionalItalic)
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = { val updated = template.copy(content = designText, designMode = designMode, fontSize = traditionalFontSize, fontFamily = traditionalFont, alignment = traditionalAlignment, bold = traditionalBold, underline = traditionalUnderline, italic = traditionalItalic, paperWidth = paperWidth); updateTemplate(templates, updated, ::persist); notice = UsbEscPosPrinter.print(context, updated) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Print, null); Spacer(Modifier.width(4.dp)); Text("Imprimir") }
                                        Button(onClick = { newTemplateName = ""; showSaveTemplateDialog = true }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Save, null); Spacer(Modifier.width(4.dp)); Text("Guardar como") }
                                    }
                                }
                            }
                        }
                    }
                }
                AppTab.TEMPLATES -> {
                    item { SectionHeading("Tus plantillas", "Guardá distintos diseños para cada necesidad") }
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
                        if (isLandscape) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                                CardBlock(modifier = Modifier.weight(1.15f)) {
                                    Text("Previsualización", fontWeight = FontWeight.SemiBold, color = Ink)
                                    Box(Modifier.fillMaxWidth().height(previewHeight).clip(RoundedCornerShape(16.dp)).background(RosePale), contentAlignment = Alignment.Center) {
                                        when {
                                            imageLoading -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(20.dp)) { CircularProgressIndicator(); Spacer(Modifier.height(8.dp)); Text("Cargando imagen…", color = RoseDark) }
                                            imagePreview != null -> androidx.compose.foundation.Image(bitmap = imagePreview!!.asImageBitmap(), contentDescription = "Vista previa de imagen", modifier = Modifier.fillMaxSize().padding(8.dp), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
                                            else -> Icon(Icons.Default.Image, contentDescription = "Sin imagen", tint = RoseDark, modifier = Modifier.padding(28.dp).size(48.dp))
                                        }
                                    }
                                    Text(selectedImage?.lastPathSegment ?: "Todavía no elegiste una imagen", fontSize = 12.sp, color = Muted, maxLines = 2)
                                }
                                CardBlock(modifier = Modifier.weight(0.85f)) {
                                    Text("Opciones", fontWeight = FontWeight.SemiBold, color = Ink)
                                    Button(onClick = { imagePicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Elegir imagen") }
                                    Text("Elegir imagen", fontSize = 12.sp, color = Muted)
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        IconButton(onClick = { imageRotation = (imageRotation + 270) % 360 }, enabled = selectedImage != null) { Icon(Icons.Default.RotateLeft, contentDescription = "Girar a la izquierda") }
                                        IconButton(onClick = { imageRotation = (imageRotation + 90) % 360 }, enabled = selectedImage != null) { Icon(Icons.Default.RotateRight, contentDescription = "Girar a la derecha") }
                                        Text("${imageRotation}°", color = Muted)
                                    }
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text("Brillo · ${brightnessControl.toInt()}", fontSize = 12.sp, color = Ink)
                                        Slider(value = brightnessControl, onValueChange = { brightnessControl = it }, onValueChangeFinished = { imageBrightness = brightnessControl }, valueRange = -100f..100f, enabled = selectedImage != null)
                                        Text("Contraste · ${contrastControl.toInt()}%", fontSize = 12.sp, color = Ink)
                                        Slider(value = contrastControl, onValueChange = { contrastControl = it }, onValueChangeFinished = { imageContrast = contrastControl }, valueRange = 0f..200f, enabled = selectedImage != null)
                                        TextButton(onClick = { brightnessControl = 0f; contrastControl = 100f; imageBrightness = 0f; imageContrast = 100f }, enabled = selectedImage != null) { Text("Restablecer ajustes") }
                                    }
                                    Button(onClick = {
                                        val uri = selectedImage
                                        notice = if (uri == null) "Primero elegí una imagen." else runCatching {
                                            val bitmap = imagePreview ?: loadMonochromeImage(context, uri, imageRotation, imageBrightness, imageContrast)
                                            UsbEscPosPrinter.printBitmap(context, bitmap, paperWidth, selected?.feedLines ?: 3)
                                        }.getOrElse { "No se pudo preparar la imagen: " + (it.message ?: "error desconocido") }
                                    }, enabled = selectedImage != null && !imageLoading && imagePreview != null, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Print, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Imprimir") }
                                    Text("La imagen se prepara en blanco y negro antes de imprimir.", fontSize = 11.sp, color = Muted)
                                }
                            }
                        } else {
                            CardBlock {
                                Box(Modifier.fillMaxWidth().height(previewHeight).clip(RoundedCornerShape(16.dp)).background(RosePale), contentAlignment = Alignment.Center) {
                                    when {
                                        imageLoading -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(20.dp)) { CircularProgressIndicator(); Text("Cargando imagen…", color = RoseDark) }
                                        imagePreview != null -> androidx.compose.foundation.Image(bitmap = imagePreview!!.asImageBitmap(), contentDescription = "Vista previa de imagen", modifier = Modifier.fillMaxWidth().padding(8.dp), contentScale = androidx.compose.ui.layout.ContentScale.FillWidth)
                                        else -> Icon(Icons.Default.Image, contentDescription = "Sin imagen", tint = RoseDark, modifier = Modifier.padding(28.dp).size(48.dp))
                                    }
                                }
                                Text(selectedImage?.lastPathSegment ?: "Todavía no elegiste una imagen", fontSize = 12.sp, color = Muted)
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("Brillo · ${brightnessControl.toInt()}", fontSize = 12.sp, color = Ink)
                                    Slider(value = brightnessControl, onValueChange = { brightnessControl = it }, onValueChangeFinished = { imageBrightness = brightnessControl }, valueRange = -100f..100f, enabled = selectedImage != null)
                                    Text("Contraste · ${contrastControl.toInt()}%", fontSize = 12.sp, color = Ink)
                                    Slider(value = contrastControl, onValueChange = { contrastControl = it }, onValueChangeFinished = { imageContrast = contrastControl }, valueRange = 0f..200f, enabled = selectedImage != null)
                                    TextButton(onClick = { brightnessControl = 0f; contrastControl = 100f; imageBrightness = 0f; imageContrast = 100f }, enabled = selectedImage != null) { Text("Restablecer ajustes") }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { imagePicker.launch("image/*") }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 6.dp)) { Icon(Icons.Default.Add, contentDescription = null); Spacer(Modifier.width(4.dp)); Text("Elegir", fontSize = 12.sp) }
                                    Button(onClick = { val uri = selectedImage; notice = if (uri == null) "Primero elegí una imagen." else runCatching { UsbEscPosPrinter.printBitmap(context, imagePreview ?: loadMonochromeImage(context, uri, imageRotation), paperWidth, selected?.feedLines ?: 3) }.getOrElse { "No se pudo preparar la imagen: " + (it.message ?: "error desconocido") } }, enabled = selectedImage != null && !imageLoading && imagePreview != null, modifier = Modifier.weight(3f)) { Icon(Icons.Default.Print, null); Spacer(Modifier.width(8.dp)); Text("Imprimir") }
                                }
                            }
                        }
                    }
                }
                AppTab.PDF -> {
                    item { SectionHeading("Documentos PDF", "Elegí un archivo y las páginas que querés imprimir") }
                    item {
                        if (isLandscape) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                                CardBlock(modifier = Modifier.weight(1.2f)) {
                                    Text("Previsualización de páginas", fontWeight = FontWeight.SemiBold, color = Ink)
                                    if (pdfLoading) {
                                        Box(Modifier.fillMaxWidth().height(previewHeight), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(); Spacer(Modifier.height(12.dp)); Text("Cargando PDF…", color = RoseDark) } }
                                        LinearProgressIndicator(Modifier.fillMaxWidth())
                                    } else if (pdfPages.isEmpty()) {
                                        Box(Modifier.fillMaxWidth().height(previewHeight).clip(RoundedCornerShape(16.dp)).background(RosePale), contentAlignment = Alignment.Center) { Icon(Icons.Default.PictureAsPdf, null, tint = RoseDark, modifier = Modifier.size(48.dp)) }
                                    } else {
                                        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(minSize = 105.dp), modifier = Modifier.fillMaxWidth().height(previewHeight), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            items(pdfPages.size) { index ->
                                                val isSelected = index in selectedPdfPages
                                                Card(onClick = { if (isSelected) selectedPdfPages.remove(index) else selectedPdfPages.add(index); pdfSelectionText = formatPageRanges(selectedPdfPages.toList()) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = if (isSelected) RosePale else Color.White), border = androidx.compose.foundation.BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) RoseDark else Line)) {
                                                    Column(Modifier.padding(6.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = isSelected, onCheckedChange = null); Text("${index + 1}", fontWeight = FontWeight.SemiBold) }; androidx.compose.foundation.Image(bitmap = pdfPages[index].asImageBitmap(), contentDescription = "Página ${index + 1}", modifier = Modifier.fillMaxWidth().heightIn(max = 145.dp), contentScale = androidx.compose.ui.layout.ContentScale.Fit) }
                                                }
                                            }
                                        }
                                        Text("Tocá cualquier parte de una página para seleccionarla o quitarla.", fontSize = 11.sp, color = Muted)
                                    }
                                }
                                CardBlock(modifier = Modifier.weight(0.8f)) {
                                    FilledTonalIconButton(onClick = { pdfPicker.launch(arrayOf("application/pdf")) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, contentDescription = "Elegir PDF") }
                                    Text(if (selectedPdf == null) "Elegir PDF" else selectedPdf?.lastPathSegment ?: "PDF seleccionado", fontSize = 12.sp, color = Muted, maxLines = 2)
                                    if (pdfPages.isNotEmpty() && !pdfLoading) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            OutlinedButton(onClick = { selectedPdfPages.clear(); selectedPdfPages.addAll(pdfPages.indices); pdfSelectionText = formatPageRanges(selectedPdfPages.toList()) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 6.dp)) { Icon(Icons.Default.CheckCircle, null); Text("Todas", fontSize = 11.sp) }
                                            OutlinedButton(onClick = { selectedPdfPages.clear(); pdfSelectionText = "" }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 6.dp)) { Icon(Icons.Default.Delete, null); Text("Ninguna", fontSize = 11.sp) }
                                        }
                                        OutlinedTextField(value = pdfSelectionText, onValueChange = { value -> pdfSelectionText = value; selectedPdfPages.clear(); selectedPdfPages.addAll(parsePageRanges(value, pdfPages.size)) }, label = { Text("Páginas") }, placeholder = { Text("1-2, 4-6") }, supportingText = { Text("Total: ${pdfPages.size} páginas") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                        Text("${selectedPdfPages.size} de ${pdfPages.size} seleccionadas", color = Muted, fontSize = 12.sp)
                                        Button(onClick = { notice = if (selectedPdfPages.isEmpty()) "Seleccioná al menos una página." else runCatching { val combined = combineBitmaps(selectedPdfPages.sorted().map { pdfPages[it] }) ?: error("No se pudo preparar el documento."); UsbEscPosPrinter.printBitmap(context, combined, paperWidth, selected?.feedLines ?: 3) }.getOrElse { "No se pudo imprimir el PDF: " + (it.message ?: "error desconocido") } }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Print, null); Spacer(Modifier.width(8.dp)); Text("Imprimir páginas") }
                                    }
                                }
                            }
                        } else {
                            CardBlock {
                                Box(Modifier.fillMaxWidth().height(previewHeight).clip(RoundedCornerShape(16.dp)).background(RosePale), contentAlignment = Alignment.Center) { if (pdfLoading) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp); Text("Cargando PDF…", color = RoseDark) } else Icon(Icons.Default.PictureAsPdf, null, tint = RoseDark, modifier = Modifier.size(36.dp)) }
                                if (pdfLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                                Text(if (selectedPdf == null) "Ningún PDF seleccionado" else selectedPdf?.lastPathSegment ?: "PDF seleccionado", fontWeight = FontWeight.SemiBold, color = Ink)
                                Button(onClick = { pdfPicker.launch(arrayOf("application/pdf")) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Elegir PDF") }
                                if (pdfPages.isNotEmpty() && !pdfLoading) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick = { selectedPdfPages.clear(); selectedPdfPages.addAll(pdfPages.indices); pdfSelectionText = formatPageRanges(selectedPdfPages.toList()) }, modifier = Modifier.weight(1f)) { Text("Seleccionar todas") }; OutlinedButton(onClick = { selectedPdfPages.clear(); pdfSelectionText = "" }, modifier = Modifier.weight(1f)) { Text("Deseleccionar") } }
                                    OutlinedTextField(value = pdfSelectionText, onValueChange = { value -> pdfSelectionText = value; selectedPdfPages.clear(); selectedPdfPages.addAll(parsePageRanges(value, pdfPages.size)) }, label = { Text("Páginas seleccionadas") }, placeholder = { Text("Ej.: 1-2, 4-6") }, supportingText = { Text("Total: ${pdfPages.size} páginas") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(minSize = 120.dp), modifier = Modifier.fillMaxWidth().height(previewHeight), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(pdfPages.size) { index -> val isSelected = index in selectedPdfPages; Card(onClick = { if (isSelected) selectedPdfPages.remove(index) else selectedPdfPages.add(index); pdfSelectionText = formatPageRanges(selectedPdfPages.toList()) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = if (isSelected) RosePale else Color.White), border = androidx.compose.foundation.BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) RoseDark else Line)) { Column(Modifier.padding(8.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = isSelected, onCheckedChange = null); Text("${index + 1}", fontWeight = FontWeight.SemiBold) }; androidx.compose.foundation.Image(bitmap = pdfPages[index].asImageBitmap(), contentDescription = "Página ${index + 1}", modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp), contentScale = androidx.compose.ui.layout.ContentScale.Fit) } } } }
                                    Button(onClick = { notice = if (selectedPdfPages.isEmpty()) "Seleccioná al menos una página." else runCatching { val combined = combineBitmaps(selectedPdfPages.sorted().map { pdfPages[it] }) ?: error("No se pudo preparar el documento."); UsbEscPosPrinter.printBitmap(context, combined, paperWidth, selected?.feedLines ?: 3) }.getOrElse { "No se pudo imprimir el PDF: " + (it.message ?: "error desconocido") } }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Print, null); Spacer(Modifier.width(8.dp)); Text("Imprimir páginas") }
                                }
                            }
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
                            Text("Página de prueba", fontWeight = FontWeight.SemiBold, color = Ink)
                            Text("Imprime una hoja de diagnóstico con tamaños, tipografías, estilos, alineaciones, letras, números y símbolos.", color = Muted, fontSize = 12.sp)
                            Button(onClick = {
                                val testContent = "<p align=\"left\"><span style=\"font-family:sans-serif;font-size:24px\"><b>PÁGINA DE PRUEBA</b></span></p>" +
                                    "<p align=\"left\"><span style=\"font-family:sans-serif;font-size:12px\">Sans Serif · 12 px · Normal</span></p>" +
                                    "<p align=\"center\"><span style=\"font-family:serif;font-size:18px\"><b>Serif · 18 px · Negrita</b></span></p>" +
                                    "<p align=\"right\"><span style=\"font-family:monospace;font-size:14px\"><i>Monospace · 14 px · Cursiva</i></span></p>" +
                                    "<p align=\"left\"><span style=\"font-family:sans-serif;font-size:16px\"><u>Subrayado · 16 px</u></span></p>" +
                                    "<p align=\"left\"><span style=\"font-family:sans-serif;font-size:12px\">ABCDEFGHIJKLMNOPQRSTUVWXYZ</span></p>" +
                                    "<p align=\"left\"><span style=\"font-family:sans-serif;font-size:12px\">abcdefghijklmnopqrstuvwxyz</span></p>" +
                                    "<p align=\"left\"><span style=\"font-family:monospace;font-size:12px\">0123456789</span></p>" +
                                    "<p align=\"left\"><span style=\"font-family:sans-serif;font-size:12px\">¡! ¿? @ # $ % &amp; * ( ) - _ + = / \\ : ; , .</span></p>"
                                val testTemplate = TicketTemplate(name = "Página de prueba", content = testContent, paperWidth = paperWidth, feedLines = selected?.feedLines ?: 3, designMode = "Tradicional", fontSize = 18, fontFamily = "Sans Serif", alignment = "Izquierda")
                                notice = UsbEscPosPrinter.print(context, testTemplate)
                            }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Print, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Imprimir página de prueba") }
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
    }

    if (showSaveTemplateDialog) {
        AlertDialog(
            onDismissRequest = { showSaveTemplateDialog = false },
            title = { Text("Guardar nueva plantilla") },
            text = {
                OutlinedTextField(value = newTemplateName, onValueChange = { newTemplateName = it },
                    label = { Text("Nombre de la plantilla") }, singleLine = true)
            },
            confirmButton = {
                Button(onClick = {
                    val name = newTemplateName.trim()
                    if (name.isNotEmpty()) {
                        val source = selected
                        val created = TicketTemplate(
                            name = name,
                            content = if (activeTab == AppTab.DESIGN) serializeRichText(designValue.annotatedString, ticketPreviewScale) else source?.content ?: "",
                            designMode = if (activeTab == AppTab.DESIGN) designMode else source?.designMode ?: "Ajuste automático", fontSize = traditionalFontSize, fontFamily = traditionalFont, alignment = traditionalAlignment, bold = traditionalBold, underline = traditionalUnderline, italic = traditionalItalic,
                            paperWidth = paperWidth,
                            feedLines = source?.feedLines ?: 3,
                            cutAfterPrint = source?.cutAfterPrint ?: true
                        )
                        templates.add(created)
                        selectedId = created.id
                        persist()
                        showSaveTemplateDialog = false
                        activeTab = AppTab.TEMPLATES
                    }
                }, enabled = newTemplateName.trim().isNotEmpty()) { Text("Guardar") }
            },
            dismissButton = { OutlinedButton(onClick = { showSaveTemplateDialog = false }) { Text("Cancelar") } }
        )
    }
    deleteCandidate?.let { template ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Eliminar plantilla") },
            text = { Text("¿Querés eliminar «${template.name}»? Esta acción no se puede deshacer.") },
            confirmButton = {
                Button(onClick = {
                    templates.removeAll { it.id == template.id }
                    if (templates.isEmpty()) templates.add(TicketTemplate(name = "Plantilla en blanco", content = ""))
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


@Composable
private fun TicketTextPreview(text: String, paperWidth: Int, modifier: Modifier = Modifier, autoFit: Boolean = true, fontSize: Int = 24, fontFamilyName: String = "Sans Serif", alignment: String = "Izquierda", bold: Boolean = false, underline: Boolean = false, italic: Boolean = false) {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n")
    val baseTextPx = if (paperWidth <= 58) 28f else 34f
    val previewWidth = if (paperWidth <= 58) 384f else 576f
    val paint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG) }
    Column(
        modifier = modifier
            .border(1.dp, Line, RoundedCornerShape(8.dp))
            .background(Color.White)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
    ) {
        lines.forEach { line ->
            if (line.isEmpty()) {
                Spacer(Modifier.height(12.dp))
            } else {
                paint.textSize = baseTextPx
                val measured = paint.measureText(line).coerceAtLeast(1f)
                val scaledPx = if (autoFit) (baseTextPx * (previewWidth - 20f) / measured).coerceAtLeast(8f) else fontSize * density
                val family = when (fontFamilyName) { "Serif" -> FontFamily.Serif; "Monospace" -> FontFamily.Monospace; else -> FontFamily.SansSerif }
                val align = when (alignment) { "Centro" -> TextAlign.Center; "Derecha" -> TextAlign.End; "Justificado" -> TextAlign.Justify; else -> TextAlign.Start }
                Text(
                    text = line, color = Color.Black,
                    fontSize = if (autoFit) (scaledPx / density).sp else fontSize.sp,
                    fontFamily = family,
                    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (italic) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
                    textDecoration = if (underline) androidx.compose.ui.text.style.TextDecoration.Underline else androidx.compose.ui.text.style.TextDecoration.None,
                    textAlign = align, maxLines = 1, softWrap = false, modifier = Modifier.fillMaxWidth()
                )
            }
        }
        if (lines.isEmpty()) Text("La vista previa aparecerá aquí", color = Muted, fontSize = 12.sp)
    }
}

private fun ticketContentToAnnotatedString(content: String, sizeScale: Float = 1f): AnnotatedString {
    if (!content.contains("<") || !content.contains(">")) return AnnotatedString(content)
    val spanned = android.text.Html.fromHtml(content, android.text.Html.FROM_HTML_MODE_LEGACY)
    val plain = spanned.toString().trimEnd('\n')
    val builder = AnnotatedString.Builder(plain)
    val spans = spanned as? android.text.Spanned ?: return builder.toAnnotatedString()
    spans.getSpans(0, plain.length, Any::class.java).forEach { span ->
        val start = spans.getSpanStart(span).coerceIn(0, plain.length)
        val end = spans.getSpanEnd(span).coerceIn(start, plain.length)
        if (start >= end) return@forEach
        when (span) {
            is android.text.style.AlignmentSpan -> {
                val alignment = when (span.alignment) {
                    android.text.Layout.Alignment.ALIGN_CENTER -> TextAlign.Center
                    android.text.Layout.Alignment.ALIGN_OPPOSITE -> TextAlign.End
                    else -> TextAlign.Start
                }
                builder.addStyle(ParagraphStyle(textAlign = alignment), start, end)
            }
            is android.text.style.StyleSpan -> builder.addStyle(SpanStyle(
                fontWeight = if (span.style and android.graphics.Typeface.BOLD != 0) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (span.style and android.graphics.Typeface.ITALIC != 0) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal
            ), start, end)
            is android.text.style.UnderlineSpan -> builder.addStyle(SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline), start, end)
            is android.text.style.AbsoluteSizeSpan -> builder.addStyle(SpanStyle(fontSize = (span.size * sizeScale).sp), start, end)
            is android.text.style.RelativeSizeSpan -> builder.addStyle(SpanStyle(fontSize = (18f * span.sizeChange * sizeScale).sp), start, end)
            is android.text.style.TypefaceSpan -> builder.addStyle(SpanStyle(fontFamily = when (span.family) { "serif" -> FontFamily.Serif; "monospace" -> FontFamily.Monospace; else -> FontFamily.SansSerif }), start, end)
        }
    }
    return builder.toAnnotatedString()
}

private fun serializeRichText(value: AnnotatedString, sizeScale: Float = 1f): String {
    val out = StringBuilder()
    val text = value.text
    var lineStart = 0
    while (lineStart <= text.length) {
        val breakAt = text.indexOf('\n', lineStart).let { if (it < 0) text.length else it }
        val alignment = value.paragraphStyles.lastOrNull { it.start <= lineStart && it.end >= breakAt }?.item?.textAlign
        val alignName = when (alignment) {
            TextAlign.Center -> "center"
            TextAlign.End, TextAlign.Right -> "right"
            else -> "left"
        }
        out.append("<p align=\"").append(alignName).append("\">")
        for (i in lineStart until breakAt) {
            val styles = value.spanStyles.filter { i >= it.start && i < it.end }.map { it.item }
            val bold = styles.lastOrNull { it.fontWeight != null }?.fontWeight == FontWeight.Bold
            val italic = styles.lastOrNull { it.fontStyle != null }?.fontStyle == androidx.compose.ui.text.font.FontStyle.Italic
            val underline = styles.lastOrNull { it.textDecoration != null }?.textDecoration == androidx.compose.ui.text.style.TextDecoration.Underline
            val size = styles.lastOrNull { it.fontSize.isSp }?.fontSize?.value?.div(sizeScale.coerceAtLeast(0.01f))?.toInt()
            val family = styles.lastOrNull { it.fontFamily != null }?.fontFamily
            if (bold) out.append("<b>")
            if (italic) out.append("<i>")
            if (underline) out.append("<u>")
            if (size != null || family != null) {
                val css = buildList {
                    if (size != null) add("font-size:${size}px")
                    if (family != null) add("font-family:${when (family) { FontFamily.Serif -> "serif"; FontFamily.Monospace -> "monospace"; else -> "sans-serif" }}")
                }.joinToString(";")
                out.append("<span style=\"").append(css).append("\">")
            }
            when (val ch = text[i]) {
                '&' -> out.append("&amp;")
                '<' -> out.append("&lt;")
                '>' -> out.append("&gt;")
                else -> out.append(ch)
            }
            if (size != null || family != null) out.append("</span>")
            if (underline) out.append("</u>")
            if (italic) out.append("</i>")
            if (bold) out.append("</b>")
        }
        out.append("</p>")
        if (breakAt == text.length) break
        lineStart = breakAt + 1
    }
    return out.toString()
}

private fun ticketContentToPlainText(content: String): String {
    if (!content.contains("<") || !content.contains(">")) return content
    val withBreaks = content
        .replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace(Regex("(?i)</p\\s*>|</div\\s*>|</tr\\s*>"), "\n")
        .replace(Regex("(?i)</t[dh]\\s*>"), "  ")
    return android.text.Html.fromHtml(withBreaks, android.text.Html.FROM_HTML_MODE_LEGACY)
        .toString().replace(Regex("\n{3,}"), "\n\n").trimEnd()
}

private fun formatPageRanges(indices: List<Int>): String {
    val pages = indices.distinct().sorted().map { it + 1 }
    if (pages.isEmpty()) return ""
    val ranges = mutableListOf<String>()
    var start = pages.first()
    var previous = start
    for (page in pages.drop(1)) {
        if (page == previous + 1) previous = page
        else {
            ranges.add(if (start == previous) start.toString() else "$start-$previous")
            start = page
            previous = page
        }
    }
    ranges.add(if (start == previous) start.toString() else "$start-$previous")
    return ranges.joinToString(", ")
}

private fun parsePageRanges(value: String, pageCount: Int): List<Int> {
    val pages = sortedSetOf<Int>()
    value.split(",").forEach { part ->
        val token = part.trim()
        if (token.isEmpty()) return@forEach
        val range = Regex("""^(\d+)\s*-\s*(\d+)$""").matchEntire(token)
        if (range != null) {
            val first = range.groupValues[1].toIntOrNull() ?: return@forEach
            val last = range.groupValues[2].toIntOrNull() ?: return@forEach
            if (first <= last) for (page in first..last) if (page in 1..pageCount) pages.add(page - 1)
        } else {
            token.toIntOrNull()?.let { if (it in 1..pageCount) pages.add(it - 1) }
        }
    }
    return pages.toList()
}

private fun updateTemplate(templates: MutableList<TicketTemplate>, updated: TicketTemplate, persist: () -> Unit) {
    val index = templates.indexOfFirst { it.id == updated.id }
    if (index >= 0) { templates[index] = updated; persist() }
}

private fun loadMonochromeImage(context: Context, uri: Uri, rotation: Int, brightness: Float = 0f, contrast: Float = 100f): Bitmap {
    // Downsample large photos before rotating/adjusting them to avoid exhausting Android heap.
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val boundsStream = context.contentResolver.openInputStream(uri)
        ?: error("No se pudo abrir la imagen.")
    boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) error("El archivo no contiene una imagen compatible.")
    var sample = 1
    while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample.coerceAtLeast(1); inPreferredConfig = Bitmap.Config.RGB_565 }
    val source = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        ?: error("No se pudo decodificar la imagen.")
    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    val result = Bitmap.createBitmap(rotated.width, rotated.height, Bitmap.Config.RGB_565)
    for (y in 0 until rotated.height) for (x in 0 until rotated.width) {
        val p = rotated.getPixel(x, y)
        val rawLuminance = (android.graphics.Color.red(p) * 299 + android.graphics.Color.green(p) * 587 + android.graphics.Color.blue(p) * 114) / 1000f
        val contrastFactor = (contrast.coerceIn(0f, 200f) / 100f)
        val adjusted = (((rawLuminance - 127.5f) * contrastFactor) + 127.5f + brightness.coerceIn(-100f, 100f) * 1.8f).toInt().coerceIn(0, 255)
        result.setPixel(x, y, if (adjusted < 160) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    }
    return result
}

private fun combineBitmaps(pages: List<Bitmap>): Bitmap? {
    if (pages.isEmpty()) return null
    val width = pages.maxOf { it.width }
    val height = pages.sumOf { it.height }
    val combined = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    combined.eraseColor(android.graphics.Color.WHITE)
    val canvas = AndroidCanvas(combined)
    var y = 0f
    pages.forEach { page ->
        canvas.drawBitmap(page, 0f, y, null)
        y += page.height.toFloat()
    }
    return combined
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
private fun RichTicketEditor(content: String, onContentChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var web by remember { mutableStateOf<WebView?>(null) }
    val initial = remember(content.take(100), content.length) { if (content.trimStart().startsWith("<p", true) || content.contains("<table", true) || content.contains("<div", true)) content else "<p>" + android.text.TextUtils.htmlEncode(content).replace("\n", "<br>") + "</p>" }
    var fonts by remember { mutableStateOf(false) }
    var sizes by remember { mutableStateOf(false) }
    var vars by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            Box { OutlinedButton(onClick = { fonts = true }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) { Text("Tipografía") }; DropdownMenu(expanded = fonts, onDismissRequest = { fonts = false }) { listOf("Arial" to "Arial", "Serif" to "serif", "Monospace" to "monospace", "Sans serif" to "sans-serif").forEach { (label, value) -> DropdownMenuItem(text = { Text(label) }, onClick = { web?.evaluateJavascript("document.execCommand('fontName',false,'" + value + "')", null); fonts = false }) } } }
            Box { OutlinedButton(onClick = { sizes = true }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) { Text("Tamaño") }; DropdownMenu(expanded = sizes, onDismissRequest = { sizes = false }) { listOf("Chico" to 2, "Normal" to 3, "Grande" to 5, "Muy grande" to 6).forEach { (label, value) -> DropdownMenuItem(text = { Text(label) }, onClick = { web?.evaluateJavascript("document.execCommand('fontSize',false,'" + value + "')", null); sizes = false }) } } }
            OutlinedButton(onClick = { web?.evaluateJavascript("document.execCommand('bold')", null) }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) { Text("B", fontWeight = FontWeight.Bold) }
            OutlinedButton(onClick = { web?.evaluateJavascript("document.execCommand('italic')", null) }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) { Text("I", fontStyle = androidx.compose.ui.text.font.FontStyle.Italic) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            OutlinedButton(onClick = { web?.evaluateJavascript("document.execCommand('justifyLeft')", null) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) { Text("Izquierda") }
            OutlinedButton(onClick = { web?.evaluateJavascript("document.execCommand('justifyCenter')", null) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) { Text("Centrar") }
            OutlinedButton(onClick = { web?.evaluateJavascript("document.execCommand('insertHTML',false,'<table border=\"1\" style=\"width:100%;border-collapse:collapse\"><tr><td>Celda 1</td><td>Celda 2</td></tr><tr><td>Celda 3</td><td>Celda 4</td></tr></table><p><br></p>')", null) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) { Text("+ Tabla") }
            Box { OutlinedButton(onClick = { vars = true }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) { Text("+ Variable") }; DropdownMenu(expanded = vars, onDismissRequest = { vars = false }) { listOf("Fecha" to "{{fecha}}", "Hora" to "{{hora}}", "Producto" to "{{producto}}", "Cantidad" to "{{cantidad}}", "Precio" to "{{precio}}", "Total" to "{{total}}").forEach { (label, value) -> DropdownMenuItem(text = { Text(label) }, onClick = { web?.evaluateJavascript("document.execCommand('insertText',false,'" + value + "')", null); vars = false }) } } }
        }
        AndroidView(modifier = Modifier.fillMaxWidth().height(320.dp).border(1.dp, Line, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp)), factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true; setBackgroundColor(android.graphics.Color.WHITE); isVerticalScrollBarEnabled = true
                addJavascriptInterface(object { @JavascriptInterface fun changed(html: String) { android.os.Handler(android.os.Looper.getMainLooper()).post { onContentChange(html) } } }, "TicketEditor")
                webViewClient = WebViewClient()
                val page = "<!doctype html><html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><style>body{font-family:Arial,sans-serif;font-size:16px;color:#30262a;padding:10px;margin:0;min-height:280px;outline:none}table{border-collapse:collapse;width:100%;table-layout:fixed}td,th{border:1px solid #c9aab3;padding:5px;min-width:30px;word-break:break-word}p{margin:0 0 8px}</style></head><body contenteditable=\"true\" oninput=\"TicketEditor.changed(document.body.innerHTML)\"></body></html>"
                loadDataWithBaseURL(null, page, "text/html", "UTF-8", null)
                post { evaluateJavascript("document.body.innerHTML=" + org.json.JSONObject.quote(initial), null) }; web = this
            }
        }, update = { web = it })
    }
}
@Composable
private fun TicketTableEditor(template: TicketTemplate, onContentChange: (String) -> Unit) {
    fun parse(value: String): List<List<String>> =
        value.split("\n").map { it.split("\t") }.ifEmpty { listOf(listOf("")) }
    var grid by remember(template.id) { mutableStateOf(parse(template.content)) }
    val selectedCells = remember(template.id) { mutableStateListOf<Pair<Int, Int>>() }
    var editingCell by remember(template.id) { mutableStateOf<Pair<Int, Int>?>(null) }
    var showSplitDialog by remember { mutableStateOf(false) }
    var splitRows by remember { mutableStateOf("2") }
    var splitColumns by remember { mutableStateOf("2") }
    val columnCount = grid.maxOfOrNull { it.size } ?: 1

    fun publish(next: List<List<String>>) {
        val count = next.maxOfOrNull { it.size } ?: 1
        val normalized = next.map { row -> (0 until count).map { row.getOrElse(it) { "" } } }
        grid = normalized
        onContentChange(normalized.joinToString("\n") { it.joinToString("\t") })
    }
    fun toggleCell(row: Int, col: Int) {
        val key = row to col
        if (key in selectedCells) selectedCells.remove(key) else selectedCells.add(key)
    }
    fun toggleRow(row: Int) {
        val cells = (0 until columnCount).map { row to it }
        if (cells.all { it in selectedCells }) selectedCells.removeAll(cells.toSet())
        else { selectedCells.removeAll(cells.toSet()); selectedCells.addAll(cells) }
    }
    fun toggleColumn(col: Int) {
        val cells = (0 until grid.size).map { it to col }
        if (cells.all { it in selectedCells }) selectedCells.removeAll(cells.toSet())
        else { selectedCells.removeAll(cells.toSet()); selectedCells.addAll(cells) }
    }
    val selectedRows = (0 until grid.size).filter { row -> (0 until columnCount).all { (row to it) in selectedCells } }
    val selectedColumns = (0 until columnCount).filter { col -> (0 until grid.size).all { (it to col) in selectedCells } }
    val horizontalPair = selectedCells.size == 2 && selectedCells[0].first == selectedCells[1].first &&
        kotlin.math.abs(selectedCells[0].second - selectedCells[1].second) == 1
    val verticalPair = selectedCells.size == 2 && selectedCells[0].second == selectedCells[1].second &&
        kotlin.math.abs(selectedCells[0].first - selectedCells[1].first) == 1

    if (selectedRows.isNotEmpty() || selectedColumns.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selectedRows.isNotEmpty()) {
                OutlinedButton(onClick = {
                    val insertAt = (selectedRows.maxOrNull() ?: (grid.size - 1)) + 1
                    val next = grid.toMutableList()
                    next.add(insertAt.coerceIn(0, next.size), List(columnCount) { "" })
                    publish(next); selectedCells.clear()
                }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text("+ Fila", fontSize = 12.sp) }
                if (selectedRows.size < grid.size) {
                    OutlinedButton(onClick = {
                        publish(grid.filterIndexed { index, _ -> index !in selectedRows }); selectedCells.clear()
                    }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text("− Fila", fontSize = 12.sp) }
                }
            }
            if (selectedColumns.isNotEmpty()) {
                OutlinedButton(onClick = {
                    val insertAt = (selectedColumns.maxOrNull() ?: (columnCount - 1)) + 1
                    publish(grid.map { row -> row.toMutableList().apply { add(insertAt.coerceIn(0, size), "") } }); selectedCells.clear()
                }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text("+ Col.", fontSize = 12.sp) }
                if (selectedColumns.size < columnCount) {
                    OutlinedButton(onClick = {
                        publish(grid.map { row -> row.filterIndexed { index, _ -> index !in selectedColumns } }); selectedCells.clear()
                    }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text("− Col.", fontSize = 12.sp) }
                }
            }
        }
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
                next[first.first][maxOf(first.second, second.second)] = ""
            } else {
                next[maxOf(first.first, second.first)][first.second] = ""
            }
            publish(next); selectedCells.clear()
        }, modifier = Modifier.fillMaxWidth()) { Text("Combinar celdas seleccionadas") }
    }
    Text("Tocá un elemento para seleccionarlo; tocá dos veces su texto para editarlo. Usá «Listo» al terminar.", fontSize = 11.sp, color = Muted)
    Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(42.dp).height(34.dp), contentAlignment = Alignment.Center) { Text("Fila", fontSize = 10.sp, color = Muted) }
            repeat(columnCount) { colIndex ->
                val allSelected = (0 until grid.size).all { (it to colIndex) in selectedCells }
                OutlinedButton(onClick = { toggleColumn(colIndex) },
                    modifier = Modifier.width((LocalConfiguration.current.screenWidthDp.dp - 16.dp - 42.dp - 4.dp * columnCount.coerceAtLeast(1)) / columnCount.coerceAtLeast(1)).height(34.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp),
                    colors = if (allSelected) ButtonDefaults.outlinedButtonColors(containerColor = RosePale, contentColor = RoseDark) else ButtonDefaults.outlinedButtonColors()
                ) { Text(if (allSelected) "✓ ${colIndex + 1}" else "Col. ${colIndex + 1}", fontSize = 10.sp, maxLines = 1) }
            }
        }
        grid.forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                val rowAllSelected = (0 until columnCount).all { (rowIndex to it) in selectedCells }
                OutlinedButton(onClick = { toggleRow(rowIndex) }, modifier = Modifier.width(42.dp).heightIn(min = 54.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                    colors = if (rowAllSelected) ButtonDefaults.outlinedButtonColors(containerColor = RosePale, contentColor = RoseDark) else ButtonDefaults.outlinedButtonColors()
                ) { Text(if (rowAllSelected) "✓ ${rowIndex + 1}" else "${rowIndex + 1}", fontSize = 10.sp) }
                row.forEachIndexed { colIndex, text ->
                    val selected = (rowIndex to colIndex) in selectedCells
                    var editValue by remember(template.id, rowIndex, colIndex, text) {
                        mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(text))
                    }
                    val cellFocusRequester = remember(template.id, rowIndex, colIndex) { FocusRequester() }
                    Column(
                        Modifier.width((LocalConfiguration.current.screenWidthDp.dp - 16.dp - 42.dp - 4.dp * columnCount.coerceAtLeast(1)) / columnCount.coerceAtLeast(1))
                            .border(if (selected) 2.dp else 1.dp, if (selected) RoseDark else Line, RoundedCornerShape(8.dp))
                            .background(if (selected) RosePale else Color.White, RoundedCornerShape(8.dp))
                            .then(
                                if (editingCell != (rowIndex to colIndex)) Modifier.pointerInput(rowIndex, colIndex) {
                                    detectTapGestures(
                                        onTap = { toggleCell(rowIndex, colIndex) },
                                        onDoubleTap = {
                                            if ((rowIndex to colIndex) !in selectedCells) selectedCells.add(rowIndex to colIndex)
                                            editingCell = rowIndex to colIndex
                                        }
                                    )
                                } else Modifier
                            )
                            .padding(5.dp)
                    ) {
                        LaunchedEffect(editingCell) {
                            if (editingCell == (rowIndex to colIndex)) cellFocusRequester.requestFocus()
                        }
                        if (editingCell == (rowIndex to colIndex)) {
                            androidx.compose.foundation.text.BasicTextField(
                                value = editValue,
                                onValueChange = { newValue ->
                                    editValue = newValue
                                    val next = grid.map { it.toMutableList() }.toMutableList()
                                    next[rowIndex][colIndex] = newValue.text
                                    publish(next)
                                },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).focusRequester(cellFocusRequester),
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    color = Ink,
                                    fontSize = (56f - text.length * 1.4f).coerceIn(12f, 52f).sp,
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
                            TextButton(onClick = { editingCell = null }, modifier = Modifier.align(Alignment.End)) { Text("Listo", fontSize = 11.sp) }
                        } else {
                            Box(Modifier.fillMaxWidth().heightIn(min = 54.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = text.ifEmpty { "Tocá dos veces para editar" },
                                    color = if (text.isEmpty()) Muted else Ink,
                                    fontSize = (56f - text.length * 1.4f).coerceIn(12f, 52f).sp,
                                    fontWeight = if (rowIndex == 0) FontWeight.SemiBold else FontWeight.Normal,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
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
                        Text(cell, fontFamily = FontFamily.Monospace, fontSize = (56f - cell.length * 1.4f).coerceIn(12f, 52f).sp, lineHeight = (62f - cell.length * 1.4f).coerceIn(14f, 58f).sp, color = Ink, maxLines = 3)
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
private fun CardBlock(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
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

