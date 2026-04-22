package com.swordfish.lemuroid.app.mobile.feature.settings.advanced
import android.content.Intent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.main.MainRoute
import com.swordfish.lemuroid.app.shared.logs.LogViewerActivity
import com.swordfish.lemuroid.app.shared.storage.cache.StorageCleanupManager
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidCardSettingsGroup
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsList
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsMenuLink
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsPage
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsSlider
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsSwitch
import com.swordfish.lemuroid.app.utils.android.settings.booleanPreferenceState
import com.swordfish.lemuroid.app.utils.android.settings.indexPreferenceState
import com.swordfish.lemuroid.app.utils.android.settings.intPreferenceState
import android.text.format.Formatter
import android.widget.Toast
import kotlinx.coroutines.launch

@Composable
fun AdvancedSettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: AdvancedSettingsViewModel,
    navController: NavHostController,
) {
    val context = LocalContext.current
    val uiState =
        viewModel.uiState
            .collectAsState()
            .value

    LemuroidSettingsPage(
        modifier = modifier.fillMaxSize(),
    ) {
        if (uiState?.cache == null) {
            return@LemuroidSettingsPage
        }

        GeneralSettings()
        SystemInteractionSettings()
        CacheManagementSettings(uiState.cache)
        FactoryResetSection(viewModel, navController)
    }
}

@Composable
private fun SystemInteractionSettings() {
    LemuroidCardSettingsGroup(
        title = { Text(text = stringResource(id = R.string.settings_category_system_interaction)) },
    ) {
        val rumbleEnabled = booleanPreferenceState(R.string.pref_key_enable_rumble, false)
        LemuroidSettingsSwitch(
            state = rumbleEnabled,
            title = { Text(text = stringResource(id = R.string.settings_title_enable_rumble)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_enable_rumble)) },
        )
        LemuroidSettingsSwitch(
            enabled = rumbleEnabled.value,
            state = booleanPreferenceState(R.string.pref_key_enable_device_rumble, false),
            title = { Text(text = stringResource(id = R.string.settings_title_enable_device_rumble)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_enable_device_rumble)) },
        )
        LemuroidSettingsSlider(
            state =
                intPreferenceState(
                    key = stringResource(id = R.string.pref_key_tilt_sensitivity_index),
                    default = 6,
                ),
            steps = 10,
            valueRange = 0f..10f,
            enabled = true,
            title = { Text(text = stringResource(R.string.settings_title_tilt_sensitivity)) },
        )
    }
}

@Composable
private fun GeneralSettings() {
    val context = LocalContext.current
    LemuroidCardSettingsGroup(
        title = { Text(text = stringResource(id = R.string.settings_category_general)) },
    ) {
        LemuroidSettingsSwitch(
            state = booleanPreferenceState(R.string.pref_key_low_latency_audio, false),
            title = { Text(text = stringResource(id = R.string.settings_title_low_latency_audio)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_low_latency_audio)) },
        )
        LemuroidSettingsSwitch(
            state = booleanPreferenceState(R.string.pref_key_allow_direct_game_load, true),
            title = { Text(text = stringResource(id = R.string.settings_title_direct_game_load)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_direct_game_load)) },
        )
        LemuroidSettingsMenuLink(
            title = { Text(text = stringResource(id = R.string.settings_title_log_viewer)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_log_viewer)) },
            onClick = { context.startActivity(Intent(context, LogViewerActivity::class.java)) },
        )
    }
}

@Composable
private fun FactoryResetSection(
    viewModel: AdvancedSettingsViewModel,
    navController: NavController,
) {
    val factoryResetDialogState = remember { mutableStateOf(false) }

    LemuroidCardSettingsGroup(
        title = { Text(text = stringResource(id = R.string.settings_category_factory_reset)) },
    ) {
        LemuroidSettingsMenuLink(
            title = { Text(text = stringResource(id = R.string.settings_title_reset_settings)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_reset_settings)) },
            onClick = { factoryResetDialogState.value = true },
        )
    }

    if (factoryResetDialogState.value) {
        FactoryResetDialog(factoryResetDialogState, viewModel, navController)
    }
}

@Composable
private fun CacheManagementSettings(cacheState: AdvancedSettingsViewModel.CacheState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cleanupDialogState = remember { mutableStateOf<StorageCleanupManager.Bucket?>(null) }
    val cleanupRefreshToken = remember { mutableStateOf(0) }
    val cleanupBuckets =
        produceState(
            initialValue = emptyList<StorageCleanupManager.Bucket>(),
            key1 = cleanupRefreshToken.value,
        ) {
            value = StorageCleanupManager.getBuckets(context)
        }

    LemuroidCardSettingsGroup(
        title = { Text(text = stringResource(id = R.string.settings_cleanup_section_title)) },
    ) {
        LemuroidSettingsList(
            title = { Text(text = stringResource(R.string.settings_title_maximum_cache_usage)) },
            items = cacheState.displayNames,
            state =
                indexPreferenceState(
                    R.string.pref_key_max_cache_size,
                    cacheState.default,
                    cacheState.values,
                ),
        )

        cleanupBuckets.value.forEach { bucket ->
            val label = Formatter.formatShortFileSize(context, bucket.sizeBytes)
            LemuroidSettingsMenuLink(
                title = { Text(text = stringResource(StorageCleanupManager.getBucketLabelRes(bucket.type))) },
                subtitle = { Text(text = stringResource(R.string.settings_clear_entry_summary, label)) },
                onClick = {
                    cleanupDialogState.value = bucket
                },
            )
        }
    }

    cleanupDialogState.value?.let { bucket ->
        val bucketTitle = stringResource(StorageCleanupManager.getBucketLabelRes(bucket.type))
        val bucketSize = Formatter.formatShortFileSize(context, bucket.sizeBytes)
        AlertDialog(
            title = { Text(text = stringResource(R.string.settings_clear_confirm_title)) },
            text = {
                Text(
                    text = stringResource(R.string.settings_clear_confirm_message, bucketTitle, bucketSize),
                )
            },
            onDismissRequest = { cleanupDialogState.value = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        cleanupDialogState.value = null
                        scope.launch {
                            val freed = StorageCleanupManager.cleanBucket(context, bucket.type)
                            val freedLabel = Formatter.formatShortFileSize(context, freed)
                            Toast.makeText(
                                context,
                                context.getString(R.string.settings_clear_done, bucketTitle, freedLabel),
                                Toast.LENGTH_SHORT,
                            ).show()
                            cleanupRefreshToken.value += 1
                        }
                    },
                ) {
                    Text(text = stringResource(R.string.delete_games_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { cleanupDialogState.value = null }) {
                    Text(text = stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun FactoryResetDialog(
    factoryResetDialogState: MutableState<Boolean>,
    viewModel: AdvancedSettingsViewModel,
    navController: NavController,
) {
    val onDismiss = {
        factoryResetDialogState.value = false
    }
    AlertDialog(
        title = { Text(stringResource(id = R.string.reset_settings_warning_message_title)) },
        text = { Text(stringResource(id = R.string.reset_settings_warning_message_description)) },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    viewModel.resetAllSettings()
                    navController.popBackStack(MainRoute.SETTINGS.route, false)
                },
            ) {
                Text(text = stringResource(id = R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.cancel))
            }
        },
    )
}

