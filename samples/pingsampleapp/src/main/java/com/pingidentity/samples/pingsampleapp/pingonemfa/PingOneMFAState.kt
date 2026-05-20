/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.pingonemfa

import com.pingidentity.pingonemfa.commons.PingOneMfaAccount
import com.pingidentity.pingonemfa.otp.OtpCodeInfo

/**
 * UI state for all PingOne MFA screens: pairing, accounts, OTP, and mobile payload.
 */
data class PingOneMFAState(
    /** True while a pairing operation is in flight. */
    val isLoading: Boolean = false,
    /** True while [PingOneMFAViewModel.loadAccounts] is in flight. */
    val isLoadingAccounts: Boolean = false,
    /** Paired PingOne MFA accounts. Populated by [PingOneMFAViewModel.loadAccounts]. */
    val accounts: List<PingOneMfaAccount> = emptyList(),
    /** True while [PingOneMFAViewModel.collectOtp] is in flight. */
    val isLoadingOtp: Boolean = false,
    /** The most recently fetched OTP code info, or null if not yet loaded. */
    val otp: OtpCodeInfo? = null,
    /** Live countdown in seconds for the current OTP, driven by [PingOneMFAViewModel]. */
    val otpSecondsRemaining: Int = 0,
    /** True when [PingOneMFAViewModel.collectOtp] fails because the device is not paired. */
    val isOtpDeviceNotPaired: Boolean = false,
    /** True while [PingOneMFAViewModel.collectPayload] is in flight. */
    val isLoadingPayload: Boolean = false,
    /** The most recently fetched mobile payload string, or null if not yet loaded. */
    val payload: String? = null,
    /** Non-null when an operation has completed successfully. Cleared by [PingOneMFAViewModel.clearMessage]. */
    val message: String? = null,
    /** Non-null when an operation has failed. Cleared by [PingOneMFAViewModel.clearError]. */
    val error: String? = null,
)
