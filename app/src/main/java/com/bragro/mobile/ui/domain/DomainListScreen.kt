package com.bragro.mobile.ui.domain

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bragro.mobile.data.model.DomainConfig
import com.bragro.mobile.data.repo.ConfigRepository
import com.bragro.mobile.data.repo.RecordRepository
import com.bragro.mobile.ui.print.HtmlPrinter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DomainListViewModel(app: Application) : AndroidViewModel(app) {
    private val configRepository = ConfigRepository(app)
    private val recordRepository = RecordRepository(app)

    var config = mutableStateOf<DomainConfig?>(null)
        private set
    var records = mutableStateOf<List<Map<String, String?>>>(emptyList())
        private set
    var refreshing = mutableStateOf(false)
        private set

    fun load(domainId: String) {
        viewModelScope.launch {
            config.value = configRepository.domainConfig(domainId)
            recordRepository.observeRecords(domainId).collectLatest { records.value = it }
        }
        refresh(domainId)
    }

    fun refresh(domainId: String) {
        refreshing.value = true
        viewModelScope.launch {
            recordRepository.refreshFromServer(domainId)
            refreshing.value = false
        }
    }
}

/** Uma unica tela de lista serve TODOS os 16 modulos -- guiada pelo
 * DomainConfig (mesma ideia do motor generico do site, ver
 * components/domain/data-table.tsx). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DomainListScreen(
    domainId: String,
    onBack: () -> Unit,
    onNewRecord: () -> Unit,
    onEditRecord: (String) -> Unit,
    viewModel: DomainListViewModel = viewModel(),
) {
    LaunchedEffect(domainId) { viewModel.load(domainId) }
    val config by viewModel.config
    val records by viewModel.records
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(config?.label ?: domainId) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar") }
                },
                actions = {
                    // Fase 2 (Task #41): imprime/exporta em PDF a lista
                    // atual (registros ja cacheados no Room) via o dialogo
                    // de impressao nativo do Android -- mesmo principio do
                    // botao "Exportar PDF" do site (tabela HTML + impressao
                    // do sistema, sem gerar PDF no servidor).
                    val cfg = config
                    if (cfg != null && records.isNotEmpty()) {
                        IconButton(onClick = { HtmlPrinter.printList(context, cfg, records) }) {
                            Icon(Icons.Filled.Print, contentDescription = "Imprimir / exportar PDF")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewRecord) { Icon(Icons.Filled.Add, contentDescription = "Novo lançamento") }
        },
    ) { padding ->
        val cfg = config
        if (cfg == null) {
            Column(modifier = Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.Center) {
                Text("Carregando...", modifier = Modifier.padding(24.dp))
            }
            return@Scaffold
        }
        if (records.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.Center) {
                Text("Nenhum lançamento ainda. Toque em + para adicionar.", modifier = Modifier.padding(24.dp))
            }
            return@Scaffold
        }
        LazyColumn(contentPadding = PaddingValues(12.dp, padding.calculateTopPadding() + 4.dp, 12.dp, 80.dp)) {
            items(records, key = { it["id"] ?: it.hashCode().toString() }) { record ->
                val recordId = record["id"]
                Card(
                    onClick = { if (recordId != null) onEditRecord(recordId) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        cfg.columns.filter { !it.hideInTable }.take(4).forEach { col ->
                            val value = record[col.key]
                            if (!value.isNullOrBlank()) {
                                Text("${col.label}: ${if (col.money) "R$ $value" else value}")
                            }
                        }
                    }
                }
            }
        }
    }
}
