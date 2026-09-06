package com.bragro.mobile.ui.domain

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bragro.mobile.data.model.ProviderIntegrationDto
import com.bragro.mobile.ui.theme.Card
import com.bragro.mobile.ui.theme.appFieldColors

/** "Ocupado com" -- espelha o `busy` (union "salvar"|"sync"|"desconectar"|
 * null) do ProviderIntegrationCard do site, só que tipado em vez de String
 * solta (evita erro de digitação, já que este enum só circula dentro do
 * Compose, nunca vai pra rede -- diferente do "action" do corpo HTTP). */
enum class IntegrationBusy { SALVANDO, SINCRONIZANDO, DESCONECTANDO }

/** Card "Acesso automático via prestadora de serviço" (Task #341/#54) --
 * réplica mobile do ProviderIntegrationCard do site (ver
 * components/domain/provider-integration-card.tsx), reaproveitando a mesma
 * UX: colapsável e fechado por padrão, dropdown de provedor + campo de
 * senha/token, botões Salvar/Desconectar, e "Testar sincronização" quando
 * já conectado (mesmo aviso de que a sincronização automática em si ainda
 * depende de aprovação de parceiro do fabricante -- ver mensagem que volta
 * em [onSync]). Compartilhado entre FieldviewScreen e DroneScreen: só muda
 * a lista de provedores e a descrição por módulo (ver chamadas em cada
 * tela). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ProviderIntegrationCard(
    providers: List<String>,
    descricao: String,
    integration: ProviderIntegrationDto?,
    busy: IntegrationBusy?,
    syncMessage: String?,
    onSave: (provedor: String, apiKey: String) -> Unit,
    onDisconnect: () -> Unit,
    onSync: () -> Unit,
    modifier: Modifier = Modifier,
    // true quando o card é o ÚNICO conteúdo de um diálogo dedicado (ex.:
    // FAB "Bomba"/"Balança" em Frota/Romaneios, ver DomainListScreen.kt) --
    // não faz sentido abrir fechado se o usuário já tocou num botão
    // especificamente pra ver isto. FieldView/Drone não passam este
    // parâmetro (continuam fechados por padrão, mesmo comportamento de
    // sempre).
    initiallyOpen: Boolean = false,
) {
    var open by remember { mutableStateOf(initiallyOpen) }
    var expanded by remember { mutableStateOf(false) }
    var provedor by remember(integration?.provedor) { mutableStateOf(integration?.provedor ?: "") }
    var apiKey by remember { mutableStateOf("") }
    val conectado = integration?.status == "CONECTADO"

    Card(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // Linha 1: só o título -- pedido do usuário ("a frase acesso
            // automático via prestadora de serviços" isolada). Agora tem a
            // largura TODA do card pra si (nada mais dividindo espaço),
            // então o basicMarquee() só entra em ação em telas realmente
            // estreitas -- mesmo bug de weight(1f) espremido já corrigido
            // antes (task #338), agora resolvido de raiz em vez de só
            // mitigado.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 8.dp))
                Text(
                    "Acesso automático via prestadora de serviço",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f).basicMarquee(),
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
            // Linha 2: status ("Não conectado"/"Conectado") ao lado da
            // setinha de recolher -- pedido do usuário. É esta linha
            // inteira que abre/fecha o card (clicável), não só a setinha.
            Row(
                modifier = Modifier.fillMaxWidth().clickable { open = !open },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (conectado) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.height(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(integration?.provedor ?: "", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                } else {
                    Text("Não conectado", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.weight(1f))
                IconButtonToggle(open) { open = !open }
            }
        }
        if (open) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Resumo das informações -- pedido do usuário ("abaixo
                // resuma as informações"): descrição de UMA linha só,
                // repassada por cada tela (FieldView/Drone/Frota/
                // Romaneios), já encurtada desde a varredura da task #398.
                Text(descricao, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)

                // Provedor, API Key, Salvar e Fechar agora EMPILHADOS (cada
                // um em sua própria linha, não mais lado a lado) -- pedido
                // do usuário ("abaixo coloque provedor... abaixo coloque
                // api key... abaixo coloque salvar... mais abaixo coloque
                // fechar"). Cada campo continua no seu FieldBlock individual
                // (task #438), só que agora ocupando a largura toda.
                FieldBlock(modifier = Modifier.fillMaxWidth()) {
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedTextField(
                            value = provedor, onValueChange = {}, readOnly = true,
                            label = { Text("Provedor") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            colors = appFieldColors(),
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            providers.forEach { p -> DropdownMenuItem(text = { Text(p) }, onClick = { provedor = p; expanded = false }) }
                        }
                    }
                }

                FieldBlock(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = apiKey, onValueChange = { apiKey = it },
                        label = { Text("API Key / Token") },
                        placeholder = { Text(if (integration?.apiKeyConfigurado == true) "•••• (salvo)" else "Cole a credencial") },
                        leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = appFieldColors(),
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = busy == null && provedor.isNotBlank() && apiKey.isNotBlank(),
                        onClick = { onSave(provedor, apiKey.trim()); apiKey = "" },
                    ) {
                        if (busy == IntegrationBusy.SALVANDO) CircularProgressIndicator(modifier = Modifier.height(18.dp)) else Text("Salvar")
                    }
                    if (conectado) {
                        OutlinedButton(enabled = busy == null, onClick = onDisconnect) {
                            if (busy == IntegrationBusy.DESCONECTANDO) CircularProgressIndicator(modifier = Modifier.height(18.dp)) else Text("Desconectar")
                        }
                    }
                }

                if (conectado) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(enabled = busy == null, onClick = onSync) {
                            if (busy == IntegrationBusy.SINCRONIZANDO) {
                                CircularProgressIndicator(modifier = Modifier.height(18.dp))
                            } else {
                                Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                            }
                            Text("Testar sincronização")
                        }
                    }
                    // Nota permanente (não só depois de clicar "Testar
                    // sincronização") -- pedido do usuário via auditoria
                    // ("deixar claro na UI que sync depende de parceria
                    // externa"): o site já mostra isso uma vez, num toast,
                    // logo após salvar a credencial (ver savedDescription em
                    // provider-integration-card.tsx) -- mas um toast some da
                    // tela; o app não tinha NENHUM aviso proativo, só a
                    // mensagem reativa (syncMessage) depois de tentar (que
                    // sempre falha, já que é um stub aguardando aprovação de
                    // parceiro -- ver comentário completo em
                    // provider-integration.ts). Com o texto fixo aqui, quem
                    // reabre a tela mais tarde (sem ter visto o toast) já
                    // entende que "conectado" não significa "sincronizando
                    // automaticamente" ainda.
                    // Aviso resumido -- pedido do usuário ("resuma os
                    // avisos"). O comportamento (sync automática ainda
                    // depende de aprovação de parceiro) continua o mesmo.
                    Text(
                        "Sincronização automática ainda depende de aprovação do parceiro/fabricante.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    integration?.ultimaSincronizacaoEm?.let {
                        Text("Última sincronização: ${formatIsoDateTime(it)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                syncMessage?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // Botão Fechar -- pedido do usuário ("mais abaixo coloque
                // fechar"): último elemento do card aberto, faz a mesma
                // coisa que tocar na setinha/linha de status (recolhe o
                // card), só que como ação explícita no fim do fluxo em vez
                // de só a setinha lá em cima.
                OutlinedButton(onClick = { open = false }, modifier = Modifier.fillMaxWidth()) {
                    Text("Fechar")
                }
            }
        }
    }
}

@Composable
private fun IconButtonToggle(open: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.IconButton(onClick = onClick) {
        Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = if (open) "Recolher" else "Expandir")
    }
}

/** Bloco individual pra separar campos dentro do card de integração --
 * pedido do usuário ("crie blocos individuais separando dentro do bloco
 * inteiro"). Usa um tom (surfaceVariant) diferente do container do Card
 * (colorScheme.surface, ver appFieldColors/Card em AppCard.kt) em vez de
 * borda, respeitando a regra do app de não ter bordas em lugar nenhum
 * (tasks anteriores "tire todas as bordas de todo app"). */
@Composable
private fun FieldBlock(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
    ) {
        Box(modifier = Modifier.padding(6.dp)) {
            content()
        }
    }
}

/** "AAAA-MM-DDTHH:mm:ss..." (ISO, mesmo formato que Prisma/Next.js mandam)
 * -> "DD/MM/AAAA HH:mm", mesmo padrão pt-BR do resto do app (ver
 * isoDateOnlyDrone em DroneScreen.kt). String crua se o formato vier
 * inesperado, em vez de derrubar a tela. */
private fun formatIsoDateTime(iso: String): String {
    val m = Regex("^(\\d{4})-(\\d{2})-(\\d{2})T(\\d{2}):(\\d{2})").find(iso) ?: return iso
    val (y, mo, d, h, min) = m.destructured
    return "$d/$mo/$y $h:$min"
}
