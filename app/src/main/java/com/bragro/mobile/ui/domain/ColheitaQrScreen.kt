package com.bragro.mobile.ui.domain

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bragro.mobile.data.model.LoteQrData
import com.bragro.mobile.data.repo.ColheitaQrRepository
import com.bragro.mobile.ui.print.HtmlPrinter
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.launch

/** Tela "QR Code de Rastreabilidade" (Task #773, paridade com
 * RastreabilidadeQrButton/listLotesParaQr do site): gera um QR Code por lote
 * de Colheita já lançado (mesmo payload "BRAGRO:LOTE:..." de ColheitaQr.kt)
 * usando ZXing (mesmo mecanismo já usado em FrotaQrScreen.kt), buscando a
 * lista de lotes do servidor (ao contrário da Frota, que lê de lookups já
 * sincronizados localmente -- aqui são registros de verdade, não lookups),
 * e imprime tudo de uma vez via HtmlPrinter.printQrCodes. Pensado pra ser
 * colado na embalagem/sacaria do produto colhido. */
class ColheitaQrViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = ColheitaQrRepository(app)

    var lotes = mutableStateOf<List<LoteQrData>>(emptyList())
        private set
    var carregando = mutableStateOf(true)
        private set
    var erro = mutableStateOf<String?>(null)
        private set

    init { carregar() }

    fun carregar() {
        carregando.value = true
        erro.value = null
        viewModelScope.launch {
            val resultado = repository.fetch()
            carregando.value = false
            if (resultado == null) {
                erro.value = "Sem conexão -- não foi possível carregar os lotes agora."
                return@launch
            }
            lotes.value = resultado.lotes
        }
    }
}

/** Gera o bitmap do QR (matriz preto/branco), tamanho fixo -- mesmo critério
 * de FrotaQrScreen.kt (gerarQrBitmap), reaproveitado aqui com outro payload. */
private fun gerarQrBitmapLote(payload: String, tamanho: Int = 512): Bitmap {
    val writer = QRCodeWriter()
    val matrix = writer.encode(payload, BarcodeFormat.QR_CODE, tamanho, tamanho)
    val bitmap = Bitmap.createBitmap(tamanho, tamanho, Bitmap.Config.RGB_565)
    for (x in 0 until tamanho) {
        for (y in 0 until tamanho) {
            bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
    return bitmap
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColheitaQrScreen(onBack: () -> Unit, viewModel: ColheitaQrViewModel = viewModel()) {
    val context = LocalContext.current
    val lotes by viewModel.lotes
    val carregando by viewModel.carregando
    val erro by viewModel.erro

    val bitmaps = remember(lotes) {
        lotes.associate { it.id to gerarQrBitmapLote(buildLoteQrPayload(it)) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("QR Code de Rastreabilidade", color = MaterialTheme.colorScheme.primary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val items = lotes.mapNotNull { l -> bitmaps[l.id]?.let { l.label to it } }
                            if (items.isNotEmpty()) HtmlPrinter.printQrCodes(context, "QR Code de Rastreabilidade", items)
                        },
                        enabled = lotes.isNotEmpty(),
                    ) {
                        Icon(Icons.Filled.Print, contentDescription = "Imprimir todos", tint = MaterialTheme.colorScheme.primary)
                    }
                },
            )
        },
    ) { padding ->
        if (carregando) {
            Column(modifier = Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        if (erro != null) {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(erro ?: "", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }
            return@Scaffold
        }
        if (lotes.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Nenhum lote colhido lançado ainda.", style = MaterialTheme.typography.bodyMedium)
            }
            return@Scaffold
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(lotes, key = { it.id }) { l ->
                val bmp = bitmaps[l.id]
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "QR Code de ${l.label}",
                            modifier = Modifier.size(140.dp),
                        )
                    }
                    Text(l.label, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
