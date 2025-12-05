package com.kpm.ide

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

class IdeSync {
    
    fun triggerGradleSync(projectDir: File) {
        // Only trigger file-based sync methods, don't launch IDEs
        triggerFileBasedSync(projectDir)
    }
    
    private fun triggerFileBasedSync(projectDir: File) {
        try {
            // Touch IntelliJ/Android Studio files to trigger sync
            val ideaDir = File(projectDir, ".idea")
            if (ideaDir.exists()) {
                val gradleXml = File(ideaDir, "gradle.xml")
                if (gradleXml.exists()) {
                    gradleXml.setLastModified(System.currentTimeMillis())
                }
                
                val modulesXml = File(ideaDir, "modules.xml")
                if (modulesXml.exists()) {
                    modulesXml.setLastModified(System.currentTimeMillis())
                }
                
                // Create sync marker for Android Studio
                val syncMarker = File(ideaDir, ".gradle-sync-trigger")
                syncMarker.writeText(System.currentTimeMillis().toString())
            }
            
            // Touch VS Code files if present
            val vscodeDir = File(projectDir, ".vscode")
            if (vscodeDir.exists()) {
                val settingsJson = File(vscodeDir, "settings.json")
                if (settingsJson.exists()) {
                    settingsJson.setLastModified(System.currentTimeMillis())
                }
            }
            
        } catch (e: Exception) {
            // Silently fail - IDE sync is best effort
        }
    }
    
    
    fun createGradleRefreshScript(projectDir: File) {
        try {
            // Create a script that IDEs can detect and use for auto-refresh
            val refreshScript = File(projectDir, ".kpm-refresh")
            refreshScript.writeText("""
                #!/bin/bash
                # KPM Gradle Refresh Trigger
                # This file is updated when dependencies change
                # IDEs can watch this file to trigger automatic Gradle sync
                LAST_UPDATE=${System.currentTimeMillis()}
                echo "Dependencies updated at: $(date)"
            """.trimIndent())
            
            refreshScript.setExecutable(true)
            
        } catch (e: Exception) {
            // Silently fail
        }
    }
}
