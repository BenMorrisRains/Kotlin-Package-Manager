package com.kpm.cli.commands

import com.kpm.cli.Command
import com.kpm.cli.echo
import com.kpm.config.GlobalConfigManager
import com.kpm.config.TomlParser
import com.kpm.gradle.GradleGenerator
import com.kpm.model.KpmLockfile
import java.io.File

class GlobalSyncCommand : Command("sync-global", "Sync local project with global dependencies") {
    
    override fun run() {
        val currentDir = File(System.getProperty("user.dir"))
        val manifestFile = File(currentDir, "kpm.toml")
        
        if (!manifestFile.exists()) {
            echo("Error: No kpm.toml found. Run 'kpm init' first.", err = true)
            return
        }
        
        echo("🔄 Syncing project with global dependencies...")
        
        val tomlParser = TomlParser()
        val manifest = tomlParser.parseManifest(manifestFile)
        val globalConfig = GlobalConfigManager().getGlobalConfig()
        
        // Get current global dependencies
        val globalDeps = globalConfig.globalDependencies.alwaysInclude
        val currentDeps = manifest.dependencies.toMutableMap()
        
        // Track changes
        val added = mutableListOf<String>()
        val removed = mutableListOf<String>()
        val updated = mutableListOf<String>()
        
        // Add missing global dependencies
        globalDeps.forEach { (name, coordinates) ->
            if (!currentDeps.containsKey(name)) {
                currentDeps[name] = coordinates
                added.add("$name = \"$coordinates\"")
            } else if (currentDeps[name] != coordinates) {
                val oldCoords = currentDeps[name]
                currentDeps[name] = coordinates
                updated.add("$name: \"$oldCoords\" → \"$coordinates\"")
            }
        }
        
        // Find dependencies that were removed from global config
        val globalDepNames = globalDeps.keys
        val localGlobalDeps = currentDeps.filter { (name, _) ->
            // Check if this dependency was likely added by global config
            // (This is a heuristic - we could improve this with metadata)
            name in globalDepNames || isLikelyGlobalDependency(name, manifest.project.type.toString())
        }
        
        localGlobalDeps.forEach { (name, coordinates) ->
            if (!globalDeps.containsKey(name) && isLikelyGlobalDependency(name, manifest.project.type.toString())) {
                currentDeps.remove(name)
                removed.add("$name = \"$coordinates\"")
            }
        }
        
        // Update manifest if changes were made
        if (added.isNotEmpty() || removed.isNotEmpty() || updated.isNotEmpty()) {
            val updatedManifest = manifest.copy(dependencies = currentDeps)
            tomlParser.writeManifest(updatedManifest, manifestFile)
            
            // Regenerate Gradle files
            val lockfileFile = File(currentDir, "kpm.lock")
            val lockfile = if (lockfileFile.exists()) {
                tomlParser.parseLockfile(lockfileFile)
            } else {
                KpmLockfile()
            }
            
            val gradleGenerator = GradleGenerator()
            val buildGradle = gradleGenerator.generateBuildGradle(updatedManifest, lockfile, currentDir)
            File(currentDir, "build.gradle.kts").writeText(buildGradle)
            
            // Show summary
            echo("✅ Project synced with global dependencies!")
            echo("")
            
            if (added.isNotEmpty()) {
                echo("📦 Added dependencies:")
                added.forEach { echo("  + $it") }
                echo("")
            }
            
            if (updated.isNotEmpty()) {
                echo("🔄 Updated dependencies:")
                updated.forEach { echo("  ~ $it") }
                echo("")
            }
            
            if (removed.isNotEmpty()) {
                echo("🗑️  Removed dependencies:")
                removed.forEach { echo("  - $it") }
                echo("")
            }
            
            echo("Gradle files have been regenerated.")
            echo("Run './gradlew build' to apply changes.")
            
        } else {
            echo("✅ Project is already in sync with global dependencies.")
        }
    }
    
    private fun isLikelyGlobalDependency(name: String, projectType: String): Boolean {
        // Heuristic to determine if a dependency was likely added by global config
        // This could be improved by storing metadata about dependency sources
        
        val commonProjectDeps = when (projectType.lowercase()) {
            "android_app" -> setOf("coreKtx", "appcompat", "material", "composeBom", "composeUi", "activityCompose")
            "android_library" -> setOf("coreKtx")
            "ktor_api" -> setOf("ktorServerCore", "ktorServerNetty", "ktorSerialization", "logback")
            "jvm_application" -> setOf("coroutines")
            else -> emptySet()
        }
        
        // If it's not a common project dependency, it's likely global
        return name !in commonProjectDeps
    }
}
