/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.pingonemfa.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pingidentity.pingonemfa.push.PushNotification
import com.pingidentity.pingonemfa.push.PushType
import com.pingidentity.samples.pingsampleapp.R
import com.pingidentity.samples.pingsampleapp.pingonemfa.ui.components.ApproveDenyRow
import com.pingidentity.samples.pingsampleapp.pingonemfa.ui.components.ManualNumberChallenge
import com.pingidentity.samples.pingsampleapp.pingonemfa.ui.components.NumberChallengeOptions
import kotlinx.coroutines.launch

/** Models the three dialog states the push notification screen can be in. */
private sealed interface DialogState {
    data object None : DialogState
    data class Success(val title: String, val message: String) : DialogState
    data class Error(val message: String) : DialogState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PingOnePushNotificationScreen(
    notification: PushNotification,
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var dialogState by remember { mutableStateOf<DialogState>(DialogState.None) }

    val approvedTitle = stringResource(R.string.text_pingone_mfa_approved_title)
    val approvedMessage = stringResource(R.string.text_pingone_mfa_approved_message)
    val deniedTitle = stringResource(R.string.text_pingone_mfa_denied_title)
    val deniedMessage = stringResource(R.string.text_pingone_mfa_denied_message)
    val approvalFailedMessage = stringResource(R.string.text_pingone_mfa_approval_failed)
    val denyFailedMessage = stringResource(R.string.text_pingone_mfa_deny_failed)

    fun approve(numberChallenge: Int? = null) {
        isLoading = true
        scope.launch {
            notification.approveNotification(
                context = context,
                authenticationMethod = "app",
                numberChallenge = numberChallenge
            ).onSuccess {
                isLoading = false
                dialogState = DialogState.Success(approvedTitle, approvedMessage)
            }.onFailure { e ->
                isLoading = false
                dialogState = DialogState.Error(e.message ?: approvalFailedMessage)
            }
        }
    }

    fun deny() {
        isLoading = true
        scope.launch {
            notification.denyNotification(context)
                .onSuccess {
                    isLoading = false
                    dialogState = DialogState.Success(deniedTitle, deniedMessage)
                }.onFailure { e ->
                    isLoading = false
                    dialogState = DialogState.Error(e.message ?: denyFailedMessage)
                }
        }
    }

    // Show dialog on top of the screen
    when (val state = dialogState) {
        is DialogState.Success -> AlertDialog(
            onDismissRequest = onFinish,
            title = { Text(state.title) },
            text = { Text(state.message) },
            confirmButton = {
                Button(onClick = onFinish) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
        is DialogState.Error -> AlertDialog(
            onDismissRequest = onFinish,
            title = { Text(stringResource(R.string.error_title)) },
            text = { Text(state.message) },
            confirmButton = {
                Button(onClick = onFinish) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
        DialogState.None -> Unit
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.text_pingone_mfa_screen_push_title)) },
                navigationIcon = {
                    IconButton(onClick = onFinish) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = notification.title ?: stringResource(R.string.system_notification_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = notification.message ?: stringResource(R.string.system_notification_content),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (isLoading) {
                CircularProgressIndicator()
            } else {
                when (notification.getPushType()) {
                    PushType.DEFAULT -> {
                        ApproveDenyRow(
                            onApprove = { approve() },
                            onDeny = { deny() }
                        )
                    }
                    PushType.CHALLENGE -> {
                        val options = notification.getNumbersChallenge()
                        if (options != null) {
                            NumberChallengeOptions(
                                options = options,
                                onSelected = { approve(it) },
                                onDeny = { deny() }
                            )
                        } else {
                            ManualNumberChallenge(
                                onConfirm = { number -> approve(number) },
                                onDeny = { deny() }
                            )
                        }
                    }
                    PushType.DRY -> {
                        Text(
                            text = stringResource(R.string.text_pingone_mfa_dry_push_message),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onFinish) { Text(stringResource(R.string.text_pingone_mfa_dismiss)) }
                    }
                }
            }
        }
    }
}
