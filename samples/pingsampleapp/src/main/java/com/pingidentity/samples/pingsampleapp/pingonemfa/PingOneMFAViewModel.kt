/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.pingonemfa

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pingidentity.pingonemfa.commons.PingOneMFA
import com.pingidentity.samples.pingsampleapp.authenticator.data.DiagnosticLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * ViewModel for the PingOne MFA screens: pairing, accounts, OTP, and mobile payload.
 *
 * SDK initialization is handled once at application startup in
 * [com.pingidentity.samples.pingsampleapp.PingSampleApplication]; this ViewModel only makes
 * user-triggered calls to the already-initialized [com.pingidentity.pingonemfa.commons.PingOneMFA]
 * singleton and maps results to [PingOneMFAState] for the UI.
 */
class PingOneMFAViewModel : ViewModel() {

    private val diagnosticLogger = DiagnosticLogger
    private val _state = MutableStateFlow(PingOneMFAState())
    val state: StateFlow<PingOneMFAState> = _state.asStateFlow()

    /**
     * Fetches the list of paired PingOne MFA accounts and updates [PingOneMFAState.accounts].
     * Shows a loading indicator while in flight via [PingOneMFAState.isLoadingAccounts].
     */
    fun loadAccounts() {
        _state.update { it.copy(isLoadingAccounts = true, error = null) }
        viewModelScope.launch {
            PingOneMFA.getDeviceInfo()
                .onSuccess { accounts ->
                    diagnosticLogger.i("Successfully loaded PingOne MFA accounts: ${accounts.size}")
                    _state.update { it.copy(isLoadingAccounts = false, accounts = accounts) }
                }
                .onFailure { e ->
                    diagnosticLogger.e("Failed to load accounts", e)
                    _state.update { it.copy(isLoadingAccounts = false, error = e.message ?: "Failed to load accounts") }
                }
        }
    }

    /**
     * Pairs the device using [pairingKey] obtained from a QR code scan or manual entry.
     */
    fun pair(pairingKey: String) {
        _state.update { it.copy(isLoading = true, error = null, message = null) }
        viewModelScope.launch {
            PingOneMFA.pair(pairingKey)
                .onSuccess {
                    _state.update {
                        it.copy(isLoading = false, message = "Device paired successfully")
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = e.message ?: "Pairing failed",
                        )
                    }
                }
        }
    }

    private var otpCountdownJob: Job? = null

    /**
     * Fetches the current one-time passcode from PingOne, updates [PingOneMFAState.otp],
     * and starts a countdown that decrements [PingOneMFAState.otpSecondsRemaining] every
     * second, automatically re-fetching when it reaches zero.
     */
    fun collectOtp() {
        otpCountdownJob?.cancel()
        _state.update { it.copy(isLoadingOtp = true, otp = null, otpSecondsRemaining = 0, error = null, isOtpDeviceNotPaired = false) }
        viewModelScope.launch {
            PingOneMFA.getOneTimePasscode()
                .onSuccess { otpInfo ->
                    diagnosticLogger.i("Successfully collected OTP")
                    _state.update { it.copy(isLoadingOtp = false, otp = otpInfo, otpSecondsRemaining = otpInfo.secondsRemaining) }
                    if (!state.value.isOtpDeviceNotPaired) startOtpCountdown()
                }
                .onFailure { e ->
                    diagnosticLogger.e("Failed to collect OTP", e)
                    if (e.message?.contains("Code=10008", ignoreCase = true) == true) {
                        _state.update { it.copy(isLoadingOtp = false, isOtpDeviceNotPaired = true) }
                    } else {
                        _state.update { it.copy(isLoadingOtp = false, error = e.message ?: "Failed to collect OTP") }
                    }
                }
        }
    }

    private fun startOtpCountdown() {
        otpCountdownJob?.cancel()
        otpCountdownJob = viewModelScope.launch {
            while (_state.value.otpSecondsRemaining > 0) {
                delay(1_000)
                _state.update { it.copy(otpSecondsRemaining = it.otpSecondsRemaining - 1) }
            }
            collectOtp()
        }
    }

    /**
     * Fetches the mobile payload from PingOne and updates [PingOneMFAState.payload].
     * Shows a loading indicator while in flight via [PingOneMFAState.isLoadingPayload].
     */
    fun collectPayload() {
        _state.update { it.copy(isLoadingPayload = true, error = null) }
        viewModelScope.launch {
            PingOneMFA.generateMobilePayload()
                .onSuccess { payload ->
                    diagnosticLogger.i("Successfully collected mobile payload")
                    _state.update { it.copy(isLoadingPayload = false, payload = payload) }
                }
                .onFailure { e ->
                    diagnosticLogger.e("Failed to collect mobile payload", e)
                    _state.update { it.copy(isLoadingPayload = false, error = e.message ?: "Failed to collect mobile payload") }
                }
        }
    }

    /**
     * Posts an error message directly into state without triggering any SDK call.
     *
     * Used by the UI layer to surface infrastructure failures (e.g. camera bind errors)
     * through the same Snackbar path as SDK errors, rather than routing them through a
     * no-op SDK call just to produce an error response.
     */
    fun reportError(message: String) {
        _state.update { it.copy(error = message) }
    }

    /** Clears any transient error from the state. */
    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    /** Clears any transient success message from the state. */
    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }
}
