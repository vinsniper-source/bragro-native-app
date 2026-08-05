package com.bragro.mobile.ui.print

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.bragro.mobile.data.model.ColumnConfig
import com.bragro.mobile.data.model.DomainConfig
import com.bragro.mobile.ui.domain.displayValueFor
import com.bragro.mobile.ui.domain.formatMoneyValue

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

    private fun print(context: Context, jobName: String, html: String) {
        val webView = WebView(context)
        activeWebView = webView
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                if (printManager != null) {
                    val adapter = view.createPrintDocumentAdapter(jobName)
                    printManager.print(jobName, adapter, PrintAttributes.Builder().build())
                }
                activeWebView = null
            }
        }
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
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
