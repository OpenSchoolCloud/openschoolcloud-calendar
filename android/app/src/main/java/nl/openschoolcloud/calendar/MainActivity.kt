package nl.openschoolcloud.calendar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import nl.openschoolcloud.calendar.data.remote.auth.CredentialStorage
import nl.openschoolcloud.calendar.presentation.navigation.AppNavigation
import nl.openschoolcloud.calendar.presentation.navigation.Route
import nl.openschoolcloud.calendar.presentation.theme.OpenSchoolCloudCalendarTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var credentialStorage: CredentialStorage

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen before super.onCreate()
        installSplashScreen()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Determine start destination based on whether there are existing accounts
        val startDestination = try {
            if (credentialStorage.hasAnyAccount()) {
                Route.Calendar.route
            } else {
                Route.Login.route
            }
        } catch (e: Exception) {
            Route.Login.route
        }

        setContent {
            OpenSchoolCloudCalendarTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(startDestination = startDestination)
                }
            }
        }
    }
}
