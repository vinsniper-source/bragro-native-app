package com.bragro.mobile.ui.i18n

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.bragro.mobile.ui.theme.LinhaTextField as OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * v1.6.13 (mockup v114) -- menu País · Idioma · Exibir valores em · Conversor,
 * igual ao da plataforma (components/layout/currency-menu.tsx). Usado no
 * login e em Configurações.
 */
@Composable
fun MenuPaisIdiomaMoeda(modifier: Modifier = Modifier) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var aberto by remember { mutableStateOf(false) }
    val idioma = Idioma
    val moeda = MoedaApp
    Box(modifier) {
        // v1.6.17 (mockup v120) -- ícone e sigla em preto (claro) / branco (escuro).
        TextButton(onClick = { aberto = true }, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onBackground)) {
            Icon(com.bragro.mobile.ui.domain.IconeMoedas, contentDescription = "País, idioma e moeda", modifier = Modifier.size(18.dp))
            androidx.compose.material3.Text(moeda.sigla, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
        }
        com.bragro.mobile.ui.theme.MenuSuspenso(expanded = aberto, onDismissRequest = { aberto = false }) {
            @Composable
            fun titulo(t: String) = androidx.compose.material3.Text(t, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            titulo("País")
            listOf("BR" to "Brasil (pt-BR · R$)", "AR" to "Argentina (es · ARS)").forEach { (cod, nome) ->
                DropdownMenuItem(text = { androidx.compose.material3.Text(nome + if (idioma.pais == cod) " ✓" else "") }, onClick = { if (idioma.pais != cod) idioma.definirPais(ctx, cod); aberto = false })
            }
            HorizontalDivider()
            titulo("Idioma")
            listOf("pt" to "Português (Brasil)", "es" to "Español").forEach { (cod, nome) ->
                DropdownMenuItem(text = { androidx.compose.material3.Text(nome + if (idioma.codigo == cod) " ✓" else "") }, onClick = { if (idioma.codigo != cod) idioma.definir(ctx, cod); aberto = false })
            }
            HorizontalDivider()
            titulo(if (idioma.espanhol) "Mostrar valores en" else "Exibir valores em")
            listOf("BRL" to "Real (R$)", "USD" to "Dólar (U\$D)", "ARS" to "Peso argentino (ARS)").forEach { (cod, nome) ->
                DropdownMenuItem(text = { androidx.compose.material3.Text(nome + if (moeda.codigo == cod) " ✓" else "") }, onClick = { moeda.definir(ctx, cod); aberto = false })
            }
            HorizontalDivider()
            titulo("⇄ Conversor")
            var valor by remember { mutableStateOf("1000") }
            var de by remember { mutableStateOf("BRL") }
            Column(Modifier.padding(horizontal = 16.dp).width(240.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = valor, onValueChange = { valor = it }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.bodyMedium,
                    )
                    listOf("BRL", "USD", "ARS").forEach { c ->
                        androidx.compose.material3.Text(
                            if (c == "USD") "U\$D" else c,
                            fontWeight = if (de == c) FontWeight.ExtraBold else FontWeight.Normal,
                            color = if (de == c) com.bragro.mobile.ui.theme.VerdeBarra else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            modifier = Modifier.clickable { de = c }.padding(4.dp),
                        )
                    }
                }
                val n = valor.replace(".", "").replace(",", ".").toDoubleOrNull()
                listOf("BRL", "USD", "ARS").filter { it != de }.forEach { c ->
                    Row(Modifier.fillMaxWidth()) {
                        androidx.compose.material3.Text(if (c == "USD") "U\$D" else c, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        val r = n?.let { moeda.converter(it, de, c) }
                        androidx.compose.material3.Text(r?.let { moeda.comCodigo(it, c) } ?: "—", fontWeight = FontWeight.Bold)
                    }
                }
                if (moeda.usdBrl == null) androidx.compose.material3.Text("Cotação disponível depois de abrir o Início.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

