package com.jeeva.fitnest

import android.content.Context

data class ProgressPhoto(val takenAt: Long, val path: String)

data class FoodEntry(val name: String, val calories: Int, val loggedAt: Long)

data class FitnessState(
    val waterMl: Int = 0,
    val sleepMinutes: Int = 0,
    val steps: Int = 0,
    val selectedEquipment: Set<String> = emptySet(),
    val onboardingComplete: Boolean = false,
    val completedWorkouts: Set<String> = emptySet(),
    val progressPhotos: List<ProgressPhoto> = emptyList(),
    val foodPhotoPath: String? = null,
    val foodEntries: List<FoodEntry> = emptyList()
)

class FitnessStore(context: Context) {
    private val preferences = context.getSharedPreferences("fitnest_state", Context.MODE_PRIVATE)

    fun read(): FitnessState = FitnessState(
        waterMl = preferences.getInt(KEY_WATER, 0),
        sleepMinutes = preferences.getInt(KEY_SLEEP, 0),
        steps = preferences.getInt(KEY_STEPS, 0),
        selectedEquipment = preferences.getStringSet(KEY_EQUIPMENT, emptySet()).orEmpty(),
        onboardingComplete = preferences.getBoolean(KEY_ONBOARDING_COMPLETE, false),
        completedWorkouts = preferences.getStringSet(KEY_COMPLETED_WORKOUTS, emptySet()).orEmpty(),
        progressPhotos = decodePhotos(preferences.getString(KEY_PROGRESS_PHOTOS, "").orEmpty()),
        foodPhotoPath = preferences.getString(KEY_FOOD_PHOTO, null),
        foodEntries = decodeFoodEntries(preferences.getString(KEY_FOOD_ENTRIES, "").orEmpty())
    )

    fun save(state: FitnessState) {
        preferences.edit()
            .putInt(KEY_WATER, state.waterMl)
            .putInt(KEY_SLEEP, state.sleepMinutes)
            .putInt(KEY_STEPS, state.steps)
            .putStringSet(KEY_EQUIPMENT, state.selectedEquipment)
            .putBoolean(KEY_ONBOARDING_COMPLETE, state.onboardingComplete)
            .putStringSet(KEY_COMPLETED_WORKOUTS, state.completedWorkouts)
            .putString(KEY_PROGRESS_PHOTOS, encodePhotos(state.progressPhotos))
            .putString(KEY_FOOD_PHOTO, state.foodPhotoPath)
            .putString(KEY_FOOD_ENTRIES, encodeFoodEntries(state.foodEntries))
            .apply()
    }

    private fun encodePhotos(photos: List<ProgressPhoto>): String = photos.joinToString("~") {
        "${it.takenAt}|${it.path}"
    }

    private fun decodePhotos(value: String): List<ProgressPhoto> = value.split("~")
        .mapNotNull { item ->
            val pieces = item.split("|", limit = 2)
            val takenAt = pieces.firstOrNull()?.toLongOrNull()
            val path = pieces.getOrNull(1)

            if (takenAt != null && !path.isNullOrBlank()) ProgressPhoto(takenAt, path) else null
        }
        .sortedByDescending(ProgressPhoto::takenAt)

    private fun encodeFoodEntries(entries: List<FoodEntry>): String = entries.joinToString("~") {
        "${it.loggedAt}|${it.calories}|${it.name.replace("|", " ").replace("~", " ")}" 
    }

    private fun decodeFoodEntries(value: String): List<FoodEntry> = value.split("~")
        .mapNotNull { item ->
            val pieces = item.split("|", limit = 3)
            val loggedAt = pieces.getOrNull(0)?.toLongOrNull()
            val calories = pieces.getOrNull(1)?.toIntOrNull()
            val name = pieces.getOrNull(2)

            if (loggedAt != null && calories != null && !name.isNullOrBlank()) {
                FoodEntry(name, calories, loggedAt)
            } else {
                null
            }
        }
        .sortedByDescending(FoodEntry::loggedAt)

    private companion object {
        const val KEY_WATER = "water_ml"
        const val KEY_SLEEP = "sleep_minutes"
        const val KEY_STEPS = "steps"
        const val KEY_EQUIPMENT = "equipment"
        const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        const val KEY_COMPLETED_WORKOUTS = "completed_workouts"
        const val KEY_PROGRESS_PHOTOS = "progress_photos"
        const val KEY_FOOD_PHOTO = "food_photo"
        const val KEY_FOOD_ENTRIES = "food_entries"
    }
}
