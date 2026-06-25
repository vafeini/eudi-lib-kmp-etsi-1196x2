// swift-tools-version:5.9
import PackageDescription

// BEGIN KMMBRIDGE VARIABLES BLOCK (do not edit)
let remoteKotlinUrl = "https://maven.pkg.github.com/vafeini/eudi-lib-kmp-etsi-1196x2/eu/europa/ec/eudi/etsi-1196x2-ios-kmmbridge/0.4.0-alpha.7/etsi-1196x2-ios-kmmbridge-0.4.0-alpha.7.zip"
let remoteKotlinChecksum = "77266c3c2faf935ebc45602b4dd76f63b8ff369b80411a2bd30434b826aefc17"
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