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
        
        // Parse dependency using shared resolver
        val dep = DependencyResolver.resolveDependencyInteractive(dependency) ?: return
        
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
}
