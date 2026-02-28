/*
 * OpenSchoolCloud Calendar
 * Copyright (C) 2025 OpenSchoolCloud / Aldewereld Consultancy
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
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
import nl.openschoolcloud.calendar.presentation.screens.booking.BookingScreen
import nl.openschoolcloud.calendar.presentation.screens.calendar.CalendarScreen
import nl.openschoolcloud.calendar.presentation.screens.feedback.FeedbackScreen
import nl.openschoolcloud.calendar.presentation.screens.event.EventDetailScreen
import nl.openschoolcloud.calendar.presentation.screens.event.EventEditScreen
import nl.openschoolcloud.calendar.presentation.screens.holidays.HolidayDiscoverScreen
import nl.openschoolcloud.calendar.presentation.screens.login.LoginScreen
import nl.openschoolcloud.calendar.presentation.screens.modeselection.ModeSelectionScreen
import nl.openschoolcloud.calendar.presentation.screens.modeselection.ModeSelectionViewModel
import nl.openschoolcloud.calendar.presentation.screens.onboarding.OnboardingScreen
import nl.openschoolcloud.calendar.presentation.screens.planning.WeekPlanningScreen
import nl.openschoolcloud.calendar.presentation.screens.planning.WeekProgressScreen
import nl.openschoolcloud.calendar.presentation.screens.reflection.WeekReviewScreen
import nl.openschoolcloud.calendar.presentation.screens.settings.SettingsScreen
import nl.openschoolcloud.calendar.presentation.screens.splash.SplashScreen

/**
 * Navigation routes
 */
sealed class Route(val route: String) {
    object Splash : Route("splash")
    object Onboarding : Route("onboarding")
    object ModeSelection : Route("mode_selection")
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
    object WeekReview : Route("week-review")
    object HolidayDiscover : Route("holidays")
    object WeekPlanning : Route("week-planning")
    object WeekProgress : Route("week-progress")
    object Feedback : Route("feedback")
    object Booking : Route("booking")
    object QrCode : Route("qrcode?url={url}&name={name}") {
        fun createRoute(url: String, name: String) =
            "qrcode?url=${java.net.URLEncoder.encode(url, "UTF-8")}&name=${java.net.URLEncoder.encode(name, "UTF-8")}"
    }
}

@Composable
fun AppNavigation(
    hasAccount: Boolean = false,
    onboardingCompleted: Boolean = false,
    hasCompletedModeSelection: Boolean = false,
    isStandaloneMode: Boolean = false
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Route.Splash.route
    ) {
        // Branded splash screen
        composable(Route.Splash.route) {
            SplashScreen(
                hasAccount = hasAccount,
                onboardingCompleted = onboardingCompleted,
                hasCompletedModeSelection = hasCompletedModeSelection,
                isStandaloneMode = isStandaloneMode,
                onNavigateToCalendar = {
                    navController.navigate(Route.Calendar.route) {
                        popUpTo(Route.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToOnboarding = {
                    navController.navigate(Route.Onboarding.route) {
                        popUpTo(Route.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToModeSelection = {
                    navController.navigate(Route.ModeSelection.route) {
                        popUpTo(Route.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.navigate(Route.Login.route) {
                        popUpTo(Route.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        // Onboarding (first-time users)
        composable(Route.Onboarding.route) {
            OnboardingScreen(
                onOnboardingComplete = {
                    navController.navigate(Route.ModeSelection.route) {
                        popUpTo(Route.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        // Mode selection (standalone vs Nextcloud)
        composable(Route.ModeSelection.route) {
            val modeViewModel: ModeSelectionViewModel = hiltViewModel()
            ModeSelectionScreen(
                onStartStandalone = {
                    modeViewModel.selectStandalone {
                        navController.navigate(Route.Calendar.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                },
                onConnectNextcloud = {
                    modeViewModel.selectNextcloud {
                        navController.navigate(Route.Login.route) {
                            popUpTo(Route.ModeSelection.route) { inclusive = true }
                        }
                    }
                }
            )
        }

        // Login
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
                },
                onBookingClick = {
                    navController.navigate(Route.Booking.route)
                },
                onWeekReviewClick = {
                    navController.navigate(Route.WeekReview.route)
                },
                onWeekProgressClick = {
                    navController.navigate(Route.WeekProgress.route)
                },
                onFeedbackClick = {
                    navController.navigate(Route.Feedback.route)
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
                },
                onHolidayDiscoverClick = {
                    navController.navigate(Route.HolidayDiscover.route)
                },
                onConnectNextcloud = {
                    navController.navigate(Route.Login.route)
                }
            )
        }

        // Week review
        composable(Route.WeekReview.route) {
            WeekReviewScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Week planning
        composable(Route.WeekPlanning.route) {
            WeekPlanningScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Week progress
        composable(Route.WeekProgress.route) {
            WeekProgressScreen(
                onNavigateBack = { navController.popBackStack() },
                onPlanWeekClick = {
                    navController.navigate(Route.WeekPlanning.route)
                }
            )
        }

        // Holiday discover
        composable(Route.HolidayDiscover.route) {
            HolidayDiscoverScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Feedback
        composable(Route.Feedback.route) {
            FeedbackScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Booking links
        composable(Route.Booking.route) {
            BookingScreen(
                onNavigateBack = { navController.popBackStack() },
                onShowQrCode = { url, name ->
                    navController.navigate(Route.QrCode.createRoute(url, name))
                }
            )
        }

        // QR Code display
        composable(
            route = Route.QrCode.route,
            arguments = listOf(
                navArgument("url") { type = NavType.StringType },
                navArgument("name") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val url = backStackEntry.arguments?.getString("url")?.let {
                java.net.URLDecoder.decode(it, "UTF-8")
            } ?: return@composable
            val name = backStackEntry.arguments?.getString("name")?.let {
                java.net.URLDecoder.decode(it, "UTF-8")
            } ?: ""
            nl.openschoolcloud.calendar.presentation.screens.booking.QrCodeScreen(
                url = url,
                name = name,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
