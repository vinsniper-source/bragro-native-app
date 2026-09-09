package com.bragro.mobile.ui.orcamento

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import com.bragro.mobile.ui.theme.Card
import com.bragro.mobile.ui.theme.appFieldColors
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bragro.mobile.data.AppLog
import com.bragro.mobile.data.local.FarmEntity
import com.bragro.mobile.data.local.LookupEntity
import com.bragro.mobile.data.model.OrcamentoItemData
import com.bragro.mobile.data.repo.ConfigRepository
import com.bragro.mobile.data.repo.OrcamentoRepository
import com.bragro.mobile.data.repo.OrcamentoUploadRepository
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

// Módulo de Orçamento (OCR + conciliação com nota mãe) -- ver
// handoff-ocr-orcamento.md. Tela "Novo Orçamento" com 3 blocos (pedido do
// usuário): cabeçalho (com 2 pontos de captura de foto -- requisição e
// comprovante/orçamento), itens repetíveis, status/observações. Os DOIS
// pontos de foto chamam OCR SEPARADOS de propósito (ocr_requisicao só lê
// cabeçalho, ocr_itens só lê a lista de itens) -- evita o bug de item
// duplicado que o handoff pede pra evitar. Mesmo padrão de tela/ViewModel já
// usado em RomaneioQuickScreen.kt (câmera+compressão+upload+OCR) e
// PedidoMultiItemScreen.kt (itens repetíveis em blocos individuais).

class OrcamentoLinha {
    var item by mutableStateOf("")
    var unidade by mutableStateOf("")
    var quantidade by mutableStateOf("")
    var valorUnitario by mutableStateOf("")
    var fazendaId by mutableStateOf<String?>(null)
    var equipamentoTalhao by mutableStateOf("")
    var statusEntrega by mutableStateOf("RETIRADO")
}

private val STATUS_ENTREGA_OPTIONS = listOf("RETIRADO", "PENDENTE", "PARCIAL")

private fun parseDecimal(s: String): Double =
    s.trim().replace(".", "").replace(",", ".").toDoubleOrNull()
        ?: s.trim().toDoubleOrNull()
        ?: 0.0

/** Remove acentos/caixa pra comparar nome de fazenda lido pelo OCR contra o
 * cadastro real -- mesmo critério (best-effort, nunca bloqueia) de
 * resolverFazendaId() no servidor (orcamento-ocr.ts), só que aqui client-side
 * pra já vir com o dropdown de Fazenda pré-selecionado quando bater. */
private fun normalizar(s: String): String =
    java.text.Normalizer.normalize(s.trim().lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "")

private fun encontrarFazendaPorNome(farms: List<FarmEntity>, nomeLido: String?): String? {
    if (nomeLido.isNullOrBlank()) return null
    val alvo = normalizar(nomeLido)
    return farms.firstOrNull { normalizar(it.name) == alvo }?.id
        ?: farms.firstOrNull { normalizar(it.name).contains(alvo) || alvo.contains(normalizar(it.name)) }?.id
}

class OrcamentoViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = OrcamentoRepository(app)
    private val uploadRepository = OrcamentoUploadRepository(app)
    private val configRepository = ConfigRepository(app)

    var fornecedoresOptions = mutableStateOf<List<LookupEntity>>(emptyList())
        private set
    var unidadesOptions = mutableStateOf<List<LookupEntity>>(emptyList())
        private set
    var farms = mutableStateOf<List<FarmEntity>>(emptyList())
        private set

    var data by mutableStateOf(SimpleDateFormat("dd/MM/yyyy", Locale.US).format(java.util.Date()))
    var requisicao by mutableStateOf("")
    var autorizadoPorNome by mutableStateOf("")
    var fornecedorNome by mutableStateOf<String?>(null)
    var numeroOrcamento by mutableStateOf("")
    var observacoes by mutableStateOf("")

    var fotoRequisicaoUrl = mutableStateOf<String?>(null)
        private set
    var fotoComprovanteUrl = mutableStateOf<String?>(null)
        private set
    var uploadingRequisicao = mutableStateOf(false)
        private set
    var uploadingComprovante = mutableStateOf(false)
        private set
    var ocrMensagemRequisicao = mutableStateOf<String?>(null)
        private set
    var ocrMensagemItens = mutableStateOf<String?>(null)
        private set

    val linhas = mutableStateListOf(OrcamentoLinha())

    var pending = mutableStateOf(false)
        private set
    var errorMessage = mutableStateOf<String?>(null)
        private set
    var successMessage = mutableStateOf<String?>(null)
        private set
    var copiando = mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            // "entidades_financeiro" -- mesma categoria usada como
            // Fornecedor em Cotações/Pedidos (ver findOrCreatePartnerId em
            // actions.ts): texto livre que vira/reaproveita um Partner no
            // servidor, não obriga cadastro formal antes de lançar.
            fornecedoresOptions.value = configRepository.lookupsByCategory("entidades_financeiro").sortedBy { it.label }
            unidadesOptions.value = configRepository.lookupsByCategory("unidades").sortedBy { it.label }
            farms.value = configRepository.farms()
        }
    }

    fun addLinha() {
        linhas.add(OrcamentoLinha())
    }

    fun removeLinha(i: Int) {
        if (linhas.size > 1) linhas.removeAt(i)
    }

    private fun linhasValidas() = linhas.filter { it.item.isNotBlank() && it.quantidade.isNotBlank() }

    fun podeSalvar(): Boolean = data.isNotBlank() && linhasValidas().isNotEmpty()

    /** Foto da REQUISIÇÃO -- lê só número da requisição + quem autorizou
     * (ver handoff-ocr-orcamento.md: 2 endpoints de OCR separados de
     * propósito, evita duplicar item na leitura automática). */
    fun onFotoRequisicaoTaken(context: Context, uri: Uri) {
        uploadingRequisicao.value = true
        ocrMensagemRequisicao.value = null
        viewModelScope.launch {
            val bytes = compressPhoto(context, uri)
            if (bytes == null) {
                uploadingRequisicao.value = false
                ocrMensagemRequisicao.value = "Não foi possível processar a foto -- tente novamente."
                return@launch
            }
            val url = uploadRepository.uploadFoto(bytes, "requisicao")
            if (url == null) {
                uploadingRequisicao.value = false
                ocrMensagemRequisicao.value = "Sem conexão -- não foi possível enviar a foto agora."
                return@launch
            }
            fotoRequisicaoUrl.value = url
            val resultado = repository.lerRequisicao(url)
            uploadingRequisicao.value = false
            if (resultado?.ok == true && resultado.campos != null) {
                resultado.campos.requisicao?.let { if (it.isNotBlank()) requisicao = it }
                resultado.campos.autorizadoPorNomeLido?.let { if (it.isNotBlank()) autorizadoPorNome = it }
                ocrMensagemRequisicao.value = "Campos pré-preenchidos automaticamente -- confira antes de lançar."
            } else {
                ocrMensagemRequisicao.value = "Foto enviada, mas não foi possível ler automaticamente -- preencha manualmente."
            }
        }
    }

    /** Foto do COMPROVANTE/ORÇAMENTO -- lê só a lista de itens. */
    fun onFotoComprovanteTaken(context: Context, uri: Uri) {
        uploadingComprovante.value = true
        ocrMensagemItens.value = null
        viewModelScope.launch {
            val bytes = compressPhoto(context, uri)
            if (bytes == null) {
                uploadingComprovante.value = false
                ocrMensagemItens.value = "Não foi possível processar a foto -- tente novamente."
                return@launch
            }
            val url = uploadRepository.uploadFoto(bytes, "comprovante")
            if (url == null) {
                uploadingComprovante.value = false
                ocrMensagemItens.value = "Sem conexão -- não foi possível enviar a foto agora."
                return@launch
            }
            fotoComprovanteUrl.value = url
            val resultado = repository.lerItens(url)
            uploadingComprovante.value = false
            val itensLidos = resultado?.itens
            if (resultado?.ok == true && !itensLidos.isNullOrEmpty()) {
                linhas.clear()
                itensLidos.forEach { lido ->
                    val linha = OrcamentoLinha()
                    linha.item = lido.item
                    linha.unidade = lido.unidade ?: ""
                    linha.quantidade = lido.quantidade?.let { formatQtd(it) } ?: ""
                    linha.valorUnitario = lido.valorUnitario?.let { formatQtd(it) } ?: ""
                    linha.fazendaId = lido.fazendaIdSugerido ?: encontrarFazendaPorNome(farms.value, lido.fazendaNomeLido)
                    linhas.add(linha)
                }
                ocrMensagemItens.value = "${itensLidos.size} item(ns) lido(s) automaticamente -- confira antes de lançar."
            } else {
                ocrMensagemItens.value = "Foto enviada, mas não foi possível ler os itens -- preencha manualmente."
            }
        }
    }

    private fun formatQtd(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString().replace(".", ",")

    private suspend fun compressPhoto(context: Context, uri: Uri): ByteArray? {
        return try {
            val original = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return null
            val rotated = applyExifRotation(context, uri, original)
            val maxLado = 1600
            val escala = minOf(1f, maxLado.toFloat() / maxOf(rotated.width, rotated.height))
            val largura = (rotated.width * escala).toInt().coerceAtLeast(1)
            val altura = (rotated.height * escala).toInt().coerceAtLeast(1)
            val redimensionada = Bitmap.createScaledBitmap(rotated, largura, altura, true)
            val out = ByteArrayOutputStream()
            redimensionada.compress(Bitmap.CompressFormat.JPEG, 75, out)
            out.toByteArray()
        } catch (e: Exception) {
            AppLog.e("OrcamentoScreen", "Falha ao comprimir/redimensionar foto do orçamento", e)
            null
        }
    }

    private fun applyExifRotation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            val exif = context.contentResolver.openInputStream(uri)?.use { ExifInterface(it) } ?: return bitmap
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            val degrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (degrees == 0f) return bitmap
            val matrix = Matrix().apply { postRotate(degrees) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: Exception) {
            AppLog.e("OrcamentoScreen", "Falha ao ler EXIF/rotacionar foto do orçamento -- mantendo bitmap original", e)
            bitmap
        }
    }

    fun onPhotoCancelled() {
        ocrMensagemRequisicao.value = ocrMensagemRequisicao.value
    }

    /** "Copiar último lançamento" -- busca o último Orçamento lançado no
     * servidor (sem cache offline de propósito, ver comentário em
     * OrcamentoRepository.kt) e preenche o cabeçalho + a primeira linha de
     * item, mesmo padrão de preencherComUltimo() em PedidoMultiItemScreen.kt. */
    fun preencherComUltimo() {
        viewModelScope.launch {
            copiando.value = true
            val resposta = repository.listar()
            copiando.value = false
            val last = resposta?.orcamentos?.firstOrNull()
            if (last == null) {
                errorMessage.value = "Nenhum orçamento lançado ainda para copiar."
                return@launch
            }
            last.autorizadoPorNome?.let { autorizadoPorNome = it }
            val primeiroItem = last.itens.firstOrNull()
            if (primeiroItem != null) {
                val linha = OrcamentoLinha()
                linha.item = primeiroItem.item
                linha.unidade = primeiroItem.unidade ?: ""
                linha.quantidade = formatQtd(primeiroItem.quantidade)
                linha.valorUnitario = formatQtd(primeiroItem.valorUnitario)
                linha.fazendaId = primeiroItem.fazendaId
                linha.equipamentoTalhao = primeiroItem.equipamentoTalhao ?: ""
                linha.statusEntrega = primeiroItem.statusEntrega ?: "RETIRADO"
                linhas.clear()
                linhas.add(linha)
            }
            successMessage.value = null
            errorMessage.value = null
        }
    }

    fun reset() {
        data = SimpleDateFormat("dd/MM/yyyy", Locale.US).format(java.util.Date())
        requisicao = ""; autorizadoPorNome = ""; fornecedorNome = null
        numeroOrcamento = ""; observacoes = ""
        fotoRequisicaoUrl.value = null; fotoComprovanteUrl.value = null
        ocrMensagemRequisicao.value = null; ocrMensagemItens.value = null
        linhas.clear(); linhas.add(OrcamentoLinha())
        successMessage.value = null
        errorMessage.value = null
    }

    fun submit() {
        if (!podeSalvar()) return
        val validas = linhasValidas()
        pending.value = true
        errorMessage.value = null
        viewModelScope.launch {
            val resultado = repository.criar(
                data = brDateToIso(data),
                compradorId = null,
                fornecedorId = null,
                fornecedorNome = fornecedorNome,
                numeroOrcamento = numeroOrcamento.trim().ifBlank { null },
                requisicao = requisicao.trim().ifBlank { null },
                autorizadoPorId = null,
                autorizadoPorNome = autorizadoPorNome.trim().ifBlank { null },
                fotoRequisicaoUrl = fotoRequisicaoUrl.value,
                fotoComprovanteUrl = fotoComprovanteUrl.value,
                observacoes = observacoes.trim().ifBlank { null },
                origemId = null,
                itens = validas.map {
                    OrcamentoItemData(
                        item = it.item,
                        unidade = it.unidade.ifBlank { null },
                        quantidade = parseDecimal(it.quantidade),
                        valorUnitario = parseDecimal(it.valorUnitario),
                        fazendaId = it.fazendaId,
                        equipamentoTalhao = it.equipamentoTalhao.ifBlank { null },
                        statusEntrega = it.statusEntrega,
                    )
                },
            )
            pending.value = false
            if (resultado == null || !resultado.ok) {
                errorMessage.value = resultado?.error ?: "Erro ao lançar o orçamento."
                return@launch
            }
            successMessage.value = "${resultado.itens ?: validas.size} item(ns) lançado(s) no orçamento."
        }
    }
}

private fun brDateToIso(br: String): String {
    val m = Regex("^(\\d{2})/(\\d{2})/(\\d{4})$").find(br.trim())
        ?: return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date())
    val (d, mo, y) = m.destructured
    return "$y-$mo-$d"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StringDropdown(
    label: String,
    value: String?,
    options: List<String>,
    placeholder: String,
    allowEmpty: Boolean = false,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            colors = appFieldColors(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (allowEmpty) {
                DropdownMenuItem(text = { Text(" ") }, onClick = { onSelect(null); expanded = false })
            }
            for (opt in options) {
                DropdownMenuItem(text = { Text(opt, maxLines = 1, overflow = TextOverflow.Ellipsis) }, onClick = { onSelect(opt); expanded = false })
            }
        }
    }
}

// Bloco individual por campo dentro de cada item -- mesmo critério de
// PedidoMultiItemScreen.kt/CotacaoMultiItemScreen.kt (scrim onSurface 0.12f,
// contraste com qualquer fundo por trás).
@Composable
private fun ItemFieldBlock(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.small,
    ) {
        Box(modifier = Modifier.padding(6.dp)) {
            content()
        }
    }
}

@Composable
private fun OrcamentoLinhaCard(
    linha: OrcamentoLinha,
    unidadesOptions: List<LookupEntity>,
    farms: List<FarmEntity>,
    showRemove: Boolean,
    onRemove: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ItemFieldBlock {
                OutlinedTextField(
                    value = linha.item,
                    onValueChange = { linha.item = it },
                    label = { Text("Item *") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = appFieldColors(),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ItemFieldBlock(modifier = Modifier.weight(1f)) {
                    StringDropdown(
                        label = "Unidade",
                        value = unidadesOptions.firstOrNull { it.value == linha.unidade }?.label ?: linha.unidade.ifBlank { null },
                        options = unidadesOptions.map { it.label },
                        placeholder = "Opcional",
                        allowEmpty = true,
                        onSelect = { picked -> linha.unidade = unidadesOptions.firstOrNull { it.label == picked }?.value ?: picked.orEmpty() },
                    )
                }
                ItemFieldBlock(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = linha.quantidade,
                        onValueChange = { linha.quantidade = it },
                        label = { Text("Quantidade *") },
                        placeholder = { Text("0") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = appFieldColors(),
                    )
                }
            }
            ItemFieldBlock {
                OutlinedTextField(
                    value = linha.valorUnitario,
                    onValueChange = { linha.valorUnitario = it },
                    label = { Text("Valor unitário") },
                    placeholder = { Text("0,00") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = appFieldColors(),
                )
            }
            ItemFieldBlock {
                StringDropdown(
                    label = "Fazenda",
                    value = farms.firstOrNull { it.id == linha.fazendaId }?.name,
                    options = farms.map { it.name },
                    placeholder = "Opcional",
                    allowEmpty = true,
                    onSelect = { picked -> linha.fazendaId = farms.firstOrNull { it.name == picked }?.id },
                )
            }
            ItemFieldBlock {
                OutlinedTextField(
                    value = linha.equipamentoTalhao,
                    onValueChange = { linha.equipamentoTalhao = it },
                    label = { Text("Equipamento/Talhão") },
                    placeholder = { Text("Opcional") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = appFieldColors(),
                )
            }
            ItemFieldBlock {
                StringDropdown(
                    label = "Status de entrega",
                    value = linha.statusEntrega,
                    options = STATUS_ENTREGA_OPTIONS,
                    placeholder = "Selecione",
                    onSelect = { picked -> linha.statusEntrega = picked ?: "RETIRADO" },
                )
            }
            if (showRemove) {
                IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remover item", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrcamentoScreen(onBack: () -> Unit, viewModel: OrcamentoViewModel = viewModel()) {
    val context = LocalContext.current
    val fornecedoresOptions by viewModel.fornecedoresOptions
    val unidadesOptions by viewModel.unidadesOptions
    val farms by viewModel.farms
    val fotoRequisicaoUrl by viewModel.fotoRequisicaoUrl
    val fotoComprovanteUrl by viewModel.fotoComprovanteUrl
    val uploadingRequisicao by viewModel.uploadingRequisicao
    val uploadingComprovante by viewModel.uploadingComprovante
    val ocrMensagemRequisicao by viewModel.ocrMensagemRequisicao
    val ocrMensagemItens by viewModel.ocrMensagemItens
    val pending by viewModel.pending
    val errorMessage by viewModel.errorMessage
    val successMessage by viewModel.successMessage
    val copiando by viewModel.copiando

    // 2 pontos de captura de foto (requisição / comprovante) -- cada um com
    // seu próprio Uri pendente e seu próprio launcher, mesmo padrão de
    // FileProvider+createNewFile() de RomaneioQuickScreen.kt (evita a
    // incompatibilidade com apps de Câmera de fabricante Xiaomi/Samsung).
    var pendingRequisicaoUri by remember { mutableStateOf<Uri?>(null) }
    val takeRequisicaoPicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingRequisicaoUri
        if (success && uri != null) viewModel.onFotoRequisicaoTaken(context, uri)
        else if (!success) viewModel.onPhotoCancelled()
    }
    var pendingComprovanteUri by remember { mutableStateOf<Uri?>(null) }
    val takeComprovantePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingComprovanteUri
        if (success && uri != null) viewModel.onFotoComprovanteTaken(context, uri)
        else if (!success) viewModel.onPhotoCancelled()
    }

    fun launchCamera(prefixo: String, setPending: (Uri) -> Unit, launcher: androidx.activity.result.ActivityResultLauncher<Uri>) {
        try {
            val file = File(File(context.cacheDir, "orcamento").apply { mkdirs() }, "${prefixo}_${System.currentTimeMillis()}.jpg")
            file.createNewFile()
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            setPending(uri)
            launcher.launch(uri)
        } catch (e: Exception) {
            AppLog.e("OrcamentoScreen", "Falha ao abrir a câmera pra foto do orçamento ($prefixo)", e)
            viewModel.onPhotoCancelled()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Novo Orçamento", color = MaterialTheme.colorScheme.primary)
                    }
                },
                navigationIcon = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                // Ícone Copiar no topo -- mesmo padrão de Pedidos/Cotações/
                // Romaneio Rápido (preencherComUltimo).
                actions = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        IconButton(onClick = { viewModel.preencherComUltimo() }, enabled = !copiando) {
                            if (copiando) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Filled.ContentCopy, contentDescription = "Copiar último orçamento", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (successMessage != null) {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Orçamento lançado com sucesso", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(successMessage ?: "", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Button(onClick = { viewModel.reset() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Lançar outro orçamento")
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "Fica pendente até a nota fiscal chegar e ser conciliada em Financeiro.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (errorMessage != null) {
                item { Text(errorMessage ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
            // Bloco 1: cabeçalho.
            item {
                OutlinedTextField(
                    value = viewModel.data,
                    onValueChange = { viewModel.data = it },
                    label = { Text("Data *") },
                    placeholder = { Text("DD/MM/AAAA") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = appFieldColors(),
                )
            }
            item {
                OutlinedTextField(
                    value = viewModel.requisicao,
                    onValueChange = { viewModel.requisicao = it },
                    label = { Text("Requisição") },
                    placeholder = { Text("Nº da requisição") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = appFieldColors(),
                )
            }
            item {
                OutlinedTextField(
                    value = viewModel.autorizadoPorNome,
                    onValueChange = { viewModel.autorizadoPorNome = it },
                    label = { Text("Autorizado por") },
                    placeholder = { Text("Nome de quem autorizou") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = appFieldColors(),
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(
                        onClick = { launchCamera("requisicao", { uri -> pendingRequisicaoUri = uri }, takeRequisicaoPicture) },
                        enabled = !uploadingRequisicao,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (uploadingRequisicao) {
                            CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                            Text("Lendo e enviando foto...")
                        } else {
                            Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                            Text(if (fotoRequisicaoUrl != null) "Trocar foto da requisição" else "Foto da requisição (lê automaticamente)")
                        }
                    }
                    if (ocrMensagemRequisicao != null) {
                        Text(ocrMensagemRequisicao ?: "", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            item {
                StringDropdown(
                    label = "Fornecedor",
                    value = viewModel.fornecedorNome,
                    options = fornecedoresOptions.map { it.label },
                    placeholder = "Opcional",
                    allowEmpty = true,
                    onSelect = { picked -> viewModel.fornecedorNome = picked },
                )
            }
            item {
                OutlinedTextField(
                    value = viewModel.numeroOrcamento,
                    onValueChange = { viewModel.numeroOrcamento = it },
                    label = { Text("Nº do orçamento") },
                    placeholder = { Text("Opcional") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = appFieldColors(),
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(
                        onClick = { launchCamera("comprovante", { uri -> pendingComprovanteUri = uri }, takeComprovantePicture) },
                        enabled = !uploadingComprovante,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (uploadingComprovante) {
                            CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                            Text("Lendo e enviando foto...")
                        } else {
                            Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                            Text(if (fotoComprovanteUrl != null) "Trocar foto do comprovante/orçamento" else "Foto do comprovante/orçamento (lê os itens)")
                        }
                    }
                    if (ocrMensagemItens != null) {
                        Text(ocrMensagemItens ?: "", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }
            // Bloco 2: itens.
            item { Text("Itens *", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
            items(viewModel.linhas.size) { i ->
                OrcamentoLinhaCard(
                    linha = viewModel.linhas[i],
                    unidadesOptions = unidadesOptions,
                    farms = farms,
                    showRemove = viewModel.linhas.size > 1,
                    onRemove = { viewModel.removeLinha(i) },
                )
            }
            item {
                OutlinedButton(onClick = { viewModel.addLinha() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                    Text("Adicionar item")
                }
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }
            // Bloco 3: observações.
            item {
                OutlinedTextField(
                    value = viewModel.observacoes,
                    onValueChange = { viewModel.observacoes = it },
                    label = { Text("Observações") },
                    placeholder = { Text("Opcional") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = appFieldColors(),
                )
            }
            item {
                Button(
                    onClick = { viewModel.submit() },
                    enabled = !pending && viewModel.podeSalvar(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (pending) CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                    else Text("Lançar orçamento")
                }
            }
        }
    }
}
