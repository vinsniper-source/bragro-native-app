package com.bragro.mobile.ui.nfe

import android.app.Application
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import com.bragro.mobile.ui.theme.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bragro.mobile.data.AppLog
import com.bragro.mobile.data.local.FarmEntity
import com.bragro.mobile.data.model.NfeImportedInvoiceData
import com.bragro.mobile.data.model.NfeItemData
import com.bragro.mobile.data.model.NfePreviewData
import com.bragro.mobile.data.repo.ConfigRepository
import com.bragro.mobile.data.repo.NfeImportRepository
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.NumberFormat
import java.util.Locale

// Fase 2 do app nativo (Task #40): importacao de XML de NF-e -- o usuario
// escolhe o arquivo XML pelo seletor nativo do Android (Storage Access
// Framework, sem pedir nenhuma permissao de armazenamento em tempo de
// execucao), o app le como texto puro (igual a file.text() no navegador) e
// manda pra /api/mobile/nfe-preview / /api/mobile/nfe-import -- que
// reaproveitam DIRETO previewXmlAction()/confirmXmlImportAction() do site
// (mesmo parser fast-xml-parser, mesma classificacao de categoria de
// Estoque, mesmo motor de rateio do Financeiro). Nenhuma logica de negocio
// de NF-e duplicada em Kotlin.
class NfeImportViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = NfeImportRepository(app)
    private val configRepository = ConfigRepository(app)

    var fileName = mutableStateOf<String?>(null)
        private set
    var xmlRaw = mutableStateOf<String?>(null)
        private set
    var preview = mutableStateOf<NfePreviewData?>(null)
        private set
    var farms = mutableStateOf<List<FarmEntity>>(emptyList())
        private set
    var selectedFarm = mutableStateOf<String?>(null)
        private set
    var loadingPreview = mutableStateOf(false)
        private set
    var importing = mutableStateOf(false)
        private set
    var errorMessage = mutableStateOf<String?>(null)
        private set
    var importedInvoice = mutableStateOf<NfeImportedInvoiceData?>(null)
        private set

    init {
        viewModelScope.launch {
            farms.value = configRepository.farms()
        }
    }

    fun setSelectedFarm(value: String) {
        selectedFarm.value = value
    }

    /** Le o arquivo escolhido pelo usuario como texto (mesmo formato que o
     * site espera de file.text() no navegador) e ja dispara a
     * pre-visualizacao. */
    fun onFileSelected(uri: Uri) {
        errorMessage.value = null
        preview.value = null
        importedInvoice.value = null
        viewModelScope.launch {
            val text = try {
                withContentResolver { resolver ->
                    resolver.openInputStream(uri)?.use { stream ->
                        BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).readText()
                    }
                }
            } catch (e: Exception) {
                AppLog.e("NfeImportScreen", "Falha ao ler arquivo XML de NF-e selecionado", e)
                null
            }
            if (text.isNullOrBlank()) {
                errorMessage.value = "Não foi possível ler o arquivo selecionado."
                return@launch
            }
            fileName.value = queryDisplayName(uri)
            xmlRaw.value = text
            loadPreview(text)
        }
    }

    private fun loadPreview(xml: String) {
        loadingPreview.value = true
        viewModelScope.launch {
            when (val result = repository.preview(xml)) {
                is NfeImportRepository.Result.Success -> {
                    preview.value = result.data
                    // Sugere a primeira fazenda cadastrada como destino
                    // padrao -- o usuario ainda pode trocar antes de
                    // confirmar.
                    if (selectedFarm.value == null) {
                        selectedFarm.value = farms.value.firstOrNull()?.name
                    }
                }
                is NfeImportRepository.Result.Error -> errorMessage.value = result.message
            }
            loadingPreview.value = false
        }
    }

    fun confirmImport() {
        val xml = xmlRaw.value ?: return
        val fazenda = selectedFarm.value
        if (fazenda.isNullOrBlank()) {
            errorMessage.value = "Escolha a fazenda de destino antes de confirmar."
            return
        }
        importing.value = true
        errorMessage.value = null
        viewModelScope.launch {
            when (val result = repository.confirmImport(xml, fazenda)) {
                is NfeImportRepository.Result.Success -> importedInvoice.value = result.data
                is NfeImportRepository.Result.Error -> errorMessage.value = result.message
            }
            importing.value = false
        }
    }

    /** Limpa tudo pra importar outro arquivo (depois de um sucesso ou pra
     * tentar de novo depois de um erro). */
    fun reset() {
        fileName.value = null
        xmlRaw.value = null
        preview.value = null
        errorMessage.value = null
        importedInvoice.value = null
    }

    private suspend fun <T> withContentResolver(block: (android.content.ContentResolver) -> T): T {
        return block(getApplication<Application>().contentResolver)
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            getApplication<Application>().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
        } catch (e: Exception) {
            AppLog.e("NfeImportScreen", "Falha ao consultar nome de exibição do arquivo XML de NF-e selecionado", e)
            null
        }
    }
}

private fun formatMoneyBrl(value: Double): String =
    NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(value)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FarmDropdown(value: String?, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value ?: "Selecione a fazenda",
            onValueChange = {},
            readOnly = true,
            label = { Text("Fazenda de destino") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (opt in options) {
                DropdownMenuItem(text = { Text(opt) }, onClick = { onSelect(opt); expanded = false })
            }
        }
    }
}

@Composable
private fun NfeItemRow(item: NfeItemData) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(item.descricao, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        Row(modifier = Modifier.fillMaxWidth()) {
            Text("${item.quantidade} ${item.unidade} x ${formatMoneyBrl(item.valorUnitario)}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Text(formatMoneyBrl(item.valorTotal), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        }
        if (!item.categoriaSugerida.isNullOrBlank()) {
            Text("Categoria sugerida (Estoque): ${item.categoriaSugerida}", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NfeImportScreen(onBack: () -> Unit, viewModel: NfeImportViewModel = viewModel()) {
    val fileName by viewModel.fileName
    val preview by viewModel.preview
    val farms by viewModel.farms
    val selectedFarm by viewModel.selectedFarm
    val loadingPreview by viewModel.loadingPreview
    val importing by viewModel.importing
    val errorMessage by viewModel.errorMessage
    val importedInvoice by viewModel.importedInvoice

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) viewModel.onFileSelected(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Importar NF-e (XML)", color = MaterialTheme.colorScheme.primary)
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "Escolha o arquivo XML da nota (o mesmo arquivo que a SEFAZ entrega, ou que você já usaria para importar pelo site).",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            item {
                OutlinedButton(onClick = { filePicker.launch("*/*") }, modifier = Modifier.fillMaxWidth()) {
                    Text(fileName ?: "Selecionar arquivo XML")
                }
            }

            if (importedInvoice != null) {
                val inv = importedInvoice!!
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Nota importada com sucesso", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Row { Text("Número: "); Text(inv.numero, fontWeight = FontWeight.Bold) }
                            if (!inv.emitenteNome.isNullOrBlank()) Row { Text("Emitente: "); Text(inv.emitenteNome, fontWeight = FontWeight.Bold) }
                            if (inv.valorTotal != null) Row { Text("Valor total: "); Text(formatMoneyBrl(inv.valorTotal), fontWeight = FontWeight.Bold) }
                            Text(
                                "Lançamentos de Estoque e Financeiro já foram gerados automaticamente, igual à importação pelo site.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Button(onClick = { viewModel.reset() }, modifier = Modifier.fillMaxWidth()) {
                                Text("Importar outro arquivo")
                            }
                        }
                    }
                }
            } else {
                if (errorMessage != null) {
                    item {
                        Text(errorMessage ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (loadingPreview) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                            Text("Lendo o XML...")
                        }
                    }
                }
                if (preview != null) {
                    val p = preview!!
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Pré-visualização", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Row { Text("Número/Série: "); Text("${p.numero}/${p.serie}", fontWeight = FontWeight.Bold) }
                                if (!p.chaveAcesso.isNullOrBlank()) Row { Text("Chave de acesso: "); Text(p.chaveAcesso, style = MaterialTheme.typography.labelSmall) }
                                Row { Text("Emitente: "); Text(p.emitenteNome, fontWeight = FontWeight.Bold) }
                                Row { Text("Valor total: "); Text(formatMoneyBrl(p.valorTotal), fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Itens (${p.items.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                                p.items.forEachIndexed { idx, item ->
                                    NfeItemRow(item)
                                    if (idx < p.items.size - 1) HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                                }
                            }
                        }
                    }
                    item {
                        FarmDropdown(selectedFarm, farms.map { it.name }) { viewModel.setSelectedFarm(it) }
                    }
                    item {
                        Button(
                            onClick = { viewModel.confirmImport() },
                            enabled = !importing && !selectedFarm.isNullOrBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (importing) CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                            else Text("Confirmar importação")
                        }
                    }
                    item {
                        Text(
                            "Confirmar cria a nota, os lançamentos de Estoque e o lançamento no Financeiro (com rateio) automaticamente -- confira os itens antes de continuar.",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}
