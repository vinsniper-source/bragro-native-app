package com.bragro.mobile.ui.nfe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bragro.mobile.data.model.InvoiceData
import com.bragro.mobile.ui.i18n.Text
import com.bragro.mobile.ui.theme.SearchableDropdownField
import com.bragro.mobile.ui.theme.appFieldColors
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.text.NumberFormat
import java.util.Locale

/** v139 (mockup aprovado, item 8) -- emissão nativa de NF-e pelo app:
 * "+ Emitir" abre [EmitirNfeAppDialog]; tocar numa nota nativa abre
 * [AcoesNotaNativaDialog] (DANFE · XML · Carta de correção · Cancelar).
 * O servidor (/api/mobile/nfe, actions form/emitir_saida/pdf/xml/cce/
 * cancelar/cep) usa o mesmo motor do site -- nenhuma regra fiscal aqui. */

internal fun JsonElement?.txt(): String = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content.orEmpty()
internal fun JsonObject?.obj(k: String): JsonObject? = this?.get(k) as? JsonObject
internal fun JsonObject?.arr(k: String): List<JsonObject> = (this?.get(k) as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()

private val dig = Regex("\\D")
private fun num(s: String): Double = s.trim().replace(".", "").replace(",", ".").toDoubleOrNull() ?: 0.0
private fun moeda(v: Double) = NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(v)

private val TIPOS = listOf("venda" to "Venda", "devolucao" to "Devolução", "remessa" to "Remessa")
private val NATUREZA = mapOf("venda" to "VENDA DE PRODUÇÃO DO ESTABELECIMENTO", "devolucao" to "DEVOLUÇÃO DE MERCADORIA", "remessa" to "REMESSA PARA DEPÓSITO")
private val IND_IE = listOf("1" to "Contribuinte (com inscrição estadual)", "2" to "Contribuinte isento", "9" to "Não contribuinte")
private val FRETE = listOf("9" to "Sem frete", "0" to "Por conta do emitente", "1" to "Por conta do destinatário", "2" to "Por conta de terceiros")
private val PAGAMENTO = listOf("15" to "Boleto", "17" to "PIX", "01" to "Dinheiro", "03" to "Cartão de crédito", "90" to "Sem pagamento", "99" to "Outros")

private fun cfopPadrao(tipo: String, mesmaUf: Boolean): String = when (tipo) {
    "devolucao" -> if (mesmaUf) "5202" else "6202"
    "remessa" -> if (mesmaUf) "5905" else "6905"
    else -> if (mesmaUf) "5101" else "6101"
}

private class ItemForm(cfop: String, cst: String) {
    var estoqueItem by mutableStateOf("")
    var descricao by mutableStateOf("")
    var unidade by mutableStateOf("SC")
    var quantidade by mutableStateOf("")
    var valor by mutableStateOf("")
    var ncm by mutableStateOf("")
    var cfop by mutableStateOf(cfop)
    var icmsCst by mutableStateOf(cst)
    var icmsAliquota by mutableStateOf("")
    var pisCofinsCst by mutableStateOf("08")
    val total get() = num(quantidade) * num(valor)
}

@Composable
private fun Campo(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    teclado: KeyboardType = KeyboardType.Text,
    trailing: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = teclado), trailingIcon = trailing,
        colors = appFieldColors(), modifier = modifier,
    )
}

@Composable
private fun Lista(label: String, value: String, options: List<Pair<String, String>>, modifier: Modifier = Modifier.fillMaxWidth(), onSelect: (String) -> Unit) {
    SearchableDropdownField(value = value, label = label, options = options, onSelect = onSelect, emptyOptionLabel = null, modifier = modifier)
}

@Composable
internal fun EmitirNfeAppDialog(
    form: JsonObject,
    pending: Boolean,
    onDismiss: () -> Unit,
    onBuscarCep: (String, (JsonObject?) -> Unit) -> Unit,
    onSalvar: (nota: JsonObject, emitir: Boolean) -> Unit,
) {
    val ufEmitente = form["uf"].txt().uppercase()
    val cstPadrao = if (form["crt"].txt() == "3") "41" else "102"
    val parceiros = remember(form) { form.arr("parceiros") }
    val produtos = remember(form) { form.arr("itens") }

    var tipo by remember { mutableStateOf("venda") }
    var natureza by remember { mutableStateOf(NATUREZA.getValue("venda")) }
    var chaveRef by remember { mutableStateOf("") }
    var parceiroId by remember { mutableStateOf("") }
    var documento by remember { mutableStateOf("") }
    var nome by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var indIE by remember { mutableStateOf("1") }
    var ie by remember { mutableStateOf("") }
    var cep by remember { mutableStateOf("") }
    var logradouro by remember { mutableStateOf("") }
    var numero by remember { mutableStateOf("") }
    var bairro by remember { mutableStateOf("") }
    var municipio by remember { mutableStateOf("") }
    var codigoMunicipio by remember { mutableStateOf("") }
    var uf by remember { mutableStateOf("") }
    var buscandoCep by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf<String?>(null) }
    var frete by remember { mutableStateOf("9") }
    var pagamento by remember { mutableStateOf("15") }
    var infCpl by remember { mutableStateOf("") }
    val itens = remember { mutableStateListOf(ItemForm(cfopPadrao("venda", true), cstPadrao)) }

    fun mesmaUf() = uf.isBlank() || uf.equals(ufEmitente, ignoreCase = true)
    fun recalcularCfop() { val c = cfopPadrao(tipo, mesmaUf()); itens.forEach { it.cfop = c } }

    fun buscarCep() {
        if (cep.replace(dig, "").length != 8) { aviso = "CEP precisa de 8 dígitos."; return }
        buscandoCep = true
        onBuscarCep(cep) { e ->
            buscandoCep = false
            if (e == null) { aviso = "CEP não encontrado."; return@onBuscarCep }
            aviso = null
            logradouro = e["logradouro"].txt().uppercase(); bairro = e["bairro"].txt().uppercase()
            municipio = e["municipio"].txt().uppercase(); uf = e["uf"].txt().uppercase(); codigoMunicipio = e["codigoMunicipio"].txt()
            recalcularCfop()
        }
    }

    fun escolherParceiro(id: String) {
        parceiroId = id
        val p = parceiros.firstOrNull { it["id"].txt() == id } ?: return
        val f = p.obj("dadosFiscais")
        documento = p["documento"].txt(); nome = p["nome"].txt(); email = p["email"].txt()
        indIE = f?.get("indIE").txt().ifBlank { "1" }; ie = f?.get("ie").txt()
        cep = f?.get("cep").txt(); logradouro = f?.get("logradouro").txt(); numero = f?.get("numero").txt()
        bairro = f?.get("bairro").txt(); municipio = f?.get("municipio").txt(); uf = f?.get("uf").txt(); codigoMunicipio = f?.get("codigoMunicipio").txt()
        recalcularCfop()
        if (codigoMunicipio.isBlank() && cep.replace(dig, "").length == 8) buscarCep()
    }

    fun escolherProduto(item: ItemForm, value: String) {
        item.estoqueItem = value
        val p = produtos.firstOrNull { it["value"].txt() == value } ?: return
        val f = p.obj("dadosFiscais")
        item.descricao = p["label"].txt().uppercase()
        f?.get("ncm").txt().takeIf { it.isNotBlank() }?.let { item.ncm = it }
        f?.get("icmsCst").txt().takeIf { it.isNotBlank() }?.let { item.icmsCst = it }
        f?.get("icmsAliquota").txt().takeIf { it.isNotBlank() }?.let { item.icmsAliquota = it.replace(".", ",") }
        f?.get("pisCofinsCst").txt().takeIf { it.isNotBlank() }?.let { item.pisCofinsCst = it }
    }

    fun montar(): JsonObject = buildJsonObject {
        put("modelo", "55")
        put("tipoNota", tipo)
        put("naturezaOperacao", natureza.trim().uppercase())
        put("modalidadeFrete", frete)
        put("meioPagamento", pagamento)
        put("informacoesComplementares", infCpl.trim().ifBlank { null })
        put("referencias", buildJsonArray { chaveRef.replace(dig, "").takeIf { tipo == "devolucao" && it.isNotBlank() }?.let { add(JsonPrimitive(it)) } })
        put("destinatario", buildJsonObject {
            put("documento", documento.replace(dig, "")); put("nome", nome.trim()); put("email", email.trim().ifBlank { null })
            put("ie", ie.trim().ifBlank { null }); put("indIE", indIE)
            put("cep", cep.replace(dig, "")); put("logradouro", logradouro); put("numero", numero); put("complemento", null as String?)
            put("bairro", bairro); put("codigoMunicipio", codigoMunicipio); put("municipio", municipio); put("uf", uf)
        })
        put("itens", buildJsonArray {
            itens.forEach { i ->
                add(buildJsonObject {
                    put("descricao", i.descricao.trim().uppercase()); put("estoqueItem", i.estoqueItem.ifBlank { null })
                    put("unidade", i.unidade.trim().uppercase()); put("quantidade", num(i.quantidade)); put("valorUnitario", num(i.valor))
                    put("ncm", i.ncm.replace(dig, "")); put("cfop", i.cfop.replace(dig, "")); put("icmsCst", i.icmsCst)
                    put("icmsAliquota", i.icmsAliquota.takeIf { it.isNotBlank() }?.let { num(it) }); put("pisCofinsCst", i.pisCofinsCst)
                })
            }
        })
    }

    AlertDialog(
        onDismissRequest = { if (!pending) onDismiss() },
        modifier = Modifier.fillMaxWidth(0.96f),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        title = { Text("Emitir NF-e") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Lista("Tipo da nota", tipo, TIPOS, Modifier.weight(1f)) { tipo = it; natureza = NATUREZA.getValue(it); recalcularCfop() }
                    Lista("Pagamento", pagamento, PAGAMENTO, Modifier.weight(1f)) { pagamento = it }
                }
                Campo("Natureza da operação", natureza, { natureza = it })
                if (tipo == "devolucao") Campo("Chave da nota devolvida (44 dígitos)", chaveRef, { chaveRef = it }, teclado = KeyboardType.Number)

                Text("Destinatário", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                if (parceiros.isNotEmpty()) {
                    Lista("Parceiro cadastrado", parceiroId, parceiros.map { it["id"].txt() to it["nome"].txt() }) { escolherParceiro(it) }
                }
                Campo("CNPJ ou CPF", documento, { documento = it }, teclado = KeyboardType.Number)
                Campo("Nome ou razão social", nome, { nome = it })
                Lista("Inscrição estadual", indIE, IND_IE) { indIE = it }
                if (indIE == "1") Campo("Número da inscrição estadual", ie, { ie = it }, teclado = KeyboardType.Number)
                Campo("CEP", cep, { cep = it }, teclado = KeyboardType.Number, trailing = {
                    IconButton(onClick = { buscarCep() }, enabled = !buscandoCep) {
                        if (buscandoCep) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Outlined.Search, contentDescription = "Buscar CEP")
                    }
                })
                Campo("Logradouro", logradouro, { logradouro = it })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Campo("Número", numero, { numero = it }, Modifier.weight(1f))
                    Campo("Bairro", bairro, { bairro = it }, Modifier.weight(2f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Campo("Município", municipio, { municipio = it }, Modifier.weight(3f))
                    Campo("UF", uf, { uf = it.uppercase().take(2); recalcularCfop() }, Modifier.weight(1f))
                }
                if (codigoMunicipio.isBlank() && municipio.isNotBlank()) {
                    Text("Use a lupa do CEP para preencher o código do município.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                }

                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Itens", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = { itens.add(ItemForm(cfopPadrao(tipo, mesmaUf()), cstPadrao)) }) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp)); Text("Item")
                    }
                }
                itens.forEachIndexed { n, item ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Item ${n + 1}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                            if (itens.size > 1) IconButton(onClick = { itens.removeAt(n) }) { Icon(Icons.Outlined.Close, contentDescription = "Remover item") }
                        }
                        if (produtos.isNotEmpty()) {
                            Lista("Produto do estoque", item.estoqueItem, produtos.map { it["value"].txt() to it["label"].txt() }) { escolherProduto(item, it) }
                        }
                        Campo("Descrição", item.descricao, { item.descricao = it })
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Campo("NCM", item.ncm, { item.ncm = it }, Modifier.weight(1.4f), KeyboardType.Number)
                            Campo("CFOP", item.cfop, { item.cfop = it }, Modifier.weight(1f), KeyboardType.Number)
                            Campo("Unidade", item.unidade, { item.unidade = it }, Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Campo("Quantidade", item.quantidade, { item.quantidade = it }, Modifier.weight(1f), KeyboardType.Decimal)
                            Campo("Valor unitário", item.valor, { item.valor = it }, Modifier.weight(1f), KeyboardType.Decimal)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Campo("CST do ICMS", item.icmsCst, { item.icmsCst = it }, Modifier.weight(1f), KeyboardType.Number)
                            Campo("Alíquota ICMS %", item.icmsAliquota, { item.icmsAliquota = it }, Modifier.weight(1f), KeyboardType.Decimal)
                            Campo("CST PIS/COFINS", item.pisCofinsCst, { item.pisCofinsCst = it }, Modifier.weight(1f), KeyboardType.Number)
                        }
                        Text("Total do item: ${moeda(item.total)}", style = MaterialTheme.typography.labelMedium)
                    }
                }
                Text("ICMS ST, IPI, transportadora e exportação: use a plataforma.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                HorizontalDivider()
                Lista("Frete", frete, FRETE) { frete = it }
                Campo("Informações complementares", infCpl, { infCpl = it })
                Text("Total da nota: ${moeda(itens.sumOf { it.total })}", fontWeight = FontWeight.Bold)
                aviso?.let { Text(it, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (pending) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                OutlinedButton(onClick = { onSalvar(montar(), false) }, enabled = !pending) { Text("Salvar rascunho") }
                Button(onClick = { onSalvar(montar(), true) }, enabled = !pending) { Text("Emitir") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !pending) { Text("Fechar") } },
    )
}

/** Tocar numa nota nativa autorizada: DANFE · XML · Carta de correção · Cancelar. */
@Composable
internal fun AcoesNotaNativaDialog(
    inv: InvoiceData,
    pending: Boolean,
    onDismiss: () -> Unit,
    onDanfe: () -> Unit,
    onXml: () -> Unit,
    onTexto: (acao: String, texto: String) -> Unit,
) {
    var acao by remember { mutableStateOf<String?>(null) } // "cce" | "cancelar"
    var texto by remember { mutableStateOf("") }
    val nfce = inv.modelo == "65"
    val cancelada = inv.statusSefaz == "CANCELADA"
    AlertDialog(
        onDismissRequest = { if (!pending) onDismiss() },
        title = { Text("${if (nfce) "NFC-e" else "NF-e"} Nº ${inv.numero}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                inv.chaveAcesso?.let { Text(it.chunked(4).joinToString(" "), style = MaterialTheme.typography.labelSmall) }
                if (acao == null) {
                    OutlinedButton(onClick = onDanfe, enabled = !pending, modifier = Modifier.fillMaxWidth()) { Text(if (nfce) "DANFE NFC-e (PDF)" else "DANFE (PDF)") }
                    OutlinedButton(onClick = onXml, enabled = !pending, modifier = Modifier.fillMaxWidth()) { Text("XML") }
                    if (!cancelada && !nfce) OutlinedButton(onClick = { acao = "cce"; texto = "" }, enabled = !pending, modifier = Modifier.fillMaxWidth()) { Text("Carta de correção") }
                    if (!cancelada) OutlinedButton(onClick = { acao = "cancelar"; texto = "" }, enabled = !pending, modifier = Modifier.fillMaxWidth()) {
                        Text("Cancelar nota", color = MaterialTheme.colorScheme.tertiary)
                    }
                    if (pending) CircularProgressIndicator(Modifier.size(20.dp).align(Alignment.CenterHorizontally), strokeWidth = 2.dp)
                } else {
                    Text(
                        if (acao == "cce") "Descreva a correção (não pode mudar valores, impostos, destinatário ou data)."
                        else "Motivo do cancelamento. Prazo: até 24 horas após a autorização.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = texto, onValueChange = { texto = it }, minLines = 3, colors = appFieldColors(),
                        label = { Text(if (acao == "cce") "Correção" else "Motivo") }, modifier = Modifier.fillMaxWidth(),
                    )
                    Text("${texto.trim().length} de 15 caracteres mínimos", style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            if (acao != null) {
                Button(onClick = { onTexto(acao!!, texto.trim()) }, enabled = !pending && texto.trim().length >= 15) {
                    if (pending) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text(if (acao == "cce") "Enviar correção" else "Cancelar nota")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { if (acao != null) acao = null else onDismiss() }, enabled = !pending) { Text(if (acao != null) "Voltar" else "Fechar") }
        },
    )
}
