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
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val modelStore by lazy { ModelStore(this) }
    private val runtime by lazy { LlmRuntimeProvider.get(this) }
    private val diagnostics by lazy { ModelDiagnostics(this) }
    private val clientAuthorizations by lazy { ClientAuthorizationStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LlmServiceTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppScreen(
                        initialModel = modelStore.current(),
                        initialClients = clientAuthorizations.clients(),
                        onRegisterModel = ::registerModel,
                        onClearModel = ::clearModel,
                        onGenerate = ::generate,
                        onDiagnose = ::diagnose,
                        onSetClientApproved = ::setClientApproved,
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

    private fun setClientApproved(
        packageName: String,
        approved: Boolean,
        callback: (List<ClientAuthorization>) -> Unit,
    ) {
        clientAuthorizations.setApproved(packageName, approved)
        callback(clientAuthorizations.clients())
    }
}

@Composable
private fun AppScreen(
    initialModel: RegisteredModel?,
    initialClients: List<ClientAuthorization>,
    onRegisterModel: (Uri, (Result<RegisteredModel>) -> Unit) -> Unit,
    onClearModel: ((() -> Unit) -> Unit),
    onGenerate: (RegisteredModel, String, (Result<GenerationResult>) -> Unit) -> Unit,
    onDiagnose: (RegisteredModel, (Result<ModelDiagnosticResult>) -> Unit) -> Unit,
    onSetClientApproved: (String, Boolean, (List<ClientAuthorization>) -> Unit) -> Unit,
) {
    var model by remember { mutableStateOf(initialModel) }
    var clients by remember { mutableStateOf(initialClients) }
    var prompt by remember { mutableStateOf("Antworte kurz auf Deutsch: Nenne drei Vorteile lokaler LLMs auf einem Smartphone.") }
    var response by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Bereit") }
    var diagnostic by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true
            failed = false
            status = "Modell wird importiert …"
            response = ""
            diagnostic = ""
            onRegisterModel(uri) { result ->
                busy = false
                result.onSuccess {
                    model = it
                    failed = false
                    status = "Modell importiert"
                }.onFailure {
                    failed = true
                    status = it.message ?: "Modell konnte nicht importiert werden."
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
        failed -> "Fehler"
        busy -> status
        current != null -> "Modell bereit"
        else -> "Kein Modell"
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
            LlmHairlineSurface(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        LlmSectionLabel("Modell", modifier = Modifier.weight(1f))
                        Text(
                            if (current == null) "Nicht konfiguriert" else "Zentrale Kopie aktiv",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (current == null) {
                        Text("Kein Modell importiert.", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Der Service verwaltet eine zentrale lokale .litertlm-Modellkopie für alle Clients.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(current.displayName, style = MaterialTheme.typography.titleMedium)
                        current.sizeBytes?.let { LlmCompactMeta("Größe", formatSize(it)) }
                        LlmCompactMeta("URI", current.uri.toString())
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            modifier = Modifier.weight(1f),
                            enabled = !busy,
                            onClick = { picker.launch(arrayOf("*/*")) },
                        ) {
                            Text(if (current == null) "Modell auswählen" else "Modell wechseln")
                        }
                        if (current != null) {
                            OutlinedButton(
                                enabled = !busy,
                                onClick = {
                                    busy = true
                                    failed = false
                                    status = "Modell wird entfernt …"
                                    onClearModel {
                                        model = null
                                        response = ""
                                        diagnostic = ""
                                        status = "Zentrale Modellkopie entfernt"
                                        busy = false
                                    }
                                },
                            ) { Text("Entfernen") }
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
                                        failed = false
                                        status = "Diagnose abgeschlossen"
                                    }.onFailure {
                                        failed = true
                                        status = it.message ?: "Diagnose fehlgeschlagen"
                                    }
                                }
                            },
                        ) { Text("Modelldatei prüfen") }
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
                        LlmSectionLabel("Clients", modifier = Modifier.weight(1f))
                        Text(
                            "${clients.count { it.approved }} freigegeben",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (clients.isEmpty()) {
                        Text("Keine Client-App gefunden.", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Client-Apps erscheinen hier, sobald sie die Binder-Berechtigung im Manifest deklarieren.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        clients.forEachIndexed { index, client ->
                            if (index > 0) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(3.dp),
                                    ) {
                                        Text(client.label, style = MaterialTheme.typography.titleMedium)
                                        LlmCompactMeta("Paket", client.packageName)
                                        LlmCompactMeta("Zertifikat", formatFingerprint(client.certificateSha256))
                                        Text(
                                            when {
                                                client.sameSigner -> "Automatisch freigegeben · gleiche Signatur"
                                                client.approved -> "Vom Nutzer freigegeben"
                                                else -> "Nicht freigegeben"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (client.approved) {
                                                MaterialTheme.colorScheme.tertiary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                        )
                                    }

                                    if (!client.sameSigner) {
                                        if (client.approved) {
                                            OutlinedButton(
                                                onClick = {
                                                    onSetClientApproved(client.packageName, false) { clients = it }
                                                },
                                            ) { Text("Entziehen") }
                                        } else {
                                            Button(
                                                onClick = {
                                                    onSetClientApproved(client.packageName, true) { clients = it }
                                                },
                                            ) { Text("Freigeben") }
                                        }
                                    }
                                }
                            }
                        }
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
                            if (current == null) "Modell erforderlich" else "Lokale Inferenz",
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
                            status = "LiteRT-LM läuft …"
                            onGenerate(selected, prompt) { result ->
                                busy = false
                                result.onSuccess {
                                    response = it.text.ifBlank { "(Leere Antwort)" }
                                    failed = false
                                    status = "${if (it.coldStart) "Cold start" else "Warm"} · Init ${it.initializationMillis} ms · Generation ${it.generationMillis} ms"
                                }.onFailure {
                                    failed = true
                                    status = it.message ?: "Inferenz fehlgeschlagen"
                                }
                            }
                        },
                    ) { Text(if (busy) "Läuft …" else "Lokal ausführen") }
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
                        response.ifBlank { if (busy) "Inferenz läuft …" else "Noch keine Antwort." },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String = String.format("%.2f GB", bytes / 1024.0 / 1024.0 / 1024.0)

private fun formatFingerprint(fingerprint: String): String =
    if (fingerprint.length <= 20) fingerprint else "${fingerprint.take(20)}…"
