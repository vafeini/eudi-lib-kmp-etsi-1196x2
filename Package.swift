// swift-tools-version:5.9
import PackageDescription

// BEGIN KMMBRIDGE VARIABLES BLOCK (do not edit)
let remoteKotlinUrl = "https://api.github.com/repos/vafeini/eudi-lib-kmp-etsi-1196x2/releases/assets/454596612.zip"
let remoteKotlinChecksum = "7a3129abd3f31c1f221b8f2b7d46cb1c37c744d5cd40c69dc4a4c764923e1d5d"
let packageName = "EudiEtsi1196x2"
// END KMMBRIDGE BLOCK

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
        ,
    ]
)