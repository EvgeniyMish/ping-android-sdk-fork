/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.pingonemfa.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pingidentity.samples.pingsampleapp.R
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pingidentity.samples.pingsampleapp.authenticator.ui.components.BackNavigationTopAppBar
import com.pingidentity.samples.pingsampleapp.authenticator.ui.components.LoadingIndicator
import com.pingidentity.samples.pingsampleapp.pingonemfa.util.PingOneMFAQrCodeAnalyzer
import com.pingidentity.samples.pingsampleapp.pingonemfa.util.matchesPingOnePairingKeyScheme
import com.pingidentity.samples.pingsampleapp.pingonemfa.PingOneMFAViewModel
import java.util.concurrent.Executors

/**
 * QR scanner screen for PingOne MFA device pairing.
 *
 * Shows a camera preview. Scanned or manually entered pairing keys are forwarded to
 * [PingOneMFAViewModel.pair]. Errors are shown via a Snackbar. On successful pairing an
 * AlertDialog confirms the result; tapping OK calls [onPairComplete] to close the screen.
 *
 * @param onBack Navigation callback for the top-app-bar back button.
 * @param onPairComplete Callback invoked after a successful pairing operation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PingOneQrScannerScreen(
    onBack: () -> Unit,
    onPairComplete: () -> Unit,
    viewModel: PingOneMFAViewModel = viewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var manualKey by remember { mutableStateOf("") }
    var scanEnabled by remember { mutableStateOf(true) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { hasCameraPermission = it }
    )

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val qrAnalyzer = remember { PingOneMFAQrCodeAnalyzer { pairingKey ->
        if (!scanEnabled) return@PingOneMFAQrCodeAnalyzer
        scanEnabled = false
        viewModel.pair(pairingKey)
    } }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) requestPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val state by viewModel.state.collectAsState()

    // Show error dialog; re-enable scanning when the user dismisses so they can retry.
    state.error?.let { errorMessage ->
        AlertDialog(
            onDismissRequest = {
                scanEnabled = true
                viewModel.clearError()
            },
            title = { Text(stringResource(R.string.error_title)) },
            text = { Text(errorMessage) },
            confirmButton = {
                Button(onClick = {
                    scanEnabled = true
                    viewModel.clearError()
                }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }

    // Show success dialog on successful pairing; navigate away when user taps OK.
    state.message?.let { message ->
        AlertDialog(
            onDismissRequest = {
                viewModel.clearMessage()
                onPairComplete()
            },
            text = { Text(message) },
            confirmButton = {
                Button(onClick = {
                    viewModel.clearMessage()
                    onPairComplete()
                }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            BackNavigationTopAppBar(
                title = stringResource(R.string.text_pingone_mfa_screen_scanner_title),
                onBackClick = onBack,
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .navigationBarsPadding(),
        ) {
            // Camera area — fills all space above the manual-entry panel
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF616161)),
                contentAlignment = Alignment.Center,
            ) {
                if (hasCameraPermission && !state.isLoading) {
                    // Full-size camera preview behind the overlay
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            val previewView = PreviewView(ctx).apply {
                                implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                            }

                            val preview = Preview.Builder().build().also {
                                it.surfaceProvider = previewView.surfaceProvider
                            }

                            val selector = CameraSelector.Builder()
                                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                                .build()

                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()

                            imageAnalysis.setAnalyzer(cameraExecutor, qrAnalyzer)

                            ProcessCameraProvider.getInstance(ctx).also { future ->
                                future.addListener({
                                    try {
                                        val cameraProvider = future.get()
                                        cameraProvider.unbindAll()
                                        cameraProvider.bindToLifecycle(
                                            lifecycleOwner, selector, preview, imageAnalysis
                                        )
                                    } catch (e: Exception) {
                                        // Surface the failure through the error AlertDialog path
                                        viewModel.reportError("Camera unavailable: ${e.message}")
                                    }
                                }, ContextCompat.getMainExecutor(ctx))
                            }

                            previewView
                        },
                    )
                }

                if (state.isLoading) {
                    // Pairing in flight — show spinner over the solid grey background
                    LoadingIndicator(
                        message = stringResource(R.string.text_pingone_mfa_pairing),
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    // Overlay: label + corner-bracket scanning window
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 40.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.text_pingone_mfa_scan_qr),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        // Transparent scanning window — only corner brackets are drawn
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f),
                        ) {
                            val stroke = 6.dp.toPx()
                            val arm = 32.dp.toPx()
                            val radius = 16.dp.toPx()
                            val w = size.width
                            val h = size.height

                            val corners = listOf(
                                Offset(0f, 0f) to (1f to 1f),    // top-left
                                Offset(w, 0f) to (-1f to 1f),    // top-right
                                Offset(w, h) to (-1f to -1f),    // bottom-right
                                Offset(0f, h) to (1f to -1f),    // bottom-left
                            )

                            corners.forEach { (pivot, signs) ->
                                val (sx, sy) = signs
                                drawLine(
                                    color = Color.White,
                                    start = Offset(pivot.x + sx * radius, pivot.y),
                                    end = Offset(pivot.x + sx * (radius + arm), pivot.y),
                                    strokeWidth = stroke,
                                    cap = StrokeCap.Round,
                                )
                                drawLine(
                                    color = Color.White,
                                    start = Offset(pivot.x, pivot.y + sy * radius),
                                    end = Offset(pivot.x, pivot.y + sy * (radius + arm)),
                                    strokeWidth = stroke,
                                    cap = StrokeCap.Round,
                                )
                                drawArc(
                                    color = Color.White,
                                    startAngle = when {
                                        sx > 0 && sy > 0 -> 180f
                                        sx < 0 && sy > 0 -> 270f
                                        sx < 0 && sy < 0 -> 0f
                                        else -> 90f
                                    },
                                    sweepAngle = 90f,
                                    useCenter = false,
                                    topLeft = Offset(pivot.x + sx * radius - radius, pivot.y + sy * radius - radius),
                                    size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                                        width = stroke,
                                        cap = StrokeCap.Round,
                                    ),
                                )
                            }
                        }
                    }
                }

                // Permission-denied fallback shown on top of the grey background
                if (!hasCameraPermission) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                    ) {
                        Text(
                            text = "Camera permission is required to scan QR codes",
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { requestPermissionLauncher.launch(Manifest.permission.CAMERA) }) {
                            Text(stringResource(R.string.text_pingone_mfa_grant_permission))
                        }
                    }
                }
            }

            // Manual entry panel pinned at the bottom
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 4.dp,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    OutlinedTextField(
                        value = manualKey,
                        onValueChange = { manualKey = it },
                        placeholder = { Text(stringResource(R.string.text_pingone_mfa_enter_pairing_key_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                        ),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            keyboardController?.hide()
                            viewModel.pair(manualKey.trim())
                            manualKey = ""
                        },
                        enabled = manualKey.trim().matchesPingOnePairingKeyScheme() && !state.isLoading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.text_pingone_mfa_pair_button))
                    }
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            // Shut down the camera thread executor and release the ML Kit barcode client
            // together, so neither outlives the other once the screen leaves composition.
            qrAnalyzer.close()
            cameraExecutor.shutdown()
        }
    }
}
