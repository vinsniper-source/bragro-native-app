package com.bragro.mobile.ui.basededados

import androidx.compose.material.icons.outlined.Storage
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Add
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Sync
import com.bragro.mobile.ui.theme.Card
import com.bragro.mobile.ui.theme.SearchableDropdownField
import com.bragro.mobile.ui.theme.appFieldColors
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import com.bragro.mobile.ui.theme.LinhaTextField as OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import com.bragro.mobile.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.bragro.mobile.data.AppLog
import com.bragro.mobile.data.repo.BaseDeDadosRepository
import com.bragro.mobile.data.repo.LookupOptionUploadRepository
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// Categorias "visuais" que ganham upload de foto por valor (pedido do
// usuário: "anexar foto em cada valor de lista suspensa relevante") -- as
// outras ~68 categorias de Base de Dados não ganham esse campo, mesmo
// critério do site/backend (ver fotoUrl em api/mobile/base-de-dados/route.ts,
// 13ª exceção de schema). Decisão tomada aqui (ver relatório): não
// pré-popular fotos dos valores já cadastrados, só upload manual daqui pra
// frente.
private val FOTO_CATEGORIES = setOf(
    "itens_estoque", "frotas", "marcas", "racas_pecuaria", "oficinas", "locais",
    "culturas", "produtos_prescricao", "combustiveis_lubrificantes", "colaboradores", "funcoes",
    "itens_epi", "itens_escritorio", "itens_limpeza", "seguradoras", "bancos", "itens_receituario",
)
private val FOTO_PREFIXOS = listOf("itens_estoque_", "combustiveis_lubrificantes_", "tipos_patrimonio_")
private fun categoriaTemFoto(category: String): Boolean =
    category in FOTO_CATEGORIES || FOTO_PREFIXOS.any { category.startsWith(it) }

class BaseDeDadosViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = BaseDeDadosRepository(app)
    private val uploadRepo = LookupOptionUploadRepository(app)

    var data = mutableStateOf<JsonObject?>(null)
        private set
    var loading = mutableStateOf(false)
        private set
    var busy = mutableStateOf(false)
        private set
    var errorMessage = mutableStateOf<String?>(null)
        private set
    // Upload de foto por valor de lista suspensa (categorias "visuais" --
    // ver FOTO_CATEGORIES) -- estado separado de "busy" pra não desabilitar
    // o resto da tela enquanto só a foto está subindo.
    var uploadingFoto = mutableStateOf(false)
        private set

    fun load() {
        loading.value = true
        viewModelScope.launch {
            val result = repo.run("get")
            loading.value = false
            result.onSuccess { data.value = it?.jsonObject }.onFailure { errorMessage.value = it.message }
        }
    }

    private fun act(action: String, category: String? = null, value: String? = null, id: String? = null, ativo: Boolean? = null, name: String? = null, areaHa: Double? = null, cultura: String? = null, areaSafrinhaHa: Double? = null, areaSafrinhaCultura1: String? = null, areaSafrinhaCultura1Ha: Double? = null, areaSafrinhaCultura2: String? = null, areaSafrinhaCultura2Ha: Double? = null, latitude: Double? = null, longitude: Double? = null, situacaoTerra: String? = null, fotoUrl: String? = null, localCulturas: List<Double?>? = null, onDone: (Boolean) -> Unit = {}) {
        busy.value = true
        viewModelScope.launch {
            val result = repo.run(action, category, value, id, ativo, name, areaHa, cultura, areaSafrinhaHa, areaSafrinhaCultura1, areaSafrinhaCultura1Ha, areaSafrinhaCultura2, areaSafrinhaCultura2Ha, latitude, longitude, situacaoTerra, fotoUrl, localCulturas)
            busy.value = false
            result.onSuccess { load(); onDone(true) }.onFailure { errorMessage.value = it.message; onDone(false) }
        }
    }

    // fotoUrl (13ª exceção de schema, ver MEMORY.md) -- opcional, colocado
    // por ÚLTIMO pra não quebrar nenhuma chamada existente de addLookup.
    // Como add_lookup é upsert por (category, value) no backend, chamar de
    // novo pra um valor JÁ cadastrado só atualiza o fotoUrl (ver "Anexar
    // foto" em SectorCard abaixo) -- não duplica nem recria o valor.
    fun addLookup(category: String, value: String, fotoUrl: String? = null, onDone: (Boolean) -> Unit = {}) =
        act("add_lookup", category = category, value = value, fotoUrl = fotoUrl, onDone = onDone)

    /** Comprime a imagem escolhida no seletor, sobe pro Storage
     * (LookupOptionUploadRepository) e só então chama addLookup de novo pro
     * MESMO valor já cadastrado (upsert), agora com fotoUrl preenchido --
     * pedido do usuário ("anexar foto em cada valor de lista suspensa
     * relevante"). Nunca bloqueia o cadastro em si: se qualquer etapa falhar,
     * só mostra erro, o valor continua existindo sem foto. */
    fun uploadLookupFoto(context: Context, uri: Uri, category: String, value: String) {
        uploadingFoto.value = true
        errorMessage.value = null
        viewModelScope.launch {
            val bytes = compressFoto(context, uri)
            if (bytes == null) {
                uploadingFoto.value = false
                errorMessage.value = "Falha na imagem."
                return@launch
            }
            val url = uploadRepo.uploadFoto(bytes)
            if (url == null) {
                uploadingFoto.value = false
                errorMessage.value = "Sem conexão. Foto não enviada."
                return@launch
            }
            addLookup(category, value, fotoUrl = url) { ok ->
                uploadingFoto.value = false
                if (!ok) errorMessage.value = errorMessage.value ?: "Não foi possível salvar a foto."
            }
        }
    }

    /** Mesma lógica de PragaFotoScreen.kt (compressPhoto/applyExifRotation):
     * redimensiona pro maior lado não passar de 1600px e reexporta em JPEG
     * qualidade 0.75 antes de subir -- limite de 5MB (ver
     * LookupOptionUploadRepository/padrão PragaUploadRepository) nunca é
     * atingido com essa compressão. */
    private fun compressFoto(context: Context, uri: Uri): ByteArray? {
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
            AppLog.e("BaseDeDadosScreen", "Falha ao comprimir/redimensionar foto do valor de lista suspensa", e)
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
            AppLog.e("BaseDeDadosScreen", "Falha ao ler EXIF/rotacionar foto do valor de lista suspensa -- mantendo bitmap original", e)
            bitmap
        }
    }
    fun deleteLookup(id: String) = act("delete_lookup", id = id)
    fun toggleLookup(id: String, ativo: Boolean) = act("toggle_lookup", id = id, ativo = ativo)
    // v51 -- lápis: troca o nome e liga/desliga o item (opção A aprovada:
    // só a lista muda; registros já lançados mantêm o nome antigo).
    fun updateLookup(id: String, value: String, ativo: Boolean, onDone: (Boolean) -> Unit = {}) =
        act("update_lookup", id = id, value = value, ativo = ativo, onDone = onDone)
    fun importDefaults() = act("import_defaults")
    // "Recusar" -- pedido do usuário ("coloque o botão com a opção
    // recusar"), espelho de declineDefaultsAction no site: lapideia os
    // itens faltando agora, o aviso some sem importar nada.
    fun declineDefaults() = act("decline_defaults")
    // areaSafrinhaHa (2ª safra, área menor que a área total) -- exceção de
    // schema autorizada (ver MEMORY.md), opcional, ao lado da área
    // principal (pedido do usuário). Alimenta o círculo do Canvas quando a
    // safra filtrada bate "SAFRINHA ..." (ver lib/services/canvas.ts).
    // areaSafrinhaCultura1/2 + suas Ha (5ª exceção de schema, ver MEMORY.md,
    // atualizada no mega-lote pra 2 slots LIVRES escolhidos via dropdown em
    // vez de Milho/Sorgo fixos) -- área safrinha POR CULTURA, pra quando a
    // fazenda planta duas culturas na mesma safrinha, cada uma com sua parte
    // do total.
    // Cultura (safra principal/verão) que ocupa a Área Total -- 8ª exceção
    // de schema (ver MEMORY.md), opcional, escolhida via dropdown ANTES do
    // campo de Área Total (pedido do usuário).
    // Localização (6ª exceção de schema, ver MEMORY.md) -- agora também no
    // cadastro (antes só existia em updateFarm/edição). Pedido do usuário:
    // "coloque o campo latitude longitude depois da área 2".
    // Situação da terra / regime de posse (9ª exceção de schema, ver
    // MEMORY.md) -- opcional, colocado por ÚLTIMO (mesmo critério de "name"
    // em updateFarm abaixo) pra não quebrar nenhuma chamada existente.
    fun addFarm(name: String, areaHa: Double, cultura: String? = null, areaSafrinhaHa: Double? = null, areaSafrinhaCultura1: String? = null, areaSafrinhaCultura1Ha: Double? = null, areaSafrinhaCultura2: String? = null, areaSafrinhaCultura2Ha: Double? = null, latitude: Double? = null, longitude: Double? = null, situacaoTerra: String? = null, localCulturas: List<Double?>? = null) =
        act("add_farm", localCulturas = localCulturas, name = name, areaHa = areaHa, cultura = cultura, areaSafrinhaHa = areaSafrinhaHa, areaSafrinhaCultura1 = areaSafrinhaCultura1, areaSafrinhaCultura1Ha = areaSafrinhaCultura1Ha, areaSafrinhaCultura2 = areaSafrinhaCultura2, areaSafrinhaCultura2Ha = areaSafrinhaCultura2Ha, latitude = latitude, longitude = longitude, situacaoTerra = situacaoTerra)
    // Localização (6ª exceção de schema, ver MEMORY.md) -- latitude/
    // longitude sempre viajam juntas (as duas null limpa, as duas
    // preenchidas define; validação de "uma sem a outra" já é feita no
    // backend, ver update_farm em api/mobile/base-de-dados/route.ts).
    // name -- pedido do usuário ("coloque para editar também o nome da
    // fazenda"): opcional (null = não mexe no nome, mesmo padrão
    // partial-update dos demais campos), validado/normalizado no servidor
    // (ver case "update_farm" em api/mobile/base-de-dados/route.ts, que
    // também renomeia a entrada equivalente em "locais"). Colocado por
    // ÚLTIMO na lista (não logo após "id") pra não quebrar nenhuma chamada
    // existente que ainda passe "areaHa" posicionalmente como 2º argumento.
    // Situação da terra / regime de posse (9ª exceção de schema, ver
    // MEMORY.md) -- opcional, colocado por ÚLTIMO (mesmo critério de "name"
    // acima) pra não quebrar nenhuma chamada existente.
    fun updateFarm(id: String, areaHa: Double, cultura: String? = null, areaSafrinhaHa: Double? = null, areaSafrinhaCultura1: String? = null, areaSafrinhaCultura1Ha: Double? = null, areaSafrinhaCultura2: String? = null, areaSafrinhaCultura2Ha: Double? = null, latitude: Double? = null, longitude: Double? = null, name: String? = null, situacaoTerra: String? = null, localCulturas: List<Double?>? = null) =
        act("update_farm", localCulturas = localCulturas, id = id, name = name, areaHa = areaHa, cultura = cultura, areaSafrinhaHa = areaSafrinhaHa, areaSafrinhaCultura1 = areaSafrinhaCultura1, areaSafrinhaCultura1Ha = areaSafrinhaCultura1Ha, areaSafrinhaCultura2 = areaSafrinhaCultura2, areaSafrinhaCultura2Ha = areaSafrinhaCultura2Ha, latitude = latitude, longitude = longitude, situacaoTerra = situacaoTerra)
    fun deleteFarm(id: String) = act("delete_farm", id = id)
    fun syncLocais() = act("sync_locais")
}

// Réplica 100% nativa de Base de Dados (src/app/(app)/base-de-dados/) --
// mesmo pedido do usuário de tela fixa no app, sem redirecionar pro site.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaseDeDadosScreen(
    onBack: () -> Unit,
    // v326 (mockup v326) -- tela própria das Fazendas, logo após o login:
    // só o cadastro das fazendas, com a logo e a casinha (vai para o Início).
    modoFazendas: Boolean = false,
    onHome: (() -> Unit)? = null,
    // Null = esconde o botão (só OWNER/ADMIN recebem a lambda, ver BRAgroNavHost).
    onOpenCsvImport: (() -> Unit)? = null,
    viewModel: BaseDeDadosViewModel = viewModel(),
) {
    LaunchedEffect(Unit) { viewModel.load() }
    val data by viewModel.data
    val loading by viewModel.loading
    val busy by viewModel.busy
    val error by viewModel.errorMessage
    val uploadingFoto by viewModel.uploadingFoto

    // Upload de foto por valor de lista suspensa (categorias "visuais" --
    // ver FOTO_CATEGORIES) -- um ÚNICO seletor de imagem compartilhado por
    // todas as categorias/valores (abrir um launcher por item dinâmico da
    // lista não é seguro no Compose), igual padrão do "attach" de
    // pendingPhotoUri em PragaFotoScreen.kt/RomaneioQuickScreen.kt: guarda
    // qual (categoria, valor) está esperando a foto ANTES de abrir o
    // seletor, e usa esse alvo quando o resultado volta.
    val lookupFotoContext = LocalContext.current
    var pendingFotoTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    val lookupFotoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val target = pendingFotoTarget
        if (uri != null && target != null) {
            viewModel.uploadLookupFoto(lookupFotoContext, uri, target.first, target.second)
        }
    }

    // v275 -- sobem para fora do Scaffold: os ícones de importar/recusar estão no título.
    var showImportConfirm by remember { mutableStateOf(false) }
    var showDeclineConfirm by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            if (modoFazendas) {
                TopAppBar(
                    title = {
                        Column {
                            Spacer(modifier = Modifier.height(16.dp))
                            com.bragro.mobile.ui.login.MarcaBRAgro(modifier = Modifier.height(34.dp))
                        }
                    },
                    actions = {
                        Column {
                            Spacer(modifier = Modifier.height(16.dp))
                            androidx.compose.material3.IconButton(onClick = { onHome?.invoke() }) {
                                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.Home, contentDescription = "Ir para o Início", modifier = Modifier.size(22.dp))
                            }
                        }
                    },
                )
            } else
            TopAppBar(
                navigationIcon = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        com.bragro.mobile.ui.domain.BackNavButton()
                    }
                },
                // Seta+título uma linha abaixo + fonte verde -- padrão global
                // (pedido do usuário, ver comentário em SettingsScreen.kt).
                title = { androidx.compose.material3.ProvideTextStyle(androidx.compose.material3.LocalTextStyle.current.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)) {
                    // v21 -- ícone verde do módulo ao lado do título.
                    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom) {

                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        // v275 (mockup v330) -- aviso no ⓘ; importar/recusar/CSV viram ícones.
                        androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text("Base de dados", color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                            val faltamTopo = data?.get("missingCount")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                            if (faltamTopo > 0 && !modoFazendas) com.bragro.mobile.ui.theme.DicaInfo("$faltamTopo item(ns) padrão para importar.")
                        }
                    }
                
                    }
                } },
                actions = { Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        /* v275 (mockup v331) -- sem casinha na página inicial do módulo */
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            val faltamTopo = data?.get("missingCount")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                            if (faltamTopo > 0 && !modoFazendas) {
                                androidx.compose.material3.IconButton(onClick = { showImportConfirm = true }, enabled = !busy) {
                                    androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.Download, contentDescription = "Importar padrões", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                }
                                androidx.compose.material3.IconButton(onClick = { showDeclineConfirm = true }, enabled = !busy) {
                                    androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.Close, contentDescription = "Recusar", tint = com.bragro.mobile.ui.domain.corAtencao(), modifier = Modifier.size(20.dp))
                                }
                            }
                            if (onOpenCsvImport != null && !modoFazendas) {
                                androidx.compose.material3.IconButton(onClick = onOpenCsvImport, enabled = !busy) {
                                    androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.UploadFile, contentDescription = "Migrar dados de outra plataforma (CSV)", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    } },
            )
        },
    ) { padding ->
        if (loading && data == null) {
            Column(modifier = Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.Center) {
                Text("Carregando...", modifier = Modifier.padding(24.dp))
            }
            return@Scaffold
        }
        val missingCount = data?.get("missingCount")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
        val bySector = data?.get("bySector")?.jsonArray
        val farms = data?.get("farms")?.jsonArray
        // Descrição item a item do que vai ser importado/recusado -- pedido
        // do usuário ("quando subir descreva quais são arquivos que serão
        // enviados"): antes os diálogos só mostravam a CONTAGEM
        // ($missingCount item(ns) padrão), sem dizer quais categorias/
        // fazendas de fato entram na leva -- ver missingLookupItems/
        // missingFarmNames em api/mobile/base-de-dados/route.ts.
        val missingSummary = remember(data) {
            val parts = mutableListOf<String>()
            data?.get("missingLookupItems")?.jsonArray?.forEach { el ->
                val obj = el.jsonObject
                val label = obj["label"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                val values = obj["values"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
                if (values.isNotEmpty()) parts += "${com.bragro.mobile.ui.domain.exibirTexto(label, true)}: ${values.joinToString(", ") { com.bragro.mobile.ui.domain.exibirTexto(it) }}"
            }
            val farmNames = data?.get("missingFarmNames")?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
            if (farmNames.isNotEmpty()) parts += "Fazendas: ${farmNames.joinToString(", ") { com.bragro.mobile.ui.domain.exibirTexto(it, true) }}"
            parts.joinToString("\n")
        }
        // Confirmacao (Sim/Nao) antes de importar -- pedido do usuario
        // ("coloque a opcao nao tambem"), o botao antes disparava a
        // importacao direto no toque, sem chance de desistir. So traz
        // itens padrao que ainda NAO existem nesta organizacao e que o
        // usuario nunca excluiu de proposito (ver import_defaults/
        // LookupOptionExclusion em api/mobile/base-de-dados/route.ts).
        // "Recusar" -- pedido do usuário ("coloque o botão com a opção
        // recusar"): diferente do "Não" do diálogo de Importar (que só
        // fecha o diálogo, o aviso continua aparecendo depois), este marca
        // os itens faltando como decisão definitiva de não importar -- o
        // aviso some de vez, reversível cadastrando na mão depois.

        // v1.6.28 (mockup v132b, item 3) -- veio do menu do cabeçalho: rola
        // até o setor que tem a lista escolhida.
        val listaEstado = androidx.compose.foundation.lazy.rememberLazyListState()
        LaunchedEffect(bySector) {
            val alvo = BaseDeDadosFoco.categoria ?: return@LaunchedEffect
            val setores = bySector ?: return@LaunchedEffect
            val i = setores.indexOfFirst { el -> el.jsonObject["categories"]?.jsonArray?.any { c -> (c.jsonObject["category"]?.jsonPrimitive?.contentOrNull ?: c.toString().trim('"')) == alvo } == true }
            if (i >= 0) {
                val antes = (if (error != null) 1 else 0) + 1
                listaEstado.scrollToItem(antes + i)
                BaseDeDadosFoco.categoria = null
            }
        }
        LazyColumn(
            state = listaEstado,
            // Margem horizontal reduzida de 12dp pra 6dp -- pedido do
            // usuário ("expanda mais o limite da tela, diminua a margem
            // para caber tudo sem cortar palavra ou usar letreiro"): o
            // bloco de fazendas tem 4 campos por linha (Cultura/TOTAL ha/
            // Cultura 1/ha, depois Cultura 2/ha/lat,lon), cada dp a mais de
            // largura útil ajuda os rótulos a caberem sem elipse.
            contentPadding = if (modoFazendas) PaddingValues(20.dp, padding.calculateTopPadding() + 4.dp, 20.dp, 24.dp) else PaddingValues(6.dp, padding.calculateTopPadding() + 4.dp, 6.dp, 24.dp), // v277 -- margem lateral no cadastro de fazendas
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (error != null) {
                item(key = "error") { Text(error!!, color = MaterialTheme.colorScheme.error) }
            }
            // v275 -- importar/recusar/CSV foram para a linha do título.
            // v321/v326 -- Fazendas saiu da Base de Dados: só na tela Fazendas.
            if (modoFazendas) item(key = "farms_titulo") {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                    Text("Cadastre suas fazendas", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    Spacer(Modifier.width(6.dp))
                    com.bragro.mobile.ui.theme.DicaInfo("Área e localização de cada cultura alimentam o rateio, o clima e os mapas.")
                }
            }
            if (modoFazendas) item(key = "farms") {
                FazendasCadastro(farms = farms, busy = busy, viewModel = viewModel)
            }
            if (false) item(key = "farms_antigo") {
                FarmsCard(
                    abertoInicial = true,
                    farms = farms,
                    busy = busy,
                    onAdd = { name, area, cultura, areaSafrinha, cultura1, cultura1Ha, cultura2, cultura2Ha, latitude, longitude, situacaoTerra ->
                        viewModel.addFarm(name, area, cultura = cultura, areaSafrinhaHa = areaSafrinha, areaSafrinhaCultura1 = cultura1, areaSafrinhaCultura1Ha = cultura1Ha, areaSafrinhaCultura2 = cultura2, areaSafrinhaCultura2Ha = cultura2Ha, latitude = latitude, longitude = longitude, situacaoTerra = situacaoTerra)
                    },
                    onUpdate = { id, name, area, cultura, areaSafrinha, cultura1, cultura1Ha, cultura2, cultura2Ha, latitude, longitude, situacaoTerra ->
                        viewModel.updateFarm(id, area, cultura = cultura, areaSafrinhaHa = areaSafrinha, areaSafrinhaCultura1 = cultura1, areaSafrinhaCultura1Ha = cultura1Ha, areaSafrinhaCultura2 = cultura2, areaSafrinhaCultura2Ha = cultura2Ha, latitude = latitude, longitude = longitude, name = name, situacaoTerra = situacaoTerra)
                    },
                    onDelete = { id -> viewModel.deleteFarm(id) },
                    onSync = { viewModel.syncLocais() },
                )
            }
            if (!modoFazendas) bySector?.forEach { sectorEl ->
                val sector = sectorEl.jsonObject
                val sectorId = sector["sector"]?.jsonPrimitive?.contentOrNull ?: "outros"
                val label = sector["label"]?.jsonPrimitive?.contentOrNull ?: "Outros"
                val categories = sector["categories"]?.jsonArray
                item(key = "sector_$sectorId") {
                    SectorCard(
                        label = label,
                        categories = categories,
                        busy = busy,
                        farmNames = farms?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull } ?: emptyList(),
                        onAddValue = { category, value -> viewModel.addLookup(category, value) },
                        onToggle = { id, ativo -> viewModel.toggleLookup(id, ativo) },
                        onDelete = { id -> viewModel.deleteLookup(id) },
                        onEdit = { id, value, ativo, done -> viewModel.updateLookup(id, value, ativo, done) },
                        onAttachFoto = { category, value -> pendingFotoTarget = category to value; lookupFotoPicker.launch("image/*") },
                        uploadingFoto = uploadingFoto,
                    )
                }
            }
        }

        if (showImportConfirm) {
            AlertDialog(
                onDismissRequest = { showImportConfirm = false },
                // v275 (mockup v330) -- ✓/✕ na linha do título; aviso no ⓘ; minúsculas.
                title = {
                    com.bragro.mobile.ui.domain.TituloJanela("Importar padrões?", onFechar = { showImportConfirm = false }, dica = "Adiciona $missingCount item(ns). Excluídos não voltam.") {
                        com.bragro.mobile.ui.domain.SalvarIcone(onClick = { showImportConfirm = false; viewModel.importDefaults() }, descricao = "Sim, importar")
                    }
                },
                text = {
                    if (missingSummary.isNotBlank()) {
                        Text(missingSummary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.verticalScroll(rememberScrollState()))
                    }
                },
                confirmButton = {},
            )
        }
        if (showDeclineConfirm) {
            AlertDialog(
                onDismissRequest = { showDeclineConfirm = false },
                title = { Text("Recusar estes $missingCount item(ns) padrão?") },
                text = {
                    Column {
                        Text("Esses itens deixam de ser sugeridos. Nada é apagado.")
                        if (missingSummary.isNotBlank()) {
                            Text(missingSummary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showDeclineConfirm = false
                        viewModel.declineDefaults()
                    }) { Text("Sim, recusar") }
                },
                dismissButton = {
                    TextButton(onClick = { showDeclineConfirm = false }) { Text("Cancelar") }
                },
            )
        }
    }
}

@Composable
private fun CollapsibleCard(title: String, initiallyOpen: Boolean = false, content: @Composable () -> Unit) {
    var open by remember { mutableStateOf(initiallyOpen) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { open = !open },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                com.bragro.mobile.ui.domain.CantoL(open)
            }
            if (open) Column(modifier = Modifier.padding(top = 12.dp)) { content() }
        }
    }
}

// Mesma lista fixa de culturas usada no site (DEFAULT_LOOKUPS.culturas.values
// em default-lookups.ts) -- fonte dos 2 dropdowns de "cultura" dos slots de
// área safrinha (5ª exceção de schema, ver MEMORY.md, atualizada no
// mega-lote pra slots LIVRES). Duplicada aqui (não importada do site, que é
// TypeScript) só pra manter os MESMOS valores exatos -- resolveAreaTotal()
// (shared.ts, site) faz match exato (trim+uppercase) contra o que for salvo
// aqui, então essa lista não pode divergir da do site.
private val CULTURAS_SAFRINHA = listOf(
    "SOJA", "MILHO", "SORGO", "ALGODÃO", "FEIJÃO", "GIRASSOL", "TRIGO", "CAFÉ",
    "PASTAGEM", "CROTOLARIA", "MILHETO", "ARROZ", "CANA", "AVEIA", "BRAQUIÁRIA",
    "CEVADA", "AMENDOIM", "GERGELIM", "MAMONA", "CÁRTAMO", "LINHAÇA", "CENTEIO",
    "TRITICALE", "NABO FORRAGEIRO", "ERVILHACA", "TREMOÇO", "TRIGO MOURISCO",
    "CONSÓRCIO MILHO+BRAQUIÁRIA",
)

// Dropdown de cultura reutilizável pros 2 slots de área safrinha (padrão
// ExposedDropdownMenuBox já usado em ProviderIntegrationCard.kt).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CulturaDropdownField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    // Fonte menor pra caber nas linhas do bloco por fazenda -- pedido do
    // usuário ("diminua o tamanho da letra da fonte para caber em uma
    // linha"). Só usado nas linhas de fazenda já cadastrada (dense = true);
    // o formulário "Nova fazenda" mantém o tamanho normal.
    dense: Boolean = false,
) {
    SearchableDropdownField(
        value = value,
        label = label,
        options = remember { CULTURAS_SAFRINHA.map { it to it } },
        onSelect = onValueChange,
        modifier = modifier,
        emptyOptionLabel = "(nenhuma)",
        dense = dense,
    )
}

// Situação da terra / regime de posse da fazenda (9ª exceção de schema, ver
// MEMORY.md) -- mesma lista fixa (ordem alfabética) do site
// (SITUACOES_TERRA em default-lookups.ts). Duplicada aqui pelo mesmo motivo
// de CULTURAS_SAFRINHA acima: precisa bater exatamente com o valor salvo
// pelo site (trim+uppercase), não é lookup dinâmico de Base de Dados.
private val SITUACOES_TERRA = listOf("ARRENDO", "COMODATO", "OCUPAÇÃO", "PARCERIA", "PRÓPRIA")

// Dropdown de Situação da terra -- mesmo padrão ExposedDropdownMenuBox de
// CulturaDropdownField acima, lista diferente.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SituacaoTerraDropdownField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    dense: Boolean = false,
) {
    SearchableDropdownField(
        value = value,
        label = "Situação da terra",
        options = remember { SITUACOES_TERRA.map { it to it } },
        onSelect = onValueChange,
        modifier = modifier,
        emptyOptionLabel = "(nenhuma)",
        dense = dense,
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun FarmsCard(
    farms: kotlinx.serialization.json.JsonArray?,
    busy: Boolean,
    // 3º = cultura da Área Total (8ª exceção de schema, ver MEMORY.md),
    // 4º/5º = cultura1/área1, 6º/7º = cultura2/área2 (slots livres de
    // safrinha, 5ª exceção de schema), 8º/9º = latitude/longitude (6ª
    // exceção de schema) -- pedido do usuário: "coloque o campo latitude
    // longitude depois da área 2".
    // Último parâmetro = situação da terra (9ª exceção de schema, ver
    // MEMORY.md) -- null = não informado.
    onAdd: (String, Double, String?, Double?, String?, Double?, String?, Double?, Double?, Double?, String?) -> Unit,
    // 2º parâmetro = nome novo (null = não mexe -- pedido do usuário
    // "coloque para editar também o nome da fazenda"); 9º/10º parâmetros =
    // latitude/longitude (6ª exceção de schema, ver MEMORY.md) -- null/null
    // limpa, ambos preenchidos define. Último parâmetro = situação da terra
    // (9ª exceção de schema).
    onUpdate: (String, String?, Double, String?, Double?, String?, Double?, String?, Double?, Double?, Double?, String?) -> Unit,
    onDelete: (String) -> Unit,
    onSync: () -> Unit,
    abertoInicial: Boolean = false,
) {
    var newName by remember { mutableStateOf("") }
    var newArea by remember { mutableStateOf("") }
    // Cultura (safra principal/verão) que ocupa a Área Total -- 8ª exceção
    // de schema (ver MEMORY.md), opcional. Pedido do usuário: "crie um
    // campo cultura com lista suspensa antes de área total".
    var newCultura by remember { mutableStateOf("") }
    // Área "safrinha" -- exceção de schema autorizada (ver MEMORY.md),
    // opcional, ao lado da área principal (pedido do usuário: "acrescente
    // um campo ao lado da área maior"). Alimenta o círculo do Canvas quando
    // a safra filtrada bate "SAFRINHA ..." (ver lib/services/canvas.ts).
    var newAreaSafrinha by remember { mutableStateOf("") }
    // Área safrinha POR CULTURA (5ª exceção de schema, ver MEMORY.md,
    // atualizada no mega-lote pra 2 slots LIVRES escolhidos via dropdown em
    // vez de Milho/Sorgo fixos) -- pra quando a fazenda planta duas culturas
    // na mesma safrinha, cada uma com sua parte do total.
    var newCultura1 by remember { mutableStateOf("") }
    var newCultura1Ha by remember { mutableStateOf("") }
    var newCultura2 by remember { mutableStateOf("") }
    var newCultura2Ha by remember { mutableStateOf("") }
    // Localização (lat, lon) -- 6ª exceção de schema (ver MEMORY.md), mesmo
    // campo único "lat, lon" já usado na edição, agora também no cadastro.
    // Pedido do usuário: "coloque o campo latitude longitude depois da área
    // 2" -- por isso vem depois de newCultura2Ha abaixo.
    var newLocation by remember { mutableStateOf("") }
    val ctxLoc = androidx.compose.ui.platform.LocalContext.current
    // Situação da terra / regime de posse (9ª exceção de schema, ver
    // MEMORY.md) -- pedido do usuário: "logo após lat/lon o campo com
    // lista suspensa (parceria, comodato, própria, arrendo)".
    var newSituacaoTerra by remember { mutableStateOf("") }

    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    CollapsibleCard("Fazendas (${farms?.size ?: 0})", initiallyOpen = abertoInicial) { // v44: entra fechado
        farms?.forEach { el ->
            val f = el.jsonObject
            val id = f["id"]?.jsonPrimitive?.contentOrNull ?: return@forEach
            val name = f["name"]?.jsonPrimitive?.contentOrNull ?: ""
            // Nome editável -- pedido do usuário ("coloque para editar
            // também o nome da fazenda"): antes só existia como Text
            // estático (com marquee pra nomes longos); agora é um campo de
            // texto igual aos demais, editado direto no bloco da fazenda
            // (esta tela não tem um modo "editar"/"visualizar" separado
            // como o site -- todo campo já fica sempre editável aqui).
            var nameText by remember(id) { mutableStateOf(name) }
            var areaText by remember(id) { mutableStateOf((f["areaHa"]?.jsonPrimitive?.doubleOrNull ?: 0.0).toString()) }
            var culturaTotal by remember(id) { mutableStateOf(f["cultura"]?.jsonPrimitive?.contentOrNull ?: "") }
            var areaSafrinhaText by remember(id) { mutableStateOf(f["areaSafrinhaHa"]?.jsonPrimitive?.doubleOrNull?.toString() ?: "") }
            var cultura1 by remember(id) { mutableStateOf(f["areaSafrinhaCultura1"]?.jsonPrimitive?.contentOrNull ?: "") }
            var cultura1HaText by remember(id) { mutableStateOf(f["areaSafrinhaCultura1Ha"]?.jsonPrimitive?.doubleOrNull?.toString() ?: "") }
            var cultura2 by remember(id) { mutableStateOf(f["areaSafrinhaCultura2"]?.jsonPrimitive?.contentOrNull ?: "") }
            var cultura2HaText by remember(id) { mutableStateOf(f["areaSafrinhaCultura2Ha"]?.jsonPrimitive?.doubleOrNull?.toString() ?: "") }
            // Localização real (6ª exceção de schema, ver MEMORY.md) -- um
            // único campo "lat, lon" (mesmo formato que o Google Maps mostra
            // ao tocar e segurar num ponto do mapa), espelhando o site.
            // Usada por resolveFarmCoords() (lib/services/weather.ts) pra
            // trocar o fallback fixo de clima (Tupaciguara/MG) pela
            // localização real assim que cadastrada aqui ou no site.
            val fLat = f["latitude"]?.jsonPrimitive?.doubleOrNull
            val fLon = f["longitude"]?.jsonPrimitive?.doubleOrNull
            var locationText by remember(id) { mutableStateOf(if (fLat != null && fLon != null) "$fLat, $fLon" else "") }
            // Situação da terra / regime de posse (9ª exceção de schema,
            // ver MEMORY.md) -- pedido do usuário: "que apareça no bloco
            // principal esta informação".
            var situacaoTerraText by remember(id) { mutableStateOf(f["situacaoTerra"]?.jsonPrimitive?.contentOrNull ?: "") }
            // Recolher por bloco de fazenda (pedido: botão de recolher em cada bloco).
            var blocoAberto by remember(id) { mutableStateOf(false) } // v44: cada fazenda também fechada
            // Bloco individual por fazenda -- pedido do usuário ("divida
            // cada fazenda em um bloco e dentro do bloco individual separe
            // os campos, coloque todos os campos em apenas duas linhas"):
            // antes as fazendas eram só linhas soltas dentro do CollapsibleCard
            // "Fazendas (N)" (sem nenhuma separação visual entre uma e
            // outra), e os campos quebravam em quantas linhas o FlowRow
            // decidisse. Agora cada fazenda vive dentro do seu próprio
            // Surface (scrim onSurface, mesmo critério do FieldBlock em
            // ProviderIntegrationCard.kt -- sem borda, só diferença de tom,
            // respeitando a regra "sem bordas" do app) e os 6 campos +
            // ícone salvar ficam distribuídos em EXATAMENTE 2 linhas fixas
            // (não FlowRow), com fonte reduzida (dense = true / bodySmall)
            // pra cada campo caber sem cortar ou quebrar palavra.
            // surfaceVariant -> onSurface.copy(alpha=0.12f): usuário
            // reportou os cards de fazenda ainda sem separação visível
            // nenhuma mesmo após reinstalar o app do zero -- mesmo bug de
            // raiz (surfaceVariant coincidindo com o fundo por trás) já
            // corrigido nos outros arquivos com blocos individuais.
            Surface(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                shape = MaterialTheme.shapes.small,
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(6.dp)) {
                    // v1.6.14 (mockup v115, item 4) -- linha compacta igual à
                    // plataforma: nome, etiquetas (cultura, situação da terra,
                    // hectares), 📍 se tem localização, editar (✎) e remover (✕).
                    if (!blocoAberto) {
                        @Composable
                        fun etiqueta(t: String, cheia: Boolean = false) = Text(
                            com.bragro.mobile.ui.domain.exibirTexto(t), style = MaterialTheme.typography.labelSmall, maxLines = 1,
                            modifier = Modifier.padding(start = 4.dp)
                                /* v273 -- sem fundo colorido */
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.extraSmall)
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                        androidx.compose.foundation.layout.FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(com.bragro.mobile.ui.domain.exibirTexto(name, true), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.weight(1f).align(Alignment.CenterVertically).padding(end = 4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (culturaTotal.isNotBlank()) etiqueta(culturaTotal)
                                if (situacaoTerraText.isNotBlank()) etiqueta(situacaoTerraText)
                                etiqueta("${areaText.toDoubleOrNull()?.let { java.text.NumberFormat.getNumberInstance(java.util.Locale("pt", "BR")).format(it) } ?: areaText} hectares", cheia = true)
                                if (fLat != null && fLon != null) Icon(Icons.Outlined.Place, contentDescription = "Localização cadastrada", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp).size(16.dp))
                                IconButton(onClick = { blocoAberto = true }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Outlined.Edit, contentDescription = "Editar fazenda", modifier = Modifier.size(16.dp))
                                }
                                IconButton(onClick = { onDelete(id) }, enabled = !busy, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Remover fazenda", tint = com.bragro.mobile.ui.domain.corAtencao(), modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    } else
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Nome editável (era Text estático com marquee) --
                        // pedido do usuário ("coloque para editar também o
                        // nome da fazenda").
                        OutlinedTextField(
                            value = nameText,
                            onValueChange = { nameText = it },
                            textStyle = MaterialTheme.typography.bodyMedium,
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = appFieldColors(),
                        )
                        IconButton(onClick = { onDelete(id) }, enabled = !busy) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Excluir fazenda")
                        }
                        com.bragro.mobile.ui.domain.CantoL(blocoAberto, onClick = { blocoAberto = !blocoAberto })
                    }
                    if (blocoAberto) {
                    // 4 linhas de 2 campos cada (em vez de 2 linhas de 4) --
                    // achado pela análise do screenshot do usuário
                    // ("entendeu?", Cultura 1/Cultura quebrando letra por
                    // letra na vertical, e até "SOJA" saindo cortado como
                    // "SOJ"): com 4 campos dividindo a largura da tela em
                    // Row, cada coluna ficava tão estreita (~1/4 da tela)
                    // que nem um valor de 4 letras cabia, e o label "Cultura
                    // 1" (quando o campo está vazio, o Material3 mostra o
                    // label no tamanho GRANDE, não a versão pequena
                    // flutuante) não tinha largura nem pra uma letra por
                    // linha. 2 campos por linha dobra a largura disponível
                    // pra cada um -- mesmo critério que já funciona no SITE
                    // (grid-cols-2 no mobile, só grid-cols-4 a partir de
                    // telas maiores, ver base-de-dados-client.tsx).
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    ) {
                        // Cultura (safra principal/verão) que ocupa a Área
                        // Total -- 8ª exceção de schema (ver MEMORY.md).
                        CulturaDropdownField(
                            value = culturaTotal,
                            onValueChange = { culturaTotal = it },
                            label = "Cultura",
                            modifier = Modifier.weight(1f),
                            dense = true,
                        )
                        OutlinedTextField(
                            value = areaText,
                            onValueChange = { areaText = it },
                            label = { Text("TOTAL (hectares)", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            textStyle = MaterialTheme.typography.bodySmall,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = appFieldColors(),
                        )
                    }
                    // Campo genérico "safrinha" REMOVIDO da UI -- pedido do
                    // usuário (X na screenshot): só os 2 slots por cultura
                    // ficam visíveis agora. areaSafrinhaText continua
                    // existindo e sendo reenviado sem alteração em onUpdate
                    // (pré-populado do JSON acima), preservando o valor
                    // antigo no backend/fallback do Canvas pra fazendas que
                    // já tinham esse campo preenchido.
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    ) {
                        CulturaDropdownField(
                            value = cultura1,
                            onValueChange = { cultura1 = it },
                            label = "Cultura 1",
                            modifier = Modifier.weight(1f),
                            dense = true,
                        )
                        OutlinedTextField(
                            value = cultura1HaText,
                            onValueChange = { cultura1HaText = it },
                            label = { Text("Hectares", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            textStyle = MaterialTheme.typography.bodySmall,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = appFieldColors(),
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    ) {
                        CulturaDropdownField(
                            value = cultura2,
                            onValueChange = { cultura2 = it },
                            label = "Cultura 2",
                            modifier = Modifier.weight(1f),
                            dense = true,
                        )
                        OutlinedTextField(
                            value = cultura2HaText,
                            onValueChange = { cultura2HaText = it },
                            label = { Text("Hectares", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            textStyle = MaterialTheme.typography.bodySmall,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = appFieldColors(),
                        )
                    }
                    // lat/lon + Situação da terra (9ª exceção de schema, ver
                    // MEMORY.md) NA MESMA LINHA -- pedido do usuário:
                    // "coloque o campo situação da terra ao lado de
                    // lat.lon". Botão salvar continua ao final da linha.
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    ) {
                        OutlinedTextField(
                            value = locationText,
                            onValueChange = { locationText = it },
                            label = { Text("latitude, longitude", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            textStyle = MaterialTheme.typography.bodySmall,
                            singleLine = true,
                            modifier = Modifier.weight(1.4f),
                            colors = appFieldColors(),
                            // v139 -- 📍 minha localização e 🔍 município.
                            trailingIcon = { LocalizacaoBotoes(onLocal = { locationText = it }, onAviso = { android.widget.Toast.makeText(ctxLoc, it, android.widget.Toast.LENGTH_LONG).show() }) },
                        )
                        SituacaoTerraDropdownField(
                            value = situacaoTerraText,
                            onValueChange = { situacaoTerraText = it },
                            modifier = Modifier.weight(1f),
                            dense = true,
                        )
                        IconButton(
                            onClick = {
                                areaText.toDoubleOrNull()?.let { area ->
                                    // "lat, lon" num campo só -- em branco
                                    // limpa (null/null), preenchido precisa
                                    // dos dois números separados por vírgula
                                    // (mesma regra do site, ver saveFarmArea
                                    // em base-de-dados-client.tsx).
                                    val locTrim = locationText.trim()
                                    var lat: Double? = null
                                    var lon: Double? = null
                                    if (locTrim.isNotEmpty()) {
                                        val partes = locTrim.split(",").map { it.trim() }
                                        lat = partes.getOrNull(0)?.toDoubleOrNull()
                                        lon = partes.getOrNull(1)?.toDoubleOrNull()
                                        if (partes.size != 2 || lat == null || lon == null) return@let
                                    }
                                    // Slot só é enviado se cultura+área
                                    // estiverem AMBOS preenchidos (ou ambos
                                    // vazios) -- mesma regra "tudo ou nada"
                                    // do site (parseSlot em
                                    // base-de-dados-client.tsx), pra não
                                    // salvar cultura sem área nem área órfã
                                    // sem cultura.
                                    val c1 = cultura1.trim()
                                    val c1ha = cultura1HaText.toDoubleOrNull()
                                    val c2 = cultura2.trim()
                                    val c2ha = cultura2HaText.toDoubleOrNull()
                                    val culturaTotalTrim = culturaTotal.trim()
                                    // Nome -- só manda se realmente mudou
                                    // (senão fica null = "não mexe", mesmo
                                    // padrão partial-update dos demais
                                    // campos aqui) e nunca em branco (nome é
                                    // obrigatório, ver validação no servidor).
                                    val nomeTrim = nameText.trim()
                                    val novoNome = if (nomeTrim.isNotEmpty() && nomeTrim != name) nomeTrim else null
                                    val situacaoTerraTrim = situacaoTerraText.trim()
                                    onUpdate(
                                        id, novoNome, area, if (culturaTotalTrim.isNotEmpty()) culturaTotalTrim else null,
                                        areaSafrinhaText.toDoubleOrNull(),
                                        if (c1.isNotEmpty()) c1 else null, if (c1.isNotEmpty()) c1ha else null,
                                        if (c2.isNotEmpty()) c2 else null, if (c2.isNotEmpty()) c2ha else null,
                                        lat, lon,
                                        if (situacaoTerraTrim.isNotEmpty()) situacaoTerraTrim else null,
                                    )
                                }
                            },
                            enabled = !busy,
                        ) {
                            Icon(Icons.Outlined.Check, contentDescription = "Salvar área")
                        }
                    }
                    }
                }
            }
        }
        // Mesmo arranjo FIXO em 2 linhas do bloco de fazenda existente acima
        // (não mais FlowRow com larguras fixas em dp, que dependia de sobrar
        // espaço e podia cortar rótulo) -- pedido do usuário: "distribua da
        // seguinte forma: cultura, total ha, cultura 1, ha na primeira
        // linha... na segunda linha coloque cultura2, ha, lat, lon sem
        // cortar os números também". weight(1f) + dense/bodySmall (mesmo
        // padrão do bloco de edição) faz cada campo dividir a largura real
        // da tela em vez de uma largura fixa que não cabe em aparelhos
        // estreitos.
        // v221 (mockup aprovado) -- um campo por linha; a foto do documento
        // (CCIR/CAR/matrícula) fica ao lado do GPS e da busca.
        val ocrContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
        Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = newName, onValueChange = { newName = it }, label = { Text("Nome da fazenda") },
                modifier = Modifier.fillMaxWidth(), singleLine = true, colors = appFieldColors(),
            )
            CulturaDropdownField(value = newCultura, onValueChange = { newCultura = it }, label = "Cultura", modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                value = newArea, onValueChange = { newArea = it }, label = { Text("TOTAL (hectares)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth(),
                colors = appFieldColors(),
            )
            CulturaDropdownField(value = newCultura1, onValueChange = { newCultura1 = it }, label = "Cultura 1", modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                value = newCultura1Ha, onValueChange = { newCultura1Ha = it }, label = { Text("Hectares") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth(),
                colors = appFieldColors(),
            )
            CulturaDropdownField(value = newCultura2, onValueChange = { newCultura2 = it }, label = "Cultura 2", modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                value = newCultura2Ha, onValueChange = { newCultura2Ha = it }, label = { Text("Hectares") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth(),
                colors = appFieldColors(),
            )
            OutlinedTextField(
                value = newLocation, onValueChange = { newLocation = it },
                label = { Text("Latitude, longitude") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                colors = appFieldColors(),
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LocalizacaoBotoes(onLocal = { newLocation = it }, onAviso = { android.widget.Toast.makeText(ctxLoc, it, android.widget.Toast.LENGTH_LONG).show() })
                        com.bragro.mobile.ui.domain.OcrFotoButton(label = "Preencher com foto do documento", somenteIcone = true) { url ->
                            val r = com.bragro.mobile.data.repo.OcrFotoRepository(ocrContext).lerFazenda(url)
                            val f = r.fazenda
                            if (r.ok && f != null) {
                                f.name?.let { newName = it }
                                f.areaHa?.let { newArea = it.toString() }
                                if (f.latitude != null && f.longitude != null) newLocation = "${f.latitude}, ${f.longitude}"
                            }
                            r.mensagem ?: r.error
                        }
                    }
                },
            )
            SituacaoTerraDropdownField(value = newSituacaoTerra, onValueChange = { newSituacaoTerra = it }, modifier = Modifier.fillMaxWidth())
        }
        Row(modifier = Modifier.padding(top = 8.dp)) {
            Button(
                onClick = {
                    val area = newArea.toDoubleOrNull()
                    if (newName.isNotBlank() && area != null && area > 0) {
                        val culturaTrim = newCultura.trim()
                        val c1 = newCultura1.trim()
                        val c2 = newCultura2.trim()
                        // "lat, lon" num campo só -- mesma regra da edição
                        // (locationText acima): em branco limpa (null/null),
                        // preenchido precisa dos dois números separados por
                        // vírgula, senão o cadastro é ignorado em silêncio
                        // (mesmo comportamento do botão de salvar área).
                        val locTrim = newLocation.trim()
                        var lat: Double? = null
                        var lon: Double? = null
                        var locationOk = true
                        if (locTrim.isNotEmpty()) {
                            val partes = locTrim.split(",").map { it.trim() }
                            lat = partes.getOrNull(0)?.toDoubleOrNull()
                            lon = partes.getOrNull(1)?.toDoubleOrNull()
                            if (partes.size != 2 || lat == null || lon == null) locationOk = false
                        }
                        if (locationOk) {
                            val situacaoTerraTrim = newSituacaoTerra.trim()
                            onAdd(
                                newName.trim(), area, if (culturaTrim.isNotEmpty()) culturaTrim else null,
                                newAreaSafrinha.toDoubleOrNull(),
                                if (c1.isNotEmpty()) c1 else null, if (c1.isNotEmpty()) newCultura1Ha.toDoubleOrNull() else null,
                                if (c2.isNotEmpty()) c2 else null, if (c2.isNotEmpty()) newCultura2Ha.toDoubleOrNull() else null,
                                lat, lon,
                                if (situacaoTerraTrim.isNotEmpty()) situacaoTerraTrim else null,
                            )
                            newName = ""; newArea = ""; newCultura = ""; newAreaSafrinha = ""
                            newCultura1 = ""; newCultura1Ha = ""; newCultura2 = ""; newCultura2Ha = ""
                            newLocation = ""; newSituacaoTerra = ""
                        }
                    }
                },
                enabled = !busy,
            ) { Text("Adicionar") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onSync, enabled = !busy) { Text("Sincronizar locais") }
        }
        if (busy) CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SectorCard(
    label: String,
    categories: kotlinx.serialization.json.JsonArray?,
    busy: Boolean,
    // Nomes de fazenda cadastradas (Farm) -- só usado pra categoria "locais"
    // (campo "Local" de Safra/Financeiro/Frota etc.). Pedido do usuário:
    // "lista suspensa direcionada na categoria locais, dentro dela coloque
    // apenas as opções de fazendas" -- mesma correspondência exigida pelo
    // botão "Sincronizar locais" (nome digitado precisa bater com Farm.name
    // pra Área Total não zerar), só que evitando o problema na origem em vez
    // de corrigir depois.
    farmNames: List<String> = emptyList(),
    onAddValue: (String, String) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    // Upload de foto por valor (categorias "visuais" -- ver FOTO_CATEGORIES)
    // -- pedido do usuário ("anexar foto em cada valor de lista suspensa
    // relevante"). onAttachFoto abre o seletor de imagem pro (category,
    // value) desse valor já cadastrado; o upload em si (compressão +
    // Storage + addLookup de novo com fotoUrl) acontece no ViewModel.
    onAttachFoto: (String, String) -> Unit = { _, _ -> },
    uploadingFoto: Boolean = false,
    onEdit: (String, String, Boolean, (Boolean) -> Unit) -> Unit = { _, _, _, _ -> },
) {
    // v51 -- janela do lápis (nome + ativo) e confirmação da lixeira.
    var editando by remember { mutableStateOf<LookupEmEdicao?>(null) }
    var excluindo by remember { mutableStateOf<Pair<String, String>?>(null) }
    editando?.let { e ->
        var nome by remember(e.id) { mutableStateOf(e.nome) }
        var ativoEd by remember(e.id) { mutableStateOf(e.ativo) }
        AlertDialog(
            onDismissRequest = { editando = null },
            title = { com.bragro.mobile.ui.domain.TituloJanela("Editar · ${e.categoria}", onFechar = { editando = null }) { com.bragro.mobile.ui.domain.SalvarIcone(enabled = !busy && nome.isNotBlank(), onClick = { onEdit(e.id, nome.trim(), ativoEd) { ok -> if (ok) editando = null } }) } },
            text = {
                androidx.compose.foundation.layout.Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = nome, onValueChange = { nome = it }, label = { Text("Nome") }, singleLine = true, colors = appFieldColors())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = ativoEd,
                            onCheckedChange = { ativoEd = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary, checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Ativo (aparece nas listas suspensas)", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {},
        )
    }
    excluindo?.let { (idEx, nomeEx) ->
        AlertDialog(
            onDismissRequest = { excluindo = null },
            title = { Text("Excluir “$nomeEx”?") },
            text = { Text("Sai da lista. Registros com “$nomeEx” ficam.", style = MaterialTheme.typography.bodySmall) },
            confirmButton = {
                Button(
                    onClick = { onDelete(idEx); excluindo = null },
                    enabled = !busy,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = com.bragro.mobile.ui.domain.corAtencao()),
                ) { Text("Excluir") }
            },
            dismissButton = { TextButton(onClick = { excluindo = null }) { Text("Cancelar") } },
        )
    }
    CollapsibleCard(label) {
        categories?.forEach { catEl ->
            val cat = catEl.jsonObject
            val category = cat["category"]?.jsonPrimitive?.contentOrNull ?: return@forEach
            val values = cat["values"]?.jsonArray
            var newValue by remember(category) { mutableStateOf("") }
            val temFoto = categoriaTemFoto(category)

            // v1.6.4 (mockup v105, item 1) -- nome legível (vem do servidor) e aviso de lista vazia.
            Text(cat["label"]?.jsonPrimitive?.contentOrNull ?: category, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
            if (values == null || values.isEmpty()) {
                Text("Nenhum valor. Digite e toque em +", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            values?.forEach { vEl ->
                val v = vEl.jsonObject
                val id = v["id"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                val valorReal = v["value"]?.jsonPrimitive?.contentOrNull ?: ""
                val vLabel = v["label"]?.jsonPrimitive?.contentOrNull ?: valorReal
                val ativo = v["ativo"]?.jsonPrimitive?.booleanOrNull ?: true
                // fotoUrl (13ª exceção de schema, ver MEMORY.md) -- pedido do
                // usuário: não pré-popular valores já cadastrados, só exibe
                // a miniatura se ALGUÉM já anexou manualmente daqui pra
                // frente (ver uploadLookupFoto no ViewModel).
                val fotoUrl = v["fotoUrl"]?.jsonPrimitive?.contentOrNull
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    if (temFoto && !fotoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = fotoUrl,
                            contentDescription = vLabel,
                            modifier = Modifier.size(32.dp).clip(CircleShape),
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        // v273 (mockup v328) -- minúsculas como na plataforma.
                        if (ativo) com.bragro.mobile.ui.domain.exibirTexto(vLabel) else "${com.bragro.mobile.ui.domain.exibirTexto(vLabel)} (desativado)",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (ativo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (temFoto) {
                        IconButton(onClick = { onAttachFoto(category, valorReal) }, enabled = !busy && !uploadingFoto) {
                            Icon(Icons.Outlined.CameraAlt, contentDescription = "Anexar foto")
                        }
                    }
                    // v43 -- o interruptor virou o ícone de editar; ligar e
                    // desligar o item passou para dentro da edição.
                    IconButton(onClick = { editando = LookupEmEdicao(id, category, vLabel, ativo) }, enabled = !busy) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Editar", tint = MaterialTheme.colorScheme.primary)
                    }
                    if (false) Switch(checked = ativo, onCheckedChange = { onToggle(id, it) }, enabled = !busy, colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary, checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)))
                    IconButton(onClick = { excluindo = id to vLabel }, enabled = !busy) { Icon(Icons.Outlined.Delete, contentDescription = "Excluir") }
                }
            }
            if (category == "locais") {
                val jaListados = remember(values) {
                    values?.mapNotNull { it.jsonObject["label"]?.jsonPrimitive?.contentOrNull?.trim()?.uppercase() }?.toSet() ?: emptySet()
                }
                val disponiveis = farmNames.filter { it.trim().uppercase() !in jaListados }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)) {
                    SearchableDropdownField(
                        value = newValue,
                        label = if (disponiveis.isEmpty()) "Todas as fazendas já estão na lista" else "Selecione a fazenda",
                        options = remember(disponiveis) { disponiveis.map { it to it } },
                        onSelect = { newValue = it },
                        modifier = Modifier.weight(1f),
                        emptyOptionLabel = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { if (newValue.isNotBlank()) { onAddValue(category, newValue.trim()); newValue = "" } }, enabled = !busy && newValue.isNotBlank()) { Text("+") }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)) {
                    OutlinedTextField(value = newValue, onValueChange = { newValue = it }, label = { Text("Novo valor") }, modifier = Modifier.weight(1f), singleLine = true, colors = appFieldColors())
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { if (newValue.isNotBlank()) { onAddValue(category, newValue.trim()); newValue = "" } }, enabled = !busy) { Text("+") }
                }
            }
        }
    }
}

/** v51 -- item da lista suspensa aberto na janela do lápis. */
private data class LookupEmEdicao(val id: String, val categoria: String, val nome: String, val ativo: Boolean)

/** v1.6.28 -- lista escolhida no menu "Base de Dados" do cabeçalho (rola até ela). */
object BaseDeDadosFoco { var categoria: String? = null }


// ---------------------------------------------------------------------------
// v271 (mockups v323b/v325 aprovados, iguais ao site) -- cadastro das fazendas
// da tela Fazendas: lista com editar/lixeira laranja; "Nova fazenda" com ＋
// (mais um bloco) e ✓ (cadastra todos); cada cultura (principal, 1 e 2) com
// cultura, hectares e latitude/longitude, com localizar, buscar e câmera.

private class LinhaCultura(cultura: String = "", ha: String = "", local: String = "") {
    var cultura by mutableStateOf(cultura)
    var ha by mutableStateOf(ha)
    var local by mutableStateOf(local)
}

private class FormFazenda(nome: String = "", situacao: String = "", val linhas: List<LinhaCultura> = List(3) { LinhaCultura() }) {
    var nome by mutableStateOf(nome)
    var situacao by mutableStateOf(situacao)
}

private fun lerLocalTexto(t: String): Pair<Double, Double>? {
    val p = t.split(",").map { it.trim() }
    if (p.size != 2) return null
    val la = p[0].toDoubleOrNull() ?: return null
    val lo = p[1].toDoubleOrNull() ?: return null
    return la to lo
}

private fun localValido(t: String) = t.isBlank() || lerLocalTexto(t) != null

@Composable
private fun CamposFazenda(form: FormFazenda) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val ocrContext = ctx.applicationContext
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = form.nome, onValueChange = { form.nome = it }, label = { Text("Nome da fazenda") },
            modifier = Modifier.fillMaxWidth(), singleLine = true, colors = appFieldColors(),
        )
        SituacaoTerraDropdownField(value = form.situacao, onValueChange = { form.situacao = it }, modifier = Modifier.fillMaxWidth())
        listOf("Cultura principal", "Cultura 1", "Cultura 2").forEachIndexed { i, rot ->
            val l = form.linhas[i]
            Text(rot, style = MaterialTheme.typography.labelLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
            // v277 (mockup v334) -- um campo por linha; os ícones de localizar,
            // buscar e câmera sobem para a linha do nome do campo (nada cortado).
            CulturaDropdownField(value = l.cultura, onValueChange = { l.cultura = it }, label = "Cultura", modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                value = l.ha, onValueChange = { l.ha = it }, label = { Text("Hectares") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
                modifier = Modifier.fillMaxWidth(), colors = appFieldColors(),
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text("Latitude, longitude", style = MaterialTheme.typography.labelMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LocalizacaoBotoes(onLocal = { l.local = it }, onAviso = { android.widget.Toast.makeText(ctx, it, android.widget.Toast.LENGTH_LONG).show() })
                        com.bragro.mobile.ui.domain.OcrFotoButton(label = "Preencher com foto do documento", somenteIcone = true) { url ->
                            val r = com.bragro.mobile.data.repo.OcrFotoRepository(ocrContext).lerFazenda(url)
                            val f = r.fazenda
                            if (r.ok && f != null) {
                                // Na cultura principal a foto preenche nome, área e local;
                                // na Cultura 1 e 2, só a localização daquela linha.
                                if (i == 0) {
                                    f.name?.let { form.nome = it }
                                    f.areaHa?.let { l.ha = it.toString() }
                                }
                                if (f.latitude != null && f.longitude != null) l.local = "${f.latitude}, ${f.longitude}"
                            }
                            r.mensagem ?: r.error
                        }
                    }
            }
            OutlinedTextField(
                value = l.local, onValueChange = { l.local = it }, label = null,
                singleLine = true, modifier = Modifier.fillMaxWidth(), colors = appFieldColors(),
            )
        }
    }
}

@Composable
private fun FazendasCadastro(farms: kotlinx.serialization.json.JsonArray?, busy: Boolean, viewModel: BaseDeDadosViewModel) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val laranja = com.bragro.mobile.ui.domain.corAtencao()
    val br = java.util.Locale("pt", "BR")
    fun aviso(t: String) = android.widget.Toast.makeText(ctx, t, android.widget.Toast.LENGTH_LONG).show()
    var editandoId by remember { mutableStateOf<String?>(null) }
    var formEdicao by remember { mutableStateOf<FormFazenda?>(null) }
    val novas = remember { androidx.compose.runtime.mutableStateListOf(FormFazenda()) }

    fun enviar(form: FormFazenda, id: String?, nomeAntigo: String?) : Boolean {
        val principal = form.linhas[0]
        val area = principal.ha.replace(",", ".").toDoubleOrNull()
        if (form.nome.isBlank()) return false
        if (area == null || area <= 0) { aviso("Informe os hectares da cultura principal de ${form.nome}."); return false }
        if (form.linhas.any { !localValido(it.local) }) { aviso("Localização inválida. Formato: latitude, longitude (ex.: -18.58, -48.69)."); return false }
        val l0 = lerLocalTexto(principal.local)
        val l1 = lerLocalTexto(form.linhas[1].local)
        val l2 = lerLocalTexto(form.linhas[2].local)
        fun c(i: Int) = form.linhas[i].cultura.trim().ifEmpty { null }
        fun h(i: Int) = if (c(i) != null) form.linhas[i].ha.replace(",", ".").toDoubleOrNull() else null
        val loc = listOf(l1?.first, l1?.second, l2?.first, l2?.second)
        val situacao = form.situacao.trim().ifEmpty { null }
        if (id == null) {
            viewModel.addFarm(form.nome.trim(), area, cultura = c(0), areaSafrinhaCultura1 = c(1), areaSafrinhaCultura1Ha = h(1), areaSafrinhaCultura2 = c(2), areaSafrinhaCultura2Ha = h(2), latitude = l0?.first, longitude = l0?.second, situacaoTerra = situacao, localCulturas = loc)
        } else {
            val novoNome = form.nome.trim().takeIf { it != nomeAntigo }
            viewModel.updateFarm(id, area, cultura = c(0), areaSafrinhaCultura1 = c(1), areaSafrinhaCultura1Ha = h(1), areaSafrinhaCultura2 = c(2), areaSafrinhaCultura2Ha = h(2), latitude = l0?.first, longitude = l0?.second, name = novoNome, situacaoTerra = situacao, localCulturas = loc)
        }
        return true
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        androidx.compose.material3.HorizontalDivider()
        farms?.forEach { el ->
            val f = el.jsonObject
            val id = f["id"]?.jsonPrimitive?.contentOrNull ?: return@forEach
            val nome = f["name"]?.jsonPrimitive?.contentOrNull ?: ""
            fun s(k: String) = f[k]?.jsonPrimitive?.contentOrNull
            fun d(k: String) = f[k]?.jsonPrimitive?.doubleOrNull
            fun ha(v: Double?) = v?.let { java.text.NumberFormat.getNumberInstance(br).format(it) } ?: ""
            if (editandoId == id && formEdicao != null) {
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Editar fazenda", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.weight(1f))
                        IconButton(onClick = { if (enviar(formEdicao!!, id, nome)) { editandoId = null; formEdicao = null } }, enabled = !busy) {
                            Icon(Icons.Outlined.Check, contentDescription = "Salvar", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = { editandoId = null; formEdicao = null }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Cancelar", modifier = Modifier.size(20.dp))
                        }
                    }
                    CamposFazenda(formEdicao!!)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(com.bragro.mobile.ui.domain.exibirTexto(nome, true), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Text(
                            com.bragro.mobile.ui.domain.exibirTexto(listOfNotNull(
                                s("situacaoTerra"),
                                "${s("cultura")?.let { "$it " } ?: ""}${ha(d("areaHa"))} ha",
                                s("areaSafrinhaCultura1")?.let { "$it ${ha(d("areaSafrinhaCultura1Ha"))} ha" },
                                s("areaSafrinhaCultura2")?.let { "$it ${ha(d("areaSafrinhaCultura2Ha"))} ha" },
                            ).joinToString(" · ")),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (d("latitude") != null) Icon(Icons.Outlined.Place, contentDescription = "Localização cadastrada", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    IconButton(onClick = {
                        fun loc(a: String, b: String) = if (d(a) != null && d(b) != null) "${d(a)}, ${d(b)}" else ""
                        formEdicao = FormFazenda(nome, s("situacaoTerra") ?: "", listOf(
                            LinhaCultura(s("cultura") ?: "", d("areaHa")?.toString() ?: "", loc("latitude", "longitude")),
                            LinhaCultura(s("areaSafrinhaCultura1") ?: "", d("areaSafrinhaCultura1Ha")?.toString() ?: "", loc("latitudeCultura1", "longitudeCultura1")),
                            LinhaCultura(s("areaSafrinhaCultura2") ?: "", d("areaSafrinhaCultura2Ha")?.toString() ?: "", loc("latitudeCultura2", "longitudeCultura2")),
                        ))
                        editandoId = id
                    }) { Icon(Icons.Outlined.Edit, contentDescription = "Editar", modifier = Modifier.size(20.dp)) }
                    IconButton(onClick = { viewModel.deleteFarm(id) }, enabled = !busy) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Excluir", tint = laranja, modifier = Modifier.size(20.dp))
                    }
                }
            }
            androidx.compose.material3.HorizontalDivider()
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
            Text("Nova fazenda", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = { novas.add(FormFazenda()) }) {
                Icon(Icons.Outlined.Add, contentDescription = "Adicionar outra fazenda", modifier = Modifier.size(20.dp))
            }
            IconButton(
                onClick = {
                    val preenchidas = novas.filter { it.nome.isNotBlank() }
                    if (preenchidas.isNotEmpty() && preenchidas.all { enviar(it, null, null) }) {
                        novas.clear(); novas.add(FormFazenda())
                    }
                },
                enabled = !busy,
            ) {
                if (busy) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(Icons.Outlined.Check, contentDescription = "Cadastrar fazenda", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
        }
        novas.forEachIndexed { i, form ->
            if (i > 0) {
                androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Nova fazenda ${i + 1}", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { novas.removeAt(i) }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Excluir este bloco", tint = laranja, modifier = Modifier.size(20.dp))
                    }
                }
            }
            CamposFazenda(form)
        }
    }
}
