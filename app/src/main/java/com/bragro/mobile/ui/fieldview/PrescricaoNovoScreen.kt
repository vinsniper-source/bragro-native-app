package com.bragro.mobile.ui.fieldview

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bragro.mobile.data.model.PrescricaoFarmDto
import com.bragro.mobile.data.model.PrescricaoFeatureInput
import com.bragro.mobile.data.repo.PrescricaoRepository
import com.bragro.mobile.data.vra.IsoXmlSemZonasException
import com.bragro.mobile.data.vra.ZonaTaxaVra
import com.bragro.mobile.data.vra.parseIsoXmlZonas
import com.bragro.mobile.data.vra.zonaParaFeatureJson
import com.bragro.mobile.ui.theme.Card
import com.bragro.mobile.ui.theme.appFieldColors
import kotlinx.coroutines.launch

/**
 * "Nova Prescrição" (Task #651) -- pedido do usuário ("crie no native como
 * foi criado na plataforma"): até a v1.2.84 o app só EXIBIA prescrições já
 * salvas pelo site (ver PrescricaoScreen.kt), criar era exclusivo do site.
 *
 * Fluxo aqui: preenche os mesmos metadados do site (nome/produto/unidade/
 * safra/cultura/talhão/fazenda), importa um TASKDATA.XML (ISO-XML por
 * zonas de tratamento) direto do aparelho -- parser nativo em
 * IsoXmlVraParser.kt, réplica 1:1 de parseIsoXmlZonas() (lib/geo-vra.ts) --
 * e salva chamando o MESMO savePrescricaoAction do site (via
 * /api/mobile/prescricao, action "salvar"): nenhum cálculo de taxaMedia/
 * Min/Max duplicado em Kotlin.
 *
 * Import de .SHP continua exclusivo do site (shpjs no navegador decodifica
 * o shapefile binário + DBF; sem lib equivalente disponível em Kotlin,
 * ficou fora do escopo desta rodada -- mesmo critério das integrações
 * "aguardando parceria comercial" de FieldView/Drone).
 */
class PrescricaoNovoViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = PrescricaoRepository(app)

    var nome by mutableStateOf("")
    var produto by mutableStateOf("")
    var unidadeTaxa by mutableStateOf("")
    var safra by mutableStateOf("")
    var cultura by mutableStateOf("")
    var talhao by mutableStateOf("")
    var farmId by mutableStateOf<String?>(null)

    var farms = mutableStateOf<List<PrescricaoFarmDto>>(emptyList())
        private set
    var origemArquivo = mutableStateOf<String?>(null)
        private set
    var zonas = mutableStateOf<List<ZonaTaxaVra>>(emptyList())
        private set
    var errorMessage = mutableStateOf<String?>(null)
        private set
    var busy = mutableStateOf(false)
        private set
    var successId = mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch { farms.value = repository.listFarms() }
    }

    fun onIsoXmlImportado(nomeArquivo: String, zonasLidas: List<ZonaTaxaVra>) {
        origemArquivo.value = nomeArquivo
        zonas.value = zonasLidas
        errorMessage.value = null
    }

    fun onErroImportacao(mensagem: String) {
        errorMessage.value = mensagem
    }

    fun salvar() {
        if (nome.isBlank()) {
            errorMessage.value = "Informe um nome pra prescrição."
            return
        }
        if (zonas.value.isEmpty()) {
            errorMessage.value = "Importe um arquivo ISO-XML primeiro."
            return
        }
        busy.value = true
        errorMessage.value = null
        viewModelScope.launch {
            val features = zonas.value.map { zona ->
                val obj = zonaParaFeatureJson(zona)
                PrescricaoFeatureInput(type = "Feature", geometry = obj.getValue("geometry"), properties = obj["properties"])
            }
            val result = repository.salvar(
                nome = nome.trim(),
                produto = produto.trim().ifBlank { null },
                unidadeTaxa = unidadeTaxa.trim().ifBlank { null },
                safra = safra.trim().ifBlank { null },
                cultura = cultura.trim().ifBlank { null },
                talhao = talhao.trim().ifBlank { null },
                farmId = farmId,
                origemTipo = "ISO_XML",
                origemArquivo = origemArquivo.value,
                features = features,
            )
            busy.value = false
            result.onSuccess { id -> successId.value = id }
                .onFailure { e -> errorMessage.value = e.message ?: "Erro ao salvar prescrição." }
        }
    }

    fun reset() {
        nome = ""; produto = ""; unidadeTaxa = ""; safra = ""; cultura = ""; talhao = ""; farmId = null
        origemArquivo.value = null
        zonas.value = emptyList()
        successId.value = null
        errorMessage.value = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrescricaoNovoScreen(onBack: () -> Unit, viewModel: PrescricaoNovoViewModel = viewModel()) {
    val context = LocalContext.current
    val farms by viewModel.farms
    val origemArquivo by viewModel.origemArquivo
    val zonas by viewModel.zonas
    val errorMessage by viewModel.errorMessage
    val busy by viewModel.busy
    val successId by viewModel.successId
    var farmExpanded by remember { mutableStateOf(false) }

    val isoXmlPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                val zonasLidas = parseIsoXmlZonas(context, uri)
                val nomeArquivo = uri.lastPathSegment?.substringAfterLast('/') ?: "TASKDATA.xml"
                viewModel.onIsoXmlImportado(nomeArquivo, zonasLidas)
            } catch (e: IsoXmlSemZonasException) {
                viewModel.onErroImportacao(e.message ?: "Não foi possível importar este ISO-XML.")
            } catch (e: Exception) {
                viewModel.onErroImportacao("Erro ao importar ISO-XML.")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Nova Prescrição", color = MaterialTheme.colorScheme.primary)
                    }
                },
                navigationIcon = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (successId != null) {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Prescrição salva com sucesso", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.reset() }) { Text("Lançar outra") }
                    OutlinedButton(onClick = onBack) { Text("Voltar à lista") }
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "Importa um TASKDATA.XML (ISO-XML por zonas de tratamento) direto do aparelho. Importação de .SHP continua exclusiva do site.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (errorMessage != null) {
                item { Text(errorMessage ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
            item {
                OutlinedTextField(
                    value = viewModel.nome,
                    onValueChange = { viewModel.nome = it },
                    label = { Text("Nome *") },
                    placeholder = { Text("Ex.: Adubação zona norte") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = appFieldColors(),
                )
            }
            item {
                OutlinedTextField(
                    value = viewModel.produto,
                    onValueChange = { viewModel.produto = it },
                    label = { Text("Produto") },
                    placeholder = { Text("Ex.: Ureia") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = appFieldColors(),
                )
            }
            item {
                OutlinedTextField(
                    value = viewModel.unidadeTaxa,
                    onValueChange = { viewModel.unidadeTaxa = it },
                    label = { Text("Unidade da taxa") },
                    placeholder = { Text("Ex.: kg/ha") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = appFieldColors(),
                )
            }
            item {
                ExposedDropdownMenuBox(expanded = farmExpanded, onExpandedChange = { farmExpanded = it }) {
                    OutlinedTextField(
                        value = farms.find { it.id == viewModel.farmId }?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Fazenda (opcional)") },
                        placeholder = { Text("Não vincular") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = farmExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        colors = appFieldColors(),
                    )
                    ExposedDropdownMenu(expanded = farmExpanded, onDismissRequest = { farmExpanded = false }) {
                        DropdownMenuItem(text = { Text("Não vincular") }, onClick = { viewModel.farmId = null; farmExpanded = false })
                        for (f in farms) {
                            DropdownMenuItem(
                                text = { Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                onClick = { viewModel.farmId = f.id; farmExpanded = false },
                            )
                        }
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = viewModel.safra,
                    onValueChange = { viewModel.safra = it },
                    label = { Text("Safra") },
                    placeholder = { Text("Ex.: 2025/26") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = appFieldColors(),
                )
            }
            item {
                OutlinedTextField(
                    value = viewModel.cultura,
                    onValueChange = { viewModel.cultura = it },
                    label = { Text("Cultura") },
                    placeholder = { Text("Ex.: Soja") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = appFieldColors(),
                )
            }
            item {
                OutlinedTextField(
                    value = viewModel.talhao,
                    onValueChange = { viewModel.talhao = it },
                    label = { Text("Talhão") },
                    placeholder = { Text("Ex.: 12") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = appFieldColors(),
                )
            }
            item {
                OutlinedButton(
                    onClick = { isoXmlPicker.launch(arrayOf("text/xml", "application/xml", "*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Importar ISO-XML (.xml)")
                }
            }
            if (origemArquivo != null) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(origemArquivo ?: "", style = MaterialTheme.typography.labelSmall)
                            Text("${zonas.size} zona(s) de tratamento lida(s).", style = MaterialTheme.typography.bodyMedium)
                            val taxas = zonas.mapNotNull { it.taxa }
                            if (taxas.isNotEmpty()) {
                                Text(
                                    "Taxa mín–méd–máx: ${"%.2f".format(taxas.min())}–${"%.2f".format(taxas.average())}–${"%.2f".format(taxas.max())}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
            item {
                Button(onClick = { viewModel.salvar() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    if (busy) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("Salvar prescrição")
                }
            }
        }
    }
}
