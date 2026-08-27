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
import com.metrolist.music.constants.EchoBrainArtistDiversity
import com.metrolist.music.constants.EchoBrainArtistDiversityKey
import com.metrolist.music.constants.EchoBrainEnabledKey
import com.metrolist.music.constants.EchoBrainLastDiagnosticKey
import com.metrolist.music.constants.EchoBrainListeningConfirmation
import com.metrolist.music.constants.EchoBrainListeningConfirmationKey
import com.metrolist.music.constants.EchoBrainMinimumSimilarityKey
import com.metrolist.music.constants.EchoBrainNetworkMode
import com.metrolist.music.constants.EchoBrainNetworkModeKey
import com.metrolist.music.constants.EchoBrainQueueContinuity
import com.metrolist.music.constants.EchoBrainQueueContinuityKey
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
    val (artistDiversityValue, onArtistDiversityChange) =
        rememberPreference(EchoBrainArtistDiversityKey, defaultValue = EchoBrainArtistDiversity.BALANCED.name)
    val artistDiversity = EchoBrainArtistDiversity.fromPreference(artistDiversityValue)
    val (listeningConfirmationValue, onListeningConfirmationChange) =
        rememberPreference(
            EchoBrainListeningConfirmationKey,
            defaultValue = EchoBrainListeningConfirmation.SIXTY_PERCENT.name,
        )
    val listeningConfirmation = EchoBrainListeningConfirmation.fromPreference(listeningConfirmationValue)
    val (queueContinuityValue, onQueueContinuityChange) =
        rememberPreference(
            EchoBrainQueueContinuityKey,
            defaultValue = EchoBrainQueueContinuity.DOMINANT.name,
        )
    val queueContinuity = EchoBrainQueueContinuity.fromPreference(queueContinuityValue)
    val (networkModeValue, onNetworkModeChange) =
        rememberPreference(EchoBrainNetworkModeKey, defaultValue = EchoBrainNetworkMode.WIFI_ONLY.name)
    val networkMode = EchoBrainNetworkMode.fromPreference(networkModeValue)
    val (lastDiagnostic, _) = rememberPreference(EchoBrainLastDiagnosticKey, defaultValue = "")

    var showSimilarityDialog by rememberSaveable { mutableStateOf(false) }
    var showArtistDiversityDialog by rememberSaveable { mutableStateOf(false) }
    var showListeningConfirmationDialog by rememberSaveable { mutableStateOf(false) }
    var showQueueContinuityDialog by rememberSaveable { mutableStateOf(false) }
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

    if (showArtistDiversityDialog) {
        EnumDialog(
            onDismiss = { showArtistDiversityDialog = false },
            onSelect = {
                onArtistDiversityChange(it.name)
                showArtistDiversityDialog = false
            },
            title = stringResource(R.string.echo_brain_artist_diversity),
            current = artistDiversity,
            values = EchoBrainArtistDiversity.entries.toList(),
            valueText = { diversity -> artistDiversityLabel(diversity) },
            valueDescription = { diversity -> artistDiversityDescription(diversity) },
        )
    }

    if (showListeningConfirmationDialog) {
        EnumDialog(
            onDismiss = { showListeningConfirmationDialog = false },
            onSelect = {
                onListeningConfirmationChange(it.name)
                showListeningConfirmationDialog = false
            },
            title = stringResource(R.string.echo_brain_listening_confirmation),
            current = listeningConfirmation,
            values = EchoBrainListeningConfirmation.entries.toList(),
            valueText = { confirmation -> listeningConfirmationLabel(confirmation) },
            valueDescription = { confirmation -> listeningConfirmationDescription(confirmation) },
        )
    }

    if (showQueueContinuityDialog) {
        EnumDialog(
            onDismiss = { showQueueContinuityDialog = false },
            onSelect = {
                onQueueContinuityChange(it.name)
                showQueueContinuityDialog = false
            },
            title = stringResource(R.string.echo_brain_queue_continuity),
            current = queueContinuity,
            values = EchoBrainQueueContinuity.entries.toList(),
            valueText = { continuity -> queueContinuityLabel(continuity) },
            valueDescription = { continuity -> queueContinuityDescription(continuity) },
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
                    icon = painterResource(R.drawable.group),
                    title = { Text(stringResource(R.string.echo_brain_artist_diversity)) },
                    description = { Text(artistDiversityLabel(artistDiversity)) },
                    onClick = { showArtistDiversityDialog = true },
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.timer),
                    title = { Text(stringResource(R.string.echo_brain_listening_confirmation)) },
                    description = { Text(listeningConfirmationLabel(listeningConfirmation)) },
                    onClick = { showListeningConfirmationDialog = true },
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.queue_music),
                    title = { Text(stringResource(R.string.echo_brain_queue_continuity)) },
                    description = { Text(queueContinuityLabel(queueContinuity)) },
                    onClick = { showQueueContinuityDialog = true },
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.wifi_proxy),
                    title = { Text(stringResource(R.string.echo_brain_network)) },
                    description = { Text(networkModeLabel(networkMode)) },
                    onClick = { showNetworkDialog = true },
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.radio),
                    title = { Text(stringResource(R.string.echo_brain_status)) },
                    description = {
                        Text(
                            lastDiagnostic.ifBlank {
                                stringResource(R.string.echo_brain_status_idle)
                            },
                        )
                    },
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

@Composable
private fun artistDiversityLabel(diversity: EchoBrainArtistDiversity): String =
    when (diversity) {
        EchoBrainArtistDiversity.UNLIMITED -> stringResource(R.string.echo_brain_artist_diversity_unlimited)
        EchoBrainArtistDiversity.BALANCED -> stringResource(R.string.echo_brain_artist_diversity_balanced)
        EchoBrainArtistDiversity.HIGH -> stringResource(R.string.echo_brain_artist_diversity_high)
    }

@Composable
private fun artistDiversityDescription(diversity: EchoBrainArtistDiversity): String =
    when (diversity) {
        EchoBrainArtistDiversity.UNLIMITED -> stringResource(R.string.echo_brain_artist_diversity_unlimited_desc)
        EchoBrainArtistDiversity.BALANCED -> stringResource(R.string.echo_brain_artist_diversity_balanced_desc)
        EchoBrainArtistDiversity.HIGH -> stringResource(R.string.echo_brain_artist_diversity_high_desc)
    }

@Composable
private fun listeningConfirmationLabel(confirmation: EchoBrainListeningConfirmation): String =
    when (confirmation) {
        EchoBrainListeningConfirmation.IMMEDIATE -> stringResource(R.string.echo_brain_listening_confirmation_immediate)
        EchoBrainListeningConfirmation.SIXTY_PERCENT -> stringResource(R.string.echo_brain_listening_confirmation_60)
        EchoBrainListeningConfirmation.EIGHTY_PERCENT -> stringResource(R.string.echo_brain_listening_confirmation_80)
    }

@Composable
private fun listeningConfirmationDescription(confirmation: EchoBrainListeningConfirmation): String =
    when (confirmation) {
        EchoBrainListeningConfirmation.IMMEDIATE -> stringResource(R.string.echo_brain_listening_confirmation_immediate_desc)
        EchoBrainListeningConfirmation.SIXTY_PERCENT -> stringResource(R.string.echo_brain_listening_confirmation_60_desc)
        EchoBrainListeningConfirmation.EIGHTY_PERCENT -> stringResource(R.string.echo_brain_listening_confirmation_80_desc)
    }

@Composable
private fun queueContinuityLabel(continuity: EchoBrainQueueContinuity): String =
    when (continuity) {
        EchoBrainQueueContinuity.MIX_PRESERVING -> stringResource(R.string.echo_brain_queue_continuity_mix)
        EchoBrainQueueContinuity.DOMINANT -> stringResource(R.string.echo_brain_queue_continuity_dominant)
    }

@Composable
private fun queueContinuityDescription(continuity: EchoBrainQueueContinuity): String =
    when (continuity) {
        EchoBrainQueueContinuity.MIX_PRESERVING -> stringResource(R.string.echo_brain_queue_continuity_mix_desc)
        EchoBrainQueueContinuity.DOMINANT -> stringResource(R.string.echo_brain_queue_continuity_dominant_desc)
    }
