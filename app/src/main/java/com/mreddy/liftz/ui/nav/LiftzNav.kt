package com.mreddy.liftz.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mreddy.liftz.LiftzApp
import com.mreddy.liftz.ui.calendar.CalendarScreen
import com.mreddy.liftz.ui.common.BlinkingOfflineIcon
import com.mreddy.liftz.ui.common.OfflineBanner
import com.mreddy.liftz.ui.common.UpdateBanner
import com.mreddy.liftz.data.update.UpdateStatus
import com.mreddy.liftz.ui.editor.ExerciseEditorScreen
import com.mreddy.liftz.ui.exercise.ExerciseScreen
import com.mreddy.liftz.ui.coach.CoachScreen
import com.mreddy.liftz.ui.settings.SettingsScreen
import com.mreddy.liftz.ui.stats.StatsScreen
import com.mreddy.liftz.ui.summary.SummaryScreen
import com.mreddy.liftz.ui.workout.WorkoutScreen
import kotlinx.coroutines.launch
import java.time.LocalDate

/** All navigation routes in one place. Days are passed around as epoch days (a plain Long). */
object Routes {
    /**
     * The five bottom-nav tabs are NOT separate nav destinations — they are pages of a swipeable
     * pager living behind this single route. That is what makes left/right swiping between
     * Calendar / Today / Coach / Progress / Profile work; with independent NavHost destinations
     * there is nothing for a horizontal drag to move between.
     */
    const val HOME = "home"

    const val WORKOUT = "workout/{epochDay}"
    fun workout(epochDay: Long) = "workout/$epochDay"

    const val EXERCISE = "exercise/{exerciseId}/{epochDay}"
    fun exercise(exerciseId: String, epochDay: Long) = "exercise/$exerciseId/$epochDay"

    const val SUMMARY = "summary/{epochDay}"
    fun summary(epochDay: Long) = "summary/$epochDay"

    /** "new" creates; any other value edits that exercise. */
    const val EDITOR = "editor/{exerciseId}"
    fun editor(exerciseId: String? = null) = "editor/${exerciseId ?: "new"}"
}

/* Pager page indices. Order here is the left-to-right swipe order. */
/** Six hours: often enough that nobody sits on a stale build, rare enough to be free. */
private const val UPDATE_CHECK_INTERVAL_MS = 6L * 60 * 60 * 1000

private const val PAGE_CALENDAR = 0
private const val PAGE_TODAY = 1
private const val PAGE_COACH = 2
private const val PAGE_PROGRESS = 3
private const val PAGE_PROFILE = 4
private const val PAGE_COUNT = 5

private data class HomeTab(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private val homeTabs = listOf(
    HomeTab("Calendar", Icons.Filled.CalendarMonth),
    HomeTab("Today", Icons.Filled.FitnessCenter),
    HomeTab("Coach", Icons.Filled.Lightbulb),
    HomeTab("Progress", Icons.AutoMirrored.Filled.TrendingUp),
    HomeTab("Profile", Icons.Filled.Person)
)

@Composable
fun LiftzNavHost(navController: NavHostController = rememberNavController()) {
    val today = LocalDate.now()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // Hoisted above the NavHost so the selected tab survives pushing/popping a detail screen.
    val pagerState = rememberPagerState(initialPage = PAGE_CALENDAR) { PAGE_COUNT }
    val scope = rememberCoroutineScope()
    val onHome = currentRoute == Routes.HOME

    val showOfflineUi by LiftzApp.prefs().showOfflineIndicator.collectAsState(initial = false)
    val online by LiftzApp.connectivity().isOnline().collectAsState(initial = true)
    // Dismissing hides the strip until connectivity actually changes again, rather than
    // permanently or on a timer - so it never nags, but never silently stops telling the truth.
    var bannerDismissed by remember { mutableStateOf(false) }
    if (online) bannerDismissed = false
    val offline = showOfflineUi && !online

    /* ---- in-app update check ----
     * Runs once per Activity and at most every six hours. A sideloaded app has no store to tell
     * anyone a new build exists, so this is the only thing that closes that loop.
     */
    var update by remember { mutableStateOf<UpdateStatus.Available?>(null) }
    var downloading by remember { mutableStateOf(false) }
    var downloadPct by remember { mutableStateOf(0) }
    val skipped by LiftzApp.prefs().skippedUpdate.collectAsState(initial = null)

    LaunchedEffect(Unit) {
        val prefs = LiftzApp.prefs()
        val since = System.currentTimeMillis() - prefs.lastUpdateCheck()
        if (since < UPDATE_CHECK_INTERVAL_MS) return@LaunchedEffect
        val checker = LiftzApp.updates()
        when (val result = checker.check()) {
            is UpdateStatus.Available -> {
                prefs.setLastUpdateCheck(System.currentTimeMillis())
                update = result
            }
            // UpToDate and Unknown are both non-events. A failed check must never surface as an
            // error: the app works perfectly well without ever reaching GitHub.
            else -> prefs.setLastUpdateCheck(System.currentTimeMillis())
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                homeTabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        // Only highlight a tab when the pager is actually what is on screen; on a
                        // pushed detail screen nothing is selected.
                        selected = onHome && pagerState.currentPage == index,
                        onClick = {
                            // From a detail screen, come back to the pager first, then move to
                            // the requested page.
                            if (!onHome) {
                                navController.popBackStack(Routes.HOME, inclusive = false)
                            }
                            scope.launch { pagerState.animateScrollToPage(index) }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.HOME) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (page) {
                        PAGE_CALENDAR -> CalendarScreen(
                            onDayClick = { date ->
                                // Today opens as the adjacent pager page rather than a pushed
                                // screen, so swiping still works after tapping today's cell.
                                if (date == today) {
                                    scope.launch { pagerState.animateScrollToPage(PAGE_TODAY) }
                                } else {
                                    navController.navigate(Routes.workout(date.toEpochDay()))
                                }
                            }
                        )

                        PAGE_TODAY -> WorkoutScreen(
                            date = today,
                            onExerciseClick = { exerciseId ->
                                navController.navigate(Routes.exercise(exerciseId, today.toEpochDay()))
                            },
                            onSummaryClick = {
                                navController.navigate(Routes.summary(today.toEpochDay()))
                            },
                            onAddExercise = { navController.navigate(Routes.editor()) },
                            onEditExercise = { id -> navController.navigate(Routes.editor(id)) },
                            onDesignWithAi = {
                                scope.launch { pagerState.animateScrollToPage(PAGE_COACH) }
                            },
                            onBack = { }   // top-level page: nothing to go back to
                        )

                        PAGE_COACH -> CoachScreen()

                        PAGE_PROGRESS -> StatsScreen(
                            onExerciseClick = { exerciseId ->
                                navController.navigate(
                                    Routes.exercise(exerciseId, today.toEpochDay())
                                )
                            }
                        )

                        PAGE_PROFILE -> SettingsScreen()
                    }
                }
            }

            composable(
                route = Routes.EDITOR,
                arguments = listOf(navArgument("exerciseId") { type = NavType.StringType })
            ) { entry ->
                val raw = entry.arguments?.getString("exerciseId")
                ExerciseEditorScreen(
                    exerciseId = raw?.takeIf { it != "new" },
                    onDone = { navController.popBackStack() }
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
                    onAddExercise = { navController.navigate(Routes.editor()) },
                    onEditExercise = { id -> navController.navigate(Routes.editor(id)) },
                    onDesignWithAi = {
                        navController.popBackStack(Routes.HOME, inclusive = false)
                        scope.launch { pagerState.animateScrollToPage(PAGE_COACH) }
                    },
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
        }

            if (offline) {
                BlinkingOfflineIcon(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 14.dp, end = 16.dp)
                )
            }
            OfflineBanner(
                visible = offline && !bannerDismissed,
                onDismiss = { bannerDismissed = true },
                modifier = Modifier.align(Alignment.BottomCenter)
            )

            val pending = update
            UpdateBanner(
                // Never both banners at once: offline is the more urgent fact, and an update
                // cannot be downloaded without a connection anyway.
                visible = pending != null && !offline && pending.versionName != skipped,
                versionName = pending?.versionName.orEmpty(),
                sizeBytes = pending?.sizeBytes ?: 0L,
                downloading = downloading,
                progress = downloadPct,
                onSkip = {
                    pending?.let { scope.launch { LiftzApp.prefs().skipUpdate(it.versionName) } }
                },
                onInstall = {
                    val u = pending ?: return@UpdateBanner
                    val checker = LiftzApp.updates()
                    if (!checker.canInstallPackages()) {
                        // Android 8+ gates this per app. Send them to the toggle rather than
                        // failing with a permission error they cannot act on.
                        checker.requestInstallPermission()
                        return@UpdateBanner
                    }
                    downloading = true
                    scope.launch {
                        checker.downloadAndInstall(u) { downloadPct = it }
                        downloading = false
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
