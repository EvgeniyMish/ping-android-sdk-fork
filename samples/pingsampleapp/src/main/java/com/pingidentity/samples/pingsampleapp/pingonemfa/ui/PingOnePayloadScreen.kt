/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.pingonemfa.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pingidentity.samples.pingsampleapp.R
import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pingidentity.samples.pingsampleapp.authenticator.ui.components.LoadingIndicator
import com.pingidentity.samples.pingsampleapp.pingonemfa.PingOneMFAViewModel

/**
 * Screen that fetches and displays the PingOne mobile payload.
 *
 * Calls [PingOneMFAViewModel.collectPayload] on first composition. The payload is shown
 * in a scrollable box with a Copy button pinned at the bottom. Errors are shown via a
 * Snackbar.
 *
 * @param onBack Navigation callback for the top-app-bar back button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PingOnePayloadScreen(
    onBack: (() -> Unit)? = null,
    viewModel: PingOneMFAViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboard.current
    val scope = rememberCoroutineScope()

    // Fetch payload as soon as the screen is first composed
    LaunchedEffect(Unit) {
        viewModel.collectPayload()
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            if (onBack != null) {
                TopAppBar(
                    title = { Text(stringResource(R.string.text_pingone_mfa_screen_payload_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(paddingValues),
        ) {
            when {
                state.isLoadingPayload -> {
                    LoadingIndicator(
                        message = stringResource(R.string.text_pingone_mfa_loading_payload),
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                state.payload != null -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        // Scrollable payload box occupies all available space above the button
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(16.dp)
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    shape = MaterialTheme.shapes.medium,
                                )
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            Text(
                                text = state.payload!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    clipboardManager.setClipEntry(
                                        ClipEntry(ClipData.newPlainText("payload", state.payload))
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                            )
                            Text(
                                text = stringResource(R.string.text_pingone_mfa_copy),
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }

                else -> {
                    // No payload yet and not loading — offer a manual retry after an error
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Button(onClick = { viewModel.collectPayload() }) {
                            Text(stringResource(R.string.text_pingone_mfa_try_again))
                        }
                    }
                }
            }
        }
    }
}
