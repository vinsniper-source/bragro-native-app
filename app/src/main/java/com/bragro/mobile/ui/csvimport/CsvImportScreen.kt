package com.bragro.mobile.ui.csvimport

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bragro.mobile.data.AppLog
import com.bragro.mobile.data.model.CsvImportAnalyzeResponse
import com.bragro.mobile.data.model.CsvImportDomainOption
import com.bragro.mobile.data.model.CsvImportPlatformOption
import com.bragro.mobile.data.model.CsvImportRunResponse
import com.bragro.mobile.data.repo.CsvImportRepository
import com.bragro.mobile.ui.theme.Card
import com.bragro.mobile.ui.theme.SearchableDropdownField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

// Réplica mobile da tela "Migração de Dados (CSV)" do site
// (base-de-dados/importar/import-client.tsx), 3 passos: (1) módulo de destino +
// plataforma de origem + arquivo, (2) conferir o mapeamento coluna -> campo,
// (3) resultado. O parsing e a gravação rodam no SERVIDOR
// (/api/mobile/csv-import -> mesmas Server Actions do site); aqui só a
// interface. Tela exclusiva de OWNER/ADMIN (o botão só aparece pra eles em
// Base de Dados e o servidor também recusa outros papéis).

enum class CsvStep { ESCOLHER, MAPEAR, RESULTADO }

class CsvImportViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = CsvImportRepository(app)

    var step = mutableStateOf(CsvStep.ESCOLHER)
        private set
    var domains = mutableStateOf<List<CsvImportDomainOption>>(emptyList())
        private set
    var platforms = mutableStateOf<List<CsvImportPlatformOption>>(emptyList())
        private set
    var domainId = mutableStateOf("")
        private set
    var platformId = mutableStateOf("generica")
        private set
    var fileName = mutableStateOf<String?>(null)
        private set
    var analysis = mutableStateOf<CsvImportAnalyzeResponse?>(null)
        private set
    // coluna do CSV -> field key ("" = Não importar)
    var mapping = mutableStateOf<Map<String, String>>(emptyMap())
        private set
    var result = mutableStateOf<CsvImportRunResponse?>(null)
        private set
    var busy = mutableStateOf(false)
        private set
    var errorMessage = mutableStateOf<String?>(null)
        private set

    private var csvText: String = ""
    private var optionsLoaded = false

    fun loadOptions() {
        if (optionsLoaded) return
        busy.value = true
        viewModelScope.launch {
            val res = repository.listOptions()
            busy.value = false
            val data = res.data
            if (data == null) {
                errorMessage.value = res.error
                return@launch
            }
            optionsLoaded = true
            domains.value = data.domains
            platforms.value = data.platforms
            // Mesmo padrão do site: "Genérica" pré-selecionada (se existir).
            if (data.platforms.none { it.id == platformId.value }) {
                platformId.value = data.platforms.firstOrNull()?.id ?: ""
            }
        }
    }

    fun selectDomain(id: String) { domainId.value = id }
    fun selectPlatform(id: String) { platformId.value = id }
    fun setMapping(column: String, fieldKey: String) {
        mapping.value = mapping.value + (column to fieldKey)
    }

    /** Lê o arquivo escolhido e já pede a análise (sugestão de mapeamento) ao servidor. */
    fun onFileSelected(uri: Uri, name: String?) {
        if (domainId.value.isBlank()) {
            errorMessage.value = "Escolha o módulo de destino antes de enviar o arquivo."
            return
        }
        errorMessage.value = null
        busy.value = true
        viewModelScope.launch {
            val text = readFileSmart(uri)
            if (text.isNullOrBlank()) {
                errorMessage.value = "Não foi possível ler o arquivo selecionado (verifique se é um CSV)."
                busy.value = false
                return@launch
            }
            val res = repository.analyze(domainId.value, text, platformId.value.ifBlank { null })
            busy.value = false
            val data = res.data
            if (data == null) {
                errorMessage.value = res.error
                return@launch
            }
            if (data.totalRows == 0) {
                errorMessage.value = "Nenhuma linha com dado encontrada nesse CSV."
                return@launch
            }
            csvText = text
            fileName.value = name
            analysis.value = data
            mapping.value = data.columns.associateWith { col -> data.suggestedMapping[col] ?: "" }
            step.value = CsvStep.MAPEAR
        }
    }

    fun confirm() {
        if (analysis.value == null) return
        val finalMapping = mapping.value.filterValues { it.isNotBlank() }
        if (finalMapping.isEmpty()) {
            errorMessage.value = "Mapeie pelo menos uma coluna antes de importar."
            return
        }
        errorMessage.value = null
        busy.value = true
        viewModelScope.launch {
            val res = repository.run(domainId.value, csvText, finalMapping)
            busy.value = false
            val data = res.data
            if (data == null) {
                errorMessage.value = res.error
                return@launch
            }
            result.value = data
            step.value = CsvStep.RESULTADO
        }
    }

    fun backToChoose() {
        errorMessage.value = null
        step.value = CsvStep.ESCOLHER
    }

    fun reset() {
        step.value = CsvStep.ESCOLHER
        domainId.value = ""
        platformId.value = platforms.value.firstOrNull { it.id == "generica" }?.id ?: platforms.value.firstOrNull()?.id ?: ""
        fileName.value = null
        analysis.value = null
        mapping.value = emptyMap()
        result.value = null
        errorMessage.value = null
        csvText = ""
    }

    /** Igual readFileSmart do site: tenta UTF-8 ESTRITO (falha se houver byte
     * inválido) e, se falhar, decodifica como Windows-1252 (exports antigos). */
    private suspend fun readFileSmart(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val bytes = getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext null
            val text = try {
                Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString()
            } catch (e: CharacterCodingException) {
                // Fluxo normal de detecção de charset (não é erro de verdade).
                AppLog.w("CsvImportScreen", "CSV não é UTF-8 -- tentando windows-1252", e)
                String(bytes, charset("windows-1252"))
            }
            text.removePrefix("﻿") // BOM do Excel
        } catch (e: Exception) {
            AppLog.e("CsvImportScreen", "Falha ao ler o CSV selecionado", e)
            null
        }
    }
}

private fun queryDisplayName(context: android.content.Context, uri: Uri): String? = try {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
    }
} catch (e: Exception) {
    AppLog.e("CsvImportScreen", "Falha ao consultar nome do arquivo CSV", e)
    null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CsvImportScreen(onBack: () -> Unit, viewModel: CsvImportViewModel = viewModel()) {
    LaunchedEffect(Unit) { viewModel.loadOptions() }
    val context = androidx.compose.ui.platform.LocalContext.current
    val step by viewModel.step
    val domains by viewModel.domains
    val platforms by viewModel.platforms
    val domainId by viewModel.domainId
    val platformId by viewModel.platformId
    val fileName by viewModel.fileName
    val analysis by viewModel.analysis
    val mapping by viewModel.mapping
    val result by viewModel.result
    val busy by viewModel.busy
    val errorMessage by viewModel.errorMessage

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) viewModel.onFileSelected(uri, queryDisplayName(context, uri))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Migração de Dados (CSV)", color = MaterialTheme.colorScheme.primary)
                    }
                },
                navigationIcon = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = MaterialTheme.colorScheme.primary) }
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
                    "Importe registros de uma planilha exportada de outra plataforma. Valores de listas suspensas e fazendas já cadastrados são reaproveitados; o que não existir é criado em CAIXA ALTA.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (errorMessage != null) {
                item { Text(errorMessage ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }

            when (step) {
                CsvStep.ESCOLHER -> {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("1. Módulo de destino e arquivo", fontWeight = FontWeight.Bold)
                                val domainOptions = remember(domains) { domains.map { it.id to it.label } }
                                SearchableDropdownField(
                                    value = domainId,
                                    label = "Módulo de destino *",
                                    options = domainOptions,
                                    onSelect = { viewModel.selectDomain(it) },
                                    emptyOptionLabel = null,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                val platformOptions = remember(platforms) { platforms.map { it.id to it.label } }
                                SearchableDropdownField(
                                    value = platformId,
                                    label = "Plataforma de origem",
                                    options = platformOptions,
                                    onSelect = { viewModel.selectPlatform(it) },
                                    emptyOptionLabel = null,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                val hint = platforms.firstOrNull { it.id == platformId }?.hint
                                if (!hint.isNullOrBlank()) {
                                    Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                OutlinedButton(
                                    onClick = { filePicker.launch("*/*") },
                                    enabled = !busy && domainId.isNotBlank(),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Escolher arquivo CSV")
                                }
                                if (busy) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                                        Text("Processando...", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }

                CsvStep.MAPEAR -> {
                    val a = analysis
                    if (a != null) {
                        val domainLabel = domains.firstOrNull { it.id == domainId }?.label ?: domainId
                        item {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("2. Conferir o mapeamento", fontWeight = FontWeight.Bold)
                                    Text(
                                        "${fileName ?: "arquivo"} -- ${a.totalRows} linha(s) -> $domainLabel. Confira para qual campo do BRAgro cada coluna vai; campos com * são obrigatórios.",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                        val fieldOptions = a.fields.map { it.key to (it.label + if (it.required) " *" else "") }
                        items(a.columns, key = { it }) { col ->
                            // Primeiro valor não vazio da coluna, só de exemplo.
                            val example = a.sampleRows.firstNotNullOfOrNull { row -> row[col]?.takeIf { it.isNotBlank() } }
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(col, fontWeight = FontWeight.Bold)
                                    Text(
                                        "Exemplo: ${example ?: "(vazio)"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    SearchableDropdownField(
                                        value = mapping[col] ?: "",
                                        label = "Campo do BRAgro",
                                        options = fieldOptions,
                                        onSelect = { viewModel.setMapping(col, it) },
                                        emptyOptionLabel = "Não importar",
                                        placeholder = "Não importar",
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (busy) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                                        Text("Importando...", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                Button(
                                    onClick = { viewModel.confirm() },
                                    enabled = !busy && mapping.values.any { it.isNotBlank() },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Importar ${a.totalRows} linha(s)")
                                }
                                OutlinedButton(
                                    onClick = { viewModel.backToChoose() },
                                    enabled = !busy,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Voltar")
                                }
                            }
                        }
                    }
                }

                CsvStep.RESULTADO -> {
                    val r = result
                    if (r != null) {
                        item {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("3. Resultado", fontWeight = FontWeight.Bold)
                                    Text("${r.criados} registro(s) criado(s).", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Text("${r.lookupsReaproveitados} item(ns) de lista/fazenda reaproveitado(s).", style = MaterialTheme.typography.bodyMedium)
                                    Text("${r.lookupsNovos} item(ns) de lista/fazenda novo(s) criado(s).", style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "Os registros importados ficam marcados como pendentes de revisão.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        if (r.erros.isNotEmpty()) {
                            item {
                                Text("${r.erros.size} linha(s) com erro", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            }
                            items(r.erros) { e ->
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("Linha ${e.row}", fontWeight = FontWeight.Bold)
                                        Text(e.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                        item {
                            Button(onClick = { viewModel.reset() }, modifier = Modifier.fillMaxWidth()) {
                                Text("Importar outro arquivo")
                            }
                        }
                    }
                }
            }
        }
    }
}
