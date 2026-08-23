/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.EchoBrainEnabledKey
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EchoBrainSettings(
    navController: NavController,
) {
    LocalPlayerConnection.current ?: return
    val (echoBrainEnabled, onEchoBrainEnabledChange) =
        rememberPreference(EchoBrainEnabledKey, defaultValue = true)

    Column(
        Modifier
            .windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                ),
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top),
            ),
        )

        Text(
            text = stringResource(R.string.echo_brain_section_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
        )

        Material3SettingsGroup(
            title = stringResource(R.string.echo_brain),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.radio),
                    title = { Text(stringResource(R.string.echo_brain_enabled)) },
                    description = { Text(stringResource(R.string.echo_brain_enabled_desc)) },
                    trailingContent = {
                        Switch(
                            checked = echoBrainEnabled,
                            onCheckedChange = onEchoBrainEnabledChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        if (echoBrainEnabled) R.drawable.check else R.drawable.close,
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.padding(2.dp),
                                )
                            },
                        )
                    },
                    onClick = { onEchoBrainEnabledChange(!echoBrainEnabled) },
                ),
            ),
        )

        Spacer(modifier = Modifier.padding(top = 13.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.echo_brain_how_it_works),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.add_circle),
                    title = { Text(stringResource(R.string.echo_brain_preserves_queue)) },
                    description = { Text(stringResource(R.string.echo_brain_preserves_queue_desc)) },
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.refresh),
                    title = { Text(stringResource(R.string.echo_brain_refreshes_candidates)) },
                    description = { Text(stringResource(R.string.echo_brain_refreshes_candidates_desc)) },
                ),
            ),
        )

        Spacer(modifier = Modifier.padding(bottom = 16.dp))
    }

    TopAppBar(
        title = { Text(stringResource(R.string.echo_brain)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        },
    )
}
