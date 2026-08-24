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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.DEFAULT_ECHO_BRAIN_MINIMUM_SIMILARITY
import com.metrolist.music.constants.EchoBrainAllowAlternativeVersionsKey
import com.metrolist.music.constants.EchoBrainEnabledKey
import com.metrolist.music.constants.EchoBrainMinimumSimilarityKey
import com.metrolist.music.constants.EchoBrainNetworkMode
import com.metrolist.music.constants.EchoBrainNetworkModeKey
import com.metrolist.music.ui.component.EnumDialog
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
    val (minimumSimilarity, onMinimumSimilarityChange) =
        rememberPreference(
            EchoBrainMinimumSimilarityKey,
            defaultValue = DEFAULT_ECHO_BRAIN_MINIMUM_SIMILARITY,
        )
    val (allowAlternativeVersions, onAllowAlternativeVersionsChange) =
        rememberPreference(EchoBrainAllowAlternativeVersionsKey, defaultValue = false)
    val (networkModeValue, onNetworkModeChange) =
        rememberPreference(EchoBrainNetworkModeKey, defaultValue = EchoBrainNetworkMode.WIFI_ONLY.name)
    val networkMode = EchoBrainNetworkMode.fromPreference(networkModeValue)

    var showSimilarityDialog by rememberSaveable { mutableStateOf(false) }
    var showNetworkDialog by rememberSaveable { mutableStateOf(false) }

    if (showSimilarityDialog) {
        EnumDialog(
            onDismiss = { showSimilarityDialog = false },
            onSelect = {
                onMinimumSimilarityChange(it)
                showSimilarityDialog = false
            },
            title = stringResource(R.string.echo_brain_similarity),
            current = minimumSimilarity,
            values = listOf(90, 80, 70, 60),
            valueText = { value -> similarityLabel(value) },
            valueDescription = { value -> similarityDescription(value) },
        )
    }

    if (showNetworkDialog) {
        EnumDialog(
            onDismiss = { showNetworkDialog = false },
            onSelect = {
                onNetworkModeChange(it.name)
                showNetworkDialog = false
            },
            title = stringResource(R.string.echo_brain_network),
            current = networkMode,
            values = EchoBrainNetworkMode.entries.toList(),
            valueText = { mode -> networkModeLabel(mode) },
            valueDescription = { mode -> networkModeDescription(mode) },
        )
    }

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
            text = stringResource(R.string.echo_brain_strict_section_desc),
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
                Material3SettingsItem(
                    icon = painterResource(R.drawable.tune),
                    title = { Text(stringResource(R.string.echo_brain_similarity)) },
                    description = { Text(similarityLabel(minimumSimilarity)) },
                    onClick = { showSimilarityDialog = true },
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.music_note),
                    title = { Text(stringResource(R.string.echo_brain_alternative_versions)) },
                    description = {
                        Text(
                            stringResource(
                                if (allowAlternativeVersions) {
                                    R.string.echo_brain_alternative_versions_on
                                } else {
                                    R.string.echo_brain_alternative_versions_off
                                },
                            ),
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = allowAlternativeVersions,
                            onCheckedChange = onAllowAlternativeVersionsChange,
                        )
                    },
                    onClick = { onAllowAlternativeVersionsChange(!allowAlternativeVersions) },
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.wifi_proxy),
                    title = { Text(stringResource(R.string.echo_brain_network)) },
                    description = { Text(networkModeLabel(networkMode)) },
                    onClick = { showNetworkDialog = true },
                ),
            ),
        )

        Spacer(modifier = Modifier.padding(top = 13.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.echo_brain_how_it_works),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.tune),
                    title = { Text(stringResource(R.string.echo_brain_strict_filter)) },
                    description = { Text(stringResource(R.string.echo_brain_strict_filter_desc)) },
                ),
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

@Composable
private fun similarityLabel(value: Int): String =
    when (value) {
        90 -> stringResource(R.string.echo_brain_similarity_90)
        80 -> stringResource(R.string.echo_brain_similarity_80)
        70 -> stringResource(R.string.echo_brain_similarity_70)
        else -> stringResource(R.string.echo_brain_similarity_60)
    }

@Composable
private fun similarityDescription(value: Int): String =
    when (value) {
        90 -> stringResource(R.string.echo_brain_similarity_90_desc)
        80 -> stringResource(R.string.echo_brain_similarity_80_desc)
        70 -> stringResource(R.string.echo_brain_similarity_70_desc)
        else -> stringResource(R.string.echo_brain_similarity_60_desc)
    }

@Composable
private fun networkModeLabel(mode: EchoBrainNetworkMode): String =
    when (mode) {
        EchoBrainNetworkMode.LOCAL_ONLY -> stringResource(R.string.echo_brain_network_local)
        EchoBrainNetworkMode.WIFI_ONLY -> stringResource(R.string.echo_brain_network_wifi)
        EchoBrainNetworkMode.ANY_NETWORK -> stringResource(R.string.echo_brain_network_any)
    }

@Composable
private fun networkModeDescription(mode: EchoBrainNetworkMode): String =
    when (mode) {
        EchoBrainNetworkMode.LOCAL_ONLY -> stringResource(R.string.echo_brain_network_local_desc)
        EchoBrainNetworkMode.WIFI_ONLY -> stringResource(R.string.echo_brain_network_wifi_desc)
        EchoBrainNetworkMode.ANY_NETWORK -> stringResource(R.string.echo_brain_network_any_desc)
    }
