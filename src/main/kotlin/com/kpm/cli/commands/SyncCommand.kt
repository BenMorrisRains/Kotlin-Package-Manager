package com.kpm.cli.commands

import com.kpm.cli.Command
import com.kpm.cli.echo
import com.kpm.config.TomlParser
import com.kpm.config.GlobalConfigManager
import com.kpm.gradle.GradleGenerator
import com.kpm.model.KpmLockfile
import com.kpm.ide.IdeSync
import java.io.File

class SyncCommand : Command("sync", "Regenerate build files from kpm.toml") {
    
    override fun run() {
        val currentDir = File(System.getProperty("user.dir"))
        val manifestFile = File(currentDir, "kpm.toml")
        
        if (!manifestFile.exists()) {
            echo("❌ No kpm.toml found in current directory", err = true)
            echo("Run this command from a KPM project directory", err = true)
            return
        }
        
        echo("Syncing build files with kpm.toml...")
        
        try {
            val tomlParser = TomlParser()
            val manifest = tomlParser.parseManifest(manifestFile)
            
            // Sync global dependencies (add new ones, remove deleted ones)
            val globalConfigManager = GlobalConfigManager()
            val globalConfig = globalConfigManager.getGlobalConfig()
            val globalDeps = globalConfig.globalDependencies.alwaysInclude
            
            // Find dependencies that were added/removed from global config
            val currentDeps = manifest.dependencies.toMutableMap()
            val originalGlobalDeps = currentDeps.filter { (key, value) ->
                // Check if this dependency might be from global config by checking if it exists in current global config
                // or if it's a common global dependency pattern
                globalDeps.containsKey(key) || 
                listOf("timber", "retrofit", "gson", "okhttp", "leakcanary", "picasso", "glide").contains(key)
            }
            
            // Remove global dependencies that are no longer in global config
            val depsToRemove = originalGlobalDeps.keys.filter { !globalDeps.containsKey(it) }
            if (depsToRemove.isNotEmpty()) {
                echo("Removing outdated global dependencies: ${depsToRemove.joinToString(", ")}")
                depsToRemove.forEach { currentDeps.remove(it) }
            }
            
            // Add new global dependencies
            val depsToAdd = globalDeps.filter { !currentDeps.containsKey(it.key) }
            if (depsToAdd.isNotEmpty()) {
                echo("Adding new global dependencies: ${depsToAdd.keys.joinToString(", ")}")
                currentDeps.putAll(depsToAdd)
            }
            
            val updatedManifest = manifest.copy(dependencies = currentDeps)
            
            // Write updated manifest if any changes were made
            if (depsToRemove.isNotEmpty() || depsToAdd.isNotEmpty()) {
                tomlParser.writeManifest(updatedManifest, manifestFile)
            }
            
            // Read or create lockfile
            val lockfileFile = File(currentDir, "kpm.lock")
            val lockfile = if (lockfileFile.exists()) {
                tomlParser.parseLockfile(lockfileFile)
            } else {
                KpmLockfile()
            }
            
            // Regenerate build.gradle.kts
            val gradleGenerator = GradleGenerator()
            val buildGradle = gradleGenerator.generateBuildGradle(updatedManifest, lockfile, currentDir)
            File(currentDir, "build.gradle.kts").writeText(buildGradle)
            
            // Regenerate settings.gradle.kts if needed
            val settingsGradle = gradleGenerator.generateSettingsGradle(updatedManifest)
            File(currentDir, "settings.gradle.kts").writeText(settingsGradle)
            
            echo("✅ Build files synchronized successfully")
            echo("Updated: build.gradle.kts, settings.gradle.kts")
            
            // Trigger IDE sync
            val ideSync = IdeSync()
            ideSync.triggerGradleSync(currentDir)
            ideSync.createGradleRefreshScript(currentDir)
            echo("Triggering IDE sync...")
            
        } catch (e: Exception) {
            echo("❌ Failed to sync build files: ${e.message}", err = true)
        }
    }
}
