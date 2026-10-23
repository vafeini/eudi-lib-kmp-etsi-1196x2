# Swift Package Manager Consumption Guide: EudiEtsi1196x2

**Purpose:** This guide shows Swift developers how to integrate the EUDI ETSI 1196x2 framework into their Xcode projects via Swift Package Manager (SPM).

**Target Audience:** iOS/macOS developers using Xcode 15+

---

## Quick Start

### Option 1: Via Xcode UI (Recommended for most developers)

1. **Open your Xcode project**
2. **File** → **Add Packages**
3. **Paste repository URL:**
   ```
   https://github.com/eu-digital-identity-wallet/eudi-lib-kmp-etsi-1196x2.git
   ```
4. **Select version:** "Exact Version" → enter `0.4.0` (or desired version)
5. **Choose target** to link the framework to
6. **Click "Add Package"**

**Done!** You can now import the framework in your Swift files:

```swift
import EudiEtsi1196x2
```

---

### Option 2: Via Package.swift (For SPM packages)

If your project is itself a Swift Package, add to your `Package.swift`:

```swift
dependencies: [
    .package(
        url: "https://github.com/eu-digital-identity-wallet/eudi-lib-kmp-etsi-1196x2.git",
        from: "0.4.0"
    )
]
```

Then add to your target:

```swift
targets: [
    .target(
        name: "MyTarget",
        dependencies: [
            .product(name: "EudiEtsi1196x2", package: "eudi-lib-kmp-etsi-1196x2")
        ]
    )
]
```

---

## System Requirements

### Minimum Deployment Targets

| Platform | Minimum Version |
|----------|---|
| **iOS** | 16.0 |
| **macOS** | 13.0 |
| **tvOS** | 16.0 |
| **watchOS** | 9.0 |

**If your project targets iOS 15 or lower:** You cannot use this framework. Your app's minimum deployment target must be iOS 16.0 or higher.

### Architecture Support

| Platform | Supported Architectures |
|---|---|
| **iOS Physical Device** | arm64 |
| **iOS Simulator (Apple Silicon Mac)** | arm64 |
| **iOS Simulator (Intel Mac)** | x86_64 |

---

## Using the Framework

### Basic Usage

```swift
import EudiEtsi1196x2

// Access Kotlin-compiled classes and functions
// The framework exposes the public API from:
// - consultation (core validation abstractions)
// - consultation119602 (LoTE-based validation)
// - 119602-data-model (ETSI TS 119 602 data structures)

let validator = EudiEtsi1196x2.Validator()
let result = try validator.validate(certificate: certData, trustedList: listData)
```

### Available Classes and Functions

The framework exposes all `public` API declarations from these Kotlin modules:

- **consultation:** Core PKIX certificate chain validation logic
- **consultation119602:** Main public API for validating against ETSI TS 119 602 Lists of Trusted Entities (LoTE)
- **119602-data-model:** Data classes representing LoTE structures

Refer to the [LoTE Certificate Validation documentation](./LoTE-Certificate-Validation.md) for detailed API documentation.

---

## Understanding What You're Getting

### What is This Framework?

The `EudiEtsi1196x2` framework is a **static Kotlin/Native framework** compiled for iOS that provides certificate chain validation against ETSI Trusted Lists.

**Key characteristics:**

- **Static linking:** Entire framework is compiled into your app binary (no dynamic linking overhead)
- **Self-contained:** Includes the Kotlin runtime; no additional dependencies needed at runtime
- **Multi-architecture:** Single `.xcframework` bundle works on device and simulator
- **PKIX validation:** Uses both Apple's `SecTrust` API (via PKIXBridge) and custom ETSI logic

### Architecture: How It's Built

```
EudiEtsi1196x2.xcframework/
├── ios-arm64/                    [Device - arm64 only]
│   └── EudiEtsi1196x2.framework/
└── ios-arm64_x86_64-simulator/   [Simulator - both architectures]
    └── EudiEtsi1196x2.framework/
```

Xcode automatically selects the correct slice based on your build target:
- **Building for device:** Uses `ios-arm64` slice
- **Building for simulator:** Uses `ios-arm64_x86_64-simulator` slice

---

## Verifying the Download

### How SPM Verifies Integrity

When you add this package, SPM automatically:

1. **Downloads** the `.xcframework.zip` from GitHub Releases
2. **Calculates** the SHA256 checksum of the downloaded file
3. **Compares** it against the checksum in `Package.swift`
4. **Accepts or rejects** based on match

This ensures the framework hasn't been corrupted or tampered with.

### If You See "Checksum Mismatch" Error

**Steps to fix:**

1. **Clear Xcode cache:**
   ```bash
   rm -rf ~/Library/Developer/Xcode/DerivedData
   ```

2. **Remove the package from your project** (File → Packages → right-click → Remove)

3. **Re-add the package** (File → Add Packages)

4. **If it still fails:** Report the issue to the [project repository](https://github.com/eu-digital-identity-wallet/eudi-lib-kmp-etsi-1196x2)

---

## Troubleshooting

### "Could not resolve package repository"

**Cause:** Incorrect URL or network issue

**Fix:**
- Verify the URL is correct: `https://github.com/eu-digital-identity-wallet/eudi-lib-kmp-etsi-1196x2.git`
- Check your internet connection
- Try cloning the repository manually to verify access

### "Package.swift not found at version"

**Cause:** The version tag doesn't exist or Package.swift wasn't included in the tag

**Fix:**
- Use a version that exists (e.g., `0.4.0` or later)
- Check available versions by visiting: https://github.com/eu-digital-identity-wallet/eudi-lib-kmp-etsi-1196x2/releases

### "Minimum deployment target mismatch"

**Cause:** Your project's deployment target is lower than iOS 16.0

**Fix:**
- Update your app's minimum deployment target to iOS 16.0 or higher:
  1. Select your project in Xcode
  2. Select your target
  3. Go to **General** tab
  4. Change **Minimum Deployments** to iOS 16.0+

### Framework symbols not recognized in code

**Cause:** Xcode hasn't rebuilt the dependency cache

**Fix:**
- **Product** → **Clean Build Folder** (Cmd+Shift+K)
- **Product** → **Build** (Cmd+B)
- If still not resolved, repeat the checksum mismatch steps above

---

## Version Management

### Specifying Versions

In Xcode's "Add Packages" dialog, you can specify:

- **Exact Version:** `0.4.0` (exactly this version)
- **From Version:** `0.4.0` or later (accepts newer minor/patch versions)
- **Up to Next Major:** Accepts versions up to (but not including) the next major

### Recommended Practice

**For production apps:** Use exact version to ensure reproducible builds:

```swift
.package(url: "...", exact: "0.4.0")
```

**For development/libraries:** Use "from" version to allow updates:

```swift
.package(url: "...", from: "0.4.0")
```

---

## Example: Complete Integration

### Step 1: Add Package via Xcode

See "Quick Start" section above.

### Step 2: Create Swift Code

**File: `ValidationService.swift`**

```swift
import EudiEtsi1196x2
import Foundation

class CertificateValidator {
    /// Validate a certificate chain against ETSI Trusted Lists
    func validate(
        certificateData: Data,
        trustedListData: Data
    ) throws -> ValidationResult {
        // Use the EudiEtsi1196x2 framework
        let validator = EudiEtsi1196x2.Validator()
        
        // Call framework methods
        let isValid = try validator.validate(
            certificate: certificateData,
            trustedList: trustedListData
        )
        
        return ValidationResult(isValid: isValid, timestamp: Date())
    }
}

struct ValidationResult {
    let isValid: Bool
    let timestamp: Date
}
```

### Step 3: Use in Your App

```swift
import SwiftUI

struct ContentView: View {
    @State private var validationResult: String = "Not validated"
    let validator = CertificateValidator()
    
    var body: some View {
        VStack {
            Text("Certificate Validation")
                .font(.headline)
            
            Text(validationResult)
                .padding()
            
            Button("Validate Certificate") {
                Task {
                    do {
                        // Load your certificate and trusted list data
                        let certData = try Data(contentsOf: Bundle.main.url(forResource: "cert", withExtension: "der")!)
                        let listData = try Data(contentsOf: Bundle.main.url(forResource: "trustedlist", withExtension: "json")!)
                        
                        let result = try validator.validate(certificateData: certData, trustedListData: listData)
                        validationResult = result.isValid ? "✅ Valid" : "❌ Invalid"
                    } catch {
                        validationResult = "❌ Error: \(error.localizedDescription)"
                    }
                }
            }
        }
    }
}
```

---

## Performance Considerations

### First Launch

The first time you add this package, Xcode will:
1. Download the `.xcframework.zip` (~15-30 MB depending on architecture)
2. Extract and cache it
3. Index the framework symbols
4. Rebuild your project

**Expect 2-5 minutes for initial integration.**

### Subsequent Builds

The framework is cached locally, so subsequent builds are fast.

### App Size Impact

The framework adds approximately **15-25 MB** to your app's binary size (depending on build configuration).

---

## Security Notes

### Certificate Validation

This framework validates certificate chains using:
- **Apple's SecTrust API** for PKIX validation
- **Custom ETSI logic** for TS 119 602 Trusted List validation

**Important:** This framework validates certificates against known trusted lists. It does **not** replace the need for proper SSL/TLS configuration in your networking code.

### Runtime Safety

The framework is stateless and thread-safe for validation operations. Multiple threads can safely call validation methods concurrently.

---

## Getting Help

### Documentation

- **LoTE Validation Details:** [LoTE-Certificate-Validation.md](./LoTE-Certificate-Validation.md)
- **ETSI TS 119 602 Specification:** Official ETSI documentation
- **Project Repository:** https://github.com/eu-digital-identity-wallet/eudi-lib-kmp-etsi-1196x2

### Reporting Issues

If you encounter problems:

1. Check this guide's Troubleshooting section
2. Review the project's [GitHub Issues](https://github.com/eu-digital-identity-wallet/eudi-lib-kmp-etsi-1196x2/issues)
3. [Create a new issue](https://github.com/eu-digital-identity-wallet/eudi-lib-kmp-etsi-1196x2/issues/new) with:
   - Xcode version
   - Swift version
   - iOS deployment target
   - Exact error message
   - Steps to reproduce

---

## Appendix: SPM File Structure

### Package.swift Location

The `Package.swift` file is located at the repository root and is automatically read by Xcode when you add the package.

### Binary Target Definition

```swift
targets: [
    .binaryTarget(
        name: "EudiEtsi1196x2",
        url: "https://github.com/eu-digital-identity-wallet/eudi-lib-kmp-etsi-1196x2/releases/download/v0.4.0/EudiEtsi1196x2.xcframework.zip",
        checksum: "a3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3"
    )
]
```

This tells SPM:
- **What to download:** The `.xcframework.zip` at the GitHub Release URL
- **How to verify:** Calculate SHA256 and match it against the checksum
- **Where to use:** Link it into any target that depends on it

---

**Last Updated:** October 22, 2026  
**Framework Version:** 0.4.0+  
**SPM Minimum Version:** 5.9
