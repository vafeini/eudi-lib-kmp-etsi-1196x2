/*
 * Copyright (c) 2026 European Commission
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.europa.ec.eudi.plugins.pkixbridge.tasks

import eu.europa.ec.eudi.plugins.pkixbridge.BuildXcframeworkSettings
import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.Output
import org.jetbrains.amper.plugins.TaskAction
import java.nio.file.Path

/**
 * Builds the PKIXBridge xcframework by executing the existing build script.
 *
 * This task:
 * - Checks if running on macOS (required for xcframework compilation)
 * - Executes `PKIXBridge/scripts/build-xcframework.sh` to compile and package the framework
 * - Outputs the resulting xcframework to the task output directory
 *
 * Usage: `./kotlin do buildXcframework`
 */
@TaskAction
public fun buildXcframework(
    @Input pkixbridgeDir: Path,
    @Output outputDir: Path,
    settings: BuildXcframeworkSettings,
) {
    val osName = System.getProperty("os.name")?.lowercase() ?: ""
    val isMacOS = osName.contains("mac")

    if (!isMacOS) {
        val message = "PKIXBridge xcframework build is only supported on macOS. Current OS: $osName"
        if (settings.skipIfNotMacOS) {
            println("⚠️  SKIPPED: $message")
            return
        } else {
            throw IllegalStateException("❌ FAILED: $message")
        }
    }

    val buildScript = pkixbridgeDir.resolve("scripts/build-xcframework.sh")
    if (!buildScript.toFile().exists()) {
        throw IllegalStateException("Build script not found: $buildScript")
    }

    // Make the script executable and run it
    buildScript.toFile().setExecutable(true)

    println("🔨 Building PKIXBridge xcframework...")
    println("   Script: $buildScript")
    println("   Output: $outputDir")

    // Execute the build script with the output directory passed as an environment variable
    val processBuilder = ProcessBuilder("bash", buildScript.toString())
        .directory(pkixbridgeDir.toFile())
        .redirectOutput(ProcessBuilder.Redirect.INHERIT)
        .redirectError(ProcessBuilder.Redirect.INHERIT)

    // Pass output directory to the script via environment variable
    processBuilder.environment()["XCFRAMEWORK_OUTPUT_DIR"] = outputDir.toString()

    val process = processBuilder.start()
    val exitCode = process.waitFor()

    if (exitCode != 0) {
        throw RuntimeException("Build script failed with exit code $exitCode")
    }

    println("✅ PKIXBridge xcframework built successfully")
}
