package com.bragro.mobile.ui.nav

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.bragro.mobile.ui.nfe.NotaMultiItemScreen
import com.bragro.mobile.ui.pedidos.PedidoMultiItemScreen
import com.bragro.mobile.ui.cotacoes.CotacaoMultiItemScreen
import com.bragro.mobile.ui.operacoes.OperacoesScreen
import com.bragro.mobile.ui.romaneio.RomaneioQuickScreen
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
    const val NOTA_MULTI_ITEM = "nota_multi_item"
    const val CONTROLE_INSUMOS = "controle_insumos"
    const val OPERACOES = "operacoes"
    const val ROMANEIO_QUICK = "romaneio_quick"
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
            )
        }
        composable(Routes.NFE_IMPORT) {
            NfeImportScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.NOTA_MULTI_ITEM) {
            NotaMultiItemScreen(onBack = { navController.popBackStack() })
        }
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
            if (domainId == "financeiro") {
                FinanceiroScreen(
                    onBack = { navController.popBackStack() },
                    onNewRecord = { navController.navigate(Routes.domainFormNew(domainId)) },
                    onEditRecord = { recordId -> navController.navigate(Routes.domainFormEdit(domainId, recordId)) },
                    onOpenBankImport = { navController.navigate(Routes.BANK_IMPORT) },
                    onOpenNfeImport = { navController.navigate(Routes.NFE_IMPORT) },
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
