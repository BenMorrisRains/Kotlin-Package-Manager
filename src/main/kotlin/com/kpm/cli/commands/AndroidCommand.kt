package com.kpm.cli.commands

import com.kpm.cli.Command
import com.kpm.cli.echo
import com.kpm.android.AndroidSdkManager
import java.io.File

class AndroidCommand : Command("android", "Android SDK management commands") {
    
    private val action by argument("action", "Action: setup, detect, install")
    private val sdkPath by option("sdk-path", "Custom SDK path")
    
    override fun run() {
        val androidSdkManager = AndroidSdkManager()
        
        when (action.lowercase()) {
            "detect" -> detectAndroidSdk(androidSdkManager)
            "setup" -> setupAndroidSdk(androidSdkManager)
            "install" -> installSdkComponents(androidSdkManager)
            else -> {
                echo("Unknown action: $action", err = true)
                echo("Available actions: detect, setup, install")
            }
        }
    }
    
    private fun detectAndroidSdk(androidSdkManager: AndroidSdkManager) {
        echo("🔍 Detecting Android SDK...")
        
        val sdkInfo = androidSdkManager.detectAndroidSdk()
        
        if (sdkInfo != null) {
            echo("✅ Found Android SDK at: ${sdkInfo.path}")
            echo("")
            echo("📱 Build Tools:")
            sdkInfo.buildTools.forEach { version ->
                echo("   - $version")
            }
            echo("")
            echo("📱 Platforms:")
            sdkInfo.platforms.forEach { platform ->
                echo("   - $platform")
            }
            echo("")
            echo("🛠️  Command Line Tools: ${if (sdkInfo.cmdlineTools) "✅ Available" else "❌ Not found"}")
            
            // Check current project
            val currentDir = File(System.getProperty("user.dir"))
            val localProperties = File(currentDir, "local.properties")
            if (localProperties.exists()) {
                echo("📁 Current project SDK: ${localProperties.readText().trim()}")
            } else {
                echo("📁 Current project: No local.properties found")
                echo("💡 Run 'kpm android setup' to configure this project")
            }
            
        } else {
            echo("❌ No Android SDK found")
            echo("")
            echo("📱 To install Android SDK:")
            echo("   Option 1: kpm android setup")
            echo("   Option 2: Install Android Studio")
            echo("   Option 3: Download SDK manually from https://developer.android.com/studio")
        }
    }
    
    private fun setupAndroidSdk(androidSdkManager: AndroidSdkManager) {
        val currentDir = File(System.getProperty("user.dir"))
        
        // Check if custom SDK path was provided
        if (sdkPath != null) {
            val sdkDir = File(sdkPath!!)
            if (sdkDir.exists()) {
                echo("🔧 Using custom Android SDK: $sdkPath")
                val localProperties = File(currentDir, "local.properties")
                localProperties.writeText("sdk.dir=$sdkPath")
                echo("✅ Android SDK configured for current project")
                return
            } else {
                echo("❌ Custom SDK path not found: $sdkPath", err = true)
                return
            }
        }
        
        // Try to detect existing SDK first
        val sdkInfo = androidSdkManager.detectAndroidSdk()
        
        if (sdkInfo != null) {
            echo("✅ Found existing Android SDK at: ${sdkInfo.path}")
            androidSdkManager.setupAndroidSdkEnvironment(currentDir, sdkInfo)
            echo("✅ Android SDK configured for current project")
        } else {
            echo("📥 No Android SDK found. Downloading and setting up...")
            
            // Create SDK directory
            val sdkDir = File(System.getProperty("user.home"), "Android/Sdk")
            sdkDir.mkdirs()
            
            // Download and setup SDK
            if (androidSdkManager.downloadAndroidCommandLineTools(sdkDir)) {
                // Install essential components
                val components = listOf(
                    "platform-tools",
                    "platforms;android-35",
                    "platforms;android-34", 
                    "build-tools;35.0.0",
                    "build-tools;34.0.0"
                )
                
                if (androidSdkManager.installAndroidSdkComponents(sdkDir.absolutePath, components)) {
                    echo("✅ Android SDK setup completed")
                    val localProperties = File(currentDir, "local.properties")
                    localProperties.writeText("sdk.dir=${sdkDir.absolutePath}")
                    echo("✅ Android SDK configured for current project")
                } else {
                    echo("❌ Failed to install Android SDK components", err = true)
                }
            } else {
                echo("❌ Failed to download Android SDK", err = true)
                echo("💡 Try installing Android Studio instead: https://developer.android.com/studio")
            }
        }
    }
    
    private fun installSdkComponents(androidSdkManager: AndroidSdkManager) {
        val sdkInfo = androidSdkManager.detectAndroidSdk()
        
        if (sdkInfo == null) {
            echo("❌ No Android SDK found. Run 'kpm android setup' first.", err = true)
            return
        }
        
        echo("📦 Available SDK components to install:")
        echo("   1. Latest platform (android-35)")
        echo("   2. Build tools (35.0.0)")
        echo("   3. Platform tools")
        echo("   4. Emulator")
        echo("   5. System images")
        echo("")
        echo("💡 To install specific components, use:")
        echo("   ${sdkInfo.path}/cmdline-tools/latest/bin/sdkmanager \"platforms;android-35\"")
        echo("")
        
        // Install commonly needed components
        val components = listOf(
            "platform-tools",
            "platforms;android-35",
            "build-tools;35.0.0"
        )
        
        echo("🔄 Installing essential components...")
        if (androidSdkManager.installAndroidSdkComponents(sdkInfo.path, components)) {
            echo("✅ Essential Android SDK components installed")
        } else {
            echo("❌ Failed to install some components", err = true)
        }
    }
}
