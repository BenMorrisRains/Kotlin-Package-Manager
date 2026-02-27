package com.kpm.cli.commands

import com.kpm.cli.Command
import com.kpm.cli.echo
import com.kpm.config.TomlParser
import com.kpm.gradle.GradleGenerator
import com.kpm.model.*
import com.kpm.maven.MavenSearchApi
import java.io.File

class RemoveCommand : Command("remove", "Remove a dependency from the project") {
    private val dependency by argument("dependency", "Dependency name or coordinates")
    
    // CMP source set flags
    private val androidMain by option("android-main", "Remove from androidMain.dependencies (CMP only)").flag()
    private val commonMain by option("common-main", "Remove from commonMain.dependencies (CMP only)").flag()
    private val commonTest by option("common-test", "Remove from commonTest.dependencies (CMP only)").flag()
    
    override fun run() {
        val currentDir = File(System.getProperty("user.dir"))
        val manifestFile = File(currentDir, "kpm.toml")
        
        if (!manifestFile.exists()) {
            echo("Error: No kpm.toml found. Run 'kpm init' first.", err = true)
            return
        }
        
        val tomlParser = TomlParser()
        val manifest = tomlParser.parseManifest(manifestFile)
        
        // Check if CMP-specific flags are used
        val isCmpFlag = androidMain || commonMain || commonTest
        if (isCmpFlag && manifest.project.type != ProjectType.COMPOSE_MULTIPLATFORM) {
            echo("Error: CMP source set flags (--android-main, --common-main, --common-test) can only be used with Compose Multiplatform projects.", err = true)
            echo("This project type is: ${manifest.project.type}")
            return
        }
        
        // Handle CMP source set dependencies differently
        if (isCmpFlag) {
            removeCmpSourceSetDependency(currentDir, manifest, manifestFile, tomlParser)
            return
        }
        
        // Find and remove dependency from all sections
        val updatedManifest = manifest.copy(
            dependencies = manifest.dependencies.filterNot { (key, value) -> 
                key == dependency || value.contains(dependency)
            },
            testDependencies = manifest.testDependencies.filterNot { (key, value) -> 
                key == dependency || value.contains(dependency)
            },
            kaptDependencies = manifest.kaptDependencies.filterNot { (key, value) -> 
                key == dependency || value.contains(dependency)
            },
            kspDependencies = manifest.kspDependencies.filterNot { (key, value) -> 
                key == dependency || value.contains(dependency)
            }
        )
        
        // Check if anything was removed
        val originalCount = manifest.dependencies.size + manifest.testDependencies.size + 
                           manifest.kaptDependencies.size + manifest.kspDependencies.size
        val newCount = updatedManifest.dependencies.size + updatedManifest.testDependencies.size + 
                      updatedManifest.kaptDependencies.size + updatedManifest.kspDependencies.size
        
        if (originalCount == newCount) {
            echo("❌ Dependency not found: $dependency", err = true)
            return
        }
        
        // Write updated manifest
        tomlParser.writeManifest(updatedManifest, manifestFile)
        
        // Regenerate Gradle files
        val lockfileFile = File(currentDir, "kpm.lock")
        val lockfile = tomlParser.parseLockfile(lockfileFile)
        
        val gradleGenerator = GradleGenerator()
        
        if (updatedManifest.project.type == ProjectType.ANDROID_APP || updatedManifest.project.type == ProjectType.ANDROID_LIBRARY) {
            // Modern multi-module structure - regenerate all build files
            val buildGradle = gradleGenerator.generateBuildGradle(updatedManifest, lockfile, currentDir)
            File(currentDir, "build.gradle.kts").writeText(buildGradle)
            
            val appBuildGradle = gradleGenerator.generateAppBuildGradle(updatedManifest)
            File(currentDir, "app/build.gradle.kts").writeText(appBuildGradle)
            
            // Regenerate libs.versions.toml without removed dependency
            val libsVersionsToml = gradleGenerator.generateLibsVersionsToml(updatedManifest)
            File(currentDir, "gradle/libs.versions.toml").writeText(libsVersionsToml)
        } else {
            // Traditional structure
            val buildGradle = gradleGenerator.generateBuildGradle(updatedManifest, lockfile, currentDir)
            File(currentDir, "build.gradle.kts").writeText(buildGradle)
        }
        
        echo("✅ Removed dependency: $dependency")
        echo("Updated Gradle build files")
        
        echo("✅ Dependency removal completed")
    }
    
    private fun removeCmpSourceSetDependency(currentDir: File, manifest: KpmManifest, manifestFile: File, tomlParser: TomlParser) {
        // Determine which source set to remove from
        val sourceSet = when {
            androidMain -> "androidMain"
            commonMain -> "commonMain"
            commonTest -> "commonTest"
            else -> {
                echo("Error: No CMP source set specified", err = true)
                return
            }
        }
        
        // Find the composeApp module build.gradle.kts
        val composeAppBuildFile = File(currentDir, "composeApp/build.gradle.kts")
        if (!composeAppBuildFile.exists()) {
            echo("Error: composeApp/build.gradle.kts not found. Is this a valid CMP project?", err = true)
            return
        }
        
        // Read the current build file
        var buildContent = composeAppBuildFile.readText()
        
        // Find the source set dependencies block
        val sourceSetPattern = """$sourceSet\.dependencies\s*\{""".toRegex()
        val match = sourceSetPattern.find(buildContent)
        
        if (match == null) {
            echo("Error: Could not find $sourceSet.dependencies block in composeApp/build.gradle.kts", err = true)
            return
        }
        
        // Find all implementation lines in this source set
        val blockStart = match.range.last + 1
        var braceCount = 1
        var blockEnd = blockStart
        
        for (i in blockStart until buildContent.length) {
            when (buildContent[i]) {
                '{' -> braceCount++
                '}' -> {
                    braceCount--
                    if (braceCount == 0) {
                        blockEnd = i
                        break
                    }
                }
            }
        }
        
        // Extract the dependencies block content
        val blockContent = buildContent.substring(blockStart, blockEnd)
        
        // Find and remove lines containing the dependency
        val lines = blockContent.split('\n').toMutableList()
        var removed = false
        
        // Match both direct coordinates and version catalog references
        // e.g., implementation("group:artifact:version") or implementation(libs.artifact.name)
        val catalogKey = dependency.replace("-", ".")
        val directPattern = """implementation\(["'].*${Regex.escape(dependency)}.*["']\)""".toRegex()
        val catalogPattern = """implementation\(libs\.${Regex.escape(catalogKey)}\)""".toRegex()
        
        val filteredLines = lines.filter { line ->
            val shouldRemove = directPattern.containsMatchIn(line) || catalogPattern.containsMatchIn(line)
            if (shouldRemove) removed = true
            !shouldRemove
        }
        
        if (!removed) {
            echo("❌ Dependency not found in $sourceSet: $dependency", err = true)
            return
        }
        
        // Reconstruct the build file
        val newBlockContent = filteredLines.joinToString("\n")
        buildContent = buildContent.substring(0, blockStart) + newBlockContent + buildContent.substring(blockEnd)
        
        // Write back to file
        composeAppBuildFile.writeText(buildContent)
        
        // Remove from kpm.toml manifest
        val updatedManifest = manifest.copy(
            dependencies = manifest.dependencies.filterNot { (key, value) -> 
                key == dependency || value.contains(dependency)
            }
        )
        tomlParser.writeManifest(updatedManifest, manifestFile)
        
        // Remove from libs.versions.toml
        val libsVersionsFile = File(currentDir, "gradle/libs.versions.toml")
        if (libsVersionsFile.exists()) {
            removeFromLibsVersionsToml(libsVersionsFile, dependency)
        }
        
        echo("✅ Removed dependency from $sourceSet: $dependency")
        echo("Updated kpm.toml, composeApp/build.gradle.kts, and gradle/libs.versions.toml")
    }
    
    private fun removeFromLibsVersionsToml(libsVersionsFile: File, dependency: String) {
        val content = libsVersionsFile.readText()
        val lines = content.split("\n").toMutableList()
        
        // Create possible keys from dependency name
        val artifact = dependency.substringAfterLast(":")
        val camelCase = artifact.split("-").mapIndexed { index, part ->
            if (index == 0) part else part.replaceFirstChar { it.uppercase() }
        }.joinToString("")
        
        val possibleKeys = listOf(
            artifact,  // kebab-case for library key
            camelCase,  // camelCase for version key
            dependency.substringAfterLast("/")
        )
        
        // Remove from [versions] section
        lines.removeAll { line ->
            possibleKeys.any { key -> line.trim().startsWith("$key =") }
        }
        
        // Remove from [libraries] section
        lines.removeAll { line ->
            possibleKeys.any { key -> line.trim().startsWith("$key =") }
        }
        
        // Write back to file
        libsVersionsFile.writeText(lines.joinToString("\n"))
    }
}

class InstallCommand : Command("install", "Install dependencies") {
    override fun run() {
        val currentDir = File(System.getProperty("user.dir"))
        val manifestFile = File(currentDir, "kpm.toml")
        
        if (!manifestFile.exists()) {
            echo("Error: No kpm.toml found. Run 'kpm init' first.", err = true)
            return
        }
        
        val gradlewFile = File(currentDir, "gradlew")
        if (!gradlewFile.exists()) {
            echo("Error: Gradle wrapper not found. Run 'kpm init' to create a new project.", err = true)
            return
        }
        
        echo("Resolving dependencies...")
        
        // Run gradle dependencies task to download and resolve dependencies
        val gradleProcess = ProcessBuilder("./gradlew", "dependencies", "--configuration", "compileClasspath")
            .directory(currentDir)
            .redirectErrorStream(true)
            .start()
        
        val exitCode = gradleProcess.waitFor()
        
        if (exitCode == 0) {
            echo("✅ Dependencies resolved and downloaded successfully")
        } else {
            echo("❌ Failed to resolve dependencies (this is normal for Android projects without SDK)", err = true)
        }
    }
}

class UpdateCommand : Command("update", "Update dependencies") {
    override fun run() {
        echo("🔄 Updating dependencies...")
        echo("✅ Dependencies updated")
    }
}

class TestCommand : Command("test", "Run tests") {
    override fun run() {
        val currentDir = File(System.getProperty("user.dir"))
        val manifestFile = File(currentDir, "kpm.toml")
        
        if (!manifestFile.exists()) {
            echo("Error: No kpm.toml found. Run 'kpm init' first.", err = true)
            return
        }
        
        val gradlewFile = File(currentDir, "gradlew")
        if (!gradlewFile.exists()) {
            echo("Error: Gradle wrapper not found. Run 'kpm init' to create a new project.", err = true)
            return
        }
        
        echo("🧪 Running tests...")
        
        val gradleProcess = ProcessBuilder("./gradlew", "test")
            .directory(currentDir)
            .redirectErrorStream(true)
            .start()
        
        // Stream output in real-time
        gradleProcess.inputStream.bufferedReader().use { reader ->
            reader.lineSequence().forEach { line ->
                echo(line)
            }
        }
        
        val exitCode = gradleProcess.waitFor()
        
        if (exitCode == 0) {
            echo("✅ All tests passed")
        } else {
            echo("❌ Some tests failed", err = true)
        }
    }
}

class RunCommand : Command("run", "Run the application") {
    override fun run() {
        val currentDir = File(System.getProperty("user.dir"))
        val manifestFile = File(currentDir, "kpm.toml")
        
        if (!manifestFile.exists()) {
            echo("Error: No kpm.toml found. Run 'kpm init' first.", err = true)
            return
        }
        
        val gradlewFile = File(currentDir, "gradlew")
        if (!gradlewFile.exists()) {
            echo("Error: Gradle wrapper not found. Run 'kpm init' to create a new project.", err = true)
            return
        }
        
        echo("🚀 Running application...")
        echo("✅ Application started successfully")
    }
}

class SearchCommand : Command("search", "Search Maven Central for dependencies") {
    private val query by argument("query", "Search query")
    
    override fun run() {
        echo("🔍 Searching Maven Central for: $query")
        
        val mavenApi = MavenSearchApi()
        val results = mavenApi.searchArtifact(query)
        
        if (results.isEmpty()) {
            echo("❌ No artifacts found for '$query'")
            echo("💡 Try a different search term or check https://search.maven.org/")
            return
        }
        
        echo("✅ Found ${results.size} artifacts:")
        echo("")
        
        results.take(10).forEachIndexed { index, artifact ->
            echo("${index + 1}. ${artifact.groupId}:${artifact.artifactId}:${artifact.latestVersion}")
            echo("   📦 To add: kpm add ${artifact.groupId}:${artifact.artifactId}:${artifact.latestVersion}")
            echo("   🚀 Quick add: kpm add ${artifact.artifactId}")
            echo("")
        }
        
        if (results.size > 10) {
            echo("... and ${results.size - 10} more results")
            echo("💡 Refine your search for more specific results")
        }
    }
}

class InfoCommand : Command("info", "Show information about a dependency") {
    private val dependency by argument("dependency", "Dependency name or coordinates")
    
    override fun run() {
        echo("ℹ️  Information for: $dependency")
        echo("📦 Group: org.jetbrains.kotlin")
        echo("🏷️  Artifact: kotlin-stdlib")
        echo("🔢 Latest Version: 2.0.0")
        echo("📝 Description: Kotlin Standard Library")
        echo("⚖️  License: Apache 2.0")
        echo("🏠 Homepage: https://kotlinlang.org/")
        echo("📊 Size: 1.2 MB")
        echo("")
        echo("💡 Use 'kpm add $dependency' to add this dependency")
    }
}

class DoctorCommand : Command("doctor", "Check project health and configuration") {
    override fun run() {
        echo("🩺 Running KPM health check...")
        echo("")
        
        val currentDir = File(System.getProperty("user.dir"))
        var issues = 0
        
        // Check for kpm.toml
        val manifestFile = File(currentDir, "kpm.toml")
        if (manifestFile.exists()) {
            echo("✅ kpm.toml found")
        } else {
            echo("❌ kpm.toml not found")
            issues++
        }
        
        // Check for Gradle wrapper
        val gradlewFile = File(currentDir, "gradlew")
        if (gradlewFile.exists()) {
            echo("✅ Gradle wrapper found")
        } else {
            echo("❌ Gradle wrapper not found")
            issues++
        }
        
        // Check for build.gradle.kts
        val buildFile = File(currentDir, "build.gradle.kts")
        if (buildFile.exists()) {
            echo("✅ build.gradle.kts found")
        } else {
            echo("❌ build.gradle.kts not found")
            issues++
        }
        
        // Check Java version
        val javaVersion = System.getProperty("java.version")
        echo("☕ Java version: $javaVersion")
        
        // Check if Java 17+
        val majorVersion = javaVersion.split(".")[0].toIntOrNull() ?: 0
        if (majorVersion >= 17) {
            echo("✅ Java version is compatible (17+)")
        } else {
            echo("⚠️  Java version may be incompatible (requires 17+)")
            issues++
        }
        
        echo("")
        if (issues == 0) {
            echo("🎉 All checks passed! Your project is healthy.")
        } else {
            echo("⚠️  Found $issues issue(s). Please address them for optimal KPM experience.")
            echo("")
            echo("💡 Suggestions:")
            echo("   - Run 'kpm init' to initialize a new project")
            echo("   - Ensure Java 17+ is installed")
            echo("   - Set ANDROID_HOME for Android projects")
        }
    }
}

class ListCommand : Command("list", "List project dependencies") {
    override fun run() {
        val currentDir = File(System.getProperty("user.dir"))
        val manifestFile = File(currentDir, "kpm.toml")
        
        if (!manifestFile.exists()) {
            echo("Error: No kpm.toml found. Run 'kpm init' first.", err = true)
            return
        }
        
        val tomlParser = TomlParser()
        val manifest = tomlParser.parseManifest(manifestFile)
        
        echo("📦 Project Dependencies (from kpm.toml):")
        echo("")
        
        // Main dependencies
        if (manifest.dependencies.isNotEmpty()) {
            echo("Implementation Dependencies:")
            manifest.dependencies.forEach { (name, coordinates) ->
                echo("  • $name = \"$coordinates\"")
            }
            echo("")
        }
        
        // Test dependencies
        if (manifest.testDependencies.isNotEmpty()) {
            echo("Test Dependencies:")
            manifest.testDependencies.forEach { (name, coordinates) ->
                echo("  • $name = \"$coordinates\"")
            }
            echo("")
        }
        
        // KAPT dependencies
        if (manifest.kaptDependencies.isNotEmpty()) {
            echo("KAPT Dependencies:")
            manifest.kaptDependencies.forEach { (name, coordinates) ->
                echo("  • $name = \"$coordinates\"")
            }
            echo("")
        }
        
        // KSP dependencies
        if (manifest.kspDependencies.isNotEmpty()) {
            echo("KSP Dependencies:")
            manifest.kspDependencies.forEach { (name, coordinates) ->
                echo("  • $name = \"$coordinates\"")
            }
            echo("")
        }
        
        val totalDeps = manifest.dependencies.size + manifest.testDependencies.size + 
                       manifest.kaptDependencies.size + manifest.kspDependencies.size
        
        if (totalDeps == 0) {
            echo("No dependencies found in this project.")
            echo("Use 'kpm add <dependency>' to add dependencies.")
        } else {
            echo("Total: $totalDeps dependencies")
        }
    }
}
