package com.mreddy.liftz.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mreddy.liftz.ui.calendar.CalendarScreen
import com.mreddy.liftz.ui.exercise.ExerciseScreen
import com.mreddy.liftz.ui.settings.SettingsScreen
import com.mreddy.liftz.ui.summary.SummaryScreen
import com.mreddy.liftz.ui.workout.WorkoutScreen
import java.time.LocalDate

/** All navigation routes in one place. Days are passed around as epoch days (a plain Long). */
object Routes {
    const val CALENDAR = "calendar"
    const val SETTINGS = "settings"

    const val WORKOUT = "workout/{epochDay}"
    fun workout(epochDay: Long) = "workout/$epochDay"

    const val EXERCISE = "exercise/{exerciseId}/{epochDay}"
    fun exercise(exerciseId: String, epochDay: Long) = "exercise/$exerciseId/$epochDay"

    const val SUMMARY = "summary/{epochDay}"
    fun summary(epochDay: Long) = "summary/$epochDay"
}

private data class BottomItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@Composable
fun LiftzNavHost(navController: NavHostController = rememberNavController()) {
    val today = LocalDate.now()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val bottomItems = listOf(
        BottomItem(Routes.CALENDAR, "Calendar", Icons.Filled.CalendarMonth),
        BottomItem(Routes.workout(today.toEpochDay()), "Today", Icons.Filled.FitnessCenter),
        BottomItem(Routes.SETTINGS, "Profile", Icons.Filled.Person)
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                bottomItems.forEach { item ->
                    val selected = currentRoute == item.route ||
                        (item.route.startsWith("workout/") && currentRoute == Routes.WORKOUT)
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.CALENDAR,
            modifier = androidx.compose.ui.Modifier.padding(innerPadding)
        ) {
            composable(Routes.CALENDAR) {
                CalendarScreen(
                    onDayClick = { date -> navController.navigate(Routes.workout(date.toEpochDay())) }
                )
            }
            composable(
                route = Routes.WORKOUT,
                arguments = listOf(navArgument("epochDay") { type = NavType.LongType })
            ) { entry ->
                val epochDay = entry.arguments?.getLong("epochDay") ?: today.toEpochDay()
                WorkoutScreen(
                    date = LocalDate.ofEpochDay(epochDay),
                    onExerciseClick = { exerciseId ->
                        navController.navigate(Routes.exercise(exerciseId, epochDay))
                    },
                    onSummaryClick = { navController.navigate(Routes.summary(epochDay)) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Routes.SUMMARY,
                arguments = listOf(navArgument("epochDay") { type = NavType.LongType })
            ) { entry ->
                val epochDay = entry.arguments?.getLong("epochDay") ?: today.toEpochDay()
                SummaryScreen(
                    date = LocalDate.ofEpochDay(epochDay),
                    onBack = { navController.popBackStack() },
                    onExerciseClick = { exerciseId ->
                        navController.navigate(Routes.exercise(exerciseId, epochDay))
                    }
                )
            }
            composable(
                route = Routes.EXERCISE,
                arguments = listOf(
                    navArgument("exerciseId") { type = NavType.StringType },
                    navArgument("epochDay") { type = NavType.LongType }
                )
            ) { entry ->
                val exerciseId = entry.arguments?.getString("exerciseId").orEmpty()
                val epochDay = entry.arguments?.getLong("epochDay") ?: today.toEpochDay()
                ExerciseScreen(
                    exerciseId = exerciseId,
                    date = LocalDate.ofEpochDay(epochDay),
                    onFinished = { navController.popBackStack() }
                )
            }
            composable(Routes.SETTINGS) { SettingsScreen() }
        }
    }
}
