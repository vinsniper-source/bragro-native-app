package com.bragro.mobile.ui.domain

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import com.bragro.mobile.data.model.ColumnConfig
import com.bragro.mobile.data.model.DomainConfig
import com.bragro.mobile.data.repo.ConfigRepository
import com.bragro.mobile.data.repo.RecordRepository
import com.bragro.mobile.data.repo.SaveResult
import kotlinx.coroutines.launch

class DomainFormViewModel(app: Application) : AndroidViewModel(app) {
    private val configRepository = ConfigRepository(app)
    private val recordRepository = RecordRepository(app)

    var config = mutableStateOf<DomainConfig?>(null)
        private set
    var lookupsByCategory = mutableStateOf<Map<String, List<LookupEntity>>>(emptyMap())
        private set
    var saving = mutableStateOf(false)
        private set
    var errorMessage = mutableStateOf<String?>(null)
        private set
    var offlineNotice = mutableStateOf<String?>(null)
        private set
    // "Copiar último lançamento" (Task #51/#77) -- só faz sentido ao CRIAR
    // (recordId == null); guarda o registro mais recente do módulo pra poder
    // pré-preencher o formulário com um toque, mesmo mecanismo genérico do
    // site (data-table.tsx), sem exceção por domínio.
    var lastRecord = mutableStateOf<Map<String, String?>?>(null)
        private set

    val fields = mutableStateMapOf<String, String>()

    fun load(domainId: String, recordId: String?) {
        viewModelScope.launch {
            val cfg = configRepository.domainConfig(domainId) ?: return@launch
            config.value = cfg

            val categories = cfg.columns.mapNotNull { it.lookupCategory }.distinct()
            val loaded = mutableMapOf<String, List<LookupEntity>>()
            for (cat in categories) loaded[cat] = configRepository.lookupsByCategory(cat)
            lookupsByCategory.value = loaded

            fields.clear()
            val existing = recordId?.let { recordRepository.getRecord(domainId, it) }
            for (col in cfg.columns) {
                if (col.computed) continue
                val raw = existing?.get(col.key) ?: ""
                // Datas chegam do servidor como timestamp ISO completo
                // ("2026-08-05T00:00:00.000Z") -- mostrar isso cru no campo
                // de edição é o que o usuário reportou como "as datas estão
                // incorretas junto com elas tem fuso horário". Corta pro
                // "AAAA-MM-DD" que o próprio placeholder do campo promete.
                fields[col.key] = if (col.type == "date") isoDateOnly(raw) else raw
            }

            lastRecord.value = if (recordId == null) recordRepository.mostRecent(domainId) else null
        }
    }

    fun setField(key: String, value: String) {
        fields[key] = value
    }

    /** Preenche todos os campos (exceto computados) com os valores do
     * lançamento mais recente do módulo -- o usuário ainda pode editar
     * qualquer campo antes de salvar, igual ao site. */
    fun copyFromLastRecord() {
        val last = lastRecord.value ?: return
        val cfg = config.value ?: return
        for (col in cfg.columns) {
            if (col.computed) continue
            val raw = last[col.key] ?: ""
            fields[col.key] = if (col.type == "date") isoDateOnly(raw) else raw
        }
    }

    fun save(domainId: String, recordId: String?, onDone: () -> Unit) {
        saving.value = true
        errorMessage.value = null
        offlineNotice.value = null
        viewModelScope.launch {
            val snapshot = fields.toMap()
            val result = if (recordId == null) recordRepository.createRecord(domainId, snapshot) else recordRepository.updateRecord(domainId, recordId, snapshot)
            saving.value = false
            when (result) {
                is SaveResult.SavedOnline -> onDone()
                is SaveResult.SavedOffline -> {
                    offlineNotice.value = "Sem conexão -- salvo no aparelho, sincroniza sozinho quando a internet voltar."
                    onDone()
                }
                is SaveResult.Failure -> errorMessage.value = result.message
            }
        }
    }
}

/** Formulario generico -- UMA tela cobre os 16 modulos, montada a partir do
 * DomainConfig (mesmo principio do site, ver components/domain/record-form.tsx).
 * Os campos "computed" (calculados pelo servidor -- rateio, vencimento,
 * numeracao de O.S. etc.) nao aparecem aqui: so existem depois da
 * sincronizacao, quando o servidor devolve o registro definitivo. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DomainFormScreen(
    domainId: String,
    recordId: String?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: DomainFormViewModel = viewModel(),
) {
    LaunchedEffect(domainId, recordId) { viewModel.load(domainId, recordId) }
    val config by viewModel.config
    val lookups by viewModel.lookupsByCategory
    val saving by viewModel.saving
    val error by viewModel.errorMessage
    val offlineNotice by viewModel.offlineNotice
    val lastRecord by viewModel.lastRecord

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (recordId == null) "Novo lançamento" else "Editar lançamento") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar") } },
            )
        },
    ) { padding ->
        val cfg = config
        if (cfg == null) {
            Column(modifier = Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.Center) {
                Text("Carregando...", modifier = Modifier.padding(24.dp))
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            if (cfg.notice != null) {
                Text(cfg.notice, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 12.dp))
            }

            // "Copiar último lançamento" (Task #51/#77): só ao criar
            // (recordId == null) e só quando já existe pelo menos um
            // registro no módulo -- mesma condição do site.
            if (recordId == null && lastRecord != null) {
                androidx.compose.material3.OutlinedButton(
                    onClick = { viewModel.copyFromLastRecord() },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                ) {
                    Icon(
                        Icons.Filled.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text("Copiar último lançamento")
                }
            }

            for (col in cfg.columns) {
                if (col.computed) continue
                FormField(col = col, options = col.lookupCategory?.let { lookups[it] }, viewModel = viewModel)
                androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 10.dp))
            }

            if (error != null) {
                Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            }
            if (offlineNotice != null) {
                Text(offlineNotice!!, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(top = 8.dp))
            }

            androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 16.dp))
            Button(
                onClick = { viewModel.save(domainId, recordId, onSaved) },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (saving) CircularProgressIndicator(modifier = Modifier.padding(2.dp)) else Text("Salvar")
            }
        }
    }
}

// col.label de toda coluna "money" JÁ vem com "(R$)" embutido (ver
// lib/domains/registry.ts no site, ex.: "Valor (R$)", "Bruto (R$)") --
// completar de novo aqui era a causa do "há 2 (R$) (R$)" relatado pelo
// usuário. Só o "*" de obrigatório é acrescentado.
private fun fieldLabel(col: ColumnConfig): String = col.label + if (col.required) " *" else ""

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormField(col: ColumnConfig, options: List<LookupEntity>?, viewModel: DomainFormViewModel) {
    val value = viewModel.fields[col.key] ?: ""

    when (col.type) {
        "checkbox" -> {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = value == "true", onCheckedChange = { viewModel.setField(col.key, it.toString()) })
                Text(col.label + if (col.required) " *" else "")
            }
        }
        "select" -> {
            var expanded by remember { mutableStateOf(false) }
            val optionLabels = options?.associate { it.value to it.label } ?: emptyMap()
            val staticOpts = col.staticOptions
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = optionLabels[value] ?: value,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(fieldLabel(col)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text("(vazio)") }, onClick = { viewModel.setField(col.key, ""); expanded = false })
                    if (staticOpts != null) {
                        for (opt in staticOpts) {
                            DropdownMenuItem(text = { Text(opt) }, onClick = { viewModel.setField(col.key, opt); expanded = false })
                        }
                    } else {
                        for (opt in options.orEmpty()) {
                            DropdownMenuItem(text = { Text(opt.label) }, onClick = { viewModel.setField(col.key, opt.value); expanded = false })
                        }
                    }
                }
            }
        }
        "number" -> {
            OutlinedTextField(
                value = value,
                onValueChange = { viewModel.setField(col.key, it) },
                label = { Text(fieldLabel(col)) },
                // Prefixo "R$" dentro do próprio campo pros monetários --
                // mesmo padrão do site (record-form.tsx), pedido do usuário
                // ("não tem valores convertidos em moedas"): antes só virava
                // R$ formatado DEPOIS de salvo, sem nenhuma pista enquanto
                // se digitava.
                prefix = if (col.money) ({ Text("R$ ") }) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        "date" -> {
            OutlinedTextField(
                value = value,
                onValueChange = { viewModel.setField(col.key, it) },
                label = { Text(fieldLabel(col)) },
                placeholder = { Text("AAAA-MM-DD") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        "textarea" -> {
            OutlinedTextField(
                value = value,
                onValueChange = { viewModel.setField(col.key, it) },
                label = { Text(fieldLabel(col)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
        }
        else -> {
            OutlinedTextField(
                value = value,
                onValueChange = { viewModel.setField(col.key, it) },
                label = { Text(fieldLabel(col)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
    }
    if (col.hint != null) {
        Text(col.hint, style = MaterialTheme.typography.bodySmall)
    }
}
