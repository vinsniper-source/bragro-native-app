package com.bragro.mobile.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bragro.mobile.ui.i18n.Text
import com.bragro.mobile.ui.theme.appFieldColors
import java.text.Normalizer

/** Busca de módulos (equivalente mobile do Ctrl+K do site): digita parte do
 * nome (sem acento/maiúscula) e toca no módulo pra abrir. Respeita as
 * permissões: "*" ou lista vazia (ainda não carregada) = todos. */
private val SEARCHABLE_MODULES: List<Pair<String, String>> = listOf(
    "financeiro" to "Financeiro",
    "safra" to "Atividades",
    "planejamentosafra" to "Planejamento de Safra",
    "colheita" to "Colheita",
    "romaneios" to "Romaneios",
    "receituarios" to "Receituários",
    "pragas" to "Pragas",
    "clima" to "Clima",
    "estoque" to "Estoque",
    "inventario" to "Inventário",
    "frota" to "Frota",
    "controleinterno" to "Controle Interno",
    "rh" to "RH",
    "pedidos" to "Pedidos",
    "cotacoesfornecedores" to "Cotações de Fornecedores",
    "contratos" to "Contratos",
    "caixainterno" to "Caixa Interno",
    "cobrancas" to "Cobranças",
    "nfse" to "NFS-e",
    "pecuaria" to "Pecuária",
)

private fun norm(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()

@Composable
fun ModuleSearchDialog(
    allowedModules: List<String>,
    onOpenDomain: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val q = norm(query.trim())
    val results = SEARCHABLE_MODULES.filter { (id, label) ->
        (allowedModules.isEmpty() || allowedModules.contains("*") || allowedModules.contains(id)) &&
            (q.isEmpty() || norm(label).contains(q) || id.contains(q))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Buscar módulo") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Digite o nome do módulo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = appFieldColors(),
                )
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).padding(top = 8.dp)) {
                    if (results.isEmpty()) {
                        item { Text("Nenhum módulo encontrado.", style = MaterialTheme.typography.bodySmall) }
                    }
                    items(results, key = { it.first }) { (id, label) ->
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onDismiss(); onOpenDomain(id) }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
    )
}
