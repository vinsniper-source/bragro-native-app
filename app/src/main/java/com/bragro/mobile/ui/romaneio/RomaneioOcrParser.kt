package com.bragro.mobile.ui.romaneio

/** Extrai peso bruto/tara/umidade/impureza/numero do romaneio do texto bruto
 * reconhecido pelo ML Kit na foto do ticket (Fase 2, Task #42) -- heuristica
 * simples por palavra-chave + primeiro numero decimal encontrado depois
 * dela, o suficiente pra maioria dos tickets de balanca (que seguem um
 * layout parecido: rotulo em maiusculas seguido do valor). Sempre um
 * "melhor esforco" -- o usuario confere/edita os campos antes de lancar,
 * igual ao aviso que ja existia no site pra quando um provedor de OCR
 * estiver configurado (hoje o site nao tem OCR real nenhum, so a
 * estrutura pronta; este e o primeiro OCR de verdade no projeto, rodando
 * 100% no aparelho via ML Kit, sem custo de servidor). */
object RomaneioOcrParser {
    private val numberAfterLabelRegex = Regex("[0-9]+[.,]?[0-9]*")

    fun parse(rawText: String): Map<String, String> {
        val text = rawText.uppercase()
        val fields = mutableMapOf<String, String>()

        numberAfter(text, listOf("PESO BRUTO", "BRUTO"))?.let { fields["pesoBrutoKg"] = it }
        numberAfter(text, listOf("TARA"))?.let { fields["taraKg"] = it }
        numberAfter(text, listOf("UMIDADE"))?.let { fields["umidade"] = it }
        numberAfter(text, listOf("IMPUREZA"))?.let { fields["impureza"] = it }
        numberAfter(text, listOf("Nº ROMANEIO", "NO ROMANEIO", "ROMANEIO Nº", "ROMANEIO NO", "ROMANEIO", "TICKET"))
            ?.let { fields["noRomaneio"] = it }

        return fields
    }

    /** Procura o rotulo (primeira ocorrencia entre as variacoes dadas) e
     * pega o primeiro numero decimal logo depois dele na mesma linha. */
    private fun numberAfter(text: String, labels: List<String>): String? {
        for (label in labels) {
            val idx = text.indexOf(label)
            if (idx < 0) continue
            val restoLinha = text.substring(idx + label.length).substringBefore("\n")
            val match = numberAfterLabelRegex.find(restoLinha) ?: continue
            return match.value.replace(",", ".")
        }
        return null
    }
}
