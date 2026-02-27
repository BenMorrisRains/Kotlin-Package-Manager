package com.kpm.cli.commands

import com.kpm.cli.Command
import com.kpm.cli.DependencyResolver
import com.kpm.cli.echo
import com.kpm.config.TomlParser
import com.kpm.gradle.GradleGenerator
import com.kpm.model.*
import com.kpm.maven.MavenSearchApi
import java.io.File

class AddCommand : Command("add", "Add a dependency to the project") {
    
    private val dependency by argument("dependency", "Dependency coordinates")
    private val test by option("test", "Add as test dependency").flag()
    private val kapt by option("kapt", "Add as KAPT dependency").flag()
    private val ksp by option("ksp", "Add as KSP dependency").flag()
    
    // CMP source set flags
    private val androidMain by option("android-main", "Add to androidMain.dependencies (CMP only)").flag()
    private val commonMain by option("common-main", "Add to commonMain.dependencies (CMP only)").flag()
    private val commonTest by option("common-test", "Add to commonTest.dependencies (CMP only)").flag()
    
    override fun run() {
        addDependency()
    }
    
    private fun addDependency() {
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
            echo("Error: CMP source set flags (-a, -c, -ct) can only be used with Compose Multiplatform projects.", err = true)
            echo("This project type is: ${manifest.project.type}")
            return
        }
        
        // Parse dependency using shared resolver
        val dep = DependencyResolver.resolveDependencyInteractive(dependency) ?: return
        
        // Handle CMP source set dependencies differently
        if (isCmpFlag) {
            addCmpSourceSetDependency(currentDir, dep)
            return
        }
        
        // Add to appropriate section
        val updatedManifest = when {
            test -> manifest.copy(
                testDependencies = manifest.testDependencies + (dep.artifact to dep.coordinates)
            )
            kapt -> manifest.copy(
                kaptDependencies = manifest.kaptDependencies + (dep.artifact to dep.coordinates)
            )
            ksp -> manifest.copy(
                kspDependencies = manifest.kspDependencies + (dep.artifact to dep.coordinates)
            )
            else -> manifest.copy(
                dependencies = manifest.dependencies + (dep.artifact to dep.coordinates)
            )
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
            
            // Regenerate libs.versions.toml with new dependency
            val libsVersionsToml = gradleGenerator.generateLibsVersionsToml(updatedManifest)
            File(currentDir, "gradle/libs.versions.toml").writeText(libsVersionsToml)
        } else {
            // Traditional structure
            val buildGradle = gradleGenerator.generateBuildGradle(updatedManifest, lockfile, currentDir)
            File(currentDir, "build.gradle.kts").writeText(buildGradle)
        }
        
        val scope = when {
            test -> "test"
            kapt -> "kapt"
            ksp -> "ksp"
            else -> "implementation"
        }
        
        echo("✅ Added $scope dependency: ${dep.coordinates}")
        echo("Updated Gradle build files")
        
        
        // Automatically sync dependencies (like running kpm install)
        val gradlewFile = File(currentDir, "gradlew")
        if (gradlewFile.exists()) {
            echo("Syncing dependencies...")
            try {
                // For Android projects, use a simpler task that doesn't require full compilation
                val isAndroidProject = File(currentDir, "local.properties").exists()
                val gradleTask = if (isAndroidProject) {
                    listOf("./gradlew", "tasks", "--quiet")
                } else {
                    listOf("./gradlew", "dependencies", "--configuration", "compileClasspath", "--quiet")
                }
                
                val gradleProcess = ProcessBuilder(gradleTask)
                    .directory(currentDir)
                    .redirectErrorStream(true)
                    .start()
                
                val exitCode = gradleProcess.waitFor()
                
                if (exitCode == 0) {
                    echo("✅ Dependencies synced successfully")
                } else {
                    if (isAndroidProject) {
                        echo("Dependency added but Gradle sync had issues")
                        echo("Try running 'kpm build' to see if everything works")
                    } else {
                        echo("Dependency added but sync failed")
                    }
                }
            } catch (e: Exception) {
                echo("Dependency added but couldn't sync automatically")
                echo("The dependency was added to kpm.toml and build.gradle.kts successfully")
            }
        } else {
            echo("Gradle wrapper not found. Run 'gradle wrapper' to complete setup.")
        }
    }
    
    private fun addCmpSourceSetDependency(currentDir: File, dep: Dependency) {
        // Determine which source set to add to
        val sourceSet = when {
            androidMain -> "androidMain"
            commonMain -> "commonMain"
            commonTest -> "commonTest"
            else -> {
                echo("Error: No CMP source set specified", err = true)
                return
            }
        }
        
        // Add to kpm.toml manifest
        val manifestFile = File(currentDir, "kpm.toml")
        val tomlParser = TomlParser()
        val manifest = tomlParser.parseManifest(manifestFile)
        
        val updatedManifest = manifest.copy(
            dependencies = manifest.dependencies + (dep.artifact to dep.coordinates)
        )
        tomlParser.writeManifest(updatedManifest, manifestFile)
        
        // Find the composeApp module build.gradle.kts
        val composeAppBuildFile = File(currentDir, "composeApp/build.gradle.kts")
        if (!composeAppBuildFile.exists()) {
            echo("Error: composeApp/build.gradle.kts not found. Is this a valid CMP project?", err = true)
            return
        }
        
        // Read the current build file
        val buildContent = composeAppBuildFile.readText()
        
        // Find the source set dependencies block
        val sourceSetPattern = """$sourceSet\.dependencies\s*\{""".toRegex()
        val match = sourceSetPattern.find(buildContent)
        
        if (match == null) {
            echo("Error: Could not find $sourceSet.dependencies block in composeApp/build.gradle.kts", err = true)
            return
        }
        
        // Find the last implementation line to determine indentation
        val blockStart = match.range.last + 1
        var braceCount = 1
        var insertPosition = blockStart
        var indentation = "            " // default 12 spaces
        
        for (i in blockStart until buildContent.length) {
            when (buildContent[i]) {
                '{' -> braceCount++
                '}' -> {
                    braceCount--
                    if (braceCount == 0) {
                        insertPosition = i
                        break
                    }
                }
            }
            
            // Look for implementation lines to detect indentation
            if (braceCount == 1) {
                val remaining = buildContent.substring(i)
                if (remaining.startsWith("implementation(")) {
                    // Find the start of this line to get indentation
                    var lineStart = i
                    while (lineStart > blockStart && buildContent[lineStart - 1] != '\n') {
                        lineStart--
                    }
                    indentation = buildContent.substring(lineStart, i)
                }
            }
        }
        
        // This will be replaced by the version catalog reference code below
        
        // Add to libs.versions.toml
        val libsVersionsFile = File(currentDir, "gradle/libs.versions.toml")
        if (libsVersionsFile.exists()) {
            updateLibsVersionsToml(libsVersionsFile, dep)
        }
        
        // Use version catalog reference in build.gradle.kts
        // Convert kebab-case to dot notation for Gradle accessor (e.g., compose-charts -> compose.charts)
        val catalogKey = dep.artifact.replace("-", ".")
        val catalogDepLine = "${indentation}implementation(libs.${catalogKey})\n"
        
        // Insert the dependency before the closing brace
        val updatedContent = buildContent.substring(0, insertPosition) + 
                           catalogDepLine + 
                           buildContent.substring(insertPosition)
        
        // Write back to file
        composeAppBuildFile.writeText(updatedContent)
        
        echo("✅ Added dependency to $sourceSet: ${dep.coordinates}")
        echo("Updated kpm.toml, composeApp/build.gradle.kts, and gradle/libs.versions.toml")
    }
    
    private fun updateLibsVersionsToml(libsVersionsFile: File, dep: Dependency) {
        val content = libsVersionsFile.readText()
        val lines = content.split("\n").toMutableList()
        
        // Convert to camelCase for version key (e.g., compose-charts -> composeCharts)
        val versionKey = dep.artifact.split("-").mapIndexed { index, part ->
            if (index == 0) part else part.replaceFirstChar { it.uppercase() }
        }.joinToString("")
        
        // Keep kebab-case for library key (e.g., compose-charts)
        val libraryKey = dep.artifact
        
        // Check if already exists
        val versionExists = lines.any { it.trim().startsWith("$versionKey =") }
        val libraryExists = lines.any { it.trim().startsWith("$libraryKey =") }
        
        // Add version to [versions] section
        if (!versionExists) {
            val versionsIndex = lines.indexOfFirst { it.trim().startsWith("[versions]") }
            if (versionsIndex != -1) {
                // Find last non-empty line in versions section (before blank line or next section)
                var insertIndex = versionsIndex + 1
                while (insertIndex < lines.size) {
                    val trimmed = lines[insertIndex].trim()
                    if (trimmed.startsWith("[") && trimmed != "[versions]") {
                        // Hit next section, insert before it
                        break
                    }
                    if (trimmed.isEmpty()) {
                        // Hit blank line, insert before it
                        break
                    }
                    // This is a version line, keep going
                    insertIndex++
                }
                
                val versionLine = "$versionKey = \"${dep.version}\""
                lines.add(insertIndex, versionLine)
            }
        }
        
        // Add library to [libraries] section
        if (!libraryExists) {
            // Re-find libraries index since we may have added a line
            val librariesIndex = lines.indexOfFirst { it.trim().startsWith("[libraries]") }
            if (librariesIndex != -1) {
                // Find last non-empty line in libraries section (before blank line or next section)
                var insertIndex = librariesIndex + 1
                while (insertIndex < lines.size) {
                    val trimmed = lines[insertIndex].trim()
                    if (trimmed.startsWith("[") && trimmed != "[libraries]") {
                        // Hit next section, insert before it
                        break
                    }
                    if (trimmed.isEmpty()) {
                        // Hit blank line, insert before it
                        break
                    }
                    // This is a library line, keep going
                    insertIndex++
                }
                
                val libraryLine = "$libraryKey = { module = \"${dep.group}:${dep.artifact}\", version.ref = \"$versionKey\" }"
                lines.add(insertIndex, libraryLine)
            }
        }
        
        // Write back to file
        libsVersionsFile.writeText(lines.joinToString("\n"))
    }
}
