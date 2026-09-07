package com.bragro.mobile.ui.domain

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bragro.mobile.data.local.LookupEntity
import com.bragro.mobile.data.model.ColumnConfig
import com.bragro.mobile.data.model.DomainConfig
import com.bragro.mobile.data.repo.ConfigRepository
import com.bragro.mobile.data.repo.ModuleActionsRepository
import com.bragro.mobile.data.repo.RecordRepository
import com.bragro.mobile.data.repo.SaveResult
import com.bragro.mobile.ui.theme.BrGreen
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

class DomainFormViewModel(app: Application) : AndroidViewModel(app) {
    private val configRepository = ConfigRepository(app)
    private val recordRepository = RecordRepository(app)

    var config = mutableStateOf<DomainConfig?>(null)
        private set
    var lookupsByCategory = mutableStateOf<Map<String, List<LookupEntity>>>(emptyMap())
        private set
    var saving = mutableStateOf(false)
        private set
    var errorMessage = mutableStateOf<String?>(null)
        private set
    var offlineNotice = mutableStateOf<String?>(null)
        private set
    // "Copiar último lançamento" (Task #51/#77) -- só faz sentido ao CRIAR
    // (recordId == null); guarda o registro mais recente do módulo pra poder
    // pré-preencher o formulário com um toque, mesmo mecanismo genérico do
    // site (data-table.tsx), sem exceção por domínio.
    var lastRecord = mutableStateOf<Map<String, String?>?>(null)
        private set
    // Campos calculados pelo servidor (rateio, vencimento, líquido, status
    // etc.) -- só leitura, NUNCA entram em `fields` (que é o que vai no
    // corpo do save()), mesmo critério do site (record-form.tsx): aparecem
    // na tela pra consulta, "—" até o primeiro Salvar num lançamento novo,
    // mas quem preenche de verdade é o service layer do servidor. Pedido do
    // usuário: "coloque todos os campos até os que são calculados
    // automaticamente na ordem correta da operação em todos os módulos".
    var computedValues = mutableStateOf<Map<String, String>>(emptyMap())
        private set

    // Fazendas reais (cache local, ver ConfigRepository.farms()) -- pedido
    // do usuário ("em pragas o campo fazenda... deixar apenas fazendas e
    // excluir as que já não existem mais" / "em clima adicione na lista
    // suspensa apenas as fazendas habilitadas"). O campo "fazenda" (só
    // existe em Pragas e Clima, ver registry.ts) usava lookupCategory
    // "locais" -- uma lista de texto livre digitada manualmente em Base de
    // Dados, sem NENHUMA relação com o cadastro real de fazendas, então
    // nunca refletia exclusão/desativação. /api/mobile/bootstrap já filtra
    // `ativo: true` ao montar essa lista (ver route.ts), então usar farms
    // aqui resolve os dois pedidos de uma vez, sempre em dia sozinho.
    var farms = mutableStateOf<List<com.bragro.mobile.data.local.FarmEntity>>(emptyList())
        private set

    val fields = mutableStateMapOf<String, String>()

    // Guarda domainId/recordId da carga atual -- preview de O.S. (ver
    // refreshOsPreview()) precisa deles fora de load() pra poder ser
    // rechamado quando o usuário troca a fazenda (setField("local")),
    // mesmo comportamento reativo do site (useEffect [localValue] em
    // record-form.tsx).
    private var currentDomainId: String? = null
    private var currentRecordId: String? = null

    fun load(domainId: String, recordId: String?) {
        currentDomainId = domainId
        currentRecordId = recordId
        viewModelScope.launch {
            val cfg = configRepository.domainConfig(domainId) ?: return@launch
            config.value = cfg

            // Categorias de TODO campo: as diretas (lookupCategory) e, pra
            // campos dependentes (dependsOn + lookupCategoryByValue, ver
            // ColumnConfig em Models.kt), todas as categorias que o mapa
            // pode escolher -- pré-carrega tudo de uma vez (mesmo se o
            // usuário ainda não escolheu o campo do qual depende).
            val categories = (
                cfg.columns.mapNotNull { it.lookupCategory } +
                    cfg.columns.flatMap { it.lookupCategoryByValue?.values.orEmpty() }
                ).distinct()
            val loaded = mutableMapOf<String, List<LookupEntity>>()
            for (cat in categories) loaded[cat] = configRepository.lookupsByCategory(cat)
            lookupsByCategory.value = loaded

            // Só busca se o domínio realmente tem um campo "fazenda" (Pragas/
            // Clima) -- evita a query à toa nos outros 14 módulos.
            if (cfg.columns.any { it.key == "fazenda" }) {
                farms.value = configRepository.farms()
            }

            fields.clear()
            val existing = recordId?.let { recordRepository.getRecord(domainId, it) }
            val computed = mutableMapOf<String, String>()
            for (col in cfg.columns) {
                // Checkbox num registro NOVO (existing == null) nasce com
                // col.defaultChecked, não "" -- pedido do usuário ("erro 400
                // no offline-sync mobile"): "" era serializado e mandado pro
                // servidor como valor real do campo (em vez de a chave ficar
                // ausente, como um <input type=checkbox> desmarcado faria no
                // HTML), o que virava `null` num Boolean NOT NULL do Prisma
                // e derrubava a sincronização com 400 (ver parseFormData em
                // actions.ts, fix irmão deste). Mesma lógica do
                // defaultChecked:true do site (record-form.tsx).
                val raw = existing?.get(col.key)
                    ?: if (col.type == "checkbox") col.defaultChecked.toString() else ""
                if (col.computed) {
                    computed[col.key] = raw
                    continue
                }
                // Datas chegam do servidor como timestamp ISO completo
                // ("2026-08-05T00:00:00.000Z") -- mostrar isso cru no campo
                // de edição é o que o usuário reportou primeiro como "as
                // datas estão incorretas junto com elas tem fuso horário", e
                // depois como "as datas estão com padrão americano" (mesmo
                // corte, só que AAAA-MM-DD ainda lê como ano-mês-dia, fora
                // do costume brasileiro). Mostra em DD/MM/AAAA no campo; a
                // conversão de volta pra ISO só acontece em save().
                fields[col.key] = if (col.type == "date") isoDateToBr(isoDateOnly(raw)) else raw
            }
            computedValues.value = computed

            lastRecord.value = if (recordId == null) recordRepository.mostRecent(domainId) else null

            // Preview do próximo número de O.S. num lançamento NOVO (pedido
            // do usuário: "aplique tambem no app o que foi aplicado na
            // plataforma do preenchimento automático da O.S. posterior") --
            // mesmo critério do site (record-form.tsx): só dispara se o
            // domínio de fato tem a coluna "os" (evita chamada à toa nos
            // ~13 módulos que não usam O.S.; o servidor também confere
            // OS_AUTO_DOMAINS, mas checar aqui evita a requisição inteira).
            // Coroutine separada pra não atrasar a exibição do formulário
            // enquanto o preview ainda não voltou.
            if (recordId == null && cfg.columns.any { it.key == "os" }) {
                refreshOsPreview(overwrite = false)
            }
        }
    }

    /** Recalcula o preview do próximo número de O.S., agora considerando a
     * fazenda atual do campo "local" (pedido do usuário: "uma sequência de
     * O.S. por fazenda") -- mesma Server Action do site (previewNextOsAction),
     * chamada tanto na carga inicial (load()) quanto de novo sempre que o
     * usuário troca a fazenda (setField("local")), pra manter paridade com o
     * comportamento reativo do site (useEffect [localValue] + key={osPreview}
     * em record-form.tsx, que troca o número exibido a cada troca de
     * fazenda). "overwrite=false" (carga inicial) só preenche se "os" ainda
     * estiver vazio; "overwrite=true" (troca de fazenda) sempre substitui,
     * igual o remount por key do site. */
    private fun refreshOsPreview(overwrite: Boolean) {
        val domainId = currentDomainId ?: return
        if (currentRecordId != null) return
        val cfg = config.value ?: return
        if (cfg.columns.none { it.key == "os" }) return
        viewModelScope.launch {
            val preview = ModuleActionsRepository(getApplication()).run(
                "preview-next-os", domainId = domainId, local = fields["local"],
            )
            val os = (preview?.get("os") as? JsonPrimitive)?.contentOrNull
            if (!os.isNullOrBlank() && (overwrite || fields["os"].isNullOrBlank())) {
                fields["os"] = os
            }
        }
    }

    fun setField(key: String, value: String) {
        fields[key] = value
        // Campos que dependem deste (dependsOn, ver ColumnConfig em
        // Models.kt) têm a seleção atual limpa -- ela pode não existir mais
        // na lista filtrada pra o novo valor (mesmo comportamento do site,
        // ver handleSelectChange em record-form.tsx). Ex.: trocar a
        // Categoria do Controle Interno (EPI/MATERIAL DE LIMPEZA/MATERIAL
        // DE ESCRITÓRIO) esvazia o Item já escolhido.
        val dependents = config.value?.columns?.filter { it.dependsOn == key }.orEmpty()
        for (dep in dependents) {
            if (fields[dep.key]?.isNotEmpty() == true) fields[dep.key] = ""
        }
        // Some o aviso vermelho desse campo específico assim que o usuário
        // começa a corrigir -- sem isso ficava marcado até o próximo Salvar.
        if (value.isNotBlank() && key in missingFields.value) {
            missingFields.value = missingFields.value - key
        }
        // Troca de fazenda ("local") num lançamento novo: recalcula o
        // próximo número de O.S. NA SEQUÊNCIA DAQUELA FAZENDA (ver
        // refreshOsPreview() acima).
        if (key == "local") {
            refreshOsPreview(overwrite = true)
        }
    }

    /** Categoria de lookup efetiva pra um campo -- se o campo tem dependsOn
     * + lookupCategoryByValue (ver ColumnConfig em Models.kt), usa o valor
     * atual do campo do qual ele depende pra escolher qual lista mostrar
     * (cai em lookupCategory normal se esse campo ainda não tem valor, ou o
     * valor não está no mapa). Mesma lógica de effectiveLookupCategory em
     * record-form.tsx (site). */
    fun effectiveLookupCategory(col: ColumnConfig): String? {
        val dependsOn = col.dependsOn
        val byValue = col.lookupCategoryByValue
        if (dependsOn != null && byValue != null) {
            val depVal = fields[dependsOn]
            if (!depVal.isNullOrBlank()) {
                byValue[depVal]?.let { return it }
            }
        }
        return col.lookupCategory
    }

    /** Opções pro campo "select" -- caso especial pro campo "fazenda"
     * (Pragas/Clima): usa a lista real de fazendas ATIVAS (farms, ver acima)
     * em vez do lookupCategory "locais" (texto livre, nunca sincronizado com
     * exclusão/desativação de fazenda). Qualquer outro campo select continua
     * usando lookups normalmente (inclusive "local", usado por outros
     * módulos, que fica como estava -- só "fazenda" muda). */
    fun optionsFor(col: ColumnConfig, lookups: Map<String, List<LookupEntity>>): List<LookupEntity>? {
        if (col.key == "fazenda") {
            return farms.value.sortedBy { it.name }.map { LookupEntity("locais", it.name, it.name, 0) }
        }
        return effectiveLookupCategory(col)?.let { lookups[it] }
    }

    /** Preenche todos os campos (exceto computados) com os valores do
     * lançamento mais recente do módulo -- o usuário ainda pode editar
     * qualquer campo antes de salvar, igual ao site. */
    fun copyFromLastRecord() {
        val last = lastRecord.value ?: return
        val cfg = config.value ?: return
        for (col in cfg.columns) {
            if (col.computed) continue
            val raw = last[col.key] ?: ""
            fields[col.key] = if (col.type == "date") isoDateToBr(isoDateOnly(raw)) else raw
        }
    }

    // Campos com o rótulo faltando -- exibido junto do campo em vermelho
    // depois de uma tentativa de salvar sem preenchê-lo (pedido do usuário:
    // "coloque uma condição para salvar o lançamento, todos os campos
    // [obrigatórios] terão que ser preenchidos", confirmado como válido em
    // TODOS os módulos, não só Receituários/Cobranças). Servidor já rejeita
    // isso via Zod (registry.ts `required: true`), mas sem aviso nenhum na
    // tela -- o usuário só via um erro genérico depois de tentar salvar.
    var missingFields = mutableStateOf<Set<String>>(emptySet())
        private set

    /** Valida os campos `required: true` (não computados) antes de tentar
     * salvar -- mesma regra já usada pelo servidor (registry.ts), só que
     * aqui bloqueia ANTES de gastar uma chamada de rede/gravação offline.
     * Retorna a lista de rótulos faltando (vazia = pode salvar). */
    private fun validateRequired(): List<String> {
        val cfg = config.value ?: return emptyList()
        val missing = cfg.columns.filter { !it.computed && it.required && fields[it.key].isNullOrBlank() }
        missingFields.value = missing.map { it.key }.toSet()
        return missing.map { it.label }
    }

    fun save(domainId: String, recordId: String?, onDone: () -> Unit) {
        val missingLabels = validateRequired()
        if (missingLabels.isNotEmpty()) {
            errorMessage.value = "Preencha os campos obrigatórios: ${missingLabels.joinToString(", ")}."
            return
        }
        saving.value = true
        errorMessage.value = null
        offlineNotice.value = null
        viewModelScope.launch {
            // Campos de data são exibidos/digitados em DD/MM/AAAA (pedido do
            // usuário) mas o servidor espera AAAA-MM-DD -- converte só aqui,
            // na hora de montar o corpo, sem afetar o que está na tela.
            val dateKeys = config.value?.columns?.filter { it.type == "date" }?.map { it.key }?.toSet().orEmpty()
            val snapshot = fields.mapValues { (key, value) -> if (key in dateKeys) brDateToIso(value) else value }
            val result = if (recordId == null) recordRepository.createRecord(domainId, snapshot) else recordRepository.updateRecord(domainId, recordId, snapshot)
            saving.value = false
            when (result) {
                is SaveResult.SavedOnline -> onDone()
                is SaveResult.SavedOffline -> {
                    offlineNotice.value = "Sem conexão -- salvo no aparelho, sincroniza sozinho quando a internet voltar."
                    onDone()
                }
                is SaveResult.Failure -> errorMessage.value = result.message
            }
        }
    }
}

/** Formulario generico -- UMA tela cobre os 16 modulos, montada a partir do
 * DomainConfig (mesmo principio do site, ver components/domain/record-form.tsx).
 * Os campos "computed" (calculados pelo servidor -- rateio, vencimento,
 * numeracao de O.S. etc.) nao aparecem aqui: so existem depois da
 * sincronizacao, quando o servidor devolve o registro definitivo. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DomainFormScreen(
    domainId: String,
    recordId: String?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: DomainFormViewModel = viewModel(),
) {
    LaunchedEffect(domainId, recordId) { viewModel.load(domainId, recordId) }
    val config by viewModel.config
    val lookups by viewModel.lookupsByCategory
    val saving by viewModel.saving
    val error by viewModel.errorMessage
    val offlineNotice by viewModel.offlineNotice
    val lastRecord by viewModel.lastRecord
    val computedValues by viewModel.computedValues
    val missingFields by viewModel.missingFields

    Scaffold(
        topBar = {
            TopAppBar(
                // Título desce uma linha, mesmo padrão já usado em
                // DomainListScreen/Financeiro/DRE/Análises -- pedido do
                // usuário ("em novo lançamento em todos os módulos... rebaixe
                // a seta a esquerda e o título uma linha abaixo").
                title = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        // basicMarquee() REMOVIDO -- suspeita forte de causa
                        // do crash "app fecha ao preencher qualquer campo
                        // (menos Data)" reportado pelo usuário: o único campo
                        // que não fecha é o único preenchível SEM abrir o
                        // teclado (o seletor de calendário); todos os outros
                        // exigem o teclado, e abrir/fechar o teclado
                        // redimensiona a janela (Scaffold), o que pode
                        // recalcular a largura do marquee em condição de
                        // corrida -- já houve um bug real confirmado de
                        // marquee travando a barra inferior neste mesmo app
                        // (ver StatusStyle.kt, "Reverter marquee dentro de
                        // DropdownMenuItem"). Aqui o texto é sempre um destes
                        // dois literais curtos e fixos ("Novo lançamento"/
                        // "Editar lançamento"), nunca precisou de letreiro --
                        // Ellipsis é suficiente e nunca vai nem aparecer.
                        Text(
                            if (recordId == null) "Novo lançamento" else "Editar lançamento",
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                navigationIcon = {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar", tint = MaterialTheme.colorScheme.primary) }
                    }
                },
                actions = {
                    // "Copiar último lançamento" (Task #51/#77) virou ícone
                    // no topo à direita -- pedido do usuário ("transforme a
                    // palavra copiar último lançamento em ícone, posicione-o
                    // à direita"), em vez do botão largo que ocupava uma
                    // linha inteira do formulário. Mesmo Spacer do
                    // título/seta (16.dp) pra ficar na mesma altura -- pedido
                    // do usuário ("insira... o ícone copiar na mesma altura
                    // do título no canto superior direito").
                    // Ícone "Nota com itens" (atalho pra NotaMultiItemScreen)
                    // removido daqui -- pedido do usuário (achado de
                    // auditoria: "exclua o ícone de adicionar itens do app
                    // native"), pra Financeiro/Novo Lançamento ficar no mesmo
                    // modelo enxuto do módulo Lançamentos da plataforma (que
                    // não tem esse atalho no cabeçalho). Mantém só o ícone
                    // Copiar abaixo.
                    // Sempre visível em Novo Lançamento, mesmo sem nenhum
                    // registro anterior pra copiar -- pedido do usuário
                    // ("force também o ícone copiar em novo lançamento mesmo
                    // não estando preenchido os campos"): antes o ícone
                    // inteiro sumia quando lastRecord era null (nenhum
                    // lançamento anterior no módulo ainda), o que escondia a
                    // funcionalidade em vez de só desabilitá-la. Agora
                    // aparece sempre, só fica acinzentado/inerte quando não
                    // há nada pra copiar (copyFromLastRecord() já retorna
                    // sem fazer nada nesse caso, com segurança).
                    if (recordId == null) {
                        Column {
                            Spacer(modifier = Modifier.height(16.dp))
                            // Cor verde -- pedido do usuário ("em novo
                            // lançamento altere a cor do ícone copiar para
                            // verde").
                            val hasLastRecord = lastRecord != null
                            IconButton(onClick = { viewModel.copyFromLastRecord() }, enabled = hasLastRecord) {
                                Icon(
                                    Icons.Filled.ContentCopy,
                                    contentDescription = "Copiar último lançamento",
                                    tint = if (hasLastRecord) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        val cfg = config
        if (cfg == null) {
            Column(modifier = Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.Center) {
                Text("Carregando...", modifier = Modifier.padding(24.dp))
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            if (cfg.notice != null) {
                Text(cfg.notice, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 12.dp))
            }

            // Avisos da nuvem/copiar e de campos calculados REMOVIDOS daqui --
            // pedido do usuário ("retire os avisos da nuvem copiar e dos
            // campos calculados automaticamente"): o rótulo "(calculado
            // automaticamente)" que já aparece acima de cada campo calculado
            // (ver ComputedFieldDisplay abaixo) já deixa isso claro sem
            // precisar de um parágrafo extra repetindo a mesma informação; o
            // ícone de copiar (TopAppBar) também não precisa mais de legenda
            // -- é auto-explicativo (ícone de copiar + tooltip/contentDescription
            // "Copiar último lançamento").

            // Mostra TODAS as colunas na ordem natural do domínio (mesma
            // ordem de cfg.columns, que já reflete a ordem da operação em
            // registry.ts) -- pedido do usuário ("coloque todos os campos
            // até os que são calculados automaticamente na ordem correta da
            // operação em todos os módulos"). Os computed viram uma caixa
            // somente-leitura em vez de um campo editável.
            for (col in cfg.columns) {
                if (col.computed) {
                    val optionLabels = col.lookupCategory?.let { cat -> lookups[cat]?.associate { it.value to it.label } } ?: emptyMap()
                    ComputedFieldDisplay(col = col, raw = computedValues[col.key] ?: "", optionLabels = optionLabels)
                } else {
                    // Campos com dependsOn (ver ColumnConfig em Models.kt)
                    // usam effectiveLookupCategory pra escolher a lista
                    // certa conforme o valor atual do campo do qual
                    // dependem (ex.: Item do Controle Interno conforme a
                    // Categoria) -- campos sem dependsOn continuam usando
                    // lookupCategory direto (effectiveLookupCategory cai
                    // nele automaticamente).
                    FormField(col = col, options = viewModel.optionsFor(col, lookups), viewModel = viewModel, isMissing = col.key in missingFields)
                }
                androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 10.dp))

                // "Lançar nota com itens" EMBUTIDO logo depois do campo
                // Doc/NF -- pedido do usuário (achado de auditoria: "não foi
                // inserido no native como está na plataforma o módulo
                // lançamentos, está faltando adicionar itens na sequência
                // dos campos, como está em plataforma"), mesma posição do
                // site (ver insertAfterField="docNf" em data-table.tsx).
                // Só ao CRIAR (recordId == null) e só em Financeiro --
                // complementa o formulário genérico em vez de substituí-lo,
                // já que Financeiro cobre muitos outros tipos de lançamento
                // sem "itens" (salário, aluguel, venda...). Ver
                // FinanceiroItensInlineSection (FinanceiroItensInline.kt),
                // que lê Doc/NF/Data/Local/Entidade/Safra/Cultura/Setor/
                // Banco/Forma Pgto./Período/Bruto AO VIVO deste mesmo
                // viewModel.fields, sem duplicar nenhum campo.
                if (recordId == null && domainId == "financeiro" && col.key == "docNf") {
                    FinanceiroItensInlineSection(
                        docNf = viewModel.fields["docNf"] ?: "",
                        data = viewModel.fields["data"] ?: "",
                        local = viewModel.fields["local"] ?: "",
                        entidade = viewModel.fields["entidade"] ?: "",
                        safra = viewModel.fields["safra"] ?: "",
                        cultura = viewModel.fields["cultura"] ?: "",
                        setor = viewModel.fields["setor"] ?: "",
                        banco = viewModel.fields["banco"] ?: "",
                        formaPgto = viewModel.fields["formaPgto"] ?: "",
                        periodo = viewModel.fields["periodo"] ?: "",
                        bruto = viewModel.fields["bruto"] ?: "",
                        onDone = onSaved,
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 10.dp))
                }
            }

            if (error != null) {
                Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            }
            if (offlineNotice != null) {
                Text(offlineNotice!!, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(top = 8.dp))
            }

            androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 16.dp))
            Button(
                onClick = { viewModel.save(domainId, recordId, onSaved) },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (saving) CircularProgressIndicator(modifier = Modifier.padding(2.dp)) else Text("Salvar")
            }
        }
    }
}

// Mascara de moeda ao digitar (pedido do usuario: "coloque auto
// preenchimento nos valores moeda... ponto na milhar e as ,00
// automaticamente") -- réplica exata da mesma lógica em money-input.tsx no
// site (formatBrMoneyInput/moneyRawToDisplay/toRawNumberString), pra
// digitar "970000" no app dar "970.000,00" igual no site.

/** So digitos (+ 1 "-" opcional na frente e 1 "," opcional) -- ponto de
 * milhar automatico. NAO completa ",00" aqui (ver finalizeMoneyMask abaixo)
 * -- bug real reportado pelo usuário ("não consigo preencher nenhum campo
 * do app... quando clico em outro campo"): completar ",00" a CADA tecla
 * fazia o valor exibido crescer com um ",00" fantasma que o usuário nunca
 * digitou; como o campo é recomposto a partir desse valor formatado, o
 * cursor sempre ficava reposicionado no fim de uma string maior do que o
 * que foi realmente digitado, e a tecla seguinte caía dentro da parte
 * decimal fantasma (sempre truncada em 2 dígitos) em vez de continuar a
 * parte inteira -- dava a impressão de campo travado em 1 caractere. Sem
 * completar ",00" durante a digitação, o fim da string sempre coincide com
 * o que o usuário realmente digitou. Ex. enquanto digita: "1" -> "1",
 * "1234" -> "1.234", "123,5" -> "123,5". */
private fun formatMoneyMask(raw: String): String {
    val negative = raw.trim().startsWith("-")
    val cleaned = raw.filter { it.isDigit() || it == ',' }
    val firstComma = cleaned.indexOf(',')
    val intPartRaw = if (firstComma == -1) cleaned else cleaned.substring(0, firstComma)
    // No maximo 2 casas decimais, exatamente as que o usuario ja digitou --
    // SEM completar com "00" aqui (isso so acontece no blur, ver
    // finalizeMoneyMask).
    val decPartRaw = if (firstComma == -1) "" else cleaned.substring(firstComma + 1).replace(",", "").take(2)
    val intDigits = intPartRaw.replaceFirst(Regex("^0+(?=\\d)"), "")
    if (intDigits.isEmpty() && decPartRaw.isEmpty() && firstComma == -1) return ""
    val intFormatted = groupThousands(intDigits.ifEmpty { "0" })
    if (firstComma == -1) return "${if (negative) "-" else ""}$intFormatted"
    return "${if (negative) "-" else ""}$intFormatted,$decPartRaw"
}

/** Completa a formatação quando o campo perde o foco: garante ",00" (ou
 * completa "1,5" -> "1,50") -- mesma aparência final de sempre
 * ("970.000,00"), só que aplicada ao SAIR do campo em vez de a cada tecla
 * (ver comentário acima em formatMoneyMask sobre por que isso quebrava a
 * digitação). Réplica de finalizeBrMoneyInput em money-input.tsx (site). */
private fun finalizeMoneyMask(display: String): String {
    if (display.isEmpty()) return ""
    val negative = display.startsWith("-")
    val unsigned = if (negative) display.substring(1) else display
    val parts = unsigned.split(",")
    val intFormatted = parts.getOrElse(0) { "" }.ifEmpty { "0" }
    val decFormatted = (parts.getOrElse(1) { "" } + "00").take(2)
    return "${if (negative) "-" else ""}$intFormatted,$decFormatted"
}

/** "-970.000,00" -> "-970000.00" -- mesmo formato numerico cru que o campo
 * sempre guardou em viewModel.fields (o servidor faz Number(raw)/
 * toDoubleOrNull nisso, sem mudanca nenhuma). */
private fun moneyDisplayToRaw(display: String): String {
    if (display.isEmpty()) return ""
    val negative = display.startsWith("-")
    val unsigned = if (negative) display.substring(1) else display
    val parts = unsigned.split(",")
    val intDigits = parts[0].replace(".", "").ifEmpty { "0" }
    val decPart = parts.getOrElse(1) { "00" }
    return "${if (negative) "-" else ""}$intDigits.$decPart"
}

/** "970000.00" (valor cru guardado no campo) -> "970.000,00" (exibicao) --
 * usado tanto pro valor inicial ao editar quanto a cada keystroke (o campo
 * e 100% controlado pelo valor cru, nunca guarda o texto formatado). */
private fun moneyRawToDisplay(raw: String): String {
    val n = raw.toDoubleOrNull() ?: return ""
    val negative = n < 0
    val cents = Math.round(kotlin.math.abs(n) * 100)
    val intPart = (cents / 100).toString()
    val decPart = (cents % 100).toString().padStart(2, '0')
    return "${if (negative) "-" else ""}${groupThousands(intPart)},$decPart"
}

private fun groupThousands(digits: String): String =
    digits.reversed().chunked(3).joinToString(".").reversed()

// col.label de toda coluna "money" JÁ vem com "(R$)" embutido (ver
// lib/domains/registry.ts no site, ex.: "Valor (R$)", "Bruto (R$)") --
// completar de novo aqui era a causa do "há 2 (R$) (R$)" relatado pelo
// usuário. Só o "*" de obrigatório é acrescentado.
private fun fieldLabel(col: ColumnConfig): String = col.label + if (col.required) " *" else ""

// Réplica de fmtComputed() em record-form.tsx: "—" enquanto vazio (registro
// novo, ainda sem passar pelo servidor), money formatado com formatMoneyValue
// (mesma função usada em toda a lista), select mapeado pro rótulo amigável
// via lookup, e o resto pelo displayValueFor genérico (datas/números).
private fun computedDisplayValue(col: ColumnConfig, raw: String, optionLabels: Map<String, String>): String {
    if (raw.isBlank()) return "—"
    if (col.money) return formatMoneyValue(raw)
    if (col.type == "select") return optionLabels[raw] ?: raw
    return displayValueFor(col.key, raw, col.type)
}

// Campo calculado (rateio, vencimento, líquido, status, numeração de O.S.
// etc.) -- caixa cinza somente-leitura, NUNCA um input, mesmo tratamento
// visual do site (record-form.tsx: "bg-muted/40" + rótulo "(calculado)").
@Composable
private fun ComputedFieldDisplay(col: ColumnConfig, raw: String, optionLabels: Map<String, String>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            // "(calculado automaticamente)" -> "(automático)" -- pedido do
            // usuário ("substitua esta palavra... por automático").
            "${col.label} (automático)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                // Preenchimento igual aos campos manuais (appFieldColors, ver
                // AppCard.kt), sem borda -- pedido do usuário ("tire todas as
                // bordas de todo app"), revertendo a borda que antes
                // igualava este campo aos manuais.
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            // Fonte verde -- pedido do usuário ("nos campos que forem
            // calculados automaticamente coloque a cor da fonte de verde").
            Text(
                computedDisplayValue(col, raw, optionLabels),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

// Tonalidade de verde da marca (mesma da barra inferior de navegação)
// explícita em TODOS os campos de lançamento -- pedido do usuário ("aplique
// a tonalidade de verde da barra inferior de botões em todos os campos de
// lançamentos"). Usa `colorScheme.primary` (não mais o BrGreen fixo) --
// pedido do usuário ("no modo claro e escuro siga a cor da barra inferior
// para as listas suspensas dos módulos e dos campos"): BrGreen é uma
// constante única (0xFF2F6F4F) que não muda com o tema, enquanto a barra
// inferior (NavigationBar, ver BottomNavBar.kt) usa colorScheme.primary, que
// É diferente por tema (BrGreenPrimaryLight/BrGreenPrimaryDark, ver
// Theme.kt) -- com BrGreen fixo, o campo ficava com um verde ligeiramente
// diferente do da barra inferior no modo escuro. colorScheme.primary garante
// que os dois sempre batem, em qualquer tema.
// Preenchido com a MESMA cor dos blocos (Cards) + sem borda -- pedido do
// usuário ("preeencha os campos da mesma cor dos blocos e rretire as
// bordas odss campos"). Antes o campo tinha fundo transparente (só a
// borda demarcava onde clicar) -- agora container = colorScheme.surface,
// o mesmo token que os Cards usam (ver AppCard.kt/Theme.kt), então campo e
// bloco ficam visualmente no mesmo tom. Bordas (focada e não-focada) viram
// transparentes nos dois estados -- quem demarca o campo agora é só o
// preenchimento, igual um bloco.
@Composable
private fun greenFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Color.Transparent,
    unfocusedBorderColor = Color.Transparent,
    disabledBorderColor = Color.Transparent,
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    disabledContainerColor = MaterialTheme.colorScheme.surface,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    cursorColor = MaterialTheme.colorScheme.primary,
    focusedTrailingIconColor = MaterialTheme.colorScheme.primary,
)

// Mesmas cores de sempre, só com a borda/rótulo em vermelho quando o campo
// está na lista de "faltando" depois de uma tentativa de Salvar (pedido do
// usuário: validação de obrigatório em todos os módulos) -- também some o
// cursor e ícones da cor de erro, senão fica um vermelho só na borda e o
// resto verde, inconsistente.
// Borda vermelha PRESERVADA de propósito aqui (diferente de greenFieldColors
// acima) -- não é decoração, é o único sinal visual de "campo obrigatório
// faltando" depois de uma tentativa de Salvar. Container também preenchido
// com a cor dos blocos (mesmo critério do campo normal), só a borda que
// continua vermelha em vez de transparente.
@Composable
private fun errorFieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedBorderColor = MaterialTheme.colorScheme.error,
    focusedBorderColor = MaterialTheme.colorScheme.error,
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedLabelColor = MaterialTheme.colorScheme.error,
    focusedLabelColor = MaterialTheme.colorScheme.error,
    cursorColor = MaterialTheme.colorScheme.error,
    focusedTrailingIconColor = MaterialTheme.colorScheme.error,
    unfocusedTrailingIconColor = MaterialTheme.colorScheme.error,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormField(col: ColumnConfig, options: List<LookupEntity>?, viewModel: DomainFormViewModel, isMissing: Boolean = false) {
    val value = viewModel.fields[col.key] ?: ""
    val fieldColors = if (isMissing) errorFieldColors() else greenFieldColors()

    when (col.type) {
        "checkbox" -> {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(
                    checked = value == "true",
                    onCheckedChange = { viewModel.setField(col.key, it.toString()) },
                    colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
                )
                Text(col.label + if (col.required) " *" else "")
            }
        }
        "select" -> {
            // Combobox pesquisavel -- pedido do usuario ("quando clicar no
            // campo apareça o teclado para facilitar a busca... são todos os
            // campos que possuem lista suspensa de todos os módulos"). Antes
            // o campo era "readOnly = true" (só abria a lista pra rolar e
            // tocar, sem teclado nenhum) -- inútil pra listas longas como
            // Local/Entidade/Categoria com dezenas de itens. Agora o campo é
            // editável de verdade: digitar abre o teclado E filtra a lista
            // (igual ao SearchableSelect novo em record-form.tsx no site).
            var expanded by remember { mutableStateOf(false) }
            val optionLabels = options?.associate { it.value to it.label } ?: emptyMap()
            val staticOpts = col.staticOptions
            // (valor, rotulo) na mesma ordem que já vinha (staticOpts
            // preserva ordem própria do domínio; options já chega ordenado
            // alfabeticamente do Room -- ver LookupDao.byCategory).
            val allOptions: List<Pair<String, String>> = staticOpts?.map { it to it }
                ?: options?.map { it.value to it.label }
                ?: emptyList()
            var query by remember(value) { mutableStateOf(optionLabels[value] ?: value) }
            val filtered = remember(query, allOptions) {
                val q = query.trim()
                if (q.isEmpty()) allOptions else allOptions.filter { it.second.contains(q, ignoreCase = true) }
            }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { typed -> query = typed; expanded = true },
                    label = { Text(fieldLabel(col)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    colors = fieldColors,
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = {
                        expanded = false
                        // Fechou sem escolher nada novo -- volta o texto pro
                        // que realmente está selecionado (senão um texto
                        // digitado e não confirmado ficaria "preso" no campo).
                        query = optionLabels[value] ?: value
                    },
                ) {
                    DropdownMenuItem(text = { Text("(vazio)") }, onClick = {
                        viewModel.setField(col.key, "")
                        query = ""
                        expanded = false
                    })
                    if (filtered.isEmpty()) {
                        DropdownMenuItem(text = { Text("Nenhum resultado", color = MaterialTheme.colorScheme.onSurfaceVariant) }, onClick = {}, enabled = false)
                    }
                    for ((optValue, optLabel) in filtered) {
                        DropdownMenuItem(text = { Text(optLabel) }, onClick = {
                            viewModel.setField(col.key, optValue)
                            query = optLabel
                            expanded = false
                        })
                    }
                }
            }
        }
        "number" -> {
            if (col.money) {
                // Mascara de milhar/centavos automatica (pedido do usuario:
                // "970000" -> "970.000,00" enquanto digita, mesma mascara
                // do site -- ver money-input.tsx). "viewModel.fields"
                // continua guardando o numero cru ("970000.00", igual
                // antes), so a EXIBICAO e formatada -- o parser do servidor
                // no Number(raw)/toDoubleOrNull nao precisou mudar.
                //
                // Estado local "display" (em vez de recalcular sempre via
                // moneyRawToDisplay(value)) -- bug real reportado pelo
                // usuário ("não consigo preencher nenhum campo... quando
                // clico em outro campo"): recalcular a exibição a partir do
                // valor cru a cada tecla sempre reintroduzia ",00" (2 casas
                // fixas), mesmo depois de tirar o auto-preenchimento de
                // formatMoneyMask -- o valor cru sempre carrega centavos
                // (".00" por padrão). Com estado local, o campo mostra
                // exatamente o que foi digitado enquanto o usuário digita, e
                // só sincroniza com o valor cru (útil ao ABRIR um registro
                // existente, carregado de forma assíncrona) enquanto o
                // campo não está com foco -- padrão "uncontrolled enquanto
                // focado, sincronizado quando não".
                var focused by remember { mutableStateOf(false) }
                var display by remember { mutableStateOf(moneyRawToDisplay(value)) }
                LaunchedEffect(value, focused) {
                    if (!focused) display = moneyRawToDisplay(value)
                }
                OutlinedTextField(
                    value = display,
                    onValueChange = { typed ->
                        val formatted = formatMoneyMask(typed)
                        display = formatted
                        viewModel.setField(col.key, moneyDisplayToRaw(formatted))
                    },
                    label = { Text(fieldLabel(col)) },
                    prefix = { Text("R$ ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().onFocusChanged { state ->
                        val wasFocused = focused
                        focused = state.isFocused
                        if (wasFocused && !state.isFocused) {
                            // Perdeu o foco -- completa ",00"/casas decimais
                            // faltando (ver finalizeMoneyMask acima).
                            val finalized = finalizeMoneyMask(display)
                            display = finalized
                            viewModel.setField(col.key, moneyDisplayToRaw(finalized))
                        }
                    },
                    singleLine = true,
                    colors = fieldColors,
                )
            } else {
                // BUG real encontrado (Sentry SISTEMA-AGRO-BRA-3, "Hectare
                // (ha): valor numérico inválido", POST /api/offline-sync,
                // domainId "safra"): KeyboardType.Decimal mostra a tecla ","
                // em teclado numérico de aparelho com locale pt-BR (não "."),
                // e este campo (diferente do "money" acima, que já passa por
                // moneyDisplayToRaw) guardava o texto digitado cru -- "45,5"
                // virava Number("45,5") = NaN no servidor, rejeitado pelo
                // Zod. Afeta qualquer campo number não-money com casas
                // decimais (Hectare, Qtd, Produtividade, Vazão/Dose): o
                // usuário só via "erro 400" genérico ao sincronizar depois
                // de digitar um valor quebrado. Troca "," por "." a cada
                // tecla -- campo continua 100% controlado por um único valor
                // (sem exibição/valor cru separados como no money), então o
                // que aparece na tela já é o que vai pro servidor.
                OutlinedTextField(
                    value = value,
                    onValueChange = { viewModel.setField(col.key, it.replace(",", ".")) },
                    label = { Text(fieldLabel(col)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = fieldColors,
                )
            }
        }
        "date" -> {
            // Ícone de calendário dentro do campo -- pedido do usuário ("o
            // ícone copiar em campos de data... coloque ao lado dentro do
            // campo um calendário para aplicar a data"): antes só dava pra
            // digitar a data na mão (DD/MM/AAAA), sem nenhum seletor.
            var showPicker by remember { mutableStateOf(false) }
            OutlinedTextField(
                value = value,
                onValueChange = { viewModel.setField(col.key, it) },
                label = { Text(fieldLabel(col)) },
                placeholder = { Text("DD/MM/AAAA") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = fieldColors,
                trailingIcon = {
                    // Cor verde -- pedido do usuário ("nos campos que houver
                    // calendário altere o ícone para cor verde").
                    IconButton(onClick = { showPicker = true }) {
                        Icon(Icons.Filled.CalendarMonth, contentDescription = "Escolher data", tint = MaterialTheme.colorScheme.primary)
                    }
                },
            )
            if (showPicker) {
                val pickerState = rememberDatePickerState(initialSelectedDateMillis = brDateToMillisOrNull(value) ?: System.currentTimeMillis())
                DatePickerDialog(
                    onDismissRequest = { showPicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            pickerState.selectedDateMillis?.let { viewModel.setField(col.key, millisToBrDate(it)) }
                            showPicker = false
                        }) { Text("OK") }
                    },
                    dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancelar") } },
                ) {
                    DatePicker(state = pickerState)
                }
            }
        }
        "textarea" -> {
            OutlinedTextField(
                value = value,
                onValueChange = { viewModel.setField(col.key, it) },
                label = { Text(fieldLabel(col)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                colors = fieldColors,
            )
        }
        else -> {
            OutlinedTextField(
                value = value,
                onValueChange = { viewModel.setField(col.key, it) },
                label = { Text(fieldLabel(col)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = fieldColors,
            )
        }
    }
    if (isMissing) {
        Text("Campo obrigatório", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
    }
    if (col.hint != null) {
        Text(col.hint, style = MaterialTheme.typography.bodySmall)
    }
}

// Conversão pro DatePicker (Material3) -- ele trabalha em epoch millis UTC,
// o campo trabalha em texto "DD/MM/AAAA". Calendar com fuso UTC explícito
// (nunca o fuso do aparelho) pra não "voltar um dia" -- mesmo cuidado já
// documentado em isoDateToBr/brDateToIso (StatusStyle.kt).
private fun millisToBrDate(millis: Long): String {
    val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
    cal.timeInMillis = millis
    val d = cal.get(java.util.Calendar.DAY_OF_MONTH)
    val mo = cal.get(java.util.Calendar.MONTH) + 1
    val y = cal.get(java.util.Calendar.YEAR)
    return "%02d/%02d/%04d".format(d, mo, y)
}

private fun brDateToMillisOrNull(br: String): Long? {
    val m = Regex("^(\\d{2})/(\\d{2})/(\\d{4})$").find(br.trim()) ?: return null
    val (d, mo, y) = m.destructured
    val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
    cal.clear()
    cal.set(y.toInt(), mo.toInt() - 1, d.toInt())
    return cal.timeInMillis
}
