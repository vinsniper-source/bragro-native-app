package com.bragro.mobile.ui.domain

import android.app.Application
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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

    var frotas = mutableStateOf<List<LookupEntity>>(emptyList())
        private set
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
                val valoresValidos = frotas.value.map { it.value }
                val match = parseFrotaQrPayload(texto, valoresValidos)
                if (match != null) {
                    frota = match
                    qrMensagem.value = null
                } else {
                    qrMensagem.value = "QR Code lido, mas não corresponde a nenhuma máquina/frota cadastrada."
                }
            } catch (e: Exception) {
                AppLog.e("QuickAbastecimentoDialog", "Falha ao ler QR Code do abastecimento", e)
                qrMensagem.value = "Falha ao ler o QR Code -- tente novamente ou escolha manualmente."
            } finally {
                lendoQr.value = false
            }
        }
    }

    fun onQrPhotoCancelled() {
        lendoQr.value = false
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
                    androidx.compose.material3.IconButton(onClick = { launchQrCamera() }, enabled = !lendoQr) {
                        if (lendoQr) {
                            CircularProgressIndicator(modifier = Modifier)
                        } else {
                            androidx.compose.material3.Icon(
                                Icons.Filled.QrCodeScanner,
                                contentDescription = "Ler QR Code da máquina",
                                tint = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                if (qrMensagem != null) Text(qrMensagem ?: "", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
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
    var expanded by remember { mutableStateOf(false) }
    val labelFor = options.associate { it.value to it.label }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = labelFor[value] ?: value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            colors = appFieldColors(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(text = { Text(opt.label) }, onClick = { onChange(opt.value); expanded = false })
            }
        }
    }
}
