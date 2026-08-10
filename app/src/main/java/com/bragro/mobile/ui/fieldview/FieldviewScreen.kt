package com.bragro.mobile.ui.fieldview

import android.app.Application
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.CircularProgressIndicator
import com.bragro.mobile.ui.theme.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bragro.mobile.data.model.FieldBoundaryDto
import com.bragro.mobile.data.model.FieldviewResponse
import com.bragro.mobile.data.repo.FieldviewRepository
import com.bragro.mobile.ui.domain.displayValueFor
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

// Versão SÓ DADOS do FieldView (Task #107) -- talhões (com área calculada a
// partir do KML/KMZ já importado no site), status de Safra por talhão e
// status de Frota por máquina, tudo somente leitura. SEM mapa interativo
// nem importação de KML/KMZ aqui (o app não tem biblioteca de mapa
// nenhuma hoje -- ver justificativa completa em
// /api/mobile/fieldview/route.ts e no resumo desta rodada de tarefas).
class FieldviewViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = FieldviewRepository(app)

    var data = mutableStateOf<FieldviewResponse?>(null)
        private set
    var loading = mutableStateOf(false)
        private set

    fun load() {
        loading.value = true
        viewModelScope.launch {
            data.value = repository.fetch()
            loading.value = false
        }
    }
}

private val IGNORED_KEYS = setOf("id", "orgId", "criadoEm", "editadoEm", "updatedAt")

/** Mostra os campos primitivos de um registro cru (Safra/Frota), exceto
 * campos técnicos -- mesmo critério de "mostrar tudo que veio preenchido"
 * já usado no Ver de DomainListScreen, só que sem um ColumnConfig por trás
 * (esses dados não vêm do domínio genérico). */
@Composable
private fun RawRecordFields(obj: JsonObject) {
    Column {
        obj.entries
            .filter { (k, v) -> k !in IGNORED_KEYS && v.jsonPrimitive.contentOrNull?.isNotBlank() == true }
            .forEach { (k, v) ->
                Text(
                    "$k: ${displayValueFor(k, v.jsonPrimitive.contentOrNull ?: "", "text")}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
    }
}

@Composable
fun FieldviewScreen(onBack: () -> Unit, viewModel: FieldviewViewModel = viewModel()) {
    LaunchedEffect(Unit) { viewModel.load() }
    val data by viewModel.data
    val loading by viewModel.loading
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Talhões", "Máquinas", "Fazendas/KML")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("FieldView")
                    }
                },
                navigationIcon = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar") }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Text(
                "Dados de talhões, safra e máquinas -- o mapa interativo e a importação de KML/KMZ continuam só no site por enquanto.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            TabRow(selectedTabIndex = tab) {
                tabs.forEachIndexed { i, label ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) })
                }
            }
            when {
                loading -> Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.padding(top = 24.dp))
                }
                data == null -> Text("Sem conexão -- não foi possível carregar.", modifier = Modifier.padding(16.dp))
                else -> when (tab) {
                    0 -> TalhaoStatusList(data!!.talhaoStatus)
                    1 -> MaquinaStatusList(data!!.maquinaStatus)
                    else -> BoundariesList(data!!.boundaries)
                }
            }
        }
    }
}

@Composable
private fun TalhaoStatusList(rows: List<JsonObject>) {
    if (rows.isEmpty()) {
        Text("Nenhum lançamento de Safra com talhão preenchido ainda.", modifier = Modifier.padding(16.dp))
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        items(rows.size) { i ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(modifier = Modifier.padding(12.dp)) { RawRecordFields(rows[i]) }
            }
        }
    }
}

@Composable
private fun MaquinaStatusList(rows: List<JsonObject>) {
    if (rows.isEmpty()) {
        Text("Nenhum lançamento de Frota ainda.", modifier = Modifier.padding(16.dp))
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        items(rows.size) { i ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.DirectionsCar, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    }
                    RawRecordFields(rows[i])
                }
            }
        }
    }
}

@Composable
private fun BoundariesList(boundaries: List<FieldBoundaryDto>) {
    if (boundaries.isEmpty()) {
        Text("Nenhum talhão com contorno importado (KML/KMZ) ainda -- importe pelo site.", modifier = Modifier.padding(16.dp))
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        items(boundaries, key = { it.id }) { b ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text(b.nome ?: b.talhao, style = MaterialTheme.typography.titleSmall)
                    }
                    Text("Talhão: ${b.talhao}", style = MaterialTheme.typography.bodySmall)
                    b.areaHaCalc?.let { Text("Área calculada: $it ha", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}
