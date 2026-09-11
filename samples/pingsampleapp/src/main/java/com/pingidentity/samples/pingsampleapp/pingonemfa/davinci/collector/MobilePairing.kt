package com.pingidentity.samples.pingsampleapp.pingonemfa.davinci.collector

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pingidentity.pingonemfa.commons.PingOneMFAException
import com.pingidentity.pingonemfa.davinci.MobilePairingCollector
import com.pingidentity.samples.pingsampleapp.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Structured error surfaced to the UI after [MobilePairingCollector.collect] fails.
 *
 * @param code   Native SDK error code, or `"INTERNAL_ERROR"` for non-SDK failures, or
 *               `"USER_CANCELLED"` when the user tapped Cancel.
 * @param message Human-readable description of the failure.
 * @param details Optional extra context from [PingOneMFAException.internalErrorsList]
 *                `userInfo` map, formatted as `"key: value"` lines. Null when absent.
 */
private data class PairingError(
    val code: String?,
    val message: String,
    val details: String?,
)


/**
 * Composable that drives a [com.pingidentity.pingonemfa.davinci.MobilePairingCollector] through
 * the DaVinci flow. Three visual states:
 *
 * - **In progress** — spinner + "Pairing your device…" + Cancel button.
 * - **Success** — red check icon + "Pairing successful" + Continue button.
 * - **Failure** — red error icon + "Pairing failed" + detail text + Continue button.
 *
 * In the terminal states the user taps "Continue" to submit the result to the server via
 * [onNext]. On failure, tapping Continue immediately hides the card (matching Cancel) while
 * the submission runs in the background; on success the card stays visible until the
 * submission completes ([isSubmitting] disables the button to prevent double-taps). Cancel
 * records a `USER_CANCELLED` payload so the server-side connector can handle the
 * cancellation gracefully.
 */
@Composable
fun MobilePairing(
    field: MobilePairingCollector,
    onNext: () -> Unit,
) {
    var pairingJob: Job? by remember { mutableStateOf(null) }
    // Set to true by Cancel and by Continue on the failure card — hides the entire card.
    var isDone by remember(field) { mutableStateOf(false) }
    // True once collect() returned Result.success.
    var pairingSuccess by remember(field) { mutableStateOf(false) }
    // Non-null once collect() returned Result.failure.
    var pairingError: PairingError? by remember(field) { mutableStateOf(null) }
    // True while onNext() is in flight after the user taps Continue — disables the button.
    var isSubmitting by remember(field) { mutableStateOf(false) }

    LaunchedEffect(field) {
        pairingJob = launch {
            val result = field.collect()
            // CancellationException bypasses both branches — composable is being disposed or
            // the user already tapped Cancel, so no state update is needed.
            result.onSuccess { pairingSuccess = true }
            result.onFailure { e ->
                val mfaEx = e as? PingOneMFAException
                val native = mfaEx?.internalErrorsList?.firstOrNull()
                pairingError = PairingError(
                    code = native?.code?.toString(),
                    message = e.message ?: "Pairing failed",
                    details = native?.userInfo
                        ?.get("InternalError")
                        ?.let { jsonStr ->
                            runCatching {
                                org.json.JSONObject(jsonStr).optString("message", null)
                            }.getOrNull()
                        }
                        ?.takeIf { it.isNotBlank() },
                )
            }
        }
    }

    DisposableEffect(field) {
        onDispose { pairingJob?.cancel() }
    }

    if (!isDone) {
        when {
            pairingSuccess -> {
                // Pairing succeeded — same Box(fillMaxSize, Center) gravity as the other states.
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.text_pingone_mfa_davinci_pairing_succeeded),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            enabled = !isSubmitting,
                            onClick = {
                                isSubmitting = true
                                onNext()
                            },
                        ) {
                            Text(stringResource(R.string.text_pingone_mfa_davinci_pairing_continue))
                        }
                    }
                }
            }

            // Failure — hide the card when the user taps Continue, matching the cancel
            // behaviour: the submission continues in the background while the route pops.
            pairingError != null -> {
                // Pairing failed — same Box(fillMaxSize, Center) gravity as the other states.
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.text_pingone_mfa_davinci_pairing_failed),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = buildString {
                                append(pairingError!!.message)
                                pairingError!!.code?.let { append(" ($it)") }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        pairingError!!.details?.let { details ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = details,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            enabled = !isSubmitting,
                            onClick = {
                                isSubmitting = true
                                isDone = true
                                onNext()
                            },
                        ) {
                            Text(stringResource(R.string.text_pingone_mfa_davinci_pairing_continue))
                        }
                    }
                }
            }

            else -> {
                // Pairing in progress — use Box(fillMaxSize, Center) to inherit DaVinci's
                // visual gravity: content is centred in the remaining space below the logo,
                // matching where DaVinci's own CircularProgressIndicator floats during loading.
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.text_pingone_mfa_pairing),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = {
                                isDone = true
                                field.cancel()
                                pairingJob?.cancel()
                                onNext()
                            },
                        ) {
                            Text(stringResource(R.string.text_pingone_mfa_davinci_pairing_cancel))
                        }
                    }
                }
            }
        }
    }
}
