// swift-tools-version:5.9
import PackageDescription

// CI UPDATABLE (do not edit)
let remoteKotlinUrl = ""
let remoteKotlinChecksum = ""
// END CI UPDATABLE (do not edit)

let packageName = "EudiEtsi1196x2"

let package = Package(
    name: packageName,
    platforms: [
        .iOS(.v13)
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