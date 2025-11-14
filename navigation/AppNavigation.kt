package com.example.bestplannner.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.bestplannner.screens.ChatScreen
import com.example.bestplannner.screens.ConversationHistoryScreen
import com.example.bestplannner.screens.PlanScreen
import com.example.bestplannner.screens.SettingsScreen

@Composable
fun AppNavigation(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = "conversation_history"
    ) {
        composable("plan") {
            PlanScreen(navController)
        }

        composable("chat") {
            ChatScreen(navController)
        }

        composable(
            "chat/{conversationId}",
            arguments = listOf(navArgument("conversationId") { type = androidx.navigation.NavType.LongType })
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getLong("conversationId") ?: 0L
            ChatScreen(navController, conversationId)
        }

        composable("conversation_history") {
            ConversationHistoryScreen(navController)
        }

        composable("settings") {
            SettingsScreen(navController)
        }

        composable(
            "settings/{conversationId}",
            arguments = listOf(navArgument("conversationId") { type = androidx.navigation.NavType.LongType })
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getLong("conversationId")
            SettingsScreen(navController, conversationId)
        }
    }
}