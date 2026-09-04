package com.bragro.mobile.ui.home

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.bragro.mobile.data.model.CanvasFazendaCardData
import com.bragro.mobile.ui.theme.Card
import com.bragro.mobile.ui.theme.BrBlue
import com.bragro.mobile.ui.theme.BrGreen
import com.bragro.mobile.ui.theme.BrYellow
import java.text.NumberFormat
import java.util.Locale

// Réplica mobile do "Canvas da fazenda" (ver components/canvas/canvas-view.tsx
// no site) -- pedido do usuário ("implemente nessa sequência no app
// nativo... a mesma sequência da plataforma"): cada fazenda é um círculo
// (tamanho pela área, cor pelo status de desvio custo planejado x
// realizado); tocar numa mostra o "fluxo" de custo por categoria como barra
// empilhada, igual ao site.

// Virou @Composable (não mais função pura) -- pedido do usuário ("coloque
// as cores das fontes preto/branco modo claro/escuro"): "ok" usava BrGreen
// cru como cor de TEXTO (nome da fazenda dentro do círculo), que fica com
// contraste muito baixo no modo Escuro (verde escuro sobre fundo quase-
// preto). MaterialTheme.colorScheme.primary já resolve certo pros dois
// temas (ver Theme.kt).
@Composable
private fun statusColor(status: String): Color = when (status) {
    "ok" -> MaterialTheme.colorScheme.primary
    "alerta" -> BrYellow
    "risco" -> Color(0xFFD32F2F)
    else -> Color(0xFF9E9E9E) // "semdado"
}

private fun statusLabel(status: String): String = when (status) {
    "ok" -> "Dentro do planejado"
    "alerta" -> "Atenção: custo se afastando do planejado"
    "risco" -> "Fora do planejado"
    else -> "Sem lançamento de safra na janela"
}

private val ESTAGIO_LABEL: Map<String, String> = mapOf(
    "plantio" to "Plantio",
    "vegetativo" to "Vegetativo",
    "colheita" to "Colheita",
    "indefinido" to "Sem operação de safra na janela",
)

private val JANELAS_CANVAS = listOf(30, 60, 90, 180)

/** Estágio da safra + seletor de janela (30/60/90/180d) + sugestão
 * adaptativa, unidos num card só -- pedido do usuário ("junte estagio e
 * sugestao num bloco só, sem repetir a fase"), mesmo ajuste feito no site
 * (ver dashboard/page.tsx). Antes eram 2 blocos separados e a sugestão
 * repetia o nome da fase que já aparecia no rótulo de estágio (ex.: rótulo
 * "Vegetativo" + sugestão "Fase vegetativa: bom momento..."). Cada metade
 * ainda liga/desliga pelo próprio toggle de permissão (mostrarEstagio/
 * mostrarSugestao) -- só o Card em volta é compartilhado quando os dois
 * estão ligados. "Ver por operação" (link pro módulo de Operações
 * agrupadas) não tem equivalente no app ainda -- omitido em vez de virar
 * um link quebrado. */
@Composable
fun EstagioSugestaoCard(
    estagio: String,
    mostrarEstagio: Boolean,
    janelaAtual: Int,
    onJanelaChange: (Int) -> Unit,
    mostrarSugestao: Boolean,
    onOpenDomain: (String) -> Unit,
) {
    val sugestao = if (mostrarSugestao) ESTAGIO_SUGESTAO[estagio] else null
    if (!mostrarEstagio && sugestao == null) return
    Card(modifier = Modifier.fillMaxWidth(), border = BorderStroke(0.dp, Color.Transparent)) {
        Column {
            if (mostrarEstagio) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Estágio da safra na janela: ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(ESTAGIO_LABEL[estagio] ?: estagio, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        JANELAS_CANVAS.forEach { dias ->
                            val ativo = dias == janelaAtual
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (ativo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { onJanelaChange(dias) }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                            ) {
                                Text(
                                    "${dias}d",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = if (ativo) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            if (sugestao != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .let { m -> if (sugestao.domainId != null) m.clickable { onOpenDomain(sugestao.domainId) } else m }
                        .padding(horizontal = 12.dp, vertical = if (mostrarEstagio) 8.dp else 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(sugestao.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(sugestao.texto, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    if (sugestao.domainId != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(sugestao.label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun tendenciaSeta(t: String?): String? = when (t) {
    "melhorando" -> "↓"
    "piorando" -> "↑"
    "estavel" -> null // seta só aparece pra desvios não-estáveis, igual ao site
    else -> null
}

private val BREAKDOWN_COLORS = listOf(BrGreen, BrBlue, BrYellow, Color(0xFF9E9E9E))

private val moneyFmt = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
private fun formatMoney(v: Double): String = moneyFmt.format(v)

// Texto do rótulo abaixo da área do círculo quando areaFiltroHa != null --
// espelho exato de labelFiltro() em canvas-view.tsx (site), mesmo pedido do
// usuário: primeiro anotou "CULTURA" na screenshot do Dashboard ao lado do
// texto "da safra selecionada" (fix: refletir qual filtro está ativo), e
// depois pediu explicitamente "onde está escrito dentro do círculo 'da
// cultura selecionada' troque pelo nome da cultura, no caso sorgo" -- agora
// mostra o valor de fato selecionado (ex.: "Sorgo"), não uma frase genérica.
private fun labelFiltro(safra: String?, cultura: String?): String = when {
    !safra.isNullOrBlank() && !cultura.isNullOrBlank() -> "$safra · $cultura"
    !safra.isNullOrBlank() -> safra
    !cultura.isNullOrBlank() -> cultura
    else -> ""
}

/** Fileira de círculos (um por fazenda), rolável horizontalmente -- o site
 * usa flex-wrap centralizado; num celular estreito, rolagem horizontal cabe
 * melhor que quebrar linha (círculos ficariam pequenos demais). Tocar num
 * círculo seleciona a fazenda (mesmo clique do site, abre o card de detalhe
 * logo abaixo). */
@Composable
fun CanvasCirclesRow(
    fazendas: List<CanvasFazendaCardData>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    safra: String? = null,
    cultura: String? = null,
) {
    val filtroLabel = labelFiltro(safra, cultura)
    val context = LocalContext.current
    fun openUrl(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
    // Forma/cor do bloco -- pedido do usuario ("bloco 3 [cantos assimetricos]
    // com estilo do 4 [cor cheia], a cor tem que seguir o modo
    // escuro/claro"): colorScheme.primary/onPrimary ja tem par
    // light/dark definido em Theme.kt (mesmo verde da marca usado em botoes
    // no resto do app), entao o bloco muda de tom sozinho com o tema, sem
    // hex fixo. Cantos assimetricos (36/8/36/8) quebram a grade retangular
    // do resto da Início de proposito (pedido: "nao comunicar visualmente
    // com o restante da pagina").
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 8.dp, bottomEnd = 36.dp, bottomStart = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
        border = BorderStroke(0.dp, Color.Transparent),
    ) {
        Column {
        if (fazendas.isEmpty()) {
            Text(
                "Nenhuma fazenda ativa cadastrada ainda.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                modifier = Modifier.padding(16.dp),
            )
        } else {
        // Centralizado quando sobra só 1 fazenda (org com uma única fazenda
        // OU filtro global de fazenda selecionado, ver FarmSelectorButton --
        // o backend já filtra canvasData.fazendas pra conter só ela) --
        // pedido do usuário ("em início quando selecionar uma fazenda
        // centralize-a dentro do bloco"): antes o círculo único ficava
        // dentro do MESMO Row com .horizontalScroll() usado pra lista de
        // várias fazendas -- sob scroll horizontal o Row mede os filhos com
        // largura infinita (é assim que o conteúdo pode ficar maior que a
        // tela pra rolar), então Arrangement.Center não tem "espaço sobrando"
        // nenhum pra distribuir e o círculo ficava colado na borda esquerda
        // mesmo tentando centralizar. Sem scroll nenhum (não precisa, é 1
        // círculo só) o Row respeita fillMaxWidth() de verdade e o Center
        // passa a centralizar igual esperado. Com 2+ fazendas continua igual
        // (Start, scroll horizontal, spacedBy).
        val unicaFazenda = fazendas.size == 1
        // Layout de fazenda única: texto (nome/área/safra/cultura/status) à
        // esquerda + círculo à direita -- espelho exato do bloco "fazenda
        // única" do site (canvas-view.tsx, linhas ~235-277). Até aqui o
        // native só desenhava o círculo (sem esse painel de texto do lado),
        // então faltavam culturaAtual/STATUS_LABEL como TEXTO (só a cor da
        // borda do círculo indicava o status) -- pedido do usuário: "o
        // primeiro bloco do native não tem a mesma configuração da
        // plataforma, insira também nas informações a safra".
        if (unicaFazenda) {
            val f = fazendas[0]
            val areaExibida = f.areaFiltroHa ?: f.areaHa
            val filtroAtivo = f.areaFiltroHa != null
            val temMapa = f.latitude != null && f.longitude != null
            val sizeDp = (56 + areaExibida * 0.06).coerceIn(90.0, 130.0).dp
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(f.nome, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${NumberFormat.getNumberInstance(Locale("pt", "BR")).format(areaExibida)} ha",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                    )
                    if (filtroAtivo) {
                        Text(filtroLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f))
                    }
                    Text(
                        f.culturaAtual ?: "Sem cultura registrada na janela",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                    )
                    // Safra atual (ex.: "24/25", "SAFRINHA 26") -- pedido do
                    // usuário, novo campo (ver Models.kt/canvas.ts). Só
                    // aparece quando há lançamento de Safra na janela.
                    if (!f.safraAtual.isNullOrBlank()) {
                        Text(
                            "Safra ${f.safraAtual}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(width = 1.dp, color = statusColor(f.status), shape = RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            statusLabel(f.status),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            fontWeight = FontWeight.Medium,
                            color = statusColor(f.status),
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(sizeDp)
                        .clip(CircleShape)
                        .then(if (!temMapa) Modifier.background(MaterialTheme.colorScheme.surface) else Modifier)
                        .border(width = 1.5.dp, color = statusColor(f.status), shape = CircleShape)
                        .clickable { openUrl(googleEarthUrl(f.latitude, f.longitude, f.nome)) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (temMapa) {
                        AsyncImage(
                            model = esriSatelliteUrl(f.latitude!!, f.longitude!!),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize(),
                        )
                        Box(modifier = Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.38f)))
                    } else {
                        Text(
                            "Google Earth",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = statusColor(f.status),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 6.dp),
                        )
                    }
                }
            }
        } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            fazendas.forEach { f ->
                val unica = false
                // areaFiltroHa (área de fato coberta pelos lançamentos da
                // safra/cultura filtrada, ex.: safrinha ocupa menos área
                // que a safra verão na mesma fazenda) tem prioridade sobre
                // a área cadastral (areaHa) -- espelho exato de
                // canvas-view.tsx (site), mesmo pedido do usuário
                // ("gostaria que o círculo tivesse áreas diferentes da
                // mesma fazenda dependendo a seleção dos filtros"). Sem
                // filtro de safra/cultura, ou sem lançamento com hectare
                // preenchido ainda, cai de volta pra areaHa.
                val areaExibida = f.areaFiltroHa ?: f.areaHa
                val filtroAtivo = f.areaFiltroHa != null
                val sizeDp = if (unica) {
                    (56 + areaExibida * 0.06).coerceIn(90.0, 130.0).dp
                } else {
                    (42 + areaExibida * 0.05).coerceIn(56.0, 84.0).dp
                }
                val selecionada = f.id == selectedId
                val seta = tendenciaSeta(f.tendencia)
                // Foto de satélite de fundo -- só no círculo único (fazenda
                // selecionada/única), igual ao mockup do usuário: em vez do
                // preenchimento de cor lisa, mostra a imagem aérea real da
                // fazenda (Esri World Imagery, endpoint público SEM chave de
                // API -- mesmo critério do mapa do FieldView, que usa
                // osmdroid em vez de Google Maps/Mapbox de propósito, por
                // exigirem cadastro/cobrança, ver comentário em
                // build.gradle.kts). Precisa de latitude/longitude
                // cadastrada (Base de Dados, 6ª exceção de schema); sem
                // isso cai de volta pro preenchimento de cor de sempre.
                val temMapa = unica && f.latitude != null && f.longitude != null
                Box(contentAlignment = Alignment.TopEnd) {
                    Box(
                        modifier = Modifier
                            .size(sizeDp)
                            .clip(CircleShape)
                            // bg-surface fixo (nao mais o status tintado) --
                            // pedido do usuario ("bloco com estilo do 4 [cor
                            // cheia]"): um circulo "ok" com fundo verde
                            // translucido ficava quase invisivel em cima do
                            // Card verde cheio (ver Card acima). Fundo neutro
                            // opaco + borda/texto com a cor do status mantem
                            // a leitura de sempre, legivel em cima de
                            // qualquer cor.
                            .then(if (!temMapa) Modifier.background(MaterialTheme.colorScheme.surface) else Modifier)
                            .border(
                                width = if (selecionada) 2.5.dp else 1.5.dp,
                                color = statusColor(f.status),
                                shape = CircleShape,
                            )
                            .clickable {
                                // Círculo único: o toque não "seleciona" mais
                                // nada (já é a única fazenda) -- abre o
                                // Google Earth naquele ponto, no lugar do
                                // antigo botão "Importar KML" -- pedido do
                                // usuário ("retire... a palavra importar kml,
                                // sendo que ao clicar no círculo será
                                // direcionado para o google earth para kml").
                                if (unica) {
                                    openUrl(googleEarthUrl(f.latitude, f.longitude, f.nome))
                                } else {
                                    onSelect(f.id)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (temMapa) {
                            AsyncImage(
                                model = esriSatelliteUrl(f.latitude!!, f.longitude!!),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.matchParentSize(),
                            )
                            // Escurece a foto por baixo do texto -- mesmo
                            // critério do mockup (nome/ha em branco sobre a
                            // imagem, precisa de contraste garantido).
                            Box(modifier = Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.38f)))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                f.nome,
                                style = if (unica) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (temMapa) Color.White else statusColor(f.status),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 6.dp),
                            )
                            Text(
                                "${NumberFormat.getNumberInstance(Locale("pt", "BR")).format(areaExibida)} ha",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (temMapa) Color.White.copy(alpha = 0.9f) else statusColor(f.status).copy(alpha = 0.8f),
                            )
                            if (filtroAtivo) {
                                Text(
                                    filtroLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                    color = if (temMapa) Color.White.copy(alpha = 0.75f) else statusColor(f.status).copy(alpha = 0.6f),
                                )
                            }
                        }
                    }
                    if (seta != null) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                // Sem borda -- pedido do usuário ("tire todas
                                // as bordas de todo app"); já tinha fundo
                                // (surface), então continua visível igual.
                                .background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                seta,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (f.tendencia == "melhorando") MaterialTheme.colorScheme.primary else Color(0xFFD32F2F),
                            )
                        }
                    }
                }
            }
        }
        }
        // Bloco "Importar KML" removido daqui -- pedido do usuário ("retire
        // do lado inferior direito a palavra importar kml, sendo que ao
        // clicar no círculo será direcionado para o google earth"): a ação
        // agora mora no próprio clique do círculo único (ver
        // Modifier.clickable acima), que abre o Google Earth diretamente.
        }
    }
}
}

// Deep link do Google Earth Web pro ponto da fazenda -- mesmo padrão já
// usado em FieldviewScreen.kt (câmera a ~1000m de altitude, olhando reto
// pra baixo). Sem lat/lon cadastrada (fazenda ainda sem localização em
// Base de Dados), cai pra busca por texto do nome da fazenda.
private fun googleEarthUrl(lat: Double?, lon: Double?, fallbackQuery: String): String =
    if (lat != null && lon != null) "https://earth.google.com/web/@$lat,$lon,1000a,1000d,35y,0h,0t,0r"
    else "https://earth.google.com/web/search/${Uri.encode(fallbackQuery)}"

// Foto de satélite estática via Esri World Imagery -- endpoint público
// "export" da ArcGIS Online, SEM chave de API (ao contrário de Google
// Static Maps/Mapbox, descartados de propósito no projeto todo por
// exigirem cadastro/cobrança -- ver comentário do osmdroid em
// build.gradle.kts). delta=0.006 graus (~650m) dá um recorte que mostra a
// fazenda com contexto ao redor, sem zoom excessivo nem de menos.
private fun esriSatelliteUrl(lat: Double, lon: Double, delta: Double = 0.006): String {
    val (xmin, ymin, xmax, ymax) = listOf(lon - delta, lat - delta, lon + delta, lat + delta)
    return "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/export" +
        "?bbox=$xmin,$ymin,$xmax,$ymax&bboxSR=4326&size=300,300&format=png&f=image"
}

/** Card de detalhe da fazenda selecionada -- Custo médio/ha + barra
 * empilhada de categorias (top 3 + "Outros", ver lib/services/canvas.ts no
 * site pro motivo do agrupamento). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CanvasDetailCard(fazenda: CanvasFazendaCardData) {
    Card(modifier = Modifier.fillMaxWidth(), border = BorderStroke(0.dp, Color.Transparent)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(fazenda.nome, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        fazenda.culturaAtual ?: "Sem cultura registrada na janela",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    val seta = tendenciaSeta(fazenda.tendencia)
                    if (fazenda.tendencia != null) {
                        Text(
                            "${seta ?: "→"} ${
                                when (fazenda.tendencia) {
                                    "melhorando" -> "Convergindo com o planejado (vs. período anterior)"
                                    "piorando" -> "Se afastando do planejado (vs. período anterior)"
                                    else -> "Estável em relação ao período anterior"
                                }
                            }",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = when (fazenda.tendencia) {
                                "melhorando" -> MaterialTheme.colorScheme.primary
                                "piorando" -> Color(0xFFD32F2F)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Custo médio/ha", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        fazenda.custoHaMedio?.let { formatMoney(it) } ?: "—",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            if (fazenda.breakdown.isNotEmpty()) {
                val total = fazenda.breakdown.sumOf { it.valor }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(50)),
                ) {
                    fazenda.breakdown.forEachIndexed { i, b ->
                        val pct = if (total > 0) (b.valor / total).toFloat() else 0f
                        if (pct > 0f) {
                            Box(
                                modifier = Modifier
                                    .weight(pct)
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .background(BREAKDOWN_COLORS[i % BREAKDOWN_COLORS.size]),
                            )
                        }
                    }
                }
                // FlowRow (pedido do usuario: "coloque as legendas na
                // horizontal ate o final do bloco e depois se necessario na
                // linha debaixo") -- antes era uma Column empilhada, uma
                // categoria por linha. Agora ficam lado a lado ocupando a
                // largura do card, so quebrando pra proxima linha quando nao
                // cabe mais (mesmo padrao ja usado em ModuleIconRow.kt).
                // SpaceBetween espalha os itens ate a borda direita do card
                // em vez de deixar tudo grudado a esquerda -- mesmo raciocinio
                // do "justify-between" aplicado no site (canvas-view.tsx).
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    fazenda.breakdown.forEachIndexed { i, b ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(BREAKDOWN_COLORS[i % BREAKDOWN_COLORS.size]),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(b.categoria, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                Text(
                    "Sem lançamentos financeiros dessa fazenda na janela selecionada.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

private data class Sugestao(val texto: String, val domainId: String?, val label: String, val icon: ImageVector)

// Texto sem repetir o nome da fase -- pedido do usuário ("junte estagio e
// sugestao num bloco só, sem repetir a fase"), mesmo critério do site (ver
// ESTAGIO_SUGESTAO em dashboard/page.tsx). "indefinido" saiu do mapa -- sem
// operação de safra lançada, a dica seria só mais uma repetição do próprio
// rótulo de estágio (ver EstagioSugestaoCard acima), sem nenhuma ação nova.
private val ESTAGIO_SUGESTAO: Map<String, Sugestao> = mapOf(
    "plantio" to Sugestao("Registre cada operação (data, hectare, insumo) conforme for plantando.", "safra", "Ir para Safra", Icons.Filled.Grass),
    "vegetativo" to Sugestao("Bom momento pra acompanhar pragas e manter os receituários em dia.", "pragas", "Ir para Pragas", Icons.Filled.BugReport),
    "colheita" to Sugestao("Registre romaneios e a produtividade realizada.", "colheita", "Ir para Colheita", Icons.Filled.Agriculture),
)
