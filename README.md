[![Build Status](https://github.com/ForgeRock/ping-android-sdk/actions/workflows/ci.yaml/badge.svg)](https://github.com/ForgeRock/ping-android-sdk/actions/workflows/ci.yaml)
[![Coverage](https://codecov.io/gh/ForgeRock/unified-sdk-android/graph/badge.svg?token=1UYU8JMS8C)](https://codecov.io/gh/ForgeRock/unified-sdk-android)

[![Ping Identity](https://www.pingidentity.com/content/dam/picr/nav/Ping-Logo-2.svg)](https://github.com/ForgeRock/ping-android-sdk)

The Ping SDK for Android is designed for creating mobile native Apps that seamlessly integrate with the PingOne platform.
It offers a range of APIs for user authentication, user device management, and accessing resources secured by PingOne.
This SDK is support Browser, iOS and Android platforms.

Please visit our [ documentation ](https://docs.pingidentity.com/sdks/latest/sdks/index.html) site for more information.

# Modules
    ping
    ├── foundation                            # Foundation module
    │   ├── android                           # Android common utilities
    │   ├── browser                           # Browser launcher / Custom Tabs support
    │   ├── davinci-plugin                    # Plugin contract for DaVinci integration
    │   ├── device                            # Device capabilities
    │   │   ├── device-client                 # Device client information
    │   │   ├── device-id                     # Unique device identifier
    │   │   ├── device-profile                # Device profile collection
    │   │   └── device-root                   # Device root / integrity detection
    │   ├── journey-plugin                    # Plugin contract for Journey integration
    │   ├── logger                            # Logging interface and built-in loggers
    │   ├── migration                         # Legacy-to-modern data migration helpers
    │   ├── network                           # Ktor-based HTTP network layer
    │   ├── oidc                              # OIDC / OAuth 2.0 interface
    │   ├── orchestrate                       # Authentication flow orchestration framework
    │   ├── storage                           # Encrypted and plain storage abstractions
    │   ├── testrail                          # TestRail integration for test reporting
    │   └── utils                             # Shared utility functions
    ├── davinci                               # Orchestrate authentication with PingOne DaVinci
    ├── external-idp                          # Native social login (Google, Facebook, Apple)
    ├── journey                               # Orchestrate authentication with PingAM Journey
    ├── mfa                                   # MFA capabilities
    │   ├── auth-migration                    # Authentication data migration helpers
    │   ├── binding                           # Device binding (biometric / PIN / none)
    │   ├── binding-migration                 # Legacy device binding data migration
    │   ├── binding-ui                        # Jetpack Compose UI for device binding
    │   ├── commons                           # Shared MFA types and utilities
    │   ├── fido                              # FIDO2 / WebAuthn
    │   ├── oath                              # TOTP / HOTP one-time passwords
    │   └── push                              # Push notification authentication
    ├── protect                               # PingOne Protect fraud signals
    ├── recaptcha-enterprise                  # reCAPTCHA Enterprise integration
    └── samples                               # Sample applications
        └── pingsampleapp                     # Combined Ping sample app

# License

This project is licensed under the MIT License - see the [LICENSE](./LICENSE) file for details