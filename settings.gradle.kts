/*
 * Copyright (c) 2024 - 2025 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        //mavenLocal()
        maven(url = "https://central.sonatype.com/repository/maven-snapshots/")
    }
}

rootProject.name = "ping"

include(":davinci")
include(":foundation")
include(":foundation:android")
include(":foundation:davinci-plugin")
include(":foundation:device")
include(":foundation:device:device-profile")
//include(":foundation:device:device-binding")
include(":foundation:device:device-id")
include(":foundation:device:device-root")
include(":foundation:device:device-client")
//include(":foundation:device:device-integrity")
//include(":foundation:fido")
include(":foundation:journey-plugin")
include(":foundation:logger")
include(":foundation:network")
include(":foundation:migration")
include(":foundation:oidc")
include(":foundation:orchestrate")
include(":foundation:storage")
include(":foundation:utils")
include(":foundation:browser")
include(":journey")
include(":mfa")
include(":mfa:commons")
include(":mfa:oath")
include(":mfa:push")
include(":protect")
include(":external-idp")
//include(":verify")
include(":recaptcha-enterprise")
//include(":wallet")
include(":foundation:testrail")

include(":mfa")
include(":mfa:fido")

include(":samples:app")
include(":samples:journeyapp")
include(":mfa:binding")
include(":mfa:binding-ui")
include(":mfa:binding-migration")
include(":samples:authenticatorapp")
include(":pingonemfa")
include(":samples:pingsampleapp")
include(":mfa:auth-migration")
