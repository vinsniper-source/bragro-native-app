package com.bragro.mobile.ui.basededados

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Sync
import com.bragro.mobile.ui.theme.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bragro.mobile.data.repo.BaseDeDadosRepository
import com.bragro.mobile.ui.theme.BrGreen
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class BaseDeDadosViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = BaseDeDadosRepository(app)

    var data = mutableStateOf<JsonObject?>(null)
        private set
    var loading = mutableStateOf(false)
        private set
    var busy = mutableStateOf(false)
        private set
    var errorMessage = mutableStateOf<String?>(null)
        private set

    fun load() {
        loading.value = true
        viewModelScope.launch {
            val result = repo.run("get")
            loading.value = false
            result.onSuccess { data.value = it?.jsonObject }.onFailure { errorMessage.value = it.message }
        }
    }

    private fun act(action: String, category: String? = null, value: String? = null, id: String? = null, ativo: Boolean? = null, name: String? = null, areaHa: Double? = null, onDone: (Boolean) -> Unit = {}) {
        busy.value = true
        viewModelScope.launch {
            val result = repo.run(action, category, value, id, ativo, name, areaHa)
            busy.value = false
            result.onSuccess { load(); onDone(true) }.onFailure { errorMessage.value = it.message; onDone(false) }
        }
    }

    fun addLookup(category: String, value: String) = act("add_lookup", category = category, value = value)
    fun deleteLookup(id: String) = act("delete_lookup", id = id)
    fun toggleLookup(id: String, ativo: Boolean) = act("toggle_lookup", id = id, ativo = ativo)
    fun importDefaults() = act("import_defaults")
    // "Recusar" -- pedido do usuário ("coloque o botão com a opção
    // recusar"), espelho de declineDefaultsAction no site: lapideia os
    // itens faltando agora, o aviso some sem importar nada.
    fun declineDefaults() = act("decline_defaults")
    fun addFarm(name: String, areaHa: Double) = act("add_farm", name = name, areaHa = areaHa)
    fun updateFarm(id: String, areaHa: Double) = act("update_farm", id = id, areaHa = areaHa)
    fun deleteFarm(id: String) = act("delete_farm", id = id)
    fun syncLocais() = act("sync_locais")
}

// Réplica 100% nativa de Base de Dados (src/app/(app)/base-de-dados/) --
// mesmo pedido do usuário de tela fixa no app, sem redirecionar pro site.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaseDeDadosScreen(onBack: () -> Unit, viewModel: BaseDeDadosViewModel = viewModel()) {
    LaunchedEffect(Unit) { viewModel.load() }
    val data by viewModel.data
    val loading by viewModel.loading
    val busy by viewModel.busy
    val error by viewModel.errorMessage

    Scaffold(
        topBar = {
            TopAppBar(
                // Seta+título uma linha abaixo + fonte verde -- padrão global
                // (pedido do usuário, ver comentário em SettingsScreen.kt).
                title = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Base de Dados", color = MaterialTheme.colorScheme.primary)
                    }
                },
                navigationIcon = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar", tint = MaterialTheme.colorScheme.primary) }
                    }
                },
            )
        },
    ) { padding ->
        if (loading && data == null) {
            Column(modifier = Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.Center) {
                Text("Carregando...", modifier = Modifier.padding(24.dp))
            }
            return@Scaffold
        }
        val missingCount = data?.get("missingCount")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
        val bySector = data?.get("bySector")?.jsonArray
        val farms = data?.get("farms")?.jsonArray
        // Confirmacao (Sim/Nao) antes de importar -- pedido do usuario
        // ("coloque a opcao nao tambem"), o botao antes disparava a
        // importacao direto no toque, sem chance de desistir. So traz
        // itens padrao que ainda NAO existem nesta organizacao e que o
        // usuario nunca excluiu de proposito (ver import_defaults/
        // LookupOptionExclusion em api/mobile/base-de-dados/route.ts).
        var showImportConfirm by remember { mutableStateOf(false) }
        // "Recusar" -- pedido do usuário ("coloque o botão com a opção
        // recusar"): diferente do "Não" do diálogo de Importar (que só
        // fecha o diálogo, o aviso continua aparecendo depois), este marca
        // os itens faltando como decisão definitiva de não importar -- o
        // aviso some de vez, reversível cadastrando na mão depois.
        var showDeclineConfirm by remember { mutableStateOf(false) }

        LazyColumn(
            contentPadding = PaddingValues(12.dp, padding.calculateTopPadding() + 4.dp, 12.dp, 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (error != null) {
                item(key = "error") { Text(error!!, color = MaterialTheme.colorScheme.error) }
            }
            if (missingCount > 0) {
                item(key = "import") {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Há $missingCount item(ns) padrão ainda não importados (categorias e/ou fazendas).", style = MaterialTheme.typography.bodyMedium)
                            Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { showImportConfirm = true }, enabled = !busy) {
                                    Text("Importar padrões")
                                }
                                TextButton(onClick = { showDeclineConfirm = true }, enabled = !busy) {
                                    Text("Recusar")
                                }
                            }
                        }
                    }
                }
            }
            item(key = "farms") {
                FarmsCard(
                    farms = farms,
                    busy = busy,
                    onAdd = { name, area -> viewModel.addFarm(name, area) },
                    onUpdate = { id, area -> viewModel.updateFarm(id, area) },
                    onDelete = { id -> viewModel.deleteFarm(id) },
                    onSync = { viewModel.syncLocais() },
                )
            }
            bySector?.forEach { sectorEl ->
                val sector = sectorEl.jsonObject
                val sectorId = sector["sector"]?.jsonPrimitive?.contentOrNull ?: "outros"
                val label = sector["label"]?.jsonPrimitive?.contentOrNull ?: "Outros"
                val categories = sector["categories"]?.jsonArray
                item(key = "sector_$sectorId") {
                    SectorCard(
                        label = label,
                        categories = categories,
                        busy = busy,
                        onAddValue = { category, value -> viewModel.addLookup(category, value) },
                        onToggle = { id, ativo -> viewModel.toggleLookup(id, ativo) },
                        onDelete = { id -> viewModel.deleteLookup(id) },
                    )
                }
            }
        }

        if (showImportConfirm) {
            AlertDialog(
                onDismissRequest = { showImportConfirm = false },
                title = { Text("Importar dados padrão?") },
                text = { Text("Isso vai trazer $missingCount item(ns) padrão (categorias/valores de listas suspensas e/ou fazendas) que ainda não existem nesta organização. Valores que você já excluiu de propósito não são repostos.") },
                confirmButton = {
                    TextButton(onClick = {
                        showImportConfirm = false
                        viewModel.importDefaults()
                    }) { Text("Sim, importar") }
                },
                dismissButton = {
                    TextButton(onClick = { showImportConfirm = false }) { Text("Não") }
                },
            )
        }
        if (showDeclineConfirm) {
            AlertDialog(
                onDismissRequest = { showDeclineConfirm = false },
                title = { Text("Recusar estes $missingCount item(ns) padrão?") },
                text = { Text("O aviso de importação para de aparecer para estes itens específicos (categorias/valores e/ou fazendas). Nada é apagado do que já existe — e você pode trazer qualquer um deles de volta depois, cadastrando manualmente.") },
                confirmButton = {
                    TextButton(onClick = {
                        showDeclineConfirm = false
                        viewModel.declineDefaults()
                    }) { Text("Sim, recusar") }
                },
                dismissButton = {
                    TextButton(onClick = { showDeclineConfirm = false }) { Text("Cancelar") }
                },
            )
        }
    }
}

@Composable
private fun CollapsibleCard(title: String, initiallyOpen: Boolean = false, content: @Composable () -> Unit) {
    var open by remember { mutableStateOf(initiallyOpen) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { open = !open },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = if (open) "Recolher" else "Expandir")
            }
            if (open) Column(modifier = Modifier.padding(top = 12.dp)) { content() }
        }
    }
}

@Composable
private fun FarmsCard(
    farms: kotlinx.serialization.json.JsonArray?,
    busy: Boolean,
    onAdd: (String, Double) -> Unit,
    onUpdate: (String, Double) -> Unit,
    onDelete: (String) -> Unit,
    onSync: () -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    var newArea by remember { mutableStateOf("") }

    CollapsibleCard("Fazendas (${farms?.size ?: 0})", initiallyOpen = true) {
        farms?.forEach { el ->
            val f = el.jsonObject
            val id = f["id"]?.jsonPrimitive?.contentOrNull ?: return@forEach
            val name = f["name"]?.jsonPrimitive?.contentOrNull ?: ""
            var areaText by remember(id) { mutableStateOf((f["areaHa"]?.jsonPrimitive?.doubleOrNull ?: 0.0).toString()) }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = areaText,
                    onValueChange = { areaText = it },
                    label = { Text("ha") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.width(100.dp),
                )
                IconButton(onClick = { areaText.toDoubleOrNull()?.let { onUpdate(id, it) } }, enabled = !busy) {
                    Icon(Icons.Filled.Check, contentDescription = "Salvar área")
                }
                IconButton(onClick = { onDelete(id) }, enabled = !busy) {
                    Icon(Icons.Filled.Delete, contentDescription = "Excluir fazenda")
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Nova fazenda") }, modifier = Modifier.weight(1f), singleLine = true)
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = newArea, onValueChange = { newArea = it }, label = { Text("ha") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.width(90.dp),
            )
        }
        Row(modifier = Modifier.padding(top = 8.dp)) {
            Button(
                onClick = {
                    val area = newArea.toDoubleOrNull()
                    if (newName.isNotBlank() && area != null && area > 0) { onAdd(newName.trim(), area); newName = ""; newArea = "" }
                },
                enabled = !busy,
            ) { Text("Adicionar") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onSync, enabled = !busy) { Text("Sincronizar locais") }
        }
        if (busy) CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun SectorCard(
    label: String,
    categories: kotlinx.serialization.json.JsonArray?,
    busy: Boolean,
    onAddValue: (String, String) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
) {
    CollapsibleCard(label) {
        categories?.forEach { catEl ->
            val cat = catEl.jsonObject
            val category = cat["category"]?.jsonPrimitive?.contentOrNull ?: return@forEach
            val values = cat["values"]?.jsonArray
            var newValue by remember(category) { mutableStateOf("") }

            Text(category, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
            values?.forEach { vEl ->
                val v = vEl.jsonObject
                val id = v["id"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                val vLabel = v["label"]?.jsonPrimitive?.contentOrNull ?: v["value"]?.jsonPrimitive?.contentOrNull ?: ""
                val ativo = v["ativo"]?.jsonPrimitive?.booleanOrNull ?: true
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    Text(vLabel, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = ativo, onCheckedChange = { onToggle(id, it) }, enabled = !busy, colors = SwitchDefaults.colors(checkedThumbColor = BrGreen, checkedTrackColor = BrGreen.copy(alpha = 0.5f)))
                    IconButton(onClick = { onDelete(id) }, enabled = !busy) { Icon(Icons.Filled.Delete, contentDescription = "Excluir") }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)) {
                OutlinedTextField(value = newValue, onValueChange = { newValue = it }, label = { Text("Novo valor") }, modifier = Modifier.weight(1f), singleLine = true)
                Spacer(Modifier.width(8.dp))
                Button(onClick = { if (newValue.isNotBlank()) { onAddValue(category, newValue.trim()); newValue = "" } }, enabled = !busy) { Text("+") }
            }
        }
    }
}
