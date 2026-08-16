package com.bragro.mobile.ui.home

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bragro.mobile.ui.theme.Card
import java.util.Locale

// Réplica mobile da captura rápida por voz/texto (ver
// components/dashboard/quick-capture-bar.tsx no site) -- pedido do usuário
// ("implemente nessa sequência no app nativo"). MESMO escopo deliberadamente
// limitado do site: transcreve + extrai um valor em R$ por regex, e manda o
// usuário pro módulo Financeiro já com o texto pronto pra copiar -- NÃO
// pré-preenche o formulário sozinho (mesmo motivo documentado no site: o
// formulário genérico de módulo não expõe valores iniciais via navegação).
//
// A transcrição usa RecognizerIntent.ACTION_RECOGNIZE_SPEECH (diálogo do
// sistema/Google, não SpeechRecognizer contínuo) -- o app NÃO precisa da
// permissão RECORD_AUDIO pra isso (quem grava é o app do reconhecedor, não
// o BRAgro), evitando pedir mais uma permissão sensível só por essa
// funcionalidade.

private val VALOR_REGEX = Regex("""(?:r\$\s*)?(\d{1,3}(?:\.\d{3})*(?:,\d{2})?|\d+(?:[.,]\d{2})?)""", RegexOption.IGNORE_CASE)

private fun extrairValor(texto: String): Double? {
    val m = VALOR_REGEX.find(texto) ?: return null
    val raw = m.groupValues[1].replace(".", "").replace(",", ".")
    return raw.toDoubleOrNull()
}

@Composable
fun QuickCaptureBar(onOpenFinanceiro: () -> Unit) {
    var texto by remember { mutableStateOf("") }
    val context = LocalContext.current

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull()
            if (!spoken.isNullOrBlank()) texto = if (texto.isBlank()) spoken else "$texto $spoken"
        }
    }

    fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale("pt", "BR").toString())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Fale o lançamento, ex.: \"paguei 450 reais de diesel na fazenda tal\"")
        }
        runCatching { speechLauncher.launch(intent) }.onFailure {
            Toast.makeText(context, "Nenhum app de reconhecimento de voz disponível neste aparelho.", Toast.LENGTH_SHORT).show()
        }
    }

    val valor = if (texto.isNotBlank()) extrairValor(texto) else null

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = texto,
                    onValueChange = { texto = it },
                    placeholder = { Text("Fale ou digite: \"paguei 450 reais de diesel na fazenda tal\"", style = MaterialTheme.typography.bodySmall) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                IconButton(onClick = { startListening() }) {
                    Icon(Icons.Filled.Mic, contentDescription = "Falar", tint = MaterialTheme.colorScheme.primary)
                }
                if (texto.isNotBlank()) {
                    IconButton(onClick = { texto = "" }) {
                        Icon(Icons.Filled.Close, contentDescription = "Limpar")
                    }
                }
            }
            if (texto.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (valor != null) {
                            "Valor identificado: R$ ${String.format(Locale("pt", "BR"), "%,.2f", valor)} — confira e complete no formulário."
                        } else {
                            "Não encontramos um valor em R$ no texto — pode completar direto no formulário."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                    )
                    TextButton(onClick = onOpenFinanceiro) {
                        Text("Abrir em Financeiro")
                        Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
        }
    }
}
