package com.bragro.mobile.ui.print

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import java.util.Locale
import android.graphics.pdf.PdfDocument
import android.print.PrintAttributes
import android.print.PrintManager
import android.util.Base64
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import com.bragro.mobile.data.model.ColumnConfig
import com.bragro.mobile.data.model.DomainConfig
import com.bragro.mobile.ui.domain.displayValueFor
import com.bragro.mobile.ui.domain.formatChartValue
import com.bragro.mobile.ui.domain.formatMoneyValue
import java.io.File
import java.io.FileOutputStream

// Fase 2 do app nativo (Task #41): "Impressao" -- no site, o unico mecanismo
// de impressao que existe hoje (ver components/domain/data-table.tsx,
// exportPdf()) e 100% client-side: monta uma tabela HTML pura e chama
// window.print() do navegador (sem nenhuma geracao de PDF no servidor, sem
// biblioteca de PDF em nenhum lugar do projeto -- confirmado por busca em
// package.json e no codigo). O app nativo reproduz o MESMO principio (HTML
// gerado localmente + dialogo de impressao nativo do sistema), so que via
// as APIs proprias do Android (PrintManager/WebView.createPrintDocumentAdapter)
// em vez de window.print() do navegador -- e usando os registros que a
// tela de lista JA tem (RecordRepository, cache Room), sem precisar de
// nenhuma rota nova em /api/mobile. Isso cobre o caso pratico mais comum:
// imprimir/exportar em PDF a lista de qualquer um dos 16 modulos, exatamente
// como o botao "Exportar PDF" faz no site.
object HtmlPrinter {
    // Guarda a referencia enquanto a pagina carrega (WebViewClient.onPageFinished
    // e assincrono) -- sem isso o WebView pode ser coletado pelo garbage
    // collector antes do callback disparar, numa tela mais lenta.
    private var activeWebView: WebView? = null

    // "visibleKeys" (opcional) restringe às colunas escolhidas no botão
    // "Colunas" (ver ColumnsAndExport.kt) -- null mantém o comportamento
    // antigo (todas as colunas não ocultas), mesmo critério do site (o botão
    // Colunas também afeta o PDF/CSV exportado, não só a tela).
    fun printList(context: Context, domain: DomainConfig, records: List<Map<String, String?>>, visibleKeys: Set<String>? = null) {
        val cols = domain.columns.filter { !it.hideInTable && (visibleKeys == null || visibleKeys.contains(it.key)) }
        val html = buildListHtml(domain.label, cols, records)
        print(context, jobName = domain.label, html = html)
    }

    // Ícone Imprimir de telas que NÃO usam DomainConfig (Prescrição /
    // Ajustar Físico x Fiscal -- dados vêm de repositórios próprios,
    // não do sistema genérico de módulo/coluna). Reaproveita o mesmo HTML de
    // tabela simples (título + data + linhas) e o mesmo diálogo nativo de
    // impressão (print() abaixo), só que a partir de headers/linhas já
    // formatados como texto pelo chamador, em vez de ColumnConfig/records.
    fun printSimpleTable(context: Context, title: String, headers: List<String>, rows: List<List<String>>) {
        val header = headers.joinToString("") { "<th>${escapeHtml(it)}</th>" }
        val (font, padding) = printSizing(headers.size)
        val body = rows.joinToString("") { row -> "<tr>${row.joinToString("") { "<td>${escapeHtml(it)}</td>" }}</tr>" }
        val geradoEm = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale("pt", "BR")).format(java.util.Date())
        val html = """
            <!DOCTYPE html><html lang="pt-BR"><head><meta charset="utf-8"><title>${escapeHtml(title)}</title>
            <style>
              @page { size: landscape; margin: 12mm; }
              body { font-family: Arial, Helvetica, sans-serif; padding: 24px; color: #111; }
              h1 { font-size: 18px; margin-bottom: 2px; }
              p { font-size: 11px; color: #666; margin-top: 0; margin-bottom: 16px; }
              table { border-collapse: collapse; width: 100%; font-size: ${font}px; }
              th, td { border: 1px solid #ccc; padding: $padding; text-align: left; }
            </style></head>
            <body>
              <h1>${escapeHtml(title)}</h1>
              <p>Gerado em $geradoEm -- ${rows.size} registro(s)</p>
              <table><thead><tr>$header</tr></thead><tbody>$body</tbody></table>
            </body></html>
        """.trimIndent()
        print(context, jobName = title, html = html)
    }

    // Ícone Imprimir/PDF de telas de RELATÓRIO multi-seção (ex.: Dossiê
    // Bancário -- Task #599/#615/#711) que não cabem nem em printList (uma
    // tabela guiada por DomainConfig) nem em printSimpleTable (uma tabela
    // simples só): a tela monta o próprio HTML completo (várias seções,
    // resumos, tabelas) a partir dos dados já carregados e reaproveita o
    // MESMO motor de impressão (WebView + PrintManager) abaixo, em vez de
    // duplicar esse mecanismo. Retrato por padrão -- é um documento pra
    // anexar num pedido de crédito, não uma tabela larga.
    /** v19 -- botão "PDF" de relatórios HTML: abre a mesma tela de impressão
     * do Android já orientando a salvar como PDF. */
    fun printHtmlComoPdf(context: Context, jobName: String, html: String, landscape: Boolean = false) {
        android.widget.Toast.makeText(context, "Na próxima tela, escolha \"Salvar como PDF\".", android.widget.Toast.LENGTH_LONG).show()
        printHtml(context, jobName, html, landscape)
    }

    fun printHtml(context: Context, jobName: String, html: String, landscape: Boolean = false) {
        print(context, jobName = jobName, html = html, landscape = landscape)
    }

    private fun print(context: Context, jobName: String, html: String, landscape: Boolean = true) {
        val webView = WebView(context)
        activeWebView = webView
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                if (printManager != null) {
                    val adapter = view.createPrintDocumentAdapter(jobName)
                    // Paisagem explícita -- bug real reportado pelo usuário
                    // ("está configurado para retrato, acho que fica melhor em
                    // paisagem"): sem isso o PrintManager assume retrato por
                    // padrão, mesmo com "@page { size: landscape }" no HTML
                    // (o atributo do job de impressão manda mais que o CSS em
                    // vários apps/drivers). O site já força paisagem global
                    // (globals.css "@page { size: landscape }"), esta é a
                    // mesma decisão espelhada no app -- exceto pros QR Codes
                    // de Frota (printQrCodes), que pedem retrato explícito
                    // (landscape = false) por ter uma grade normal, não uma
                    // tabela larga.
                    val mediaSize = if (landscape) PrintAttributes.MediaSize.ISO_A4.asLandscape() else PrintAttributes.MediaSize.ISO_A4.asPortrait()
                    val attrs = PrintAttributes.Builder()
                        .setMediaSize(mediaSize)
                        .build()
                    printManager.print(jobName, adapter, attrs)
                }
                activeWebView = null
            }
        }
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    // Ícone "PDF" -- pedido do usuário ("faça que no ícone pdf já seja
    // direcionado pro Adobe ou similares"): abre direto no leitor instalado,
    // sem passar pelo diálogo de impressão do sistema (que o ícone
    // "Imprimir" continua usando, ver printList acima).
    //
    // TENTATIVA ANTERIOR (revertida): dirigir manualmente o
    // PrintDocumentAdapter do próprio WebView (mesmo usado no printList) pra
    // escrever direto num arquivo em vez de mandar pro PrintManager. NÃO
    // COMPILA -- LayoutResultCallback/WriteResultCallback têm construtor
    // package-private no SDK do Android (só o sistema pode instanciá-los,
    // confirmado pelo erro "Cannot access '<init>': it is package-private").
    // Ou seja: não existe API pública pra "roubar" a renderização de HTML do
    // WebView sem passar pelo diálogo de verdade.
    //
    // Por isso aqui é um gerador de PDF PRÓPRIO (android.graphics.pdf.PdfDocument
    // + Canvas), bem mais simples que o HTML/CSS da impressão -- só texto em
    // colunas, sem as bordas/zebra da versão impressa -- mas cobre o mesmo
    // conteúdo e evita o diálogo por completo.
    fun exportPdfDirect(context: Context, domain: DomainConfig, records: List<Map<String, String?>>, visibleKeys: Set<String>? = null) {
        val cols = domain.columns.filter { !it.hideInTable && (visibleKeys == null || visibleKeys.contains(it.key)) }
        val file = buildPdfFile(context, domain.label, cols, records)
        openPdf(context, file)
    }

    /** v19 -- PDF direto de uma tabela simples (botão "PDF" separado do "Imprimir"). */
    fun exportSimpleTablePdf(context: Context, title: String, headers: List<String>, rows: List<List<String>>) {
        val cols = headers.mapIndexed { i, h -> ColumnConfig(key = "c$i", label = h, type = "text") }
        val records = rows.map { r -> cols.indices.associate { i -> "c$i" to r.getOrNull(i) } }
        openPdf(context, buildPdfFile(context, title, cols, records))
    }

    private fun buildPdfFile(context: Context, title: String, cols: List<ColumnConfig>, records: List<Map<String, String?>>): File {
        // A4 paisagem em pontos (1/72"), mais colunas cabem numa linha.
        val pageWidth = 842
        val pageHeight = 595
        val margin = 24f
        val colCount = cols.size.coerceAtLeast(1)
        val fontSize = when {
            colCount <= 4 -> 12f
            colCount <= 6 -> 10f
            colCount <= 9 -> 9f
            else -> 8f
        }
        val colWidth = (pageWidth - margin * 2) / colCount
        val rowHeight = fontSize + 12f
        val headerPaint = Paint().apply { textSize = fontSize; isFakeBoldText = true; color = android.graphics.Color.BLACK }
        val cellPaint = Paint().apply { textSize = fontSize; color = android.graphics.Color.BLACK }
        val titlePaint = Paint().apply { textSize = 16f; isFakeBoldText = true; color = android.graphics.Color.BLACK }
        val linePaint = Paint().apply { color = android.graphics.Color.LTGRAY; strokeWidth = 0.5f }

        fun maxChars(): Int = (colWidth / (fontSize * 0.55f)).toInt().coerceAtLeast(3)
        fun clip(text: String): String {
            val max = maxChars()
            return if (text.length > max) text.take(max - 1) + "…" else text
        }

        val pdf = PdfDocument()
        var pageNum = 1
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
        var canvas = page.canvas
        var y = margin + 16f
        canvas.drawText(title, margin, y, titlePaint)
        y += rowHeight

        fun drawHeaderRow() {
            cols.forEachIndexed { i, col -> canvas.drawText(clip(col.label), margin + i * colWidth, y, headerPaint) }
            y += 4f
            canvas.drawLine(margin, y, pageWidth - margin, y, linePaint)
            y += rowHeight
        }
        drawHeaderRow()

        records.forEach { record ->
            if (y > pageHeight - margin) {
                pdf.finishPage(page)
                pageNum++
                page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
                canvas = page.canvas
                y = margin + 16f
                drawHeaderRow()
            }
            cols.forEachIndexed { i, col ->
                val raw = record[col.key].orEmpty()
                val text = if (col.money && raw.isNotBlank()) formatMoneyValue(raw) else displayValueFor(col.key, raw, col.type)
                canvas.drawText(clip(text), margin + i * colWidth, y, cellPaint)
            }
            y += rowHeight
        }
        pdf.finishPage(page)

        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safeTitle = title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        val file = File(dir, "$safeTitle.pdf")
        FileOutputStream(file).use { pdf.writeTo(it) }
        pdf.close()
        return file
    }

    // "Imprimir"/"PDF" dentro do bloco Gráficos (pedido do usuário: "dentro
    // do ícone gráficos... coloque do lado direito na vertical os seguintes
    // ícones imprimir e pdf") -- os gráficos não são uma lista de registros
    // (não passam por DomainConfig/ColumnConfig), então usam sua própria
    // representação simples de título + tabela de linhas, uma por gráfico
    // já separado em bloco (ModuleChartsCard.kt monta essa lista a partir
    // dos mesmos dados exibidos na tela, um item por bloco de gráfico).
    // barCategories/barSeries (opcionais, só preenchidos pra gráficos do tipo
    // "bar" -- ver genericToPrintData/specToPrintData em ModuleChartsCard.kt)
    // guardam os valores NUMÉRICOS crus, separado de headers/rows (que já
    // vêm formatados como texto pra tabela) -- necessários pra desenhar as
    // barras propocionalmente ao maior valor, igual ao SimpleBarChart da tela.
    data class ChartPrintData(
        val title: String,
        val headers: List<String>,
        val rows: List<List<String>>,
        val barCategories: List<String> = emptyList(),
        val barSeries: List<Pair<String, List<Double>>> = emptyList(),
        val isMoney: Boolean = false,
    )

    // QR Codes de Frota (Task #602, paridade com frota-qr-codes-button.tsx
    // do site) -- reaproveita o MESMO mecanismo de impressão via WebView
    // acima (HTML com <img> em base64 por QR), só que em retrato (o layout
    // do site usa uma grade normal, não uma tabela larga).
    fun printQrCodes(context: Context, title: String, items: List<Pair<String, Bitmap>>) {
        val html = buildQrCodesHtml(title, items)
        print(context, jobName = title, html = html, landscape = false)
    }

    private fun buildQrCodesHtml(title: String, items: List<Pair<String, Bitmap>>): String {
        val cards = items.joinToString("") { (label, bitmap) ->
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            val base64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            """
              <div class="qr-card">
                <img src="data:image/png;base64,$base64" />
                <p>${escapeHtml(label)}</p>
              </div>
            """.trimIndent()
        }
        return """
            <!DOCTYPE html><html lang="pt-BR"><head><meta charset="utf-8"><title>${escapeHtml(title)}</title>
            <style>
              @page { size: portrait; margin: 12mm; }
              body { font-family: Arial, Helvetica, sans-serif; padding: 12px; color: #111; }
              h1 { font-size: 16px; margin-bottom: 12px; }
              .grid { display: flex; flex-wrap: wrap; gap: 16px; }
              .qr-card { width: 160px; text-align: center; border: 1px solid #ccc; padding: 8px; page-break-inside: avoid; }
              .qr-card img { width: 140px; height: 140px; }
              .qr-card p { font-size: 12px; margin: 6px 0 0; word-break: break-word; }
            </style></head>
            <body>
              <h1>${escapeHtml(title)}</h1>
              <div class="grid">$cards</div>
            </body></html>
        """.trimIndent()
    }

    fun printCharts(context: Context, title: String, charts: List<ChartPrintData>) {
        val html = buildChartsHtml(title, charts)
        print(context, jobName = title, html = html)
    }

    fun exportChartsPdfDirect(context: Context, title: String, charts: List<ChartPrintData>) {
        val file = buildChartsPdfFile(context, title, charts)
        openPdf(context, file)
    }

    private fun buildChartsHtml(title: String, charts: List<ChartPrintData>): String {
        val geradoEm = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale("pt", "BR")).format(java.util.Date())
        val blocks = charts.joinToString("") { chart ->
            val header = chart.headers.joinToString("") { "<th>${escapeHtml(it)}</th>" }
            val rows = chart.rows.joinToString("") { row -> "<tr>${row.joinToString("") { "<td>${escapeHtml(it)}</td>" }}</tr>" }
            val svg = if (chart.barCategories.isNotEmpty() && chart.barSeries.isNotEmpty()) buildBarChartSvg(chart.barCategories, chart.barSeries, chart.isMoney) else ""
            """
              <h2>${escapeHtml(chart.title)}</h2>
              $svg
              <table><thead><tr>$header</tr></thead><tbody>$rows</tbody></table>
            """.trimIndent()
        }
        return """
            <!DOCTYPE html><html lang="pt-BR"><head><meta charset="utf-8"><title>${escapeHtml(title)}</title>
            <style>
              /* Paisagem -- bug real reportado pelo usuário ("está
                 configurado para retrato, acho que fica melhor em
                 paisagem"): mesma convenção "@page { size: landscape }" já
                 usada pelo site inteiro (globals.css), que este HTML gerado
                 pelo app nunca tinha. */
              @page { size: landscape; margin: 12mm; }
              body { font-family: Arial, Helvetica, sans-serif; padding: 24px; color: #111; }
              h1 { font-size: 18px; margin-bottom: 2px; }
              h2 { font-size: 14px; margin-top: 20px; margin-bottom: 6px; }
              p { font-size: 11px; color: #666; margin-top: 0; margin-bottom: 16px; }
              table { border-collapse: collapse; width: 100%; font-size: 11px; margin-bottom: 12px; }
              th, td { border: 1px solid #ccc; padding: 6px 10px; text-align: left; word-break: break-word; overflow-wrap: anywhere; }
              svg { overflow: visible; }
              .chart-legend { font-size: 10px; color: #444; margin: 4px 0 10px; }
              .chart-legend span { display: inline-block; margin-right: 12px; }
              .chart-legend i { display: inline-block; width: 8px; height: 8px; margin-right: 4px; }
            </style></head>
            <body>
              <h1>${escapeHtml(title)} -- Gráficos</h1>
              <p>Gerado em $geradoEm</p>
              $blocks
            </body></html>
        """.trimIndent()
    }

    // Barras de verdade (SVG) em vez de só a tabela por trás -- bug real
    // reportado pelo usuário ("gráficos não aparecem na pré-visualização da
    // impressora, apenas tabela, e também não aparecem em PDF"): igual ao
    // SimpleBarChart.kt da tela, altura proporcional ao maior valor de todas
    // as séries/categorias, cores ciclando verde/amarelo/azul da marca.
    private val PRINT_SERIES_COLORS = listOf("#2F6F4F", "#F2C037", "#1E4B8A")

    private fun buildBarChartSvg(categories: List<String>, series: List<Pair<String, List<Double>>>, isMoney: Boolean): String {
        if (categories.isEmpty() || series.isEmpty()) return ""
        val maxValue = (series.flatMap { it.second }.maxOrNull() ?: 0.0).let { if (it <= 0.0) 1.0 else it }
        // v47 -- mesma regra da tela, recalculada para a página em PAISAGEM:
        //  1. texto e número cabem na largura da coluna -> HORIZONTAL: texto na
        //     base da barra, número no topo;
        //  2. não cabe -> em pé ao lado da barra: texto à esquerda, número à
        //     direita (texto que nem em pé cabe na altura quebra em 2 colunas);
        //  3. nem em pé cabe -> texto horizontal a cada N colunas, sem número.
        // A decisão vale para o gráfico inteiro (todas as barras iguais).
        val width = 760.0
        val n = categories.size
        val gap = 6.0
        val colW = (width - gap * (n - 1)) / n
        val charW = 5.2
        val lh = 11.0
        fun tw(t: String) = t.length * charW
        val fmt = if (isMoney) {
            java.text.NumberFormat.getCurrencyInstance(java.util.Locale("pt", "BR"))
        } else {
            java.text.NumberFormat.getNumberInstance(java.util.Locale("pt", "BR")).apply { maximumFractionDigits = 1 }
        }
        val textos = if (series.size == 1) series[0].second.map { if (it != 0.0) fmt.format(it) else "" } else emptyList()
        val temValor = textos.any { it.isNotEmpty() }
        val nomeMax = categories.maxOf { tw(it) }
        val valMax = textos.maxOfOrNull { tw(it) } ?: 0.0
        val plotH = 200.0
        val horizontal = nomeMax + 4 <= colW && valMax + 4 <= colW
        val linhas = if (horizontal || nomeMax <= plotH) 1 else 2
        val rotW = lh * linhas + (if (temValor) lh else 0.0)
        val emPe = !horizontal && colW >= rotW + 3.0 * series.size + 2
        val passo = if (horizontal || emPe) 1 else kotlin.math.ceil((nomeMax + 4) / (colW + gap)).toInt().coerceAtLeast(1)
        val topo = if (horizontal && temValor) 16.0 else 6.0
        val baseline = topo + plotH
        val height = baseline + (if (emPe) 4.0 else 16.0)
        val barW = ((if (emPe) colW - rotW - 2 else colW * 0.72) / series.size).coerceIn(3.0, 48.0)
        val groupW = barW * series.size
        val nomes = categories.map { if (linhas == 2) com.bragro.mobile.ui.domain.quebrarEmDuas(it) else listOf(it) }
        fun emPeSvg(t: String, xDir: Double, peso: String, cor: String) =
            """<text transform="translate(${xDir.f()},${(baseline - 2).f()}) rotate(-90)" font-size="9" $peso fill="$cor">${escapeHtml(t)}</text>"""
        val bars = StringBuilder()
        val labels = StringBuilder()
        categories.forEachIndexed { ci, cat ->
            val colX = ci * (colW + gap)
            val totalW = if (emPe) rotW + 2 + groupW else groupW
            var x = colX + (colW - totalW) / 2
            if (emPe) {
                nomes[ci].forEachIndexed { j, ln -> labels.append(emPeSvg(ln, x + (j + 1) * lh - 2.5, "", "#333")) }
                x += lh * linhas + 2
            }
            series.forEachIndexed { si, (_, values) ->
                val v = values.getOrElse(ci) { 0.0 }
                val h = (v / maxValue).coerceIn(0.0, 1.0) * plotH
                bars.append("""<rect x="${(x + si * barW).f()}" y="${(baseline - h).f()}" width="${(barW - 1).coerceAtLeast(2.0).f()}" height="${h.coerceAtLeast(1.0).f()}" fill="${PRINT_SERIES_COLORS[si % PRINT_SERIES_COLORS.size]}" />""")
            }
            val cx = x + groupW / 2
            val texto = textos.getOrElse(ci) { "" }
            val h0 = (series[0].second.getOrElse(ci) { 0.0 } / maxValue).coerceIn(0.0, 1.0) * plotH
            when {
                horizontal -> {
                    labels.append("""<text x="${cx.f()}" y="${(baseline + 12).f()}" font-size="9" text-anchor="middle" fill="#333">${escapeHtml(cat)}</text>""")
                    if (texto.isNotEmpty()) bars.append("""<text x="${cx.f()}" y="${(baseline - h0 - 4).f()}" font-size="9" font-weight="600" text-anchor="middle" fill="#111">${escapeHtml(texto)}</text>""")
                }
                emPe -> if (texto.isNotEmpty()) bars.append(emPeSvg(texto, x + groupW + 2 + lh - 2.5, "font-weight=\"600\"", "#111"))
                ci % passo == 0 -> labels.append("""<text x="${colX.f()}" y="${(baseline + 12).f()}" font-size="9" fill="#333">${escapeHtml(cat)}</text>""")
            }
        }
        val legend = if (series.size > 1) {
            "<div class=\"chart-legend\">" + series.mapIndexed { i, (label, _) ->
                "<span><i style=\"background:${PRINT_SERIES_COLORS[i % PRINT_SERIES_COLORS.size]}\"></i>${escapeHtml(label)}</span>"
            }.joinToString("") + "</div>"
        } else ""
        return """
            <svg viewBox="0 0 ${width.f()} ${height.f()}" width="100%" height="${(height * 0.82).f()}" xmlns="http://www.w3.org/2000/svg">
              <line x1="0" y1="${baseline.f()}" x2="${width.f()}" y2="${baseline.f()}" stroke="#ccc" stroke-width="1" />
              $bars
              $labels
            </svg>
            $legend
        """.trimIndent()
    }

    private fun Double.f(): String = String.format(java.util.Locale.US, "%.1f", this)

    private fun clipLabel(text: String): String = if (text.length > 14) text.take(13) + "…" else text

    private fun buildChartsPdfFile(context: Context, title: String, charts: List<ChartPrintData>): File {
        // v23 -- A4 paisagem (em retrato palavras e valores encavalavam).
        // v22 -- PDF dos gráficos refeito (saía desconfigurado),
        // título, barras proporcionais com o valor em cima, rótulos que
        // quebram/encolhem em vez de cortar e a tabela embaixo, com colunas
        // proporcionais ao conteúdo e texto ajustado à largura da célula.
        val pageWidth = 842
        val pageHeight = 595
        val margin = 32f
        val contentW = pageWidth - margin * 2
        val black = android.graphics.Color.BLACK
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 16f; isFakeBoldText = true; color = black }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8.5f; color = android.graphics.Color.DKGRAY }
        val chartTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f; isFakeBoldText = true; color = black }
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; isFakeBoldText = true; color = black }
        val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; color = black }
        val axisPaint = Paint().apply { color = android.graphics.Color.GRAY; strokeWidth = 0.8f }
        val gridPaint = Paint().apply { color = android.graphics.Color.parseColor("#E3E3E3"); strokeWidth = 0.5f }
        val linePaint = Paint().apply { color = android.graphics.Color.parseColor("#D4D4D4"); strokeWidth = 0.6f }
        val headBg = Paint().apply { color = android.graphics.Color.parseColor("#EEF0EF") }
        val barLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8f; color = android.graphics.Color.DKGRAY; textAlign = Paint.Align.CENTER }
        val valueLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 7.5f; color = black; textAlign = Paint.Align.CENTER }
        val legendPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8.5f; color = black }
        val seriesColors = intArrayOf(android.graphics.Color.parseColor("#2F6F4F"), android.graphics.Color.parseColor("#C9A227"), android.graphics.Color.parseColor("#1E4B8A"))
        val barPaints = seriesColors.map { Paint().apply { color = it } }
        val rowHeight = 17f
        val plotHeight = 170f

        fun fit(text: String, paint: Paint, maxW: Float): String {
            if (paint.measureText(text) <= maxW) return text
            var t = text
            while (t.length > 1 && paint.measureText("$t…") > maxW) t = t.dropLast(1)
            return "$t…"
        }

        val pdf = PdfDocument()
        var pageNum = 1
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
        var canvas = page.canvas
        var y = margin + 14f
        canvas.drawText(fit(title, titlePaint, contentW), margin, y, titlePaint)
        y += 14f
        val gerado = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(java.util.Date())
        canvas.drawText("Gerado em $gerado", margin, y, subPaint)
        y += 20f

        fun newPageIfNeeded(extra: Float = rowHeight) {
            if (y + extra > pageHeight - margin) {
                pdf.finishPage(page)
                pageNum++
                page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
                canvas = page.canvas
                y = margin + 14f
            }
        }

        fun valor(v: Double, money: Boolean): String = if (money) formatChartValue(v, true)
            else if (v == Math.floor(v)) java.text.NumberFormat.getIntegerInstance(Locale("pt", "BR")).format(v.toLong())
            else java.text.NumberFormat.getNumberInstance(Locale("pt", "BR")).apply { maximumFractionDigits = 2 }.format(v)

        // Quebra o texto em linhas que cabem em maxW (palavra longa é partida
        // em pedaços) -- v44: nada sai cortado com "…" no PDF.
        fun wrap(text: String, paint: Paint, maxW: Float): List<String> {
            if (text.isEmpty()) return listOf("")
            val linhas = mutableListOf<String>()
            var atual = ""
            for (palavra in text.split(' ')) {
                var w = palavra
                while (paint.measureText(w) > maxW && w.length > 1) {
                    if (atual.isNotEmpty()) { linhas += atual; atual = "" }
                    var k = w.length
                    while (k > 1 && paint.measureText(w.take(k)) > maxW) k--
                    linhas += w.take(k)
                    w = w.drop(k)
                }
                val tentativa = if (atual.isEmpty()) w else "$atual $w"
                if (paint.measureText(tentativa) <= maxW) atual = tentativa
                else { linhas += atual; atual = w }
            }
            if (atual.isNotEmpty() || linhas.isEmpty()) linhas += atual
            return linhas
        }

        fun drawBars(chart: ChartPrintData) {
            val all = chart.barSeries.flatMap { it.second }
            val maxValue = (all.maxOrNull() ?: 0.0).coerceAtLeast(0.0).let { if (it <= 0.0) 1.0 else it }
            val n = chart.barCategories.size
            val gapCol = 6f
            val colWidth = (contentW - gapCol * (n - 1)) / n
            val seriesCount = chart.barSeries.size.coerceAtLeast(1)
            // largura de uma coluna de texto em pé
            val alturaTexto = barLabelPaint.textSize + 2f
            // v47 -- mesma regra da tela, recalculada para a página em PAISAGEM:
            //  1. texto e número cabem na coluna -> HORIZONTAL: texto na base
            //     da barra e número no topo;
            //  2. não cabe -> em pé ao lado da barra: texto à esquerda, número à
            //     direita (texto que nem em pé cabe na altura quebra em 2 colunas);
            //  3. nem em pé cabe -> texto horizontal a cada N colunas, sem número.
            // A decisão vale para o gráfico inteiro (todas as barras iguais).
            val textos = if (seriesCount == 1) chart.barSeries[0].second.map { if (it != 0.0) valor(it, chart.isMoney) else "" } else emptyList()
            val temValor = textos.any { it.isNotEmpty() }
            val maxNomeW = chart.barCategories.maxOf { barLabelPaint.measureText(it) }
            val maxValW = textos.maxOfOrNull { valueLabelPaint.measureText(it) } ?: 0f
            val horizontal = maxNomeW + 4f <= colWidth && maxValW + 4f <= colWidth
            val linhasNome = if (horizontal || maxNomeW <= plotHeight) 1 else 2
            val nomes = chart.barCategories.map { if (linhasNome == 2) com.bragro.mobile.ui.domain.quebrarEmDuas(it) else listOf(it) }
            val rotulosW = alturaTexto * linhasNome + (if (temValor) alturaTexto else 0f)
            val emPe = !horizontal && colWidth >= rotulosW + 3f * seriesCount + 2f
            val passo = if (horizontal || emPe) 1 else kotlin.math.ceil((maxNomeW + 4f) / (colWidth + gapCol)).toInt().coerceAtLeast(1)
            val barWidth = ((if (emPe) colWidth - rotulosW - 2f else colWidth * 0.72f) / seriesCount).coerceIn(2f, 40f)
            val groupW = barWidth * seriesCount
            val folgaTopo = if (horizontal && temValor) 12f else 0f
            val labelArea = if (emPe) 6f else 14f
            val legendArea = if (chart.barSeries.size > 1) 14f else 0f
            val top = y + 12f + folgaTopo
            val baseline = top + plotHeight
            val nomeEmPePaint = Paint(barLabelPaint).apply { textAlign = Paint.Align.LEFT }
            val valorEmPePaint = Paint(valueLabelPaint).apply { textAlign = Paint.Align.LEFT; isFakeBoldText = true }
            val nomeEsqPaint = Paint(barLabelPaint).apply { textAlign = Paint.Align.LEFT }
            // texto em pé lendo de baixo pra cima, começando na base; xDir é a
            // borda direita da coluna de texto.
            fun emPeTexto(t: String, xDir: Float, paint: Paint) {
                canvas.save()
                canvas.rotate(-90f, xDir, baseline - 2f)
                canvas.drawText(t, xDir, baseline - 2f, paint)
                canvas.restore()
            }
            for (k in 0..4) {
                val gy = baseline - plotHeight * k / 4f
                canvas.drawLine(margin, gy, margin + contentW, gy, if (k == 0) axisPaint else gridPaint)
            }
            chart.barCategories.forEachIndexed { ci, cat ->
                val colX = margin + ci * (colWidth + gapCol)
                val totalW = if (emPe) rotulosW + 2f + groupW else groupW
                var x = colX + (colWidth - totalW) / 2f
                if (emPe) {
                    nomes[ci].forEachIndexed { j, ln -> emPeTexto(ln, x + (j + 1) * alturaTexto - 2.5f, nomeEmPePaint) }
                    x += alturaTexto * linhasNome + 2f
                }
                var topo0 = baseline
                chart.barSeries.forEachIndexed { si, (_, values) ->
                    val v = values.getOrElse(ci) { 0.0 }
                    val h = ((v / maxValue).coerceIn(0.0, 1.0) * plotHeight).toFloat()
                    val bx = x + si * barWidth
                    if (si == 0) topo0 = baseline - h
                    canvas.drawRect(bx + 0.5f, baseline - h, bx + barWidth - 0.5f, baseline, barPaints[si % barPaints.size])
                }
                val cx = x + groupW / 2f
                val texto = textos.getOrElse(ci) { "" }
                when {
                    horizontal -> {
                        canvas.drawText(cat, cx, baseline + 11f, barLabelPaint)
                        if (texto.isNotEmpty()) canvas.drawText(texto, cx, topo0 - 3f, valueLabelPaint)
                    }
                    emPe -> if (texto.isNotEmpty()) emPeTexto(texto, x + groupW + 2f + alturaTexto - 2.5f, valorEmPePaint)
                    ci % passo == 0 -> if (colX + nomeEsqPaint.measureText(cat) <= margin + contentW) canvas.drawText(cat, colX, baseline + 11f, nomeEsqPaint)
                }
            }
            y = baseline + labelArea
            if (legendArea > 0f) {
                var lx = margin
                chart.barSeries.forEachIndexed { si, (nome, _) ->
                    val w = 22f + legendPaint.measureText(nome)
                    if (lx + w > margin + contentW) { lx = margin; y += legendArea }
                    canvas.drawRect(lx, y + 2f, lx + 8f, y + 10f, barPaints[si % barPaints.size])
                    canvas.drawText(nome, lx + 11f, y + 9.5f, legendPaint)
                    lx += w
                }
                y += legendArea
            }
            y += 10f
        }

        fun drawTable(chart: ChartPrintData) {
            val cols = chart.headers.size.coerceAtLeast(1)
            val minCol = 64f
            // largura desejada = maior texto da coluna (limitada); a coluna
            // nunca fica abaixo de minCol, e o texto que passar QUEBRA linha.
            val desejada = (0 until cols).map { c ->
                val textos = listOf(chart.headers.getOrElse(c) { "" }) + chart.rows.map { it.getOrElse(c) { "" } }
                (textos.maxOf { cellPaint.measureText(it) } + 10f).coerceIn(minCol, 240f)
            }
            // Colunas demais para a folha: divide em blocos de colunas,
            // repetindo a 1ª coluna (a identificação da linha) em cada bloco.
            val blocos = mutableListOf<List<Int>>()
            var atual = mutableListOf(0)
            var soma = desejada[0]
            for (c in 1 until cols) {
                if (soma + minCol > contentW && atual.size > 1) {
                    blocos += atual; atual = mutableListOf(0, c); soma = desejada[0] + minCol
                } else { atual += c; soma += minCol }
            }
            blocos += atual
            blocos.forEachIndexed { bi, bloco ->
                val pesos = bloco.map { desejada[it] }
                val total = pesos.sum()
                val larguras = if (total <= contentW) pesos.map { it / total * contentW } else {
                    // encolhe proporcionalmente, sem passar do mínimo
                    val escala = contentW / total
                    pesos.map { maxOf(minCol, it * escala) }.let { l -> val t = l.sum(); l.map { it / t * contentW } }
                }
                fun linha(cells: List<String>, paint: Paint, fundo: Paint?) {
                    val quebradas = bloco.mapIndexed { i, c -> wrap(cells.getOrElse(c) { "" }, paint, larguras[i] - 8f) }
                    val h = maxOf(rowHeight, quebradas.maxOf { it.size } * 11f + 6f)
                    newPageIfNeeded(h)
                    if (fundo != null) canvas.drawRect(margin, y, margin + contentW, y + h, fundo)
                    var x = margin
                    quebradas.forEachIndexed { i, ls ->
                        val w = larguras[i]
                        val cell = cells.getOrElse(bloco[i]) { "" }
                        val numerico = bloco[i] > 0 && cell.isNotBlank() && cell.trim().let { it.first().isDigit() || it.startsWith("R$") || it.startsWith("-") }
                        ls.forEachIndexed { li, t ->
                            val ty = y + 12f + li * 11f
                            if (numerico) canvas.drawText(t, x + w - 4f - paint.measureText(t), ty, paint)
                            else canvas.drawText(t, x + 4f, ty, paint)
                        }
                        x += w
                    }
                    y += h
                    canvas.drawLine(margin, y, margin + contentW, y, linePaint)
                }
                if (blocos.size > 1) {
                    newPageIfNeeded(rowHeight * 2)
                    canvas.drawText("Colunas ${bi + 1} de ${blocos.size}", margin, y + 9f, subPaint)
                    y += 12f
                }
                linha(chart.headers, headerPaint, headBg)
                chart.rows.forEach { linha(it, cellPaint, null) }
                y += 16f
            }
        }

        charts.forEach { chart ->
            val hasBars = chart.barCategories.isNotEmpty() && chart.barSeries.isNotEmpty()
            newPageIfNeeded(30f + (if (hasBars) plotHeight + 200f else 0f) + rowHeight * 2)
            wrap(chart.title, chartTitlePaint, contentW).forEachIndexed { i, t -> if (i > 0) y += 14f; canvas.drawText(t, margin, y, chartTitlePaint) }
            y += 6f
            if (hasBars) drawBars(chart) else y += 10f
            if (chart.headers.isNotEmpty()) drawTable(chart)
        }
        pdf.finishPage(page)

        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safeTitle = "$title-graficos".lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        val file = File(dir, "$safeTitle.pdf")
        FileOutputStream(file).use { pdf.writeTo(it) }
        pdf.close()
        return file
    }

    private fun openPdf(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // Nenhum app associado a PDF -- mostra o menu "Abrir com" (mesmo
            // padrão de shareTextFile) em vez de travar sem feedback.
            val chooser = Intent.createChooser(intent, "Abrir PDF")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        }
    }
}

private fun escapeHtml(value: String): String =
    value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

/** Mesmo tamanho de fonte/preenchimento crescente conforme menos colunas
 * (printSizing em data-table.tsx), pra nao desperdicar a folha quando o
 * modulo tem poucos campos. */
private fun printSizing(colCount: Int): Pair<Int, String> = when {
    colCount <= 4 -> 18 to "10px 14px"
    colCount <= 6 -> 15 to "8px 12px"
    colCount <= 9 -> 13 to "6px 10px"
    colCount <= 13 -> 11 to "5px 8px"
    colCount <= 18 -> 10 to "4px 6px"
    else -> 8 to "3px 5px"
}

private fun buildListHtml(title: String, cols: List<ColumnConfig>, records: List<Map<String, String?>>): String {
    val (font, padding) = printSizing(cols.size)
    val header = cols.joinToString("") { "<th>${escapeHtml(it.label)}</th>" }
    val rows = records.joinToString("") { record ->
        val cells = cols.joinToString("") { col ->
            val raw = record[col.key].orEmpty()
            val text = if (col.money && raw.isNotBlank()) formatMoneyValue(raw) else displayValueFor(col.key, raw, col.type)
            "<td>${escapeHtml(text)}</td>"
        }
        "<tr>$cells</tr>"
    }
    val geradoEm = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale("pt", "BR")).format(java.util.Date())
    return """
        <!DOCTYPE html><html lang="pt-BR"><head><meta charset="utf-8"><title>${escapeHtml(title)}</title>
        <style>
          /* Paisagem -- mesma convenção do site (globals.css). */
          @page { size: landscape; margin: 12mm; }
          body { font-family: Arial, Helvetica, sans-serif; padding: 24px; color: #111; }
          h1 { font-size: 18px; margin-bottom: 2px; }
          p { font-size: 11px; color: #666; margin-top: 0; margin-bottom: 16px; }
          table { border-collapse: collapse; width: 100%; font-size: ${font}px; }
          th, td { border: 1px solid #ccc; padding: $padding; text-align: left; }
        </style></head>
        <body>
          <h1>${escapeHtml(title)}</h1>
          <p>Gerado em $geradoEm -- ${records.size} registro(s)</p>
          <table><thead><tr>$header</tr></thead><tbody>$rows</tbody></table>
        </body></html>
    """.trimIndent()
}
