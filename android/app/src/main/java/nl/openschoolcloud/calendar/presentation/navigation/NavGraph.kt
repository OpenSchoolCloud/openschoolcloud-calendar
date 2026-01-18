package nl.openschoolcloud.calendar.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import nl.openschoolcloud.calendar.presentation.screens.calendar.CalendarScreen
import nl.openschoolcloud.calendar.presentation.screens.event.EventDetailScreen
import nl.openschoolcloud.calendar.presentation.screens.event.EventEditScreen
import nl.openschoolcloud.calendar.presentation.screens.login.LoginScreen
import nl.openschoolcloud.calendar.presentation.screens.settings.SettingsScreen

/**
 * Navigation routes
 */
sealed class Route(val route: String) {
    object Login : Route("login")
    object Calendar : Route("calendar")
    object EventDetail : Route("event/{eventId}") {
        fun createRoute(eventId: String) = "event/$eventId"
    }
    object EventCreate : Route("event/create?date={date}") {
        fun createRoute(date: String? = null) = if (date != null) "event/create?date=$date" else "event/create"
    }
    object EventEdit : Route("event/{eventId}/edit") {
        fun createRoute(eventId: String) = "event/$eventId/edit"
    }
    object Settings : Route("settings")
}

@Composable
fun AppNavigation(
    startDestination: String = Route.Login.route
) {
    val navController = rememberNavController()
    
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Login / Onboarding
        composable(Route.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Route.Calendar.route) {
                        popUpTo(Route.Login.route) { inclusive = true }
                    }
                }
            )
        }
        
        // Main calendar view
        composable(Route.Calendar.route) {
            CalendarScreen(
                onEventClick = { eventId ->
                    navController.navigate(Route.EventDetail.createRoute(eventId))
                },
                onCreateEvent = { date ->
                    navController.navigate(Route.EventCreate.createRoute(date))
                },
                onSettingsClick = {
                    navController.navigate(Route.Settings.route)
                }
            )
        }
        
        // Event detail
        composable(
            route = Route.EventDetail.route,
            arguments = listOf(navArgument("eventId") { type = NavType.StringType })
        ) { backStackEntry ->
            val eventId = backStackEntry.arguments?.getString("eventId") ?: return@composable
            EventDetailScreen(
                eventId = eventId,
                onNavigateBack = { navController.popBackStack() },
                onEditEvent = {
                    navController.navigate(Route.EventEdit.createRoute(eventId))
                }
            )
        }
        
        // Create event
        composable(
            route = Route.EventCreate.route,
            arguments = listOf(
                navArgument("date") { 
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val date = backStackEntry.arguments?.getString("date")
            EventEditScreen(
                eventId = null,
                initialDate = date,
                onNavigateBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }
        
        // Edit event
        composable(
            route = Route.EventEdit.route,
            arguments = listOf(navArgument("eventId") { type = NavType.StringType })
        ) { backStackEntry ->
            val eventId = backStackEntry.arguments?.getString("eventId") ?: return@composable
            EventEditScreen(
                eventId = eventId,
                initialDate = null,
                onNavigateBack = { navController.popBackStack() },
                onSaved = { 
                    // Go back to detail
                    navController.popBackStack()
                }
            )
        }
        
        // Settings
        composable(Route.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onLogout = {
                    navController.navigate(Route.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
