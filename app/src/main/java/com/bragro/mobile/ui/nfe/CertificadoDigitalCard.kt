package com.bragro.mobile.ui.nfe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bragro.mobile.data.model.CertificadoDigitalData
import com.bragro.mobile.data.repo.CertificadoDigitalRepository
import com.bragro.mobile.ui.domain.brDateToIso
import com.bragro.mobile.ui.domain.isoDateToBr
import com.bragro.mobile.ui.i18n.Text
import com.bragro.mobile.ui.theme.SearchableDropdownField
import com.bragro.mobile.ui.theme.appFieldColors
import kotlinx.coroutines.launch

/**
 * Certificado digital (A1/A3) da organização -- paridade com o card de
 * NF-e/NFS-e do site (Task #405). É o MESMO certificado do Livro Caixa (1 por
 * organização); só metadado (tipo/emissor/validade), o .pfx nunca sobe.
 * Recolhido por padrão pra não ocupar a tela de NF-e.
 */
@Composable
fun CertificadoDigitalCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repo = remember { CertificadoDigitalRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var aberto by remember { mutableStateOf(false) }
    var carregado by remember { mutableStateOf(false) }
    var tipo by remember { mutableStateOf("") }
    var emissor by remember { mutableStateOf("") }
    var validade by remember { mutableStateOf("") }
    var salvando by remember { mutableStateOf(false) }
    var mensagem by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(aberto) {
        if (aberto && !carregado) {
            val cfg = repo.fetch()
            if (cfg != null) {
                tipo = cfg.certificadoDigitalTipo.orEmpty()
                emissor = cfg.certificadoDigitalEmissor.orEmpty()
                validade = isoDateToBr(cfg.certificadoDigitalValidade.orEmpty())
                carregado = true
            } else {
                mensagem = "Não foi possível carregar o certificado -- verifique a conexão."
            }
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Certificado digital (NF-e / NFS-e)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = { aberto = !aberto }) { Text(if (aberto) "Recolher" else "Editar") }
            }
            if (aberto) {
                SearchableDropdownField(
                    value = tipo,
                    label = "Tipo",
                    options = listOf("" to "Não informado", "A1" to "A1 (arquivo)", "A3" to "A3 (token/cartão)"),
                    onSelect = { tipo = it },
                    emptyOptionLabel = null,
                )
                OutlinedTextField(
                    value = emissor,
                    onValueChange = { emissor = it },
                    label = { Text("Emissor (AC)") },
                    placeholder = { Text("Ex.: Serasa, Certisign") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = appFieldColors(),
                )
                OutlinedTextField(
                    value = validade,
                    onValueChange = { validade = it },
                    label = { Text("Validade (DD/MM/AAAA)") },
                    placeholder = { Text("DD/MM/AAAA") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = appFieldColors(),
                )
                Text(
                    "Só metadado p/ controle de vencimento -- o arquivo .pfx/.p12 nunca é enviado aqui.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    enabled = !salvando,
                    onClick = {
                        salvando = true
                        mensagem = null
                        scope.launch {
                            val saved = repo.save(
                                CertificadoDigitalData(
                                    certificadoDigitalTipo = tipo,
                                    certificadoDigitalEmissor = emissor.trim(),
                                    certificadoDigitalValidade = brDateToIso(validade).trim(),
                                ),
                            )
                            salvando = false
                            if (saved != null) {
                                tipo = saved.certificadoDigitalTipo.orEmpty()
                                emissor = saved.certificadoDigitalEmissor.orEmpty()
                                validade = isoDateToBr(saved.certificadoDigitalValidade.orEmpty())
                                mensagem = "Certificado salvo."
                            } else {
                                mensagem = "Não foi possível salvar -- confira a data (DD/MM/AAAA) e a conexão."
                            }
                        }
                    },
                ) { Text(if (salvando) "Salvando..." else "Salvar") }
                if (mensagem != null) {
                    Text(mensagem ?: "", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
