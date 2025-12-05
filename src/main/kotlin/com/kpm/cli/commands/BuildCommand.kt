package com.kpm.cli.commands

import com.kpm.cli.Command
import com.kpm.cli.echo
import com.kpm.config.TomlParser
import com.kpm.model.ProjectType
import java.io.File

class BuildCommand : Command("build", "Build the project") {
    
    private val release by option("release", "Build release variant").flag()
    
    override fun run() {
        val currentDir = File(System.getProperty("user.dir"))
        val manifestFile = File(currentDir, "kpm.toml")
        
        if (!manifestFile.exists()) {
            echo("Error: No kpm.toml found. Run 'kpm init' first.", err = true)
            return
        }
        
        val tomlParser = TomlParser()
        val manifest = tomlParser.parseManifest(manifestFile)
        
        echo("🔨 Building ${manifest.project.name}...")
        
        val gradleTask = when (manifest.project.type) {
            ProjectType.ANDROID_APP -> if (release) "assembleRelease" else "assembleDebug"
            ProjectType.ANDROID_LIBRARY -> if (release) "bundleReleaseAar" else "bundleDebugAar"
            ProjectType.JVM_APPLICATION -> "build"
            ProjectType.JVM_LIBRARY -> "build"
            ProjectType.MULTIPLATFORM_LIBRARY -> "build"
            ProjectType.KTOR_API -> "build"
        }
        
        // Check if gradlew exists
        val gradlewFile = File(currentDir, "gradlew")
        if (!gradlewFile.exists()) {
            echo("Error: Gradle wrapper not found. Run 'kpm init' to create a new project.", err = true)
            return
        }
        
        val gradleProcess = ProcessBuilder("./gradlew", gradleTask)
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
            echo("✅ Build completed successfully")
            
            // Show build artifacts
            when (manifest.project.type) {
                ProjectType.ANDROID_APP -> {
                    val variant = if (release) "release" else "debug"
                    val apkPath = "build/outputs/apk/$variant/${manifest.project.name}-$variant.apk"
                    echo("📱 APK: $apkPath")
                }
                ProjectType.ANDROID_LIBRARY -> {
                    val variant = if (release) "release" else "debug"
                    val aarPath = "build/outputs/aar/${manifest.project.name}-$variant.aar"
                    echo("📚 AAR: $aarPath")
                }
                else -> {
                    echo("📦 JAR: build/libs/${manifest.project.name}-${manifest.project.version}.jar")
                }
            }
        } else {
            echo("❌ Build failed with exit code $exitCode", err = true)
        }
    }
}
