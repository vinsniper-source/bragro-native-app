package com.bragro.mobile.ui.print

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
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
              th, td { border: 1px solid #ccc; padding: 6px 10px; text-align: left; }
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
        val width = 760.0
        val height = 220.0
        val baseline = height - 24.0
        val chartTop = 20.0
        val chartH = baseline - chartTop
        val colWidth = width / categories.size
        val barGap = 2.0
        val barWidth = ((colWidth - barGap * (series.size + 1)) / series.size).coerceAtLeast(3.0)
        val fmt = if (isMoney) {
            java.text.NumberFormat.getCurrencyInstance(java.util.Locale("pt", "BR"))
        } else {
            java.text.NumberFormat.getNumberInstance(java.util.Locale("pt", "BR")).apply { maximumFractionDigits = 1 }
        }
        val bars = StringBuilder()
        val labels = StringBuilder()
        categories.forEachIndexed { ci, cat ->
            val colX = ci * colWidth
            series.forEachIndexed { si, (_, values) ->
                val v = values.getOrElse(ci) { 0.0 }
                val h = (v / maxValue).coerceIn(0.0, 1.0) * chartH
                val x = colX + barGap + si * (barWidth + barGap)
                val y = baseline - h
                bars.append("""<rect x="${x.f()}" y="${y.f()}" width="${barWidth.f()}" height="${h.coerceAtLeast(1.0).f()}" fill="${PRINT_SERIES_COLORS[si % PRINT_SERIES_COLORS.size]}" />""")
                if (series.size == 1 && v != 0.0) {
                    bars.append("""<text x="${(x + barWidth / 2).f()}" y="${(y - 4).coerceAtLeast(10.0).f()}" font-size="9" text-anchor="middle" fill="#111">${escapeHtml(fmt.format(v))}</text>""")
                }
            }
            labels.append("""<text x="${(colX + colWidth / 2).f()}" y="${(height - 6).f()}" font-size="9" text-anchor="middle" fill="#333">${escapeHtml(clipLabel(cat))}</text>""")
        }
        val legend = if (series.size > 1) {
            "<div class=\"chart-legend\">" + series.mapIndexed { i, (label, _) ->
                "<span><i style=\"background:${PRINT_SERIES_COLORS[i % PRINT_SERIES_COLORS.size]}\"></i>${escapeHtml(label)}</span>"
            }.joinToString("") + "</div>"
        } else ""
        return """
            <svg viewBox="0 0 ${width.f()} ${height.f()}" width="100%" height="180" xmlns="http://www.w3.org/2000/svg">
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
        // A4 paisagem -- bug real reportado pelo usuário ("está configurado
        // para retrato, acho que fica melhor em paisagem"): antes esta
        // função (só a de gráficos) usava 595x842 (retrato), diferente de
        // buildPdfFile (a de listas), que já era 842x595 (paisagem) -- agora
        // as duas seguem a mesma orientação, igual ao "@page { size:
        // landscape }" global do site.
        val pageWidth = 842
        val pageHeight = 595
        val margin = 24f
        val fontSize = 10f
        val headerPaint = Paint().apply { textSize = fontSize; isFakeBoldText = true; color = android.graphics.Color.BLACK }
        val cellPaint = Paint().apply { textSize = fontSize; color = android.graphics.Color.BLACK }
        val titlePaint = Paint().apply { textSize = 16f; isFakeBoldText = true; color = android.graphics.Color.BLACK }
        val chartTitlePaint = Paint().apply { textSize = 12f; isFakeBoldText = true; color = android.graphics.Color.BLACK }
        val axisPaint = Paint().apply { color = android.graphics.Color.LTGRAY; strokeWidth = 1f }
        val barLabelPaint = Paint().apply { textSize = 8f; color = android.graphics.Color.DKGRAY; textAlign = Paint.Align.CENTER }
        val valueLabelPaint = Paint().apply { textSize = 8f; color = android.graphics.Color.BLACK; textAlign = Paint.Align.CENTER }
        val seriesColors = intArrayOf(android.graphics.Color.parseColor("#2F6F4F"), android.graphics.Color.parseColor("#F2C037"), android.graphics.Color.parseColor("#1E4B8A"))
        val barPaints = seriesColors.map { Paint().apply { color = it } }
        val rowHeight = fontSize + 10f
        val chartAreaHeight = 150f

        val pdf = PdfDocument()
        var pageNum = 1
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
        var canvas = page.canvas
        var y = margin + 16f
        canvas.drawText(title, margin, y, titlePaint)
        y += rowHeight * 1.5f

        fun newPageIfNeeded(extra: Float = rowHeight) {
            if (y + extra > pageHeight - margin) {
                pdf.finishPage(page)
                pageNum++
                page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
                canvas = page.canvas
                y = margin + 16f
            }
        }

        // Desenha as barras de verdade (retângulos proporcionais ao maior
        // valor, igual ao SimpleBarChart da tela) -- bug real reportado pelo
        // usuário ("gráficos não aparecem... apenas tabela, também não
        // aparecem em PDF"): antes só existia a tabela abaixo, o PDF nunca
        // desenhava a barra em si.
        fun drawBars(chart: ChartPrintData) {
            val maxValue = (chart.barSeries.flatMap { it.second }.maxOrNull() ?: 0.0).let { if (it <= 0.0) 1.0 else it }
            val chartWidth = pageWidth - margin * 2
            val baseline = y + chartAreaHeight - 20f
            canvas.drawLine(margin, baseline, margin + chartWidth, baseline, axisPaint)
            val colWidth = chartWidth / chart.barCategories.size
            val seriesCount = chart.barSeries.size.coerceAtLeast(1)
            val barGap = 2f
            val barWidth = ((colWidth - barGap * (seriesCount + 1)) / seriesCount).coerceAtLeast(3f)
            chart.barCategories.forEachIndexed { ci, cat ->
                val colX = margin + ci * colWidth
                chart.barSeries.forEachIndexed { si, (_, values) ->
                    val v = values.getOrElse(ci) { 0.0 }
                    val h = ((v / maxValue).coerceIn(0.0, 1.0) * (chartAreaHeight - 20f)).toFloat()
                    val x = colX + barGap + si * (barWidth + barGap)
                    val top = baseline - h
                    canvas.drawRect(x, top, x + barWidth, baseline, barPaints[si % barPaints.size])
                    if (chart.barSeries.size == 1 && v != 0.0) {
                        val text = if (chart.isMoney) formatChartValue(v, true) else v.toString()
                        canvas.drawText(text, x + barWidth / 2, (top - 3f).coerceAtLeast(y + 8f), valueLabelPaint)
                    }
                }
                canvas.drawText(clipLabel(cat), colX + colWidth / 2, baseline + 12f, barLabelPaint)
            }
            y += chartAreaHeight
        }

        charts.forEach { chart ->
            val hasBars = chart.barCategories.isNotEmpty() && chart.barSeries.isNotEmpty()
            newPageIfNeeded(rowHeight * 2 + if (hasBars) chartAreaHeight else 0f)
            canvas.drawText(chart.title, margin, y, chartTitlePaint)
            y += rowHeight
            if (hasBars) drawBars(chart)
            val colCount = chart.headers.size.coerceAtLeast(1)
            val colWidth = (pageWidth - margin * 2) / colCount
            chart.headers.forEachIndexed { i, h -> canvas.drawText(h, margin + i * colWidth, y, headerPaint) }
            y += rowHeight
            chart.rows.forEach { row ->
                newPageIfNeeded()
                row.forEachIndexed { i, cell -> canvas.drawText(cell, margin + i * colWidth, y, cellPaint) }
                y += rowHeight
            }
            y += rowHeight * 0.5f
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
