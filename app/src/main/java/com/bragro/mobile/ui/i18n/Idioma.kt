package com.bragro.mobile.ui.i18n

import android.content.Context
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit

/**
 * Idioma do app (pt-BR padrao | es). Os textos da UI sao literais PT-BR no
 * codigo; o wrapper [Text] abaixo (usado no lugar do Text do Material3 via
 * import) traduz por texto exato quando o idioma e espanhol. O que nao tiver
 * traducao no mapa continua em portugues (fallback seguro).
 */
object Idioma {
    private const val PREFS = "bragro_prefs"
    private const val KEY = "idioma"

    /** "pt" ou "es". Estado observavel: trocar recompoe a UI toda. */
    var codigo by mutableStateOf("pt")
        private set

    /** "BR" ou "AR". Define tema (celeste/branco), cartão do dólar Argentina e
     * o idioma padrão. Estado observável como [codigo]. */
    var pais by mutableStateOf("BR")
        private set

    val argentina: Boolean get() = pais == "AR"

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        codigo = p.getString(KEY, "pt") ?: "pt"
        pais = p.getString("pais", "BR") ?: "BR"
    }

    /** Troca o país e acopla o idioma (AR -> es, BR -> pt); o idioma ainda
     * pode ser trocado à parte depois. */
    fun definirPais(context: Context, novo: String) {
        val idioma = if (novo == "AR") "es" else "pt"
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("pais", novo).putString(KEY, idioma).apply()
        pais = novo
        codigo = idioma
    }

    fun definir(context: Context, novo: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, novo).apply()
        codigo = novo
    }

    val espanhol: Boolean get() = codigo == "es"

    private val SUFIXO = Regex("^(.*?)(\\s*[*:…]+)$")

    // Cache do resultado por texto (tr() roda a cada recomposicao). "" = sem traducao.
    private val cache = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun tr(s: String): String {
        if (codigo != "es" || s.isBlank()) return s
        if (pais == "AR") {
            var t = s
            if (t.contains("BRAgro")) t = t.replace("BRAgro", "ARgro")
            t = nomesPropios(t)
            if (t != s) return tr(t)
        }
        val lead = s.takeWhile { it.isWhitespace() }
        val trail = s.takeLastWhile { it.isWhitespace() }
        val core = s.trim()
        val hit = cache.getOrPut(core) { traduzirCore(core) ?: "" }
        return if (hit.isEmpty()) s else lead + hit + trail
    }

    // Nomes proprios de fazendas ("Fazenda São João" -> "Finca San Juan"); so na Argentina.
    private val NOMES: Map<String, String> = mapOf(
        "fazenda" to "Finca", "sitio" to "Chacra", "chacara" to "Chacra", "estancia" to "Estancia",
        "sao" to "San", "joao" to "Juan", "antonio" to "Antonio", "jose" to "José", "maria" to "María",
        "sebastiao" to "Sebastián", "lourenco" to "Lorenzo", "tome" to "Tomás", "luiz" to "Luis", "luis" to "Luis",
        "boa" to "Buena", "bom" to "Buen", "esperanca" to "Esperanza", "conceicao" to "Concepción",
        "vitoria" to "Victoria", "senhora" to "Señora", "nossa" to "Nuestra", "jesus" to "Jesús",
        "rio" to "Río", "lagoa" to "Laguna", "cachoeira" to "Cascada", "pedra" to "Piedra", "serra" to "Sierra",
        "morro" to "Cerro", "ouro" to "Oro", "branca" to "Blanca", "branco" to "Blanco", "nova" to "Nueva",
        "novo" to "Nuevo", "velha" to "Vieja", "velho" to "Viejo", "estrela" to "Estrella", "lua" to "Luna",
        "palmeiras" to "Palmeras", "ipe" to "Lapacho", "trindade" to "Trinidad", "fe" to "Fe",
    )
    private val RE_GATILHO = Regex("\\b(?:Fazenda|Sítio|Sitio|Chácara|Chacara|Estância|Estancia|São|Sao|Santa|Santo|Nossa|FAZENDA|SÍTIO|CHÁCARA|ESTÂNCIA|SÃO|SANTA|SANTO|NOSSA)\\s+[A-ZÁÉÍÓÚÂÊÔÃÕÇ]")
    private val RE_NOME = Regex("\\b(?:(?:Fazenda|Sítio|Sitio|Chácara|Chacara|Estância|Estancia)\\s+)?(?:(?:S[ãa]o|Santa|Santo|Nossa|Boa|Bom|Rio|Lagoa|Serra|Vista|Nova|Novo)\\s+)?[A-ZÁÉÍÓÚÂÊÔÃÕÇ][\\p{L}'’-]*(?:\\s+(?:d[aeo]s?\\s+)?[A-ZÁÉÍÓÚÂÊÔÃÕÇ][\\p{L}'’-]*)*")

    private fun nomesPropios(s: String): String {
        if (!RE_GATILHO.containsMatchIn(s)) return s
        return RE_NOME.replace(s) { m ->
            m.value.split(Regex("(?<=\\s)|(?=\\s)")).joinToString("") { w ->
                if (w.isBlank()) w else {
                    val t = NOMES[norm(w)]
                    if (t == null) w else if (w.length > 1 && w == w.uppercase()) t.uppercase() else t
                }
            }
        }
    }

    private fun norm(s: String): String =
        java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").lowercase().replace(Regex("\\s+"), " ").trim()

    // Mapa com chave normalizada (sem acento/caixa): opcoes em CAIXA ALTA, variacoes de caixa.
    private val FRASES_N: Map<String, String> by lazy {
        val m = HashMap<String, String>()
        IdiomaExtra.FRASES.forEach { (k, v) -> m[norm(k)] = v }
        FRASES.forEach { (k, v) -> m[norm(k)] = v }
        m
    }

    private fun matchCase(core: String, tr: String): String {
        val letras = core.filter { it.isLetter() }
        if (letras.length > 1 && letras.all { it.isUpperCase() }) return tr.uppercase()
        val c0 = core.firstOrNull()
        if (c0 != null && c0.isLowerCase() && tr.firstOrNull()?.isUpperCase() == true) return tr.replaceFirstChar { it.lowercase() }
        return tr
    }

    private val MESES = mapOf(
        "janeiro" to "enero", "fevereiro" to "febrero", "marco" to "marzo", "abril" to "abril", "maio" to "mayo",
        "junho" to "junio", "julho" to "julio", "agosto" to "agosto", "setembro" to "septiembre",
        "outubro" to "octubre", "novembro" to "noviembre", "dezembro" to "diciembre",
    )
    private val DIAS = mapOf(
        "segunda-feira" to "lunes", "terca-feira" to "martes", "quarta-feira" to "miércoles", "quinta-feira" to "jueves",
        "sexta-feira" to "viernes", "sabado" to "sábado", "domingo" to "domingo",
    )
    private val STOP = setOf("de", "do", "da", "dos", "das", "e", "em", "com", "sem", "para", "por", "a", "o", "no", "na")
    private val RE_DATA = Regex("^(.*?)([A-Za-zçÇáÁ-]+),?\\s+(\\d{1,2}) de ([A-Za-zçÇ]+) de (\\d{4})$")
    private val RE_FAZENDAS = Regex("^(\\d+) fazendas?$")
    private val RE_VALID = Regex("^(.+?) (é obrigatóri[oa]|inválid[oa]s?|muito long[oa]|não encontrad[oa])\\.?$", RegexOption.IGNORE_CASE)

    private val PALAVRAS_ALL: Map<String, String> by lazy { IdiomaExtra2.PALAVRAS2 + IdiomaExtra.PALAVRAS }

    private fun traduzirPalavras(core: String): String? {
        if (core.length > 70 || core.any { it in "<>{}" }) return null
        if (core.none { it.isWhitespace() } && core.length <= 2) return null
        val parcial = core == core.uppercase() && Regex("[A-ZÀ-Ú]{3}").containsMatchIn(core)
        var conhecidas = 0
        val out = StringBuilder()
        for (p in Regex("\\s+|\\S+").findAll(core).map { it.value }) {
            if (p.isBlank()) { out.append(p); continue }
            val pre = p.takeWhile { it in "(\"'[" }
            val post = p.drop(pre.length).takeLastWhile { it in ")\"']\\.,;:!?" }
            val word = p.drop(pre.length).dropLast(post.length)
            if (word.none { it.isLetter() }) { out.append(p); continue }
            val key = norm(word).trimEnd('.', '/', '-')
            var hit = PALAVRAS_ALL[key] ?: PALAVRAS_ALL[key.replace(".", "")]
            if (hit == null && '-' in key) {
                val segs = key.split("-").map { PALAVRAS_ALL[it] }
                if (segs.all { it != null }) hit = segs.joinToString("-")
            }
            if (hit == null) {
                if (parcial) { out.append(p); continue }
                return null
            }
            if (key !in STOP) conhecidas++
            out.append(pre).append(if (parcial) hit.uppercase() else hit).append(post)
        }
        return if (conhecidas > 0) out.toString() else null
    }

    private fun traduzirCore(core: String): String? {
        FRASES[core]?.let { return it }
        FRASES_N[norm(core)]?.let { return matchCase(core, it) }
        SUFIXO.matchEntire(core)?.let { m ->
            val base = m.groupValues[1]
            (FRASES[base] ?: FRASES_N[norm(base)]?.let { matchCase(base, it) })?.let { return it + m.groupValues[2] }
        }
        RE_DATA.matchEntire(core)?.let { m ->
            val d = DIAS[norm(m.groupValues[2])]
            val mo = MESES[norm(m.groupValues[4])]
            if (d != null && mo != null) return "${m.groupValues[1]}$d, ${m.groupValues[3]} de $mo de ${m.groupValues[5]}"
        }
        RE_FAZENDAS.matchEntire(core)?.let { m ->
            val n = m.groupValues[1]
            return "$n finca" + if (n == "1") "" else "s"
        }
        Regex("^(\\S*\\s?)?(\\d+(?:[.,]\\d+)?)mm hoje$").matchEntire(core)?.let { m -> return "${m.groupValues[1]}${m.groupValues[2]}mm hoy" }
        Regex("^sem atualização desde (.+)$", RegexOption.IGNORE_CASE).matchEntire(core)?.let { m -> return "sin actualización desde ${m.groupValues[1]}" }
        RE_VALID.matchEntire(core)?.let { m ->
            val subj = FRASES[m.groupValues[1]] ?: FRASES_N[norm(m.groupValues[1])] ?: traduzirPalavras(m.groupValues[1])
            if (subj != null) {
                val kind = norm(m.groupValues[2])
                val f = kind.substringAfterLast(' ').let { it.endsWith("a") || it.endsWith("as") }
                val es = when {
                    kind.startsWith("e obrig") -> "es obligatori" + if (f) "a" else "o"
                    kind.startsWith("invalid") -> "inválid" + (if (f) "a" else "o") + (if (kind.endsWith("s")) "s" else "")
                    kind.startsWith("muito") -> "demasiado larg" + if (f) "a" else "o"
                    else -> "no encontrad" + if (f) "a" else "o"
                }
                return subj + " " + es + if (core.endsWith(".")) "." else ""
            }
        }
        if (core == core.uppercase() || core.length <= 40) {
            traduzirPalavras(core)?.let { return matchCase(core, it) }
        }
        return null
    }

    private val FRASES: Map<String, String> = mapOf(
    "Cancelar" to "Cancelar",
    "Excluir" to "Eliminar",
    "Opcional" to "Opcional",
    "Editar" to "Editar",
    "Valor" to "Valor",
    "Item" to "Ítem",
    "Status" to "Estado",
    "Ver" to "Ver",
    "Selecione" to "Seleccione",
    "Data" to "Fecha",
    "Unidade" to "Unidad",
    "Fazenda" to "Finca",
    "Fornecedor" to "Proveedor",
    "Período" to "Período",
    "Margem" to "Margen",
    "Todas as safras" to "Todas las zafras",
    "Todas as culturas" to "Todos los cultivos",
    "Base de Dados" to "Base de Datos",
    "Cultura" to "Cultivo",
    "Salvar" to "Guardar",
    "Custo total" to "Costo total",
    "Não vincular" to "No vincular",
    "Email" to "Correo",
    "E-mail" to "Correo electrónico",
    "Categoria" to "Categoría",
    "Entradas" to "Entradas",
    "Saídas" to "Salidas",
    "Conta" to "Cuenta",
    "Lançamentos" to "Asientos",
    "Origem" to "Origen",
    "De" to "De",
    "Até" to "Hasta",
    "Autorizado por" to "Autorizado por",
    "Remover item" to "Quitar ítem",
    "Adicionar item" to "Agregar ítem",
    "Sem fazenda" to "Sin finca",
    "Papel" to "Rol",
    "Não informado" to "No informado",
    "Emitir NFS-e" to "Emitir NFS-e",
    "Tentar novamente" to "Reintentar",
    "Nome da fazenda" to "Nombre de la finca",
    "Nome" to "Nombre",
    "Saldo" to "Saldo",
    "Descrição" to "Descripción",
    "Tipo" to "Tipo",
    "Vencimento" to "Vencimiento",
    "Custo/ha" to "Costo/ha",
    "Receita" to "Ingreso",
    "Imóvel" to "Inmueble",
    "Número" to "Número",
    "Emitente" to "Emisor",
    "Ações" to "Acciones",
    "Acoes" to "Acciones",
    "Emitente:" to "Emisor:",
    "Número/Série:" to "Número/Serie:",
    "Valor total:" to "Valor total:",
    "Qtd" to "Cant.",
    "Orçamentos" to "Presupuestos",
    "Todos os períodos" to "Todos los períodos",
    "Requisição" to "Requisición",
    "Itens" to "Ítems",
    "Total" to "Total",
    "Esta ação não pode ser desfeita." to "Esta acción no se puede deshacer.",
    "Produto" to "Producto",
    "Safra" to "Zafra",
    "Pendente" to "Pendiente",
    "Somente números" to "Solo números",
    "Abastecimento rápido" to "Abastecimiento rápido",
    "Depósito Central" to "Depósito Central",
    "Personalizar Início" to "Personalizar Inicio",
    "Ops, algo deu errado" to "Ups, algo salió mal",
    "Contrato" to "Contrato",
    "Contraparte" to "Contraparte",
    "Dias p/ vencer" to "Días p/ vencer",
    "Situação" to "Situación",
    "Situação da terra" to "Situación de la tierra",
    "Área total" to "Área total",
    "Voltar" to "Volver",
    "Arquivo CSV" to "Archivo CSV",
    "Configurações" to "Configuración",
    "Compartilhar" to "Compartir",
    "Adicionar à Tela de Início" to "Agregar a la pantalla de inicio",
    "Adicionar" to "Agregar",
    "Imprimir / Salvar PDF" to "Imprimir / Guardar PDF",
    "Receita total" to "Ingreso total",
    "Custo/sc" to "Costo/sc",
    "Custo/ton" to "Costo/ton",
    "Nenhuma fazenda cadastrada em Base de Dados ainda." to "Aún no hay fincas registradas en Base de Datos.",
    "Observações" to "Observaciones",
    "Observacoes" to "Observaciones",
    "Histórico" to "Historial",
    "Doc." to "Doc.",
    "Entrada" to "Entrada",
    "Saída" to "Salida",
    "Todos os imóveis" to "Todos los inmuebles",
    "Todas as contas" to "Todas las cuentas",
    "Saldo do período" to "Saldo del período",
    "Extrato Bancário" to "Extracto bancario",
    "Importar XML" to "Importar XML",
    "Nenhuma nota registrada." to "Ninguna nota registrada.",
    "Conferência da importação" to "Verificación de la importación",
    "+ Nova fazenda…" to "+ Nueva finca…",
    "+ Fazenda" to "+ Finca",
    "Possível duplicado" to "Posible duplicado",
    "Alocar custo para a fazenda" to "Asignar costo a la finca",
    "Copiar último lançamento" to "Copiar último asiento",
    "Todos os filtros" to "Todos los filtros",
    "Todos" to "Todos",
    "Todas" to "Todas",
    "Filtrar por período" to "Filtrar por período",
    "Bloco" to "Bloque",
    "Tabela" to "Tabla",
    "Filtros" to "Filtros",
    "Ver como blocos" to "Ver como bloques",
    "Ver como tabela" to "Ver como tabla",
    "Atualizar" to "Actualizar",
    "Arquivar" to "Archivar",
    "Lançar" to "Registrar",
    "Ver mapa" to "Ver mapa",
    "Restaurar padrão" to "Restaurar predeterminado",
    "Plano" to "Plan",
    "Faturas" to "Facturas",
    "Banco de dados" to "Base de datos",
    "Senha" to "Contraseña",
    "Quantidade" to "Cantidad",
    "Área" to "Área",
    "Taxa" to "Tasa",
    "Validade" to "Vigencia",
    "Imprimir" to "Imprimir",
    "Configurado" to "Configurado",
    "Devolver" to "Devolver",
    "Notificações" to "Notificaciones",
    "Início" to "Inicio",
    "Ir para o painel" to "Ir al panel",
    "Análises" to "Análisis",
    "Financeiro" to "Finanzas",
    "Agronômico" to "Agronómico",
    "Pessoas" to "Personas",
    "Não" to "No",
    "Sim, importar" to "Sí, importar",
    "Sim, recusar" to "Sí, rechazar",
    "Cadastrar fazenda" to "Registrar finca",
    "Incluir" to "Incluir",
    "Importação concluída" to "Importación completada",
    "Linha" to "Línea",
    "Motivo" to "Motivo",
    "Motivo:" to "Motivo:",
    "Módulo de destino" to "Módulo de destino",
    "Plataforma de origem" to "Plataforma de origen",
    "Não importar" to "No importar",
    "Trocar arquivo" to "Cambiar archivo",
    "Importar outro arquivo" to "Importar otro archivo",
    "Ver registros importados" to "Ver registros importados",
    "Selecione o módulo" to "Seleccione el módulo",
    "agora" to "ahora",
    "Versão" to "Versión",
    "Baixar APK" to "Descargar APK",
    "Nenhuma versão publicada ainda." to "Aún no hay versión publicada.",
    "Plano atual:" to "Plan actual:",
    "Sino no app" to "Campana en la app",
    "Salvar notificações" to "Guardar notificaciones",
    "Mínimo" to "Mínimo",
    "Pedido rápido" to "Pedido rápido",
    "Saldo por Item" to "Saldo por ítem",
    "Pedida" to "Pedida",
    "Entregue" to "Entregada",
    "A receber" to "A recibir",
    "Em estoque" to "En existencia",
    "Aplicado" to "Aplicado",
    "Controle de Insumos" to "Control de Insumos",
    "Olá, bem-vindo de volta" to "Hola, bienvenido de nuevo",
    "Clima agora" to "Clima ahora",
    "Câmbio" to "Cambio",
    "Tempo real" to "Tiempo real",
    "Dólar" to "Dólar",
    "Euro" to "Euro",
    "Peso arg." to "Peso arg.",
    "Destaques" to "Destacados",
    "Atualização semanal" to "Actualización semanal",
    "Atualizado:" to "Actualizado:",
    "Dossiê Bancário" to "Dossier Bancario",
    "Resumo Executivo" to "Resumen Ejecutivo",
    "Nenhuma fazenda cadastrada." to "Ninguna finca registrada.",
    "Contratos Ativos" to "Contratos Activos",
    "Valor contábil" to "Valor contable",
    "Saldo do ano" to "Saldo del año",
    "Depreciação acumulada" to "Depreciación acumulada",
    "Custo / ha" to "Costo / ha",
    "Custo / sc" to "Costo / sc",
    "Custo / ton" to "Costo / ton",
    "Taxa de Prenhez" to "Tasa de preñez",
    "Custo por Arroba" to "Costo por arroba",
    "Média por Ordenha" to "Promedio por ordeño",
    "Composição do Custo por Categoria" to "Composición del costo por categoría",
    "Drone" to "Dron",
    "Talhão" to "Lote",
    "Talhao" to "Lote",
    "Tipo de Captura" to "Tipo de captura",
    "Piloto" to "Piloto",
    "Abrir arquivo" to "Abrir archivo",
    "Importar KML/KMZ" to "Importar KML/KMZ",
    "Lançar talhão" to "Registrar lote",
    "Grade" to "Cuadrícula",
    "Mapa" to "Mapa",
    "Máquinas" to "Máquinas",
    "Salvar área desenhada" to "Guardar área dibujada",
    "Banco" to "Banco",
    "Confirmar Importação" to "Confirmar importación",
    "Selecione o banco" to "Seleccione el banco",
    "Totais do período" to "Totales del período",
    "Livro Caixa do Produtor Rural" to "Libro Caja del Productor Rural",
    "Imprimir / PDF" to "Imprimir / PDF",
    "Saldo inicial do período" to "Saldo inicial del período",
    "Aplicar" to "Aplicar",
    "Total de entradas" to "Total de entradas",
    "Total de saídas" to "Total de salidas",
    "Resumo por imóvel rural" to "Resumen por inmueble rural",
    "Resumo por conta" to "Resumen por cuenta",
    "Resumo mensal" to "Resumen mensual",
    "Mês" to "Mes",
    "Saldo acumulado" to "Saldo acumulado",
    "Copiar última nota" to "Copiar última nota",
    "Fechar" to "Cerrar",
    "Baixar XMLs em lote" to "Descargar XML en lote",
    "Operações" to "Operaciones",
    "Faturado" to "Facturado",
    "Pendente NF" to "Pendiente NF",
    "Limpar" to "Limpiar",
    "Mostrar campos" to "Mostrar campos",
    "Foto da requisição" to "Foto de la requisición",
    "Valor unitário *" to "Valor unitario *",
    "Status de entrega" to "Estado de entrega",
    "Cobranças e NFS-e" to "Cobros y NFS-e",
    "Cobranças" to "Cobros",
    "Irrigando" to "Regando",
    "Parado" to "Detenido",
    "Offline" to "Sin conexión",
    "Pivôs de Irrigação" to "Pivotes de Riego",
    "Novo pivô" to "Nuevo pivote",
    "Nenhum pivô cadastrado ainda." to "Aún no hay pivotes registrados.",
    "Lançar telemetria" to "Registrar telemetría",
    "Controle" to "Control",
    "Nome do pivô" to "Nombre del pivote",
    "Marca / fabricante" to "Marca / fabricante",
    "Sentido de rotação" to "Sentido de rotación",
    "Registrar" to "Registrar",
    "Prescrição / Taxa Variável" to "Prescripción / Tasa Variable",
    "Nova prescrição" to "Nueva prescripción",
    "Unidade da taxa" to "Unidad de la tasa",
    "Prescrições salvas" to "Prescripciones guardadas",
    "Nenhuma prescrição importada ainda." to "Aún no hay prescripciones importadas.",
    "Reconciliação Físico x Fiscal" to "Conciliación Físico x Fiscal",
    "Saldo Fiscal" to "Saldo Fiscal",
    "Saldo Real" to "Saldo Real",
    "Diferença" to "Diferencia",
    "Ver dados" to "Ver datos",
    "Segurança e Acessos" to "Seguridad y Accesos",
    "Usuário" to "Usuario",
    "Ativo" to "Activo",
    "Dispositivos conhecidos" to "Dispositivos conocidos",
    "Dispositivo" to "Dispositivo",
    "Todos os módulos" to "Todos los módulos",
    "Ação" to "Acción",
    "Módulo" to "Módulo",
    "Quem" to "Quién",
    "Detalhes" to "Detalles",
    "Nenhuma alteração registrada ainda." to "Aún no hay cambios registrados.",
    "Editar permissões" to "Editar permisos",
    "Crítico" to "Crítico",
    "Atenção" to "Atención",
    "Vazio" to "Vacío",
    "Normal" to "Normal",
    "Sem dados" to "Sin datos",
    "Silos cilíndricos" to "Silos cilíndricos",
    "Novo silo" to "Nuevo silo",
    "Silos ativos" to "Silos activos",
    "Armazenado / capacidade" to "Almacenado / capacidad",
    "Silos em atenção/crítico" to "Silos en atención/crítico",
    "Nenhum silo cadastrado ainda." to "Aún no hay silos registrados.",
    "Lançar leitura" to "Registrar lectura",
    "Nome do silo" to "Nombre del silo",
    "Fabricante" to "Fabricante",
    "Umidade (%)" to "Humedad (%)",
    "Todos os silos" to "Todos los silos",
    "Simulador de Cenários \"E se?\"" to "Simulador de Escenarios \"¿Y si?\"",
    "Premissas do Cenário" to "Supuestos del Escenario",
    "Resultado Projetado" to "Resultado Proyectado",
    "Variação de preço de venda" to "Variación del precio de venta",
    "Variação de produtividade" to "Variación de productividad",
    "Custo" to "Costo",
    "Teste expirado" to "Prueba expirada",
    "Em dia" to "Al día",
    "Clientes pagantes" to "Clientes pagantes",
    "Em teste" to "En prueba",
    "Clientes" to "Clientes",
    "Organização" to "Organización",
    "Dono" to "Dueño",
    "Mensalidade" to "Mensualidad",
    "Editar plano" to "Editar plan",
    "Nova fatura" to "Nueva factura",
    "Documentos" to "Documentos",
    "Boleto" to "Boleto",
    "Marcar pago" to "Marcar pagado",
    "Apagar" to "Borrar",
    "Período de teste encerrado" to "Período de prueba finalizado",
    "Falar sobre planos" to "Hablar sobre planes",
    "Sair" to "Salir",
    "Mínimo de 6 caracteres." to "Mínimo 6 caracteres.",
    "Já tem uma conta? Entrar" to "¿Ya tiene cuenta? Ingresar",
    "Termos de Uso" to "Términos de Uso",
    "Política de Privacidade" to "Política de Privacidad",
    "Recuperar senha" to "Recuperar contraseña",
    "Voltar ao login" to "Volver al inicio de sesión",
    "Criar conta gratuita" to "Crear cuenta gratuita",
    "Entre com sua conta para continuar" to "Ingrese con su cuenta para continuar",
    "Vamos começar" to "Comencemos",
    "Nome da fazenda/empresa" to "Nombre de la finca/empresa",
    "Ir para o Início" to "Ir al Inicio",
    "Convide sua equipe" to "Invite a su equipo",
    "Pular esta etapa" to "Omitir este paso",
    "Continuar" to "Continuar",
    "Nenhuma fazenda ativa cadastrada ainda." to "Aún no hay fincas activas registradas.",
    "Custo médio/ha" to "Costo promedio/ha",
    "Nenhum alerta no momento. Tudo em dia." to "Ninguna alerta por ahora. Todo al día.",
    "Novo" to "Nuevo",
    "Nenhum aviso publicado." to "Ningún aviso publicado.",
    "Geral" to "General",
    "Do meu setor" to "De mi sector",
    "Título" to "Título",
    "Mensagem" to "Mensaje",
    "Fixar no topo" to "Fijar arriba",
    "Insights — o que merece atenção" to "Insights — lo que merece atención",
    "Sistema" to "Sistema",
    "Peso bruto" to "Peso bruto",
    "Peso líquido" to "Peso neto",
    "Umidade real" to "Humedad real",
    "Umidade padrão" to "Humedad estándar",
    "Impureza" to "Impureza",
    "Produtividade" to "Productividad",
    "Toneladas" to "Toneladas",
    "Semeadura" to "Siembra",
    "Espaçamento" to "Espaciamiento",
    "Germinação" to "Germinación",
    "Área a plantar" to "Área a sembrar",
    "Pulverização" to "Pulverización",
    "Velocidade" to "Velocidad",
    "Vazão" to "Caudal",
    "Calda total" to "Caldo total",
    "Adubação" to "Fertilización",
    "Dose" to "Dosis",
    "Juros compostos" to "Interés compuesto",
    "Capital inicial" to "Capital inicial",
    "Montante final" to "Monto final",
    "Juros totais" to "Intereses totales",
    "Valor financiado" to "Valor financiado",
    "Nº de parcelas" to "N.º de cuotas",
    "Total pago" to "Total pagado",
    "Sem validade informada" to "Sin vigencia informada",
    "Não cadastrado" to "No registrado",
    "Condição de pagamento" to "Condición de pago",
    "Adicionar fornecedor" to "Agregar proveedor",
    "Todos os bancos" to "Todos los bancos",
    "Filtros ativos:" to "Filtros activos:",
    "Limpar tudo" to "Limpiar todo",
    "Pendentes de revisão" to "Pendientes de revisión",
    "Carregando gráficos..." to "Cargando gráficos...",
    "Nenhum gráfico disponível para este módulo ainda." to "Aún no hay gráficos para este módulo.",
    "Lançar nota com itens" to "Registrar nota con ítems",
    "Fornecedor:" to "Proveedor:",
    "Data:" to "Fecha:",
    "Local:" to "Lugar:",
    "Total da nota:" to "Total de la nota:",
    "Carregando detalhes..." to "Cargando detalles...",
    "Concluído" to "Completado",
    "Atrasado" to "Atrasado",
    "Realizado" to "Realizado",
    "Planejado" to "Planificado",
    "Variação" to "Variación",
    "Editar em Financeiro" to "Editar en Finanzas",
    "Ver/editar em Estoque" to "Ver/editar en Existencias",
    "Editar em Safra" to "Editar en Zafra",
    "Setor" to "Sector",
    "Data de entrega" to "Fecha de entrega",
    "Inscrição municipal" to "Inscripción municipal",
    "Optante pelo Simples Nacional" to "Adherido al Simples Nacional",
    "Nenhuma selecionada" to "Ninguna seleccionada",
    "Selecione a conta" to "Seleccione la cuenta",
    "Não conectado" to "No conectado",
    "Provedor" to "Proveedor",
    "Ambiente" to "Ambiente",
    "Selecione o provedor" to "Seleccione el proveedor",
    "Selecione o ambiente" to "Seleccione el ambiente",
    "Combustível" to "Combustible",
    "Horímetro" to "Horómetro",
    "Selecione a máquina" to "Seleccione la máquina",
    "Sem local específico" to "Sin lugar específico",
    "Foto" to "Foto",
    "Diagnóstico por foto" to "Diagnóstico por foto",
    "Diagnóstico por Foto" to "Diagnóstico por Foto",
    "Romaneio rápido" to "Romaneo rápido",
    "QR Code de Rastreabilidade" to "Código QR de Trazabilidad",
    "Transferir pra fazenda" to "Transferir a finca",
    "Nenhum lote de colheita lançado ainda." to "Aún no hay lotes de cosecha registrados.",
    "Recalcular Área" to "Recalcular área",
    "Recalcular Vencimentos" to "Recalcular vencimientos",
    "Nenhum resultado" to "Sin resultados",
    "Ajuste manual" to "Ajuste manual",
    "Ajuste manual de estoque" to "Ajuste manual de existencias",
    "Saldo por fazenda" to "Saldo por finca",
    "Nenhuma transferência lançada ainda." to "Aún no hay transferencias registradas.",
    "Ver tudo" to "Ver todo",
    "Módulos" to "Módulos",
    "Aplicativo mobile" to "Aplicación móvil",
    "Mais opções" to "Más opciones",
    "Baixar aplicativo mobile" to "Descargar aplicación móvil",
    "Exibir valores em" to "Mostrar valores en",
    "Conversor" to "Conversor",
    "Moeda e conversor" to "Moneda y conversor",
    "Moeda de origem" to "Moneda de origen",
    "Filtrar por fazenda" to "Filtrar por finca",
    "Todas as fazendas" to "Todas las fincas",
    "Busca global" to "Búsqueda global",
    "Nenhum módulo encontrado." to "Ningún módulo encontrado.",
    "Recentes" to "Recientes",
    "Buscar" to "Buscar",
    "Buscar módulo..." to "Buscar módulo...",
    "Nenhuma notificação por enquanto." to "Sin notificaciones por ahora.",
    "Marcar todas como lidas" to "Marcar todas como leídas",
    "Descartar lançamento" to "Descartar asiento",
    "Operacional" to "Operativo",
    "Gestão" to "Gestión",
    "Backup" to "Copia de seguridad",
    "Adicionar logo da empresa" to "Agregar logo de la empresa",
    "Entrar" to "Ingresar",
    "Carregando..." to "Cargando...",
    "Novo lançamento" to "Nuevo asiento",
    "Nenhum registro ainda." to "Aún no hay registros.",
    "Pesquisar" to "Buscar",
    "Exportar" to "Exportar",
    "Importar" to "Importar",
    "Confirmar" to "Confirmar",
    "Próximo" to "Siguiente",
    "Anterior" to "Anterior",
    "Sim" to "Sí",
    "Conectado" to "Conectado",
    "Pendente de conexão" to "Pendiente de conexión",
    "Testar conexão" to "Probar conexión",
    "Desconectar" to "Desconectar",
    "Idioma" to "Idioma",
    "Moeda" to "Moneda",
    "Tema" to "Tema",
    "Painel" to "Panel",
    "Perfil" to "Perfil",
    "Meu perfil" to "Mi perfil",
    "Identidade visual" to "Identidad visual",
    "Alterar foto" to "Cambiar foto",
    "Sair da conta" to "Cerrar sesión",
    "Clima" to "Clima",
    "Chuva" to "Lluvia",
    "Temperatura" to "Temperatura",
    "Umidade" to "Humedad",
    "Vento" to "Viento",
    "Previsão" to "Pronóstico",
    "Cotações" to "Cotizaciones",
    "Cotações Agrícolas" to "Cotizaciones Agrícolas",
    "Cotações Pecuária" to "Cotizaciones Ganaderas",
    "Soja" to "Soja",
    "Milho" to "Maíz",
    "Sorgo" to "Sorgo",
    "Boi Gordo" to "Novillo Gordo",
    "Bezerro" to "Ternero",
    "Leite" to "Leche",
    "Alertas" to "Alertas",
    "Central de Alertas" to "Central de Alertas",
    "Monitor em tempo real" to "Monitor en tiempo real",
    "Mural de Avisos" to "Mural de Avisos",
    "Lembretes" to "Recordatorios",
    "Fazendas cadastradas" to "Fincas registradas",
    "Estágio da safra" to "Etapa de la zafra",
    "Gráficos" to "Gráficos",
    "Processando..." to "Procesando...",
    "Excluir lançamento?" to "¿Eliminar asiento?",
    "Essa ação não pode ser desfeita." to "Esta acción no se puede deshacer.",
    "Lançamento não encontrado." to "Asiento no encontrado.",
    "Sem conexão -- não foi possível carregar." to "Sin conexión -- no se pudo cargar.",
    "Instalar no iPhone/iPad" to "Instalar en iPhone/iPad",
    "Baixar" to "Descargar",
    "Módulos liberados" to "Módulos habilitados",
    "Sem dados." to "Sin datos.",
    "Importar padrões" to "Importar predeterminados",
    "Recusar" to "Rechazar",
    "Importar dados padrão?" to "¿Importar datos predeterminados?",
    "Nova fazenda" to "Nueva finca",
    "Sincronizar locais" to "Sincronizar lugares",
    "Novo valor" to "Nuevo valor",
    "Prazo (dias)" to "Plazo (días)",
    "Nova Cotação" to "Nueva Cotización",
    "Cotação lançada com sucesso" to "Cotización registrada con éxito",
    "Lançar outra cotação" to "Registrar otra cotización",
    "Migração de Dados (CSV)" to "Migración de Datos (CSV)",
    "Escolher arquivo CSV" to "Elegir archivo CSV",
    "Importando..." to "Importando...",
    "Importar outro extrato" to "Importar otro extracto",
    "Campo obrigatório" to "Campo obligatorio",
    "Faturamento" to "Facturación",
    "Janela de Pulverização" to "Ventana de Pulverización",
    "Transferências entre Fazendas" to "Transferencias entre Fincas",
    "Transferir" to "Transferir",
    "Saída de estoque" to "Salida de existencias",
    "Registrar saída" to "Registrar salida",
    "Registrar ajuste" to "Registrar ajuste",
    "Confirmar devolução" to "Confirmar devolución",
    "Itens da nota *" to "Ítems de la nota *",
    "Nenhum lançamento nesta visão." to "Ningún asiento en esta vista.",
    "Filtrar por banco" to "Filtrar por banco",
    "Eficiência de Frota (L/h)" to "Eficiencia de Flota (L/h)",
    "QR Codes das máquinas" to "Códigos QR de las máquinas",
    "Litros *" to "Litros *",
    "Sem dados para este gráfico." to "Sin datos para este gráfico.",
    "Resultado por Fazenda (DRE)" to "Resultado por Finca (DRE)",
    "Detalhamento de custos" to "Detalle de costos",
    "DRE" to "DRE",
    "Totais" to "Totales",
    "Indicadores Zootécnicos (Pecuária)" to "Indicadores Zootécnicos (Ganadería)",
    "Composição de custo por categoria" to "Composición de costo por categoría",
    "Novo registro de drone" to "Nuevo registro de dron",
    "Altitude (m)" to "Altitud (m)",
    "Área coberta (ha)" to "Área cubierta (ha)",
    "Só divergências" to "Solo divergencias",
    "Máquina" to "Máquina",
    "Lançar talhão manualmente" to "Registrar lote manualmente",
    "Talhão (número/nome) *" to "Lote (número/nombre) *",
    "Nome (opcional)" to "Nombre (opcional)",
    "Nova Prescrição" to "Nueva Prescripción",
    "Prescrição salva com sucesso" to "Prescripción guardada con éxito",
    "Lançar outra" to "Registrar otra",
    "Voltar à lista" to "Volver a la lista",
    "Salvar prescrição" to "Guardar prescripción",
    "Não foi possível exportar" to "No se pudo exportar",
    "Taxa mín." to "Tasa mín.",
    "Taxa média" to "Tasa media",
    "Taxa máx." to "Tasa máx.",
    "Acessos" to "Accesos",
    "Baixar para Android" to "Descargar para Android",
    "Entendi" to "Entendido",
    "Sincronizar agora" to "Sincronizar ahora",
    "Nenhuma notificação ainda." to "Aún no hay notificaciones.",
    "Lançamentos aguardando sincronizar" to "Asientos pendientes de sincronizar",
    "Nenhum lançamento pendente." to "Ningún asiento pendiente.",
    "Cotações indisponíveis no momento" to "Cotizaciones no disponibles por ahora",
    "(desatualizado)" to "(desactualizado)",
    "Pular" to "Omitir",
    "Resumo" to "Resumen",
    "Saldo por item (top 10)" to "Saldo por ítem (top 10)",
    "Situação consolidada por item" to "Situación consolidada por ítem",
    "Produtor Rural / IRPF" to "Productor Rural / IRPF",
    "CNPJ" to "CNPJ",
    "CPF do produtor" to "CPF del productor",
    "Inscrição Estadual" to "Inscripción Estatal",
    "Emissor (AC)" to "Emisor (AC)",
    "Conta padrão para IRPF" to "Cuenta predeterminada para IRPF",
    "Livro Caixa" to "Libro Caja",
    "Esqueci minha senha" to "Olvidé mi contraseña",
    "Ainda não tem conta? Criar conta gratuita" to "¿Aún no tiene cuenta? Crear cuenta gratuita",
    "Importar NF-e (XML)" to "Importar NF-e (XML)",
    "Nota importada com sucesso" to "Nota importada con éxito",
    "Lendo o XML..." to "Leyendo el XML...",
    "Pré-visualização" to "Vista previa",
    "Confirmar importação" to "Confirmar importación",
    "Série" to "Serie",
    "Valor total *" to "Valor total *",
    "Data de emissão" to "Fecha de emisión",
    "Baixar XMLs (lote)" to "Descargar XML (lote)",
    "NF-e" to "NF-e",
    "Nenhuma nota fiscal lançada ainda." to "Aún no hay notas fiscales registradas.",
    "Excluir nota fiscal?" to "¿Eliminar nota fiscal?",
    "Valor unitário" to "Valor unitario",
    "Novo Orçamento" to "Nuevo Presupuesto",
    "Orçamento lançado com sucesso" to "Presupuesto registrado con éxito",
    "Lançar outro orçamento" to "Registrar otro presupuesto",
    "Voltar para a lista" to "Volver a la lista",
    "Lançar orçamento" to "Registrar presupuesto",
    "Qtd. pedida *" to "Cant. pedida *",
    "Qtd. entregue" to "Cant. entregada",
    "Novo Pedido" to "Nuevo Pedido",
    "Pedido lançado com sucesso" to "Pedido registrado con éxito",
    "Lançar outro pedido" to "Registrar otro pedido",
    "Nº do pedido *" to "N.º del pedido *",
    "Itens do pedido *" to "Ítems del pedido *",
    "Lançar pedido" to "Registrar pedido",
    "Balanço Hídrico (30 dias)" to "Balance Hídrico (30 días)",
    "Telemetria" to "Telemetría",
    "Raio irrigado (m)" to "Radio regado (m)",
    "Lâmina aplicada (mm) *" to "Lámina aplicada (mm) *",
    "Lâmina desejada (mm)" to "Lámina deseada (mm)",
    "Diagnóstico da IA" to "Diagnóstico de la IA",
    "Observações (opcional)" to "Observaciones (opcional)",
    "Lançar outra ocorrência" to "Registrar otra ocurrencia",
    "Nº Romaneio *" to "N.º Romaneo *",
    "Peso Bruto (kg) *" to "Peso Bruto (kg) *",
    "Tara (kg) *" to "Tara (kg) *",
    "Impureza (%)" to "Impureza (%)",
    "Lançar outro romaneio" to "Registrar otro romaneo",
    "Acessos e Segurança" to "Accesos y Seguridad",
    "Salvar permissões" to "Guardar permisos",
    "Convidar colaborador" to "Invitar colaborador",
    "Enviar convite" to "Enviar invitación",
    "Baixando..." to "Descargando...",
    "Gerenciar assinatura" to "Gestionar suscripción",
    "Estoque armazenado (t, 30 dias)" to "Existencias almacenadas (t, 30 días)",
    "Leitura" to "Lectura",
    "Capacidade (t)" to "Capacidad (t)",
    "Diâmetro (m)" to "Diámetro (m)",
    "Altura (m)" to "Altura (m)",
    "Nível (%) *" to "Nivel (%) *",
    "Temperatura (°C)" to "Temperatura (°C)",
    "Simulador \"E se?\"" to "Simulador \"¿Y si?\"",
    "Atualizado a cada 30 min · Fonte: Open-Meteo" to "Actualizado cada 30 min · Fuente: Open-Meteo",
    "Atualizado a cada 15 min · Fonte: AwesomeAPI" to "Actualizado cada 15 min · Fuente: AwesomeAPI",
    "Peso:" to "Peso:",
    "Conversor BRL / U\$D / ARS" to "Conversor BRL / U\$D / ARS",
    "Fechar conversor" to "Cerrar conversor",
    "Nenhum registro ainda. Toque no botão para adicionar." to "Aún no hay registros. Toque el botón para agregar.",
    "Nenhum pivô cadastrado ainda. Toque no botão para adicionar." to "Aún no hay pivotes registrados. Toque el botón para agregar.",
    "Nenhum silo cadastrado ainda. Toque no botão para adicionar." to "Aún no hay silos registrados. Toque el botón para agregar.",
    "Nenhuma máquina/frota cadastrada em Base de Dados ainda." to "Aún no hay máquinas/flota registradas en Base de Datos.",
    "Sem lançamentos nos últimos 30 dias." to "Sin asientos en los últimos 30 días.",
    "Aguardando atividade da equipe..." to "Esperando actividad del equipo...",
    "Português (Brasil)" to "Português (Brasil)",
    "Español" to "Español"
    )
}

@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current,
) {
    androidx.compose.material3.Text(
        text = Idioma.tr(text), modifier = modifier, color = color, fontSize = fontSize, fontStyle = fontStyle,
        fontWeight = fontWeight, fontFamily = fontFamily, letterSpacing = letterSpacing, textDecoration = textDecoration,
        textAlign = textAlign, lineHeight = lineHeight, overflow = overflow, softWrap = softWrap, maxLines = maxLines,
        minLines = minLines, onTextLayout = onTextLayout, style = style,
    )
}

@Composable
fun Text(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current,
) {
    androidx.compose.material3.Text(
        text = text, modifier = modifier, color = color, fontSize = fontSize, fontStyle = fontStyle,
        fontWeight = fontWeight, fontFamily = fontFamily, letterSpacing = letterSpacing, textDecoration = textDecoration,
        textAlign = textAlign, lineHeight = lineHeight, overflow = overflow, softWrap = softWrap, maxLines = maxLines,
        minLines = minLines, onTextLayout = onTextLayout, style = style,
    )
}
