package com.bragro.mobile.ui.domain

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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bragro.mobile.data.model.BankImportRowDto
import com.bragro.mobile.data.repo.BankImportRepository
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

// Réplica mobile da aba "Extrato" dentro de Financeiro (bank-import-panel.tsx):
// escolhe um CSV exportado do internet banking pelo seletor nativo do Android
// (Storage Access Framework), detecta delimitador/colunas/duplicados no
// próprio aparelho (BankImportParser.kt, espelho 1:1 de
// services/bankimport.ts) e só fala com o servidor pra (1) checar duplicados
// já importados antes e (2) confirmar a gravação -- via /api/mobile/bank-import,
// que reaproveita as MESMAS Server Actions do site (confirmBankImportAction/
// getExistingSignatures). Nenhuma lógica de negócio nova.

data class PreviewRow(val row: ParsedBankRow, val include: Boolean, val duplicate: Boolean)

class BankImportViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = BankImportRepository(app)

    var banco by mutableStateOf("")
    var fileName = mutableStateOf<String?>(null)
        private set
    var rows = mutableStateOf<List<PreviewRow>>(emptyList())
        private set
    var busy = mutableStateOf(false)
        private set
    var errorMessage = mutableStateOf<String?>(null)
        private set
    var importedCount = mutableStateOf<Int?>(null)
        private set

    fun toggleInclude(index: Int, value: Boolean) {
        rows.value = rows.value.toMutableList().also { list ->
            list[index] = list[index].copy(include = value)
        }
    }

    fun onFileSelected(uri: Uri) {
        if (banco.isBlank()) {
            errorMessage.value = "Preencha o banco antes de escolher o arquivo."
            return
        }
        errorMessage.value = null
        importedCount.value = null
        busy.value = true
        viewModelScope.launch {
            val text = readFileSmart(uri)
            if (text.isNullOrBlank()) {
                errorMessage.value = "Não foi possível ler o arquivo selecionado (verifique se é um CSV)."
                busy.value = false
                return@launch
            }
            fileName.value = queryDisplayName(uri)
            val lines = text.split("\r\n", "\n").filter { it.isNotBlank() }
            if (lines.size < 2) {
                errorMessage.value = "Arquivo vazio ou sem lançamentos."
                busy.value = false
                return@launch
            }
            val delimiter = detectDelimiter(lines.first())
            val table = lines.map { parseCsvLine(it, delimiter) }
            val header = table.first()
            val body = table.drop(1)
            val roles = guessColumnRoles(header)
            val parsed = parseBankRows(body, roles)
            if (parsed.isEmpty()) {
                errorMessage.value = "Nenhum lançamento reconhecido nesse arquivo -- confira se é um extrato bancário em CSV."
                busy.value = false
                return@launch
            }

            val signatures = repository.signatures(banco.trim())
            if (signatures == null) {
                errorMessage.value = "Sem conexão -- não foi possível checar duplicados. Tente novamente."
                busy.value = false
                return@launch
            }
            val sigSet = signatures.toSet()
            rows.value = parsed.map { r ->
                val duplicate = sigSet.contains(rowSignature(banco.trim(), r))
                PreviewRow(row = r, include = !duplicate, duplicate = duplicate)
            }
            busy.value = false
        }
    }

    fun confirm() {
        val selected = rows.value.filter { it.include }
        if (selected.isEmpty() || banco.isBlank()) return
        busy.value = true
        errorMessage.value = null
        viewModelScope.launch {
            val dtos = selected.map { BankImportRowDto(data = it.row.dataIso, descricao = it.row.descricao, valor = it.row.valor) }
            val result = repository.confirm(banco.trim(), dtos)
            busy.value = false
            if (result == null) {
                errorMessage.value = "Não foi possível confirmar a importação. Tente novamente."
            } else {
                importedCount.value = result
                rows.value = emptyList()
                fileName.value = null
            }
        }
    }

    fun reset() {
        importedCount.value = null
        errorMessage.value = null
        rows.value = emptyList()
        fileName.value = null
    }

    private suspend fun readFileSmart(uri: Uri): String? = try {
        val bytes = getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() }
        if (bytes == null) null
        else try {
            String(bytes, Charsets.UTF_8)
        } catch (e: Exception) {
            String(bytes, charset("windows-1252"))
        }
    } catch (e: Exception) {
        null
    }

    private fun queryDisplayName(uri: Uri): String? = try {
        getApplication<Application>().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    } catch (e: Exception) {
        null
    }
}

private fun formatMoneyBrl(value: Double): String = NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(value)

private fun formatDateBr(iso: String): String {
    val parts = iso.split("-")
    return if (parts.size == 3) "${parts[2]}/${parts[1]}/${parts[0]}" else iso
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankImportScreen(onBack: () -> Unit, viewModel: BankImportViewModel = viewModel()) {
    val fileName by viewModel.fileName
    val rows by viewModel.rows
    val busy by viewModel.busy
    val errorMessage by viewModel.errorMessage
    val importedCount by viewModel.importedCount

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) viewModel.onFileSelected(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Extrato Bancário", color = MaterialTheme.colorScheme.primary)
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
                    "Envie um CSV exportado do internet banking. Detectamos delimitador, formato de número/data e possíveis duplicados automaticamente -- você confirma o que será lançado no Financeiro.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (importedCount != null) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("$importedCount lançamento(s) importado(s) para o Financeiro.", fontWeight = FontWeight.Bold)
                            Button(onClick = { viewModel.reset() }, modifier = Modifier.fillMaxWidth()) {
                                Text("Importar outro extrato")
                            }
                        }
                    }
                }
                return@LazyColumn
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = viewModel.banco,
                            onValueChange = { viewModel.banco = it },
                            label = { Text("Banco *") },
                            placeholder = { Text("Ex: Banco do Brasil") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedButton(
                            onClick = { filePicker.launch("*/*") },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(fileName ?: "Selecionar arquivo CSV")
                        }
                        if (busy) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                                Text("Processando...", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (errorMessage != null) {
                            Text(errorMessage ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            if (rows.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${rows.size} lançamentos encontrados -- ${rows.count { it.include }} selecionados",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                item {
                    Button(
                        onClick = { viewModel.confirm() },
                        enabled = !busy && rows.any { it.include },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Confirmar Importação")
                    }
                }
                items(rows.size) { i ->
                    val r = rows[i]
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = r.include, onCheckedChange = { viewModel.toggleInclude(i, it) })
                            Column(modifier = Modifier.weight(1f)) {
                                Text(r.row.descricao, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                Text(formatDateBr(r.row.dataIso), style = MaterialTheme.typography.labelSmall)
                                if (r.duplicate) {
                                    Text("Possível duplicado", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                } else {
                                    Text("Novo", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Text(
                                formatMoneyBrl(r.row.valor),
                                fontWeight = FontWeight.Bold,
                                color = if (r.row.valor >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}
