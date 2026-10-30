// swift-tools-version:5.9
import PackageDescription

// CI UPDATABLE (do not edit)
let remoteKotlinUrl = "https://github.com/vafeini/eudi-lib-kmp-etsi-1196x2/releases/download/v0.4.0-alpha.5/EudiEtsi1196x2.xcframework.zip"
let remoteKotlinChecksum = "244cd6274803ea59401a2c2e32d432b830ced71be474481b7d372cfe8a96178d"
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