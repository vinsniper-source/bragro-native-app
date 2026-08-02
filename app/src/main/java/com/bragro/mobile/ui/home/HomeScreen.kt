package com.bragro.mobile.ui.home

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bragro.mobile.data.local.DomainConfigEntity
import com.bragro.mobile.data.repo.AuthRepository
import com.bragro.mobile.data.repo.ConfigRepository
import com.bragro.mobile.data.repo.RecordRepository
import com.bragro.mobile.data.TokenStore
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val configRepository = ConfigRepository(app)
    private val recordRepository = RecordRepository(app)
    private val authRepository = AuthRepository(app)
    private val tokenStore = TokenStore(app)

    var domains = mutableStateOf<List<DomainConfigEntity>>(emptyList())
        private set
    var pendingCount = mutableStateOf(0)
        private set
    var syncing = mutableStateOf(false)
        private set
    var orgLabel = mutableStateOf("")
        private set

    init {
        viewModelScope.launch { configRepository.observeDomains().collectLatest { domains.value = it } }
        viewModelScope.launch { recordRepository.observePendingCount().collectLatest { pendingCount.value = it } }
        viewModelScope.launch { tokenStore.emailFlow.collectLatest { orgLabel.value = it ?: "" } }
    }

    fun syncNow() {
        if (syncing.value) return
        syncing.value = true
        viewModelScope.launch {
            recordRepository.syncAll()
            syncing.value = false
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout()
            onDone()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenDomain: (String) -> Unit,
    onOpenDashboard: () -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val domains by viewModel.domains
    val pending by viewModel.pendingCount
    val syncing by viewModel.syncing

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("BRAgro") },
                actions = {
                    // Fase 2 (Task #31): tela de Dashboard nativa, mesmos KPIs
                    // do Início do site (ver ui/dashboard/DashboardScreen.kt).
                    IconButton(onClick = onOpenDashboard) {
                        Icon(Icons.Filled.Dashboard, contentDescription = "Início / Dashboard")
                    }
                    IconButton(onClick = { viewModel.syncNow() }) {
                        if (syncing) CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                        else Icon(Icons.Filled.CloudSync, contentDescription = "Sincronizar agora")
                    }
                    IconButton(onClick = { viewModel.logout(onLoggedOut) }) {
                        Icon(Icons.Filled.Logout, contentDescription = "Sair")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (pending > 0) {
                Text(
                    "$pending lançamento(s) aguardando conexão para sincronizar.",
                    modifier = Modifier.padding(12.dp),
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(domains, key = { it.domainId }) { domain ->
                    Card(
                        onClick = { onOpenDomain(domain.domainId) },
                        modifier = Modifier.padding(4.dp),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(domain.label, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }
}
