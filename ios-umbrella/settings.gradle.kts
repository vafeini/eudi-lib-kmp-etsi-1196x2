//
// Standalone Gradle project for umbrella xcframework build.
// Resolves KMP consultation modules from Maven instead of project refs.
// macOS only — invoke manually or via CI: ./gradlew :etsi-1196x2-ios:kmmBridgePublish
//

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        mavenLocal()      // Check local first: locally published snapshots for dev/CI staging
        mavenCentral()    // Then public Maven Central for released versions
        google()          // Android-specific artifacts
    }
}

rootProject.name = "eudi-lib-kmp-etsi-1196x2"
//include(":etsi-1196x2-ios")
//project(":etsi-1196x2-ios").projectDir = file(".")
