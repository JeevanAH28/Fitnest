package com.jeeva.fitnest

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import io.github.sceneview.SceneView
import io.github.sceneview.SurfaceType
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val WATER_GOAL_ML = 2500
private const val STEP_GOAL = 10000

private val Pine = Color(0xFF08130F)
private val Forest = Color(0xFF10281D)
private val Leaf = Color(0xFF1ED486)
private val Sand = Color(0xFFFFE8BD)
private val Mist = Color(0xFFE1F6EA)
private val Slate = Color(0xFF9AB0A2)
private val Danger = Color(0xFFFFB4AB)

class MainActivity : ComponentActivity() {
    private lateinit var store: FitnessStore
    private var fitnessState by mutableStateOf(FitnessState())

    private val requestedHealthPermissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class)
    )

    private val healthPermissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { grantedPermissions ->
        if (grantedPermissions.containsAll(requestedHealthPermissions)) {
            readTodaySteps()
        } else {
            showMessage("Step access was not granted. You can try again from Health Connect.")
        }
    }

    private val progressPhotoPicker = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val savedPath = uri?.let { copyToPrivatePhotoStorage(it, "progress") }

        if (savedPath == null) return@registerForActivityResult

        updateState {
            it.copy(
                progressPhotos = (listOf(ProgressPhoto(System.currentTimeMillis(), savedPath)) + it.progressPhotos)
                    .take(12)
            )
        }
        showMessage("Progress photo saved privately on this device.")
    }

    private val foodPhotoPicker = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val savedPath = uri?.let { copyToPrivatePhotoStorage(it, "food") }

        if (savedPath == null) return@registerForActivityResult

        updateState { it.copy(foodPhotoPath = savedPath) }
        showMessage("Food photo added. Enter the food and cooked portion for a useful estimate.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        store = FitnessStore(this)
        fitnessState = store.read()

        setContent {
            FitNestTheme {
                FitnessApp(
                    state = fitnessState,
                    onStateChange = ::updateState,
                    onProgressPhoto = {
                        progressPhotoPicker.launch(
                            ActivityResultContracts.PickVisualMedia.Request(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    },
                    onFoodPhoto = {
                        foodPhotoPicker.launch(
                            ActivityResultContracts.PickVisualMedia.Request(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    },
                    onConnectSteps = ::connectHealthConnect
                )
            }
        }
    }

    private fun updateState(change: (FitnessState) -> FitnessState) {
        fitnessState = change(fitnessState)
        store.save(fitnessState)
    }

    private fun connectHealthConnect() {
        if (HealthConnectClient.getSdkStatus(this) != HealthConnectClient.SDK_AVAILABLE) {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=com.google.android.apps.healthdata")
                )
            )
            showMessage("Install or update Health Connect, then return here to sync steps.")
            return
        }

        val healthConnectClient = HealthConnectClient.getOrCreate(this)

        lifecycleScope.launch {
            val grantedPermissions = withContext(Dispatchers.IO) {
                healthConnectClient.permissionController.getGrantedPermissions()
            }

            if (grantedPermissions.containsAll(requestedHealthPermissions)) {
                readTodaySteps()
            } else {
                healthPermissionLauncher.launch(requestedHealthPermissions)
            }
        }
    }

    private fun readTodaySteps() {
        val healthConnectClient = HealthConnectClient.getOrCreate(this)

        lifecycleScope.launch {
            val steps = runCatching {
                withContext(Dispatchers.IO) {
                    val startOfToday = LocalDate.now()
                        .atStartOfDay(ZoneId.systemDefault())
                        .toInstant()
                    val result = healthConnectClient.aggregate(
                        AggregateRequest(
                            metrics = setOf(StepsRecord.COUNT_TOTAL),
                            timeRangeFilter = TimeRangeFilter.between(startOfToday, Instant.now())
                        )
                    )
                    (result[StepsRecord.COUNT_TOTAL] ?: 0L).toInt()
                }
            }.getOrElse {
                showMessage("Could not read steps. Check Health Connect permissions and try again.")
                return@launch
            }

            updateState { it.copy(steps = steps) }
            showMessage("Today’s steps synced from Health Connect.")
        }
    }

    private fun copyToPrivatePhotoStorage(uri: Uri, folderName: String): String? = runCatching {
        val photoFolder = File(filesDir, folderName).apply { mkdirs() }
        val photoFile = File(photoFolder, "${System.currentTimeMillis()}.jpg")

        contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(photoFile).use(input::copyTo)
        } ?: return null

        photoFile.absolutePath
    }.getOrNull()

    private fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}

class HealthPermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FitNestTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("How FitNest uses step data", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "FitNest requests read-only access to your step total so it can show today’s activity on your dashboard. Step data stays on your phone in FitNest and is never sold or used for advertising.",
                            color = Slate
                        )
                        Text(
                            "You can remove this permission anytime in Android Settings > Health Connect > App permissions.",
                            color = Slate
                        )
                    }
                }
            }
        }
    }
}

private data class AppActions(
    val onStateChange: ((FitnessState) -> FitnessState) -> Unit,
    val onProgressPhoto: () -> Unit,
    val onFoodPhoto: () -> Unit,
    val onConnectSteps: () -> Unit
)

private data class TabItem(val label: String, val icon: @Composable () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FitnessApp(
    state: FitnessState,
    onStateChange: ((FitnessState) -> FitnessState) -> Unit,
    onProgressPhoto: () -> Unit,
    onFoodPhoto: () -> Unit,
    onConnectSteps: () -> Unit
) {
    val actions = AppActions(onStateChange, onProgressPhoto, onFoodPhoto, onConnectSteps)
    val tabs = listOf(
        TabItem("Today") { Icon(Icons.Rounded.Home, contentDescription = null) },
        TabItem("Train") { Icon(Icons.Rounded.FitnessCenter, contentDescription = null) },
        TabItem("Food") { Icon(Icons.Rounded.Restaurant, contentDescription = null) },
        TabItem("You") { Icon(Icons.Rounded.Spa, contentDescription = null) }
    )
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showEquipmentDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("FITNEST", fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                        Text("Home fitness, built around you", fontSize = 12.sp, color = Slate)
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = item.icon,
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> TodayScreen(state, actions, { showEquipmentDialog = true })
                1 -> WorkoutScreen(state, actions)
                2 -> NutritionScreen(state, actions)
                else -> YouScreen(state, actions, { showEquipmentDialog = true })
            }
        }
    }

    if (!state.onboardingComplete || showEquipmentDialog) {
        EquipmentDialog(
            initialEquipment = state.selectedEquipment,
            isRequired = !state.onboardingComplete,
            onSave = { selected ->
                onStateChange {
                    it.copy(selectedEquipment = selected, onboardingComplete = true)
                }
                showEquipmentDialog = false
            },
            onDismiss = { showEquipmentDialog = false }
        )
    }
}

@Composable
private fun TodayScreen(
    state: FitnessState,
    actions: AppActions,
    onEditEquipment: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            HeroCard(
                title = "Train steady. Feel stronger.",
                description = "A sustainable calorie deficit, consistent movement, sleep, and protein are the foundation for fat loss and defined abs."
            )
        }
        item {
            Text("Today’s foundation", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(
                    modifier = Modifier.weight(1f),
                    icon = { Icon(Icons.Rounded.WaterDrop, null) },
                    label = "Water",
                    value = "${state.waterMl / 1000f} L",
                    goal = "/ 2.5 L",
                    accent = Leaf
                )
                MetricCard(
                    modifier = Modifier.weight(1f),
                    icon = { Icon(Icons.Rounded.Bedtime, null) },
                    label = "Sleep",
                    value = formatSleep(state.sleepMinutes),
                    goal = "goal 7–9 h",
                    accent = Sand
                )
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Forest), shape = RoundedCornerShape(24.dp)) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.WaterDrop, null, tint = Leaf)
                        Spacer(Modifier.width(8.dp))
                        Text("Water check-in", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text("${waterProgress(state.waterMl)}%", color = Leaf, fontWeight = FontWeight.Bold)
                    }
                    Text("Tap a glass whenever you drink. Small habits make the plan easier to follow.", color = Slate)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            actions.onStateChange { it.copy(waterMl = (it.waterMl + 250).coerceAtMost(WATER_GOAL_ML)) }
                        }) { Text("+ 250 ml") }
                        OutlinedButton(onClick = {
                            actions.onStateChange { it.copy(waterMl = 0) }
                        }) { Text("Reset") }
                    }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Forest), shape = RoundedCornerShape(24.dp)) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Bedtime, null, tint = Sand)
                        Spacer(Modifier.width(8.dp))
                        Text("Last night’s sleep", fontWeight = FontWeight.Bold)
                    }
                    Text("Recovery affects energy, appetite, training quality, and skin health.", color = Slate)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(6, 7, 8, 9).forEach { hours ->
                            FilterChip(
                                selected = state.sleepMinutes == hours * 60,
                                onClick = { actions.onStateChange { it.copy(sleepMinutes = hours * 60) } },
                                label = { Text("${hours}h") }
                            )
                        }
                    }
                }
            }
        }
        item {
            StepsCard(state.steps, actions.onConnectSteps)
        }
        item {
            ProgressPhotoCard(state.progressPhotos.firstOrNull(), actions.onProgressPhoto)
        }
        item {
            EquipmentSummary(state.selectedEquipment, onEditEquipment)
        }
    }
}

@Composable
private fun WorkoutScreen(state: FitnessState, actions: AppActions) {
    var openDemoId by rememberSaveable { mutableStateOf<String?>(null) }
    val recommended = weeklyPlan.filter { workout ->
        workout.equipment.all { requirement -> requirement == "Bodyweight" || requirement in state.selectedEquipment }
    }
    val displayPlan = if (recommended.isEmpty()) weeklyPlan else recommended

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Your home plan", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        item {
            Text(
                "Choose a session that matches your energy and equipment. Core training builds strength; overall fat loss comes from consistent nutrition and activity.",
                color = Slate
            )
        }
        items(displayPlan, key = Workout::id) { workout ->
            val demo = workout.demoId?.let(exerciseDemos::get)
            WorkoutCard(
                workout = workout,
                completed = workout.id in state.completedWorkouts,
                onViewDemo = demo?.let { { openDemoId = it.id } },
                onToggle = {
                    actions.onStateChange {
                        val completed = it.completedWorkouts.toMutableSet()
                        if (!completed.add(workout.id)) completed.remove(workout.id)
                        it.copy(completedWorkouts = completed)
                    }
                }
            )
        }
        item {
            WellnessNotice("Rest if you feel sharp pain, dizziness, chest pain, or unusual shortness of breath. Ask a qualified clinician or trainer if you are new to exercise or managing an injury.")
        }
    }

    val selectedDemo = openDemoId?.let { exerciseDemos[it] }
    selectedDemo?.let { demo ->
        ExerciseDemoDialog(demo = demo, onDismiss = { openDemoId = null })
    }
}

@Composable
private fun NutritionScreen(state: FitnessState, actions: AppActions) {
    var foodName by rememberSaveable { mutableStateOf("") }
    var portionGrams by rememberSaveable { mutableStateOf("100") }
    val dailyCalories = state.foodEntries.sumOf(FoodEntry::calories)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Eat for strength", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Forest), shape = RoundedCornerShape(24.dp)) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Food photo calorie log", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${dailyCalories} kcal logged today", color = Leaf, fontSize = 26.sp, fontWeight = FontWeight.Black)
                    Text("Photos support your log, but calories are estimates until you confirm ingredients, cooking oil, and portion weight.", color = Slate)
                    OutlinedButton(onClick = actions.onFoodPhoto) {
                        Icon(Icons.Rounded.AddAPhoto, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (state.foodPhotoPath == null) "Add food photo" else "Replace food photo")
                    }
                    if (state.foodPhotoPath != null) {
                        StoredPhoto(state.foodPhotoPath, Modifier.fillMaxWidth().height(160.dp))
                    }
                    OutlinedTextField(
                        value = foodName,
                        onValueChange = { foodName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Food and preparation") },
                        placeholder = { Text("e.g. chicken curry with rice") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = portionGrams,
                        onValueChange = { portionGrams = it.filter(Char::isDigit) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Cooked portion (g)") },
                        singleLine = true
                    )
                    Button(
                        enabled = foodName.isNotBlank(),
                        onClick = {
                            val calories = estimateCalories(foodName, portionGrams.toIntOrNull() ?: 100)
                            actions.onStateChange {
                                it.copy(
                                    foodEntries = (listOf(FoodEntry(foodName.trim(), calories, System.currentTimeMillis())) + it.foodEntries).take(12),
                                    foodPhotoPath = null
                                )
                            }
                            foodName = ""
                        }
                    ) { Text("Log estimate") }
                }
            }
        }
        if (state.foodEntries.isNotEmpty()) {
            item { Text("Today’s log", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            items(state.foodEntries, key = FoodEntry::loggedAt) { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth().background(Forest, RoundedCornerShape(16.dp)).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(entry.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("~${entry.calories} kcal", color = Leaf, fontWeight = FontWeight.Bold)
                }
            }
        }
        item { FoodLibrary() }
    }
}

@Composable
private fun YouScreen(state: FitnessState, actions: AppActions, onEditEquipment: () -> Unit) {
    var question by rememberSaveable { mutableStateOf("") }
    var answer by rememberSaveable { mutableStateOf("Ask about a workout, protein, steps, sleep, skin basics, or a calorie deficit.") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Your wellbeing", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Forest), shape = RoundedCornerShape(24.dp)) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Spa, null, tint = Sand)
                        Spacer(Modifier.width(8.dp))
                        Text("Skin glow routine", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Text("Morning: gentle cleanse, moisturize, and broad-spectrum SPF 30+.", color = Mist)
                    Text("Night: gentle cleanse and moisturize. Add active ingredients slowly and patch-test first.", color = Mist)
                    Text("Support your skin with sleep, protein-rich foods, fruit and vegetables, and enough water. Persistent acne, irritation, or pigment changes need a dermatologist.", color = Slate)
                }
            }
        }
        item {
            ProgressPhotoCard(state.progressPhotos.firstOrNull(), actions.onProgressPhoto)
        }
        if (state.progressPhotos.size > 1) {
            item { Text("${state.progressPhotos.size} private check-ins saved", color = Leaf, fontWeight = FontWeight.Bold) }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Forest), shape = RoundedCornerShape(24.dp)) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("FitNest coach", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(answer, color = Mist)
                    OutlinedTextField(
                        value = question,
                        onValueChange = { question = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Ask a fitness question") },
                        singleLine = false
                    )
                    Button(
                        enabled = question.isNotBlank(),
                        onClick = {
                            answer = coachAnswer(question)
                            question = ""
                        }
                    ) { Text("Ask coach") }
                }
            }
        }
        item { EquipmentSummary(state.selectedEquipment, onEditEquipment) }
        item {
            WellnessNotice("Supplements are optional, not a shortcut. Prioritize food, sleep, and training first. If you use supplements, check for allergies, medicines, and health conditions with a clinician or registered dietitian.")
        }
    }
}

@Composable
private fun HeroCard(title: String, description: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Leaf),
        shape = RoundedCornerShape(28.dp)
    ) {
        Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("HOME WORKOUT • WEEKLY PLAN", fontSize = 11.sp, letterSpacing = 1.2.sp, color = Pine, fontWeight = FontWeight.Bold)
            Text(title, fontSize = 28.sp, lineHeight = 32.sp, color = Pine, fontWeight = FontWeight.Black)
            Text(description, color = Pine.copy(alpha = 0.82f), lineHeight = 20.sp)
        }
    }
}

@Composable
private fun MetricCard(
    modifier: Modifier,
    icon: @Composable () -> Unit,
    label: String,
    value: String,
    goal: String,
    accent: Color
) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = Forest), shape = RoundedCornerShape(22.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) { icon() }
            Text(label, color = Slate, fontSize = 12.sp)
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Black, color = accent)
            Text(goal, color = Slate, fontSize = 11.sp)
        }
    }
}

@Composable
private fun StepsCard(steps: Int, onConnectSteps: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Forest), shape = RoundedCornerShape(24.dp)) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.DirectionsWalk, null, tint = Leaf)
                Spacer(Modifier.width(8.dp))
                Text("Steps", fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("$steps / $STEP_GOAL", color = Leaf, fontWeight = FontWeight.Black)
            }
            Text("Read today’s aggregate step total from Health Connect. FitNest only asks for read access.", color = Slate)
            OutlinedButton(onClick = onConnectSteps) { Text("Connect & sync steps") }
        }
    }
}

@Composable
private fun ProgressPhotoCard(photo: ProgressPhoto?, onAddPhoto: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Forest), shape = RoundedCornerShape(24.dp)) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AddAPhoto, null, tint = Sand)
                Spacer(Modifier.width(8.dp))
                Text("Weekly progress photo", fontWeight = FontWeight.Bold)
            }
            Text("Use the same place, lighting, and front/side/back poses each week. Your photos remain in this app’s private storage.", color = Slate)
            if (photo != null) {
                StoredPhoto(photo.path, Modifier.fillMaxWidth().height(180.dp))
                Text("Latest check-in: ${formatDate(photo.takenAt)}", color = Mist)
            }
            OutlinedButton(onClick = onAddPhoto) { Text(if (photo == null) "Add first photo" else "Add this week’s photo") }
        }
    }
}

@Composable
private fun EquipmentSummary(equipment: Set<String>, onEdit: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Forest), shape = RoundedCornerShape(24.dp)) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Your equipment", fontWeight = FontWeight.Bold)
            Text(
                if (equipment.isEmpty()) "Bodyweight plan selected. Add any equipment you own to unlock better matching sessions."
                else equipment.joinToString(),
                color = Slate,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            TextButton(onClick = onEdit) { Text("Edit equipment") }
        }
    }
}

@Composable
private fun WorkoutCard(
    workout: Workout,
    completed: Boolean,
    onViewDemo: (() -> Unit)?,
    onToggle: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = if (completed) Color(0xFF153B2A) else Forest),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(workout.day, color = Leaf, fontSize = 12.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.width(10.dp))
                Text(workout.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("${workout.minutes} min", color = Sand)
            }
            Text(workout.focus, color = Slate)
            Text(workout.exercises.joinToString("  •  "), color = Mist, lineHeight = 20.sp)
            Text("Need: ${workout.equipment.joinToString()}", color = Slate, fontSize = 12.sp)
            if (onViewDemo != null) {
                OutlinedButton(onClick = onViewDemo) { Text("View 3D form demo") }
            }
            Button(onClick = onToggle) { Text(if (completed) "Completed — tap to reopen" else "Mark workout complete") }
        }
    }
}

@Composable
private fun ExerciseDemoDialog(demo: ExerciseDemo, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(demo.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ExerciseModelViewer(demo)
                Text("Drag to rotate • pinch to zoom", color = Slate, fontSize = 12.sp)
                demo.cues.forEachIndexed { index, cue ->
                    Text("${index + 1}. $cue", color = Mist, lineHeight = 19.sp)
                }
                Text("The animation is a visual aid. Move only through a comfortable range and stop for sharp pain or dizziness.", color = Sand, fontSize = 12.sp)
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
private fun ExerciseModelViewer(demo: ExerciseDemo) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val modelInstance = rememberModelInstance(modelLoader, demo.assetPath)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(245.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF132D20)),
        contentAlignment = Alignment.Center
    ) {
        SceneView(
            modifier = Modifier.fillMaxSize(),
            surfaceType = SurfaceType.TextureSurface,
            engine = engine,
            modelLoader = modelLoader,
            cameraManipulator = rememberCameraManipulator()
        ) {
            modelInstance?.let { instance ->
                ModelNode(
                    modelInstance = instance,
                    scaleToUnits = 1.5f,
                    autoAnimate = true
                )
            }
        }
        if (modelInstance == null) {
            Text("Loading 3D demo…", color = Mist)
        }
    }
}

@Composable
private fun FoodLibrary() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Food library", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        FoodSection(
            "High-protein picks",
            listOf("Eggs or egg whites", "Greek yogurt or curd", "Chicken, fish, or lean meat", "Paneer, tofu, tempeh", "Dal, chickpeas, beans", "Milk or unsweetened soy milk")
        )
        FoodSection(
            "Gut-friendly choices",
            listOf("Curd or yogurt with live cultures", "Kefir or fermented foods if tolerated", "Oats, fruit, vegetables, legumes", "Adequate water and gradual fibre increases")
        )
        FoodSection(
            "Immunity-supporting drinks",
            listOf("Water, unsweetened tea, or lemon water", "Milk or fortified soy milk", "Ginger or turmeric tea for taste and comfort", "Avoid relying on drinks as a cure or meal replacement")
        )
        FoodSection(
            "Simple recipes",
            listOf(
                "Protein curd bowl: curd, fruit, oats, nuts or seeds.",
                "Paneer/tofu stir-fry: vegetables, paneer or tofu, spices, rice or roti.",
                "Dal power plate: dal, vegetables, curd, and a measured serving of rice or roti.",
                "Egg bhurji wrap: eggs, vegetables, whole-wheat roti, and yogurt."
            )
        )
    }
}

@Composable
private fun FoodSection(title: String, lines: List<String>) {
    Card(colors = CardDefaults.cardColors(containerColor = Forest), shape = RoundedCornerShape(22.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = Sand)
            lines.forEach { line -> Text("• $line", color = Mist, lineHeight = 19.sp) }
        }
    }
}

@Composable
private fun WellnessNotice(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF2C261B)), shape = RoundedCornerShape(18.dp)) {
        Text(text, modifier = Modifier.padding(16.dp), color = Sand, lineHeight = 20.sp)
    }
}

@Composable
private fun StoredPhoto(path: String, modifier: Modifier = Modifier) {
    val bitmap = remember(path) { BitmapFactory.decodeFile(path)?.asImageBitmap() }
    if (bitmap == null) {
        Box(
            modifier = modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xFF1B3227)),
            contentAlignment = Alignment.Center
        ) { Text("Photo is no longer available", color = Slate) }
    } else {
        Image(
            bitmap = bitmap,
            contentDescription = "Private progress photo",
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(RoundedCornerShape(16.dp))
        )
    }
}

@Composable
private fun EquipmentDialog(
    initialEquipment: Set<String>,
    isRequired: Boolean,
    onSave: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember(initialEquipment) { mutableStateOf(initialEquipment) }

    AlertDialog(
        onDismissRequest = { if (!isRequired) onDismiss() },
        title = { Text(if (isRequired) "What equipment do you have?" else "Update your equipment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Choose everything available at home. These visual cards shape your workout suggestions.", color = Slate)
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(equipmentOptions.chunked(2)) { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { equipment ->
                                FilterChip(
                                    modifier = Modifier.weight(1f),
                                    selected = equipment.name in selected,
                                    onClick = {
                                        selected = if (equipment.name in selected) selected - equipment.name else selected + equipment.name
                                    },
                                    label = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(equipment.emoji, fontSize = 22.sp)
                                            Spacer(Modifier.width(5.dp))
                                            Text(equipment.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                )
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(selected) }) { Text("Save setup") } },
        dismissButton = if (!isRequired) ({ TextButton(onClick = onDismiss) { Text("Cancel") } }) else null
    )
}

private fun waterProgress(waterMl: Int): Int = (waterMl * 100 / WATER_GOAL_ML).coerceAtMost(100)

private fun formatSleep(minutes: Int): String = if (minutes == 0) "—" else "${minutes / 60}h ${minutes % 60}m"

private fun formatDate(timestamp: Long): String = Instant.ofEpochMilli(timestamp)
    .atZone(ZoneId.systemDefault())
    .toLocalDate()
    .format(DateTimeFormatter.ofPattern("d MMM"))

private fun estimateCalories(name: String, grams: Int): Int {
    val normalized = name.lowercase()
    val per100g = when {
        "chicken" in normalized || "fish" in normalized -> 165
        "paneer" in normalized -> 265
        "tofu" in normalized -> 145
        "egg" in normalized -> 155
        "rice" in normalized -> 130
        "dal" in normalized || "lentil" in normalized -> 120
        "roti" in normalized || "chapati" in normalized -> 300
        "curd" in normalized || "yogurt" in normalized -> 95
        "fruit" in normalized || "apple" in normalized || "banana" in normalized -> 90
        else -> 180
    }
    return (per100g * grams.coerceIn(20, 1000) / 100).coerceAtLeast(20)
}

private fun coachAnswer(question: String): String {
    val normalized = question.lowercase()
    return when {
        "belly" in normalized || "abs" in normalized || "fat" in normalized ->
            "You cannot choose where fat comes off first. Use a modest calorie deficit, strength training 2–4 times a week, daily steps, protein-rich meals, and sleep. Train your core for strength, not as a spot-fat-loss method."
        "protein" in normalized ->
            "Include a protein source in each meal: eggs, dairy, chicken, fish, tofu, paneer, dal, beans, or soy. Your ideal daily amount depends on body size, training, kidney health, and diet, so a dietitian can personalize it."
        "sleep" in normalized ->
            "Aim for a consistent sleep schedule and usually 7–9 hours. Dim screens before bed, get morning daylight, and keep caffeine earlier in the day if it affects you."
        "skin" in normalized || "glow" in normalized ->
            "Keep the basics simple: gentle cleanser, moisturizer, and daily broad-spectrum SPF 30+. Patch-test products and see a dermatologist for persistent irritation, acne, or pigmentation concerns."
        "supplement" in normalized ->
            "Supplements are optional. Food, training, sleep, and hydration matter more. Check quality, medicines, allergies, and health conditions with a clinician or dietitian before taking one."
        else ->
            "Build the next small habit: choose a home workout, add water, log a balanced meal, and protect your sleep. Ask a specific question about training, food, steps, sleep, or skin basics."
    }
}

private data class Equipment(val name: String, val emoji: String)

private val equipmentOptions = listOf(
    Equipment("Bodyweight", "🤸"),
    Equipment("Yoga mat", "🧘"),
    Equipment("Resistance bands", "〰️"),
    Equipment("Dumbbells", "🏋️"),
    Equipment("Kettlebell", "🔔"),
    Equipment("Bench or chair", "🪑"),
    Equipment("Pull-up bar", "🔗"),
    Equipment("Skipping rope", "🪢"),
    Equipment("Foam roller", "🟣"),
    Equipment("Exercise bike", "🚲"),
    Equipment("Treadmill", "🏃"),
    Equipment("Barbell and rack", "🏗️"),
    Equipment("Cable machine", "⚙️"),
    Equipment("Rowing machine", "🚣"),
    Equipment("Medicine ball", "⚽"),
    Equipment("Stability ball", "🔵")
)

private data class Workout(
    val id: String,
    val day: String,
    val title: String,
    val minutes: Int,
    val focus: String,
    val equipment: List<String>,
    val exercises: List<String>,
    val demoId: String? = null
)

private val weeklyPlan = listOf(
    Workout(
        "foundation", "DAY 1", "Full-body foundation", 28,
        "Build basic strength with controlled form.", listOf("Bodyweight"),
        listOf("Squats 3 × 10", "Incline push-ups 3 × 8", "Glute bridges 3 × 12", "Dead bug 3 × 8/side"),
        "push_up"
    ),
    Workout(
        "core-cardio", "DAY 2", "Core and low-impact cardio", 24,
        "Steady movement plus anti-rotation core work.", listOf("Bodyweight"),
        listOf("Brisk walk or march 12 min", "Bird dog 3 × 8/side", "Side plank 3 × 20 sec", "Mountain climber 3 × 20 sec"),
        "jumping_jack"
    ),
    Workout(
        "band-strength", "DAY 3", "Band strength", 30,
        "Train your back, legs, and core at home.", listOf("Resistance bands"),
        listOf("Band row 3 × 12", "Band squat 3 × 12", "Pallof press 3 × 10/side", "Band shoulder press 3 × 10")
    ),
    Workout(
        "dumbbell", "DAY 4", "Dumbbell strength", 32,
        "Progress gradually when each set feels controlled.", listOf("Dumbbells"),
        listOf("Goblet squat 3 × 10", "Floor press 3 × 10", "Romanian deadlift 3 × 10", "Suitcase carry 3 × 30 sec")
    )
)

private data class ExerciseDemo(
    val id: String,
    val title: String,
    val assetPath: String,
    val cues: List<String>
)

private val exerciseDemos = listOf(
    ExerciseDemo(
        id = "push_up",
        title = "3D push-up form",
        assetPath = "models/push_up.glb",
        cues = listOf(
            "Set hands under shoulders and make one straight line from head to heels.",
            "Lower with elbows angled roughly 45 degrees from your body.",
            "Press the floor away without letting the hips sag or pike."
        )
    ),
    ExerciseDemo(
        id = "jumping_jack",
        title = "3D jumping-jack form",
        assetPath = "models/jumping_jack.glb",
        cues = listOf(
            "Start tall with feet together and arms relaxed by your sides.",
            "Move arms and legs together, landing softly with knees relaxed.",
            "Use the low-impact marching version if jumping is uncomfortable."
        )
    ),
    ExerciseDemo(
        id = "bear_crawl",
        title = "3D bear-crawl form",
        assetPath = "models/bear_crawl.glb",
        cues = listOf(
            "Start on hands and toes with your knees hovering just above the floor.",
            "Move the opposite hand and foot together in small, controlled steps.",
            "Keep your hips level and your spine long; stop if wrists or shoulders hurt."
        )
    )
).associateBy(ExerciseDemo::id)

@Composable
private fun FitNestTheme(content: @Composable () -> Unit) {
    val colors: ColorScheme = darkColorScheme(
        primary = Leaf,
        onPrimary = Pine,
        secondary = Sand,
        background = Pine,
        surface = Pine,
        onBackground = Mist,
        onSurface = Mist,
        error = Danger
    )
    MaterialTheme(colorScheme = colors, content = content)
}
