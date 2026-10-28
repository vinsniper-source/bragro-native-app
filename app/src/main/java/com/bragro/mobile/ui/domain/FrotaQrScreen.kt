package com.bragro.mobile.ui.domain

import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.foundation.layout.Box
import android.app.Application
import android.graphics.Bitmap
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import com.bragro.mobile.ui.i18n.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.Image
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bragro.mobile.data.local.LookupEntity
import com.bragro.mobile.data.repo.ConfigRepository
import com.bragro.mobile.data.repo.FrotaRegistradasRepository
import com.bragro.mobile.ui.print.HtmlPrinter
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.launch

/** Tela "QR Codes das máquinas" (Task #602, paridade com
 * frota-qr-codes-button.tsx do site): gera um QR Code por máquina/frota
 * cadastrada (mesmo payload "BRAGRO:FROTA:<valor>" de FrotaQr.kt) usando
 * ZXing (encoder puro, sem UI de câmera -- ML Kit não tem gerador), e
 * imprime tudo de uma vez via HtmlPrinter.printQrCodes (mesmo mecanismo de
 * impressão HTML/WebView já usado no app). Esses QR Codes, colados na
 * máquina, são lidos depois no Abastecimento rápido (QuickAbastecimentoDialog)
 * pra preencher o campo Máquina/Frota sem digitar. */
class FrotaQrViewModel(app: Application) : AndroidViewModel(app) {
    private val configRepository = ConfigRepository(app)
    private val frotaRegistradasRepository = FrotaRegistradasRepository(app)

    var frotas = mutableStateOf<List<LookupEntity>>(emptyList())
        private set
    var carregando = mutableStateOf(true)
        private set

    init {
        viewModelScope.launch {
            val catalogo = configRepository.lookupsByCategory("frotas")
            // Só as máquinas que já têm pelo menos um registro real
            // (FrotaRegistro), não o catálogo genérico inteiro de Base de
            // Dados > Frotas (pedido do usuário: "esses QRCode foram criados
            // a partir da base de dados ou são ficticios... use como padrao
            // os que tiverem cadastrado na base de dados"). Sem rede/sessão
            // ou fazenda ainda sem nenhum registro, cai de volta pro
            // catálogo completo -- nunca fica vazio por causa disso.
            val usadas = frotaRegistradasRepository.fetch()
            frotas.value = if (usadas.isNullOrEmpty()) {
                catalogo
            } else {
                val usadasSet = usadas.map { it.trim().uppercase() }.toSet()
                catalogo.filter { it.value.trim().uppercase() in usadasSet }
            }
            carregando.value = false
        }
    }
}

/** Gera o bitmap do QR (matriz preto/branco), tamanho fixo -- suficiente
 * tanto pra exibir na tela quanto pra imprimir (a impressão reamostra pro
 * tamanho do papel via CSS). */
private fun gerarQrBitmap(payload: String, tamanho: Int = 512): Bitmap {
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
fun FrotaQrScreen(onBack: () -> Unit, viewModel: FrotaQrViewModel = viewModel()) {
    val context = LocalContext.current
    val frotas by viewModel.frotas
    val carregando by viewModel.carregando

    // Bitmaps gerados uma vez por lista de frotas (não recalcula a cada
    // recomposição) -- chave = tamanho+conteúdo da lista.
    val bitmaps = remember(frotas) {
        frotas.associate { it.value to gerarQrBitmap(buildFrotaQrPayload(it.value)) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { com.bragro.mobile.ui.domain.BackNavButton() },
                title = { androidx.compose.material3.ProvideTextStyle(androidx.compose.material3.LocalTextStyle.current.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)) {
                    // v21 -- ícone verde do módulo ao lado do título.
                    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
                        com.bragro.mobile.ui.domain.IconeNegrito(androidx.compose.material.icons.Icons.Outlined.QrCode2, contentDescription = null, tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface, size = 22.dp, modifier = androidx.compose.ui.Modifier.padding(end = 8.dp, bottom = 3.dp))
 Text("QR Codes das máquinas", color = MaterialTheme.colorScheme.onSurface) 
                    }
                } },
                actions = { com.bragro.mobile.ui.domain.HomeNavButton() },
            )
        },
    ) { padding0 ->
        // v19 -- ícones do cabeçalho movidos para a faixa Ações.
        Column(Modifier.fillMaxSize().padding(padding0)) {
            com.bragro.mobile.ui.domain.AcoesFaixa {
                com.bragro.mobile.ui.domain.LabeledIconButton(icon = com.bragro.mobile.ui.domain.IconesVazados.Print, label = "Imprimir todos", onClick = { val items = frotas.mapNotNull { f -> bitmaps[f.value]?.let { f.label to it } }; if (items.isNotEmpty()) HtmlPrinter.printQrCodes(context, "QR Codes das máquinas", items) })
                com.bragro.mobile.ui.domain.LabeledIconButton(icon = com.bragro.mobile.ui.domain.IconesVazados.PictureAsPdf, label = "PDF", onClick = { val items = frotas.mapNotNull { f -> bitmaps[f.value]?.let { f.label to it } }; if (items.isNotEmpty()) { android.widget.Toast.makeText(context, "Na próxima tela, escolha \"Salvar como PDF\".", android.widget.Toast.LENGTH_LONG).show(); HtmlPrinter.printQrCodes(context, "QR Codes das máquinas", items) } })
            }
            val padding = PaddingValues(0.dp)
            Box(Modifier.fillMaxWidth().weight(1f)) {
        if (carregando) {
            Column(modifier = Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        if (frotas.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Nenhuma máquina/frota cadastrada em Base de Dados ainda.", style = MaterialTheme.typography.bodyMedium)
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
            items(frotas) { f ->
                val bmp = bitmaps[f.value]
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "QR Code de ${f.label}",
                            modifier = Modifier.size(140.dp),
                        )
                    }
                    Text(f.label, style = MaterialTheme.typography.bodySmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
    }
        }
    }
}
