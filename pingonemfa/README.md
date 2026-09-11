[![Ping Identity](https://www.pingidentity.com/content/dam/picr/nav/Ping-Logo-2.svg)](https://github.com/ForgeRock/ping-android-sdk)

# PingOne MFA

## Overview

The `pingonemfa` module wraps the [PingOne MFA native SDK](https://github.com/pingidentity/pingone-customers-mobile-sdk-android) behind a clean, coroutine-friendly Kotlin API. It is the adapter layer between your application and the PingOne MFA platform. All PingOne SDK callbacks are bridged to `suspend` functions that return `Result<T>` — callers never need a try/catch.

---

## Features

- **Device Pairing** — pair new MFA accounts by scanning a QR code or entering a pairing key manually
- **DaVinci Mobile Pairing** — pair new MFA accounts directly from a DaVinci flow via the
  `MOBILE_PAIRING` collector, without any manual QR code or pairing key handling
- **Paired Accounts List** — retrieve information about all currently paired accounts
- **OTP** — retrieve the current one-time passcode and its remaining validity window
- **Push Notifications (foreground and background)** — approve or deny incoming authentication requests
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
└──────────────────────────┼───────────────────────┘
                           │
              ┌────────────▼─────────┐
              │   PingOne MFA SDK    │
              └──────────────────────┘
```

The `pingonemfa` module is the only component in the Orchestration SDK that imports from native PingOne MFA SDK. All other layers depend on the typed domain models and coroutine API exposed by `PingOneMFA`.

---

## Getting Started

### Prerequisites

- Android API level 24 or higher
- Firebase Cloud Messaging configured for your application (`google-services.json` present and matching your application ID)
- A PingOne environment with push notifications and/or MFA configured, for documentation on setting up PingOne MFA, see [PingOne MFA documentation](https://docs.pingidentity.com/pingone/strong_authentication_mfa/p1_strong_authentication_configure_mobile_applications.html).

---

## Add Dependency

```kotlin
dependencies {
    implementation("com.pingidentity.sdks:pingonemfa:<version>")

    // Firebase Cloud Messaging — required for push notification support
    implementation(platform("com.google.firebase:firebase-bom:<version>"))
    implementation("com.google.firebase:firebase-messaging")
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

Call `setDeviceToken()` each time Firebase delivers a new push token — typically from `FirebaseMessagingService.onNewToken`. The SDK registers the token across all configured PingOne regions; if any region rejects it the call fails and `internalErrorsList` holds one `Error` per failed region:

```kotlin
override fun onNewToken(token: String) {
    CoroutineScope(SupervisorJob()).launch {
        PingOneMFA.setDeviceToken(token)
            .onSuccess {
                // Token registered successfully in all regions
            }
            .onFailure { e ->
                Log.e("MFA", "Token registration failed: ${e.message}")
                // Per-region failure details (developer logging only)
                (e as? PingOneMFAException)?.internalErrorsList?.forEach { err ->
                    Log.e("MFA", "Region failure: code=${err.code} info=${err.userInfo}")
                }
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

### DaVinci Mobile Pairing

The `pingonemfa` module transparently registers a
[`MobilePairingCollector`](src/main/java/com/pingidentity/pingonemfa/davinci/MobilePairingCollector.kt)
with the DaVinci engine at startup via
[`CollectorInitializer`](src/main/java/com/pingidentity/pingonemfa/davinci/CollectorInitializer.kt)
- no manual wiring is required. When the DaVinci server returns a node containing a `MOBILE_PAIRING` collector, the collector reads the server-provided `pairingKey`,
calls `PingOneMFA.pair(...)`, and posts the outcome back to DaVinci in the resume envelope.

Your UI drives the collector directly:

```kotlin
val mobilePairing = continueNode.collectors
    .firstOrNull { it is MobilePairingCollector } as? MobilePairingCollector

mobilePairing?.let { collector ->
    // Suspending — call from a coroutine. Show a progress UI while pairing runs.
    val result = collector.collect()
    result.onSuccess { /* pairing succeeded */ }
        .onFailure { e -> Log.e("MFA", "Pairing failed: ${e.message}") }
    // Submit the collector's outcome (success or failure) back to the DaVinci server:
    continueNode.next()
}
```

If the user abandons the flow before pairing completes, call `collector.cancel()` to
record a `USER_CANCELLED` outcome. The native SDK has no abort API, so any in-flight
`PingOneMFA.pair(...)` call continues in the background; its result is discarded so the
user-cancelled payload is preserved.

**Resume envelope shape** posted to the DaVinci server under `formData.mobilePairing`:

| Outcome | Payload |
|---|---|
| Success | `{ "status": "CLAIMED" }` |
| Native failure | `{ "error": { "code": "<nativeCode>", "message": "<message>" } }` |
| Unexpected failure | `{ "error": { "code": "INTERNAL_ERROR", "message": "<message>" } }` |
| User cancelled | `{ "error": { "code": "USER_CANCELLED", "message": "<message>" } }` |

### Retrieve Paired Accounts

```kotlin
PingOneMFA.getDeviceInfo().onSuccess { (accounts, diagnosticErrors) ->
    accounts.forEach { account ->
        Log.d("MFA", "${account.username} | region: ${account.region}")
    }
    // diagnosticErrors is non-null only when the SDK returned partial error info alongside
    // valid data — log it for debugging, the account list is still safe to use.
    diagnosticErrors?.forEach { err ->
        Log.w("MFA", "Diagnostic: code=${err.code} info=${err.userInfo}")
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
    authenticationMethod = "user", // or "biometric", depending on your UI
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

All `suspend` functions return `Result.failure(PingOneMFAException(...))` on error and never throw. The native `PingOneSDKError` type is never exposed — all error information is available through `PingOneMFAException`.

### `PingOneMFAException`

| Property | Type | Description |
|---|---|---|
| `message` | `String` | Human-readable description of the failure. Always non-null. Use this for logging or user-facing error display. |
| `cause` | `Throwable?` | The original exception when the failure was not a native SDK error (e.g. a network timeout). Preserved in the stack trace. |
| `internalErrorsList` | `List<Error>?` | Structured list of `Error` objects parsed from the native SDK error(s). `null` when the failure did not originate from the native SDK. Each entry contains the numeric code, message, and any `userInfo` diagnostic data returned by the server — **for developer logging only, not for user-facing messages**. |

```kotlin
PingOneMFA.pair(pairingKey)
    .onSuccess {
        // success path
    }
    .onFailure { e ->
        // Use message for display or simple logging
        Log.e("MFA", e.message)

        // Use internalErrorsList containing Error objects for detailed diagnostics (developer-only)
        e.internalErrorsList?.forEach { error ->
            Log.e("MFA", "code=${error.code} userInfo=${error.userInfo}")
        }
    }
```

### `Error`

Structured representation of a single PingOne SDK error, exposed via `PingOneMFAException.internalErrorsList`.

| Property | Type | Description |
|---|---|---|
| `code` | `Int?` | Numeric error code from the native SDK. See [PingOneSDKError documentation](https://pingidentity.github.io/pingone-mobile-sdk-android/-ping-one%20-m-f-a%20-android%20-s-d-k/com.pingidentity.pingidsdkv2.error/-ping-one-s-d-k-error-type/index.html) for the full list. |
| `message` | `String?` | Human-readable error message from the native SDK. |
| `userInfo` | `Map<String, String>` | Additional diagnostic key/value pairs returned by the server. Intended for **developer logging and debugging only** — do not display to users. Empty if the server did not include additional context. |

---

## Sample Application

PingOne MFA functionality is demonstrated in the [pingsampleapp](../samples/pingsampleapp) sample under the **PINGONE MFA** section of the home screen:

- QR code scanning for device pairing
- DaVinci Pairing — an end-to-end DaVinci flow that drives `MobilePairingCollector`,
  configured through a dedicated "PingOne MFA DaVinci" card in the Configuration screen
  so it is independent from the standard DaVinci config
- Paired accounts list
- OTP display with live countdown
- Mobile payload generation screen
- Push notification handling for DEFAULT, CHALLENGE, and DRY push types
- Background push approval from the notification banner

See the [pingsampleapp README](../samples/pingsampleapp/README.md) for build instructions.

---

## API Reference

### `PingOneMFA`

| Function | Returns | Description |
|---|---|---|
| `suspend initialize(geo: Geo)` | `Result<Unit>` | Configure the PingOne SDK for the selected service region. Idempotent after first success. |
| `suspend setDeviceToken(pushToken)` | `Result<Unit>` | Register or refresh the FCM push token with PingOne across all configured regions. On failure `PingOneMFAException.internalErrorsList` holds one `Error` per failed region. |
| `suspend pair(pairingKey)` | `Result<Unit>` | Pair a new MFA account. |
| `suspend getDeviceInfo()` | `Result<Pair<List<PingOneMfaAccount>, List<Error>?>>` | Return all paired accounts. The second element of the pair contains diagnostic errors from the SDK, if any — non-null only when the SDK returned partial error context alongside valid data. |
| `suspend getOneTimePasscode()` | `Result<OtpCodeInfo>` | Return the current TOTP code and its remaining validity window. |
| `suspend processRemoteNotification(message)` | `Result<PushNotification>` | Convert an FCM `RemoteMessage` to a typed `PushNotification`. |
| `suspend generateMobilePayload()` | `Result<String>` | Generate a mobile payload for server-side authentication. |
| `approvePushNotificationFromBanner(notification)` | `Unit` | Start the background foreground service to approve a banner push. |
| `denyPushNotificationFromBanner(notification)` | `Unit` | Start the background foreground service to deny a banner push. |

### `MobilePairingCollector`

A DaVinci [`Collector`](../foundation/davinci-plugin) for the `MOBILE_PAIRING` node type,
registered with the DaVinci engine automatically at startup via `CollectorInitializer`
(no manual registration required). Drives the pairing flow from a DaVinci policy.

| Function / Field | Type | Description |
|---|---|---|
| `pairingKey` | `String` | Pairing key supplied by the DaVinci server. Populated during `init`; passed to `PingOneMFA.pair(...)` in `collect()`. |
| `key` | `String` | Field key sent by the server; also returned by `id()`. Used by DaVinci as the `formData` slot name (`"mobilePairing"` per the connector spec). |
| `suspend collect()` | `Result<Unit>` | Runs pairing via `PingOneMFA.pair(pairingKey)` and stores the outcome under `payload()`. Must be called from a coroutine. |
| `cancel(message?)` | `Unit` | Records a `USER_CANCELLED` outcome for `payload()`. Does not abort any in-flight `collect()` — the native SDK has no abort API — but a late-arriving native result is discarded so the cancellation payload is preserved. |
| `payload()` | `JsonObject?` | Pairing outcome to be posted back to DaVinci under `formData[[id]]`. `null` until `collect()` or `cancel()` runs. |
| `eventType()` | `String` | Returns `"action"` — matches the contract of self-submitting SDK Integrator connectors. |

### `PingOneMfaAccount`

| Field | Type | Description |
|---|---|---|
| `region` | `String` | Region key from the PingOne response (e.g. `"NA"`, `"EU"`) |
| `id` | `String` | PingOne user ID |
| `deviceId` | `String` | Device ID associated with this pairing within PingOne |
| `environment` | `String` | PingOne environment ID |
| `username` | `String` | The account's login username as returned by the PingOne server |
| `name` | `String?` | User's given (first) name, or `null` if not provided by the server |
| `family` | `String?` | User's family (last) name, or `null` if not provided by the server |

### `OtpCodeInfo`

| Field | Type | Description |
|---|---|---|
| `code` | `String` | Current TOTP passcode |
| `secondsRemaining` | `Int` | Seconds until the code expires (snapshot at call time); clamped to `0` if already expired |

### `PushNotification`

| Method / Field | Type | Description                                                                                                                          |
|---|---|--------------------------------------------------------------------------------------------------------------------------------------|
| `approveNotification(ctx, method, challenge?)` | `suspend Result<Unit>` | Approve the push authentication request                                                                                              |
| `denyNotification(ctx)` | `suspend Result<Unit>` | Deny the push authentication request                                                                                                 |
| `isCancelAuthentication()` | `Boolean` | `true` when the server has cancelled active request (e.g. approved on another device) — dismiss the UI without requiring user action |
| `getNumbersChallenge()` | `IntArray?` | Options for a number-matching CHALLENGE push; `null` when free-form digit entry is expected                                          |
| `getPushType()` | `PushType` | The interaction model required by this push (see `PushType`)                                                                         |
| `id` | `String` | Wrapper-generated unique identifier for this push request                                                                            |
| `title` | `String?` | Notification title extracted from the FCM payload                                                                                    |
| `message` | `String?` | Notification body extracted from the FCM payload                                                                                     |

### `PushType`

| Value | Description |
|---|---|
| `DEFAULT` | Standard authentication request — the user approves or denies with a single tap |
| `CHALLENGE` | Number-matching push — present the options from `getNumbersChallenge()`; a `null` return means free-form digit entry is expected |
| `DRY` | Silent test push sent by the server to verify push registration — no user action required |

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
