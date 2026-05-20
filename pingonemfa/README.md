[![Ping Identity](https://www.pingidentity.com/content/dam/picr/nav/Ping-Logo-2.svg)](https://github.com/ForgeRock/ping-android-sdk)

# PingOne MFA

## Overview

The `pingonemfa` module wraps the PingOne MFA native SDK (`pingidsdkv2`) behind a clean, coroutine-friendly Kotlin API. It is the adapter layer between your application and the PingOne MFA platform. All PingOne SDK callbacks are bridged to `suspend` functions that return `Result<T>` — callers never need a try/catch.

---

## Features

- **Device Pairing** — pair new MFA accounts by scanning a QR code or entering a pairing key
- **OTP** — retrieve the current one-time passcode and its remaining validity window
- **Push Notifications (foreground)** — approve or deny incoming authentication requests while the app is active
- **Push Notifications (background)** — approve or deny from the notification banner using a foreground service that bypasses Android's background network restrictions
- **Mobile Payload** — generate a cryptographic mobile payload for server-side authentication flows

---

## Architecture Overview

```
┌──────────────────────────────────────────────────┐
│               Your Application                   │
│                                                  │
│   ┌────────────────────────────────────────────┐ │
│   │         Push / OTP / Pairing Handlers      │ │
│   │              (your app code)               │ │
│   └──────────────────────┬─────────────────────┘ │
│                          │                       │
│   ┌──────────────────────▼─────────────────────┐ │
│   │              pingonemfa module             │ │
│   │         PingOneMFA (singleton object)      │ │
│   └──────────────────────┬─────────────────────┘ │
└──────────────────────────┼──────────────────────-┘
                           │
              ┌────────────▼─────────┐
              │   PingOne MFA SDK    │
              └──────────────────────┘
```

The `pingonemfa` module is the only component in the Orchestration SDK that imports from `pingidsdkv2`. All other layers depend on the typed domain models and coroutine API exposed by `PingOneMFA`.

---

## Add Dependency

```kotlin
dependencies {
    implementation("com.pingidentity.sdks:pingonemfa:<version>")
}
```

---

## Setup and Configuration

### 1. Initialize the SDK

Call `initialize()` once at application startup, before any other call. Pass the `Geo` that matches your PingOne environment's service region. The call is idempotent — repeated calls after a successful initialisation return immediately without re-entering the native SDK.

```kotlin
val result = PingOneMFA.initialize(Geo.NORTH_AMERICA)
result.onFailure { e ->
    Log.e("MFA", "Initialisation failed: ${e.message}")
}
```

Supported regions:

| `Geo` | PingOne region |
|---|---|
| `Geo.NORTH_AMERICA` | North America |
| `Geo.EUROPE` | Europe |
| `Geo.CANADA` | Canada |
| `Geo.AUSTRALIA` | Australia |
| `Geo.SINGAPORE` | Singapore |

### 2. Register the FCM Push Token

Call `setDeviceToken()` each time Firebase delivers a new push token — typically from `FirebaseMessagingService.onNewToken`:

```kotlin
override fun onNewToken(token: String) {
    lifecycleScope.launch {
        PingOneMFA.setDeviceToken(token).onFailure { e ->
            Log.e("MFA", "Token registration failed: ${e.message}")
        }
    }
}
```

---

## Usage

### Device Pairing

```kotlin
PingOneMFA.pair(pairingKey)
    .onSuccess {
        // Pairing succeeded — update UI as needed
    }
    .onFailure { e ->
        Log.e("MFA", "Pairing failed: ${e.message}")
    }
```

### Retrieve Paired Accounts

```kotlin
PingOneMFA.getDeviceInfo().onSuccess { accounts ->
    accounts.forEach { account ->
        Log.d("MFA", "${account.name} ${account.family} | region: ${account.region}")
    }
}
```

### OTP

```kotlin
PingOneMFA.getOneTimePasscode().onSuccess { otp ->
    showCode(otp.code, otp.secondsRemaining)
}
```

`OtpCodeInfo.secondsRemaining` is a snapshot computed at call time. Re-call `getOneTimePasscode()` when it reaches zero to receive the next code.

### Mobile Payload

```kotlin
PingOneMFA.generateMobilePayload().onSuccess { payload ->
    // Submit payload to your server-side authentication flow
}
```

### Push Notifications — Foreground

When your app is in the foreground, process the incoming `RemoteMessage` and present the appropriate UI based on push type:

```kotlin
// In FirebaseMessagingService.onMessageReceived:
PingOneMFA.processRemoteNotification(remoteMessage).onSuccess { push ->
    when (push.getPushType()) {
        PushType.DEFAULT   -> showApproveDenyUI(push)
        PushType.CHALLENGE -> showNumberChallengeUI(push)
        PushType.DRY       -> { /* test push — no user action required */ }
    }
}
```

After the user responds:

```kotlin
// Approve (pass numberChallenge for CHALLENGE type, null for DEFAULT)
push.approveNotification(
    context = applicationContext,
    authenticationMethod = "app",
    numberChallenge = selectedNumber
).onSuccess { /* done */ }

// Deny
push.denyNotification(applicationContext)
    .onSuccess { /* done */ }
```

For number-matching challenge pushes, retrieve the options provided by the server:

```kotlin
val options: IntArray? = push.getNumbersChallenge()
// options is null when the server expects free-form digit entry
```

### Push Notifications — Background (Notification Banner)

When the user taps Approve or Deny on the system notification banner while the app is in the background, use the banner helpers. These route the network call through `PushApprovalService`, which runs as a foreground service and is exempt from Android's background network restrictions:

```kotlin
// Called from your notification action BroadcastReceiver:
PingOneMFA.approvePushNotificationFromBanner(pushNotification)
// or
PingOneMFA.denyPushNotificationFromBanner(pushNotification)
```

> `PushApprovalService` completes the network call asynchronously and does not surface the outcome back to the UI. If your application needs to react to banner-approval results, add a custom broadcast or shared state mechanism.

---

## Required Manifest Permissions

The following permissions are declared in the module's `AndroidManifest.xml` and merged into your app automatically:

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_REMOTE_MESSAGING" />
```

These are required by `PushApprovalService` for background push handling.

---

## Error Handling

All `suspend` functions return `Result.failure(PingOneMFAException(...))` on error and never throw. `PingOneMFAException` contains a human-readable `message` — the native `PingOneSDKError` type is not exposed, so your app does not need a direct dependency on the `pingidsdkv2` AAR.

```kotlin
PingOneMFA.pair(pairingKey)
    .onSuccess {
        // success path
    }
    .onFailure { e ->
        // e is PingOneMFAException — e.message is always non-null
        Log.e("MFA", e.message)
    }
```

---

## Sample Application

PingOne MFA functionality is demonstrated in the [pingsampleapp](../samples/pingsampleapp) sample under the **PINGONE MFA** section of the home screen:

- QR code scanning for device pairing
- Paired accounts list
- OTP display with live countdown
- Push notification handling for DEFAULT, CHALLENGE, and DRY push types
- Background push approval from the notification banner

See the [pingsampleapp README](../samples/pingsampleapp/README.md) for build instructions.

---

## API Reference

### `PingOneMFA`

| Function | Returns | Description |
|---|---|---|
| `suspend initialize(geo: Geo)` | `Result<Unit>` | Configure the PingOne SDK for the selected service region. Idempotent after first success. |
| `suspend setDeviceToken(pushToken)` | `Result<Unit>` | Register or refresh the FCM push token with PingOne. |
| `suspend pair(pairingKey)` | `Result<Unit>` | Pair a new MFA account. |
| `suspend getDeviceInfo()` | `Result<List<PingOneMfaAccount>>` | Return all paired accounts. |
| `suspend getOneTimePasscode()` | `Result<OtpCodeInfo>` | Return the current TOTP code and its remaining validity window. |
| `suspend processRemoteNotification(message)` | `Result<PushNotification>` | Convert an FCM `RemoteMessage` to a typed `PushNotification`. |
| `suspend generateMobilePayload()` | `Result<String>` | Generate a mobile payload for server-side authentication. |
| `approvePushNotificationFromBanner(notification)` | `Unit` | Start the background foreground service to approve a banner push. |
| `denyPushNotificationFromBanner(notification)` | `Unit` | Start the background foreground service to deny a banner push. |

### `PingOneMfaAccount`

| Field | Type | Description |
|---|---|---|
| `region` | `String` | Region key from the PingOne response (e.g. `"NA"`, `"EU"`) |
| `id` | `String` | PingOne user ID |
| `deviceId` | `String` | Device ID within PingOne |
| `environment` | `String` | PingOne environment ID |
| `name` | `String` | User's given name |
| `family` | `String` | User's family name |

### `OtpCodeInfo`

| Field | Type | Description |
|---|---|---|
| `code` | `String` | Current TOTP passcode |
| `secondsRemaining` | `Int` | Seconds until the code expires (snapshot at call time); clamped to `0` if already expired |

### `PushNotification`

| Method / Field | Type | Description |
|---|---|---|
| `approveNotification(ctx, method, challenge?)` | `suspend Result<Unit>` | Approve the push authentication request |
| `denyNotification(ctx)` | `suspend Result<Unit>` | Deny the push authentication request |
| `getNumbersChallenge()` | `IntArray?` | Options for a number-matching CHALLENGE push; `null` when free-form digit entry is expected |
| `getPushType()` | `PushType` | The interaction model required by this push (see `PushType`) |
| `title` | `String?` | Notification title extracted from the FCM payload |
| `message` | `String?` | Notification body extracted from the FCM payload |

### `PushType`

| Value | Description |
|---|---|
| `DEFAULT` | Standard authentication request — the user approves or denies with a single tap |
| `CHALLENGE` | Number-matching push — present the options from `getNumbersChallenge()`; a `null` return means free-form digit entry is expected |
| `DRY` | Silent test push sent by the server to verify push registration — no user action required |

### `PingOneMFAException`

Returned via `Result.failure` by all `suspend` functions. The native `PingOneSDKError` is not exposed — all error information is available through the standard `message` property.

---

## Troubleshooting

**Push notifications not received:**
- Verify the device token was registered via `PingOneMFA.setDeviceToken(token)`.
- Confirm `google-services.json` is present and matches your application ID.
- Check that the PingOne environment has FCM configured.

**`getOneTimePasscode()` fails with a device-not-paired error:**
- Ensure `PingOneMFA.pair(pairingKey)` was called and succeeded before requesting an OTP.

**`generateMobilePayload()` fails:**
- Ensure `PingOneMFA.initialize()` was called and succeeded before this call.
- Check network connectivity and PingOne service status.

---

## License

Copyright (c) 2026 Ping Identity Corporation. All rights reserved.

This software may be modified and distributed under the terms of the MIT license. See the [LICENSE](../LICENSE) file for details.
