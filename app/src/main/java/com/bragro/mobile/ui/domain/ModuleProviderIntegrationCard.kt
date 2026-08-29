package com.bragro.mobile.ui.domain

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bragro.mobile.data.model.ProviderIntegrationDto
import com.bragro.mobile.data.repo.IntegrationModule
import com.bragro.mobile.data.repo.ProviderIntegrationRepository
import kotlinx.coroutines.launch

// Mesmo card "Acesso automático via prestadora de serviço" de FieldView/
// Drone (ver ProviderIntegrationCard.kt), agora pra bomba de combustível
// (Frota) e balança (Romaneios) -- pedido do usuário ("api para bomba de
// combustivel implemente e api implementado tambem para balanca"),
// aprovado como scaffolding tipo FieldView/Drone via AskUserQuestion.
// Diferente de FieldviewScreen/DroneScreen (telas próprias com seu próprio
// ViewModel), Frota e Romaneios são módulos genéricos (DomainListScreen.kt)
// -- então esta é uma pequena "cola" (2 ViewModels + 1 composable de
// entrada por domainId) que roteia pro mesmo card genérico, com o mesmo
// padrão de save/disconnect/sync usado em DroneScreen.kt.

class FrotaIntegrationViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ProviderIntegrationRepository(app, IntegrationModule.FROTA_COMBUSTIVEL)
    var integration = mutableStateOf<ProviderIntegrationDto?>(null)
        private set
    var busy = mutableStateOf<IntegrationBusy?>(null)
        private set
    var message = mutableStateOf<String?>(null)
        private set

    fun load() {
        viewModelScope.launch { integration.value = repo.get() }
    }

    fun save(provedor: String, apiKey: String) {
        busy.value = IntegrationBusy.SALVANDO
        message.value = null
        viewModelScope.launch {
            val ok = repo.save(provedor, apiKey)
            message.value = if (ok) "Credencial salva." else "Falha ao salvar credencial -- confira a conexão e tente de novo."
            if (ok) integration.value = repo.get()
            busy.value = null
        }
    }

    fun disconnect() {
        busy.value = IntegrationBusy.DESCONECTANDO
        message.value = null
        viewModelScope.launch {
            val ok = repo.disconnect()
            if (ok) {
                integration.value = repo.get()
                message.value = "Integração desconectada."
            } else {
                message.value = "Falha ao desconectar -- confira a conexão e tente de novo."
            }
            busy.value = null
        }
    }

    fun sync() {
        busy.value = IntegrationBusy.SINCRONIZANDO
        message.value = null
        viewModelScope.launch {
            val result = repo.sync()
            message.value = result.mensagem
            integration.value = repo.get()
            busy.value = null
        }
    }
}

class RomaneioIntegrationViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ProviderIntegrationRepository(app, IntegrationModule.ROMANEIO_BALANCA)
    var integration = mutableStateOf<ProviderIntegrationDto?>(null)
        private set
    var busy = mutableStateOf<IntegrationBusy?>(null)
        private set
    var message = mutableStateOf<String?>(null)
        private set

    fun load() {
        viewModelScope.launch { integration.value = repo.get() }
    }

    fun save(provedor: String, apiKey: String) {
        busy.value = IntegrationBusy.SALVANDO
        message.value = null
        viewModelScope.launch {
            val ok = repo.save(provedor, apiKey)
            message.value = if (ok) "Credencial salva." else "Falha ao salvar credencial -- confira a conexão e tente de novo."
            if (ok) integration.value = repo.get()
            busy.value = null
        }
    }

    fun disconnect() {
        busy.value = IntegrationBusy.DESCONECTANDO
        message.value = null
        viewModelScope.launch {
            val ok = repo.disconnect()
            if (ok) {
                integration.value = repo.get()
                message.value = "Integração desconectada."
            } else {
                message.value = "Falha ao desconectar -- confira a conexão e tente de novo."
            }
            busy.value = null
        }
    }

    fun sync() {
        busy.value = IntegrationBusy.SINCRONIZANDO
        message.value = null
        viewModelScope.launch {
            val result = repo.sync()
            message.value = result.mensagem
            integration.value = repo.get()
            busy.value = null
        }
    }
}

/** Ponto de entrada único -- chamado de DomainListScreen.kt só quando
 * domainId é "frota" ou "romaneios" (ver bloco "integracao" no ícone Dados
 * de cada um). Cada ramo usa seu próprio ViewModel (módulo diferente no
 * enum IntegrationModule), mas o mesmo ProviderIntegrationCard visual. */
@Composable
fun ModuleProviderIntegrationCard(domainId: String) {
    if (domainId == "frota") {
        val vm: FrotaIntegrationViewModel = viewModel()
        LaunchedEffect(Unit) { vm.load() }
        val integration by vm.integration
        val busy by vm.busy
        val message by vm.message
        ProviderIntegrationCard(
            providers = listOf("Tanque Certo", "Softlub", "Sistema Fácil Combustível", "Outro"),
            descricao = "Hoje os abastecimentos são registrados manualmente (Estoque/Frota). A credencial abaixo já fica salva com segurança; a leitura automática da bomba de combustível ainda depende de aprovação de parceiro junto ao fabricante.",
            integration = integration,
            busy = busy,
            syncMessage = message,
            onSave = { provedor, apiKey -> vm.save(provedor, apiKey) },
            onDisconnect = { vm.disconnect() },
            onSync = { vm.sync() },
        )
    } else if (domainId == "romaneios") {
        val vm: RomaneioIntegrationViewModel = viewModel()
        LaunchedEffect(Unit) { vm.load() }
        val integration by vm.integration
        val busy by vm.busy
        val message by vm.message
        ProviderIntegrationCard(
            providers = listOf("Toledo do Brasil", "Filizola", "Coti Balanças", "Outro"),
            descricao = "Hoje o peso do romaneio é registrado manualmente ou via foto do ticket (OCR). A credencial abaixo já fica salva com segurança; a leitura automática da balança ainda depende de aprovação de parceiro junto ao fabricante.",
            integration = integration,
            busy = busy,
            syncMessage = message,
            onSave = { provedor, apiKey -> vm.save(provedor, apiKey) },
            onDisconnect = { vm.disconnect() },
            onSync = { vm.sync() },
        )
    }
}
