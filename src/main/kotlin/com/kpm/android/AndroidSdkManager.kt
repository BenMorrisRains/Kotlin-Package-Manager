package com.kpm.android

import java.io.File
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class AndroidSdkManager {
    
    fun detectAndroidSdk(): AndroidSdkInfo? {
        // Check common Android SDK locations
        val possiblePaths = listOf(
            System.getenv("ANDROID_HOME"),
            System.getenv("ANDROID_SDK_ROOT"),
            "${System.getProperty("user.home")}/Android/Sdk",
            "${System.getProperty("user.home")}/Library/Android/sdk",
            "/opt/android-sdk",
            "/usr/local/android-sdk"
        ).filterNotNull()
        
        for (path in possiblePaths) {
            val sdkDir = File(path)
            if (isValidAndroidSdk(sdkDir)) {
                return AndroidSdkInfo(
                    path = path,
                    buildTools = detectBuildTools(sdkDir),
                    platforms = detectPlatforms(sdkDir),
                    cmdlineTools = detectCmdlineTools(sdkDir)
                )
            }
        }
        
        return null
    }
    
    private fun isValidAndroidSdk(dir: File): Boolean {
        if (!dir.exists() || !dir.isDirectory) return false
        
        // Check for essential SDK components
        val platformsDir = File(dir, "platforms")
        val buildToolsDir = File(dir, "build-tools")
        
        return platformsDir.exists() && buildToolsDir.exists()
    }
    
    private fun detectBuildTools(sdkDir: File): List<String> {
        val buildToolsDir = File(sdkDir, "build-tools")
        if (!buildToolsDir.exists()) return emptyList()
        
        return buildToolsDir.listFiles()
            ?.filter { it.isDirectory }
            ?.map { it.name }
            ?.sortedDescending() ?: emptyList()
    }
    
    private fun detectPlatforms(sdkDir: File): List<String> {
        val platformsDir = File(sdkDir, "platforms")
        if (!platformsDir.exists()) return emptyList()
        
        return platformsDir.listFiles()
            ?.filter { it.isDirectory }
            ?.map { it.name }
            ?.sortedDescending() ?: emptyList()
    }
    
    private fun detectCmdlineTools(sdkDir: File): Boolean {
        val cmdlineToolsDir = File(sdkDir, "cmdline-tools")
        return cmdlineToolsDir.exists()
    }
    
    fun setupAndroidSdkEnvironment(projectDir: File, sdkInfo: AndroidSdkInfo) {
        // Create local.properties file
        val localProperties = File(projectDir, "local.properties")
        localProperties.writeText("sdk.dir=${sdkInfo.path}")
        
        // Suggest environment variable setup
        println("To make Android SDK available globally, add this to your shell profile:")
        println("   export ANDROID_HOME=\"${sdkInfo.path}\"")
        println("   export PATH=\"\$PATH:\$ANDROID_HOME/platform-tools:\$ANDROID_HOME/cmdline-tools/latest/bin\"")
    }
    
    fun downloadAndroidCommandLineTools(targetDir: File): Boolean {
        try {
            val os = System.getProperty("os.name").lowercase()
            val downloadUrl = when {
                os.contains("mac") -> "https://dl.google.com/android/repository/commandlinetools-mac-11076708_latest.zip"
                os.contains("linux") -> "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
                os.contains("windows") -> "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip"
                else -> return false
            }
            
            println("📥 Downloading Android Command Line Tools...")
            val tempFile = File.createTempFile("cmdline-tools", ".zip")
            
            URL(downloadUrl).openStream().use { input ->
                Files.copy(input, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            
            // Extract the zip file (simplified - in real implementation use proper zip library)
            println("📦 Extracting Android Command Line Tools...")
            val extractProcess = ProcessBuilder("unzip", "-q", tempFile.absolutePath, "-d", targetDir.absolutePath)
                .start()
            
            val exitCode = extractProcess.waitFor()
            tempFile.delete()
            
            if (exitCode == 0) {
                println("✅ Android Command Line Tools downloaded successfully")
                return true
            }
        } catch (e: Exception) {
            println("❌ Failed to download Android Command Line Tools: ${e.message}")
        }
        
        return false
    }
    
    fun installAndroidSdkComponents(sdkPath: String, components: List<String>): Boolean {
        try {
            val sdkManager = File(sdkPath, "cmdline-tools/latest/bin/sdkmanager")
            if (!sdkManager.exists()) {
                println("❌ SDK Manager not found. Please install Android Command Line Tools first.")
                return false
            }
            
            println("📦 Installing Android SDK components: ${components.joinToString(", ")}")
            
            val process = ProcessBuilder(sdkManager.absolutePath, "--install", *components.toTypedArray())
                .redirectErrorStream(true)
                .start()
            
            // Stream output
            process.inputStream.bufferedReader().use { reader ->
                reader.lineSequence().forEach { line ->
                    println(line)
                }
            }
            
            val exitCode = process.waitFor()
            return exitCode == 0
        } catch (e: Exception) {
            println("❌ Failed to install SDK components: ${e.message}")
            return false
        }
    }
}

data class AndroidSdkInfo(
    val path: String,
    val buildTools: List<String>,
    val platforms: List<String>,
    val cmdlineTools: Boolean
)
