/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.home

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LockPerson
import androidx.compose.material.icons.filled.LogoDev
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Token
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pingidentity.device.DefaultTamperDetector
import com.pingidentity.device.analyze
import com.pingidentity.device.id.DefaultDeviceIdentifier
import com.pingidentity.samples.pingsampleapp.R
import com.pingidentity.samples.pingsampleapp.theme.AppTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeApp(
    onDaVinciFlowClick : () -> Unit,
    onJourneyFlowClick : () -> Unit,
    onOIDCLoginClick : () -> Unit,
    onAccessTokenClick : () -> Unit,
    onUserProfileClick : () -> Unit,
    onDeviceManagementClick : () -> Unit,
    onLogoutClick : () -> Unit,
    onDeviceInfoClick : () -> Unit,
    onLoggerClick : () -> Unit,
    onStorageClick : () -> Unit,
    onBindingKeysClick : () -> Unit,
    onConfigurationClick : () -> Unit,
    onQrScannerClick : () -> Unit,
    onOathClick : () -> Unit,
    onPushNotificationClick : () -> Unit,
    onDeviceIdClick : () -> Unit,
    onAuthTestScreenClick : () -> Unit,
) {
    var deviceId by remember { mutableStateOf("Loading Device ID...") }
    var deviceStatus by remember { mutableStateOf("Loading device status...") }
    var clickCount by remember { mutableIntStateOf(0) }
    val rotation = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        deviceId = try {
            DefaultDeviceIdentifier.id()
        } catch (_: Exception) {
            "Error loading Device ID"
        }
        deviceStatus = try {
            val result = analyze {
                detector(DefaultTamperDetector())
            }
            if (result == 0.0) {
                "✓ Secured"
            } else {
                "⚠\uFE0F JailBroken (Score: $result)"
            }
        } catch (exception : Exception) {
            Log.e("HomeApp", "Error loading device status", exception)
            "Error loading device status"
        }
    }

    AppTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Header row with red background, logo, and version
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colorResource(R.color.primary_dark))
                    .padding(vertical = 24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ping_logo),
                        contentDescription = "Ping Logo",
                        modifier = Modifier
                            .size(80.dp)
                            .graphicsLayer {
                                rotationZ = rotation.value
                            }
                            .clickable {
                                clickCount++
                                if (clickCount >= 3) {
                                    clickCount = 0
                                    coroutineScope.launch {
                                        // Rotate 3 times (360 degrees * 3 = 1080 degrees)
                                        rotation.animateTo(
                                            targetValue = rotation.value + 1080f,
                                            animationSpec = tween(durationMillis = 1500)
                                        )
                                        // Reset rotation to 0 to avoid overflow
                                        rotation.snapTo(0f)
                                    }
                                }
                            }
                    )
                    Text(
                        text = stringResource(R.string.app_name),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        text = stringResource(R.string.app_version),
                        color = Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // Existing content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // Authentication Section
                Text(
                    text = stringResource(R.string.text_home_section_authentication),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                IconRowItem(
                    icon = Icons.Default.Key,
                    title = stringResource(R.string.text_davinci_title),
                    subtitle = stringResource(R.string.text_davinci_subtitle),
                    onClick = onDaVinciFlowClick,
                )

                IconRowItem(
                    icon = Icons.Default.Map,
                    title = stringResource(R.string.text_journey_title),
                    subtitle = stringResource(R.string.text_journey_subtitle),
                    onClick = onJourneyFlowClick
                )

                IconRowItem(
                    icon = Icons.Default.LockPerson,
                    title = stringResource(R.string.text_oidc_title),
                    subtitle = stringResource(R.string.text_oidc_subtitle),
                    onClick = onOIDCLoginClick
                )

                // User Management Section
                Text(
                    text = stringResource(R.string.text_home_section_user_management),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(vertical = 16.dp)
                )

                IconRowItem(
                    icon = Icons.Default.Token,
                    title = stringResource(R.string.text_access_token_title),
                    subtitle = stringResource(R.string.text_access_token_subtitle),
                    onClick = onAccessTokenClick
                )
                IconRowItem(
                    icon = Icons.Default.AccountCircle,
                    title = stringResource(R.string.text_user_profile_title),
                    subtitle = stringResource(R.string.text_user_profile_subtitle),
                    onClick = onUserProfileClick
                )
                IconRowItem(
                    icon = Icons.Default.DeviceHub,
                    title = stringResource(R.string.text_device_management_title),
                    subtitle = stringResource(R.string.text_device_management_subtitle),
                    onClick = onDeviceManagementClick
                )
                IconRowItem(
                    icon = Icons.AutoMirrored.Filled.Logout,
                    title = stringResource(R.string.text_logout_title),
                    subtitle = stringResource(R.string.text_logout_subtitle),
                    onClick = onLogoutClick
                )

                // MFA Section
                Text(
                    text = stringResource(R.string.text_home_section_mfa),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(vertical = 16.dp)
                )

                IconRowItem(
                    icon = Icons.Default.QrCodeScanner,
                    title = stringResource(R.string.text_qr_scanner_title),
                    subtitle = stringResource(R.string.text_qr_scanner_subtitle),
                    onClick = onQrScannerClick
                )

                IconRowItem(
                    icon = Icons.Default.VpnKey,
                    title = stringResource(R.string.text_oath_title),
                    subtitle = stringResource(R.string.text_oath_subtitle),
                    onClick = onOathClick
                )

                IconRowItem(
                    icon = Icons.Default.Notifications,
                    title = stringResource(R.string.text_push_notification_title),
                    subtitle = stringResource(R.string.text_push_notification_subtitle),
                    onClick = onPushNotificationClick
                )
                IconRowItem(
                    icon = Icons.Default.Preview,
                    title = "MFA Test Mode",
                    subtitle = "Authenticator app test mode",
                    onClick = onAuthTestScreenClick
                )

                // Developer Tools Section
                Text(
                    text = stringResource(R.string.text_home_section_developer_tools),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(vertical = 16.dp)
                )

                IconRowItem(
                    icon = Icons.Default.PhoneAndroid,
                    title = stringResource(R.string.text_device_info_title),
                    subtitle = stringResource(R.string.text_device_info_subtitle),
                    onClick = onDeviceInfoClick
                )
                IconRowItem(
                    icon = Icons.Default.LogoDev,
                    title = stringResource(R.string.text_logger_title),
                    subtitle = stringResource(R.string.text_logger_subtitle),
                    onClick = onLoggerClick
                )
                IconRowItem(
                    icon = Icons.Default.Storage,
                    title = stringResource(R.string.text_storage_title),
                    subtitle = stringResource(R.string.text_storage_subtitle),
                    onClick = onStorageClick
                )
                IconRowItem(
                    icon = Icons.Default.VpnKey,
                    title = stringResource(R.string.text_binding_keys_title),
                    subtitle = stringResource(R.string.text_binding_keys_subtitle),
                    onClick = onBindingKeysClick
                )
                IconRowItem(
                    icon = Icons.Default.Settings,
                    title = stringResource(R.string.text_configuration_title),
                    subtitle = stringResource(R.string.text_configuration_subtitle),
                    onClick = onConfigurationClick
                )

                Spacer(modifier = Modifier
                    .padding(8.dp)
                    .fillMaxWidth())

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhoneAndroid,
                            contentDescription = stringResource(R.string.text_device_info_title),
                            modifier = Modifier
                        )
                        Spacer(modifier = Modifier.width(8.dp)) // Add space between icon and text
                        Text(
                            text = stringResource(R.string.text_device_info_title),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(8.dp),
                        )
                        Text(
                            text = deviceStatus,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(8.dp),
                            color = if (deviceStatus == "✓ Secured") Color.Green else Color.Red,
                            textAlign = TextAlign.End,
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        thickness = 1.dp,
                        color = colorResource(R.color.primary_dark)
                    )
                    TextButton(
                        onClick = onDeviceIdClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    ) {
                        Text(text = deviceId)
                    }
                }

                Spacer(modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth())
            }

        }
    }
}

@Composable
fun IconRowItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    showNavigation: Boolean = true,
    onClick: () -> Unit,
) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier.size(40.dp),
                tint = colorResource(R.color.primary_dark)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (showNavigation) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Navigate",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun PreviewHomeApp() {
    HomeApp(
        onDaVinciFlowClick = {},
        onJourneyFlowClick = {},
        onOIDCLoginClick = {},
        onAccessTokenClick = {},
        onUserProfileClick = {},
        onDeviceManagementClick = {},
        onLogoutClick = {},
        onDeviceInfoClick = {},
        onLoggerClick = {},
        onStorageClick = {},
        onBindingKeysClick = {},
        onConfigurationClick = {},
        onQrScannerClick = {},
        onOathClick = {},
        onPushNotificationClick = {},
        onDeviceIdClick = {},
        onAuthTestScreenClick = {}
    )
}

