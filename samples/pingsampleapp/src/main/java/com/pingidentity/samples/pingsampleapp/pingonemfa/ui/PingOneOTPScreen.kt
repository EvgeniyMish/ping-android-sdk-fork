/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.pingonemfa.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pingidentity.samples.pingsampleapp.R
import com.pingidentity.samples.pingsampleapp.authenticator.ui.components.EmptyStateMessage
import com.pingidentity.samples.pingsampleapp.authenticator.ui.components.LoadingIndicator
import com.pingidentity.samples.pingsampleapp.pingonemfa.PingOneMFAViewModel

/**
 * Screen that fetches and displays the current PingOne MFA one-time passcode.
 *
 * Calls [PingOneMFAViewModel.collectOtp] on first composition and whenever the user
 * taps "Refresh". Errors are shown via a Snackbar.
 *
 * @param onBack Navigation callback for the top-app-bar back button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PingOneOTPScreen(
    onBack: (() -> Unit)? = null,
    viewModel: PingOneMFAViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Fetch OTP as soon as the screen is first composed
    LaunchedEffect(Unit) {
        viewModel.collectOtp()
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
                    title = { Text(stringResource(R.string.text_pingone_mfa_screen_otp_title)) },
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
            contentAlignment = Alignment.Center,
        ) {
            when {
                state.isLoadingOtp -> {
                    LoadingIndicator(
                        message = stringResource(R.string.text_pingone_mfa_loading_otp),
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                state.isOtpDeviceNotPaired -> {
                    EmptyStateMessage(
                        title = stringResource(R.string.accounts_empty_state_title),
                        subtitle = stringResource(R.string.accounts_empty_state_subtitle),
                    )
                }

                state.otp != null -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = state.otp!!.code,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 8.sp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Refreshes in ${state.otpSecondsRemaining}s",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }

                else -> {
                    // No OTP yet and not loading — likely initial state before LaunchedEffect fires,
                    // or a failure was shown via Snackbar. Offer a manual retry.
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.text_pingone_mfa_otp_no_passcode),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.collectOtp() }) {
                            Text(stringResource(R.string.text_pingone_mfa_try_again))
                        }
                    }
                }
            }
        }
    }
}
