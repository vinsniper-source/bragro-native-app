package com.bragro.mobile.ui.basededados

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Sync
import com.bragro.mobile.ui.theme.Card
import com.bragro.mobile.ui.theme.appFieldColors
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bragro.mobile.data.repo.BaseDeDadosRepository
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

    private fun act(action: String, category: String? = null, value: String? = null, id: String? = null, ativo: Boolean? = null, name: String? = null, areaHa: Double? = null, areaSafrinhaHa: Double? = null, areaSafrinhaMilhoHa: Double? = null, areaSafrinhaSorgoHa: Double? = null, latitude: Double? = null, longitude: Double? = null, onDone: (Boolean) -> Unit = {}) {
        busy.value = true
        viewModelScope.launch {
            val result = repo.run(action, category, value, id, ativo, name, areaHa, areaSafrinhaHa, areaSafrinhaMilhoHa, areaSafrinhaSorgoHa, latitude, longitude)
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
    // areaSafrinhaHa (2ª safra, área menor que a área total) -- exceção de
    // schema autorizada (ver MEMORY.md), opcional, ao lado da área
    // principal (pedido do usuário). Alimenta o círculo do Canvas quando a
    // safra filtrada bate "SAFRINHA ..." (ver lib/services/canvas.ts).
    // areaSafrinhaMilhoHa/SorgoHa (5ª exceção de schema, ver MEMORY.md) --
    // área safrinha POR CULTURA, pra quando a fazenda planta Milho E Sorgo
    // na mesma safrinha, cada um com sua parte do total.
    fun addFarm(name: String, areaHa: Double, areaSafrinhaHa: Double? = null, areaSafrinhaMilhoHa: Double? = null, areaSafrinhaSorgoHa: Double? = null) =
        act("add_farm", name = name, areaHa = areaHa, areaSafrinhaHa = areaSafrinhaHa, areaSafrinhaMilhoHa = areaSafrinhaMilhoHa, areaSafrinhaSorgoHa = areaSafrinhaSorgoHa)
    // Localização (6ª exceção de schema, ver MEMORY.md) -- latitude/
    // longitude sempre viajam juntas (as duas null limpa, as duas
    // preenchidas define; validação de "uma sem a outra" já é feita no
    // backend, ver update_farm em api/mobile/base-de-dados/route.ts).
    fun updateFarm(id: String, areaHa: Double, areaSafrinhaHa: Double? = null, areaSafrinhaMilhoHa: Double? = null, areaSafrinhaSorgoHa: Double? = null, latitude: Double? = null, longitude: Double? = null) =
        act("update_farm", id = id, areaHa = areaHa, areaSafrinhaHa = areaSafrinhaHa, areaSafrinhaMilhoHa = areaSafrinhaMilhoHa, areaSafrinhaSorgoHa = areaSafrinhaSorgoHa, latitude = latitude, longitude = longitude)
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
        // Descrição item a item do que vai ser importado/recusado -- pedido
        // do usuário ("quando subir descreva quais são arquivos que serão
        // enviados"): antes os diálogos só mostravam a CONTAGEM
        // ($missingCount item(ns) padrão), sem dizer quais categorias/
        // fazendas de fato entram na leva -- ver missingLookupItems/
        // missingFarmNames em api/mobile/base-de-dados/route.ts.
        val missingSummary = remember(data) {
            val parts = mutableListOf<String>()
            data?.get("missingLookupItems")?.jsonArray?.forEach { el ->
                val obj = el.jsonObject
                val label = obj["label"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                val values = obj["values"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
                if (values.isNotEmpty()) parts += "• $label: ${values.joinToString(", ")}"
            }
            val farmNames = data?.get("missingFarmNames")?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
            if (farmNames.isNotEmpty()) parts += "• Fazendas: ${farmNames.joinToString(", ")}"
            parts.joinToString("\n")
        }
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
                    onAdd = { name, area, areaSafrinha, areaSafrinhaMilho, areaSafrinhaSorgo -> viewModel.addFarm(name, area, areaSafrinha, areaSafrinhaMilho, areaSafrinhaSorgo) },
                    onUpdate = { id, area, areaSafrinha, areaSafrinhaMilho, areaSafrinhaSorgo, latitude, longitude -> viewModel.updateFarm(id, area, areaSafrinha, areaSafrinhaMilho, areaSafrinhaSorgo, latitude, longitude) },
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
                text = {
                    Column {
                        Text("Isso vai trazer $missingCount item(ns) padrão que ainda não existem nesta organização. Valores que você já excluiu de propósito não são repostos.")
                        if (missingSummary.isNotBlank()) {
                            Text(missingSummary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                },
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
                text = {
                    Column {
                        Text("O aviso de importação para de aparecer para estes itens específicos. Nada é apagado do que já existe — e você pode trazer qualquer um deles de volta depois, cadastrando manualmente.")
                        if (missingSummary.isNotBlank()) {
                            Text(missingSummary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                },
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FarmsCard(
    farms: kotlinx.serialization.json.JsonArray?,
    busy: Boolean,
    onAdd: (String, Double, Double?, Double?, Double?) -> Unit,
    // 2 últimos parâmetros = latitude/longitude (6ª exceção de schema, ver
    // MEMORY.md) -- null/null limpa, ambos preenchidos define.
    onUpdate: (String, Double, Double?, Double?, Double?, Double?, Double?) -> Unit,
    onDelete: (String) -> Unit,
    onSync: () -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    var newArea by remember { mutableStateOf("") }
    // Área "safrinha" -- exceção de schema autorizada (ver MEMORY.md),
    // opcional, ao lado da área principal (pedido do usuário: "acrescente
    // um campo ao lado da área maior"). Alimenta o círculo do Canvas quando
    // a safra filtrada bate "SAFRINHA ..." (ver lib/services/canvas.ts).
    var newAreaSafrinha by remember { mutableStateOf("") }
    // Área safrinha POR CULTURA (5ª exceção de schema, ver MEMORY.md) -- pra
    // quando a fazenda planta Milho E Sorgo na mesma safrinha, cada um com
    // sua parte do total.
    var newAreaSafrinhaMilho by remember { mutableStateOf("") }
    var newAreaSafrinhaSorgo by remember { mutableStateOf("") }

    CollapsibleCard("Fazendas (${farms?.size ?: 0})", initiallyOpen = true) {
        farms?.forEach { el ->
            val f = el.jsonObject
            val id = f["id"]?.jsonPrimitive?.contentOrNull ?: return@forEach
            val name = f["name"]?.jsonPrimitive?.contentOrNull ?: ""
            var areaText by remember(id) { mutableStateOf((f["areaHa"]?.jsonPrimitive?.doubleOrNull ?: 0.0).toString()) }
            var areaSafrinhaText by remember(id) { mutableStateOf(f["areaSafrinhaHa"]?.jsonPrimitive?.doubleOrNull?.toString() ?: "") }
            var areaSafrinhaMilhoText by remember(id) { mutableStateOf(f["areaSafrinhaMilhoHa"]?.jsonPrimitive?.doubleOrNull?.toString() ?: "") }
            var areaSafrinhaSorgoText by remember(id) { mutableStateOf(f["areaSafrinhaSorgoHa"]?.jsonPrimitive?.doubleOrNull?.toString() ?: "") }
            // Localização real (6ª exceção de schema, ver MEMORY.md) -- um
            // único campo "lat, lon" (mesmo formato que o Google Maps mostra
            // ao tocar e segurar num ponto do mapa), espelhando o site.
            // Usada por resolveFarmCoords() (lib/services/weather.ts) pra
            // trocar o fallback fixo de clima (Tupaciguara/MG) pela
            // localização real assim que cadastrada aqui ou no site.
            val fLat = f["latitude"]?.jsonPrimitive?.doubleOrNull
            val fLon = f["longitude"]?.jsonPrimitive?.doubleOrNull
            var locationText by remember(id) { mutableStateOf(if (fLat != null && fLon != null) "$fLat, $fLon" else "") }
            // Layout em DUAS linhas (nome+excluir / campos de área com
            // rolagem horizontal) -- CORREÇÃO DE BUG REAL: com nome +
            // TOTAL(ha) + milho + sorgo + 2 ícones tudo numa Row só (sem
            // rolagem), a soma das larguras fixas (100+90+90dp de campos +
            // 2×48dp de ícones ≈ 376dp) já estourava a tela sozinha, sem
            // sobrar espaço nenhum pro nome (Modifier.weight(1f) ficava
            // espremido a ~0dp) -- o texto do nome então quebrava
            // caractere-por-caractere numa coluna quase invisível, criando
            // aquele vão vertical enorme reportado pelo usuário ("ficou todo
            // desconfigurado"), e o botão excluir saía cortado da tela.
            // Agora a 1ª linha (nome + excluir) sempre cabe, e a 2ª linha
            // (campos numéricos + salvar) rola horizontalmente se precisar
            // -- nunca mais estoura nem esmaga o nome, em qualquer largura
            // de tela.
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        name,
                        modifier = Modifier.weight(1f).basicMarquee(),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                    IconButton(onClick = { onDelete(id) }, enabled = !busy) {
                        Icon(Icons.Filled.Delete, contentDescription = "Excluir fazenda")
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    OutlinedTextField(
                        value = areaText,
                        onValueChange = { areaText = it },
                        label = { Text("TOTAL (ha)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.width(100.dp),
                        colors = appFieldColors(),
                    )
                    Spacer(Modifier.width(4.dp))
                    // Campo genérico "safrinha" REMOVIDO da UI -- pedido do
                    // usuário (X na screenshot): só Milho/Sorgo ficam
                    // visíveis agora. areaSafrinhaText continua existindo e
                    // sendo reenviado sem alteração em onUpdate
                    // (pré-populado do JSON acima), preservando o valor
                    // antigo no backend/fallback do Canvas pra fazendas que
                    // já tinham esse campo preenchido.
                    OutlinedTextField(
                        value = areaSafrinhaMilhoText,
                        onValueChange = { areaSafrinhaMilhoText = it },
                        label = { Text("milho") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.width(90.dp),
                        colors = appFieldColors(),
                    )
                    Spacer(Modifier.width(4.dp))
                    OutlinedTextField(
                        value = areaSafrinhaSorgoText,
                        onValueChange = { areaSafrinhaSorgoText = it },
                        label = { Text("sorgo") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.width(90.dp),
                        colors = appFieldColors(),
                    )
                    Spacer(Modifier.width(4.dp))
                    OutlinedTextField(
                        value = locationText,
                        onValueChange = { locationText = it },
                        label = { Text("lat, lon") },
                        singleLine = true,
                        modifier = Modifier.width(130.dp),
                        colors = appFieldColors(),
                    )
                    IconButton(
                        onClick = {
                            areaText.toDoubleOrNull()?.let { area ->
                                // "lat, lon" num campo só -- em branco limpa
                                // (null/null), preenchido precisa dos dois
                                // números separados por vírgula (mesma regra
                                // do site, ver saveFarmArea em
                                // base-de-dados-client.tsx).
                                val locTrim = locationText.trim()
                                var lat: Double? = null
                                var lon: Double? = null
                                if (locTrim.isNotEmpty()) {
                                    val partes = locTrim.split(",").map { it.trim() }
                                    lat = partes.getOrNull(0)?.toDoubleOrNull()
                                    lon = partes.getOrNull(1)?.toDoubleOrNull()
                                    if (partes.size != 2 || lat == null || lon == null) return@let
                                }
                                onUpdate(id, area, areaSafrinhaText.toDoubleOrNull(), areaSafrinhaMilhoText.toDoubleOrNull(), areaSafrinhaSorgoText.toDoubleOrNull(), lat, lon)
                            }
                        },
                        enabled = !busy,
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = "Salvar área")
                    }
                }
            }
        }
        // Mesmo layout em 2 linhas do bloco acima (nome / campos com
        // rolagem horizontal), mesmo motivo -- ver comentário completo ali.
        Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            OutlinedTextField(
                value = newName, onValueChange = { newName = it }, label = { Text("Nova fazenda") },
                modifier = Modifier.fillMaxWidth(), singleLine = true, colors = appFieldColors(),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp),
            ) {
                OutlinedTextField(
                    value = newArea, onValueChange = { newArea = it }, label = { Text("TOTAL (ha)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.width(100.dp),
                    colors = appFieldColors(),
                )
                Spacer(Modifier.width(4.dp))
                // Campo genérico "safrinha" REMOVIDO da UI pra fazendas
                // novas -- pedido do usuário (X na screenshot): só
                // Milho/Sorgo abaixo. newAreaSafrinha continua "" e nunca
                // populado (onAdd recebe null pra esse parâmetro), o que é
                // o comportamento correto pra cadastros novos daqui em
                // diante.
                OutlinedTextField(
                    value = newAreaSafrinhaMilho, onValueChange = { newAreaSafrinhaMilho = it }, label = { Text("milho") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.width(90.dp),
                    colors = appFieldColors(),
                )
                Spacer(Modifier.width(4.dp))
                OutlinedTextField(
                    value = newAreaSafrinhaSorgo, onValueChange = { newAreaSafrinhaSorgo = it }, label = { Text("sorgo") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.width(90.dp),
                    colors = appFieldColors(),
                )
            }
        }
        Row(modifier = Modifier.padding(top = 8.dp)) {
            Button(
                onClick = {
                    val area = newArea.toDoubleOrNull()
                    if (newName.isNotBlank() && area != null && area > 0) {
                        onAdd(newName.trim(), area, newAreaSafrinha.toDoubleOrNull(), newAreaSafrinhaMilho.toDoubleOrNull(), newAreaSafrinhaSorgo.toDoubleOrNull())
                        newName = ""; newArea = ""; newAreaSafrinha = ""; newAreaSafrinhaMilho = ""; newAreaSafrinhaSorgo = ""
                    }
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
                    Switch(checked = ativo, onCheckedChange = { onToggle(id, it) }, enabled = !busy, colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary, checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)))
                    IconButton(onClick = { onDelete(id) }, enabled = !busy) { Icon(Icons.Filled.Delete, contentDescription = "Excluir") }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)) {
                OutlinedTextField(value = newValue, onValueChange = { newValue = it }, label = { Text("Novo valor") }, modifier = Modifier.weight(1f), singleLine = true, colors = appFieldColors())
                Spacer(Modifier.width(8.dp))
                Button(onClick = { if (newValue.isNotBlank()) { onAddValue(category, newValue.trim()); newValue = "" } }, enabled = !busy) { Text("+") }
            }
        }
    }
}
