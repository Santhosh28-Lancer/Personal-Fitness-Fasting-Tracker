
package com.santhosh.lifeos

import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.provider.CalendarContract.Calendars
import android.content.pm.PackageManager
import android.content.ContentValues
import android.Manifest
import android.content.Intent
import android.content.Context
import android.os.Bundle
import android.provider.CalendarContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Calendar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Star
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.compositionLocalOf

private val Muted = Color(0xFFA8B5C8)
private val LocalLifeOsThemeId =
    compositionLocalOf { "sunny" }
private data class TaskItem(
    val title: String,
    val done: Boolean = false
)

private data class GrandLineCalendarEvent(
    val id: Long,
    val title: String,
    val startMillis: Long,
    val endMillis: Long
)

private data class WorkoutDay(
    val day: Int,
    val rawText: String
)

private data class ThemeOption(
    val id: String,
    val name: String,
    val subtitle: String,
    val price: Int,
    val background: Long,
    val surface: Long,
    val accent: Long,
    val style: String
)

private val themes = listOf(
    ThemeOption(
        "sunny",
        "Thousand Sunny",
        "Adventure • Ocean • Treasure",
        0,
        0xFF071827,
        0xFF102B40,
        0xFFFFC857,
        "voyage"
    ),
    ThemeOption(
        "strawhat",
        "Straw Hat Crew",
        "Courage • Freedom • Dreams",
        250,
        0xFF211014,
        0xFF351B22,
        0xFFFF6557,
        "crew"
    ),
    ThemeOption(
        "swordsman",
        "Zoro Training Dojo",
        "Discipline • Strength • Focus",
        350,
        0xFF09130F,
        0xFF17251E,
        0xFF73E2A0,
        "dojo"
    ),
    ThemeOption(
        "navigator",
        "Law Tactical",
        "Precision • Strategy • Control",
        300,
        0xFF0B1020,
        0xFF171F35,
        0xFF78BFFF,
        "tactical"
    ),
    ThemeOption(
        "wanted",
        "Wanted Poster",
        "Bounties • Reputation • Rewards",
        500,
        0xFF21180D,
        0xFF382819,
        0xFFFFC66D,
        "parchment"
    )
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            LifeOSApp()
        }
    }
}

@Composable
private fun LifeOSApp() {
    val context = androidx.compose.ui.platform.LocalContext.current

    val prefs = remember {
        context.getSharedPreferences("lifeos_v2", Context.MODE_PRIVATE)
    }

    val taskPrefs = remember {
        context.getSharedPreferences("lifeos_tasks", Context.MODE_PRIVATE)
    }

    val fastingPrefs = remember {
        context.getSharedPreferences("lifeos_fasting", Context.MODE_PRIVATE)
    }

    val workoutPrefs = remember {
        context.getSharedPreferences("lifeos_workouts", Context.MODE_PRIVATE)
    }

    var tab by remember { mutableIntStateOf(0) }
    var showWorkoutImport by remember { mutableStateOf(false) }
    var showAddTask by remember { mutableStateOf(false) }
    var newTask by remember { mutableStateOf("") }

    val calendarEvents = remember { mutableStateListOf<GrandLineCalendarEvent>() }
    var calendarStatus by remember { mutableStateOf("Connect your calendar to see upcoming events.") }

    fun hasCalendarPermissions(): Boolean {
        val readGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        val writeGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.WRITE_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        return readGranted && writeGranted
    }

    fun refreshGrandLineCalendar() {
        if (!hasCalendarPermissions()) {
            calendarStatus = "Grant calendar access to load your events."
            return
        }
        try {
            calendarEvents.clear()
            val now = System.currentTimeMillis()
            val endWindow = now + 7L * 24L * 60L * 60L * 1000L
            val projection = arrayOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND
            )
            val selection = "(${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?) AND ${CalendarContract.Events.DELETED}=0"
            val args = arrayOf(now.toString(), endWindow.toString())
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                args,
                "${CalendarContract.Events.DTSTART} ASC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(CalendarContract.Events._ID)
                val titleCol = cursor.getColumnIndexOrThrow(CalendarContract.Events.TITLE)
                val startCol = cursor.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)
                val endCol = cursor.getColumnIndexOrThrow(CalendarContract.Events.DTEND)
                while (cursor.moveToNext()) {
                    calendarEvents.add(
                        GrandLineCalendarEvent(
                            id = cursor.getLong(idCol),
                            title = cursor.getString(titleCol) ?: "Untitled event",
                            startMillis = cursor.getLong(startCol),
                            endMillis = if (cursor.isNull(endCol)) cursor.getLong(startCol) + 30L * 60L * 1000L else cursor.getLong(endCol)
                        )
                    )
                }
            }
            calendarStatus = if (calendarEvents.isEmpty()) {
                "Connected. No upcoming events found in the next 7 days."
            } else {
                "Connected • ${calendarEvents.size} upcoming event(s)"
            }
        } catch (e: Exception) {
            calendarStatus = "Couldn't read calendar: ${e.localizedMessage ?: "unknown error"}"
        }
    }

    fun createGrandLineCalendarEvent(title: String, description: String): Boolean {
        if (!hasCalendarPermissions()) {
            calendarStatus = "Grant calendar access before scheduling a mission."
            return false
        }
        return try {
            var calendarId: Long? = null
            val calendarProjection = arrayOf(
                Calendars._ID,
                Calendars.ACCOUNT_TYPE,
                Calendars.CALENDAR_ACCESS_LEVEL,
                Calendars.IS_PRIMARY
            )
            context.contentResolver.query(
                Calendars.CONTENT_URI,
                calendarProjection,
                "${Calendars.VISIBLE}=1 AND ${Calendars.CALENDAR_ACCESS_LEVEL} >= ?",
                arrayOf(Calendars.CAL_ACCESS_CONTRIBUTOR.toString()),
                "${Calendars.IS_PRIMARY} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(Calendars._ID)
                val typeCol = cursor.getColumnIndexOrThrow(Calendars.ACCOUNT_TYPE)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val accountType = cursor.getString(typeCol).orEmpty()
                    if (calendarId == null) calendarId = id
                    if (accountType == "com.google") {
                        calendarId = id
                        break
                    }
                }
            }

            val selectedCalendarId = calendarId
            if (selectedCalendarId == null) {
                calendarStatus = "No writable calendar found. Add or sync a Google account in Google Calendar first."
                false
            } else {
                val start = Calendar.getInstance().apply {
                    add(Calendar.MINUTE, 5)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    val minute = get(Calendar.MINUTE)
                    set(Calendar.MINUTE, ((minute + 4) / 5) * 5)
                }.timeInMillis
                val values = ContentValues().apply {
                    put(CalendarContract.Events.CALENDAR_ID, selectedCalendarId)
                    put(CalendarContract.Events.TITLE, title)
                    put(CalendarContract.Events.DESCRIPTION, description)
                    put(CalendarContract.Events.DTSTART, start)
                    put(CalendarContract.Events.DTEND, start + 30L * 60L * 1000L)
                    put(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)
                    put(CalendarContract.Events.HAS_ALARM, 0)
                }
                val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                val success = uri != null
                calendarStatus = if (success) "Mission added to your device calendar." else "Calendar provider couldn't save the event."
                if (success) refreshGrandLineCalendar()
                success
            }
        } catch (e: Exception) {
            calendarStatus = "Couldn't create event: ${e.localizedMessage ?: "unknown error"}"
            false
        }
    }

    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.READ_CALENDAR] == true &&
            result[Manifest.permission.WRITE_CALENDAR] == true
        if (granted) {
            refreshGrandLineCalendar()
        } else {
            calendarStatus = "Calendar permission was not granted."
        }
    }

    fun requestCalendarAccess() {
        calendarPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.WRITE_CALENDAR
            )
        )
    }

    var berries by remember {
        mutableIntStateOf(prefs.getInt("berries", 0))
    }

    var selectedThemeId by remember {
        mutableStateOf(prefs.getString("theme", "sunny") ?: "sunny")
    }

    val unlockedThemes = remember {
        mutableStateListOf<String>().apply {
            addAll(
                prefs.getStringSet(
                    "unlocked_themes",
                    setOf("sunny")
                ) ?: setOf("sunny")
            )

            if ("sunny" !in this) add("sunny")
        }
    }

    val currentTheme = themes.firstOrNull {
        it.id == selectedThemeId
    } ?: themes.first()

    val background = Color(currentTheme.background)
    val surface = Color(currentTheme.surface)
    val accent = Color(currentTheme.accent)

    val tasks = remember {
        mutableStateListOf<TaskItem>().apply {
            val count = taskPrefs.getInt("count", -1)

            if (count < 0) {
                add(TaskItem("Plan today's priorities"))
                add(TaskItem("Complete a workout"))
                add(TaskItem("Review today's progress"))
            } else {
                repeat(count) { index ->
                    add(
                        TaskItem(
                            title = taskPrefs.getString(
                                "title_$index", ""
                            ) ?: "",
                            done = taskPrefs.getBoolean(
                                "done_$index", false
                            )
                        )
                    )
                }
            }
        }
    }

    val workoutDays = remember {
        mutableStateListOf<WorkoutDay>().apply {
            val count = workoutPrefs.getInt("count", 0)

            repeat(count) { index ->
                val day = index + 1

                add(
                    WorkoutDay(
                        day = day,
                        rawText = workoutPrefs.getString(
                            "day_$day", ""
                        ) ?: ""
                    )
                )
            }
        }
    }

    var selectedWorkoutDay by remember {
        mutableIntStateOf(1)
    }

    var selectedHours by remember {
        mutableIntStateOf(fastingPrefs.getInt("hours", 16))
    }

    var fastingStart by remember {
        mutableLongStateOf(fastingPrefs.getLong("start", 0L))
    }

    var fastingDuration by remember {
        mutableLongStateOf(fastingPrefs.getLong("duration", 16L * 3600000L))
    }

    var fastingStatus by remember {
        mutableStateOf(
            fastingPrefs.getString(
                "status",
                if (fastingPrefs.getLong("start", 0L) > 0L) "running" else "idle"
            ) ?: "idle"
        )
    }

    var fastingAccumulated by remember {
        mutableLongStateOf(fastingPrefs.getLong("elapsed", 0L))
    }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val fastingElapsed = (
        fastingAccumulated +
            if (fastingStatus == "running" && fastingStart > 0L) {
                (now - fastingStart).coerceAtLeast(0L)
            } else 0L
        ).coerceAtLeast(0L)

    LaunchedEffect(fastingStatus, fastingStart) {
        if (fastingStatus == "running" && fastingStart > 0L) {
            while (true) {
                now = System.currentTimeMillis()
                delay(1000L)
            }
        }
    }

    // Workout importer remains in WorkoutImportScreen.kt.
    if (showWorkoutImport) {
        WorkoutImportScreen(
            onBack = {
                showWorkoutImport = false
            },
            onImport = { imported: List<Pair<Int, String>> ->
                workoutDays.clear()

                imported.sortedBy { it.first }.forEach { entry ->
                    val day = entry.first
                    val text = entry.second

                    workoutDays.add(WorkoutDay(day, text))

                    workoutPrefs.edit()
                        .putString("day_$day", text)
                        .apply()
                }

                workoutPrefs.edit()
                    .putInt("count", imported.size)
                    .apply()

                selectedWorkoutDay = imported.firstOrNull()?.first ?: 1
                showWorkoutImport = false
                tab = 2
            }
        )

        return
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = accent,
            onPrimary = Color(0xFF101010),
            background = background,
            surface = surface,
            onSurface = Color.White
        )
    ) {
        CompositionLocalProvider(
            LocalLifeOsThemeId provides currentTheme.id
        ) {
            Scaffold(
            containerColor = background,
            bottomBar = {
                NavigationBar(
                    containerColor = surface,
                    tonalElevation = 8.dp
                ) {
                    val labels = listOf(
                        "Today", "Fasting", "Fitness", "Goals", "Shop"
                    )

                    val icons = listOf(
                        Icons.Filled.Home,
                        Icons.Filled.Timer,
                        Icons.Filled.FitnessCenter,
                        Icons.Filled.Flag,
                        Icons.Filled.Star
                    )

                    labels.forEachIndexed { index, label ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = { tab = index },
                            icon = {
                                Icon(
                                    imageVector = icons[index],
                                    contentDescription = label,
                                    modifier = Modifier.size(25.dp)
                                )
                            },
                            label = {
                                Text(label, fontSize = 11.sp)
                            },
                            alwaysShowLabel = true,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = accent,
                                selectedTextColor = accent,
                                indicatorColor = accent.copy(alpha = 0.18f),
                                unselectedIconColor = Muted,
                                unselectedTextColor = Muted
                            )
                        )
                    }
                }
            }
        ) { padding ->

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(background)
                    .padding(padding),
                contentPadding = PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            "☠  GRAND LINE",
                            color = accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 2.sp
                        )

                        Text(
                            listOf(
                                "Your adventure starts today.",
                                "Hold your course.",
                                "Train like a Straw Hat.",
                                "Choose your next milestone.",
                                "Spend your Berries wisely."
                            )[tab],
                            color = Color.White,
                            fontSize = 25.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            "THOUSAND SUNNY EDITION",
                            color = Muted,
                            fontSize = 10.sp,
                            letterSpacing = 1.5.sp
                        )
                    }
                }

                when (tab) {

                    // TODAY
                    0 -> {
                        item {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                MetricCard(
                                    label = "BERRIES",
                                    value = "฿ $berries",
                                    modifier = Modifier.weight(1f),
                                    accent = accent,
                                    surface = surface
                                )

                                MetricCard(
                                    label = "TASKS DONE",
                                    value = "${tasks.count { it.done }}/${tasks.size}",
                                    modifier = Modifier.weight(1f),
                                    accent = accent,
                                    surface = surface
                                )
                            }
                        }

                        item {
                            SectionTitle(
                                "Today's priorities",
                                accent
                            ) {
                                showAddTask = true
                            }
                        }

                        item {
                            CardBlock(surface) {
                                Text(
                                    "GOOGLE CALENDAR",
                                    color = accent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "Read upcoming events and add a Grand Line mission directly to a writable device calendar.",
                                    color = Muted,
                                    fontSize = 13.sp
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(calendarStatus, color = Muted, fontSize = 12.sp)
                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            if (hasCalendarPermissions()) refreshGrandLineCalendar()
                                            else requestCalendarAccess()
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            if (hasCalendarPermissions()) "Refresh" else "Connect",
                                            color = accent
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            if (!hasCalendarPermissions()) {
                                                requestCalendarAccess()
                                            } else {
                                                val description = tasks
                                                    .filterNot { it.done }
                                                    .joinToString(separator = "\n") { "• ${it.title}" }
                                                    .ifBlank { "Daily review and progress check." }
                                                createGrandLineCalendarEvent(
                                                    title = "Grand Line • Daily Mission",
                                                    description = description
                                                )
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = accent)
                                    ) {
                                        Text("Add mission", color = Color(0xFF101010))
                                    }
                                }
                                if (calendarEvents.isNotEmpty()) {
                                    Spacer(Modifier.height(10.dp))
                                    Text("UPCOMING • 7 DAYS", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    calendarEvents.take(5).forEach { event ->
                                        Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                                            Text(event.title, color = Color.White, fontWeight = FontWeight.SemiBold)
                                            Text(
                                                java.text.SimpleDateFormat("EEE, d MMM • h:mm a", java.util.Locale.getDefault())
                                                    .format(java.util.Date(event.startMillis)),
                                                color = Muted,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (tasks.isEmpty()) {
                            item {
                                CardBlock(surface) {
                                    Text(
                                        "Nothing planned yet. Add your first task.",
                                        color = Muted
                                    )
                                }
                            }
                        }

                        items(
                            items = tasks,
                            key = { it.title }
                        ) { task ->
                            TaskRow(
                                task = task,
                                accent = accent,
                                surface = surface,
                                onToggle = {
                                    val index = tasks.indexOf(task)

                                    if (index >= 0) {
                                        tasks[index] = task.copy(
                                            done = !task.done
                                        )
                                        saveTasks(taskPrefs, tasks)
                                    }
                                },
                                onDelete = {
                                    tasks.remove(task)
                                    saveTasks(taskPrefs, tasks)
                                }
                            )
                        }

                        item {
                            CardBlock(surface) {
                                Text(
                                    "YOUR STRAW HAT MENTOR",
                                    color = accent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(Modifier.height(8.dp))

                                Text(
                                    if (
                                        tasks.isNotEmpty() &&
                                        tasks.all { it.done }
                                    ) {
                                        "Luffy: Adventure complete for today! Rest up and set sail again tomorrow."
                                    } else {
                                        "Zoro: One step at a time. Finish what you started."
                                    },
                                    color = Color.White,
                                    fontSize = 17.sp
                                )
                            }
                        }
                    }

                    // FASTING
                    1 -> {
                        item {
                            CardBlock(surface) {
                                Text("FASTING LOG", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(10.dp))

                                Text(
                                    when (fastingStatus) {
                                        "running" -> if (fastingElapsed >= fastingDuration) "Target reached" else "Fast in progress"
                                        "paused" -> "Fast paused"
                                        "stopped" -> "Last fast stopped"
                                        else -> "Ready to start your planned fast?"
                                    },
                                    color = Muted
                                )

                                Text(
                                    if (fastingStatus == "idle" || fastingStatus == "stopped") {
                                        formatDuration(fastingDuration)
                                    } else {
                                        formatDuration((fastingDuration - fastingElapsed).coerceAtLeast(0L))
                                    },
                                    color = Color.White,
                                    fontSize = 38.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )

                                Text(
                                    when (fastingStatus) {
                                        "running" -> "Elapsed: ${formatDuration(fastingElapsed)}"
                                        "paused" -> "Elapsed: ${formatDuration(fastingElapsed)} • paused"
                                        "stopped" -> "Last session: ${formatDuration(fastingElapsed)}"
                                        else -> "Selected plan: $selectedHours hours"
                                    },
                                    color = Muted
                                )

                                Spacer(Modifier.height(14.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    when (fastingStatus) {
                                        "idle", "stopped" -> {
                                            Button(
                                                onClick = {
                                                    fastingAccumulated = 0L
                                                    fastingStart = System.currentTimeMillis()
                                                    fastingDuration = selectedHours * 3600000L
                                                    fastingStatus = "running"
                                                    fastingPrefs.edit()
                                                        .putLong("start", fastingStart)
                                                        .putLong("session_start", fastingStart)
                                                        .putLong("duration", fastingDuration)
                                                        .putLong("elapsed", 0L)
                                                        .putInt("hours", selectedHours)
                                                        .putString("status", "running")
                                                        .apply()
                                                    FastingWidgetProvider.refresh(context)
                                                },
                                                modifier = Modifier.weight(1f),
                                                colors = ButtonDefaults.buttonColors(containerColor = accent)
                                            ) { Text("Start", color = Color(0xFF101010)) }
                                        }

                                        "running" -> {
                                            OutlinedButton(
                                                onClick = {
                                                    fastingAccumulated = fastingElapsed
                                                    fastingStart = 0L
                                                    fastingStatus = "paused"
                                                    fastingPrefs.edit()
                                                        .putLong("start", 0L)
                                                        .putLong("elapsed", fastingAccumulated)
                                                        .putString("status", "paused")
                                                        .apply()
                                                    FastingWidgetProvider.refresh(context)
                                                },
                                                modifier = Modifier.weight(1f)
                                            ) { Text("Pause", color = accent) }

                                            Button(
                                                onClick = {
                                                    val sessionStart = fastingPrefs.getLong("session_start", 0L)
                                                    val completed = fastingElapsed >= fastingDuration
                                                    val rewarded = fastingPrefs.getBoolean("rewarded_$sessionStart", false)
                                                    if (completed && !rewarded) {
                                                        berries += 50
                                                        prefs.edit().putInt("berries", berries).apply()
                                                        fastingPrefs.edit().putBoolean("rewarded_$sessionStart", true).apply()
                                                    }
                                                    fastingAccumulated = fastingElapsed
                                                    fastingStart = 0L
                                                    fastingStatus = "stopped"
                                                    fastingPrefs.edit()
                                                        .putLong("start", 0L)
                                                        .putLong("elapsed", fastingAccumulated)
                                                        .putString("status", "stopped")
                                                        .apply()
                                                    FastingWidgetProvider.refresh(context)
                                                },
                                                modifier = Modifier.weight(1f),
                                                colors = ButtonDefaults.buttonColors(containerColor = accent)
                                            ) { Text("Stop", color = Color(0xFF101010)) }
                                        }

                                        "paused" -> {
                                            Button(
                                                onClick = {
                                                    fastingStart = System.currentTimeMillis()
                                                    fastingStatus = "running"
                                                    fastingPrefs.edit()
                                                        .putLong("start", fastingStart)
                                                        .putString("status", "running")
                                                        .apply()
                                                    FastingWidgetProvider.refresh(context)
                                                },
                                                modifier = Modifier.weight(1f),
                                                colors = ButtonDefaults.buttonColors(containerColor = accent)
                                            ) { Text("Resume", color = Color(0xFF101010)) }

                                            OutlinedButton(
                                                onClick = {
                                                    val sessionStart = fastingPrefs.getLong("session_start", 0L)
                                                    val completed = fastingElapsed >= fastingDuration
                                                    val rewarded = fastingPrefs.getBoolean("rewarded_$sessionStart", false)
                                                    if (completed && !rewarded) {
                                                        berries += 50
                                                        prefs.edit().putInt("berries", berries).apply()
                                                        fastingPrefs.edit().putBoolean("rewarded_$sessionStart", true).apply()
                                                    }
                                                    fastingStart = 0L
                                                    fastingStatus = "stopped"
                                                    fastingPrefs.edit()
                                                        .putLong("start", 0L)
                                                        .putLong("elapsed", fastingElapsed)
                                                        .putString("status", "stopped")
                                                        .apply()
                                                    FastingWidgetProvider.refresh(context)
                                                },
                                                modifier = Modifier.weight(1f)
                                            ) { Text("Stop", color = accent) }
                                        }
                                    }
                                }

                                if (fastingStatus == "running" && fastingElapsed >= fastingDuration) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "Target completed! Stop your fast to record the session and claim 50 Berries.",
                                        color = accent,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        item {
                            SectionTitle(
                                "Choose a fasting plan",
                                accent
                            ) { }
                        }

                        item {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(14, 16, 18, 20, 21).forEach { hours ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                if (selectedHours == hours) {
                                                    accent.copy(alpha = 0.14f)
                                                } else {
                                                    surface
                                                },
                                                RoundedCornerShape(16.dp)
                                            )
                                            .clickable {
                                                if (fastingStatus == "idle" || fastingStatus == "stopped") {
                                                    selectedHours = hours
                                                    fastingDuration =
                                                        hours * 3600000L

                                                    fastingPrefs.edit()
                                                        .putInt("hours", hours)
                                                        .putLong(
                                                            "duration",
                                                            fastingDuration
                                                        )
                                                        .apply()
                                                }
                                            }
                                            .padding(15.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                "$hours:${24 - hours} schedule",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold
                                            )

                                            Text(
                                                "$hours hours fasting · ${24 - hours} hours eating window",
                                                color = Muted,
                                                fontSize = 12.sp
                                            )
                                        }

                                        RadioButton(
                                            selected = selectedHours == hours,
                                            onClick = {
                                                if (fastingStatus == "idle" || fastingStatus == "stopped") {
                                                    selectedHours = hours
                                                    fastingDuration =
                                                        hours * 3600000L

                                                    fastingPrefs.edit()
                                                        .putInt("hours", hours)
                                                        .putLong(
                                                            "duration",
                                                            fastingDuration
                                                        )
                                                        .apply()
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            Text(
                                "Choose a schedule appropriate for your health. Rewards never require extending your planned fast.",
                                color = Muted,
                                fontSize = 12.sp
                            )
                        }
                    }

                    // FITNESS
                    2 -> {
                        item {
                            Button(
                                onClick = {
                                    showWorkoutImport = true
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = accent
                                )
                            ) {
                                Text(
                                    "＋ Import workout sheet",
                                    color = Color(0xFF101010),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (workoutDays.isEmpty()) {
                            item {
                                CardBlock(surface) {
                                    Text(
                                        "NO LOGBOOK YET",
                                        color = accent,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Spacer(Modifier.height(6.dp))

                                    Text(
                                        "Import a workout calendar image to begin your challenge.",
                                        color = Muted
                                    )
                                }
                            }
                        } else {
                            item {
                                SectionTitle("Workout calendar", accent) { }
                            }

                            item {
                                val rowCount = (workoutDays.size + 4) / 5

                                Column(
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    repeat(rowCount) { row ->
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            repeat(5) { col ->
                                                val dayNumber = row * 5 + col + 1
                                                val day = workoutDays.firstOrNull {
                                                    it.day == dayNumber
                                                }

                                                if (day != null) {
                                                    val completed = workoutPrefs.getBoolean(
                                                        "completed_$dayNumber",
                                                        false
                                                    )

                                                    val missed =
                                                        !completed && workoutPrefs.getBoolean(
                                                            "missed_$dayNumber",
                                                            false
                                                        )

                                                    Box(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .aspectRatio(0.9f)
                                                            .background(
                                                                when {
                                                                    completed ->
                                                                        accent.copy(alpha = 0.2f)

                                                                    missed ->
                                                                        Color(0xFF492329)

                                                                    else -> surface
                                                                },
                                                                RoundedCornerShape(10.dp)
                                                            )
                                                            .clickable {
                                                                selectedWorkoutDay = dayNumber
                                                            }
                                                            .padding(5.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Column(
                                                            horizontalAlignment =
                                                                Alignment.CenterHorizontally
                                                        ) {
                                                            Text(
                                                                dayNumber.toString(),
                                                                color = Color.White,
                                                                fontWeight = FontWeight.Bold
                                                            )

                                                            Text(
                                                                text = when {
                                                                    completed -> "/"
                                                                    missed -> "×"
                                                                    dayNumber == selectedWorkoutDay -> "●"
                                                                    else -> ""
                                                                },
                                                                color = when {
                                                                    completed -> Color(0xFF86E3A3)
                                                                    missed -> Color(0xFFFF7777)
                                                                    else -> accent
                                                                },
                                                                fontSize = 19.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }
                                                } else {
                                                    Spacer(
                                                        Modifier
                                                            .weight(1f)
                                                            .aspectRatio(0.9f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            val selectedDay = workoutDays.firstOrNull {
                                it.day == selectedWorkoutDay
                            }

                            if (selectedDay != null) {
                                item {
                                    WorkoutDayCard(
                                        day = selectedDay,
                                        prefs = workoutPrefs,
                                        accent = accent,
                                        surface = surface,
                                        onReward = {
                                            berries += 100
                                            prefs.edit()
                                                .putInt("berries", berries)
                                                .apply()
                                        },
                                        onCompletionChanged = { completed ->
                                            workoutPrefs.edit()
                                                .putBoolean(
                                                    "completed_${selectedDay.day}",
                                                    completed
                                                )
                                                .putBoolean(
                                                    "missed_${selectedDay.day}",
                                                    false
                                                )
                                                .apply()
                                        },
                                        onMarkMissed = {
                                            workoutPrefs.edit()
                                                .putBoolean("completed_${selectedDay.day}", false)
                                                .putBoolean("missed_${selectedDay.day}", true)
                                                .apply()
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // GOALS
                    3 -> {
                        item {
                            CardBlock(surface) {
                                Text(
                                    "YOUR NEXT MILESTONE",
                                    color = accent,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(Modifier.height(8.dp))

                                Text(
                                    "Small steps. Real progress.",
                                    color = Color.White,
                                    fontSize = 21.sp
                                )

                                Text(
                                    "Luffy: You don't need to be perfect. Keep moving toward your dream.",
                                    color = Muted
                                )
                            }
                        }

                        item {
                            CardBlock(surface) {
                                Text(
                                    "MENTOR CREW",
                                    color = accent,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(Modifier.height(10.dp))

                                MentorRow("Luffy", "Dream big. Take the next step.", accent)
                                MentorRow("Zoro", "Discipline means showing up.", accent)
                                MentorRow("Nami", "Make a plan and protect your resources.", accent)
                                MentorRow("Sanji", "Recovery and good food matter too.", accent)
                                MentorRow("Chopper", "Every small improvement counts.", accent)
                                MentorRow("Robin", "Learn from every chapter.", accent)
                            }
                        }
                    }

                    // THEME SHOP
                    4 -> {
                        item {
                            CardBlock(surface) {
                                Text(
                                    "BERRIES SHOP",
                                    color = accent,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(Modifier.height(8.dp))

                                Text(
                                    "฿ $berries Berries",
                                    color = Color.White,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )

                                Text(
                                    "Complete workouts and reach planned fasting targets to earn rewards.",
                                    color = Muted
                                )
                            }
                        }

                        item {
                            SectionTitle("Choose your ship's look", accent) { }
                        }

                        items(
                            items = themes,
                            key = { it.id }
                        ) { theme ->
                            val unlocked = theme.id in unlockedThemes

                            CardBlock(surface) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(58.dp)
                                            .background(
                                                Color(theme.accent).copy(alpha = 0.16f),
                                                RoundedCornerShape(16.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        ThemeCharacterIcon(
                                            themeId = theme.id,
                                            modifier = Modifier.size(38.dp)
                                        )
                                    }

                                    Spacer(Modifier.width(12.dp))

                                    Column(
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            theme.name,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )

                                        Text(
                                            theme.subtitle,
                                            color = Muted,
                                            fontSize = 12.sp
                                        )

                                        Text(
                                            if (unlocked) {
                                                "Unlocked"
                                            } else {
                                                "฿ ${theme.price}"
                                            },
                                            color = accent,
                                            fontSize = 12.sp
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            if (unlocked) {
                                                selectedThemeId = theme.id

                                                prefs.edit()
                                                    .putString("theme", theme.id)
                                                    .apply()
                                            } else if (berries >= theme.price) {
                                                berries -= theme.price
                                                unlockedThemes.add(theme.id)

                                                prefs.edit()
                                                    .putInt("berries", berries)
                                                    .putStringSet(
                                                        "unlocked_themes",
                                                        unlockedThemes.toSet()
                                                    )
                                                    .putString("theme", theme.id)
                                                    .apply()

                                                selectedThemeId = theme.id
                                            }
                                        },
                                        enabled = unlocked || berries >= theme.price,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = accent
                                        )
                                    ) {
                                        Text(
                                            when {
                                                selectedThemeId == theme.id -> "Equipped"
                                                unlocked -> "Equip"
                                                else -> "Unlock"
                                            },
                                            color = Color(0xFF101010)
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            Text(
                                "These are original color palettes inspired by the One Piece world. Character artwork and online rankings are not yet implemented.",
                                color = Muted,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
    } // closes CompositionLocalProvider

    if (showAddTask) {
        AlertDialog(
            onDismissRequest = {
                showAddTask = false
            },
            title = {
                Text("New task")
            },
            text = {
                OutlinedTextField(
                    value = newTask,
                    onValueChange = { newTask = it },
                    label = { Text("What do you need to do?") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val title = newTask.trim()

                        if (title.isNotEmpty()) {
                            tasks.add(TaskItem(title))
                            saveTasks(taskPrefs, tasks)

                            newTask = ""
                            showAddTask = false
                        }
                    }
                ) {
                    Text("Add task", color = accent)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddTask = false
                    }
                ) {
                    Text("Cancel")
                }
            },
            containerColor = surface
        )
    }
}

// ------------------------------------------------------------
// WORKOUT DAY CARD
// ------------------------------------------------------------

@Composable
private fun WorkoutDayCard(
    day: WorkoutDay,
    prefs: android.content.SharedPreferences,
    accent: Color,
    surface: Color,
    onReward: () -> Unit,
    onCompletionChanged: (Boolean) -> Unit,
    onMarkMissed: () -> Unit
) {
    val lines = remember(day.day, day.rawText) {
        day.rawText.lines()
            .map { it.trim() }
            .filter {
                it.isNotBlank() &&
                        !it.matches(Regex("^\\d{1,3}$"))
            }
    }

    val checkedStates: SnapshotStateMap<Int, Boolean> = remember(
        day.day,
        day.rawText
    ) {
        mutableStateMapOf<Int, Boolean>().apply {
            lines.indices.forEach { index ->
                put(
                    index,
                    prefs.getBoolean("check_${day.day}_$index", false)
                )
            }
        }
    }

    var completed by remember(day.day) {
        mutableStateOf(
            prefs.getBoolean("completed_${day.day}", false)
        )
    }

    val doneCount = lines.indices.count {
        checkedStates[it] == true
    }

    CardBlock(surface) {
        Text(
            "DAY ${day.day}",
            color = accent,
            fontWeight = FontWeight.ExtraBold
        )

        Spacer(Modifier.height(8.dp))

        if (lines.isEmpty()) {
            Text(
                "No exercise text recognized. Re-import or correct the sheet.",
                color = Muted
            )
        }

        lines.forEachIndexed { index, line ->
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = checkedStates[index] == true,
                    onCheckedChange = { checked ->
                        checkedStates[index] = checked

                        prefs.edit()
                            .putBoolean(
                                "check_${day.day}_$index",
                                checked
                            )
                            .apply()
                    },
                    colors = CheckboxDefaults.colors(
                        checkedColor = accent
                    )
                )

                Text(
                    text = line,
                    color = if (checkedStates[index] == true) {
                        Muted
                    } else {
                        Color.White
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            "$doneCount / ${lines.size} items checked",
            color = Muted,
            fontSize = 12.sp
        )

        Button(
            onClick = {
                val allDone = lines.isNotEmpty() &&
                        doneCount == lines.size

                if (allDone && !completed) {
                    onReward()
                }

                completed = allDone

                prefs.edit()
                    .putBoolean("completed_${day.day}", allDone)
                    .putBoolean(
                        "missed_${day.day}",
                        false
                    )
                    .apply()

                onCompletionChanged(allDone)
            },
            enabled = lines.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = accent
            )
        ) {
            Text(
                if (completed) "Update completion" else "Complete workout",
                color = Color(0xFF101010)
            )
        }

        Spacer(Modifier.height(6.dp))

        OutlinedButton(
            onClick = {
                onMarkMissed()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "Mark day missed",
                color = Color(0xFFFF7777)
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(
            if (completed) {
                "Luffy: Nice work. Rest well and come back ready for the next adventure!"
            } else {
                "Zoro: One missed day doesn't define you. Get back on track when you're ready."
            },
            color = Muted,
            fontSize = 13.sp
        )
    }
}

// ------------------------------------------------------------
// TASK STORAGE
// ------------------------------------------------------------

private fun saveTasks(
    prefs: android.content.SharedPreferences,
    tasks: List<TaskItem>
) {
    prefs.edit().apply {
        val oldCount = prefs.getInt("count", 0)

        repeat(maxOf(oldCount, tasks.size)) { index ->
            remove("title_$index")
            remove("done_$index")
        }

        putInt("count", tasks.size)

        tasks.forEachIndexed { index, task ->
            putString("title_$index", task.title)
            putBoolean("done_$index", task.done)
        }

        apply()
    }
}

// ------------------------------------------------------------
// REUSABLE UI COMPONENTS
// ------------------------------------------------------------

@Composable
private fun MetricCard(
    label: String,
    value: String,
    modifier: Modifier,
    accent: Color,
    surface: Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = surface
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                label,
                color = Muted,
                fontSize = 10.sp,
                letterSpacing = 1.sp
            )

            Text(
                value,
                color = accent,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
private fun SectionTitle(
    title: String,
    accent: Color,
    onAdd: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        if (onAdd != null) {
            TextButton(onClick = onAdd) {
                Text("+ Add", color = accent)
            }
        }
    }
}

@Composable
private fun CardBlock(
    surface: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    val themeId = LocalLifeOsThemeId.current

    val shape = when (themeId) {
        "swordsman" -> RoundedCornerShape(10.dp)
        "navigator" -> RoundedCornerShape(14.dp)
        "wanted" -> RoundedCornerShape(4.dp)
        "strawhat" -> RoundedCornerShape(24.dp)
        else -> RoundedCornerShape(18.dp)
    }

    val borderColor = when (themeId) {
        "swordsman" -> Color(0xFF3C7556)
        "navigator" -> Color(0xFF344A72)
        "wanted" -> Color(0xFF8A693A)
        "strawhat" -> Color(0xFF77343A)
        else -> Color(0xFF28516A)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            borderColor.copy(alpha = 0.75f)
        )
    ) {
        Column(
            modifier = Modifier.padding(
                if (themeId == "wanted") 12.dp else 16.dp
            ),
            content = content
        )
    }
}

@Composable
private fun TaskRow(
    task: TaskItem,
    accent: Color,
    surface: Color,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(surface, RoundedCornerShape(14.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = task.done,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = accent
            )
        )

        Text(
            text = task.title,
            modifier = Modifier.weight(1f),
            color = if (task.done) Muted else Color.White
        )

        TextButton(onClick = onDelete) {
            Text(
                "×",
                color = Muted,
                fontSize = 20.sp
            )
        }
    }
}

@Composable
private fun MentorRow(
    name: String,
    message: String,
    accent: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
    ) {
        Text(
            name,
            color = accent,
            fontWeight = FontWeight.Bold
        )

        Text(
            message,
            color = Color.White,
            fontSize = 14.sp
        )
    }
}

private fun formatDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1000L
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return "%02d:%02d:%02d".format(
        hours,
        minutes,
        seconds
    )
}
@Composable
private fun ThemeCharacterIcon(
    themeId: String,
    modifier: Modifier = Modifier
) {
    val iconRes = when (themeId.lowercase()) {
        "sunny", "strawhat", "gear5" ->
            R.drawable.luffy_flag

        "zoro", "swordsman" ->
            R.drawable.zoro_icon

        "nami" ->
            R.drawable.nami_icon

        "navigator", "law" ->
            R.drawable.law_icon

        "ace" ->
            R.drawable.ace_icon

        "chopper" ->
            R.drawable.chopper_icon

        "brook" ->
            R.drawable.brook_icon

        "shanks" ->
            R.drawable.shanks_icon

        "whitebeard" ->
            R.drawable.whitebeard_icon

        "usopp" ->
            R.drawable.usopp_icon

        else ->
            R.drawable.luffy_flag
    }

    Image(
        painter = painterResource(id = iconRes),
        contentDescription = "$themeId character icon",
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}