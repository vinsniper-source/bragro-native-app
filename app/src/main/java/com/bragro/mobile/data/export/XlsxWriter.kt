package com.bragro.mobile.data.export

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Gerador mínimo de .xlsx (Excel real) usando só java.util.zip -- mesmo
 * espírito do parser de KML feito à mão (data/kml/KmlParser.kt): evita
 * trazer uma lib pesada (Apache POI é gigante) só pra escrever uma tabela
 * simples (texto/número, sem fórmula/estilo). Substitui o antigo
 * buildCsv/exportCsv (ver ui/domain/ColumnsAndExport.kt) -- pedido do
 * usuário ("CSV → XLSX real no mobile").
 *
 * Um .xlsx é um .zip com um conjunto mínimo de XMLs (OOXML,
 * ECMA-376/ISO-29500). Pra uma única aba sem formatação, o mínimo que o
 * Excel aceita abrir sem reclamar é:
 * - [Content_Types].xml -- declara os tipos de cada parte do pacote.
 * - _rels/.rels -- relação raiz do pacote -> aponta pro workbook.
 * - xl/workbook.xml -- lista as abas (aqui, só "Sheet1").
 * - xl/_rels/workbook.xml.rels -- relação do workbook -> aponta pra
 *   worksheets/sheet1.xml.
 * - xl/worksheets/sheet1.xml -- os dados de fato (linhas/células).
 *
 * Texto usa <c t="inlineStr"><is><t>...</t></is></c> (célula "solta", sem
 * indireção) em vez da tabela de strings compartilhadas (sharedStrings.xml)
 * -- mais XML repetido por célula, mas dispensa mais um arquivo/relação no
 * pacote, e o Excel abre igual. Números (só dígitos, opcionalmente com um
 * ponto decimal) usam <c><v>123</v></c> puro, sem t (tipo numérico é o
 * default do formato). */
object XlsxWriter {

    private val PLAIN_NUMBER_REGEX = Regex("^-?\\d+(\\.\\d+)?$")
    // Caracteres de controle invalidos em XML 1.0 (fora tab/LF/CR) -- se
    // algum registro tiver esse tipo de lixo (nunca deveria, mas dado
    // digitado livre nao tem garantia nenhuma), sem filtrar o Excel recusa
    // o arquivo inteiro em vez de so aquela celula.
    private val INVALID_XML_CHARS = Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]")

    /** Monta o .xlsx completo em memória (pacote pequeno -- listas de
     * módulo/DRE/Análises nunca chegam a milhares de linhas neste app) e
     * devolve os bytes prontos pra gravar em arquivo/compartilhar (ver
     * shareBinaryFile em ui/util/FileShare.kt). */
    fun buildXlsx(headers: List<String>, rows: List<List<String>>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            writeEntry(zip, "[Content_Types].xml", CONTENT_TYPES_XML)
            writeEntry(zip, "_rels/.rels", ROOT_RELS_XML)
            writeEntry(zip, "xl/workbook.xml", WORKBOOK_XML)
            writeEntry(zip, "xl/_rels/workbook.xml.rels", WORKBOOK_RELS_XML)
            writeEntry(zip, "xl/worksheets/sheet1.xml", sheetXml(headers, rows))
        }
        return out.toByteArray()
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun sheetXml(headers: List<String>, rows: List<List<String>>): String {
        val lastColIndex = maxOf(headers.size - 1, 0)
        val lastRow = maxOf(rows.size + 1, 1)
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
        sb.append("<dimension ref=\"A1:${columnLetter(lastColIndex)}$lastRow\"/>")
        sb.append("<sheetData>")
        sb.append(rowXml(1, headers))
        rows.forEachIndexed { i, row -> sb.append(rowXml(i + 2, row)) }
        sb.append("</sheetData>")
        sb.append("</worksheet>")
        return sb.toString()
    }

    private fun rowXml(rowNumber: Int, cells: List<String>): String {
        val sb = StringBuilder("<row r=\"$rowNumber\">")
        cells.forEachIndexed { colIndex, raw ->
            val ref = "${columnLetter(colIndex)}$rowNumber"
            val value = sanitize(raw)
            if (value.isNotEmpty() && PLAIN_NUMBER_REGEX.matches(value)) {
                sb.append("<c r=\"$ref\"><v>$value</v></c>")
            } else if (value.isNotEmpty()) {
                sb.append("<c r=\"$ref\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${escape(value)}</t></is></c>")
            }
            // Célula vazia -- omitida (Excel trata ausência de <c> na linha
            // como célula em branco, não precisa de um <c/> explícito).
        }
        sb.append("</row>")
        return sb.toString()
    }

    private fun sanitize(value: String): String = INVALID_XML_CHARS.replace(value, "")

    private fun escape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    /** Índice de coluna baseado em 0 -> letra do Excel ("A", "B", ..., "Z",
     * "AA", "AB", ...) -- mesma conversão base-26 "sem zero" usada pelas
     * colunas de planilha (não é base-26 puro: não há dígito "0"). */
    private fun columnLetter(indexZeroBased: Int): String {
        var n = indexZeroBased + 1
        val sb = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            sb.insert(0, ('A' + rem))
            n = (n - 1) / 26
        }
        return sb.toString()
    }

    private const val CONTENT_TYPES_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>"""

    private const val ROOT_RELS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private const val WORKBOOK_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="Sheet1" sheetId="1" r:id="rId1"/>
  </sheets>
</workbook>"""

    private const val WORKBOOK_RELS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
</Relationships>"""
}
