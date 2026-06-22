# iOS Module Extraction: `etsi-1196x2-ios`

## Background

The `consultation` and `119602-consultation` modules originally contained all iOS-specific code: the Security.framework bridge (`PKIXBridge` cinterop), iOS certificate implementations, the Ktor Darwin HTTP engine, the Swift-facing `EudiwIosTrust` API, and the XCFramework production task. Android authors required this code to be separated so that the cross-platform modules contain no iOS-specific build machinery.

## Module structure after extraction

```
ios/                                          ← new Gradle module (:etsi-1196x2-ios)
├── build.gradle.kts
├── PKIXBridge/                               ← moved from repo root
│   ├── Sources/
│   ├── Tests/
│   ├── scripts/build-xcframework.sh
│   └── Package.swift
└── src/
    ├── nativeInterop/cinterop/
    │   └── PKIXBridge.def                    ← moved from consultation/
    └── iosMain/kotlin/
        ├── eu/europa/ec/eudi/etsi1196x2/consultation/
        │   ├── IosConversions.kt
        │   ├── ValidateCertificateChainUsingPKIXIos.kt
        │   ├── ValidateCertificateChainUsingDirectTrustIos.kt
        │   └── certs/
        │       ├── CertificateOperationsIos.kt
        │       └── CertificateProfileValidatorIos.kt
        └── eu/europa/ec/eudi/etsi119602/consultation/
            ├── EudiwIosTrust.kt
            ├── InsecureAcceptAllJwtSignature.kt
            ├── IosLoTEHttpClient.kt
            └── ProvisionTrustAnchorsFromLoTEsIos.kt
```

## Dependency graph

`:etsi-1196x2-ios` is the **umbrella module** that produces `EudiEtsi1196x2.xcframework` for Swift Package Manager. It depends on all three cross-platform modules and re-exports their public API so Swift sees a single unified module.

```
etsi-1196x2-ios  (iOS-only; produces XCFramework)
  └─ api: etsi-119602-consultation  (KMP: JVM + Android + iOS, empty iosMain)
       └─ api: etsi-1196x2-consultation  (KMP: JVM + Android + iOS, minimal iosMain)
            └─ api: etsi-119602-data-model  (pure Kotlin)
```

## What moved where

### Source files

| File | From | To |
|------|------|----|
| `IosConversions.kt` | `consultation/iosMain/` | `ios/iosMain/` |
| `ValidateCertificateChainUsingPKIXIos.kt` | `consultation/iosMain/` | `ios/iosMain/` |
| `ValidateCertificateChainUsingDirectTrustIos.kt` | `consultation/iosMain/` | `ios/iosMain/` |
| `certs/CertificateOperationsIos.kt` | `consultation/iosMain/` | `ios/iosMain/` |
| `certs/CertificateProfileValidatorIos.kt` | `consultation/iosMain/` | `ios/iosMain/` |
| `EudiwIosTrust.kt` | `119602-consultation/iosMain/` | `ios/iosMain/` |
| `InsecureAcceptAllJwtSignature.kt` | `119602-consultation/iosMain/` | `ios/iosMain/` |
| `IosLoTEHttpClient.kt` | `119602-consultation/iosMain/` | `ios/iosMain/` |
| `ProvisionTrustAnchorsFromLoTEsIos.kt` | `119602-consultation/iosMain/` | `ios/iosMain/` |
| `PKIXBridge.def` | `consultation/nativeInterop/cinterop/` | `ios/nativeInterop/cinterop/` |

### Directory

| Directory | From | To |
|-----------|------|----|
| `PKIXBridge/` (Swift package) | repo root | `ios/PKIXBridge/` |

`PKIXBridge` is only ever consumed by `:etsi-1196x2-ios`, so co-locating it inside the module makes `ios/` fully self-contained.

### Build machinery

| Item | From | To |
|------|------|-----|
| `buildPKIXBridge` Gradle task | `consultation/build.gradle.kts` | `ios/build.gradle.kts` |
| PKIXBridge cinterop declarations | `consultation/build.gradle.kts` | `ios/build.gradle.kts` |
| Swift toolchain linker helpers + opts | `consultation/build.gradle.kts` | `ios/build.gradle.kts` |
| `CInteropProcess` input tracking | `consultation/build.gradle.kts` | `ios/build.gradle.kts` |
| `XCFramework("EudiEtsi1196x2")` declaration | `119602-consultation/build.gradle.kts` | `ios/build.gradle.kts` |
| Framework export + `umbrella.add(this)` | `119602-consultation/build.gradle.kts` | `ios/build.gradle.kts` |
| Ktor Darwin `iosMain` dependency | `119602-consultation/build.gradle.kts` | `ios/build.gradle.kts` |
| `KotlinNativeSimulatorTest` configuration | both modules | `ios/build.gradle.kts` |

### Supporting files

| File | Change |
|------|--------|
| `settings.gradle.kts` | Added `include(":etsi-1196x2-ios")` and `project(":etsi-1196x2-ios").projectDir = file("ios")` |
| `.github/workflows/xcframework-spm-release.yml` | `GRADLE_PROJECT` → `etsi-1196x2-ios`, `OUTPUT_DIR` → `ios/build/XCFrameworks/release` |
| `eudi-lib-ios-etsi-1196x2/scripts/refresh-xcframework.sh` | Gradle task → `:etsi-1196x2-ios:assembleEudiEtsi1196x2ReleaseXCFramework`, source path → `ios/build/XCFrameworks/release/` |

## What stayed and why

### `ConsultationPlatformIos.kt` remains in `consultation/iosMain/`

KMP requires that an `expect` declaration and all its `actual` implementations live in the **same Gradle module**. The `expect fun consultationPlatform()` is declared in `consultation/commonMain`; its iOS actual must therefore remain in `consultation/iosMain`. The file is a single object returning `Dispatchers.Default` and contains no Security.framework references.

### iOS target declarations remain in `consultation` and `119602-consultation`

KMP requires every module in the dependency chain to declare the same targets as its dependents. Since `:etsi-1196x2-ios` has iOS targets, both `consultation` and `119602-consultation` must also declare `iosArm64`, `iosX64`, and `iosSimulatorArm64` — even though their `iosMain` source sets are now empty (or contain only the one `actual` file).

## Build tasks

| Purpose | Command |
|---------|---------|
| Build XCFramework | `./gradlew :etsi-1196x2-ios:assembleEudiEtsi1196x2ReleaseXCFramework` |
| Refresh local dev XCFramework | `eudi-lib-ios-etsi-1196x2/scripts/refresh-xcframework.sh` |
| Verify JVM compilation unaffected | `./gradlew :etsi-1196x2-consultation:compileKotlinJvm :etsi-119602-consultation:compileKotlinJvm` |
