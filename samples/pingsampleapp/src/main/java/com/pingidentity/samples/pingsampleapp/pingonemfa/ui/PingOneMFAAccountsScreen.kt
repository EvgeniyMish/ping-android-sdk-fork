/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.pingonemfa.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pingidentity.samples.pingsampleapp.R
import com.pingidentity.samples.pingsampleapp.authenticator.ui.components.EmptyStateMessage
import com.pingidentity.samples.pingsampleapp.authenticator.ui.components.LoadingIndicator
import com.pingidentity.samples.pingsampleapp.pingonemfa.PingOneMFAViewModel

/**
 * Displays the list of paired PingOne MFA accounts.
 *
 * Fetches accounts on first composition via [PingOneMFAViewModel.loadAccounts].
 * Each account shows its name, region, and ID. Errors are surfaced via an AlertDialog.
 *
 * @param onBack Navigation callback for the top-app-bar back button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PingOneMFAAccountsScreen(
    onBack: (() -> Unit)? = null,
    onScanQr: () -> Unit = {},
    viewModel: PingOneMFAViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()

    // Fetch accounts as soon as the screen is first composed
    LaunchedEffect(Unit) {
        viewModel.loadAccounts()
    }

    // Show error dialog. Retry re-fetches the accounts; OK just dismisses.
    state.error?.let { errorMessage ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text(stringResource(R.string.error_title)) },
            text = { Text(errorMessage) },
            confirmButton = {
                Button(onClick = {
                    viewModel.clearError()
                    viewModel.loadAccounts()
                }) {
                    Text(stringResource(R.string.retry))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { viewModel.clearError() }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            if (onBack != null) {
                TopAppBar(
                    title = { Text(stringResource(R.string.text_pingone_mfa_screen_accounts_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onScanQr) {
                Icon(
                    imageVector = Icons.Filled.QrCodeScanner,
                    contentDescription = stringResource(R.string.text_pingone_mfa_qr_scanner_title),
                )
            }
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(paddingValues),
        ) {
            when {
                state.isLoadingAccounts -> {
                    // Reuse the app-wide loading indicator style
                    LoadingIndicator(
                        message = stringResource(R.string.text_pingone_mfa_loading_accounts),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                state.accounts.isEmpty() -> {
                    EmptyStateMessage(
                        title = stringResource(R.string.accounts_empty_state_title),
                        subtitle = stringResource(R.string.accounts_empty_state_subtitle),
                    )
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(state.accounts) { account ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AccountBox,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    tint = colorResource(R.color.primary_dark),
                                )
                                Column(
                                    modifier = Modifier.padding(start = 16.dp),
                                ) {
                                    val displayName = listOfNotNull(
                                        account.name?.takeIf { it.isNotBlank() },
                                        account.family?.takeIf { it.isNotBlank() }
                                    ).joinToString(" ").ifBlank { account.username }
                                    Text(
                                        text = displayName,
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    Text(
                                        text = "Region: ${account.region}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = "ID: ${account.id}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}
