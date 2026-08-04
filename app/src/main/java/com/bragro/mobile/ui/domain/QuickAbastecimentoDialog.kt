package com.bragro.mobile.ui.domain

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bragro.mobile.data.local.LookupEntity
import com.bragro.mobile.data.repo.ConfigRepository
import com.bragro.mobile.data.repo.RecordRepository
import com.bragro.mobile.data.repo.SaveResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ITENS_COMBUSTIVEL = listOf("DIESEL S10", "DIESEL S500", "ETANOL", "GASOLINA", "ARLA 32")

class QuickAbastecimentoViewModel(app: Application) : AndroidViewModel(app) {
    private val configRepository = ConfigRepository(app)
    private val recordRepository = RecordRepository(app)

    var frotas = mutableStateOf<List<LookupEntity>>(emptyList())
        private set
    var locais = mutableStateOf<List<LookupEntity>>(emptyList())
        private set
    var colaboradores = mutableStateOf<List<LookupEntity>>(emptyList())
        private set
    var saving = mutableStateOf(false)
        private set

    var frota by mutableStateOf("")
    var item by mutableStateOf(ITENS_COMBUSTIVEL[0])
    var local by mutableStateOf("")
    var qtd by mutableStateOf("")
    var unitario by mutableStateOf("")
    var horimetro by mutableStateOf("")
    var responsavel by mutableStateOf("")

    fun loadLookups() {
        viewModelScope.launch {
            frotas.value = configRepository.lookupsByCategory("frotas")
            locais.value = configRepository.lookupsByCategory("locais")
            colaboradores.value = configRepository.lookupsByCategory("colaboradores")
        }
    }

    /** "Copiar último abastecimento" -- mesmo critério do site (só entre
     * lançamentos de Frota com operacao=ABASTECIMENTO), calculado sobre os
     * registros já sincronizados no aparelho (Room), sem endpoint novo. */
    fun copyFromLastAbastecimento(onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            // Pega só o retrato ATUAL da lista (o app já sincronizou Frota
            // ao abrir a tela) -- first() encerra a coleta assim que o Room
            // devolve a primeira lista, em vez de ficar observando pra sempre.
            val records = recordRepository.observeRecords("frota").first()
            val match = records.firstOrNull { it["operacao"]?.trim()?.equals("ABASTECIMENTO", ignoreCase = true) == true }
            if (match != null) {
                frota = match["frota"] ?: frota
                item = match["item"] ?: item
                local = match["local"] ?: local
                qtd = match["qtd"] ?: qtd
                unitario = match["unitario"] ?: unitario
                horimetro = match["horimetro"] ?: horimetro
                responsavel = match["colaborador"] ?: responsavel
            }
            onDone(match != null)
        }
    }

    fun submit(onDone: (SaveResult) -> Unit) {
        if (frota.isBlank() || qtd.isBlank()) return
        saving.value = true
        val hoje = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val fields = buildMap {
            put("entrada", hoje)
            put("frota", frota)
            put("operacao", "ABASTECIMENTO")
            put("item", item)
            put("unidade", "LT")
            put("qtd", qtd)
            if (unitario.isNotBlank()) put("unitario", unitario)
            if (horimetro.isNotBlank()) put("horimetro", horimetro)
            if (local.isNotBlank()) put("local", local)
            if (responsavel.isNotBlank()) put("colaborador", responsavel)
        }
        viewModelScope.launch {
            val result = recordRepository.createRecord("frota", fields)
            saving.value = false
            onDone(result)
        }
    }
}

/** Réplica de QuickAbastecimentoButton (Dialog) -- lançamento rápido de
 * combustível em Frota, 2-3 toques (data/unidade/operação já vêm prontos). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAbastecimentoDialog(onDismiss: () -> Unit, onSaved: () -> Unit, viewModel: QuickAbastecimentoViewModel = viewModel()) {
    LaunchedEffect(Unit) { viewModel.loadLookups() }
    val frotas by viewModel.frotas
    val locais by viewModel.locais
    val colaboradores by viewModel.colaboradores
    val saving by viewModel.saving
    var copyMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Abastecimento rápido") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Só o essencial -- data, unidade e operação já ficam prontos automaticamente.")
                OutlinedButton(onClick = {
                    viewModel.copyFromLastAbastecimento { found ->
                        copyMessage = if (found) "Campos preenchidos com o último abastecimento -- confira antes de lançar." else "Nenhum abastecimento lançado ainda para copiar."
                    }
                }) { Text("Copiar último abastecimento") }
                if (copyMessage != null) Text(copyMessage!!, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)

                LookupDropdown("Máquina/Frota *", frotas, viewModel.frota) { viewModel.frota = it }
                LookupDropdown("Combustível", ITENS_COMBUSTIVEL.map { LookupEntity(category = "combustivel", value = it, label = it, order = 0) }, viewModel.item) { viewModel.item = it }
                OutlinedTextField(
                    value = viewModel.qtd,
                    onValueChange = { viewModel.qtd = it },
                    label = { Text("Litros *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = viewModel.unitario,
                    onValueChange = { viewModel.unitario = it },
                    label = { Text("R$/litro") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = viewModel.horimetro,
                    onValueChange = { viewModel.horimetro = it },
                    label = { Text("Horímetro") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LookupDropdown("Talhão/Fazenda (opcional)", locais, viewModel.local) { viewModel.local = it }
                LookupDropdown("Responsável (opcional)", colaboradores, viewModel.responsavel) { viewModel.responsavel = it }
            }
        },
        confirmButton = {
            Button(
                onClick = { viewModel.submit { onSaved() } },
                enabled = !saving && viewModel.frota.isNotBlank() && viewModel.qtd.isNotBlank(),
            ) {
                if (saving) CircularProgressIndicator(modifier = Modifier) else Text("Lançar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LookupDropdown(label: String, options: List<LookupEntity>, value: String, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val labelFor = options.associate { it.value to it.label }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = labelFor[value] ?: value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        androidx.compose.material3.ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(text = { Text(opt.label) }, onClick = { onChange(opt.value); expanded = false })
            }
        }
    }
}
