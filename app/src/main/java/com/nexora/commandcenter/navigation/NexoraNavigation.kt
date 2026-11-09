package com.nexora.commandcenter.navigation

import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nexora.commandcenter.core.designsystem.NexoraBottomBar
import com.nexora.commandcenter.core.designsystem.NexoraGlyph
import com.nexora.commandcenter.core.designsystem.NexoraNavTabItem
import com.nexora.commandcenter.core.designsystem.NexoraTopBar
import com.nexora.commandcenter.features.analytics.AnalyticsScreen
import com.nexora.commandcenter.features.assistant.AiConnectionTestScreen
import com.nexora.commandcenter.features.assistant.AssistantScreen
import com.nexora.commandcenter.features.commercial.CommercialHubScreen
import com.nexora.commandcenter.features.home.HomeScreen
import com.nexora.commandcenter.features.home.HomeViewModel
import com.nexora.commandcenter.features.network.NetworkScreen
import com.nexora.commandcenter.features.settings.SettingsScreen
import com.nexora.commandcenter.features.tasks.TasksScreen
import com.nexora.commandcenter.features.tools.ToolsScreen
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

enum class NexoraDestination(
    val route: String,
    val labelEn: String,
    val labelFa: String,
    val glyph: NexoraGlyph
) {
    HOME("home", "Home", "خانه", NexoraGlyph.HOME),
    ASSISTANT("assistant", "AI Core", "هوش مصنوعی", NexoraGlyph.ORB),
    TOOLS("tools", "Tools", "ابزارها", NexoraGlyph.TOOLS),
    ANALYTICS("analytics", "Analytics", "تحلیل‌ها", NexoraGlyph.ANALYTICS),
    NETWORK("network", "Network", "شبکه", NexoraGlyph.NETWORK),
    SETTINGS("settings", "Settings", "تنظیمات", NexoraGlyph.SETTINGS)
}

@Composable
fun NexoraMainScaffold(
    languageCode: String,
    isDarkMode: Boolean
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val rawRoute = navBackStackEntry?.destination?.route ?: NexoraDestination.HOME.route
    val currentBaseRoute = rawRoute.substringBefore("?")

    val homeViewModel: HomeViewModel = hiltViewModel()
    val isOnline by homeViewModel.isOnlineFlow.collectAsState(initial = true)
    val isFa = languageCode == "fa"

    var timeString by remember {
        mutableStateOf(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")))
    }
    LaunchedEffect(Unit) {
        while (true) {
            timeString = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
            delay(1000L)
        }
    }

    val navItems = remember(isFa) {
        NexoraDestination.entries.map { dest ->
            NexoraNavTabItem(
                route = dest.route,
                label = if (isFa) dest.labelFa else dest.labelEn,
                glyph = dest.glyph
            )
        }
    }

    Scaffold(
        topBar = {
            NexoraTopBar(
                title = if (isFa) "نکسورا · NEXORA" else "NEXORA",
                subtitle = if (isFa) "مرکز فرماندهی هوش مصنوعی شخصی" else "YOUR PERSONAL AI COMMAND CENTER",
                isOnline = isOnline,
                timeString = timeString,
                onOpenTasks = {
                    navController.navigate("tasks") {
                        launchSingleTop = true
                    }
                }
            )
        },
        bottomBar = {
            NexoraBottomBar(
                items = navItems,
                currentRoute = currentBaseRoute,
                onSelectRoute = { route ->
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = NexoraDestination.HOME.route,
            enterTransition = { fadeIn(animationSpec = tween(220)) },
            exitTransition = { fadeOut(animationSpec = tween(180)) },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            composable(NexoraDestination.HOME.route) {
                HomeScreen(
                    isFa = isFa,
                    onNavigateToRoute = { route ->
                        navController.navigate(route) {
                            launchSingleTop = true
                        }
                    },
                    onLaunchPromptInAssistant = { prompt ->
                        val encoded = Uri.encode(prompt)
                        navController.navigate("${NexoraDestination.ASSISTANT.route}?prompt=$encoded") {
                            launchSingleTop = true
                        }
                    },
                    onOpenToolId = { toolId ->
                        val encoded = Uri.encode(toolId)
                        navController.navigate("${NexoraDestination.TOOLS.route}?tool=$encoded") {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(
                route = "${NexoraDestination.ASSISTANT.route}?prompt={prompt}",
                arguments = listOf(
                    navArgument("prompt") {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            ) { backStackEntry ->
                val initialPrompt = backStackEntry.arguments?.getString("prompt").orEmpty()
                AssistantScreen(
                    isFa = isFa,
                    initialPrompt = initialPrompt,
                    onOpenConnectionTest = {
                        navController.navigate("ai_diagnostics") {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable("ai_diagnostics") {
                AiConnectionTestScreen(
                    isFa = isFa,
                    onBackToAssistant = {
                        navController.popBackStack()
                    }
                )
            }
            composable(
                route = "${NexoraDestination.TOOLS.route}?tool={tool}",
                arguments = listOf(
                    navArgument("tool") {
                        type = NavType.StringType
                        defaultValue = "calculator"
                    }
                )
            ) { backStackEntry ->
                val initialToolId = backStackEntry.arguments?.getString("tool") ?: "calculator"
                ToolsScreen(
                    isFa = isFa,
                    initialToolId = initialToolId,
                    onOpenNetworkScreen = {
                        navController.navigate(NexoraDestination.NETWORK.route) {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(NexoraDestination.ANALYTICS.route) {
                AnalyticsScreen(isFa = isFa)
            }
            composable(NexoraDestination.NETWORK.route) {
                NetworkScreen(isFa = isFa)
            }
            composable("tasks") {
                TasksScreen(isFa = isFa)
            }
            composable("commercial") {
                CommercialHubScreen(isFa = isFa)
            }
            composable(NexoraDestination.SETTINGS.route) {
                SettingsScreen(
                    isFa = isFa,
                    onOpenCommercialHub = {
                        navController.navigate("commercial") {
                            launchSingleTop = true
                        }
                    },
                    onOpenAiConnectionTest = {
                        navController.navigate("ai_diagnostics") {
                            launchSingleTop = true
                        }
                    }
                )
            }
        }
    }
}
