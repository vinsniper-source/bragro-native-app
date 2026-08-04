package com.bragro.mobile.ui.romaneio

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import com.bragro.mobile.data.local.LookupEntity
import com.bragro.mobile.data.repo.ConfigRepository
import com.bragro.mobile.data.repo.RecordRepository
import com.bragro.mobile.data.repo.RomaneioUploadRepository
import com.bragro.mobile.data.repo.SaveResult
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

// Fase 2 do app nativo (Task #42): "Romaneio rapido" -- mesmo espirito do
// componente QuickRomaneioButton do site (components/domain/
// quick-romaneio-button.tsx): so o essencial (numero, peso bruto, tara) pra
// lancar direto na balanca; os campos calculados (liquido, desconto por
// umidade/impureza, sacas, tonelada) saem sozinhos no SERVIDOR
// (computeRomaneioFields, ver lib/services/romaneios.ts) quando o
// lancamento sincroniza -- nao recalculados aqui. A foto do ticket sobe pro
// MESMO bucket do Supabase Storage que o site ja usa (RomaneioUploadRepository).
// A diferenca em relacao ao site: o site tem a ESTRUTURA de OCR pronta mas
// nenhum provedor configurado (sempre "NAO_CONFIGURADO"); aqui a leitura
// roda de verdade, 100% no aparelho, via ML Kit (RomaneioOcrParser) -- sem
// custo de servidor e funcionando ate offline (o unico passo que precisa de
// rede e o upload da foto/lancamento em si).
class RomaneioQuickViewModel(app: Application) : AndroidViewModel(app) {
    private val configRepository = ConfigRepository(app)
    private val recordRepository = RecordRepository(app)
    private val uploadRepository = RomaneioUploadRepository(app)

    var frotas = mutableStateOf<List<LookupEntity>>(emptyList())
        private set
    var colaboradores = mutableStateOf<List<LookupEntity>>(emptyList())
        private set

    var noRomaneio = mutableStateOf("")
        private set
    var pesoBrutoKg = mutableStateOf("")
        private set
    var taraKg = mutableStateOf("")
        private set
    var umidade = mutableStateOf("")
        private set
    var impureza = mutableStateOf("")
        private set
    var frotaVeiculo = mutableStateOf<String?>(null)
        private set
    var responsavel = mutableStateOf<String?>(null)
        private set
    var fotoTicketUrl = mutableStateOf<String?>(null)
        private set
    var ocrMensagem = mutableStateOf<String?>(null)
        private set
    var uploadingFoto = mutableStateOf(false)
        private set
    var saving = mutableStateOf(false)
        private set
    var resultMessage = mutableStateOf<String?>(null)
        private set
    var savedOk = mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            frotas.value = configRepository.lookupsByCategory("frotas")
            colaboradores.value = configRepository.lookupsByCategory("colaboradores")
        }
    }

    fun setNoRomaneio(v: String) { noRomaneio.value = v }
    fun setPesoBruto(v: String) { pesoBrutoKg.value = v }
    fun setTara(v: String) { taraKg.value = v }
    fun setUmidade(v: String) { umidade.value = v }
    fun setImpureza(v: String) { impureza.value = v }
    fun setFrota(v: String) { frotaVeiculo.value = v }
    fun setResponsavel(v: String) { responsavel.value = v }

    /** Usuario cancelou a foto ou o aparelho nao tem app de Camera
     * disponivel -- a foto e so opcional, entao isso nunca bloqueia o
     * lancamento, so avisa o motivo em vez de nao acontecer nada visivel. */
    fun onPhotoCancelled() {
        ocrMensagem.value = "Nenhuma foto capturada -- você pode lançar sem foto ou tentar de novo."
    }

    /** Chamado depois que a foto ja foi tirada e salva no Uri temporario
     * (content://...fileprovider): roda o OCR local (ML Kit) pra
     * pre-preencher os campos, comprime a imagem e sobe pro Storage. Nunca
     * bloqueia o lancamento se qualquer uma dessas etapas falhar. */
    fun onPhotoTaken(context: Context, uri: Uri) {
        uploadingFoto.value = true
        ocrMensagem.value = null
        viewModelScope.launch {
            val camposLidos = runOcr(context, uri)
            if (camposLidos.isNotEmpty()) {
                camposLidos["pesoBrutoKg"]?.let { pesoBrutoKg.value = it }
                camposLidos["taraKg"]?.let { taraKg.value = it }
                camposLidos["umidade"]?.let { umidade.value = it }
                camposLidos["impureza"]?.let { impureza.value = it }
                camposLidos["noRomaneio"]?.let { if (noRomaneio.value.isBlank()) noRomaneio.value = it }
                ocrMensagem.value = "Campos pré-preenchidos automaticamente — confira antes de lançar."
            } else {
                ocrMensagem.value = "Não foi possível ler o ticket automaticamente — preencha manualmente."
            }

            val bytes = compressPhoto(context, uri)
            if (bytes != null) {
                val url = uploadRepository.uploadTicketPhoto(bytes)
                if (url != null) {
                    fotoTicketUrl.value = url
                } else if (ocrMensagem.value?.startsWith("Campos") != true) {
                    ocrMensagem.value = "Foto lida, mas não foi possível enviar agora (sem conexão) — o lançamento segue sem foto."
                }
            }
            uploadingFoto.value = false
        }
    }

    private suspend fun runOcr(context: Context, uri: Uri): Map<String, String> {
        return try {
            val image = InputImage.fromFilePath(context, uri)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val result = recognizer.process(image).await()
            RomaneioOcrParser.parse(result.text)
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /** Mesma ideia de comprimirFoto() no site (quick-romaneio-button.tsx):
     * redimensiona pro maior lado nao passar de 1600px e reexporta em JPEG
     * qualidade 0.75 -- a foto so precisa ser legivel o bastante pra
     * conferencia/OCR, nao pra imprimir em alta resolucao, e a conexao no
     * campo costuma ser fraca. */
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
            bitmap
        }
    }

    fun submit() {
        if (noRomaneio.value.isBlank() || pesoBrutoKg.value.isBlank() || taraKg.value.isBlank()) {
            resultMessage.value = "Preencha Nº Romaneio, Peso Bruto e Tara."
            return
        }
        saving.value = true
        resultMessage.value = null
        viewModelScope.launch {
            val hoje = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date())
            val fields = mutableMapOf(
                "data" to hoje,
                "noRomaneio" to noRomaneio.value,
                "pesoBrutoKg" to pesoBrutoKg.value,
                "taraKg" to taraKg.value,
            )
            if (umidade.value.isNotBlank()) fields["umidade"] = umidade.value
            if (impureza.value.isNotBlank()) fields["impureza"] = impureza.value
            frotaVeiculo.value?.let { fields["frotaVeiculo"] = it }
            responsavel.value?.let { fields["responsavel"] = it }
            fotoTicketUrl.value?.let {
                fields["fotoTicketUrl"] = it
                // Ao contrario do site (sempre "NAO_CONFIGURADO"), aqui a
                // leitura de fato rodou no aparelho -- "OK" quando algum
                // campo saiu do OCR, "SEM_LEITURA" quando so a foto ficou
                // salva pra conferencia manual (mesmo texto livre do site,
                // so que refletindo o que realmente aconteceu).
                fields["ocrStatus"] = if (ocrMensagem.value?.startsWith("Campos") == true) "OK" else "SEM_LEITURA"
            }

            when (val result = recordRepository.createRecord("romaneios", fields)) {
                is SaveResult.SavedOnline -> {
                    resultMessage.value = "Romaneio lançado."
                    savedOk.value = true
                }
                is SaveResult.SavedOffline -> {
                    resultMessage.value = "Sem conexão — romaneio salvo neste aparelho. Sincroniza sozinho quando a internet voltar."
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
        noRomaneio.value = ""
        pesoBrutoKg.value = ""
        taraKg.value = ""
        umidade.value = ""
        impureza.value = ""
        frotaVeiculo.value = null
        responsavel.value = null
        fotoTicketUrl.value = null
        ocrMensagem.value = null
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
            value = selectedLabel ?: "Selecione (opcional)",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
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
fun RomaneioQuickScreen(onBack: () -> Unit, viewModel: RomaneioQuickViewModel = viewModel()) {
    val context = LocalContext.current
    val noRomaneio by viewModel.noRomaneio
    val pesoBrutoKg by viewModel.pesoBrutoKg
    val taraKg by viewModel.taraKg
    val umidade by viewModel.umidade
    val impureza by viewModel.impureza
    val frotaVeiculo by viewModel.frotaVeiculo
    val responsavel by viewModel.responsavel
    val frotas by viewModel.frotas
    val colaboradores by viewModel.colaboradores
    val fotoTicketUrl by viewModel.fotoTicketUrl
    val ocrMensagem by viewModel.ocrMensagem
    val uploadingFoto by viewModel.uploadingFoto
    val saving by viewModel.saving
    val resultMessage by viewModel.resultMessage
    val savedOk by viewModel.savedOk

    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingPhotoUri
        if (success && uri != null) {
            viewModel.onPhotoTaken(context, uri)
        } else if (!success) {
            // Fase 3: antes ficava mudo quando o usuario cancelava a foto ou
            // o aparelho nao tinha app de Camera -- agora avisa, em vez de
            // parecer que travou.
            viewModel.onPhotoCancelled()
        }
    }

    fun launchCamera() {
        val file = File(File(context.cacheDir, "romaneio").apply { mkdirs() }, "ticket_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingPhotoUri = uri
        takePicture.launch(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Romaneio rápido") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
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
                    "Só o essencial — data e os cálculos (líquido, sacas, tonelada) saem sozinhos quando sincronizar.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(onClick = { launchCamera() }, enabled = !uploadingFoto, modifier = Modifier.fillMaxWidth()) {
                        if (uploadingFoto) {
                            CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                            Text("Lendo e enviando foto...")
                        } else {
                            Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                            Text(if (fotoTicketUrl != null) "Trocar foto do ticket" else "Tirar foto do ticket (opcional)")
                        }
                    }
                    if (ocrMensagem != null) {
                        Text(ocrMensagem ?: "", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = noRomaneio,
                    onValueChange = { viewModel.setNoRomaneio(it) },
                    label = { Text("Nº Romaneio *") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = pesoBrutoKg,
                        onValueChange = { viewModel.setPesoBruto(it) },
                        label = { Text("Peso Bruto (kg) *") },
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = taraKg,
                        onValueChange = { viewModel.setTara(it) },
                        label = { Text("Tara (kg) *") },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = umidade,
                        onValueChange = { viewModel.setUmidade(it) },
                        label = { Text("Umidade (%)") },
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = impureza,
                        onValueChange = { viewModel.setImpureza(it) },
                        label = { Text("Impureza (%)") },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item {
                LookupDropdown("Frota/Veículo (opcional)", frotaVeiculo, frotas) { viewModel.setFrota(it) }
            }
            item {
                LookupDropdown("Responsável (opcional)", responsavel, colaboradores) { viewModel.setResponsavel(it) }
            }
            if (resultMessage != null) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(resultMessage ?: "")
                            if (savedOk) {
                                Button(onClick = { viewModel.reset() }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                    Text("Lançar outro romaneio")
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
