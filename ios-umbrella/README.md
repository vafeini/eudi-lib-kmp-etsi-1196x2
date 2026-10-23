# iOS Umbrella Framework: Comprehensive Guide
## XCFramework Build, Release, and SPM Distribution

**Last Updated:** October 22, 2026  
**Project:** EUDI ETSI 1196x2 Library (eudi-lib-kmp-etsi-1196x2)  
**Scope:** ios-umbrella folder purpose, release workflow, and SPM distribution

---

## Executive Summary

This guide consolidates complete documentation for the iOS umbrella framework ecosystem:

1. **ios-umbrella folder** is a **standalone Gradle project** that builds a static XCFramework for iOS containing three Kotlin Toolchain modules
2. **Release workflow** (via GitHub Actions) automates XCFramework creation, Package.swift generation, and SPM distribution
3. **SPM distribution** allows Swift developers to consume the framework via `swift package add` or Xcode's Package Manager UI

**Key Facts:**
- Built on **macOS only** (iOS framework compilation requires macOS)
- Produces a **static framework** with PKIXBridge merged in
- Distributed as `.xcframework.zip` via GitHub Releases
- Consumed via **Package.swift** manifest in Swift Package Manager

---

# SECTION 1: iOS Umbrella Folder Architecture and Purpose

## 1.1 What is ios-umbrella?

The `` directory is a **standalone Gradle project** (not part of the main Kotlin Toolchain build) that creates a static iOS framework aggregating three Kotlin Toolchain modules:

| Module | Purpose | Included |
|--------|---------|----------|
| **consultation** | Core abstractions for certificate chain validation (PKIX logic) | Transitive |
| **119602-data-model** | ETSI TS 119 602 Lists of Trusted Entities (LoTE) data model | Transitive |
| **consultation119602** | Bridges consultation + data-model; implements LoTE-based validation (primary public API) | Direct dependency |

**Output:** Single static framework named `EudiEtsi1196x2` for iOS, combining all three modules and PKIXBridge (Swift certificate validation wrapper).

### Architecture: Static Framework Model

```
┌─────────────────────────────────────────┐
│  Static Framework: EudiEtsi1196x2       │
├─────────────────────────────────────────┤
│ • Kotlin/Native runtime                 │
│ • consultation (API logic)              │
│ • consultation119602 (core)             │
│ • 119602-data-model (LoTE models)       │
│ • PKIXBridge (Swift SecTrust wrapper)   │
│                                         │
│ Build-time linking → No runtime issues │
│ Self-contained → Single binary          │
└─────────────────────────────────────────┘
```

### Why Static Framework?

- **Build-time linking:** Consumers link at compile time, not runtime
- **No runtime embedding:** Avoids duplicate Kotlin runtimes and dyld ordering issues
- **Self-contained:** PKIXBridge merged in via `libtool` post-link step
- **Distribution:** Ready for SPM (Swift Package Manager) consumption

---

## 1.2 Relationship to Main Build System

The parent repository has **migrated to Kotlin Toolchain** (away from Gradle):

```
Parent Repository Structure:
├── Kotlin Toolchain Build (./)
│   ├── module.yaml → consultation119602 module
│   ├── module.yaml → consultation module
│   ├── module.yaml → 119602-data-model module
│   └── ./kotlin build    [builds all modules]
│
└── ios-umbrella/ (Standalone Gradle)
    ├── build.gradle.kts
    ├── settings.gradle.kts
    └── ./gradlew build   [consumes Maven artifacts]
```

### Key Separation

- **Kotlin Toolchain:** Builds JVM, Android, iOS klibs and publishes to Maven
- **ios-umbrella Gradle:** Consumes Maven artifacts, links iOS frameworks, creates XCFramework

**Why separate?** The umbrella Gradle project needs a **final linking step** with PKIXBridge that's specific to iOS distribution. It's easier to keep this as a standalone Gradle project than integrate it into the main Kotlin Toolchain build.

---

## 1.3 Dependency Resolution: Maven Model

The umbrella Gradle project resolves dependencies as **Maven artifacts** rather than project references:

```kotlin
// settings.gradle.kts repository order:
repositories {
    mavenLocal()      // 1st: Local Maven cache (~/.m2/repository)
    mavenCentral()    // 2nd: Maven Central (public internet)
    google()          // 3rd: Android-specific artifacts
}
```

**In build.gradle.kts:**
```kotlin
api("eu.europa.ec.eudi:etsi-119602-consultation:${findProperty("libraryVersion") ?: "0.4.0"}")
```

**Resolution Order:**
1. **Local Maven** is checked first (dev/CI snapshots take precedence)
2. **Maven Central** is checked next (released versions)
3. **Google** repository for Android artifacts

This ensures that locally published snapshots (from `./kotlin publish -m * mavenLocal`) are used during development.

---

## 1.4 Build Outputs and Structure

### Step-by-Step Build

**Prerequisites:**
1. Kotlin Toolchain modules published to mavenLocal
2. PKIXBridge.xcframework available at `PKIXBridge/PKIXBridge.xcframework`

**Command:**
```bash
cd ios-umbrella
./gradlew build
```

**Output: Three Separate Framework Bundles**

```
ios-umbrella/build/bin/
├── iosArm64/releaseFramework/
│   └── EudiEtsi1196x2.framework/      [Device: arm64]
├── iosX64/releaseFramework/
│   └── EudiEtsi1196x2.framework/      [Simulator: x86_64]
└── iosSimulatorArm64/releaseFramework/
    └── EudiEtsi1196x2.framework/      [Simulator: arm64]
```

Each `.framework` is:
- ✅ **Static** (Kotlin runtime bundled)
- ✅ **Self-contained** (PKIXBridge merged)
- ✅ **Platform-specific** (one binary per architecture)

### Important: Not an XCFramework

The `./gradlew build` command produces **three separate `.framework` bundles**, NOT a combined `.xcframework`.

- **`.framework`** = single-architecture binary
- **`.xcframework`** = multi-architecture bundle (combines all three)

To create an XCFramework (required for SPM distribution), manually combine them:

```bash
xcrun xcodebuild -create-xcframework \
  -framework build/bin/iosArm64/releaseFramework/EudiEtsi1196x2.framework \
  -framework build/bin/iosX64/releaseFramework/EudiEtsi1196x2.framework \
  -framework build/bin/iosSimulatorArm64/releaseFramework/EudiEtsi1196x2.framework \
  -output build/EudiEtsi1196x2.xcframework
```

**Result:** `ios-umbrella/build/EudiEtsi1196x2.xcframework` (ready for SPM)

---

## 1.5 PKIXBridge Integration

### What is PKIXBridge?

**PKIXBridge** is a Swift framework wrapping Apple's `SecTrust` API for PKIX certificate chain validation.

- **Why?** Kotlin/Native's iOS interop can't directly access certain `SecTrust` internals
- **Built separately:** Via Kotlin Toolchain plugin (`plugins/pkixbridge-xcframework`)
- **Used in umbrella:** Merged into the final static framework

### Build and Integration Flow

```
┌────────────────────────────────────────────┐
│ Kotlin Toolchain: Build PKIXBridge         │
│ ./kotlin do buildXcframework               │
└────────────────────────────────────────────┘
              ↓
┌────────────────────────────────────────────┐
│ Output: PKIXBridge.xcframework             │
│ plugins/pkixbridge-xcframework/build/      │
└────────────────────────────────────────────┘
              ↓
┌────────────────────────────────────────────┐
│ Copy to umbrella project:                  │
│ ios-umbrella/PKIXBridge/                   │
│   └── PKIXBridge.xcframework/              │
└────────────────────────────────────────────┘
              ↓
┌────────────────────────────────────────────┐
│ ios-umbrella build process:                │
│ ./gradlew build                            │
│ → Extracts PKIXBridge binary               │
│ → Merges into each .framework              │
│ → Final: EudiEtsi1196x2.framework with     │
│   PKIXBridge embedded (via libtool)        │
└────────────────────────────────────────────┘
```

### Consumer Perspective

SPM consumers **don't need to link PKIXBridge separately** because:
- It's already embedded in the static framework
- SPM treats `EudiEtsi1196x2` as a single, self-contained binary

---

## 1.6 Version Management

### Single Source of Truth

All three modules share one version declared in **Kotlin Toolchain root `gradle.properties`**:

```properties
version=0.4.0-alpha.5-SNAPSHOT
```

### In ios-umbrella Build

```bash
# Extract version from gradle.properties
VERSION=$(cat ../gradle.properties | grep '^version=' | cut -d= -f2)

# Build with specific version
cd ios-umbrella
./gradlew build -PlibraryVersion=$VERSION
```

**Default version** (if not specified):
```kotlin
// From build.gradle.kts
findProperty("libraryVersion") ?: "0.4.0"
```

### Version Propagation

1. **Kotlin Toolchain**: Publishes modules with version `0.4.0-alpha.5-SNAPSHOT`
2. **ios-umbrella**: Consumes `api("...etsi-119602-consultation:$VERSION")`
3. **Release workflow**: Extracts version, builds frameworks, publishes as SPM release

---

## 1.7 Gradle Commands Reference

### Build All Frameworks

```bash
cd ios-umbrella
./gradlew build
```

**Output:** Three `.framework` bundles in `build/bin/{target}/releaseFramework/`

### Build Specific Target

```bash
./gradlew linkReleaseFrameworkIosArm64       # Device (arm64)
./gradlew linkReleaseFrameworkIosX64         # Simulator (x86_64)
./gradlew linkReleaseFrameworkIosSimulatorArm64  # Simulator (arm64)
```

### Build with Specific Version

```bash
./gradlew build -PlibraryVersion=0.4.0-alpha.5-SNAPSHOT
```

### Clean and Rebuild

```bash
./gradlew clean build
```

### Create XCFramework

```bash
xcrun xcodebuild -create-xcframework \
  -framework build/bin/iosArm64/releaseFramework/EudiEtsi1196x2.framework \
  -framework build/bin/iosX64/releaseFramework/EudiEtsi1196x2.framework \
  -framework build/bin/iosSimulatorArm64/releaseFramework/EudiEtsi1196x2.framework \
  -output build/EudiEtsi1196x2.xcframework
```

---

## 1.8 Troubleshooting

### "Could not find eu.europa.ec.eudi:etsi-119602-consultation:X.Y.Z"

**Cause:** Maven dependencies haven't been published to `mavenLocal()`.

**Fix:**
```bash
cd <root of eudi-lib-kmp-etsi-1196x2>
./kotlin publish -m consultation119602 mavenLocal
./kotlin publish -m consultation mavenLocal
./kotlin publish -m 119602-data-model mavenLocal
```

### "Could not find PKIXBridge.xcframework"

**Cause:** Pre-built PKIXBridge not at `PKIXBridge`.

**Fix (macOS only):**
```bash
./kotlin do buildXcframework
cp -r plugins/pkixbridge-xcframework/build/PKIXBridge.xcframework ios-umbrella/PKIXBridge/
```

### Frameworks Not Building (Windows/Linux)

**Cause:** iOS framework compilation only works on macOS.

**Fix:** Build on macOS. On non-macOS platforms, iOS tasks are automatically skipped.

### "Task 'linkReleaseFrameworkIosArm64' is disabled"

**Reason:** Running on non-macOS. iOS framework linking only works on macOS.

**Fix:** Build on macOS.

---

# SECTION 2: Complete Release Workflow

## 2.1 Release Process Overview

The release workflow automates the entire pipeline from Kotlin Toolchain build to SPM distribution:

```
┌─────────────────────────────────────────────────────────────────┐
│ RELEASE TRIGGER: Push version tag (v0.4.0)                     │
└─────────────────────────────────────────────────────────────────┘
         ↓ (GitHub Actions: release.yml)
┌─────────────────────────────────────────────────────────────────┐
│ STEP 1: Publish Kotlin Toolchain Modules to mavenLocal          │
│ ./kotlin publish -m * mavenLocal                                │
└─────────────────────────────────────────────────────────────────┘
         ↓
┌─────────────────────────────────────────────────────────────────┐
│ STEP 2: Build iOS Umbrella Frameworks                           │
│ cd ios-umbrella && ./gradlew build                              │
│ Outputs: 3 separate .framework bundles                          │
└─────────────────────────────────────────────────────────────────┘
         ↓
┌─────────────────────────────────────────────────────────────────┐
│ STEP 3: Create XCFramework                                      │
│ xcrun xcodebuild -create-xcframework ...                        │
│ Output: EudiEtsi1196x2.xcframework/                             │
└─────────────────────────────────────────────────────────────────┘
         ↓
┌─────────────────────────────────────────────────────────────────┐
│ STEP 4: Zip XCFramework                                         │
│ zip -r EudiEtsi1196x2.xcframework.zip                           │
└─────────────────────────────────────────────────────────────────┘
         ↓
┌─────────────────────────────────────────────────────────────────┐
│ STEP 5: Calculate SHA256 Checksum                               │
│ shasum -a 256 EudiEtsi1196x2.xcframework.zip                    │
└─────────────────────────────────────────────────────────────────┘
         ↓
┌─────────────────────────────────────────────────────────────────┐
│ STEP 6: Upload to GitHub Releases                               │
│ Create release asset: EudiEtsi1196x2.xcframework.zip            │
└─────────────────────────────────────────────────────────────────┘
         ↓
┌─────────────────────────────────────────────────────────────────┐
│ STEP 7: Generate Package.swift                                  │
│ Fill template with URL and checksum                             │
│ Commit to repository                                            │
└─────────────────────────────────────────────────────────────────┘
         ↓
┌─────────────────────────────────────────────────────────────────┐
│ RESULT: SPM Ready                                               │
│ Developers can: swift package add URL@version                   │
└─────────────────────────────────────────────────────────────────┘
```

---

## 2.2 Step 1: Publish Kotlin Modules to Maven Local

**Purpose:** Make modules available to ios-umbrella Gradle build

```bash
cd <root of eudi-lib-kmp-etsi-1196x2>

./kotlin publish -m 119602-data-model mavenLocal
./kotlin publish -m consultation mavenLocal
./kotlin publish -m consultation119602 mavenLocal
```

**Output Location:**
- **Linux/macOS:** `~/.m2/repository/eu/europa/ec/eudi/`
- **Windows:** `%USERPROFILE%\.m2\repository\eu\europa\ec\eudi\`

**Version:** Determined by `version=` in root `gradle.properties` (e.g., `0.4.0-alpha.5-SNAPSHOT`)

**In GitHub Actions:**
```yaml
- name: Publish modules to mavenLocal
  run: |
    ./kotlin publish -m 119602-data-model mavenLocal
    ./kotlin publish -m consultation mavenLocal
    ./kotlin publish -m consultation119602 mavenLocal
  env:
    KOTLIN_TOOLCHAIN_SIGNING_KEY: ${{ secrets.SIGNING_KEY }}
```

---

## 2.3 Step 2: Build iOS Umbrella Frameworks

**Purpose:** Create three platform-specific `.framework` bundles

```bash
cd ios-umbrella
./gradlew build -PlibraryVersion=$LIBRARY_VERSION
```

**What Happens:**
1. Gradle resolves `eu.europa.ec.eudi:etsi-119602-consultation` from mavenLocal
2. Kotlin/Native compiler compiles iOS klibs for each target
3. Three framework binaries are produced
4. PKIXBridge is merged into each framework via `libtool` post-link step

**Output Structure:**
```
ios-umbrella/build/bin/
├── iosArm64/
│   └── releaseFramework/
│       └── EudiEtsi1196x2.framework/
├── iosX64/
│   └── releaseFramework/
│       └── EudiEtsi1196x2.framework/
└── iosSimulatorArm64/
    └── releaseFramework/
        └── EudiEtsi1196x2.framework/
```

**In GitHub Actions:**
```yaml
- name: Build iOS frameworks
  run: |
    cd ios-umbrella
    ./gradlew build -PlibraryVersion=${{ env.LIBRARY_VERSION }}
```

---

## 2.4 Step 3: Create XCFramework

**Purpose:** Combine three `.framework` bundles into one multi-architecture `.xcframework`

```bash
cd ios-umbrella

xcrun xcodebuild -create-xcframework \
  -framework build/bin/iosArm64/releaseFramework/EudiEtsi1196x2.framework \
  -framework build/bin/iosX64/releaseFramework/EudiEtsi1196x2.framework \
  -framework build/bin/iosSimulatorArm64/releaseFramework/EudiEtsi1196x2.framework \
  -output build/EudiEtsi1196x2.xcframework
```

**What Gets Created:**
```
EudiEtsi1196x2.xcframework/
├── Info.plist                           # Metadata describing available slices
├── ios-arm64/
│   └── EudiEtsi1196x2.framework/        # Device binary (arm64)
└── ios-arm64_x86_64-simulator/
    └── EudiEtsi1196x2.framework/        # Simulator binary (arm64+x86_64)
```

**Key Point:** `Info.plist` tells Xcode/SPM which binary slices are available.

---

## 2.5 Step 4: Zip the XCFramework

**Purpose:** Create a single distributable archive for upload

```bash
cd ios-umbrella/build

zip -r EudiEtsi1196x2.xcframework.zip EudiEtsi1196x2.xcframework/
```

**Output:**
- File: `EudiEtsi1196x2.xcframework.zip` (~1-2 GB)
- Format: ZIP archive containing the entire `.xcframework` bundle
- Purpose: SPM requires `.xcframework.zip` files (not unzipped directories)

---

## 2.6 Step 5: Calculate SHA256 Checksum

**Purpose:** Generate integrity checksum for SPM verification

```bash
cd ios-umbrella/build

shasum -a 256 EudiEtsi1196x2.xcframework.zip
# Output: a3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3
```

**How It's Used:**
1. Stored in `Package.swift` as `checksum: "a3c4d5e6..."`
2. SPM calculates checksum of downloaded file
3. If checksums match → verified ✓
4. If different → download corrupted ✗

**Alternative Tools:**
```bash
# openssl
openssl dgst -sha256 EudiEtsi1196x2.xcframework.zip

# Swift Package Manager utility
swift package compute-checksum EudiEtsi1196x2.xcframework.zip

# Windows (PowerShell)
Get-FileHash -Path EudiEtsi1196x2.xcframework.zip -Algorithm SHA256 | Select-Object Hash
```

---

## 2.7 Step 6: Upload to GitHub Releases

**Purpose:** Make `.xcframework.zip` publicly downloadable

**Process:**
1. Create a GitHub Release for tag `v0.4.0`
2. Upload `EudiEtsi1196x2.xcframework.zip` as a release asset
3. Get the download URL from the release page

**URL Format:**
```
https://github.com/eu-digital-identity-wallet/eudi-lib-kmp-etsi-1196x2/releases/download/v0.4.0/EudiEtsi1196x2.xcframework.zip
```

**In GitHub Actions:**
```yaml
- name: Upload to GitHub Release
  uses: softprops/action-gh-release@v1
  with:
    files: ${{ steps.build.outputs.xcframework-zip }}
    draft: false
    prerelease: false
```

---

## 2.8 Step 7: Generate and Commit Package.swift

**Purpose:** Create SPM manifest with binary metadata

### What is Package.swift?

`Package.swift` is the **manifest file** that tells SPM:
- Package name: `EudiEtsi1196x2`
- Supported platforms: iOS, macOS, tvOS, watchOS
- Where to download binary: GitHub Releases URL
- How to verify integrity: SHA256 checksum

### Template Structure

The repository already has a **kmmbridge template** at `/Package.swift`:

```swift
// swift-tools-version:5.9
import PackageDescription

// BEGIN KMMBRIDGE VARIABLES BLOCK (do not edit)
let remoteKotlinUrl = ""              // ← To be filled with URL
let remoteKotlinChecksum = ""         // ← To be filled with SHA256
let packageName = ""                  // ← To be filled with "EudiEtsi1196x2"
// END KMMBRIDGE BLOCK

let package = Package(
    name: packageName,
    platforms: [
        $platforms                    // ← Placeholder (needs actual values)
    ],
    products: [
        .library(name: packageName, targets: [packageName]),
    ],
    targets: [
        .binaryTarget(
            name: packageName,
            url: remoteKotlinUrl,
            checksum: remoteKotlinChecksum
        )
    ]
)
```

### Completed Package.swift

After filling in the template:

```swift
// swift-tools-version:5.9
import PackageDescription

// BEGIN KMMBRIDGE VARIABLES BLOCK (do not edit)
let remoteKotlinUrl = "https://github.com/eu-digital-identity-wallet/eudi-lib-kmp-etsi-1196x2/releases/download/v0.4.0/EudiEtsi1196x2.xcframework.zip"
let remoteKotlinChecksum = "a3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3"
let packageName = "EudiEtsi1196x2"
// END KMMBRIDGE BLOCK

let package = Package(
    name: packageName,
    
    platforms: [
        .iOS(.v16),
        .macOS(.v13),
        .tvOS(.v16),
        .watchOS(.v9)
    ],
    
    products: [
        .library(
            name: packageName,
            type: .static,
            targets: [packageName]
        ),
    ],
    
    targets: [
        .binaryTarget(
            name: packageName,
            url: remoteKotlinUrl,
            checksum: remoteKotlinChecksum
        )
    ]
)
```

### Generation Script

Create `.github/scripts/generate-package-swift.sh`:

```bash
#!/bin/bash
set -e

# Configuration
FRAMEWORK_NAME="EudiEtsi1196x2"
VERSION="$1"
GITHUB_REPO="$2"
BUILD_DIR="ios-umbrella/build"
TEMP_DIR=$(mktemp -d)

echo "Generating Package.swift for version $VERSION..."

# 1. Create XCFramework
xcrun xcodebuild -create-xcframework \
  -framework "$BUILD_DIR/bin/iosArm64/releaseFramework/$FRAMEWORK_NAME.framework" \
  -framework "$BUILD_DIR/bin/iosX64/releaseFramework/$FRAMEWORK_NAME.framework" \
  -framework "$BUILD_DIR/bin/iosSimulatorArm64/releaseFramework/$FRAMEWORK_NAME.framework" \
  -output "$TEMP_DIR/$FRAMEWORK_NAME.xcframework"

# 2. Zip XCFramework
cd "$TEMP_DIR"
zip -r "$FRAMEWORK_NAME.xcframework.zip" "$FRAMEWORK_NAME.xcframework/"

# 3. Calculate checksum
CHECKSUM=$(shasum -a 256 "$FRAMEWORK_NAME.xcframework.zip" | awk '{print $1}')

# 4. Build download URL
DOWNLOAD_URL="https://github.com/$GITHUB_REPO/releases/download/$VERSION/$FRAMEWORK_NAME.xcframework.zip"

# 5. Generate Package.swift content
PACKAGE_SWIFT_CONTENT=$(cat <<EOF
// swift-tools-version:5.9
import PackageDescription

// BEGIN KMMBRIDGE VARIABLES BLOCK (do not edit)
let remoteKotlinUrl = "$DOWNLOAD_URL"
let remoteKotlinChecksum = "$CHECKSUM"
let packageName = "$FRAMEWORK_NAME"
// END KMMBRIDGE BLOCK

let package = Package(
    name: packageName,
    platforms: [
        .iOS(.v16),
        .macOS(.v13),
        .tvOS(.v16),
        .watchOS(.v9)
    ],
    products: [
        .library(
            name: packageName,
            type: .static,
            targets: [packageName]
        ),
    ],
    targets: [
        .binaryTarget(
            name: packageName,
            url: remoteKotlinUrl,
            checksum: remoteKotlinChecksum
        )
    ]
)
EOF
)

# 6. Write to repository root Package.swift
echo "$PACKAGE_SWIFT_CONTENT" > "$GITHUB_WORKSPACE/Package.swift"
echo "Package.swift generated successfully"

# 7. Output for CI/CD
echo "xcframework-path=$TEMP_DIR/$FRAMEWORK_NAME.xcframework.zip" >> $GITHUB_OUTPUT
echo "checksum=$CHECKSUM" >> $GITHUB_OUTPUT

cd - > /dev/null
```

### In GitHub Actions

```yaml
- name: Generate Package.swift
  id: package
  run: |
    chmod +x .github/scripts/generate-package-swift.sh
    .github/scripts/generate-package-swift.sh \
      "${{ github.ref_name }}" \
      "${{ github.repository }}"

- name: Commit Package.swift
  run: |
    git config user.name "CI Bot"
    git config user.email "ci@example.com"
    git add Package.swift
    git commit -m "chore: update Package.swift for ${{ github.ref_name }}"
    git push
```

---

## 2.9 Complete Release Workflow (GitHub Actions Example)

**File:** `../.github/workflows/release.yml`

```yaml
name: Release iOS XCFramework

on:
  push:
    tags:
      - 'v*'

jobs:
  release:
    runs-on: macos-latest
    steps:
      - uses: actions/checkout@v4
      
      # Extract version from tag
      - name: Extract version
        id: version
        run: |
          VERSION=${GITHUB_REF#refs/tags/}
          echo "LIBRARY_VERSION=${VERSION#v}" >> $GITHUB_ENV
          echo "ref-name=$VERSION" >> $GITHUB_OUTPUT
      
      # Setup Kotlin Toolchain
      - name: Setup Kotlin Toolchain
        run: |
          java -version
          ./kotlin --version
      
      # Publish Kotlin modules
      - name: Publish modules to mavenLocal
        run: |
          ./kotlin publish -m 119602-data-model mavenLocal
          ./kotlin publish -m consultation mavenLocal
          ./kotlin publish -m consultation119602 mavenLocal
        env:
          KOTLIN_TOOLCHAIN_SIGNING_KEY: ${{ secrets.SIGNING_KEY }}
      
      # Build iOS frameworks
      - name: Build iOS frameworks
        run: |
          cd ios-umbrella
          ./gradlew build -PlibraryVersion=${{ env.LIBRARY_VERSION }}
      
      # Create XCFramework
      - name: Create XCFramework
        run: |
          cd ios-umbrella
          xcrun xcodebuild -create-xcframework \
            -framework build/bin/iosArm64/releaseFramework/EudiEtsi1196x2.framework \
            -framework build/bin/iosX64/releaseFramework/EudiEtsi1196x2.framework \
            -framework build/bin/iosSimulatorArm64/releaseFramework/EudiEtsi1196x2.framework \
            -output build/EudiEtsi1196x2.xcframework
      
      # Zip XCFramework
      - name: Zip XCFramework
        run: |
          cd ios-umbrella/build
          zip -r EudiEtsi1196x2.xcframework.zip EudiEtsi1196x2.xcframework/
          echo "XCFRAMEWORK_ZIP=$(pwd)/EudiEtsi1196x2.xcframework.zip" >> $GITHUB_ENV
      
      # Upload to GitHub Release
      - name: Upload to GitHub Release
        uses: softprops/action-gh-release@v1
        with:
          files: ${{ env.XCFRAMEWORK_ZIP }}
          draft: false
          prerelease: false
      
      # Generate Package.swift
      - name: Generate Package.swift
        run: |
          chmod +x .github/scripts/generate-package-swift.sh
          .github/scripts/generate-package-swift.sh \
            "${{ steps.version.outputs.ref-name }}" \
            "${{ github.repository }}"
      
      # Commit updated Package.swift
      - name: Commit Package.swift
        run: |
          git config user.name "CI Bot"
          git config user.email "ci@example.com"
          git add Package.swift
          git commit -m "chore: update Package.swift for ${{ steps.version.outputs.ref-name }}"
          git push origin main
```

---

## 2.10 Release Checklist

### Pre-Release
- [ ] Ensure version is bumped in `gradle.properties` (e.g., `0.4.0`)
- [ ] Kotlin Toolchain build passes: `./kotlin build`
- [ ] iOS umbrella build passes: `cd ios-umbrella && ./gradlew build`
- [ ] All tests pass: `./kotlin test`

### Release Day (Manual Option)
- [ ] Publish modules: `./kotlin publish -m * mavenLocal`
- [ ] Build frameworks: `cd ios-umbrella && ./gradlew build`
- [ ] Create XCFramework: `xcrun xcodebuild -create-xcframework ...`
- [ ] Zip: `zip -r EudiEtsi1196x2.xcframework.zip ...`
- [ ] Calculate checksum: `shasum -a 256 ...`
- [ ] Upload to GitHub Releases
- [ ] Update Package.swift with URL and checksum
- [ ] Commit changes: `git add Package.swift && git commit -m "v0.4.0 release"`
- [ ] Tag release: `git tag v0.4.0`
- [ ] Push: `git push origin main v0.4.0`

### Release Day (Automated - Recommended)
- [ ] Push version tag to main: `git tag v0.4.0 && git push origin v0.4.0`
- [ ] GitHub Actions workflow automatically:
  - [ ] Builds and publishes modules
  - [ ] Creates XCFramework
  - [ ] Generates Package.swift
  - [ ] Uploads to GitHub Releases
  - [ ] Commits Package.swift
- [ ] Verify release on GitHub Releases page

---

# SECTION 3: SPM Distribution and Consumer Usage

## 3.1 What is Swift Package Manager (SPM)?

**Swift Package Manager** is Apple's official package management system for Swift.

**Capabilities:**
- Download binary frameworks (`.xcframework`) from URLs
- Verify integrity via checksums
- Resolve platform requirements
- Automatically link appropriate slices (device or simulator)
- Integrate seamlessly into Xcode projects

**How it works:**
1. Developer adds package URL to Xcode
2. SPM fetches `Package.swift` from repository
3. SPM reads binary metadata (URL, checksum)
4. SPM downloads and caches `.xcframework.zip`
5. SPM verifies checksum
6. Xcode links the framework automatically

---

## 3.2 Package.swift Manifest Structure

### Complete Example for EudiEtsi1196x2

```swift
// swift-tools-version:5.9  ← Minimum SPM version required
import PackageDescription

// BEGIN KMMBRIDGE VARIABLES BLOCK (do not edit)
let remoteKotlinUrl = "https://github.com/eu-digital-identity-wallet/eudi-lib-kmp-etsi-1196x2/releases/download/v0.4.0/EudiEtsi1196x2.xcframework.zip"
let remoteKotlinChecksum = "a3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3"
let packageName = "EudiEtsi1196x2"
// END KMMBRIDGE BLOCK

let package = Package(
    // Package name (used internally by SPM)
    name: packageName,
    
    // Platform requirements
    platforms: [
        .iOS(.v16),              // Minimum iOS 16
        .macOS(.v13),            // Minimum macOS 13
        .tvOS(.v16),             // Minimum tvOS 16
        .watchOS(.v9)            // Minimum watchOS 9
    ],
    
    // Products: What consumers import
    products: [
        .library(
            name: packageName,           // `import EudiEtsi1196x2`
            type: .static,               // Static linking
            targets: [packageName]       // Maps to target below
        ),
    ],
    
    // Targets: Where the binary comes from
    targets: [
        .binaryTarget(
            name: packageName,
            url: remoteKotlinUrl,        // Download URL
            checksum: remoteKotlinChecksum // SHA256 for verification
        )
    ]
)
```

### Required Metadata

| Field | Required | Purpose |
|-------|----------|---------|
| `swift-tools-version` | ✅ | Tells SPM which API version to use |
| `name` | ✅ | Package identifier |
| `platforms` | ⚠️ Recommended | Deployment target requirements |
| `products` | ✅ | What developers can import |
| `targets` | ✅ | Implementation locations |
| `url` (binary) | ✅ | Where to download `.xcframework.zip` |
| `checksum` | ✅ | SHA256 of zipped file |

---

## 3.3 XCFramework Bundle Internal Structure

### What's Inside the .xcframework Bundle

```
EudiEtsi1196x2.xcframework/
│
├── Info.plist
│   └── Declares available slices and platforms
│
├── ios-arm64/                                    [DEVICE SLICE]
│   └── EudiEtsi1196x2.framework/
│       ├── EudiEtsi1196x2                 (binary - arm64 only)
│       ├── Headers/
│       │   └── EudiEtsi1196x2.h          (umbrella header)
│       ├── Modules/
│       │   └── module.modulemap          (Clang module definition)
│       └── Info.plist
│
└── ios-arm64_x86_64-simulator/              [SIMULATOR SLICE]
    └── EudiEtsi1196x2.framework/
        ├── EudiEtsi1196x2                 (binary - arm64+x86_64)
        ├── Headers/
        │   └── EudiEtsi1196x2.h
        ├── Modules/
        │   └── module.modulemap
        └── Info.plist
```

### Slice Selection by Xcode

```
┌──────────────────────┐
│ Build Configuration  │
└──────────────────────┘
         ↓
    ┌────┴────┐
    ↓         ↓
┌─────────┐ ┌──────────┐
│ Device  │ │Simulator │
└─────────┘ └──────────┘
    ↓           ↓
   arm64   arm64+x86_64
```

- **Device build:** Links `ios-arm64/EudiEtsi1196x2.framework/EudiEtsi1196x2` (arm64)
- **Simulator build:** Links `ios-arm64_x86_64-simulator/EudiEtsi1196x2.framework/EudiEtsi1196x2` (arm64+x86_64)

Xcode automatically chooses the correct slice based on build configuration.

---

## 3.4 SPM Consumption Flow

### Step-by-Step: Adding to Xcode Project

**In Xcode:**

1. **Open project** in Xcode
2. **File** → **Add Packages**
3. **Enter repository URL:**
   ```
   https://github.com/eu-digital-identity-wallet/eudi-lib-kmp-etsi-1196x2.git
   ```
4. **Select version:** "Exact Version" → `v0.4.0`
5. **Choose target** to add package to
6. **Click "Add Package"**

**What SPM Does Automatically:**

```
1. Fetch Package.swift from v0.4.0 tag commit
         ↓
2. Parse binary target metadata
   - URL: https://github.com/.../releases/download/v0.4.0/...
   - Checksum: a3c4d5e6f7a8b9c0d1e2f3a4...
         ↓
3. Download .xcframework.zip
   Location: ~/.../Library/Developer/Xcode/DerivedData/.../artifacts/
         ↓
4. Calculate SHA256 checksum of downloaded file
         ↓
5. Verify checksum matches Package.swift value
   ✓ Match = Continue
   ✗ Mismatch = Reject (corrupted download)
         ↓
6. Extract .xcframework.zip to cache directory
         ↓
7. Register with Xcode build system
         ↓
8. Link appropriate slice at build time
   Device: arm64 binary
   Simulator: arm64+x86_64 binary
```

---

## 3.5 Swift Code Usage

### Basic Import and Usage

```swift
import EudiEtsi1196x2

// Use Kotlin-compiled classes
let validator = EudiEtsi1196x2.SomeValidator()
let result = validator.validate(certificate: cert, list: trustedList)
```

### Package Integration in Xcode

**In `Package.swift` (if building another package):**

```swift
let package = Package(
    name: "MyApp",
    platforms: [.iOS(.v16)],
    products: [
        .library(name: "MyApp", targets: ["MyApp"]),
    ],
    dependencies: [
        .package(
            url: "https://github.com/eu-digital-identity-wallet/eudi-lib-kmp-etsi-1196x2.git",
            from: "0.4.0"
        )
    ],
    targets: [
        .target(
            name: "MyApp",
            dependencies: [.product(name: "EudiEtsi1196x2", package: "eudi-lib-kmp-etsi-1196x2")]
        ),
    ]
)
```

**In `MyApp.swift`:**

```swift
import EudiEtsi1196x2

public class MyValidator {
    public func validateCertificate(_ cert: Data) throws -> Bool {
        // Use framework
        let eudiValidator = EudiEtsi1196x2.Validator()
        return try eudiValidator.validate(cert)
    }
}
```

---

## 3.6 Platform Requirements and Compatibility

### Supported Platforms

The Package.swift declares:

```swift
platforms: [
    .iOS(.v16),          // iOS 16 and later
    .macOS(.v13),        // macOS 13 and later
    .tvOS(.v16),         // tvOS 16 and later
    .watchOS(.v9)        // watchOS 9 and later
]
```

### Consumer Compatibility

SPM will **reject** projects with lower deployment targets. For example:

| Consumer Target | iOS 15.0 | iOS 16.0 | iOS 17.0 |
|---|---|---|---|
| Can use framework? | ❌ No | ✅ Yes | ✅ Yes |
| Error if attempted | "Minimum deployment target mismatch" | Success | Success |

### Architecture Support

| Build Target | Supported Architectures |
|---|---|
| **iOS Device** | arm64 |
| **iOS Simulator (Apple Silicon Mac)** | arm64 |
| **iOS Simulator (Intel Mac)** | x86_64 |

**Note:** The combined `ios-arm64_x86_64-simulator` slice supports both Intel and Apple Silicon simulators on their respective machines.

---

## 3.7 Checksum Verification

### How SPM Verifies Integrity

**On Consumer Machine:**

```bash
1. Download EudiEtsi1196x2.xcframework.zip
         ↓
2. Calculate SHA256 of downloaded file
   $ shasum -a 256 downloaded.zip
   → a3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3
         ↓
3. Compare with expected checksum from Package.swift
   Package says: a3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3
         ↓
4. Decision:
   ✓ Match → Cache and use
   ✗ Mismatch → Reject (print error)
```

### Common Checksum Errors

**Error:** `Checksum mismatch`

**Possible Causes:**
1. File corrupted during download (network issue)
2. URL redirected to different file
3. Server returned wrong file
4. Package.swift checksum is incorrect

**Fix:**
1. Delete cache: `rm -rf ~/Library/Developer/Xcode/DerivedData`
2. Re-add package in Xcode
3. If still fails, report issue to package maintainer

---

## 3.8 Version Management in SPM

### How SPM Resolves Versions

**Consumer specifies version constraint:**

```swift
.package(url: "https://...", from: "0.4.0")
```

**SPM resolution flow:**

```
1. Search for git tag matching version
   Look for: v0.4.0, 0.4.0
         ↓
2. Checkout tag commit
   git checkout v0.4.0
         ↓
3. Read Package.swift from that commit
   Contains: URL and checksum for v0.4.0 binary
         ↓
4. Download binary from that Package.swift
   Only that version's binary is downloaded
         ↓
5. Verify and integrate
```

### Version Constraints in SPM

```swift
// Exact version
.package(url: "...", exact: "0.4.0")

// Minimum version
.package(url: "...", from: "0.4.0")

// Version range
.package(url: "...", "0.4.0"..<"1.0.0")

// Up to next major
.package(url: "...", upToNextMajor: "0.4.0")

// Branch (use cautiously)
.package(url: "...", branch: "main")
```

### Release Version vs Development

**Recommended for production:** Use exact version from release tag

```swift
// Production: Use exact release
.package(url: "...", exact: "0.4.0")

// Not recommended for production: Floating version
.package(url: "...", from: "0.4.0")  // Could include breaking changes
```

---

## 3.9 Troubleshooting SPM Issues

### "Could not resolve package repository"

**Cause:** Incorrect repository URL or network issue

**Fix:**
```bash
# Verify URL is correct and accessible
git clone https://github.com/eu-digital-identity-wallet/eudi-lib-kmp-etsi-1196x2.git
```

### "Package.swift not found at version"

**Cause:** Tag doesn't exist or Package.swift wasn't committed to that tag

**Fix:**
```bash
# Check available tags
git tag -l

# Verify Package.swift is in the tag commit
git show v0.4.0:Package.swift
```

### "Checksum mismatch"

**Cause:** Downloaded file doesn't match expected checksum

**Fixes:**
1. Clear Xcode cache: `rm -rf ~/Library/Developer/Xcode/DerivedData`
2. Delete Package.resolved: `rm Package.resolved`
3. Re-add package in Xcode
4. Check if URL in Package.swift is correct

### "Minimum deployment target mismatch"

**Cause:** Project target iOS version < package minimum (iOS 16)

**Fix:** Update project to iOS 16+, or contact package maintainers for lower target support

### "Unknown platform"

**Cause:** Xcode doesn't recognize platform in Package.swift

**Fix:** Ensure platforms are valid:
```swift
// Valid
platforms: [.iOS(.v16), .macOS(.v13)]

// Invalid
platforms: ["iOS 16", "macOS 13"]  // ❌ Strings not allowed
```

---

## 3.10 Version and Checksum Update Cycle

### When to Update Package.swift

**Every Release:**

1. **New version released** (e.g., `v0.4.1`)
2. **GitHub creates release** with `.xcframework.zip` asset
3. **Checksum changes** (because binary changed)
4. **Package.swift must be updated:**
   ```swift
   let remoteKotlinUrl = "https://github.com/.../releases/download/v0.4.1/EudiEtsi1196x2.xcframework.zip"
   let remoteKotlinChecksum = "new_sha256_hash_here"
   ```
5. **Commit changes** to repository
6. **Create git tag** for release: `git tag v0.4.1`
7. **Push both:** `git push origin main v0.4.1`

### Checksum Always Changes When Binary Changes

**Important:** Never use the same checksum for different binaries.

```swift
// ❌ WRONG: Same checksum for different versions
// v0.4.0 binary
let remoteKotlinChecksum = "abc123..."

// v0.4.1 binary (DIFFERENT BINARY!)
let remoteKotlinChecksum = "abc123..."  // ❌ Should be different!

// ✅ CORRECT: Different checksum for each version
// v0.4.0
let remoteKotlinChecksum = "abc123..."

// v0.4.1
let remoteKotlinChecksum = "def456..."  // ✅ Recalculated for new binary
```

**SPM will detect mismatch and fail.**

---

## 3.11 Visual: Complete SPM Consumption Pipeline

```
┌──────────────────────────────────────────────────────────┐
│ STEP 1: Developer Adds Package in Xcode                 │
│                                                          │
│ File → Add Packages                                      │
│ Paste: https://github.com/.../eudi-lib-kmp-etsi-1196x2  │
│ Select: v0.4.0                                           │
└──────────────────────────────────────────────────────────┘
         ↓
┌──────────────────────────────────────────────────────────┐
│ STEP 2: SPM Fetches Package.swift from v0.4.0 Tag        │
│                                                          │
│ git clone URL                                            │
│ git checkout v0.4.0                                      │
│ cat Package.swift                                        │
└──────────────────────────────────────────────────────────┘
         ↓
┌──────────────────────────────────────────────────────────┐
│ STEP 3: SPM Downloads .xcframework.zip                   │
│                                                          │
│ wget https://github.com/.../releases/download/v0.4.0/...│
│ → Saves to: ~/.../SourcePackages/artifacts/...          │
└──────────────────────────────────────────────────────────┘
         ↓
┌──────────────────────────────────────────────────────────┐
│ STEP 4: SPM Verifies Checksum                            │
│                                                          │
│ shasum -a 256 downloaded.zip                             │
│ Compare with: Package.swift checksum value               │
│ ✓ Match → Continue                                       │
│ ✗ Mismatch → Error                                       │
└──────────────────────────────────────────────────────────┘
         ↓
┌──────────────────────────────────────────────────────────┐
│ STEP 5: SPM Extracts .xcframework                        │
│                                                          │
│ unzip EudiEtsi1196x2.xcframework.zip                     │
│ → EudiEtsi1196x2.xcframework/                            │
└──────────────────────────────────────────────────────────┘
         ↓
┌──────────────────────────────────────────────────────────┐
│ STEP 6: Xcode Registers Framework                        │
│                                                          │
│ Adds to: Build Phases → Link Binary With Libraries       │
│ Available for: import EudiEtsi1196x2                     │
└──────────────────────────────────────────────────────────┘
         ↓
┌──────────────────────────────────────────────────────────┐
│ STEP 7: Build Selects Appropriate Slice                  │
│                                                          │
│ Device build    → ios-arm64 binary                       │
│ Simulator build → ios-arm64_x86_64-simulator binary      │
└──────────────────────────────────────────────────────────┘
         ↓
┌──────────────────────────────────────────────────────────┐
│ STEP 8: Developer Can Use Framework                      │
│                                                          │
│ import EudiEtsi1196x2                                    │
│ let validator = EudiEtsi1196x2.Validator()               │
└──────────────────────────────────────────────────────────┘
```

---

# SECTION 4: Quick Reference Guide

## 4.1 Command Cheat Sheet

### Local Development Build

```bash
# 1. Publish Kotlin modules
cd <root>
./kotlin publish -m 119602-data-model mavenLocal
./kotlin publish -m consultation mavenLocal
./kotlin publish -m consultation119602 mavenLocal

# 2. Build iOS frameworks
cd ios-umbrella
./gradlew build

# 3. Create XCFramework
xcrun xcodebuild -create-xcframework \
  -framework build/bin/iosArm64/releaseFramework/EudiEtsi1196x2.framework \
  -framework build/bin/iosX64/releaseFramework/EudiEtsi1196x2.framework \
  -framework build/bin/iosSimulatorArm64/releaseFramework/EudiEtsi1196x2.framework \
  -output build/EudiEtsi1196x2.xcframework
```

### Release Preparation

```bash
# Build and zip
cd ios-umbrella/build
zip -r EudiEtsi1196x2.xcframework.zip EudiEtsi1196x2.xcframework/

# Calculate checksum
shasum -a 256 EudiEtsi1196x2.xcframework.zip

# Version from Kotlin build
cat ../gradle.properties | grep '^version=' | cut -d= -f2
```

### SPM Consumer

```bash
# In Xcode: File → Add Packages
# Paste: https://github.com/eu-digital-identity-wallet/eudi-lib-kmp-etsi-1196x2.git
# Select: v0.4.0

# In code
import EudiEtsi1196x2
```

---

## 4.2 Key Files and Locations

| Path | Purpose |
|------|---------|
| `gradle.properties` | Version source of truth |
| `build.gradle.kts` | Framework build config |
| `settings.gradle.kts` | Gradle repository config |
| `PKIXBridge` | Pre-built PKIXBridge framework |
| `build/bin/{target}/releaseFramework/EudiEtsi1196x2.framework` | Build output (.framework) |
| `build/EudiEtsi1196x2.xcframework` | Combined XCFramework output |
| `Package.swift` | SPM manifest (repository root) |
| `../.github/workflows/release.yml` | Release automation workflow |
| `.github/scripts/generate-package-swift.sh` | Package.swift generator script |

---

## 4.3 Common Scenarios

### Scenario 1: Local Development Build

```bash
# I want to build the iOS framework locally to test

# Step 1: Publish modules
./kotlin publish -m consultation119602 mavenLocal

# Step 2: Build frameworks
cd ios-umbrella && ./gradlew build

# Step 3: Output ready at:
# ios-umbrella/build/bin/iosArm64/releaseFramework/EudiEtsi1196x2.framework
```

### Scenario 2: Create XCFramework for Distribution

```bash
# I need to create .xcframework for SPM

# From ios-umbrella/
./gradlew build
xcrun xcodebuild -create-xcframework \
  -framework build/bin/iosArm64/releaseFramework/EudiEtsi1196x2.framework \
  -framework build/bin/iosX64/releaseFramework/EudiEtsi1196x2.framework \
  -framework build/bin/iosSimulatorArm64/releaseFramework/EudiEtsi1196x2.framework \
  -output build/EudiEtsi1196x2.xcframework

# Output: build/EudiEtsi1196x2.xcframework/
```

### Scenario 3: Publish Release

```bash
# I want to release v0.4.0

# Step 1: Push tag
git tag v0.4.0
git push origin v0.4.0

# Step 2: GitHub Actions automatically:
# - Publishes modules
# - Builds frameworks
# - Creates XCFramework
# - Generates Package.swift
# - Uploads to GitHub Releases

# Step 3: Developers can use:
# File → Add Packages → URL → v0.4.0
```

### Scenario 4: Add Framework to Swift Project

```bash
# I want to use EudiEtsi1196x2 in my Swift app

# In Xcode:
# File → Add Packages
# Paste: https://github.com/eu-digital-identity-wallet/eudi-lib-kmp-etsi-1196x2.git
# Select: v0.4.0
# Choose target
# Click "Add Package"

# In code:
import EudiEtsi1196x2
```

---

## 4.4 Troubleshooting Flowchart

```
┌─────────────────────────────────┐
│ Build Fails?                    │
└─────────────────────────────────┘
         ↓
    ┌────┴────┐
    ↓         ↓
┌──────────┐ ┌──────────────────────┐
│ Gradle?  │ │ Kotlin Toolchain?    │
└──────────┘ └──────────────────────┘
    ↓
"Maven artifact not found"?
    ↓
    ┌─→ Run: ./kotlin publish -m * mavenLocal
    └─→ Then retry: cd ios-umbrella && ./gradlew build
    
"PKIXBridge not found"?
    ↓
    ┌─→ Run: ./kotlin do buildXcframework
    ├─→ Copy: cp -r .../PKIXBridge.xcframework ios-umbrella/PKIXBridge/
    └─→ Then retry: ./gradlew build

"Task disabled" (on Windows/Linux)?
    ↓
    └─→ iOS only builds on macOS
    └─→ Use macOS for building

────────────────────────────────────

┌─────────────────────────────────┐
│ SPM Fails?                      │
└─────────────────────────────────┘
         ↓
"Checksum mismatch"?
    ↓
    ├─→ Delete: ~/Library/Developer/Xcode/DerivedData
    ├─→ Delete: Package.resolved
    └─→ Re-add package in Xcode

"Could not resolve package"?
    ↓
    ├─→ Check URL is correct
    ├─→ Check internet connection
    └─→ Verify repository is public

"Minimum deployment target mismatch"?
    ↓
    └─→ Update project to iOS 16+
```

---

## 4.5 Platform Matrix

| Platform | Supported Versions | XCFramework Support |
|---|---|---|
| iOS | 16.0+ | ✅ arm64 (device), arm64+x86_64 (simulator) |
| macOS | 13.0+ | ✅ arm64+x86_64 |
| tvOS | 16.0+ | ✅ (if supported in frameworks) |
| watchOS | 9.0+ | ✅ (if supported in frameworks) |

---

# SECTION 5: Visual Flows and Diagrams

## 5.1 Complete XCFramework Creation Pipeline

```
┌────────────────────────────────────┐
│ STAGE 1: Kotlin Toolchain          │
│ ./kotlin publish -m * mavenLocal   │
└────────────────────────────────────┘
         ↓
    [3 Maven artifacts created]
         ↓
┌────────────────────────────────────┐
│ STAGE 2: ios-umbrella Build        │
│ cd ios-umbrella && ./gradlew build │
└────────────────────────────────────┘
         ↓
    [3 .framework bundles]
    ├── iosArm64/
    ├── iosX64/
    └── iosSimulatorArm64/
         ↓
┌────────────────────────────────────┐
│ STAGE 3: XCFramework Creation      │
│ xcrun xcodebuild -create-xcframework
└────────────────────────────────────┘
         ↓
    [1 .xcframework bundle]
    ├── ios-arm64/
    └── ios-arm64_x86_64-simulator/
         ↓
┌────────────────────────────────────┐
│ STAGE 4: Packaging                 │
│ zip -r .xcframework.zip            │
│ shasum -a 256 .zip                 │
└────────────────────────────────────┘
         ↓
    [Zipped file + checksum]
         ↓
┌────────────────────────────────────┐
│ STAGE 5: Upload & Publish          │
│ GitHub Releases + Package.swift    │
└────────────────────────────────────┘
         ↓
    [SPM-ready distribution]
         ↓
┌────────────────────────────────────┐
│ STAGE 6: Consumer Uses             │
│ import EudiEtsi1196x2              │
└────────────────────────────────────┘
```

---

## 5.2 Dependency Resolution Flow

```
┌─────────────────────────────┐
│ ios-umbrella/build.gradle   │
│                             │
│ api("...etsi-119602-...")   │
└─────────────────────────────┘
         ↓
┌─────────────────────────────┐
│ Repository Search Order     │
│ 1. mavenLocal()             │
│ 2. mavenCentral()           │
│ 3. google()                 │
└─────────────────────────────┘
         ↓
    ┌────┴────┐
    ↓         ↓
┌──────────────┐ ┌──────────────┐
│Found in      │ │Not found in  │
│mavenLocal()? │ │mavenLocal()?  │
└──────────────┘ └──────────────┘
    ✅YES          ↓
    ↓          Check
 Use it       mavenCentral()
              |
              ✓ Found
              ↓
             Use it
```

---

## 5.3 Package.swift Template Transformation

```
BEFORE (Template):
┌──────────────────────────────┐
│ remoteKotlinUrl = ""         │
│ remoteKotlinChecksum = ""    │
│ packageName = ""             │
│ platforms: [$platforms]      │
└──────────────────────────────┘
         ↓↓↓
      FILL IN
         ↓↓↓
AFTER (Complete):
┌──────────────────────────────────────────┐
│ remoteKotlinUrl =                        │
│   "https://github.com/.../...zip"        │
│ remoteKotlinChecksum =                   │
│   "a3c4d5e6f7a8b9c0..."                 │
│ packageName = "EudiEtsi1196x2"          │
│ platforms: [.iOS(.v16), ...]            │
└──────────────────────────────────────────┘
```

---

# SECTION 6: FAQ and Best Practices

## 6.1 Frequently Asked Questions

### Q: What's the difference between .framework and .xcframework?

**A:** 
- **`.framework`** = single architecture (one for arm64, one for x86_64)
- **`.xcframework`** = multi-architecture bundle (contains all slices in one directory)

SPM requires `.xcframework` for multi-architecture distribution.

---

### Q: Does `xcrun xcodebuild -create-xcframework` create Package.swift?

**A:** **No.** It only creates the `.xcframework` bundle. Package.swift must be created manually or via automation.

---

### Q: How often do I need to update Package.swift?

**A:** Every release, because:
- URL changes (different version tag)
- Checksum changes (different binary)

---

### Q: Can I use a local .xcframework path in Package.swift?

**A:** No. SPM binary targets require HTTP(S) URLs for distribution. Local paths use source dependencies.

---

### Q: What if the .xcframework.zip file is corrupted?

**A:** SPM will detect checksum mismatch and report error. Delete cache and re-add package.

---

### Q: Can I distribute multiple frameworks in one SPM package?

**A:** Yes, using multiple `binaryTarget` declarations with per-module `KMMBRIDGE VARIABLES BLOCK`.

---

### Q: Is the static framework smaller than dynamic?

**A:** Static frameworks have the Kotlin runtime linked into each consumer app, so each app is larger, but there's no runtime overhead. Dynamic frameworks are smaller per app but require runtime support. For this project, static is preferred for iOS.

---

## 6.2 Best Practices

### ✅ DO

- **Use semantic versioning:** v0.4.0, v1.0.0-beta, etc.
- **Tag all releases in git:** `git tag v0.4.0`
- **Calculate checksums correctly:** Always use `shasum -a 256` on the zipped file
- **Test locally before release:** Verify `./gradlew build` succeeds
- **Document platform requirements:** In Package.swift platforms
- **Keep Package.swift in version control:** Don't generate it at build time
- **Use mavenLocal-first repository order:** Ensures local snapshots are used

### ❌ DON'T

- **Use floating versions:** Don't rely on "latest"
- **Forget to tag releases:** Version must be in both Package.swift URL and git tag
- **Mix versions:** URL tag (v0.4.0) must match Package.swift content
- **Change checksums:** Recalculate after every rebuild
- **Embed absolute paths:** All paths must be URLs or relative
- **Leave placeholders in Package.swift:** $platforms, empty strings will fail
- **Build iOS on non-macOS:** iOS framework building requires macOS

---

## 6.3 Performance Considerations

### Build Time

- **First build:** 2-5 minutes (Kotlin/Native compilation)
- **Incremental build:** 30 seconds - 1 minute (if dependencies unchanged)
- **XCFramework creation:** 30-60 seconds
- **Zipping:** 2-5 minutes (depends on file size)

### Distribution Size

- **Unzipped .xcframework:** ~500 MB - 1.5 GB (Kotlin runtime + code)
- **Zipped .xcframework.zip:** ~100-400 MB (compressed)
- **SPM cache per machine:** ~200-500 MB per version

### Optimization Tips

- Only keep **recent releases** in GitHub Releases (archives old ones)
- Use **GitHub Pages** for documentation (separate from binary distribution)
- Consider **CDN** for very large frameworks (GitHub Releases is fine for most cases)

---

## 6.4 Security Considerations

### Checksum Verification

SPM **automatically verifies** checksums, so tampering is detected:

```
Download intercept attempt:
Attacker replaces binary
         ↓
SPM calculates checksum
         ↓
Checksum doesn't match Package.swift
         ↓
SPM rejects ✓ (security preserved)
```

### Signing Recommendations

- **Sign releases** with GitHub release signatures
- **Use HTTPS only** for distribution URLs
- **Keep SIGNING_KEY secret** in GitHub Actions
- **Rotate keys periodically** (best practice)

---

# SECTION 7: Glossary

| Term | Definition |
|------|-----------|
| **ios-umbrella** | Standalone Gradle project aggregating three Kotlin modules into a static iOS framework |
| **XCFramework** | Multi-architecture iOS framework bundle supporting multiple platforms (device, simulator) |
| **.xcframework.zip** | Compressed XCFramework archive for distribution via SPM |
| **Package.swift** | Swift Package Manager manifest declaring package metadata and binary locations |
| **Checksum** | SHA256 hash used to verify file integrity during download |
| **Static Framework** | Framework with runtime compiled into consuming app (no runtime embedding) |
| **Dynamic Framework** | Framework with separate runtime (dyld managed) |
| **kmmbridge** | Gradle plugin automating XCFramework creation and SPM distribution |
| **PKIXBridge** | Swift framework wrapping Apple's SecTrust for certificate validation |
| **mavenLocal()** | Local Maven repository cache (e.g., ~/.m2/repository) |
| **SPM** | Swift Package Manager - Apple's official package management system |
| **Slice** | Single-architecture binary within an XCFramework (e.g., ios-arm64) |
| **LoTE** | Lists of Trusted Entities (ETSI TS 119 602) |
| **Kotlin/Native** | Kotlin compiler targeting native iOS binaries |

---

# SECTION 8: References and Additional Resources

## 8.1 Project Documentation

- **AGENTS.md** — Kotlin Toolchain build system overview
- **BUILD_GUIDE.md** — ios-umbrella quick reference
- **BUILD_CLARIFICATION.md** — Detailed build process explanation
- **INVESTIGATION_SUMMARY.md** — Technical background on Maven integration
- **README-MAVEN-INTEGRATION.md** — Maven dependency model architecture
- **RESEARCH_XCFRAMEWORK_AND_SPM.md** — Deep technical research (very comprehensive)
- **PACKAGE_SWIFT_GENERATION.md** — Complete Package.swift generation guide
- **PACKAGE_SWIFT_SUMMARY.md** — Quick answers on Package.swift

## 8.2 External Resources

- **Swift Package Manager Docs:** https://docs.swift.org/swift-package-manager/
- **Apple Framework Documentation:** https://developer.apple.com/documentation/
- **KMMBridge:** https://touchlab.co/kmmbridge/
- **KMMBridge SPM Guide:** https://touchlab.co/kmmbridge/spm/iosspm
- **GitHub Releases API:** https://docs.github.com/en/repositories/releasing-projects-on-github
- **Kotlin/Native:** https://kotlinlang.org/docs/native-overview.html

---

# CONCLUSION

The iOS umbrella framework ecosystem consists of:

1. **ios-umbrella** (standalone Gradle project) → Creates `.framework` bundles
2. **XCFramework creation** (manual or automated) → Combines into `.xcframework`
3. **Package.swift manifest** → Enables SPM distribution
4. **GitHub Releases** → Hosts `.xcframework.zip` for download
5. **Swift developers** → Consume via `File → Add Packages`

The **release workflow** automates all steps via GitHub Actions, making releases repeatable and reliable. Version management is centralized in `gradle.properties`, ensuring consistency across all build artifacts and distributions.

---

**Document Version:** 1.0  
**Last Updated:** October 22, 2026  
**Project:** EUDI ETSI 1196x2 Library  
**Maintained by:** Development Team

