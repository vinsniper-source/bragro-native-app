package com.bragro.mobile.ui.pragas

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import com.bragro.mobile.ui.theme.Card
import com.bragro.mobile.ui.theme.appFieldColors
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bragro.mobile.data.AppLog
import com.bragro.mobile.data.local.LookupEntity
import com.bragro.mobile.data.repo.ConfigRepository
import com.bragro.mobile.data.repo.PragaIaRepository
import com.bragro.mobile.data.repo.PragaUploadRepository
import com.bragro.mobile.data.repo.RecordRepository
import com.bragro.mobile.data.repo.SaveResult
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

// Paridade com o site (Task #601, quick-praga-foto-button.tsx): "Diagnóstico
// por Foto" pro módulo Pragas. Mesmo espirito de tela/ViewModel de
// RomaneioQuickScreen.kt (câmera+compressão+upload), só que em vez de OCR
// local (ML Kit), a leitura é uma IA de VISÃO no servidor (Claude, mesma
// ANTHROPIC_API_KEY do OCR de Romaneio/Orçamento -- ver PragaIaRepository e
// lib/services/pragas-ia.ts) -- por isso precisa de conexão pra funcionar,
// diferente do OCR de Romaneio. O diagnóstico é só uma AJUDA de triagem: o
// campo real "Alvo (praga/doença)" continua sendo escolhido pelo usuário no
// dropdown, nunca preenchido sozinho pela IA.
class PragaFotoViewModel(app: Application) : AndroidViewModel(app) {
    private val configRepository = ConfigRepository(app)
    private val recordRepository = RecordRepository(app)
    private val uploadRepository = PragaUploadRepository(app)
    private val iaRepository = PragaIaRepository(app)

    var fazendas = mutableStateOf<List<LookupEntity>>(emptyList())
        private set
    var safras = mutableStateOf<List<LookupEntity>>(emptyList())
        private set
    var alvos = mutableStateOf<List<LookupEntity>>(emptyList())
        private set
    var colaboradores = mutableStateOf<List<LookupEntity>>(emptyList())
        private set

    var fazenda = mutableStateOf<String?>(null)
        private set
    var safra = mutableStateOf<String?>(null)
        private set
    var alvo = mutableStateOf<String?>(null)
        private set
    var responsavel = mutableStateOf<String?>(null)
        private set
    var observacoes = mutableStateOf("")
        private set

    var fotoUrl = mutableStateOf<String?>(null)
        private set
    var diagnosticoTexto = mutableStateOf<String?>(null)
        private set
    var diagnosticoStatus = mutableStateOf<String?>(null)
        private set
    var mensagemFoto = mutableStateOf<String?>(null)
        private set
    var enviandoFoto = mutableStateOf(false)
        private set
    var saving = mutableStateOf(false)
        private set
    var resultMessage = mutableStateOf<String?>(null)
        private set
    var savedOk = mutableStateOf(false)
        private set
    var copiando = mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            fazendas.value = configRepository.lookupsByCategory("locais")
            safras.value = configRepository.lookupsByCategory("safras")
            alvos.value = configRepository.lookupsByCategory("pragas_alvo")
            colaboradores.value = configRepository.lookupsByCategory("colaboradores")
        }
    }

    fun setFazenda(v: String) { fazenda.value = v }
    fun setSafra(v: String) { safra.value = v }
    fun setAlvo(v: String) { alvo.value = v }
    fun setResponsavel(v: String) { responsavel.value = v }
    fun setObservacoes(v: String) { observacoes.value = v }

    /** Ícone "Copiar" no topo (gap encontrado em auditoria de paridade,
     * pedido do usuário) -- mesmo padrão de RomaneioQuickScreen.kt: só copia
     * Alvo/Fazenda/Safra/Responsável do último monitoramento, já que essas
     * costumam se repetir. Foto e diagnóstico da IA nunca são copiados --
     * cada ocorrência precisa da própria foto tirada na hora. */
    fun preencherComUltimo() {
        viewModelScope.launch {
            copiando.value = true
            val last = recordRepository.mostRecent("pragas")
            copiando.value = false
            if (last == null) {
                mensagemFoto.value = "Nenhum monitoramento lançado ainda para copiar."
                return@launch
            }
            last["alvo"]?.let { alvo.value = it }
            last["fazenda"]?.let { fazenda.value = it }
            last["safra"]?.let { safra.value = it }
            last["responsavel"]?.let { responsavel.value = it }
        }
    }

    fun onPhotoCancelled() {
        mensagemFoto.value = "Nenhuma foto capturada -- tire a foto pra rodar o diagnóstico automático, ou lance manualmente escolhendo o Alvo."
    }

    fun onCameraLaunchFailed() {
        mensagemFoto.value = "Não foi possível abrir a câmera neste aparelho -- lance manualmente escolhendo o Alvo."
    }

    /** Mesma sequência de RomaneioQuickViewModel.onPhotoTaken: comprime,
     * sobe pro Storage e SÓ ENTÃO chama a IA (precisa da URL pública já
     * publicada pro servidor conseguir baixar a foto e mandar pro modelo de
     * visão). Nunca bloqueia o lançamento se qualquer etapa falhar. */
    fun onPhotoTaken(context: Context, uri: Uri) {
        enviandoFoto.value = true
        mensagemFoto.value = null
        diagnosticoTexto.value = null
        diagnosticoStatus.value = null
        viewModelScope.launch {
            val bytes = compressPhoto(context, uri)
            if (bytes == null) {
                enviandoFoto.value = false
                mensagemFoto.value = "Não foi possível processar a foto -- tente novamente ou lance manualmente."
                return@launch
            }
            val url = uploadRepository.uploadFoto(bytes)
            if (url == null) {
                enviandoFoto.value = false
                mensagemFoto.value = "Sem conexão -- não foi possível enviar a foto agora. Lance manualmente escolhendo o Alvo."
                return@launch
            }
            fotoUrl.value = url

            val resultado = iaRepository.diagnosticar(url)
            enviandoFoto.value = false
            if (resultado == null) {
                mensagemFoto.value = "Foto enviada, mas sem conexão para rodar o diagnóstico -- confira antes de enviar de novo, ou escolha o Alvo manualmente."
                return@launch
            }
            diagnosticoStatus.value = resultado.status
            if (resultado.ok && resultado.diagnostico != null) {
                diagnosticoTexto.value = resultado.diagnostico
            } else {
                mensagemFoto.value = resultado.mensagem ?: resultado.error ?: "Não foi possível gerar o diagnóstico automático."
            }
        }
    }

    /** Mesma ideia de comprimirFoto() do site/RomaneioQuickScreen.kt:
     * redimensiona pro maior lado não passar de 1600px e reexporta em JPEG
     * qualidade 0.75 -- suficiente pra IA de visão analisar sem gastar
     * banda/dados no campo. */
    private fun compressPhoto(context: Context, uri: Uri): ByteArray? {
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
            AppLog.e("PragaFotoScreen", "Falha ao comprimir/redimensionar foto da praga antes do upload", e)
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
            AppLog.e("PragaFotoScreen", "Falha ao ler EXIF/rotacionar foto da praga -- mantendo bitmap original", e)
            bitmap
        }
    }

    fun submit() {
        if (alvo.value.isNullOrBlank()) {
            resultMessage.value = "Escolha o Alvo (praga/doença) antes de lançar."
            return
        }
        saving.value = true
        resultMessage.value = null
        viewModelScope.launch {
            val hoje = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date())
            val fields = mutableMapOf(
                "data" to hoje,
                "alvo" to (alvo.value ?: ""),
            )
            safra.value?.let { fields["safra"] = it }
            fazenda.value?.let { fields["fazenda"] = it }
            responsavel.value?.let { fields["responsavel"] = it }
            if (observacoes.value.isNotBlank()) fields["observacoes"] = observacoes.value
            fotoUrl.value?.let { fields["fotoUrl"] = it }
            diagnosticoTexto.value?.let { fields["diagnosticoIa"] = it }
            diagnosticoStatus.value?.let { fields["diagnosticoStatus"] = it }

            when (val result = recordRepository.createRecord("pragas", fields)) {
                is SaveResult.SavedOnline -> {
                    resultMessage.value = "Ocorrência de praga/doença lançada."
                    savedOk.value = true
                }
                is SaveResult.SavedOffline -> {
                    resultMessage.value = "Sem conexão — lançamento salvo neste aparelho. Sincroniza sozinho quando a internet voltar."
                    savedOk.value = true
                }
                is SaveResult.Failure -> {
                    resultMessage.value = result.message
                }
            }
            saving.value = false
        }
    }

    fun reset() {
        fazenda.value = null
        safra.value = null
        alvo.value = null
        responsavel.value = null
        observacoes.value = ""
        fotoUrl.value = null
        diagnosticoTexto.value = null
        diagnosticoStatus.value = null
        mensagemFoto.value = null
        resultMessage.value = null
        savedOk.value = false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LookupDropdown(label: String, value: String?, options: List<LookupEntity>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.find { it.value == value }?.label
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedLabel ?: "Selecione",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            colors = appFieldColors(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (opt in options) {
                DropdownMenuItem(text = { Text(opt.label) }, onClick = { onSelect(opt.value); expanded = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PragaFotoScreen(onBack: () -> Unit, viewModel: PragaFotoViewModel = viewModel()) {
    val context = LocalContext.current
    val fazenda by viewModel.fazenda
    val safra by viewModel.safra
    val alvo by viewModel.alvo
    val responsavel by viewModel.responsavel
    val observacoes by viewModel.observacoes
    val fazendas by viewModel.fazendas
    val safras by viewModel.safras
    val alvos by viewModel.alvos
    val colaboradores by viewModel.colaboradores
    val fotoUrl by viewModel.fotoUrl
    val diagnosticoTexto by viewModel.diagnosticoTexto
    val mensagemFoto by viewModel.mensagemFoto
    val enviandoFoto by viewModel.enviandoFoto
    val saving by viewModel.saving
    val resultMessage by viewModel.resultMessage
    val savedOk by viewModel.savedOk
    val copiando by viewModel.copiando

    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingPhotoUri
        if (success && uri != null) {
            viewModel.onPhotoTaken(context, uri)
        } else if (!success) {
            viewModel.onPhotoCancelled()
        }
    }

    fun launchCamera() {
        try {
            val file = File(File(context.cacheDir, "pragas").apply { mkdirs() }, "praga_${System.currentTimeMillis()}.jpg")
            // createNewFile() ANTES de gerar o Uri -- mesma incompatibilidade
            // de fabricante (Xiaomi/MIUI, Samsung) documentada em
            // RomaneioQuickScreen.kt.
            file.createNewFile()
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            pendingPhotoUri = uri
            takePicture.launch(uri)
        } catch (e: Exception) {
            AppLog.e("PragaFotoScreen", "Falha ao abrir a câmera pra foto de diagnóstico de praga", e)
            viewModel.onCameraLaunchFailed()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Diagnóstico por Foto", color = MaterialTheme.colorScheme.primary)
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
                // Ícone Copiar no canto superior direito -- gap encontrado em
                // auditoria de paridade, mesmo padrão de RomaneioQuickScreen.kt.
                actions = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        IconButton(onClick = { viewModel.preencherComUltimo() }, enabled = !copiando) {
                            if (copiando) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Filled.ContentCopy, contentDescription = "Copiar último lançamento", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "Tire uma foto da planta/folha/inseto -- a IA sugere o que pode ser, mas quem decide o Alvo (praga/doença) real é você.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(onClick = { launchCamera() }, enabled = !enviandoFoto, modifier = Modifier.fillMaxWidth()) {
                        if (enviandoFoto) {
                            CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                            Text("Enviando e analisando foto...")
                        } else {
                            Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                            Text(if (fotoUrl != null) "Trocar foto" else "Tirar foto da praga/doença")
                        }
                    }
                    if (mensagemFoto != null) {
                        Text(mensagemFoto ?: "", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (diagnosticoTexto != null) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Diagnóstico da IA", style = MaterialTheme.typography.labelLarge)
                            Text(diagnosticoTexto ?: "", style = MaterialTheme.typography.bodySmall)
                            Text(
                                "Só uma triagem -- confira antes de confiar. Escolha o Alvo abaixo.",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
            item {
                LookupDropdown("Alvo (praga/doença) *", alvo, alvos) { viewModel.setAlvo(it) }
            }
            item {
                LookupDropdown("Fazenda (opcional)", fazenda, fazendas) { viewModel.setFazenda(it) }
            }
            item {
                LookupDropdown("Safra (opcional)", safra, safras) { viewModel.setSafra(it) }
            }
            item {
                LookupDropdown("Responsável (opcional)", responsavel, colaboradores) { viewModel.setResponsavel(it) }
            }
            item {
                OutlinedTextField(
                    value = observacoes,
                    onValueChange = { viewModel.setObservacoes(it) },
                    label = { Text("Observações (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = appFieldColors(),
                )
            }
            if (resultMessage != null) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(resultMessage ?: "")
                            if (savedOk) {
                                Button(onClick = { viewModel.reset() }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                    Text("Lançar outra ocorrência")
                                }
                            }
                        }
                    }
                }
            }
            item {
                Button(
                    onClick = { viewModel.submit() },
                    enabled = !saving && !savedOk,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (saving) CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                    else Text("Lançar")
                }
            }
        }
    }
}
