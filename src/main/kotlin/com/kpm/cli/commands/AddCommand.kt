package com.kpm.cli.commands

import com.kpm.cli.Command
import com.kpm.cli.echo
import com.kpm.config.TomlParser
import com.kpm.gradle.GradleGenerator
import com.kpm.model.*
import com.kpm.maven.MavenSearchApi
import java.io.File

class AddCommand : Command("add", "Add a dependency to the project") {
    
    private val dependency by argument("dependency", "Dependency coordinates (group:artifact:version)")
    private val test by option("test", "Add as test dependency").flag()
    private val kapt by option("kapt", "Add as KAPT dependency").flag()
    private val ksp by option("ksp", "Add as KSP dependency").flag()
    
    override fun run() {
        val currentDir = File(System.getProperty("user.dir"))
        val manifestFile = File(currentDir, "kpm.toml")
        
        if (!manifestFile.exists()) {
            echo("Error: No kpm.toml found. Run 'kpm init' first.", err = true)
            return
        }
        
        val tomlParser = TomlParser()
        val manifest = tomlParser.parseManifest(manifestFile)
        
        // Parse dependency
        val dep = try {
            if (":" in dependency) {
                Dependency.parse(dependency)
            } else {
                // Try to resolve shorthand
                resolveShorthandDependency(dependency)
            }
        } catch (e: Exception) {
            echo("Error: Invalid dependency format. Use group:artifact:version or just artifact name", err = true)
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
        val buildGradle = gradleGenerator.generateBuildGradle(updatedManifest, lockfile, currentDir)
        File(currentDir, "build.gradle.kts").writeText(buildGradle)
        
        val scope = when {
            test -> "test"
            kapt -> "kapt"
            ksp -> "ksp"
            else -> "implementation"
        }
        
        echo("✅ Added $scope dependency: ${dep.coordinates}")
        echo("Updating build.gradle.kts...")
        
        
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
    
    private fun resolveShorthandDependency(shorthand: String): Dependency {
        val mavenApi = MavenSearchApi()
        
        // First check well-known artifacts for instant resolution
        val wellKnown = mavenApi.getWellKnownArtifact(shorthand)
        if (wellKnown != null) {
            echo("Found well-known artifact: ${wellKnown.groupId}:${wellKnown.artifactId}")
            return Dependency(
                group = wellKnown.groupId,
                artifact = wellKnown.artifactId,
                version = wellKnown.latestVersion
            )
        }
        
        // Try to search Maven Central for the artifact
        echo("Searching Maven Central for '$shorthand'...")
        val searchResult = mavenApi.findPopularArtifact(shorthand)
        
        if (searchResult != null) {
            echo("✅ Found: ${searchResult.groupId}:${searchResult.artifactId}:${searchResult.latestVersion}")
            return Dependency(
                group = searchResult.groupId,
                artifact = searchResult.artifactId,
                version = searchResult.latestVersion
            )
        }
        
        // Fallback to common shorthand mappings
        val commonDependencies = mapOf(
            "junit" to "junit:junit:4.13.2",
            "mockito" to "org.mockito:mockito-core:5.7.0",
            "gson" to "com.google.code.gson:gson:2.10.1",
            "retrofit" to "com.squareup.retrofit2:retrofit:2.9.0",
            "okhttp" to "com.squareup.okhttp3:okhttp:4.12.0",
            "picasso" to "com.squareup.picasso:picasso:2.8",
            "glide" to "com.github.bumptech.glide:glide:4.16.0",
            "coroutines" to "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3",
            "serialization" to "org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0",
            "ktor-client" to "io.ktor:ktor-client-core:2.3.6",
            "ktor-server" to "io.ktor:ktor-server-core:2.3.6",
            "compose-bom" to "androidx.compose:compose-bom:2024.10.00",
            "compose-ui" to "androidx.compose.ui:ui:1.5.4",
            "compose-material3" to "androidx.compose.material3:material3:1.1.2",
            "hilt" to "com.google.dagger:hilt-android:2.48",
            "room" to "androidx.room:room-runtime:2.6.1",
            "navigation" to "androidx.navigation:navigation-compose:2.7.5"
        )
        
        val coordinates = commonDependencies[shorthand.lowercase()]
        if (coordinates != null) {
            echo("📚 Using built-in mapping for '$shorthand'")
            return Dependency.parse(coordinates)
        }
        
        throw IllegalArgumentException("Could not find artifact '$shorthand' in Maven Central or built-in mappings")
    }
}
