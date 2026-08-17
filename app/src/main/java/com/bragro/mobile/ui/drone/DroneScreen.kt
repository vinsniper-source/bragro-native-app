package com.bragro.mobile.ui.drone

import android.app.Application
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import com.bragro.mobile.ui.theme.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bragro.mobile.data.local.LookupEntity
import com.bragro.mobile.data.model.DroneRecordDto
import com.bragro.mobile.data.model.ProviderIntegrationDto
import com.bragro.mobile.data.repo.ConfigRepository
import com.bragro.mobile.data.repo.DroneRepository
import com.bragro.mobile.data.repo.DroneUploadRepository
import com.bragro.mobile.data.repo.IntegrationModule
import com.bragro.mobile.data.repo.ProviderIntegrationRepository
import com.bragro.mobile.ui.domain.IntegrationBusy
import com.bragro.mobile.ui.domain.ProviderIntegrationCard
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

// Tipos de captura (mesmo enum DroneCaptureType do Prisma) com rótulo
// amigável em pt-BR -- o servidor só aceita as chaves em MAIÚSCULO. Ordem
// alfabética pelo rótulo (pedido do usuário: "coloque todas as listas
// suspensas em ordem alfabética") -- mesma ordem de chaves usada no site
// (ver TIPOS_CAPTURA em drone/page.tsx).
private val TIPOS_CAPTURA = listOf(
    "APLICACAO_PULVERIZACAO" to "Aplicação/pulverização",
    "FOTO_AEREA" to "Foto aérea",
    "MAPEAMENTO_ORTOMOSAICO" to "Mapeamento ortomosaico",
    "NDVI_MULTIESPECTRAL" to "NDVI multiespectral",
    "OUTRO" to "Outro",
    "VIDEO" to "Vídeo",
)

private fun todayBr(): String = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(java.util.Date())

private fun brDateToIsoOrNull(br: String): String? {
    val m = Regex("^(\\d{2})/(\\d{2})/(\\d{4})$").find(br.trim()) ?: return null
    val (d, mo, y) = m.destructured
    return "$y-$mo-$d"
}

class DroneViewModel(app: Application) : AndroidViewModel(app) {
    private val configRepository = ConfigRepository(app)
    private val droneRepository = DroneRepository(app)
    private val uploadRepository = DroneUploadRepository(app)
    // Card "Acesso automático via prestadora de serviço" (Task #341/#54) --
    // ver ProviderIntegrationRepository.kt/ProviderIntegrationCard.kt.
    private val integrationRepository = ProviderIntegrationRepository(app, IntegrationModule.DRONE)

    var records = mutableStateOf<List<DroneRecordDto>>(emptyList())
        private set
    var loading = mutableStateOf(false)
        private set
    var talhoes = mutableStateOf<List<LookupEntity>>(emptyList())
        private set
    var saving = mutableStateOf(false)
        private set
    var errorMessage = mutableStateOf<String?>(null)
        private set
    var integration = mutableStateOf<ProviderIntegrationDto?>(null)
        private set
    var integrationBusy = mutableStateOf<IntegrationBusy?>(null)
        private set
    var integrationMessage = mutableStateOf<String?>(null)
        private set

    fun load() {
        loading.value = true
        viewModelScope.launch {
            talhoes.value = configRepository.lookupsByCategory("talhoes")
            records.value = droneRepository.list()
            loading.value = false
        }
        viewModelScope.launch { integration.value = integrationRepository.get() }
    }

    fun saveIntegration(provedor: String, apiKey: String) {
        integrationBusy.value = IntegrationBusy.SALVANDO
        integrationMessage.value = null
        viewModelScope.launch {
            val ok = integrationRepository.save(provedor, apiKey)
            integrationMessage.value = if (ok) "Credencial salva." else "Falha ao salvar credencial -- confira a conexão e tente de novo."
            if (ok) integration.value = integrationRepository.get()
            integrationBusy.value = null
        }
    }

    fun disconnectIntegration() {
        integrationBusy.value = IntegrationBusy.DESCONECTANDO
        integrationMessage.value = null
        viewModelScope.launch {
            val ok = integrationRepository.disconnect()
            if (ok) {
                integration.value = integrationRepository.get()
                integrationMessage.value = "Integração desconectada."
            } else {
                integrationMessage.value = "Falha ao desconectar -- confira a conexão e tente de novo."
            }
            integrationBusy.value = null
        }
    }

    fun syncIntegration() {
        integrationBusy.value = IntegrationBusy.SINCRONIZANDO
        integrationMessage.value = null
        viewModelScope.launch {
            val result = integrationRepository.sync()
            integrationMessage.value = result.mensagem
            integration.value = integrationRepository.get()
            integrationBusy.value = null
        }
    }

    fun submit(
        dataBr: String, talhao: String?, tipoCaptura: String, piloto: String?,
        altitude: String, areaCoberta: String, observacoes: String?,
        fileBytes: ByteArray, fileName: String, mimeType: String,
        onDone: (Boolean) -> Unit,
    ) {
        val iso = brDateToIsoOrNull(dataBr)
        if (iso == null) {
            errorMessage.value = "Data inválida -- use DD/MM/AAAA."
            onDone(false)
            return
        }
        saving.value = true
        errorMessage.value = null
        viewModelScope.launch {
            val uploaded = uploadRepository.upload(fileBytes, fileName, mimeType, talhao)
            if (uploaded == null) {
                saving.value = false
                errorMessage.value = "Falha ao subir o arquivo -- confira a conexão e tente de novo."
                onDone(false)
                return@launch
            }
            val result = droneRepository.create(
                data = "${iso}T00:00:00.000Z",
                talhao = talhao,
                tipoCaptura = tipoCaptura,
                piloto = piloto?.takeIf { it.isNotBlank() },
                altitude = altitude.toDoubleOrNull(),
                areaCoberta = areaCoberta.toDoubleOrNull(),
                observacoes = observacoes?.takeIf { it.isNotBlank() },
                storagePath = uploaded.storagePath,
                publicUrl = uploaded.publicUrl,
                fileSizeBytes = uploaded.fileSizeBytes,
            )
            saving.value = false
            result.onSuccess {
                records.value = listOf(it) + records.value
                onDone(true)
            }.onFailure {
                errorMessage.value = it.message ?: "Falha ao salvar registro."
                onDone(false)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DroneScreen(onBack: () -> Unit, viewModel: DroneViewModel = viewModel()) {
    LaunchedEffect(Unit) { viewModel.load() }
    val records by viewModel.records
    val loading by viewModel.loading
    val talhoes by viewModel.talhoes
    val saving by viewModel.saving
    val error by viewModel.errorMessage
    val integration by viewModel.integration
    val integrationBusy by viewModel.integrationBusy
    val integrationMessage by viewModel.integrationMessage
    var showNovo by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Drone", color = MaterialTheme.colorScheme.primary)
                    }
                },
                navigationIcon = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        androidx.compose.material3.IconButton(onClick = onBack) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            // Ainda mais acima -- pedido do usuário repetiu de novo ("suba
            // mais o botão drone"): 16dp não bastou, foi pra 32dp, agora 48dp.
            FloatingActionButton(onClick = { showNovo = true }, modifier = Modifier.padding(bottom = 48.dp)) {
                Icon(Icons.Filled.FlightTakeoff, contentDescription = "Novo registro de drone")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(
                "Registro de voos e capturas (foto, NDVI, ortomosaico, vídeo). Arquivos ficam no Supabase Storage (até 50MB).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            // Card "Acesso automático via prestadora de serviço" (Task
            // #341/#54) -- mesma posição/UX do site (topo da tela, ver
            // drone-client.tsx), única peça que ainda faltava no mobile.
            ProviderIntegrationCard(
                providers = listOf("DJI Cloud", "DJI Terra", "Outro", "Pix4Dfields", "XAG One"),
                descricao = "Hoje os voos são registrados manualmente com upload de arquivo. A credencial abaixo já fica salva com segurança; a sincronização automática ainda depende de aprovação de parceiro junto ao fabricante.",
                integration = integration,
                busy = integrationBusy,
                syncMessage = integrationMessage,
                onSave = { provedor, apiKey -> viewModel.saveIntegration(provedor, apiKey) },
                onDisconnect = { viewModel.disconnectIntegration() },
                onSync = { viewModel.syncIntegration() },
                modifier = Modifier.padding(bottom = 4.dp),
            )
            when {
                loading -> Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.padding(top = 24.dp))
                }
                records.isEmpty() -> Text("Nenhum registro ainda. Toque no botão para adicionar.", modifier = Modifier.padding(vertical = 24.dp))
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 80.dp)) {
                    items(records, key = { it.id }) { record -> DroneRecordCard(record) }
                }
            }
        }
    }

    if (showNovo) {
        NovoDroneRegistroDialog(
            talhoes = talhoes,
            saving = saving,
            error = error,
            // "Copiar último lançamento" (Task #197) -- pedido do usuário
            // ("novo lançamento que não tiver o ícone copiar último
            // lançamento coloque"): Drone tinha sua própria dialog (não
            // passa pela DomainFormScreen.kt genérica, que já tinha o
            // ícone), então ficou de fora do rollout anterior (Task #130).
            // Copia só os campos de texto -- o arquivo (foto/vídeo) precisa
            // ser escolhido de novo a cada registro, não dá pra "copiar".
            ultimoRegistro = records.firstOrNull(),
            onDismiss = { showNovo = false },
            onSubmit = { dataBr, talhao, tipo, piloto, altitude, area, obs, bytes, name, mime ->
                viewModel.submit(dataBr, talhao, tipo, piloto, altitude, area, obs, bytes, name, mime) { ok ->
                    if (ok) showNovo = false
                }
            },
        )
    }
}

@Composable
private fun DroneRecordCard(record: DroneRecordDto) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            val tipoLabel = TIPOS_CAPTURA.firstOrNull { it.first == record.tipoCaptura }?.second ?: record.tipoCaptura
            // Ícone + título -- achado real da re-auditoria (usuário: "faltam
            // os ícones nos dois módulos"): diferente de FieldView (que já
            // tinha ícone+label em TalhaoStatusList/MaquinaStatusList/
            // BoundariesList), este card do Drone era só texto solto, sem
            // NENHUM ícone -- a auditoria anterior tinha checado só o FAB/
            // botão de anexo e concluído (errado) que o módulo já estava OK.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.FlightTakeoff, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 8.dp))
                Text(tipoLabel, style = MaterialTheme.typography.titleSmall)
            }
            Text(isoDateOnlyDrone(record.data), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            record.talhao?.let { Text("Talhão: $it", style = MaterialTheme.typography.bodySmall) }
            record.piloto?.let { Text("Piloto: $it", style = MaterialTheme.typography.bodySmall) }
            record.altitude?.let { Text("Altitude: $it m", style = MaterialTheme.typography.bodySmall) }
            record.areaCoberta?.let { Text("Área coberta: $it ha", style = MaterialTheme.typography.bodySmall) }
            record.observacoes?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

private fun isoDateOnlyDrone(iso: String): String {
    val datePart = iso.take(10)
    val m = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$").find(datePart) ?: return datePart
    val (y, mo, d) = m.destructured
    return "$d/$mo/$y"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NovoDroneRegistroDialog(
    talhoes: List<LookupEntity>,
    saving: Boolean,
    error: String?,
    ultimoRegistro: DroneRecordDto?,
    onDismiss: () -> Unit,
    onSubmit: (dataBr: String, talhao: String?, tipo: String, piloto: String?, altitude: String, area: String, obs: String?, bytes: ByteArray, name: String, mime: String) -> Unit,
) {
    val context = LocalContext.current
    var dataBr by remember { mutableStateOf(todayBr()) }
    var talhao by remember { mutableStateOf("") }
    var tipo by remember { mutableStateOf(TIPOS_CAPTURA[0].first) }
    var piloto by remember { mutableStateOf("") }
    var altitude by remember { mutableStateOf("") }
    var area by remember { mutableStateOf("") }
    var obs by remember { mutableStateOf("") }
    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var pickedName by remember { mutableStateOf<String?>(null) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            pickedUri = uri
            pickedName = context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
            } ?: uri.lastPathSegment
        }
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Novo registro de drone", modifier = Modifier.weight(1f))
                // "Copiar último lançamento" -- pedido do usuário. Copia só
                // os campos de texto (Talhão/Tipo/Piloto/Altitude/Área/
                // Obs); o arquivo em si precisa ser escolhido de novo.
                if (ultimoRegistro != null) {
                    IconButton(onClick = {
                        talhao = ultimoRegistro.talhao ?: ""
                        tipo = ultimoRegistro.tipoCaptura
                        piloto = ultimoRegistro.piloto ?: ""
                        altitude = ultimoRegistro.altitude?.toString() ?: ""
                        area = ultimoRegistro.areaCoberta?.toString() ?: ""
                        obs = ultimoRegistro.observacoes ?: ""
                    }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copiar último lançamento", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = dataBr, onValueChange = { dataBr = it },
                    label = { Text("Data *") }, placeholder = { Text("DD/MM/AAAA") },
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                DroneLookupDropdown("Talhão", talhoes, talhao) { talhao = it }
                DroneStaticDropdown("Tipo de captura *", TIPOS_CAPTURA, tipo) { tipo = it }
                OutlinedTextField(value = piloto, onValueChange = { piloto = it }, label = { Text("Piloto") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = altitude, onValueChange = { altitude = it }, label = { Text("Altitude (m)") },
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = area, onValueChange = { area = it }, label = { Text("Área coberta (ha)") },
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(value = obs, onValueChange = { obs = it }, label = { Text("Observações") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                OutlinedButton(
                    onClick = { filePicker.launch(arrayOf("image/*", "video/*")) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.AttachFile, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text(pickedName ?: "Selecionar arquivo *")
                }
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                enabled = !saving && pickedUri != null && tipo.isNotBlank(),
                onClick = {
                    val uri = pickedUri ?: return@Button
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@Button
                    val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
                    onSubmit(dataBr, talhao.takeIf { it.isNotBlank() }, tipo, piloto, altitude, area, obs, bytes, pickedName ?: "arquivo", mime)
                },
            ) {
                if (saving) CircularProgressIndicator(modifier = Modifier.height(18.dp)) else Text("Salvar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancelar") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DroneLookupDropdown(label: String, options: List<LookupEntity>, value: String, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val labelFor = options.associate { it.value to it.label }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = labelFor[value] ?: value, onValueChange = {}, readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("(nenhum)") }, onClick = { onChange(""); expanded = false })
            options.forEach { opt -> DropdownMenuItem(text = { Text(opt.label) }, onClick = { onChange(opt.value); expanded = false }) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DroneStaticDropdown(label: String, options: List<Pair<String, String>>, value: String, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val labelFor = options.toMap()
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = labelFor[value] ?: value, onValueChange = {}, readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (key, lbl) -> DropdownMenuItem(text = { Text(lbl) }, onClick = { onChange(key); expanded = false }) }
        }
    }
}
