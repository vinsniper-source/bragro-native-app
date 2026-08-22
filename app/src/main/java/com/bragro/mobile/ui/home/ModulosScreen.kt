package com.bragro.mobile.ui.home

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import com.bragro.mobile.ui.theme.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bragro.mobile.data.local.DomainConfigEntity
import com.bragro.mobile.data.repo.ConfigRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Grade completa de módulos (todos os domínios cadastrados) -- é a tela
 * "Módulos" acessada pela barra inferior (ver ui/nav/BottomNavBar.kt),
 * antiga tela HOME de antes da réplica do Início virar a tela principal do
 * app (ver HomeScreen.kt). Ícones/seções em DomainVisuals.kt, mesmo
 * agrupamento visual do site (modules.ts/sidebar-nav.tsx). */
class ModulosViewModel(app: Application) : AndroidViewModel(app) {
    private val configRepository = ConfigRepository(app)

    var domains = mutableStateOf<List<DomainConfigEntity>>(emptyList())
        private set

    init {
        viewModelScope.launch { configRepository.observeDomains().collectLatest { domains.value = it } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModulosScreen(
    onOpenDomain: (String) -> Unit,
    viewModel: ModulosViewModel = viewModel(),
) {
    val domains by viewModel.domains

    val grouped = domains
        .groupBy { domainSectionMeta(it.domainId) }
        .toList()
        .sortedBy { (section, _) -> section.order }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Módulos", color = MaterialTheme.colorScheme.primary)
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
            grouped.forEach { (section, domainsInSection) ->
                item(key = "header-${section.id}") {
                    ModuloSectionHeader(section)
                }
                items(domainsInSection.chunked(2), key = { row -> row.joinToString("-") { it.domainId } }) { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        row.forEach { domain ->
                            ModuloCard(
                                domain = domain,
                                section = section,
                                modifier = Modifier.weight(1f),
                                onClick = { onOpenDomain(domain.domainId) },
                            )
                        }
                        if (row.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModuloSectionHeader(section: DomainSectionMeta) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(section.color),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            section.label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ModuloCard(
    domain: DomainConfigEntity,
    section: DomainSectionMeta,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    // Fundo verde forte (background, mesmo tom da barra inferior/dropdown)
    // -- pedido do usuário ("coloque nas listas suspensas e módulos o mesmo
    // verde do app"), escopado só pra esta tela "Módulos" (grade de
    // domínios); os blocos individuais DENTRO de cada módulo continuam
    // neutros, conforme decisão anterior documentada em ModuleIconRow.kt.
    // Ícone/rótulo continuam preto/branco (onSurface), só o fundo muda.
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
            draggedElevation = 0.dp,
            disabledElevation = 0.dp,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Preto/branco (onSurface) em vez de verde -- pedido do usuário
            // ("ainda há blocos individuais com o icone e o rotulo na cor
            // verde troque por preto/branco"). Esta grade de "Módulos" é um
            // bloco individual (ícone + rótulo) igual aos das telas de
            // domínio, mas usa seu próprio Card/Box (ModuloCard) em vez de
            // ModuleIconButton -- por isso não foi pega pela troca de
            // MODULE_ICON_FG em ModuleIconRow.kt.
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    domainIcon(domain.domainId),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                domain.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
