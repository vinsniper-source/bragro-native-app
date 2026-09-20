package com.bragro.mobile.ui.nav

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.bragro.mobile.data.local.AppDatabase
import com.bragro.mobile.data.repo.AuthRepository
import com.bragro.mobile.ui.analises.AnalisesScreen
import com.bragro.mobile.ui.basededados.BaseDeDadosScreen
import com.bragro.mobile.ui.dre.DreScreen
import com.bragro.mobile.ui.livrocaixa.LivroCaixaScreen
import com.bragro.mobile.ui.drone.DroneScreen
import com.bragro.mobile.ui.fieldview.FieldviewScreen
import com.bragro.mobile.ui.domain.BankImportScreen
import com.bragro.mobile.ui.domain.DomainFormScreen
import com.bragro.mobile.ui.domain.DomainListScreen
import com.bragro.mobile.ui.domain.FinanceiroScreen
import com.bragro.mobile.ui.home.HomeScreen
import com.bragro.mobile.ui.login.LoginScreen
import com.bragro.mobile.ui.insumos.ControleInsumosScreen
import com.bragro.mobile.ui.nfe.NfeImportScreen
import com.bragro.mobile.ui.pedidos.PedidoMultiItemScreen
import com.bragro.mobile.ui.cotacoes.CotacaoMultiItemScreen
import com.bragro.mobile.ui.operacoes.OperacoesScreen
import com.bragro.mobile.ui.romaneio.RomaneioQuickScreen
import com.bragro.mobile.ui.pragas.PragaFotoScreen
import com.bragro.mobile.ui.domain.FrotaQrScreen
import com.bragro.mobile.ui.estoque.ReconciliacaoEstoqueScreen
import com.bragro.mobile.ui.fieldview.PrescricaoScreen
import com.bragro.mobile.ui.fieldview.PrescricaoNovoScreen
import com.bragro.mobile.ui.dossie.DossieScreen
import com.bragro.mobile.ui.simulador.SimuladorScreen
import com.bragro.mobile.ui.orcamento.OrcamentoScreen
import com.bragro.mobile.ui.orcamento.OrcamentoListScreen
import com.bragro.mobile.ui.nfe.NfeScreen
import com.bragro.mobile.ui.seguranca.SegurancaScreen
import com.bragro.mobile.ui.settings.SettingsScreen

private object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val DRE = "dre"
    const val ANALISES = "analises"
    const val LIVRO_CAIXA = "livro_caixa"
    const val DRONE = "drone"
    const val FIELDVIEW = "fieldview"
    const val NFE_IMPORT = "nfe_import"
    const val CONTROLE_INSUMOS = "controle_insumos"
    const val OPERACOES = "operacoes"
    const val ROMANEIO_QUICK = "romaneio_quick"
    const val PRAGA_FOTO = "praga_foto"
    const val FROTA_QR = "frota_qr"
    const val RECONCILIACAO_ESTOQUE = "reconciliacao_estoque"
    const val PRESCRICAO = "prescricao"
    const val PRESCRICAO_NOVO = "prescricao_novo"
    const val DOSSIE = "dossie"
    const val SIMULADOR = "simulador"
    // NF-e -- módulo novo (Task #628, ausente por completo no app até aqui).
    // Não confundir com NFE_IMPORT acima (import de XML dentro de
    // Financeiro, feature diferente e pré-existente).
    const val NFE = "nfe"
    // Orçamentos -- lista/histórico (Task #710, auditoria de paridade
    // site-vs-native: só existia o form de lançamento, sem tela pra ver/
    // filtrar/conferir orçamentos já lançados). ORCAMENTO_LISTA vira o
    // destino do botão "Orçamentos" na barra inferior; ORCAMENTO_NOVO
    // continua sendo o form (agora aberto pelo FAB "+" dentro da lista).
    const val ORCAMENTO_LISTA = "orcamento_lista"
    const val ORCAMENTO_NOVO = "orcamento_novo"
    const val BANK_IMPORT = "bank_import"
    const val SETTINGS = "settings"
    const val BASE_DE_DADOS = "base_de_dados"
    const val SEGURANCA = "seguranca"
    const val DOMAIN_LIST = "domain/{domainId}"
    const val DOMAIN_FORM_NEW = "domain/{domainId}/new"
    const val DOMAIN_FORM_EDIT = "domain/{domainId}/edit/{recordId}"

    fun domainList(domainId: String) = "domain/$domainId"
    fun domainFormNew(domainId: String) = "domain/$domainId/new"
    fun domainFormEdit(domainId: String, recordId: String) = "domain/$domainId/edit/$recordId"
}

// Telas "principais" (acessadas pela barra inferior -- ver
// ui/nav/BottomNavBar.kt): Início e a lista de qualquer domínio -- cada aba
// da barra agora abre um dropdown com as atividades do setor (pedido do
// usuário), em vez de navegar pra uma tela "Módulos" própria (removida daqui
// -- ver BottomNavBar.kt). Telas mais "de fluxo" (formulário, DRE, Análises,
// importação de NF-e, romaneio, login) ficam sem a barra, mesmo critério já
// usado nelas de ter seu próprio botão "Voltar".
private fun showsBottomBar(route: String?): Boolean =
    route == Routes.HOME || route == Routes.DOMAIN_LIST

@Composable
fun BRAgroNavHost() {
    val navController = rememberNavController()
    val context = LocalContext.current
    var startDestination by remember { mutableStateOf<String?>(null) }

    // Decide a tela inicial olhando so o Room (funciona 100% offline -- nao
    // depende de nenhuma chamada de rede pra saber se o usuario ja logou
    // antes neste aparelho).
    LaunchedEffect(Unit) {
        val loggedIn = AuthRepository(context).isLoggedIn()
        startDestination = if (loggedIn) Routes.HOME else Routes.LOGIN
    }

    val start = startDestination ?: return

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val currentDomainId = if (currentRoute == Routes.DOMAIN_LIST) navBackStackEntry?.arguments?.getString("domainId") else null

    // Delegação de acesso por funcionário (pedido do usuário: "delegar
    // funções pra funcionários, o que eles terão acesso ou não") -- lê a
    // sessão do Room de forma reativa (mesmo padrão de HomeScreen.kt) pra
    // saber quais módulos o usuário logado pode ver, e repassa pra
    // BRAgroBottomBar filtrar a barra inferior (ver isAllowed() em
    // BottomNavBar.kt). "*" = OWNER/ADMIN, vê tudo -- ver allowedModuleIds()
    // em lib/permissions.ts no site, que já calcula essa lista no bootstrap.
    val db = remember { AppDatabase.get(context) }
    val session by db.sessionDao().observe().collectAsState(initial = null)
    val allowedModules = remember(session?.allowedModulesCsv) {
        session?.allowedModulesCsv
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            ?: emptySet()
    }

    Scaffold(
        // Cada tela (Início, lista de módulo etc.) já tem seu próprio
        // Scaffold com TopAppBar, que já reserva o espaço da barra de
        // status sozinho -- sem isso aqui o Scaffold "de fora" reservava
        // esse espaço TAMBÉM (contentWindowInsets padrão cobre os 4 lados),
        // dobrando o respiro no topo e "descendo" o cabeçalho (reportado
        // pelo usuário: "eleve o cabeçalho"). A barra inferior continua
        // recuada da barra de navegação sozinha (NavigationBar já cuida
        // disso por conta própria).
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showsBottomBar(currentRoute)) {
                BRAgroBottomBar(
                    currentDomainId = currentDomainId,
                    allowedModules = allowedModules,
                    // isOwner controla onde Configuracoes/Base de Dados aparecem
                    // (icone no cabecalho vs menu "Módulos") -- estendido de "so
                    // OWNER" pra "OWNER ou ADMIN" (pedido do usuario: "implemente
                    // o cabecalho do owner em admin... volte os modulos completos
                    // Dados e Configuracoes pra seus locais de origem"), mesmo
                    // criterio ja usado no site (layout.tsx/api/mobile/home).
                    isOwner = session?.role == "OWNER" || session?.role == "ADMIN",
                    // ADMIN tambem recebe "*" em allowedModules (mesmo criterio
                    // do site, allowedModuleIds() em lib/permissions.ts) --
                    // pedido do usuario ("replique a barra inferior [do dono]
                    // para a conta admin"): sem isso, ADMIN caia no BOTTOM_TABS
                    // achatado (pensado pra setor limitado) com TODOS os ~20+
                    // modulos liberados, virando uma fileira de abas cortadas/
                    // ilegiveis.
                    useGroupedTabs = session?.role == "OWNER" || session?.role == "ADMIN",
                    onNavigateDomain = { domainId ->
                        navController.navigate(Routes.domainList(domainId)) {
                            popUpTo(Routes.HOME)
                            launchSingleTop = true
                        }
                    },
                    onOpenDre = { navController.navigate(Routes.DRE) },
                    onOpenAnalises = { navController.navigate(Routes.ANALISES) },
                    onOpenLivroCaixa = { navController.navigate(Routes.LIVRO_CAIXA) },
                    onOpenDrone = { navController.navigate(Routes.DRONE) },
                    onOpenFieldview = { navController.navigate(Routes.FIELDVIEW) },
                    onOpenControleInsumos = { navController.navigate(Routes.CONTROLE_INSUMOS) },
                    onOpenOperacoes = { navController.navigate(Routes.OPERACOES) },
                    // Abre a LISTA primeiro agora (Task #710) -- o form de
                    // lançamento (ORCAMENTO_NOVO) continua existindo, só que
                    // acessado pelo FAB "+" dentro da lista.
                    onOpenOrcamento = { navController.navigate(Routes.ORCAMENTO_LISTA) },
                    // Dossiê Bancário e Simulador "E se?" (Task #599/#600,
                    // paridade nativa #615/#616) -- mesmo critério de
                    // SectorTarget.Special dos outros itens acima.
                    onOpenDossie = { navController.navigate(Routes.DOSSIE) },
                    onOpenSimulador = { navController.navigate(Routes.SIMULADOR) },
                    // Prescrição e Reconciliação já tinham rota/tela prontas
                    // (usadas antes só por um FAB/ícone dentro de FieldView/
                    // Estoque) -- entrada própria na barra agora reaproveita
                    // a MESMA rota, sem tela nova. NF-e é rota nova (Task
                    // #628).
                    onOpenPrescricao = { navController.navigate(Routes.PRESCRICAO) },
                    onOpenReconciliacaoEstoque = { navController.navigate(Routes.RECONCILIACAO_ESTOQUE) },
                    onOpenNfe = { navController.navigate(Routes.NFE) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenBaseDeDados = { navController.navigate(Routes.BASE_DE_DADOS) },
                    onOpenSeguranca = { navController.navigate(Routes.SEGURANCA) },
                )
            }
        },
    ) { outerPadding ->
        NavHost(
            navController = navController,
            startDestination = start,
            modifier = Modifier.padding(outerPadding),
        ) {
        composable(Routes.LOGIN) {
            LoginScreen(onLoggedIn = {
                navController.navigate(Routes.HOME) { popUpTo(Routes.LOGIN) { inclusive = true } }
            })
        }
        composable(Routes.HOME) {
            HomeScreen(
                onOpenDomain = { domainId -> navController.navigate(Routes.domainList(domainId)) },
                onLoggedOut = { navController.navigate(Routes.LOGIN) { popUpTo(Routes.HOME) { inclusive = true } } },
                // "Importar KML desta fazenda" (linha de filtros do Canvas) --
                // pedido do usuário ("implemente nessa sequência no app
                // nativo"): FieldView tem tela própria (não é um domainId
                // genérico), mesma rota que o botão "FieldView" da barra
                // inferior já usa (onOpenFieldview acima).
                onOpenFieldview = { navController.navigate(Routes.FIELDVIEW) },
                // Ícones Configurações/Base de Dados do cabeçalho (ver
                // showConfiguracoesIcon/showBaseDeDadosIcon em HomeScreen.kt)
                // -- mesmas rotas já usadas pelo menu "Módulos" da barra
                // inferior logo abaixo (onOpenSettings/onOpenBaseDeDados).
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenBaseDeDados = { navController.navigate(Routes.BASE_DE_DADOS) },
                // Ícone "Módulos" do cabeçalho (OWNER/ADMIN) -- mesma rota já
                // usada pelo antigo menu "Módulos" da barra inferior logo
                // abaixo (onOpenSeguranca).
                onOpenSeguranca = { navController.navigate(Routes.SEGURANCA) },
            )
        }
        composable(Routes.DRE) {
            DreScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.ANALISES) {
            AnalisesScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.LIVRO_CAIXA) {
            LivroCaixaScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.DRONE) {
            DroneScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.FIELDVIEW) {
            FieldviewScreen(
                onBack = { navController.popBackStack() },
                // Atalho manual (botão + na aba Máquinas) -- pedido do
                // usuário: em vez de duplicar o formulário completo de Frota
                // dentro do FieldView (que só mostra um RESUMO automático
                // dela), leva direto pro lançamento novo de Frota.
                onNavigateToFrota = { navController.navigate(Routes.domainFormNew("frota")) },
                onOpenPrescricao = { navController.navigate(Routes.PRESCRICAO) },
            )
        }
        composable(Routes.PRESCRICAO) {
            PrescricaoScreen(
                onBack = { navController.popBackStack() },
                onNovo = { navController.navigate(Routes.PRESCRICAO_NOVO) },
            )
        }
        // Nova Prescrição (Task #651) -- pedido do usuário ("crie no native
        // como foi criado na plataforma"): antes só o site criava.
        composable(Routes.PRESCRICAO_NOVO) {
            PrescricaoNovoScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.NFE_IMPORT) {
            NfeImportScreen(onBack = { navController.popBackStack() })
        }
        // Rota NOTA_MULTI_ITEM removida -- pedido do usuário (achado de
        // auditoria: "não foi inserido no native como está na plataforma o
        // módulo lançamentos, está faltando adicionar itens na sequência
        // dos campos, como está em plataforma"): a tela separada
        // (NotaMultiItemScreen.kt) já estava desatualizada em relação ao
        // site havia várias rodadas (campos duplicados, "Valor unitário"
        // por item que o site já tinha removido) e não tinha mais nenhum
        // gatilho na UI desde a v1.2.24. Substituída por
        // FinanceiroItensInlineSection, embutida direto na sequência de
        // campos do Novo Lançamento (ver DomainFormScreen.kt).
        composable(Routes.CONTROLE_INSUMOS) {
            ControleInsumosScreen(
                onBack = { navController.popBackStack() },
                // "Pedido rápido" -- corrigido pra replicar o que o site
                // realmente faz em "/m/pedidos?item=X" (ver
                // PendingDomainFilter.kt pro porquê): abre a LISTA de
                // Pedidos já filtrada por esse item, não um formulário em
                // branco. com.bragro.mobile.ui.domain.PendingDomainFilter
                // guarda o item "de passagem" -- DomainListScreen consome no
                // onMount quando domainId == "pedidos".
                onPedidoRapido = { item ->
                    com.bragro.mobile.ui.domain.PendingDomainFilter.set("pedidos", "item", item)
                    navController.navigate(Routes.domainList("pedidos")) {
                        popUpTo(Routes.HOME)
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(Routes.OPERACOES) {
            OperacoesScreen(
                onBack = { navController.popBackStack() },
                // Icone de editar por linha (Safra/Financeiro) navega direto
                // pro registro especifico -- pedido do usuario ("preciso que
                // abra a janela para editar e salvar, aplique tambem essas
                // opcoes no native app"). Reaproveita a MESMA rota que
                // "Copiar ultimo lancamento" ja usa (domain/{domainId}/edit/
                // {recordId}), so que chegando aqui a partir do card de
                // Operacao em vez do link generico "Ver em Safra" (removido).
                onEditRecord = { domainId, recordId ->
                    navController.navigate(Routes.domainFormEdit(domainId, recordId))
                },
            )
        }
        composable(Routes.ROMANEIO_QUICK) {
            RomaneioQuickScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.PRAGA_FOTO) {
            PragaFotoScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.FROTA_QR) {
            FrotaQrScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.RECONCILIACAO_ESTOQUE) {
            ReconciliacaoEstoqueScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.ORCAMENTO_LISTA) {
            OrcamentoListScreen(
                onBack = { navController.popBackStack() },
                onNovo = { navController.navigate(Routes.ORCAMENTO_NOVO) },
            )
        }
        composable(Routes.ORCAMENTO_NOVO) {
            // onBack aqui volta pra ORCAMENTO_LISTA (topo da pilha logo
            // abaixo, ver composable acima) -- fecha o ciclo pedido (Task
            // #710): tanto a seta Voltar quanto o botão "Voltar para a
            // lista" no aviso de sucesso (ver OrcamentoScreen.kt) usam este
            // mesmo callback.
            OrcamentoScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.DOSSIE) {
            DossieScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SIMULADOR) {
            SimuladorScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.NFE) {
            NfeScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.BANK_IMPORT) {
            BankImportScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.BASE_DE_DADOS) {
            BaseDeDadosScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SEGURANCA) {
            SegurancaScreen(onBack = { navController.popBackStack() })
        }
        composable(
            Routes.DOMAIN_LIST,
            arguments = listOf(navArgument("domainId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val domainId = backStackEntry.arguments?.getString("domainId") ?: return@composable
            // Financeiro ganhou uma tela própria (visões Pagar/Receber/
            // Conciliado/Fluxo/Rateio, ver FinanceiroScreen.kt) -- réplica do
            // tratamento especial que só esse domínio recebe em
            // src/app/(app)/m/[domain]/page.tsx no site. Os outros ~17
            // módulos continuam na tela genérica.
            // "gestaofinanceira" -- entrada PRÓPRIA na lista suspensa do
            // botão Financeiro (ver BottomNavBar.kt), pedido do usuário
            // ("na barra inferior do botão financeiro insira, na lista
            // suspensa, um módulo chamado gestão financeira"). Não é um
            // domínio de verdade (não existe FinanceiroRecord filtrado por
            // "gestaofinanceira" no servidor) -- é a MESMA tela/dados de
            // "financeiro", só abrindo direto na visão "Contas a Pagar" com
            // o dropdown "Gestão Financeira" no lugar do botão "Lançamentos"
            // (ver startInGestao em FinanceiroScreen.kt). "Novo Lançamento"
            // (onNewRecord/onEditRecord) continua indo pro domínio real
            // "financeiro" -- essas 6 visões são só leitura (isQuickView).
            if (domainId == "financeiro" || domainId == "gestaofinanceira") {
                FinanceiroScreen(
                    onBack = { navController.popBackStack() },
                    onNewRecord = { navController.navigate(Routes.domainFormNew("financeiro")) },
                    onEditRecord = { recordId -> navController.navigate(Routes.domainFormEdit("financeiro", recordId)) },
                    onOpenBankImport = { navController.navigate(Routes.BANK_IMPORT) },
                    onOpenNfeImport = { navController.navigate(Routes.NFE_IMPORT) },
                    startInGestao = domainId == "gestaofinanceira",
                )
            } else {
                DomainListScreen(
                    domainId = domainId,
                    onBack = { navController.popBackStack() },
                    onNewRecord = { navController.navigate(Routes.domainFormNew(domainId)) },
                    onEditRecord = { recordId -> navController.navigate(Routes.domainFormEdit(domainId, recordId)) },
                    onOpenRomaneioQuick = if (domainId == "romaneios") {
                        { navController.navigate(Routes.ROMANEIO_QUICK) }
                    } else null,
                    onOpenPragaFoto = if (domainId == "pragas") {
                        { navController.navigate(Routes.PRAGA_FOTO) }
                    } else null,
                    onOpenFrotaQr = if (domainId == "frota") {
                        { navController.navigate(Routes.FROTA_QR) }
                    } else null,
                    onOpenReconciliacaoEstoque = if (domainId == "estoque") {
                        { navController.navigate(Routes.RECONCILIACAO_ESTOQUE) }
                    } else null,
                    // Cobranças e NFS-e unificados numa única entrada do
                    // menu (ver BottomNavBar.kt) -- pedido do usuário
                    // ("unifique e me um só módulo"): esta tela genérica
                    // ganha um alternador pra trocar entre os 2 domínios
                    // sem passar pelo menu de novo.
                    linkedDomains = if (domainId == "cobrancas" || domainId == "nfse") {
                        listOf("cobrancas" to "Cobranças", "nfse" to "NFS-e")
                    } else null,
                    onSwitchDomain = if (domainId == "cobrancas" || domainId == "nfse") {
                        { target ->
                            navController.navigate(Routes.domainList(target)) {
                                popUpTo(Routes.HOME)
                                launchSingleTop = true
                            }
                        }
                    } else null,
                )
            }
        }
        composable(
            Routes.DOMAIN_FORM_NEW,
            arguments = listOf(navArgument("domainId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val domainId = backStackEntry.arguments?.getString("domainId") ?: return@composable
            // "Novo modelo" de Pedidos/Cotações (vários itens no mesmo
            // lançamento) -- pedido do usuário ("insira o novo modelo dos
            // modulos cotaçoes e pedidos no app native"), réplica de
            // data-table.tsx no site: pra CRIAR um registro novo nesses 2
            // domínios, a tela de vários itens substitui COMPLETAMENTE o
            // formulário genérico de 1 item só (igual no site, onde
            // PedidoMultiItemForm/CotacaoMultiItemForm tomam o lugar de
            // RecordForm quando "!editing"). Editar um registro já existente
            // (DOMAIN_FORM_EDIT, abaixo) continua na tela genérica -- "vários
            // itens de uma vez" só faz sentido ao criar.
            when (domainId) {
                "pedidos" -> PedidoMultiItemScreen(onBack = { navController.popBackStack() })
                "cotacoesfornecedores" -> CotacaoMultiItemScreen(onBack = { navController.popBackStack() })
                else -> DomainFormScreen(
                    domainId = domainId,
                    recordId = null,
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
            }
        }
        composable(
            Routes.DOMAIN_FORM_EDIT,
            arguments = listOf(
                navArgument("domainId") { type = NavType.StringType },
                navArgument("recordId") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val domainId = backStackEntry.arguments?.getString("domainId") ?: return@composable
            val recordId = backStackEntry.arguments?.getString("recordId") ?: return@composable
            DomainFormScreen(
                domainId = domainId,
                recordId = recordId,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }
        }
    }
}
