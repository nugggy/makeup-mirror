package io.github.nugggy.makeupmirror.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.nugggy.makeupmirror.AppGraph
import io.github.nugggy.makeupmirror.update.UpdateDialog

private object Routes {
    const val WELCOME = "welcome"
    const val TRY_ON = "tryon"
    const val KIT = "kit"
    const val SETTINGS = "settings"
    const val EDIT = "kit/edit?id={id}"
    const val LESSON = "lesson/{lookId}"
    fun edit(id: Long?) = "kit/edit?id=${id ?: -1}"
    fun lesson(lookId: String) = "lesson/$lookId"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.TRY_ON, "Try on", Icons.Default.Face),
    Tab(Routes.KIT, "My kit", Icons.AutoMirrored.Filled.List),
    Tab(Routes.SETTINGS, "Settings", Icons.Default.Settings),
)

@Composable
fun AppRoot(graph: AppGraph) {
    val nav = rememberNavController()
    val context = LocalContext.current
    // Decided once: changing it later would rebuild the nav graph and jump back to the start.
    val startDestination = remember { if (graph.prefs.onboarded) Routes.TRY_ON else Routes.WELCOME }
    val updateState by graph.updates.state.collectAsStateWithLifecycle()
    UpdateDialog(
        state = updateState,
        onUpdate = graph.updates::startDownload,
        onInstall = { apk ->
            graph.installer.install(apk)
            graph.updates.dismiss()
        },
        onOpenPermission = { context.startActivity(graph.installer.installPermissionIntent()) },
        onDismiss = graph.updates::dismiss,
    )

    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    Scaffold(
        bottomBar = {
            if (tabs.any { it.route == route }) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(Routes.TRY_ON) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = startDestination,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.WELCOME) {
                WelcomeScreen(onDone = {
                    graph.prefs.onboarded = true
                    nav.navigate(Routes.KIT) { popUpTo(Routes.WELCOME) { inclusive = true } }
                })
            }
            composable(Routes.TRY_ON) {
                TryOnScreen(graph, onAddProducts = { nav.navigate(Routes.KIT) }, onChoose = { nav.navigate(Routes.lesson(it)) })
            }
            composable(Routes.KIT) { KitScreen(graph, onEdit = { nav.navigate(Routes.edit(it)) }) }
            composable(Routes.EDIT, arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L })) { entry ->
                val id = entry.arguments?.getLong("id")?.takeIf { it >= 0 }
                EditProductScreen(graph, id, onDone = { nav.popBackStack() })
            }
            composable(Routes.LESSON) { entry ->
                LessonScreen(graph, entry.arguments?.getString("lookId").orEmpty(), onExit = { nav.popBackStack() })
            }
            composable(Routes.SETTINGS) { SettingsScreen(graph) }
        }
    }
}
