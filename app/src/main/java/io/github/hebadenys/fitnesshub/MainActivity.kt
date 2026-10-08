package io.github.hebadenys.fitnesshub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.hebadenys.fitnesshub.feature.activity.ActivityScreen
import io.github.hebadenys.fitnesshub.feature.body.BodyScreen
import io.github.hebadenys.fitnesshub.feature.dashboard.DashboardScreen
import io.github.hebadenys.fitnesshub.feature.insights.InsightsScreen
import io.github.hebadenys.fitnesshub.feature.nutrition.NutritionScreen
import io.github.hebadenys.fitnesshub.feature.onboarding.OnboardingScreen
import io.github.hebadenys.fitnesshub.feature.onboarding.OnboardingViewModel
import io.github.hebadenys.fitnesshub.feature.settings.AiSettingsScreen
import io.github.hebadenys.fitnesshub.feature.settings.HealthConnectSourceScreen
import io.github.hebadenys.fitnesshub.feature.settings.LocalScaleSettingsScreen
import io.github.hebadenys.fitnesshub.feature.settings.SettingsScreen
import io.github.hebadenys.fitnesshub.feature.sleep.SleepScreen
import io.github.hebadenys.fitnesshub.feature.workout.WorkoutScreen
import io.github.hebadenys.fitnesshub.feature.xiaomi.XiaomiSourceScreen
import io.github.hebadenys.fitnesshub.ui.theme.FitnessHubTheme
import io.github.hebadenys.fitnesshub.ui.theme.PerformanceTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FitnessHubTheme {
                FitnessHubRoot()
            }
        }
    }
}

@Composable
private fun FitnessHubRoot(viewModel: OnboardingViewModel = hiltViewModel()) {
    val completed by viewModel.completed.collectAsStateWithLifecycle()
    if (completed) {
        FitnessHubAppRoot()
    } else {
        OnboardingScreen(onContinue = viewModel::complete, onSkip = viewModel::complete)
    }
}

private enum class Screen(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
    val cdRes: Int
) {
    Dashboard("dashboard", R.string.nav_dashboard, Icons.Default.Home, R.string.cd_icon_dashboard),
    Activity("activity", R.string.nav_activity, Icons.Default.LocationOn, R.string.cd_icon_activity),
    Sleep("sleep", R.string.nav_sleep, Icons.Default.DateRange, R.string.cd_icon_sleep),
    Body("body", R.string.nav_body, Icons.Default.Favorite, R.string.cd_icon_body),
    Nutrition("nutrition", R.string.nav_nutrition, Icons.Default.Restaurant, R.string.cd_icon_nutrition),
    Workout("workout", R.string.nav_workout, Icons.Default.FitnessCenter, R.string.cd_icon_workout);

    companion object {
        const val SETTINGS_ROUTE = "settings"
        const val INSIGHTS_ROUTE = "insights"
        const val XIAOMI_ROUTE = "sources/xiaomi"
        const val HEALTH_CONNECT_ROUTE = "sources/health-connect"
        const val LOCAL_SCALE_ROUTE = "sources/local-scale"
        const val AI_ROUTE = "settings/ai"
    }
}

@Composable
internal fun FitnessHubAppRoot(
    dashboardContent: @Composable (onSettings: () -> Unit) -> Unit = { onSettings ->
        DashboardScreen(onNavigateToSettings = onSettings)
    },
    settingsContent: @Composable (onBack: () -> Unit, navigate: (String) -> Unit) -> Unit = { onBack, navigate ->
        SettingsScreen(
            onBack = onBack,
            onNavigateToHealthConnect = { navigate(Screen.HEALTH_CONNECT_ROUTE) },
            onNavigateToXiaomi = { navigate(Screen.XIAOMI_ROUTE) },
            onNavigateToScale = { navigate(Screen.LOCAL_SCALE_ROUTE) },
            onNavigateToInsights = { navigate(Screen.INSIGHTS_ROUTE) },
            onNavigateToAi = { navigate(Screen.AI_ROUTE) }
        )
    }
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Dashboard.route

    Scaffold(
        bottomBar = {
            if (Screen.entries.any { it.route == currentRoute }) {
                PerformanceTheme {
                    NavigationBar(modifier = Modifier.testTag("main_navigation")) {
                        Screen.entries.forEach { screen ->
                            val selected = currentRoute == screen.route
                            NavigationBarItem(
                                selected = selected,
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = MaterialTheme.colorScheme.primary,
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary
                                ),
                                onClick = {
                                    if (!selected) {
                                        navController.navigate(screen.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = screen.icon,
                                        contentDescription = stringResource(screen.cdRes)
                                    )
                                },
                                label = { Text(text = stringResource(screen.labelRes)) }
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.Dashboard.route) {
                PerformanceTheme {
                    dashboardContent(
                        {
                            navController.navigate(Screen.SETTINGS_ROUTE) {
                                launchSingleTop = true
                            }
                        }
                    )
                }
            }
            composable(Screen.Activity.route) {
                ActivityScreen(
                    onNavigateToSettings = {
                        navController.navigate(Screen.SETTINGS_ROUTE) {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(Screen.Sleep.route) {
                SleepScreen(
                    onNavigateToSettings = {
                        navController.navigate(Screen.SETTINGS_ROUTE) {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(Screen.Body.route) {
                BodyScreen(
                    onNavigateToSettings = {
                        navController.navigate(Screen.SETTINGS_ROUTE) {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(Screen.Nutrition.route) { NutritionScreen() }
            composable(Screen.Workout.route) { WorkoutScreen() }
            composable(Screen.SETTINGS_ROUTE) {
                PerformanceTheme {
                    settingsContent(
                        { navController.popBackStack() },
                        { route ->
                            navController.navigate(route) { launchSingleTop = true }
                        }
                    )
                }
            }
            composable(Screen.INSIGHTS_ROUTE) {
                InsightsScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(Screen.XIAOMI_ROUTE) {
                XiaomiSourceScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.HEALTH_CONNECT_ROUTE) {
                HealthConnectSourceScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.LOCAL_SCALE_ROUTE) {
                LocalScaleSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.AI_ROUTE) {
                AiSettingsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
