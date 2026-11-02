// swift-tools-version:5.9
import PackageDescription

// CI UPDATABLE (do not edit)
let remoteKotlinUrl = "https://github.com/vafeini/eudi-lib-kmp-etsi-1196x2/releases/download/v0.4.0-alpha.5/EudiEtsi1196x2.xcframework.zip"
let remoteKotlinChecksum = "0ce46b5f080d3a86a482423c92fc1ee7a4c5b88f2ce4721aa4fec4c0d51eb652"
// END CI UPDATABLE (do not edit)

let packageName = "EudiEtsi1196x2"

let package = Package(
    name: packageName,
    platforms: [
        .iOS(.v15)
    ],
    products: [
        .library(
            name: packageName,
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