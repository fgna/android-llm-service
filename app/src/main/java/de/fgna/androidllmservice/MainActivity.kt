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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import de.fgna.androidllmservice.ui.LlmCompactMeta
import de.fgna.androidllmservice.ui.LlmContextStrip
import de.fgna.androidllmservice.ui.LlmHairlineSurface
import de.fgna.androidllmservice.ui.LlmIndicatorState
import de.fgna.androidllmservice.ui.LlmSectionLabel
import de.fgna.androidllmservice.ui.LlmServiceTheme
import java.util.Locale
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val modelStore by lazy { ModelStore(this) }
    private val runtime by lazy { LlmRuntimeProvider.get(this) }
    private val diagnostics by lazy { ModelDiagnostics(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LlmServiceTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
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
}

@Composable
private fun AppScreen(
    initialModel: RegisteredModel?,
    onRegisterModel: (Uri, (Result<RegisteredModel>) -> Unit) -> Unit,
    onClearModel: ((() -> Unit) -> Unit),
    onGenerate: (RegisteredModel, String, (Result<GenerationResult>) -> Unit) -> Unit,
    onDiagnose: (RegisteredModel, (Result<ModelDiagnosticResult>) -> Unit) -> Unit,
) {
    val isGerman = Locale.getDefault().language == "de"
    fun tr(de: String, en: String): String = if (isGerman) de else en

    var model by remember { mutableStateOf(initialModel) }
    var prompt by remember(isGerman) {
        mutableStateOf(
            tr(
                "Antworte kurz auf Deutsch: Nenne drei Vorteile lokaler LLMs auf einem Smartphone.",
                "Answer briefly in English: Name three advantages of local LLMs on a smartphone.",
            ),
        )
    }
    var response by remember { mutableStateOf("") }
    var status by remember(isGerman) { mutableStateOf(tr("Bereit", "Ready")) }
    var diagnostic by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true
            failed = false
            status = tr("Modell wird importiert …", "Importing model …")
            response = ""
            diagnostic = ""
            onRegisterModel(uri) { result ->
                busy = false
                result.onSuccess {
                    model = it
                    failed = false
                    status = tr("Modell importiert", "Model imported")
                }.onFailure {
                    failed = true
                    status = it.message ?: tr("Modell konnte nicht importiert werden.", "Model could not be imported.")
                }
            }
        }
    }

    val current = model
    val headerState = when {
        failed -> LlmIndicatorState.Warning
        busy -> LlmIndicatorState.Neutral
        current != null -> LlmIndicatorState.Positive
        else -> LlmIndicatorState.Neutral
    }
    val headerLabel = when {
        failed -> tr("Fehler", "Error")
        busy -> status
        current != null -> tr("Modell bereit", "Model ready")
        else -> tr("Kein Modell", "No model")
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
                Column(modifier = Modifier.statusBarsPadding()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Android LLM Service", style = MaterialTheme.typography.titleLarge)
                            LlmContextStrip(headerLabel, state = headerState)
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = { showHelp = !showHelp }) {
                            Text(if (showHelp) tr("Zurück", "Back") else tr("Hilfe", "Help"))
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (showHelp) {
                GuideScreen(::tr)
            } else {
            LlmHairlineSurface(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        LlmSectionLabel(tr("Modell", "Model"), modifier = Modifier.weight(1f))
                        Text(
                            if (current == null) tr("Nicht konfiguriert", "Not configured") else tr("Zentrale Kopie aktiv", "Shared copy active"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (current == null) {
                        Text(tr("Kein Modell importiert.", "No model imported."), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            tr(
                                "Der Service verwaltet eine zentrale lokale .litertlm-Modellkopie für alle Clients.",
                                "The service manages one shared local .litertlm model copy for all clients.",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(current.displayName, style = MaterialTheme.typography.titleMedium)
                        current.sizeBytes?.let { LlmCompactMeta(tr("Größe", "Size"), formatSize(it)) }
                        LlmCompactMeta("URI", current.uri.toString())
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            modifier = Modifier.weight(1f),
                            enabled = !busy,
                            onClick = { picker.launch(arrayOf("*/*")) },
                        ) {
                            Text(if (current == null) tr("Modell auswählen", "Select model") else tr("Modell wechseln", "Change model"))
                        }
                        if (current != null) {
                            OutlinedButton(
                                enabled = !busy,
                                onClick = {
                                    busy = true
                                    failed = false
                                    status = tr("Modell wird entfernt …", "Removing model …")
                                    onClearModel {
                                        model = null
                                        response = ""
                                        diagnostic = ""
                                        status = tr("Zentrale Modellkopie entfernt", "Shared model copy removed")
                                        busy = false
                                    }
                                },
                            ) { Text(tr("Entfernen", "Remove")) }
                        }
                    }

                    if (current != null) {
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !busy,
                            onClick = {
                                busy = true
                                failed = false
                                diagnostic = ""
                                status = tr("Modelldatei wird geprüft …", "Checking model file …")
                                onDiagnose(current) { result ->
                                    busy = false
                                    result.onSuccess {
                                        diagnostic = buildString {
                                            appendLine("${tr("SAF-Größe", "SAF size")}: ${it.declaredSizeBytes ?: -1}")
                                            appendLine("${tr("FD-Größe", "FD size")}: ${it.descriptorSizeBytes}")
                                            appendLine("/proc/self/fd ${tr("lesbar", "readable")}: ${it.procFdReadable}")
                                            appendLine("${tr("Erste 1 MiB identisch", "First 1 MiB identical")}: ${it.procFdFirstBytesMatch}")
                                            appendLine("SHA-256 ${tr("erste 1 MiB", "first 1 MiB")}: ${it.firstMiBSha256}")
                                            append("Header: ${it.firstBytesHex}")
                                        }
                                        failed = false
                                        status = tr("Diagnose abgeschlossen", "Diagnostics complete")
                                    }.onFailure {
                                        failed = true
                                        status = it.message ?: tr("Diagnose fehlgeschlagen", "Diagnostics failed")
                                    }
                                }
                            },
                        ) { Text(tr("Modelldatei prüfen", "Check model file")) }
                    }

                    if (diagnostic.isNotBlank()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text(diagnostic, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            LlmHairlineSurface(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        LlmSectionLabel("Prompt", modifier = Modifier.weight(1f))
                        Text(
                            if (current == null) tr("Modell erforderlich", "Model required") else tr("Lokale Inferenz", "Local inference"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy,
                        minLines = 5,
                        maxLines = 8,
                        textStyle = MaterialTheme.typography.bodyMedium,
                    )
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = current != null && prompt.isNotBlank() && !busy,
                        onClick = {
                            val selected = current ?: return@Button
                            busy = true
                            failed = false
                            response = ""
                            status = tr("LiteRT-LM läuft …", "LiteRT-LM running …")
                            onGenerate(selected, prompt) { result ->
                                busy = false
                                result.onSuccess {
                                    response = it.text.ifBlank { tr("(Leere Antwort)", "(Empty response)") }
                                    failed = false
                                    status = "${if (it.coldStart) "Cold start" else "Warm"} · Init ${it.initializationMillis} ms · Generation ${it.generationMillis} ms"
                                }.onFailure {
                                    failed = true
                                    status = it.message ?: tr("Inferenz fehlgeschlagen", "Inference failed")
                                }
                            }
                        },
                    ) { Text(if (busy) tr("Läuft …", "Running …") else tr("Lokal ausführen", "Run locally")) }
                }
            }

            LlmHairlineSurface(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            LlmSectionLabel("Output")
                            Text(
                                status,
                                style = MaterialTheme.typography.bodySmall,
                                color = when {
                                    failed -> MaterialTheme.colorScheme.error
                                    response.isNotBlank() -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                    Text(
                        response.ifBlank { if (busy) tr("Inferenz läuft …", "Inference running …") else tr("Noch keine Antwort.", "No response yet.") },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun GuideScreen(tr: (String, String) -> String) {
    GuideSection(
        tr("Erste Schritte", "Getting started"),
        tr(
            "1. Lade eine kompatible .litertlm-Modelldatei auf dein Smartphone.\n2. Tippe auf Modell auswählen und wähle die Datei. Der Service merkt sich den Dateizugriff und kopiert das Modell nicht.\n3. Prüfe bei Bedarf die Modelldatei.\n4. Gib im Prompt-Feld eine Frage ein und tippe auf Lokal ausführen.",
            "1. Put a compatible .litertlm model file on your phone.\n2. Tap Select model and choose the file. The service remembers access to it without copying the model.\n3. Optionally check the model file.\n4. Enter a question in the Prompt field and tap Run locally.",
        ),
    )
    GuideSection(
        tr("Andere Apps verbinden", "Connect other apps"),
        tr(
            "Kompatible Apps können dieselbe lokale Modell-Laufzeit verwenden. Installiere den Android LLM Service, wähle hier ein Modell und verwende die Integration der jeweiligen Client-App. Die Client-App muss die Berechtigung de.fgna.androidllmservice.permission.BIND_LLM_SERVICE deklarieren. Es ist keine Freigabe pro App nötig.",
            "Compatible apps can share this local model runtime. Install Android LLM Service, select a model here and use the integration in the client app. The client must declare the permission de.fgna.androidllmservice.permission.BIND_LLM_SERVICE. No per-app approval is required.",
        ),
    )
    GuideSection(
        tr("Bei Problemen", "Troubleshooting"),
        tr(
            "Kein Modell: Wähle zuerst eine .litertlm-Datei.\nDatei nicht lesbar: Tippe auf Modelldatei prüfen und wähle die Datei bei Bedarf erneut.\nGenerierung schlägt fehl: Prüfe, ob das Modell mit LiteRT-LM und deinem Gerät kompatibel ist und genügend Speicher verfügbar ist.\nClient findet den Service nicht: Prüfe Installation, Berechtigung und Modellstatus.",
            "No model: Select a .litertlm file first.\nFile unreadable: Tap Check model file and reselect the file if needed.\nGeneration fails: Check LiteRT-LM and device compatibility and available memory.\nClient cannot find the service: Check installation, permission and model readiness.",
        ),
    )
    GuideSection(
        tr("Datenschutz", "Privacy"),
        tr(
            "Die aktuelle Version verarbeitet Prompts lokal auf dem Gerät. Sie sendet Anfragen nicht automatisch an einen Cloud-Anbieter.",
            "The current version processes prompts locally on the device. It does not automatically send requests to a cloud provider.",
        ),
    )
}

@Composable
private fun GuideSection(title: String, body: String) {
    LlmHairlineSurface(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LlmSectionLabel(title)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun formatSize(bytes: Long): String = String.format("%.2f GB", bytes / 1024.0 / 1024.0 / 1024.0)
