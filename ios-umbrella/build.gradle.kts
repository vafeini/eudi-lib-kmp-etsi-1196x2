import org.gradle.internal.os.OperatingSystem
import org.jetbrains.kotlin.gradle.plugin.mpp.Framework
import org.jetbrains.kotlin.gradle.tasks.CInteropProcess
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink
import java.nio.file.Files
import java.nio.file.StandardCopyOption

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

// Slice paths match the xcframework layout: device = ios-arm64; both simulators share
// the lipo'd ios-arm64_x86_64-simulator slice.
fun pkixBridgeSlice(targetName: String): String =
    when (targetName) {
        "iosArm64" -> "ios-arm64"
        "iosX64", "iosSimulatorArm64" -> "ios-arm64_x86_64-simulator"
        else -> error("Unknown iOS target: $targetName")
    }

// Architecture to pull out of the (possibly fat) PKIXBridge slice when merging it into a
// static framework archive.
fun pkixBridgeArch(targetName: String): String =
    when (targetName) {
        "iosArm64", "iosSimulatorArm64" -> "arm64"
        "iosX64" -> "x86_64"
        else -> error("Unknown iOS target: $targetName")
    }

kotlin {
    // PKIXBridge.xcframework
    val pkixBridgeXcframework = projectDir.resolve("PKIXBridge/PKIXBridge.xcframework")
    
    // Fail build if PKIXBridge.xcframework does not exist
    if (!pkixBridgeXcframework.exists()) {
        error(
            "PKIXBridge.xcframework not found at $pkixBridgeXcframework\n" +
            "Please ensure PKIXBridge.xcframework is available in ios-umbrella/PKIXBridge/ directory.\n" +
            "The GitHub Actions workflow should have downloaded it from the PKIXBridge repository."
        )
    }

    // Resolve the Swift toolchain's static-library directory so the Kotlin/Native linker can find
    // the Swift ABI compatibility shims that PKIXBridge's objects force-load.
    val swiftLibBase: String? =
        if (OperatingSystem.current().isMacOsX) {
            providers.exec { commandLine("xcode-select", "-p") }
                .standardOutput.asText.get().trim() +
                    "/Toolchains/XcodeDefault.xctoolchain/usr/lib/swift"
        } else {
            null
        }

    fun swiftLibPlatform(targetName: String): String =
        when (targetName) {
            "iosArm64" -> "iphoneos"
            "iosX64", "iosSimulatorArm64" -> "iphonesimulator"
            else -> error("Unknown iOS target: $targetName")
        }

    val frameworkName = "EudiEtsi1196x2"

    listOf(iosArm64(), iosX64(), iosSimulatorArm64()).forEach { target ->
        val frameworkSearchPath = pkixBridgeXcframework.resolve(pkixBridgeSlice(target.name)).absolutePath
        target.binaries.framework {
            baseName = frameworkName
            // Static framework: the Kotlin/Native runtime and every exported module are archived
            // into the framework binary, so consumers link it at build time instead of embedding
            // and signing a dynamic framework. This also avoids the duplicate-runtime and
            // dyld-load-order problems that arise when several Kotlin frameworks ship in one app.
            isStatic = true
        }
        target.binaries.all {
            freeCompilerArgs += "-Xoverride-konan-properties=minVersion.ios=16.0"
            linkerOpts("-framework", "PKIXBridge", "-F$frameworkSearchPath")
            // Carry the iOS system Swift runtime location as an rpath so dyld can resolve
            linkerOpts("-rpath", "/usr/lib/swift")
            if (swiftLibBase != null) {
                linkerOpts("-L$swiftLibBase/${swiftLibPlatform(target.name)}")
            }
        }
    }

    sourceSets {
        iosMain {
            dependencies {
                // Resolve from Maven instead of project refs (standalone Gradle project)
                api("eu.europa.ec.eudi:etsi-1196x2-consultation:${findProperty("libraryVersion") ?: "0.4.0-alpha.5"}")
                api("eu.europa.ec.eudi:etsi-119602-consultation:${findProperty("libraryVersion") ?: "0.4.0-alpha.5"}")
                api("eu.europa.ec.eudi:etsi-119602-data-model:${findProperty("libraryVersion") ?: "0.4.0-alpha.5"}")
                // Darwin (NSURLSession) HTTP engine, linked into the umbrella framework so
                // HttpClient(Darwin) works at runtime on iOS.
                implementation(libs.ktor.client.darwin)
            }
        }
    }
}

// This standalone Gradle project assumes it is pre-built and available at projectDir/PKIXBridge/PKIXBridge.xcframework

// A static framework is an `ar` archive, not a link product: the Kotlin/Native compiler leaves
// PKIXBridge's Objective-C classes as undefined symbols rather than pulling its objects in the way
// the linker does when it produces a dynamic framework. Merge the PKIXBridge slice into the archive
// so the framework we publish stays self-contained and consumers do not have to link PKIXBridge —
// an implementation detail of this library — themselves.
tasks.withType<KotlinNativeLink>().configureEach {
    val framework = binary as? Framework ?: return@configureEach
    if (!framework.isStatic) return@configureEach

    val targetName = framework.target.name
    val pkixBridgeBinary = projectDir.resolve(
        "PKIXBridge/PKIXBridge.xcframework/${pkixBridgeSlice(targetName)}/PKIXBridge.framework/PKIXBridge",
    )
    val frameworkBinary = framework.outputFile.resolve(framework.baseName)
    val arch = pkixBridgeArch(targetName)

    inputs.file(pkixBridgeBinary)
        .withPropertyName("pkixBridgeSliceBinary")
        .withPathSensitivity(PathSensitivity.RELATIVE)

    doLast {
        val merged = frameworkBinary.resolveSibling("${frameworkBinary.name}-merged.a")
        val exit = ProcessBuilder(
            "xcrun", "libtool", "-static",
            // The simulator slice is fat (arm64 + x86_64); keep only this target's architecture.
            "-arch_only", arch,
            "-o", merged.absolutePath,
            frameworkBinary.absolutePath,
            pkixBridgeBinary.absolutePath,
        ).inheritIO().start().waitFor()
        check(exit == 0) { "libtool failed with exit code $exit while merging PKIXBridge into $frameworkBinary" }
        // Atomic rename over the existing archive: `File.renameTo` reports failure only through its
        // return value and its overwrite behaviour varies by filesystem. Source and target are
        // siblings, so the move stays within one filesystem and ATOMIC_MOVE is always supported.
        Files.move(
            merged.toPath(),
            frameworkBinary.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    }
}

tasks.withType<CInteropProcess>().configureEach {
    if (interopName == "PKIXBridge") {
        // Track the xcframework contents so a Swift-side change also invalidates the generated
        // bindings. Without this, cinterop stays UP-TO-DATE off its .def hash alone.
        inputs.dir(projectDir.resolve("PKIXBridge/PKIXBridge.xcframework"))
            .withPropertyName("pkixBridgeXcframework")
            .withPathSensitivity(PathSensitivity.RELATIVE)
    }
}
