package com.raviga.downwork.ui.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.raviga.downwork.data.api.ApiException
import com.raviga.downwork.ui.LocalAppContainer
import com.raviga.downwork.ui.capture.CaptureScreen
import com.raviga.downwork.ui.components.ErrorState
import com.raviga.downwork.ui.components.PrimaryButton
import com.raviga.downwork.ui.components.ScreenScaffold
import com.raviga.downwork.ui.credits.CreditsScreen
import com.raviga.downwork.ui.document.DocumentScreen
import com.raviga.downwork.ui.document.SectionEditorScreen
import com.raviga.downwork.ui.document.VersionPreviewScreen
import com.raviga.downwork.ui.document.VersionsScreen
import com.raviga.downwork.ui.home.HomeScreen
import com.raviga.downwork.ui.nav.Routes
import com.raviga.downwork.ui.onboarding.AiConsentScreen
import com.raviga.downwork.ui.onboarding.TermsScreen
import com.raviga.downwork.ui.onboarding.WelcomeScreen
import com.raviga.downwork.ui.openLink
import com.raviga.downwork.ui.quote.ConnectAwsScreen
import com.raviga.downwork.ui.quote.QuoteScreen
import com.raviga.downwork.ui.settings.DeliveryTargetsScreen
import com.raviga.downwork.ui.settings.NotificationsScreen
import com.raviga.downwork.ui.settings.PrivacyScreen
import com.raviga.downwork.ui.settings.RecoveryScreen
import com.raviga.downwork.ui.settings.SettingsScreen
import com.raviga.downwork.ui.status.DeliveryScreen
import com.raviga.downwork.ui.status.StatusScreen
import com.raviga.downwork.ui.theme.Dw
import com.raviga.downwork.ui.theme.DwType
import com.raviga.downwork.ui.theme.Ink
import androidx.compose.ui.platform.LocalContext

@Composable
fun AppNavHost(nav: NavHostController, pendingDeepLinkProject: String?, onDeepLinkConsumed: () -> Unit) {
    val container = LocalAppContainer.current
    val app: AppViewModel = viewModel { AppViewModel(container) }
    val state by app.state.collectAsStateWithLifecycle()

    if (!state.ready) {
        if (state.error != null) {
            ScreenScaffold { padding ->
                ErrorState(state.error!!, Modifier.padding(padding), onRetry = { app.bootstrap() })
            }
        } else {
            ScreenScaffold { _ -> }
        }
        return
    }
    if (state.upgradeRequired) {
        UpgradeScreen()
        return
    }

    LifecycleEventEffect(Lifecycle.Event.ON_START) { app.onForeground() }
    LaunchedEffect(state.legalDue) {
        if (!state.legalDue) return@LaunchedEffect
        val here = nav.currentDestination?.route
        if (here != Routes.TERMS && here != Routes.WELCOME) nav.navigate(Routes.TERMS) { launchSingleTop = true }
        app.legalShown()
    }

    LaunchedEffect(state.restartAt) {
        val route = state.restartAt ?: return@LaunchedEffect
        nav.navigate(route) { popUpTo(0) { inclusive = true } }
        app.restartConsumed()
    }

    LaunchedEffect(pendingDeepLinkProject) {
        val id = pendingDeepLinkProject ?: return@LaunchedEffect
        // Onboarding (welcome, terms) comes first; the project is on Home afterwards.
        if (state.startRoute == Routes.HOME) {
            val projects = container.projects
            projects.warmProject(id)
            val status = projects.cachedProject(id)?.status
                ?: projects.summaries.value.firstOrNull { it.id == id }?.status
                ?: try { projects.refresh(id).status } catch (e: ApiException) { null }
            if (status != null) nav.navigate(Routes.forProject(id, status)) { launchSingleTop = true }
        }
        // Consumed last: clearing it earlier would restart this effect mid-lookup.
        onDeepLinkConsumed()
    }

    NavHost(navController = nav, startDestination = state.startRoute) {
        composable(Routes.WELCOME) { WelcomeScreen(nav) }
        composable(Routes.TERMS) { TermsScreen(nav) }
        composable(Routes.HOME) { HomeScreen(nav) }
        composable(Routes.AI_CONSENT) { AiConsentScreen(nav) }
        composable(
            Routes.CAPTURE,
            arguments = listOf(
                navArgument("projectId") { type = NavType.StringType },
                navArgument("mode") { type = NavType.StringType; defaultValue = "new" },
                navArgument("tab") { type = NavType.StringType; defaultValue = "speak" },
            ),
        ) { entry ->
            CaptureScreen(
                nav = nav,
                projectId = entry.arguments?.getString("projectId") ?: "new",
                mode = entry.arguments?.getString("mode") ?: "new",
                tab = entry.arguments?.getString("tab") ?: "speak",
            )
        }
        composable(
            Routes.DOCUMENT,
            arguments = listOf(
                navArgument("projectId") { type = NavType.StringType },
                navArgument("reveal") { type = NavType.BoolType; defaultValue = false },
            ),
        ) { entry ->
            DocumentScreen(
                nav = nav,
                projectId = entry.arguments?.getString("projectId").orEmpty(),
                reveal = entry.arguments?.getBoolean("reveal") ?: false,
            )
        }
        composable(Routes.SECTION) { entry ->
            SectionEditorScreen(
                nav = nav,
                projectId = entry.arguments?.getString("projectId").orEmpty(),
                sectionId = entry.arguments?.getString("sectionId").orEmpty(),
            )
        }
        composable(Routes.VERSIONS) { entry ->
            VersionsScreen(nav, entry.arguments?.getString("projectId").orEmpty())
        }
        composable(
            Routes.VERSION,
            arguments = listOf(navArgument("version") { type = NavType.IntType }),
        ) { entry ->
            VersionPreviewScreen(
                nav,
                entry.arguments?.getString("projectId").orEmpty(),
                entry.arguments?.getInt("version") ?: 0,
            )
        }
        composable(Routes.QUOTE) { entry -> QuoteScreen(nav, entry.arguments?.getString("projectId").orEmpty()) }
        composable(Routes.CONNECT_AWS) { ConnectAwsScreen(nav) }
        composable(Routes.STATUS) { entry -> StatusScreen(nav, entry.arguments?.getString("projectId").orEmpty()) }
        composable(Routes.DELIVERY) { entry -> DeliveryScreen(nav, entry.arguments?.getString("projectId").orEmpty()) }
        composable(Routes.GO_LIVE) { entry -> com.raviga.downwork.ui.status.GoLiveScreen(nav, entry.arguments?.getString("projectId").orEmpty()) }
        composable(Routes.CREDITS) { CreditsScreen(nav) }
        composable(Routes.SETTINGS) { SettingsScreen(nav) }
        composable(Routes.DELIVERY_TARGETS) { DeliveryTargetsScreen(nav) }
        composable(Routes.PRIVACY) { PrivacyScreen(nav) }
        composable(Routes.RECOVERY) { RecoveryScreen(nav) }
        composable(Routes.FONTS) { com.raviga.downwork.ui.settings.FontsScreen(nav) }
        composable(Routes.NOTIFICATIONS) { NotificationsScreen(nav) }
    }
}

@Composable
private fun UpgradeScreen() {
    val context = LocalContext.current
    ScreenScaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(Dw.gutter)) {
            Spacer(Modifier.height(48.dp))
            Text("Update DownWork", style = DwType.title, color = Ink.ink)
            Spacer(Modifier.height(16.dp))
            Text("This version is too old to talk to DownWork. Update it from the Play Store to continue.", style = DwType.body, color = Ink.graphite)
            Spacer(Modifier.height(32.dp))
            PrimaryButton("Open Play Store", onClick = { openLink(context, "https://play.google.com/store/apps/details?id=com.raviga.downwork") })
        }
    }
}
