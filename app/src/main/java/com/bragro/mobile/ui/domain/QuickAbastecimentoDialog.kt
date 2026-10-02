package com.bragro.mobile.ui.domain

import android.app.Application
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusWeak
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.bragro.mobile.ui.theme.SearchableDropdownField
import com.bragro.mobile.ui.theme.appFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bragro.mobile.data.AppLog
import com.bragro.mobile.data.local.LookupEntity
import com.bragro.mobile.data.repo.ConfigRepository
import com.bragro.mobile.data.repo.FrotaRegistradasRepository
import com.bragro.mobile.data.repo.RecordRepository
import com.bragro.mobile.data.repo.SaveResult
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ITENS_COMBUSTIVEL = listOf("DIESEL S10", "DIESEL S500", "ETANOL", "GASOLINA", "ARLA 32")

class QuickAbastecimentoViewModel(app: Application) : AndroidViewModel(app) {
    private val configRepository = ConfigRepository(app)
    private val recordRepository = RecordRepository(app)
    private val frotaRegistradasRepository = FrotaRegistradasRepository(app)

    var frotas = mutableStateOf<List<LookupEntity>>(emptyList())
        private set
    // Nomes de frota REALMENTE registrados (pelo menos um lançamento em
    // FrotaRegistro) -- usado SÓ pra validar o QR escaneado (pedido do
    // usuário: "aceitar apenas dados para o qr que venha da base de
    // dados"), consistente com o mesmo filtro aplicado na geração dos QR
    // Codes (FrotaQrScreen.kt). O dropdown "Máquina/Frota" continua com o
    // catálogo completo (frotas acima) -- só a checagem do QR fica mais
    // restrita. null = ainda não carregou/falhou -> cai pro catálogo
    // completo (frotas.value) como fallback, nunca bloqueia o uso offline.
    private var frotasRegistradas: List<String>? = null
    var locais = mutableStateOf<List<LookupEntity>>(emptyList())
        private set
    var colaboradores = mutableStateOf<List<LookupEntity>>(emptyList())
        private set
    var saving = mutableStateOf(false)
        private set
    var qrMensagem = mutableStateOf<String?>(null)
        private set
    var lendoQr = mutableStateOf(false)
        private set

    var frota by mutableStateOf("")
    var item by mutableStateOf(ITENS_COMBUSTIVEL[0])
    var local by mutableStateOf("")
    var qtd by mutableStateOf("")
    var unitario by mutableStateOf("")
    var horimetro by mutableStateOf("")
    var responsavel by mutableStateOf("")

    fun loadLookups() {
        viewModelScope.launch {
            frotas.value = configRepository.lookupsByCategory("frotas")
            locais.value = configRepository.lookupsByCategory("locais")
            colaboradores.value = configRepository.lookupsByCategory("colaboradores")
            frotasRegistradas = frotaRegistradasRepository.fetch()
        }
    }

    /** "Copiar último abastecimento" -- mesmo critério do site (só entre
     * lançamentos de Frota com operacao=ABASTECIMENTO), calculado sobre os
     * registros já sincronizados no aparelho (Room), sem endpoint novo. */
    fun copyFromLastAbastecimento(onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            // Pega só o retrato ATUAL da lista (o app já sincronizou Frota
            // ao abrir a tela) -- first() encerra a coleta assim que o Room
            // devolve a primeira lista, em vez de ficar observando pra sempre.
            val records = recordRepository.observeRecords("frota").first()
            val match = records.firstOrNull { it["operacao"]?.trim()?.equals("ABASTECIMENTO", ignoreCase = true) == true }
            if (match != null) {
                frota = match["frota"] ?: frota
                item = match["item"] ?: item
                local = match["local"] ?: local
                qtd = match["qtd"] ?: qtd
                unitario = match["unitario"] ?: unitario
                horimetro = match["horimetro"] ?: horimetro
                responsavel = match["colaborador"] ?: responsavel
            }
            onDone(match != null)
        }
    }

    /** QR Code no abastecimento (Task #602) -- lê o payload "BRAGRO:FROTA:<valor>"
     * de uma FOTO (mesmo padrão "foto, não câmera ao vivo" já usado no app
     * pra Romaneio/Pragas) via ML Kit Barcode Scanning, e casa contra a lista
     * REAL de frotas cadastradas (parseFrotaQrPayload) -- nunca aceita um
     * valor não reconhecido. */
    fun onQrPhotoTaken(context: Context, uri: Uri) {
        lendoQr.value = true
        qrMensagem.value = null
        viewModelScope.launch {
            try {
                val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                if (bitmap == null) {
                    qrMensagem.value = "Não foi possível ler a foto do QR Code."
                    return@launch
                }
                val image = InputImage.fromBitmap(bitmap, 0)
                val scanner = BarcodeScanning.getClient()
                val barcodes = scanner.process(image).await()
                val texto = barcodes.firstOrNull { it.valueType == Barcode.TYPE_TEXT || it.rawValue != null }?.rawValue
                if (texto == null) {
                    qrMensagem.value = "Nenhum QR Code encontrado na foto -- tente novamente com mais luz e foco."
                    return@launch
                }
                aplicarTextoQrLido(texto)
            } catch (e: Exception) {
                AppLog.e("QuickAbastecimentoDialog", "Falha ao ler QR Code do abastecimento", e)
                qrMensagem.value = "Falha ao ler o QR Code -- tente novamente ou escolha manualmente."
            } finally {
                lendoQr.value = false
            }
        }
    }

    /** "Colocar a condição de acessar o QR nativo do celular, pq existem
     * celulares mais antigos que o QR code é separado da câmera" -- quando o
     * leitor NATIVO do aparelho (câmera ao vivo, via Intent de scan) já
     * devolve o texto pronto, sem precisar passar pelo fluxo de foto+ML Kit.
     * Reaproveita a MESMA validação contra as frotas registradas usada em
     * [onQrPhotoTaken], só pulando a etapa de decodificar bitmap. */
    fun onQrTextoLidoExterno(texto: String?) {
        if (texto.isNullOrBlank()) {
            qrMensagem.value = "Nenhum QR Code lido -- tente novamente ou escolha manualmente."
            return
        }
        lendoQr.value = true
        qrMensagem.value = null
        viewModelScope.launch {
            try {
                aplicarTextoQrLido(texto)
            } finally {
                lendoQr.value = false
            }
        }
    }

    private fun aplicarTextoQrLido(texto: String) {
        // Pedido do usuário: "aceitar apenas dados para o qr que
        // venha da base de dados" -- restringe a validação do QR às
        // frotas REALMENTE registradas (pelo menos um lançamento),
        // não ao catálogo genérico inteiro. Sem registros ainda
        // (fazenda nova) ou sem conseguir buscar (offline), cai de
        // volta pro catálogo completo -- nunca trava o uso.
        val registradas = frotasRegistradas
        val valoresValidos = if (registradas.isNullOrEmpty()) frotas.value.map { it.value } else registradas
        val match = parseFrotaQrPayload(texto, valoresValidos)
        if (match != null) {
            frota = match
            qrMensagem.value = null
        } else {
            qrMensagem.value = "QR Code lido, mas não corresponde a nenhuma máquina/frota cadastrada."
        }
    }

    // Bug real encontrado (usuário: "sem acesso a câmera e QR code"): quando
    // a permissão CAMERA era negada (ou a câmera falhava ao abrir), essa
    // função só zerava o spinner sem dizer nada -- parecia que o app
    // simplesmente não tinha acesso, sem explicação nem alternativa.
    fun onQrPhotoCancelled() {
        lendoQr.value = false
        qrMensagem.value = "Nenhuma foto do QR Code capturada -- tente novamente ou escolha a máquina manualmente."
    }

    fun submit(onDone: (SaveResult) -> Unit) {
        if (frota.isBlank() || qtd.isBlank()) return
        saving.value = true
        val hoje = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val fields = buildMap {
            put("entrada", hoje)
            put("frota", frota)
            put("operacao", "ABASTECIMENTO")
            put("item", item)
            put("unidade", "LT")
            put("qtd", qtd)
            if (unitario.isNotBlank()) put("unitario", unitario)
            if (horimetro.isNotBlank()) put("horimetro", horimetro)
            if (local.isNotBlank()) put("local", local)
            if (responsavel.isNotBlank()) put("colaborador", responsavel)
        }
        viewModelScope.launch {
            val result = recordRepository.createRecord("frota", fields)
            saving.value = false
            onDone(result)
        }
    }
}

/** Réplica de QuickAbastecimentoButton (Dialog) -- lançamento rápido de
 * combustível em Frota, 2-3 toques (data/unidade/operação já vêm prontos). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAbastecimentoDialog(onDismiss: () -> Unit, onSaved: () -> Unit, viewModel: QuickAbastecimentoViewModel = viewModel()) {
    LaunchedEffect(Unit) { viewModel.loadLookups() }
    val context = LocalContext.current
    val frotas by viewModel.frotas
    val locais by viewModel.locais
    val colaboradores by viewModel.colaboradores
    val saving by viewModel.saving
    val qrMensagem by viewModel.qrMensagem
    val lendoQr by viewModel.lendoQr
    var copyMessage by remember { mutableStateOf<String?>(null) }

    var pendingQrUri by remember { mutableStateOf<Uri?>(null) }
    val takeQrPicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingQrUri
        if (success && uri != null) {
            viewModel.onQrPhotoTaken(context, uri)
        } else {
            viewModel.onQrPhotoCancelled()
        }
    }
    fun launchQrCamera() {
        // "Continuo sem acesso... há como forçar a abertura" (3ª reclamação):
        // confere ANTES se existe algum app no aparelho capaz de responder ao
        // Intent de captura -- sem isso o launch() falhava em silêncio em
        // aparelho/ROM sem nenhum app de câmera.
        if (!temAppDeCameraDisponivel(context)) {
            avisarSemAppDeCamera(context)
            viewModel.onQrPhotoCancelled()
            return
        }
        try {
            val file = File(File(context.cacheDir, "frota_qr").apply { mkdirs() }, "qr_${System.currentTimeMillis()}.jpg")
            // createNewFile() antes do Uri -- mesma incompatibilidade de
            // fabricante (Xiaomi/MIUI, Samsung) documentada em RomaneioQuickScreen.kt.
            file.createNewFile()
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            pendingQrUri = uri
            takeQrPicture.launch(uri)
        } catch (e: Exception) {
            AppLog.e("QuickAbastecimentoDialog", "Falha ao abrir a câmera pra ler o QR Code da frota", e)
            viewModel.onQrPhotoCancelled()
        }
    }
    // Ícone de câmera/QR não abria em alguns aparelhos -- o app nunca pedia
    // a permissão CAMERA em tempo de execução, e vários apps de câmera de
    // fabricante (Xiaomi/MIUI, Samsung) recusam SILENCIOSAMENTE o Intent
    // implícito quando ela não está concedida (sem erro nenhum, só não
    // acontece nada ao tocar). Pede a permissão antes de abrir a câmera;
    // só chama launchQrCamera() depois de concedida.
    // "Continuo sem acesso a câmera e leitor QR" (2ª reclamação -- a correção
    // anterior só avisava, não resolvia): uma vez que o Android nega a
    // permissão CAMERA permanentemente, `shouldShowRequestPermissionRationale`
    // vira false e pedir de novo não mostra diálogo nenhum. Detecta esse
    // estado e manda direto pra Configurações do app.
    var cameraPermanentementeNegada by remember { mutableStateOf(false) }
    // "há como forçar a abertura" (3ª reclamação) -- detecta a negação
    // permanente já na ABERTURA do diálogo, em vez de só depois de um 1º
    // toque morto no botão.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        cameraPermanentementeNegada = isCameraPermanentementeNegada(context)
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedida ->
        if (concedida) {
            cameraPermanentementeNegada = false
            launchQrCamera()
        } else {
            val activity = context as? android.app.Activity
            cameraPermanentementeNegada = activity != null &&
                !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
            viewModel.onQrPhotoCancelled()
        }
    }
    fun launchQrCameraComPermissao() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            cameraPermanentementeNegada = false
            launchQrCamera()
        } else if (cameraPermanentementeNegada) {
            openAppSettings(context)
        } else {
            marcarCameraPermissaoJaPedida(context)
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // "Colocar a condição de acessar o QR nativo do celular, pq existem
    // celulares mais antigos que o QR code é separado da câmera" (pedido do
    // usuário): o fluxo acima (foto + ML Kit) depende da câmera do app saber
    // focar de perto o suficiente pra decodificar um QR Code -- em aparelhos
    // mais antigos/ROMs customizadas isso nem sempre funciona bem. Este
    // segundo botão usa o leitor de QR NATIVO de verdade do Android moderno
    // (Code Scanner API do Google Play Services, GmsBarcodeScanning --
    // ver comentário completo em iniciarLeitorQrNativo/CameraPermissionUtils.kt):
    // diferente da 1ª tentativa (#883, Intent implícito pra action do antigo
    // ZXing, confirmado pelo usuário que NÃO existe mais em celular nenhum),
    // esta é uma chamada de API direta que abre um bottomsheet pronto do
    // próprio sistema, sem precisar da permissão CAMERA do nosso próprio app.
    fun launchQrExterno() {
        iniciarLeitorQrNativo(
            context = context,
            onSucesso = { texto -> viewModel.onQrTextoLidoExterno(texto) },
            onCancelado = { viewModel.onQrPhotoCancelled() },
            onFalha = { e ->
                AppLog.e("QuickAbastecimentoDialog", "Falha ao abrir o leitor de QR nativo do aparelho", e)
                android.widget.Toast.makeText(context, "Não foi possível abrir o leitor de QR do aparelho -- use o botão de foto.", android.widget.Toast.LENGTH_LONG).show()
            },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Abastecimento rápido") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Só o essencial -- data, unidade e operação já ficam prontos automaticamente.", modifier = Modifier.weight(1f))
                    // "Copiar último abastecimento" virou ícone -- pedido do
                    // usuário ("dentro dos lançamentos rápidos converta
                    // apenas a um ícone copiar"), mesmo padrão do ícone de
                    // copiar em Novo Lançamento (DomainFormScreen.kt).
                    androidx.compose.material3.IconButton(onClick = {
                        viewModel.copyFromLastAbastecimento { found ->
                            copyMessage = if (found) "Campos preenchidos com o último abastecimento -- confira antes de lançar." else "Nenhum abastecimento lançado ainda para copiar."
                        }
                    }) {
                        androidx.compose.material3.Icon(
                            Icons.Filled.ContentCopy,
                            contentDescription = "Copiar último abastecimento",
                            tint = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (copyMessage != null) Text(copyMessage!!, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)

                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f)) {
                        LookupDropdown("Máquina/Frota *", frotas, viewModel.frota) { viewModel.frota = it }
                    }
                    androidx.compose.material3.IconButton(onClick = { launchQrCameraComPermissao() }, enabled = !lendoQr) {
                        if (lendoQr) {
                            CircularProgressIndicator(modifier = Modifier)
                        } else {
                            androidx.compose.material3.Icon(
                                Icons.Filled.QrCodeScanner,
                                contentDescription = "Ler QR Code da máquina (foto)",
                                tint = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    // 2º botão -- leitor de QR NATIVO do aparelho (câmera ao
                    // vivo do app de QR/câmera de fábrica), alternativa ao
                    // botão de foto acima pra celulares mais antigos onde o
                    // leitor de QR é separado da câmera (pedido do usuário).
                    androidx.compose.material3.IconButton(onClick = { launchQrExterno() }, enabled = !lendoQr) {
                        androidx.compose.material3.Icon(
                            Icons.Filled.CenterFocusWeak,
                            contentDescription = "Ler QR Code com o leitor do aparelho",
                            tint = androidx.compose.material3.MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
                Text(
                    "Câmera lê por foto -- leitor do aparelho usa o app de QR nativo (melhor em celulares mais antigos).",
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (qrMensagem != null) Text(qrMensagem ?: "", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                if (cameraPermanentementeNegada) {
                    Text(
                        "Permissão da câmera bloqueada pelo sistema -- toque abaixo pra liberar.",
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                    )
                    androidx.compose.material3.TextButton(onClick = { openAppSettings(context) }) {
                        Text("Abrir Configurações do app")
                    }
                }
                LookupDropdown("Combustível", ITENS_COMBUSTIVEL.map { LookupEntity(category = "combustivel", value = it, label = it, order = 0) }, viewModel.item) { viewModel.item = it }
                OutlinedTextField(
                    value = viewModel.qtd,
                    onValueChange = { viewModel.qtd = it },
                    label = { Text("Litros *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = appFieldColors(),
                )
                OutlinedTextField(
                    value = viewModel.unitario,
                    onValueChange = { viewModel.unitario = it },
                    label = { Text("R$/litro") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = appFieldColors(),
                )
                OutlinedTextField(
                    value = viewModel.horimetro,
                    onValueChange = { viewModel.horimetro = it },
                    label = { Text("Horímetro") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = appFieldColors(),
                )
                LookupDropdown("Talhão/Fazenda (opcional)", locais, viewModel.local) { viewModel.local = it }
                LookupDropdown("Responsável (opcional)", colaboradores, viewModel.responsavel) { viewModel.responsavel = it }
            }
        },
        confirmButton = {
            Button(
                onClick = { viewModel.submit { onSaved() } },
                enabled = !saving && viewModel.frota.isNotBlank() && viewModel.qtd.isNotBlank(),
            ) {
                if (saving) CircularProgressIndicator(modifier = Modifier) else Text("Lançar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LookupDropdown(label: String, options: List<LookupEntity>, value: String, onChange: (String) -> Unit) {
    SearchableDropdownField(
        value = options.firstOrNull { it.value == value }?.label ?: value,
        label = label,
        options = remember(options) { options.map { it.label to it.label } },
        onSelect = { picked -> options.firstOrNull { it.label == picked }?.let { onChange(it.value) } },
        modifier = Modifier.fillMaxWidth(),
        emptyOptionLabel = null,
    )
}
