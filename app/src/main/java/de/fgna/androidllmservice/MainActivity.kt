package de.fgna.androidllmservice

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val modelStore by lazy { ModelStore(this) }
    private val runtime by lazy { LiteRtRuntime(this) }
    private val diagnostics by lazy { ModelDiagnostics(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppScreen(
                        initialModel = modelStore.current(),
                        onRegisterModel = ::registerModel,
                        onClearModel = ::clearModel,
                        onGenerate = ::generate,
                        onDiagnose = ::diagnose,
                    )
                }
            }
        }
    }

    private fun registerModel(uri: Uri, callback: (Result<RegisteredModel>) -> Unit) {
        lifecycleScope.launch {
            callback(runCatching {
                runtime.unload()
                modelStore.register(uri)
            })
        }
    }

    private fun clearModel(callback: () -> Unit) {
        lifecycleScope.launch {
            runtime.unload()
            modelStore.clear()
            callback()
        }
    }

    private fun generate(model: RegisteredModel, prompt: String, callback: (Result<GenerationResult>) -> Unit) {
        lifecycleScope.launch { callback(runCatching { runtime.generate(model, prompt) }) }
    }

    private fun diagnose(model: RegisteredModel, callback: (Result<ModelDiagnosticResult>) -> Unit) {
        lifecycleScope.launch { callback(runCatching { diagnostics.inspect(model) }) }
    }

    override fun onDestroy() {
        if (isFinishing) runtime.close()
        super.onDestroy()
    }
}

@Composable
private fun AppScreen(
    initialModel: RegisteredModel?,
    onRegisterModel: (Uri, (Result<RegisteredModel>) -> Unit) -> Unit,
    onClearModel: ((() -> Unit) -> Unit),
    onGenerate: (RegisteredModel, String, (Result<GenerationResult>) -> Unit) -> Unit,
    onDiagnose: (RegisteredModel, (Result<ModelDiagnosticResult>) -> Unit) -> Unit,
) {
    var model by remember { mutableStateOf(initialModel) }
    var prompt by remember { mutableStateOf("Antworte kurz auf Deutsch: Nenne drei Vorteile lokaler LLMs auf einem Smartphone.") }
    var response by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Bereit") }
    var diagnostic by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true
            status = "Modell wird registriert …"
            response = ""
            diagnostic = ""
            onRegisterModel(uri) { result ->
                busy = false
                result.onSuccess {
                    model = it
                    status = "Modell registriert. Keine Kopie angelegt."
                }.onFailure { status = it.message ?: "Modell konnte nicht registriert werden." }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Android LLM Service", style = MaterialTheme.typography.headlineMedium)
        Text("M0 · Ein vorhandenes .litertlm-Modell direkt verwenden, ohne es in den App-Speicher zu kopieren.")
        Text("Modell", style = MaterialTheme.typography.titleMedium)
        val current = model
        if (current == null) {
            Text("Kein Modell registriert")
        } else {
            Text(current.displayName)
            current.sizeBytes?.let { Text(formatSize(it), style = MaterialTheme.typography.bodySmall) }
            Text(current.uri.toString(), style = MaterialTheme.typography.bodySmall)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(enabled = !busy, onClick = { picker.launch(arrayOf("*/*")) }) {
                Text(if (current == null) "Modell auswählen" else "Modell wechseln")
            }
            if (current != null) {
                Button(enabled = !busy, onClick = {
                    busy = true
                    onClearModel {
                        model = null
                        response = ""
                        diagnostic = ""
                        status = "Modellreferenz entfernt"
                        busy = false
                    }
                }) { Text("Entfernen") }
            }
        }

        if (current != null) {
            Button(enabled = !busy, onClick = {
                busy = true
                diagnostic = ""
                status = "Modelldatei wird geprüft …"
                onDiagnose(current) { result ->
                    busy = false
                    result.onSuccess {
                        diagnostic = buildString {
                            appendLine("SAF-Größe: ${it.declaredSizeBytes ?: -1}")
                            appendLine("FD-Größe: ${it.descriptorSizeBytes}")
                            appendLine("/proc/self/fd lesbar: ${it.procFdReadable}")
                            appendLine("Erste 1 MiB identisch: ${it.procFdFirstBytesMatch}")
                            appendLine("SHA-256 erste 1 MiB: ${it.firstMiBSha256}")
                            append("Header: ${it.firstBytesHex}")
                        }
                        status = "Diagnose abgeschlossen"
                    }.onFailure { status = it.message ?: "Diagnose fehlgeschlagen" }
                }
            }) { Text("Modelldatei prüfen") }
        }

        if (diagnostic.isNotBlank()) Text(diagnostic, style = MaterialTheme.typography.bodySmall)

        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            label = { Text("Prompt") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
            minLines = 4,
        )

        Button(
            enabled = current != null && prompt.isNotBlank() && !busy,
            onClick = {
                val selected = current ?: return@Button
                busy = true
                response = ""
                status = "LiteRT-LM läuft …"
                onGenerate(selected, prompt) { result ->
                    busy = false
                    result.onSuccess {
                        response = it.text.ifBlank { "(Leere Antwort)" }
                        status = "${if (it.coldStart) "Cold start" else "Warm"}: Init ${it.initializationMillis} ms · Generation ${it.generationMillis} ms"
                    }.onFailure { status = it.message ?: "Inferenz fehlgeschlagen" }
                }
            },
        ) { Text("Lokal ausführen") }

        Text(status, style = MaterialTheme.typography.bodySmall)
        if (response.isNotBlank()) {
            Text("Antwort", style = MaterialTheme.typography.titleMedium)
            Text(response)
        }
    }
}

private fun formatSize(bytes: Long): String = String.format("%.2f GB", bytes / 1024.0 / 1024.0 / 1024.0)
