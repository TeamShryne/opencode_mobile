package com.opencode.mobile.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.opencode.mobile.OpencodeApp
import com.opencode.mobile.data.OpencodeRepository
import com.opencode.mobile.ui.screens.ChatScreen
import com.opencode.mobile.ui.screens.ConnectionScreen
import com.opencode.mobile.ui.screens.DashboardScreen
import com.opencode.mobile.ui.screens.FilesScreen
import com.opencode.mobile.ui.screens.InfraScreen
import com.opencode.mobile.ui.screens.ProvidersScreen
import com.opencode.mobile.ui.screens.SessionsScreen
import com.opencode.mobile.ui.theme.OpencodeTheme
import com.opencode.mobile.ui.viewmodel.CatalogViewModel
import com.opencode.mobile.ui.viewmodel.ChatViewModel
import com.opencode.mobile.ui.viewmodel.ConnectionViewModel
import com.opencode.mobile.ui.viewmodel.FilesViewModel
import com.opencode.mobile.ui.viewmodel.InfraViewModel
import com.opencode.mobile.ui.viewmodel.SessionsViewModel

private fun vmFactory(repo: OpencodeRepository) = object : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(ConnectionViewModel::class.java) -> ConnectionViewModel(repo) as T
            modelClass.isAssignableFrom(SessionsViewModel::class.java) -> SessionsViewModel(repo) as T
            modelClass.isAssignableFrom(FilesViewModel::class.java) -> FilesViewModel(repo) as T
            modelClass.isAssignableFrom(CatalogViewModel::class.java) -> CatalogViewModel(repo) as T
            modelClass.isAssignableFrom(InfraViewModel::class.java) -> InfraViewModel(repo) as T
            else -> throw IllegalArgumentException("unknown VM")
        }
    }
}

@Composable
fun AppNav(app: OpencodeApp) {
    OpencodeTheme {
        val nav = rememberNavController()
        val repo = app.repo
        val factory = vmFactory(repo)
        val backstack by nav.currentBackStackEntryAsState()
        val route = backstack?.destination?.route ?: Routes.CONNECTION
        val showBar = route != Routes.CONNECTION && !route.startsWith("chat/")

        Scaffold(
            bottomBar = {
                if (showBar) {
                    NavigationBar {
                        NavigationBarItem(
                            selected = route == Routes.DASHBOARD,
                            onClick = { nav.navigate(Routes.DASHBOARD) { launchSingleTop = true } },
                            icon = { Icon(Icons.Filled.Home, null) }, label = { Text("Home") }
                        )
                        NavigationBarItem(
                            selected = route == Routes.SESSIONS,
                            onClick = { nav.navigate(Routes.SESSIONS) { launchSingleTop = true } },
                            icon = { Icon(Icons.Filled.List, null) }, label = { Text("Chats") }
                        )
                        NavigationBarItem(
                            selected = route == Routes.FILES,
                            onClick = { nav.navigate(Routes.FILES) { launchSingleTop = true } },
                            icon = { Icon(Icons.Filled.Search, null) }, label = { Text("Files") }
                        )
                        NavigationBarItem(
                            selected = route == Routes.PROVIDERS,
                            onClick = { nav.navigate(Routes.PROVIDERS) { launchSingleTop = true } },
                            icon = { Icon(Icons.Filled.Star, null) }, label = { Text("Models") }
                        )
                        NavigationBarItem(
                            selected = route == Routes.INFRA,
                            onClick = { nav.navigate(Routes.INFRA) { launchSingleTop = true } },
                            icon = { Icon(Icons.Filled.Settings, null) }, label = { Text("Infra") }
                        )
                    }
                }
            }
        ) { pad ->
            NavHost(nav, startDestination = Routes.CONNECTION, modifier = Modifier.padding(pad)) {
                composable(Routes.CONNECTION) {
                    val vm: ConnectionViewModel = viewModel(factory = factory)
                    ConnectionScreen(vm) { nav.navigate(Routes.DASHBOARD) { popUpTo(Routes.CONNECTION) { inclusive = true } } }
                }
                composable(Routes.DASHBOARD) {
                    val vm: InfraViewModel = viewModel(factory = factory)
                    DashboardScreen(vm)
                }
                composable(Routes.SESSIONS) {
                    val vm: SessionsViewModel = viewModel(factory = factory)
                    SessionsScreen(vm) { id -> nav.navigate(Routes.chat(id)) }
                }
                composable(
                    Routes.CHAT,
                    arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
                ) { entry ->
                    val id = entry.arguments?.getString("sessionId") ?: return@composable
                    val vm: ChatViewModel = viewModel(
                        key = "chat-$id",
                        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                                ChatViewModel(repo, id) as T
                        }
                    )
                    ChatScreen(vm, id) { newId -> nav.navigate(Routes.chat(newId)) }
                }
                composable(Routes.FILES) {
                    val vm: FilesViewModel = viewModel(factory = factory)
                    FilesScreen(vm)
                }
                composable(Routes.PROVIDERS) {
                    val vm: CatalogViewModel = viewModel(factory = factory)
                    ProvidersScreen(vm, onPickModel = { _, _ -> nav.navigate(Routes.SESSIONS) })
                }
                composable(Routes.INFRA) {
                    InfraScreen(repo)
                }
            }
        }
    }
}
