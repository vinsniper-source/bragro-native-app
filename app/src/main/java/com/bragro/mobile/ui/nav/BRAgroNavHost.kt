package com.bragro.mobile.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.bragro.mobile.data.repo.AuthRepository
import com.bragro.mobile.ui.analises.AnalisesScreen
import com.bragro.mobile.ui.dashboard.DashboardScreen
import com.bragro.mobile.ui.dre.DreScreen
import com.bragro.mobile.ui.domain.DomainFormScreen
import com.bragro.mobile.ui.domain.DomainListScreen
import com.bragro.mobile.ui.home.HomeScreen
import com.bragro.mobile.ui.login.LoginScreen
import com.bragro.mobile.ui.nfe.NfeImportScreen
import com.bragro.mobile.ui.romaneio.RomaneioQuickScreen

private object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val DASHBOARD = "dashboard"
    const val DRE = "dre"
    const val ANALISES = "analises"
    const val NFE_IMPORT = "nfe_import"
    const val ROMANEIO_QUICK = "romaneio_quick"
    const val DOMAIN_LIST = "domain/{domainId}"
    const val DOMAIN_FORM_NEW = "domain/{domainId}/new"
    const val DOMAIN_FORM_EDIT = "domain/{domainId}/edit/{recordId}"

    fun domainList(domainId: String) = "domain/$domainId"
    fun domainFormNew(domainId: String) = "domain/$domainId/new"
    fun domainFormEdit(domainId: String, recordId: String) = "domain/$domainId/edit/$recordId"
}

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
    NavHost(navController = navController, startDestination = start) {
        composable(Routes.LOGIN) {
            LoginScreen(onLoggedIn = {
                navController.navigate(Routes.HOME) { popUpTo(Routes.LOGIN) { inclusive = true } }
            })
        }
        composable(Routes.HOME) {
            HomeScreen(
                onOpenDomain = { domainId -> navController.navigate(Routes.domainList(domainId)) },
                onOpenDashboard = { navController.navigate(Routes.DASHBOARD) },
                onLoggedOut = { navController.navigate(Routes.LOGIN) { popUpTo(Routes.HOME) { inclusive = true } } },
            )
        }
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onBack = { navController.popBackStack() },
                onOpenDre = { navController.navigate(Routes.DRE) },
                onOpenAnalises = { navController.navigate(Routes.ANALISES) },
                onOpenNfeImport = { navController.navigate(Routes.NFE_IMPORT) },
                onOpenRomaneioQuick = { navController.navigate(Routes.ROMANEIO_QUICK) },
            )
        }
        composable(Routes.DRE) {
            DreScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.ANALISES) {
            AnalisesScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.NFE_IMPORT) {
            NfeImportScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.ROMANEIO_QUICK) {
            RomaneioQuickScreen(onBack = { navController.popBackStack() })
        }
        composable(
            Routes.DOMAIN_LIST,
            arguments = listOf(navArgument("domainId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val domainId = backStackEntry.arguments?.getString("domainId") ?: return@composable
            DomainListScreen(
                domainId = domainId,
                onBack = { navController.popBackStack() },
                onNewRecord = { navController.navigate(Routes.domainFormNew(domainId)) },
                onEditRecord = { recordId -> navController.navigate(Routes.domainFormEdit(domainId, recordId)) },
            )
        }
        composable(
            Routes.DOMAIN_FORM_NEW,
            arguments = listOf(navArgument("domainId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val domainId = backStackEntry.arguments?.getString("domainId") ?: return@composable
            DomainFormScreen(
                domainId = domainId,
                recordId = null,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
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
