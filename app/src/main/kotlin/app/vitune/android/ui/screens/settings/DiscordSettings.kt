package app.vitune.android.ui.screens.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.vitune.android.DiscordLoginActivity
import app.vitune.android.R
import app.vitune.android.preferences.DiscordPreferences
import app.vitune.android.service.DiscordPresence
import app.vitune.android.ui.components.themed.TextFieldDialog
import app.vitune.android.utils.hasPermission
import app.vitune.android.utils.toast
import app.vitune.core.ui.utils.isAtLeastAndroid13

@Composable
fun DiscordSettingsGroup() = SettingsGroup(
    title = stringResource(R.string.discord),
    description = stringResource(R.string.discord_presence_warning),
    important = true
) {
    var isEditingToken by rememberSaveable { mutableStateOf(false) }
    var isEditingApplicationId by rememberSaveable { mutableStateOf(false) }
    // Needed on Android 13+ for the "Discord rejected your token" notification.
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { }
    )
    fun requestNotificationPermission() {
        if (
            isAtLeastAndroid13 &&
            !context.applicationContext.hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        ) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    val loginLauncher = rememberLauncherForActivityResult(DiscordLoginActivity.Contract()) { token ->
        if (token.isNullOrBlank()) return@rememberLauncherForActivityResult
        DiscordPreferences.token = token
        DiscordPreferences.enabled = true
        context.toast(R.string.discord_login_success)
        requestNotificationPermission()
    }

    // The "token rejected" notification asks for the login screen to open straight away.
    LaunchedEffect(Unit) {
        SettingsScreenRequests.discordLoginRequested.collect { requested ->
            if (requested) {
                loginLauncher.launch(Unit)
                SettingsScreenRequests.discordLoginRequested.value = false
            }
        }
    }

    if (isEditingToken) TextFieldDialog(
        hintText = stringResource(R.string.discord_token),
        onDismiss = { isEditingToken = false },
        onAccept = {
            DiscordPreferences.token = it.trim()
            if (DiscordPreferences.enabled) requestNotificationPermission()
        },
        isTextInputValid = { true }
    )

    if (isEditingApplicationId) TextFieldDialog(
        hintText = stringResource(R.string.discord_application_id),
        initialTextInput = DiscordPreferences.applicationId,
        onDismiss = { isEditingApplicationId = false },
        onAccept = { DiscordPreferences.applicationId = it.trim() },
        isTextInputValid = { it.isBlank() || it.trim().all(Char::isDigit) }
    )

    SwitchSettingsEntry(
        title = stringResource(R.string.discord_presence),
        text = stringResource(R.string.discord_presence_description),
        isChecked = DiscordPreferences.enabled,
        onCheckedChange = {
            DiscordPreferences.enabled = it
            if (it) requestNotificationPermission()
        }
    )

    if (DiscordPreferences.enabled) {
        val status by DiscordPresence.status.collectAsState()
        SettingsEntry(
            title = stringResource(R.string.discord_status),
            text = stringResource(
                when (status) {
                    DiscordPresence.Status.Off -> R.string.discord_status_off
                    DiscordPresence.Status.Connecting -> R.string.discord_status_connecting
                    DiscordPresence.Status.Connected -> R.string.discord_status_connected
                    DiscordPresence.Status.Reconnecting -> R.string.discord_status_reconnecting
                    DiscordPresence.Status.TokenRejected -> R.string.discord_status_token_rejected
                }
            ),
            onClick = { }
        )
    }

    SettingsEntry(
        title = stringResource(R.string.discord_login),
        text = stringResource(R.string.discord_login_description),
        onClick = { loginLauncher.launch(Unit) }
    )

    SettingsEntry(
        title = stringResource(R.string.discord_token),
        text = stringResource(
            if (DiscordPreferences.token.isBlank()) R.string.discord_token_not_set
            else R.string.discord_token_set
        ),
        onClick = { isEditingToken = true }
    )

    SettingsEntry(
        title = stringResource(R.string.discord_application_id),
        text = DiscordPreferences.applicationId.ifBlank {
            stringResource(R.string.discord_application_id_description)
        },
        onClick = { isEditingApplicationId = true }
    )
}
