/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.davinci

import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pingidentity.oidc.module.VERIFICATION_URI_COMPLETE
import com.pingidentity.orchestrate.ContinueNode
import com.pingidentity.samples.pingsampleapp.config.daVinci
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DaVinciViewModel(
    private val verificationUri: String? = null,
): ViewModel() {
    var state = MutableStateFlow(DaVinciState())
        private set

    var loading = MutableStateFlow(false)
        private set

    init {
        start()
    }

    fun next(current: ContinueNode) {
        loading.update {
            true
        }
        viewModelScope.launch {
            try {
                val next = current.next()
                state.update {
                    it.copy(node = next, counter = it.counter + 1)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state.update {
                    it.copy(node = null, error = e.message ?: "An unexpected error occurred")
                }
            } finally {
                loading.update {
                    false
                }
            }
        }
    }

    fun start() {
        if (daVinci == null) {
            state.update {
                it.copy(node = null, error = "DaVinci not configured")
            }
            return
        }
        loading.update {
            true
        }
        viewModelScope.launch {
            try {
                val next = if (!verificationUri.isNullOrBlank()) {
                    daVinci?.start {
                        VERIFICATION_URI_COMPLETE to verificationUri.toUri()
                    }
                } else { daVinci?.start() }

                state.update {
                    it.copy(node = next, counter = it.counter + 1)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state.update {
                    it.copy(node = null, error = e.message ?: "An unexpected error occurred")
                }
            } finally {
                loading.update {
                    false
                }
            }
        }
    }

    fun refresh() {
        state.update {
            it.copy(node = it.node, counter = it.counter + 1)
        }
    }

    companion object {
        fun factory(verificationUri: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    DaVinciViewModel(verificationUri) as T
            }
    }
}