package com.bragro.mobile.ui.analises

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bragro.mobile.data.repo.AnalisesRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Fase 2 do app nativo (Task #36): Analises cruzadas entre modulos,
// espelhando src/app/(app)/analises/analises-client.tsx no site (15
// cruzamentos: Planejado x Realizado x Pago, Custo/ha por fonte, Pedido x
// Recebimento, Consumo de Estoque, Clima x Produtividade, Pragas x
// Produtividade, Folha x Custo, Eficiencia de maquina etc.) -- via
// /api/mobile/analises, que reaproveita a MESMA getAnalisesCruzadas() do
// site. Renderizacao GENERICA (cada chave do JSON vira uma secao de
// cards com os campos brutos) em vez de modelar 15 formatos de linha
// diferentes em Kotlin -- mesmo principio do motor generico de
// lista/formulario ja usado nos 16 modulos (DomainListScreen/
// DomainFormScreen, guiados por DomainConfig em vez de 16 telas escritas
// a mao).
class AnalisesViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = AnalisesRepository(app)

    var analises = mutableStateOf<JsonObject?>(null)
        private set
    var safrasDisponiveis = mutableStateOf<List<String>>(emptyList())
        private set
    // Filtro de Cultura ao lado do de Safra -- pedido do usuário ("análises
    // coloque filtro cultura, dividindo a mesma linha com o filtro safra"),
    // mesmo padrão já usado no DRE (DreScreen.kt).
    var culturasDisponiveis = mutableStateOf<List<String>>(emptyList())
        private set
    var loading = mutableStateOf(false)
        private set
    var offline = mutableStateOf(false)
        private set
    var safra = mutableStateOf<String?>(null)
        private set
    var cultura = mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            repository.observeCached().collectLatest { entity ->
                if (entity != null) {
                    analises.value = repository.parse(entity)
                    safrasDisponiveis.value = repository.safras(entity)
                    culturasDisponiveis.value = repository.culturas(entity)
                }
            }
        }
        refresh()
    }

    fun setSafra(value: String?) {
        safra.value = value
        refresh()
    }

    fun setCultura(value: String?) {
        cultura.value = value
        refresh()
    }

    fun refresh() {
        if (loading.value) return
        loading.value = true
        viewModelScope.launch {
            val ok = repository.refresh(safra.value, cultura.value)
            offline.value = !ok
            loading.value = false
        }
    }
}

/** Rotulo de secao mais legivel: "planejadoVsRealizado" -> "Planejado Vs
 * Realizado". So cosmetico -- nao muda a chave usada para nada alem de
 * exibicao. */
private fun tituloSecao(chave: String): String {
    val comEspacos = chave.replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
    return comEspacos.replaceFirstChar { it.uppercase() }
}

private fun valorParaTexto(el: JsonElement): String = when (el) {
    is JsonNull -> "—"
    is JsonArray -> "${el.size} item(ns)"
    is JsonObject -> "${el.size} campo(s)"
    is JsonPrimitive -> el.content.ifBlank { "—" }
    else -> "—"
}

@Composable
private fun ObjetoCard(obj: JsonObject) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            obj.entries.forEach { (campo, valor) ->
                Row {
                    Text("$campo: ", style = MaterialTheme.typography.bodySmall)
                    Text(valorParaTexto(valor), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SecaoAnalise(chave: String, valor: JsonElement) {
    Column(modifier = Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(tituloSecao(chave), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        when (valor) {
            is JsonArray -> {
                if (valor.isEmpty()) {
                    Text("Sem dados.", style = MaterialTheme.typography.bodySmall)
                } else {
                    valor.forEach { item ->
                        if (item is JsonObject) ObjetoCard(item)
                    }
                }
            }
            is JsonObject -> ObjetoCard(valor)
            else -> Text(valorParaTexto(valor), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterDropdown(label: String, value: String?, options: List<String>, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value ?: "Todas",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Todas") }, onClick = { onSelect(null); expanded = false })
            for (opt in options) {
                DropdownMenuItem(text = { Text(opt) }, onClick = { onSelect(opt); expanded = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalisesScreen(onBack: () -> Unit, viewModel: AnalisesViewModel = viewModel()) {
    val analises by viewModel.analises
    val safrasDisponiveis by viewModel.safrasDisponiveis
    val culturasDisponiveis by viewModel.culturasDisponiveis
    val loading by viewModel.loading
    val offline by viewModel.offline
    val safra by viewModel.safra
    val cultura by viewModel.cultura

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Análises") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        if (loading) CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                        else Icon(Icons.Filled.Refresh, contentDescription = "Atualizar")
                    }
                },
            )
        },
    ) { padding ->
        val data = analises
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                // Cultura ao lado de Safra, mesma linha, blocos separados --
                // pedido do usuário ("análises coloque filtro cultura,
                // dividindo a mesma linha com o filtro safra, blocos
                // separados"), mesmo padrão do DRE (DreScreen.kt).
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        FilterDropdown("Safra", safra, safrasDisponiveis) { viewModel.setSafra(it) }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        FilterDropdown("Cultura", cultura, culturasDisponiveis) { viewModel.setCultura(it) }
                    }
                }
            }
            if (offline) {
                item {
                    Text(
                        "Sem conexão -- mostrando o último resultado salvo neste aparelho.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (data == null) {
                item {
                    Text(if (loading) "Carregando..." else "Sem dados ainda. Conecte-se à internet e atualize.")
                }
            } else {
                data.entries.forEachIndexed { index, (chave, valor) ->
                    item(key = chave) { SecaoAnalise(chave, valor) }
                    if (index < data.entries.size - 1) {
                        item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }
                    }
                }
            }
        }
    }
}
