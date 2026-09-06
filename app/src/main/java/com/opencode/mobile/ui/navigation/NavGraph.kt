package com.opencode.mobile.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.opencode.mobile.OpencodeApp
import com.opencode.mobile.ui.screens.AgentHomeScreen
import com.opencode.mobile.ui.screens.ConnectionScreen
import com.opencode.mobile.ui.theme.OpencodeTheme
import com.opencode.mobile.ui.viewmodel.ConnectionViewModel

@Composable
fun AppNav(app: OpencodeApp) {
    OpencodeTheme {
        val nav = rememberNavController()
        val repo = app.repo
        NavHost(nav, startDestination = Routes.CONNECTION) {
            composable(Routes.CONNECTION) {
                val vm: ConnectionViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T =
                            ConnectionViewModel(repo) as T
                    }
                )
                ConnectionScreen(vm) {
                    nav.navigate(Routes.HOME) {
                        popUpTo(Routes.CONNECTION) { inclusive = true }
                    }
                }
            }
            composable(Routes.HOME) {
                AgentHomeScreen(
                    repo = repo,
                    initialSessionId = null,
                    onDisconnect = {
                        nav.navigate(Routes.CONNECTION) {
                            popUpTo(Routes.HOME) { inclusive = true }
                        }
                    }
                )
            }
            composable(
                Routes.CHAT,
                arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
            ) { entry ->
                val id = entry.arguments?.getString("sessionId")
                AgentHomeScreen(
                    repo = repo,
                    initialSessionId = id,
                    onDisconnect = {
                        nav.navigate(Routes.CONNECTION) {
                            popUpTo(Routes.CHAT) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}
