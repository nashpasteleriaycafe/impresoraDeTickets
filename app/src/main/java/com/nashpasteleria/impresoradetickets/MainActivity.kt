package com.nashpasteleria.impresoradetickets

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class TicketTemplate(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val content: String = "NASH Pastelería & Café\n--------------------------------\nProducto       Cant.     Precio\n--------------------------------\nGracias por tu compra!",
    val paperWidth: Int = 80,
    val feedLines: Int = 3,
    val cutAfterPrint: Boolean = true
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) { TicketApp(this@MainActivity) }
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
    var notice by remember { mutableStateOf<String?>(null) }
    val selected = templates.firstOrNull { it.id == selectedId } ?: templates.firstOrNull()

    fun persist() {
        val arr = JSONArray()
        templates.forEach { t ->
            arr.put(JSONObject().apply {
                put("id", t.id); put("name", t.name); put("content", t.content)
                put("paperWidth", t.paperWidth); put("feedLines", t.feedLines)
                put("cutAfterPrint", t.cutAfterPrint)
            })
        }
        prefs.edit().putString("items", arr.toString()).apply()
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Impresora de Tickets") }) }) { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(insets).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Configuración del ticket", style = MaterialTheme.typography.titleLarge)
                Text("Prepará tus diseños para papel térmico de 58 u 80 mm.")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        selected?.let { t -> templates[templates.indexOfFirst { it.id == t.id }] = t.copy(paperWidth = 58); persist() }
                    }) { Text("Papel 58 mm") }
                    OutlinedButton(onClick = {
                        selected?.let { t -> templates[templates.indexOfFirst { it.id == t.id }] = t.copy(paperWidth = 80); persist() }
                    }) { Text("Papel 80 mm") }
                }
                Text("Ancho seleccionado: ${selected?.paperWidth ?: 80} mm")
                Text("Avance: ${selected?.feedLines ?: 3} líneas · Corte: ${if (selected?.cutAfterPrint != false) "activado" else "desactivado"}")
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Plantillas", style = MaterialTheme.typography.titleLarge)
                    Button(onClick = { editing = null; showEditor = true }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text(" Nueva")
                    }
                }
            }
            items(templates, key = { it.id }) { template ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(template.name, style = MaterialTheme.typography.titleMedium)
                            Text("${template.paperWidth} mm · ${template.content.lines().size} líneas")
                        }
                        IconButton(onClick = { selectedId = template.id; editing = template; showEditor = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Editar")
                        }
                        IconButton(onClick = {
                            templates.removeAll { it.id == template.id }
                            if (templates.isEmpty()) templates.add(TicketTemplate(name = "Ticket de ejemplo"))
                            selectedId = templates.first().id
                            persist()
                        }) { Icon(Icons.Default.Delete, contentDescription = "Eliminar") }
                    }
                }
            }
            item {
                Text("Vista previa", style = MaterialTheme.typography.titleLarge)
                Card(Modifier.fillMaxWidth()) {
                    Text(selected?.content ?: "Creá una plantilla para empezar", Modifier.fillMaxWidth().padding(16.dp))
                }
                Button(
                    onClick = {
                        notice = selected?.let { UsbEscPosPrinter.print(context, it) }
                            ?: "Primero creá o seleccioná una plantilla."
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Imprimir ticket por USB") }
                Text("Las plantillas se guardan localmente en este dispositivo.")
            }
        }
    }

    if (showEditor) {
        TemplateEditor(
            initial = editing,
            onDismiss = { showEditor = false },
            onSave = { name, content, width ->
                val old = editing
                if (old == null) {
                    val created = TicketTemplate(name = name, content = content, paperWidth = width)
                    templates.add(created)
                    selectedId = created.id
                } else {
                    val index = templates.indexOfFirst { it.id == old.id }
                    if (index >= 0) templates[index] = old.copy(name = name, content = content, paperWidth = width)
                    selectedId = old.id
                }
                persist()
                showEditor = false
            }
        )
    }
    notice?.let { message ->
        AlertDialog(onDismissRequest = { notice = null }, title = { Text("Aviso") }, text = { Text(message) },
            confirmButton = { Button(onClick = { notice = null }) { Text("Aceptar") } })
    }
}

@Composable
private fun TemplateEditor(
    initial: TicketTemplate?,
    onDismiss: () -> Unit,
    onSave: (String, String, Int) -> Unit
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name ?: "") }
    var content by remember(initial?.id) { mutableStateOf(initial?.content ?: "") }
    var width by remember(initial?.id) { mutableIntStateOf(initial?.paperWidth ?: 80) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nueva plantilla" else "Editar plantilla") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nombre") }, singleLine = true)
                OutlinedTextField(value = content, onValueChange = { content = it }, label = { Text("Contenido del ticket") }, minLines = 5)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { width = 58 }) { Text("58 mm") }
                    OutlinedButton(onClick = { width = 80 }) { Text("80 mm") }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(name.trim().ifBlank { "Plantilla sin nombre" }, content, width) }) { Text("Guardar") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
