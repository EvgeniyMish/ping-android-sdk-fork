/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.samples.pingsampleapp.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pingidentity.orchestrate.SuccessNode
import com.pingidentity.samples.pingsampleapp.PingSampleApplication
import com.pingidentity.samples.pingsampleapp.authenticator.data.AuthenticatorViewModel
import com.pingidentity.samples.pingsampleapp.authenticator.ui.AboutScreen
import com.pingidentity.samples.pingsampleapp.authmigration.AuthMigrationScreen
import com.pingidentity.samples.pingsampleapp.authmigration.AuthMigrationViewModel
import com.pingidentity.samples.pingsampleapp.authmigration.MigrationStatus
import com.pingidentity.samples.pingsampleapp.authenticator.ui.AccountDetailScreen
import com.pingidentity.samples.pingsampleapp.authenticator.ui.AccountsScreen
import com.pingidentity.samples.pingsampleapp.authenticator.ui.EditAccountsScreen
import com.pingidentity.samples.pingsampleapp.authenticator.ui.ManualEntryScreen
import com.pingidentity.samples.pingsampleapp.authenticator.ui.NotificationResponseScreen
import com.pingidentity.samples.pingsampleapp.authenticator.ui.PushNotificationsScreen
import com.pingidentity.samples.pingsampleapp.authenticator.ui.QrScannerScreen
import com.pingidentity.samples.pingsampleapp.authenticator.ui.SettingsScreen
import com.pingidentity.samples.pingsampleapp.authenticator.ui.TestScreen
import com.pingidentity.samples.pingsampleapp.authenticator.util.NavigationAnimations
import com.pingidentity.samples.pingsampleapp.authgrant.DeviceAuthorizationGrantScreen
import com.pingidentity.samples.pingsampleapp.config.Env
import com.pingidentity.samples.pingsampleapp.davinci.DaVinci
import com.pingidentity.samples.pingsampleapp.davinci.DaVinciViewModel
import com.pingidentity.samples.pingsampleapp.devicemanagement.DeviceManagement
import com.pingidentity.samples.pingsampleapp.devicemanagement.DeviceManagementViewModel
import com.pingidentity.samples.pingsampleapp.devtools.DeviceInfo
import com.pingidentity.samples.pingsampleapp.home.HomeApp
import com.pingidentity.samples.pingsampleapp.pingonemfa.ui.PingOneMFAAccountsScreen
import com.pingidentity.samples.pingsampleapp.pingonemfa.ui.PingOneOTPScreen
import com.pingidentity.samples.pingsampleapp.pingonemfa.ui.PingOnePayloadScreen
import com.pingidentity.samples.pingsampleapp.pingonemfa.ui.PingOneQrScannerScreen
import com.pingidentity.samples.pingsampleapp.journey.BackchannelAuthScreen
import com.pingidentity.samples.pingsampleapp.journey.JourneyScreen
import com.pingidentity.samples.pingsampleapp.journey.JourneyRoute
import com.pingidentity.samples.pingsampleapp.journey.JourneyViewModel
import com.pingidentity.samples.pingsampleapp.journey.PreferenceViewModel
import com.pingidentity.samples.pingsampleapp.keystore.KeyStoreScreen
import com.pingidentity.samples.pingsampleapp.logout.Logout
import com.pingidentity.samples.pingsampleapp.oidc.Centralize
import com.pingidentity.samples.pingsampleapp.token.TokenScreen
import com.pingidentity.samples.pingsampleapp.token.TokenViewModel
import com.pingidentity.samples.pingsampleapp.userprofile.UserProfile
import com.pingidentity.samples.pingsampleapp.userprofile.UserProfileType
import com.pingidentity.samples.pingsampleapp.userprofile.UserProfileViewModel

/**
 * Sealed class representing all possible navigation destinations in the app
 */
object Route {
    const val HOME = "home"
    const val DAVINCI = "davinci"
    const val JOURNEY_ROUTE = "journey_route"
    const val JOURNEY = "journey"
    const val OIDC = "oidc"
    const val ACCESS_TOKEN = "access_token"
    internal const val USER_PROFILE_ROUTE = "user_profile?type={type}"
    fun userProfile(type: UserProfileType? = null) =
        if (type != null) "user_profile?type=${type.name}" else "user_profile?type=${UserProfileType.JOURNEY.name}"
    const val DEVICE_MANAGEMENT = "device_management"
    const val LOGOUT = "logout"
    const val DEVICE_INFO = "device_info"
    const val LOGGER = "logger"
    const val STORAGE = "storage"
    const val BINDING_KEYS = "binding_keys"
    const val CONFIGURATION = "configuration"
    const val QR_SCANNER = "qr_scanner"
    const val OATH = "oath"
    const val PUSH_NOTIFICATION = "push_notification"
    const val ROUTE_AUTH_APP_MANUAL_ENTRY = "route_auth_app_manual_entry"
    const val ROUTE_AUTH_APP_EDIT_ACCOUNTS = "route_auth_app_edit_accounts"
    const val ROUTE_AUTH_APP_SETTINGS = "route_auth_app_settings"
    const val ROUTE_AUTH_APP_ABOUT = "route_auth_app_about"
    const val ROUTE_AUTH_APP_ACCOUNT = "account/{issuer}/{accountName}"
    fun routeForAuthAppAccount(accountName: String) = "account/$accountName"
    const val ROUTE_AUTH_TEST_APP = "route_auth_test_app"
    const val AUTH_MIGRATION = "auth_migration"
    const val ROUTE_PINGONE_ACCOUNTS = "pingone_accounts"
    const val ROUTE_PINGONE_OTP = "pingone_otp"
    const val ROUTE_PINGONE_PAYLOAD = "pingone_payload"
    const val ROUTE_PINGONE_QR_SCANNER = "pingone_qr_scanner"
    const val ROUTE_PINGONE_DAVINCI_PAIRING = "pingone_davinci_pairing"
    const val DEVICE_AUTHORIZATION_GRANT = "device_authorization_grant"
    const val BACKCHANNEL_AUTH = "backchannel_auth"
    internal const val BACKCHANNEL_JOURNEY = "backchannel_journey?uri={uri}"
    fun backchannelJourney(uri: String) = "backchannel_journey?uri=${android.net.Uri.encode(uri)}"
    const val DAVINCI_DEVICE_APPROVE = "davinci_device_approve?uri={uri}"
    const val JOURNEY_DEVICE_APPROVAL = "journey_device_approval?uri={uri}"
    internal const val JOURNEY_WITH_VERIFICATION = "$JOURNEY/{name}?verificationUri={verificationUri}"
    fun daVinciDeviceApprove(uri: String) = "davinci_device_approve?uri=${android.net.Uri.encode(uri)}"
    fun journeyDeviceApproval(uri: String) = "journey_device_approval?uri=${android.net.Uri.encode(uri)}"
    fun journeyWithVerification(name: String, uri: String) =
        "$JOURNEY/$name?verificationUri=${android.net.Uri.encode(uri)}"
}

/**
 * Main navigation host for the app
 */
@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Route.HOME
) {
    var authenticatorViewModel by remember { mutableStateOf<AuthenticatorViewModel?>(null) }

    LaunchedEffect(Unit) {
        authenticatorViewModel = PingSampleApplication.getAuthenticatorViewModel()
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Route.HOME) {
            HomeApp(
                onDaVinciFlowClick = {
                    navController.navigate(Route.DAVINCI)
                },
                onJourneyFlowClick = {
                    navController.navigate(Route.JOURNEY_ROUTE)
                },
                onOIDCLoginClick = {
                    navController.navigate(Route.OIDC)
                },
                onAccessTokenClick = {
                    navController.navigate(Route.ACCESS_TOKEN)
                },
                onUserProfileClick = {
                    navController.navigate(Route.userProfile())
                },
                onDeviceManagementClick = {
                    navController.navigate(Route.DEVICE_MANAGEMENT)
                },
                onLogoutClick = {
                    navController.navigate(Route.LOGOUT)
                },
                onDeviceInfoClick = {
                    navController.navigate(Route.DEVICE_INFO)
                },
                onLoggerClick = {
                    navController.navigate(Route.LOGGER)
                },
                onStorageClick = {
                    navController.navigate(Route.STORAGE)
                },
                onBindingKeysClick = {
                    navController.navigate(Route.BINDING_KEYS)
                },
                onConfigurationClick = {
                    navController.navigate(Route.CONFIGURATION)
                },
                onQrScannerClick = {
                    navController.navigate(Route.QR_SCANNER)
                },
                onOathClick = {
                    navController.navigate(Route.OATH)
                },
                onPushNotificationClick = {
                    navController.navigate(Route.PUSH_NOTIFICATION)
                },
                onDeviceIdClick = {
                    navController.navigate(Route.DEVICE_INFO)
                },
                onAuthTestScreenClick = {
                    navController.navigate(Route.ROUTE_AUTH_TEST_APP)
                },
                onAuthMigrationClick = {
                    navController.navigate(Route.AUTH_MIGRATION)
                },
                onPingOneAccountsClick = {
                    navController.navigate(Route.ROUTE_PINGONE_ACCOUNTS)
                },
                onPingOneOTPClick = {
                    navController.navigate(Route.ROUTE_PINGONE_OTP)
                },
                onPingOnePayloadClick = {
                    navController.navigate(Route.ROUTE_PINGONE_PAYLOAD)
                },
                onPingOneQrScannerClick = {
                    navController.navigate(Route.ROUTE_PINGONE_QR_SCANNER)
                },
                onPingOneDaVinciPairingClick = {
                    navController.navigate(Route.ROUTE_PINGONE_DAVINCI_PAIRING)
                },
                onDeviceAuthorizationGrantClick = {
                    navController.navigate(Route.DEVICE_AUTHORIZATION_GRANT)
                },
                onBackchannelAuthClick = {
                    navController.navigate(Route.BACKCHANNEL_AUTH)
                },
            )
        }
        
        composable(Route.DAVINCI) {
            DaVinci(
                onSuccess = {
                    navController.navigate(Route.userProfile(UserProfileType.DAVINCI)) {
                        popUpTo(Route.HOME) {
                            inclusive = false
                        }
                    }
                },
                onLogoClick = {
                    navController.navigateUp()
                },
                onBack = {
                    navController.navigateUp()
                }
            )
        }

        composable(Route.JOURNEY_ROUTE) {
            val preferenceViewModel = viewModel<PreferenceViewModel>(
                factory = PreferenceViewModel.factory(LocalContext.current)
            )
            JourneyRoute(
                preferenceViewModel = preferenceViewModel,
                onSubmit = { journeyName ->
                    navController.navigate(Route.JOURNEY + "/$journeyName")
                },
                onBack = {
                    navController.navigateUp()
                }
            )
        }
        
        composable(
            route = Route.JOURNEY_WITH_VERIFICATION,
            arguments = listOf(
                navArgument("name") { type = NavType.StringType },
                navArgument("verificationUri") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            )
        ) { backStackEntry ->
            val name = backStackEntry.arguments?.getString("name") ?: return@composable
            val verificationUri = backStackEntry.arguments?.getString("verificationUri")
            val journeyViewModel = viewModel<JourneyViewModel>(
                factory = if (!verificationUri.isNullOrBlank())
                    JourneyViewModel.factory(name, verificationUri)
                else
                    JourneyViewModel.factory(name)
            )
            JourneyScreen(
                journeyViewModel = journeyViewModel,
                onSuccess = {
                    navController.navigate(Route.userProfile(UserProfileType.JOURNEY)) {
                        popUpTo(Route.HOME) { inclusive = false }
                    }
                },
                onBack = { navController.navigateUp() },
            )
        }

        
        composable(Route.OIDC) {
            Centralize(
                onSuccess = {
                    navController.navigate(Route.userProfile(UserProfileType.OIDC))
                },
                onBack = {
                    navController.navigateUp()
                }
            )
        }
        
        composable(Route.ACCESS_TOKEN) {
            val tokenViewModel = viewModel<TokenViewModel>(
                factory = TokenViewModel.factory()
            )
            TokenScreen(tokenViewModel) {
                navController.navigateUp()
            }
        }
        
        composable(
            route = Route.USER_PROFILE_ROUTE,
            arguments = listOf(navArgument("type") {
                type = NavType.StringType
                defaultValue = UserProfileType.JOURNEY.name
            })
        ) { backStackEntry ->
            val initialTab = backStackEntry.arguments?.getString("type")
                ?.let { runCatching { UserProfileType.valueOf(it) }.getOrNull() }
                ?: UserProfileType.JOURNEY
            val userProfileViewModel = viewModel<UserProfileViewModel>(
                factory = UserProfileViewModel.factory()
            )
            UserProfile(
                userProfileViewModel = userProfileViewModel,
                onSelectedUserProfileType = initialTab,
                onBack = {
                    // Navigate to home and clear the entire back stack
                    navController.navigate(Route.HOME) {
                        popUpTo(navController.graph.startDestinationId) {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                },
                onAction = { userProfileType ->
                    when (userProfileType) {
                        UserProfileType.JOURNEY -> {
                            navController.navigate(Route.JOURNEY_ROUTE)
                        }
                        UserProfileType.DAVINCI -> {
                            navController.navigate(Route.DAVINCI)
                        }
                        UserProfileType.OIDC -> {
                            navController.navigate(Route.OIDC)
                        }
                        UserProfileType.AUTH_GRANT -> {
                            navController.navigate(Route.DEVICE_AUTHORIZATION_GRANT)
                        }
                    }
                }
            )
        }
        
        composable(Route.DEVICE_MANAGEMENT) {
            val deviceManagementViewModel = viewModel<DeviceManagementViewModel>(
                factory = DeviceManagementViewModel.factory()
            )
            DeviceManagement(
                viewModel = deviceManagementViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(Route.LOGOUT) {
            Logout(
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(Route.DEVICE_INFO) {
            DeviceInfo(
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(Route.LOGGER) {
            PlaceholderScreen(title = "Logger", navController = navController)
        }
        
        composable(Route.STORAGE) {
            PlaceholderScreen(title = "Storage", navController = navController)
        }
        
        composable(Route.BINDING_KEYS) {
            KeyStoreScreen(
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(Route.CONFIGURATION) {
            Env(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Route.QR_SCANNER) {
            authenticatorViewModel?.let { viewModel ->
                QrScannerScreen(
                    viewModel = viewModel,
                    onScanComplete = {
                        navController.navigate(Route.OATH) {
                            popUpTo(Route.HOME) {
                                inclusive = false
                            }
                        }
                    },
                    onDismiss = {
                        navController.popBackStack()
                    }
                )
            }
        }

        composable(Route.OATH) {
            authenticatorViewModel?.let { viewModel ->
                AccountsScreen(
                    viewModel = viewModel,
                    onScanQrCode = {
                        navController.navigate(Route.QR_SCANNER)
                    },
                    onAddManually = {
                        navController.navigate(Route.ROUTE_AUTH_APP_MANUAL_ENTRY)
                    },
                    onAccountClick = { accountId ->
                        navController.navigate(Route.routeForAuthAppAccount(accountId))
                    },
                    onNotificationsClick = {
                        navController.navigate(Route.PUSH_NOTIFICATION)
                    },
                    onSettingsClick = {
                        navController.navigate(Route.ROUTE_AUTH_APP_SETTINGS)
                    },
                    onAboutClick = {
                        navController.navigate(Route.ROUTE_AUTH_APP_ABOUT)
                    },
                    onEditAccountsClick = {
                        navController.navigate(Route.ROUTE_AUTH_APP_EDIT_ACCOUNTS)
                    },
                    onTestModeClick = {
                        navController.navigate(Route.ROUTE_AUTH_TEST_APP)
                    },
                    onNavigateToLogin = {
                        navController.navigate(Route.JOURNEY_ROUTE)
                    }
                )
            }
        }

        composable(Route.ROUTE_AUTH_APP_MANUAL_ENTRY) {
            authenticatorViewModel?.let {
                ManualEntryScreen(
                    viewModel = it,
                    onDismiss = {
                        navController.popBackStack()
                    },
                    onEntryComplete = {
                        navController.popBackStack()
                    }
                )
            }
        }

        composable(Route.ROUTE_AUTH_APP_SETTINGS) {
            authenticatorViewModel?.let {
                SettingsScreen(
                    viewModel = it,
                    onDismiss = {
                        navController.popBackStack()
                    },
                    onDiagnosticLogsClick = {
                        navController.popBackStack()
                    }
                )
            }
        }

        composable(Route.ROUTE_AUTH_APP_ABOUT) {
            AboutScreen(
                onDismiss = {
                    navController.popBackStack()
                }
            )
        }

        composable(Route.ROUTE_AUTH_APP_EDIT_ACCOUNTS) {
            authenticatorViewModel?.let {
                EditAccountsScreen(
                    viewModel = it,
                    onDismiss = { navController.popBackStack() }
                )
            }
        }

        composable(Route.ROUTE_AUTH_APP_ACCOUNT) { backStackEntry ->
            val encodedIssuer = backStackEntry.arguments?.getString("issuer") ?: ""
            val encodedAccountName = backStackEntry.arguments?.getString("accountName") ?: ""
            val issuer = java.net.URLDecoder.decode(encodedIssuer, "UTF-8")
            val accountName = java.net.URLDecoder.decode(encodedAccountName, "UTF-8")
            authenticatorViewModel?.let {
                AccountDetailScreen(
                    issuer = issuer,
                    accountName = accountName,
                    viewModel = it,
                    onDismiss = {
                        navController.popBackStack()
                    }
                )
            }
        }

        composable(Route.PUSH_NOTIFICATION) {
            authenticatorViewModel?.let { viewModel ->
                PushNotificationsScreen(
                    viewModel = viewModel,
                    onNotificationClick = { notificationId ->
                        // Navigate to notification response screen
                        navController.navigate("notification/$notificationId")
                    },
                    onDismiss = {
                        navController.popBackStack()
                    }
                )
            }
        }

        // Individual notification screen
        composable(
            route = "notification/{notificationId}",
            enterTransition = NavigationAnimations.enterTransition,
            exitTransition = NavigationAnimations.exitTransition,
            popEnterTransition = NavigationAnimations.popEnterTransition,
            popExitTransition = NavigationAnimations.popExitTransition
        ) { backStackEntry ->
            val notificationId = backStackEntry.arguments?.getString("notificationId") ?: ""
            authenticatorViewModel?.let { viewModel ->
                viewModel.getNotificationItemById(notificationId)?.let { notificationItem ->
                    NotificationResponseScreen(
                        notificationItem = notificationItem,
                        onDismiss = { navController.popBackStack() },
                        onApprove = {
                            viewModel.approveNotification(notificationId)
                            navController.popBackStack()
                        },
                        onDeny = {
                            viewModel.denyNotification(notificationId)
                            navController.popBackStack()
                        },
                        onChallengeSolution = { solution ->
                            viewModel.approveChallengeNotification(notificationId, solution)
                            navController.popBackStack()
                        }
                    )
                }
            }
        }

        composable(Route.ROUTE_AUTH_TEST_APP) {
            authenticatorViewModel?.let {
                TestScreen(
                    viewModel = it,
                ) {
                    navController.popBackStack()
                }
            }
        }

        composable(Route.AUTH_MIGRATION) {
            val authMigrationViewModel = viewModel<AuthMigrationViewModel>()
            val migrationState by authMigrationViewModel.state.collectAsState()

            // When migration finishes successfully, close and re-initialize the MFA clients so
            // their in-memory credential caches are cleared and fresh database connections are
            // opened — then reload credentials into the UI-bound AuthenticatorViewModel.
            LaunchedEffect(migrationState.migrationStatus) {
                if (migrationState.migrationStatus == MigrationStatus.COMPLETED) {
                    PingSampleApplication.reinitializeMfaClients()
                    authenticatorViewModel?.refreshCredentials()
                }
            }

            AuthMigrationScreen(
                viewModel = authMigrationViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Route.ROUTE_PINGONE_ACCOUNTS) {
            PingOneMFAAccountsScreen(
                onBack = { navController.popBackStack() },
                onScanQr = { navController.navigate(Route.ROUTE_PINGONE_QR_SCANNER) }
            )
        }

        composable(Route.ROUTE_PINGONE_OTP) {
            PingOneOTPScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Route.ROUTE_PINGONE_PAYLOAD) {
            PingOnePayloadScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Route.ROUTE_PINGONE_QR_SCANNER) {
            PingOneQrScannerScreen(
                onBack = { navController.popBackStack() },
                onPairComplete = { navController.popBackStack() }
            )
        }

        composable(Route.ROUTE_PINGONE_DAVINCI_PAIRING) {
            val daVinciViewModel = viewModel<DaVinciViewModel>()
            val state by daVinciViewModel.state.collectAsState()
            val loading by daVinciViewModel.loading.collectAsState()

            // Set once the user taps Continue on the pairing card. The route then exits as
            // soon as the submission completes — the server's response node is intentionally
            // ignored, except SuccessNode which exits via DaVinci's own onSuccess callback.
            var submitted by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(state.counter, loading) {
                if (submitted && !loading && state.error == null && state.node !is SuccessNode) {
                    navController.popBackStack()
                }
            }

            DaVinci(
                state = state,
                loading = loading,
                onNodeUpdated = { daVinciViewModel.refresh() },
                onNext = {
                    submitted = true
                    daVinciViewModel.next(it)
                },
                onStart = { daVinciViewModel.start() },
                onSuccess = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Route.DEVICE_AUTHORIZATION_GRANT) {
            DeviceAuthorizationGrantScreen(
                onBack = { navController.popBackStack() },
                onSuccess = {
                    navController.navigate(Route.ACCESS_TOKEN) {
                        popUpTo(Route.HOME) {
                            inclusive = false
                        }
                    }
                },
                onApproveWithDaVinci = { uri ->
                    navController.navigate(Route.daVinciDeviceApprove(uri))
                },
                onApproveWithJourney = { uri ->
                    navController.navigate(Route.journeyDeviceApproval(uri))
                },
            )
        }

        composable(Route.BACKCHANNEL_AUTH) {
            BackchannelAuthScreen(
                onStartAuth = { uri ->
                    navController.navigate(Route.backchannelJourney(uri))
                },
                onBack = { navController.navigateUp() },
            )
        }

        composable(
            route = Route.BACKCHANNEL_JOURNEY,
            arguments = listOf(navArgument("uri") { type = NavType.StringType }),
        ) { backStackEntry ->
            val uri = backStackEntry.arguments?.getString("uri") ?: ""
            val journeyViewModel = viewModel<JourneyViewModel>(
                factory = JourneyViewModel.factoryForBackchannel(uri)
            )
            JourneyScreen(
                journeyViewModel = journeyViewModel,
                onSuccess = {
                    navController.navigate(Route.userProfile(UserProfileType.JOURNEY)) {
                        popUpTo(Route.HOME) { inclusive = false }
                    }
                },
                onBack = { navController.navigateUp() },
            )
        }

        composable(
            route = Route.DAVINCI_DEVICE_APPROVE,
            arguments = listOf(navArgument("uri") { type = NavType.StringType }),
        ) { backStackEntry ->
            val uri = backStackEntry.arguments?.getString("uri") ?: ""
            DaVinci(
                verificationUri = uri,
                onSuccess = {
                    navController.navigate(Route.userProfile(UserProfileType.DAVINCI)) {
                        popUpTo(Route.HOME) { inclusive = false }
                    }
                },
                onBack = { navController.navigateUp() },
            )
        }
        composable(
            route = Route.JOURNEY_DEVICE_APPROVAL,
            arguments = listOf(navArgument("uri") { type = NavType.StringType }),
        ) { backStackEntry ->
            val uri = backStackEntry.arguments?.getString("uri") ?: ""
            val preferenceViewModel = viewModel<PreferenceViewModel>(
                factory = PreferenceViewModel.factory(LocalContext.current)
            )
            JourneyRoute(
                preferenceViewModel = preferenceViewModel,
                onSubmit = { journeyName ->
                    navController.navigate(Route.journeyWithVerification(journeyName, uri))
                },
                onBack = { navController.navigateUp() },
            )
        }
    }
}

/**
 * Placeholder screen for unimplemented screens
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceholderScreen(
    title: String,
    navController: NavHostController
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .statusBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$title Screen\n\nComing Soon...",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

