[![Ping Identity](https://www.pingidentity.com/content/dam/picr/nav/Ping-Logo-2.svg)](https://github.com/ForgeRock/ping-android-sdk)

# Ping Sample App

A comprehensive sample application demonstrating the integration of multiple Ping Identity SDK features in a single, unified application. This app serves as a reference implementation combining authentication flows, MFA capabilities, device management, and developer tools.

## Overview

The Ping Sample App is a consolidated sample that brings together functionality from multiple standalone samples (Journey, DaVinci, OIDC, and Authenticator) into one cohesive application. It demonstrates best practices for building production-ready Android applications with the Ping Identity SDK.

## Key Features

### 🔐 Authentication
- **Journey Flow**: ForgeRock Journey-based authentication with callback rendering
- **DaVinci Flow**: PingOne DaVinci authentication flows
- **OIDC Login**: OpenID Connect web authentication

### 👤 User Management
- **User Profile**: View and display user information with JSON toggle
- **Access Token**: View, refresh, and revoke access tokens with formatted JSON display
- **Device Management**: Register, view, edit, and delete trusted devices
- **Logout**: Comprehensive logout with session management (Journey, DaVinci, OIDC)

### 🔑 Multi-Factor Authentication (MFA)
- **OATH (TOTP/HOTP)**: 
  - QR code scanning for account registration
  - Manual account entry
  - Time-based and counter-based OTP generation
  - Account grouping by issuer
- **Push Notifications**:
  - Push authentication registration
  - Notification history and management
  - Biometric authentication support
  - Challenge verification
- **QR Scanner**: Integrated camera-based QR code scanning

### 🔒 PingOne MFA
- **QR Code Registration**: Scan a QR code to pair the device with PingOne MFA
- **DaVinci Pairing**: Pair the device by running a DaVinci flow that drives the
  `MobilePairingCollector` from the `pingonemfa` module — no manual QR scan or pairing
  key entry required. Configured through a dedicated "PingOne MFA DaVinci" card in the
  Configuration screen, independent from the standard DaVinci config.
- **MFA Accounts**: View all paired PingOne MFA accounts
- **One-Time Passcode**: Display the current OTP code with a live countdown
- **Mobile Payload**: Generate a mobile payload for server-side authentication flows
- **Push Notifications**: Full foreground and background push authentication handling
  - DEFAULT: approve or deny with a single tap
  - CHALLENGE: number-matching with server-provided options or free-form digit entry
  - DRY: silent test push with automatic dismissal
  - Cancellation: server-revoked requests are dismissed automatically on all surfaces

### 🛠️ Developer Tools
- **Configuration**: Environment selection (Staging/Snapshot) with custom URL support
- **Device Information**: Comprehensive device data collection and display
- **Diagnostic Logging**: View application logs for debugging

## Architecture

### Application Structure

The app follows a modular architecture with clear separation of concerns:

```
PingSampleApp
├── Authentication Flows (Journey, DaVinci, OIDC)
├── User Management (Profile, Token, Device, Logout)
├── MFA Features (OATH, Push, QR Scanner)
├── PingOne MFA (Pairing, OTP, Push, Payload)
├── Developer Tools (Config, Device Info, Logger)
└── Shared Components (Navigation, Theme, Config)
```

### Key Components

#### 1. **PingSampleApplication**
Application-level initialization and dependency management:
- Initializes SDK clients (OATH, Push, PingOne MFA)
- Creates and manages managers (OathManager, PushManager, JourneyManager)
- Provides application-scoped AuthenticatorViewModel
- Handles Firebase Cloud Messaging setup and token registration for both MFA modules

#### 2. **Navigation System**
Centralized navigation with proper screen routing:
- Home screen with categorized feature access
- Deep linking support for authentication flows
- Back navigation handling
- Screen-specific ViewModels

#### 3. **Authenticator Integration**
Complete MFA functionality integrated from AuthenticatorApp:
- **Managers**: OathManager, PushManager, JourneyManager, AccountGroupingManager
- **UI Screens**: AccountsScreen, PushNotificationsScreen, QrScannerScreen, and more
- **Services**: PushNotificationService, LocationService
- **Notification Handlers**: BiometricPromptActivity, NotificationActionReceiver

#### 4. **PingOne MFA Integration**
Direct integration of the `pingonemfa` module for PingOne push and OTP:
- **ViewModel**: PingOneMFAViewModel — manages pairing, OTP countdown, payload, and account state
- **UI Screens**: PingOneQrScannerScreen, PingOneMFAAccountsScreen, PingOneOTPScreen, PingOnePayloadScreen, PingOnePushNotificationScreen
- **Notification**: PingOneNotificationHelper, PingOneNotificationActionReceiver, PingOnePushNotificationActivity
- **Store**: PushNotificationStore — single-slot in-process store for the active push notification
- **DaVinci Pairing**:
  - `PingOneMfaDaVinciViewModel` drives the DaVinci flow against the dedicated
    `pingOneMfaDaVinci` global (never the standard `daVinci`)
  - `pingonemfa/davinci/collector/MobilePairing.kt` renders the pairing UI states
    (in-progress with cancel, success, failure with server error details) driven by the
    `MobilePairingCollector` from the `pingonemfa` SDK module
  - Configured via a dedicated "PingOne MFA DaVinci" card in the Configuration screen —
    see [PingOne MFA DaVinci Setup](#pingone-mfa-davinci-setup)

#### 5. **Device Management**
Comprehensive device registration and management:
- Device registration with custom names
- Device list display with platform icons
- Device editing (rename)
- Device deletion with confirmation
- Automatic list refresh

#### 6. **Token Management**
Access token viewing and manipulation:
- Pretty-printed JSON display
- Token refresh functionality
- Token revocation
- Scrollable token view with dropdown menu

## Configuration

### Environment Setup

The app supports multiple environments configured via `EnvViewModel`:

1. **Staging Environment** (default)
2. **Snapshot Environment**
3. **Custom Configuration** (manual URL entry)

Configuration includes:
- Discovery endpoint URL
- Client ID
- Redirect URI
- Scopes
- Cookie name

### Firebase Setup

For Push notifications:
1. Add `google-services.json` to the app directory
2. Configure Firebase Cloud Messaging in Firebase Console
3. Enable push notifications in device settings

### PingOne MFA Setup

The PingOne MFA module (`pingonemfa`) requires additional one-time configuration:
1. `PingOneMFA.initialize(Geo.NORTH_AMERICA)` is called automatically at startup in `PingSampleApplication` — update the `Geo` value to match your PingOne environment's region
2. The FCM token is registered with PingOne automatically via `PingOneMFA.setDeviceToken(token)` whenever Firebase delivers a new token
3. See the [pingonemfa README](../pingonemfa/README.md) for the full list of supported regions and API reference
4. See the [PingOne MFA documentation](https://docs.pingidentity.com/pingone/strong_authentication_mfa/p1_strong_authentication_configure_mobile_applications.html) for server-side configuration and integration details

### PingOne MFA DaVinci Setup

The DaVinci pairing flow uses a **dedicated DaVinci client** so its configuration is
completely independent from the standard DaVinci flow. Any change to the standard
DaVinci config in the Configuration screen has no effect on the pairing flow, and
vice versa.

- **Preset asset file** — configs placed under `src/main/assets/` and flagged with
  `"pingOneMfa": true` at the top level are auto-loaded as presets in the
  "PingOne MFA DaVinci" card. They are excluded from the DaVinci / Web / DeviceAuth
  preset lists so they never pollute other cards. Example:

  ```json
  {
    "pingOneMfa": true,
    "oidc": {
      "clientId": "…",
      "discoveryEndpoint": "https://…/.well-known/openid-configuration",
      "scopes": ["openid"],
      "redirectUri": "app://oauth2redirect"
    }
  }
  ```

- **Custom configs** — add, edit, duplicate, and delete configs from the
  "PingOne MFA DaVinci" card. Custom configs and the applied selection persist in
  DataStore under the `pmfa_` key prefix, independent from the `dv_` keys used by
  the standard DaVinci.
- **Out-of-the-box behaviour** — on a fresh install with no user-applied config, the
  first asset preset is auto-selected so pairing works immediately from the Home
  screen's "DaVinci Pairing" entry.
- **Runtime instance** — the applied config builds the `pingOneMfaDaVinci` global in
  `EnvViewModel`, consumed only by `PingOneMfaDaVinciViewModel`. The standard
  `daVinci` global is never overwritten.
### PingOne Recognize integration

The Recognize integration is optional because its Keyless SDK dependencies are hosted in protected Cloudsmith repositories.

#### Required Cloudsmith tokens

Add both tokens to a local Gradle properties file using these exact property names:

| Gradle property | Repository | Used for |
|---|---|---|
| `cloudsmithTokenRecognize` | `keyless/partners` | PingOne Recognize / Keyless Mobile SDK |
| `cloudsmithTokenAesWrap` | `keyless/aeswrap` | AES wrap dependencies used by the SDK |

- **Recommended:** `~/.gradle/gradle.properties` — applies locally without changing the repository
- **Alternative:** the repository's `gradle.properties` — keep this file local and never commit it

```properties
cloudsmithTokenRecognize=<your-recognize-cloudsmith-token>
cloudsmithTokenAesWrap=<your-aeswrap-cloudsmith-token>
```

Do not place either token in source code, commit them, or share them in logs. The build reads both properties from Gradle properties and uses them to configure the protected Maven repositories in `settings.gradle.kts`.

When `cloudsmithTokenRecognize` is present and non-blank, Gradle enables the `:recognize` module and the sample compiles its real Recognize callback integration. The AES wrap repository is configured separately through `cloudsmithTokenAesWrap`; provide that token whenever the dependency graph requires AES wrap artifacts.

```bash
./gradlew :samples:pingsampleapp:assembleDebug
```

#### Run without Recognize

If `cloudsmithTokenRecognize` is missing or blank, the sample remains buildable without the protected Recognize dependency. The build excludes the `:recognize` module and selects the local Recognize stub, so Recognize callbacks are safely skipped rather than preventing the rest of the sample app from running.

This means you can work on the sample without Recognize Cloudsmith access, then add the required tokens locally whenever you need to exercise the full integration. After changing either token, sync or rerun Gradle so the correct repository and dependency graph are selected.

## Implementation Highlights

### ViewModel Initialization

The app uses application-level ViewModel initialization for the AuthenticatorViewModel:

```kotlin
// In PingSampleApplication
val authenticatorViewModel = AuthenticatorViewModel(
    application = this,
    userPreferences = userPreferences,
    oathManager = oathManager,
    pushManager = pushManager,
    accountGroupingManager = accountGroupingManager,
    testAccountFactory = testAccountFactory
)
```

Access pattern in Composables:
```kotlin
var authenticatorViewModel by remember { mutableStateOf<AuthenticatorViewModel?>(null) }

LaunchedEffect(Unit) {
    authenticatorViewModel = PingSampleApplication.getAuthenticatorViewModel()
}

authenticatorViewModel?.let { viewModel ->
    // Use ViewModel
}
```

### Device Management Pattern

Device operations follow a consistent pattern:

```kotlin
// Register Device
deviceClient.register(deviceName, deviceType, pushToken)

// Edit Device
deviceClient.update(deviceId, newName, ifMatch)

// Delete Device
deviceClient.delete(deviceId)

// List Devices
deviceClient.devices()
```

### Token Display with State Flow

Token formatting uses reactive StateFlow pattern:

```kotlin
val formattedToken = state.map { tokenState ->
    tokenState.token?.let {
        json.encodeToString(Token.serializer(), it)
    } ?: "No token available"
}
```

### Logout Session Management

Supports multiple simultaneous sessions:

```kotlin
// Check active sessions
val hasActiveSessions = state.journey || state.daVinci || state.oidc

// Logout all sessions
fun logoutAll() {
    journey.user()?.logout()
    daVinci?.user()?.logout()
    web?.user()?.logout()
}
```

## Screen Organization

### Home Screen Categories

**AUTHENTICATION**
- DaVinci Flow
- Journey Flow
- OIDC Login

**USER MANAGEMENT**
- User Profile
- Access Token
- Device Management
- Logout

**MFA**
- QR Scanner
- OATH
- Push
- Push Notifications

**PINGONE MFA**
- QR Code Registration
- DaVinci Pairing
- MFA Accounts
- One-Time Passcode
- Mobile Payload

**DEVELOPER TOOLS**
- Configuration
- Device Information
- Logger

## Dependencies

Key dependencies include:
- Ping Identity SDK modules (Journey, DaVinci, OIDC, MFA, PingOne MFA)
- Jetpack Compose for UI
- Navigation Component
- Firebase Cloud Messaging
- CameraX for QR scanning
- Biometric authentication
- Kotlinx Serialization

## Differences from Standalone Samples

### vs. AuthenticatorApp
- Integrated into main navigation (not standalone app)
- Shares application lifecycle with other features
- Uses PingSampleApplication instead of AuthenticatorApp
- Consistent theme with rest of the app

### vs. JourneyApp
- Journey flows accessible from main navigation
- Integrated with DaVinci and OIDC flows
- Shared user management features
- Combined logout functionality

### vs. App Sample
- Consolidated all features into single app
- Unified navigation structure
- Shared configuration system
- Common theme and styling

## Building and Running

1. Clone the repository
2. Add `google-services.json` for Firebase (optional, for push notifications)
3. Configure environment in app (Settings → Configuration)
4. Build and run:
   ```bash
   ./gradlew :samples:pingsampleapp:assembleDebug
   ```

## Package Structure

```
com.pingidentity.samples.pingsampleapp
├── authenticator/              # MFA functionality
│   ├── managers/              # Business logic managers
│   ├── ui/                    # Authenticator screens
│   ├── notification/          # Push notification handlers
│   └── service/               # Background services (PushNotificationService)
├── pingonemfa/                 # PingOne MFA integration
│   └── notification/          # Push management
├── config/                    # Environment configuration
├── davinci/                   # DaVinci flow screens
├── devicemanagement/          # Device registration/management
├── home/                      # Main home screen
├── journey/                   # Journey flow screens
├── logout/                    # Logout functionality
├── navigation/                # App navigation
├── oidc/                      # OIDC authentication
├── theme/                     # App theming
├── token/                     # Token management
├── userprofile/               # User profile display
└── PingSampleApplication.kt   # Application entry point
```

## Known Limitations

- Firebase configuration required for push notifications
- Some Journey flows may require specific backend configuration
- Device management requires authenticated session
- Location services used in push notifications require permissions

## Support

For issues, questions, or contributions, please refer to the main Ping Android SDK repository.

## License

This sample application is licensed under the MIT License. See LICENSE file for details.


© Copyright 2025-2026 Ping Identity Corporation. All Rights Reserved
