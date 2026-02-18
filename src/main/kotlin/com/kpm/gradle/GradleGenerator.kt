package com.kpm.gradle

import com.kpm.model.*
import com.kpm.maven.MavenSearchApi
import java.io.File

class GradleGenerator {
    
    fun generateBuildGradle(manifest: KpmManifest, lockfile: KpmLockfile, projectDir: File): String {
        // For Android projects, use modern multi-module structure with version catalog
        if (manifest.project.type == ProjectType.ANDROID_APP || manifest.project.type == ProjectType.ANDROID_LIBRARY) {
            return generateModernAndroidBuildGradle(manifest)
        }
        
        // For non-Android projects, use traditional structure
        return generateTraditionalBuildGradle(manifest, lockfile)
    }
    
    private fun generateModernAndroidBuildGradle(manifest: KpmManifest): String {
        val builder = StringBuilder()
        
        // Root build.gradle.kts for Android projects
        builder.appendLine("// Top-level build file where you can add configuration options common to all sub-projects/modules.")
        builder.appendLine("plugins {")
        
        val pluginType = if (manifest.project.type == ProjectType.ANDROID_APP) "application" else "library"
        builder.appendLine("    alias(libs.plugins.android.$pluginType) apply false")
        builder.appendLine("    alias(libs.plugins.kotlin.android) apply false")
        
        val hasCompose = manifest.dependencies.keys.any { it.startsWith("compose") || it == "composeBom" }
        if (hasCompose) {
            builder.appendLine("    alias(libs.plugins.kotlin.compose) apply false")
        }
        
        builder.appendLine("}")
        
        return builder.toString()
    }
    
    fun generateAppBuildGradle(manifest: KpmManifest): String {
        val builder = StringBuilder()
        
        // App module build.gradle.kts
        builder.appendLine("plugins {")
        
        val pluginType = if (manifest.project.type == ProjectType.ANDROID_APP) "application" else "library"
        builder.appendLine("    alias(libs.plugins.android.$pluginType)")
        builder.appendLine("    alias(libs.plugins.kotlin.android)")
        
        val hasCompose = manifest.dependencies.keys.any { it.startsWith("compose") || it == "composeBom" }
        if (hasCompose) {
            builder.appendLine("    alias(libs.plugins.kotlin.compose)")
        }
        
        builder.appendLine("}")
        builder.appendLine()
        
        // Android configuration
        manifest.android?.let { android ->
            generateModernAndroidConfig(android, builder, hasCompose)
        }
        builder.appendLine()
        
        // Dependencies using version catalog
        builder.appendLine("dependencies {")
        generateModernDependencies(manifest, builder)
        builder.appendLine("}")
        
        return builder.toString()
    }
    
    private fun generateModernAndroidConfig(android: AndroidConfig, builder: StringBuilder, hasCompose: Boolean) {
        builder.appendLine("android {")
        android.namespace?.let { 
            builder.appendLine("    namespace = \"$it\"")
        }
        builder.appendLine("    compileSdk = ${android.compileSdk}")
        builder.appendLine()
        builder.appendLine("    defaultConfig {")
        android.applicationId?.let {
            builder.appendLine("        applicationId = \"$it\"")
        }
        builder.appendLine("        minSdk = ${android.minSdk}")
        builder.appendLine("        targetSdk = ${android.targetSdk}")
        builder.appendLine("        versionCode = 1")
        builder.appendLine("        versionName = \"1.0\"")
        builder.appendLine("        testInstrumentationRunner = \"androidx.test.runner.AndroidJUnitRunner\"")
        builder.appendLine("    }")
        builder.appendLine()
        builder.appendLine("    buildTypes {")
        builder.appendLine("        release {")
        builder.appendLine("            isMinifyEnabled = false")
        builder.appendLine("            proguardFiles(")
        builder.appendLine("                getDefaultProguardFile(\"proguard-android-optimize.txt\"),")
        builder.appendLine("                \"proguard-rules.pro\"")
        builder.appendLine("            )")
        builder.appendLine("        }")
        builder.appendLine("    }")
        builder.appendLine()
        builder.appendLine("    compileOptions {")
        builder.appendLine("        sourceCompatibility = JavaVersion.VERSION_17")
        builder.appendLine("        targetCompatibility = JavaVersion.VERSION_17")
        builder.appendLine("    }")
        builder.appendLine()
        builder.appendLine("    kotlinOptions {")
        builder.appendLine("        jvmTarget = \"17\"")
        builder.appendLine("    }")
        
        if (hasCompose) {
            builder.appendLine()
            builder.appendLine("    buildFeatures {")
            builder.appendLine("        compose = true")
            builder.appendLine("    }")
        }
        
        builder.appendLine("}")
    }
    
    private fun generateModernDependencies(manifest: KpmManifest, builder: StringBuilder) {
        // Main dependencies using version catalog aliases
        manifest.dependencies.forEach { (key, _) ->
            // Keep camelCase for Kotlin DSL compatibility (no hyphens)
            val libKey = key.replaceFirstChar { it.lowercase() }
            if (key.contains("Bom") || key.contains("bom")) {
                builder.appendLine("    implementation(platform(libs.$libKey))")
            } else {
                builder.appendLine("    implementation(libs.$libKey)")
            }
        }
        
        // Test dependencies
        manifest.testDependencies.forEach { (key, _) ->
            // Keep camelCase for Kotlin DSL compatibility (no hyphens)
            val libKey = key.replaceFirstChar { it.lowercase() }
            builder.appendLine("    testImplementation(libs.$libKey)")
        }
    }
    
    private fun generateTraditionalBuildGradle(manifest: KpmManifest, lockfile: KpmLockfile): String {
        val builder = StringBuilder()
        
        // Plugins
        builder.appendLine("plugins {")
        generatePlugins(manifest, builder)
        builder.appendLine("}")
        builder.appendLine()
        
        // Android configuration
        manifest.android?.let { android ->
            generateAndroidConfig(android, builder, manifest)
        }
        builder.appendLine()
        
        // Repositories
        builder.appendLine("repositories {")
        generateRepositories(manifest.repositories, builder)
        builder.appendLine("}")
        builder.appendLine()
        
        // Dependencies
        builder.appendLine("dependencies {")
        generateDependencies(manifest, lockfile, builder)
        builder.appendLine("}")
        builder.appendLine()
        
        // Kotlin configuration
        generateKotlinConfig(manifest, builder)
        
        return builder.toString()
    }
    
    private fun generatePlugins(manifest: KpmManifest, builder: StringBuilder) {
        val hasCompose = manifest.dependencies.keys.any { it.startsWith("compose") || it == "composeBom" }
        // Use AGP version from manifest, fallback to 8.7.3 if not specified
        val agpVersion = manifest.project.agpVersion ?: "8.7.3"
        
        when (manifest.project.type) {
            ProjectType.ANDROID_APP -> {
                builder.appendLine("    id(\"com.android.application\") version \"$agpVersion\"")
                builder.appendLine("    id(\"org.jetbrains.kotlin.android\") version \"${manifest.project.kotlinVersion}\"")
                if (hasCompose) {
                    addComposePlugin(manifest.project.kotlinVersion, builder)
                }
            }
            ProjectType.ANDROID_LIBRARY -> {
                builder.appendLine("    id(\"com.android.library\") version \"$agpVersion\"")
                builder.appendLine("    id(\"org.jetbrains.kotlin.android\") version \"${manifest.project.kotlinVersion}\"")
                if (hasCompose) {
                    addComposePlugin(manifest.project.kotlinVersion, builder)
                }
            }
            ProjectType.JVM_APPLICATION -> {
                builder.appendLine("    kotlin(\"jvm\") version \"${manifest.project.kotlinVersion}\"")
                builder.appendLine("    application")
            }
            ProjectType.JVM_LIBRARY -> {
                builder.appendLine("    kotlin(\"jvm\") version \"${manifest.project.kotlinVersion}\"")
            }
            ProjectType.MULTIPLATFORM_LIBRARY -> {
                builder.appendLine("    kotlin(\"multiplatform\") version \"${manifest.project.kotlinVersion}\"")
            }
            ProjectType.KTOR_API -> {
                builder.appendLine("    kotlin(\"jvm\") version \"${manifest.project.kotlinVersion}\"")
                builder.appendLine("    application")
                builder.appendLine("    id(\"io.ktor.plugin\") version \"2.3.6\"")
            }
        }
        
        // Custom plugins from manifest
        manifest.plugins.forEach { (_, pluginId) ->
            builder.appendLine("    id(\"$pluginId\")")
        }
    }
    
    private fun addComposePlugin(kotlinVersion: String, builder: StringBuilder) {
        // Kotlin 2.0+ uses the new compose compiler plugin
        // Kotlin 1.x doesn't need a separate plugin (uses composeOptions in android block)
        val version = parseVersion(kotlinVersion)
        if (version.major >= 2) {
            builder.appendLine("    id(\"org.jetbrains.kotlin.plugin.compose\") version \"$kotlinVersion\"")
        }
    }
    
    private fun parseVersion(versionString: String): Version {
        val parts = versionString.split(".").map { it.toIntOrNull() ?: 0 }
        return Version(
            major = parts.getOrNull(0) ?: 0,
            minor = parts.getOrNull(1) ?: 0,
            patch = parts.getOrNull(2) ?: 0
        )
    }
    
    private data class Version(val major: Int, val minor: Int, val patch: Int)
    
    private fun generateAndroidConfig(android: AndroidConfig, builder: StringBuilder, manifest: KpmManifest) {
        builder.appendLine("android {")
        android.namespace?.let { 
            builder.appendLine("    namespace = \"$it\"")
        }
        builder.appendLine("    compileSdk = ${android.compileSdk}")
        builder.appendLine()
        builder.appendLine("    defaultConfig {")
        android.applicationId?.let {
            builder.appendLine("        applicationId = \"$it\"")
        }
        builder.appendLine("        minSdk = ${android.minSdk}")
        builder.appendLine("        targetSdk = ${android.targetSdk}")
        builder.appendLine("        versionCode = 1")
        builder.appendLine("        versionName = \"1.0\"")
        builder.appendLine("        testInstrumentationRunner = \"androidx.test.runner.AndroidJUnitRunner\"")
        builder.appendLine("    }")
        builder.appendLine()
        builder.appendLine("    buildTypes {")
        builder.appendLine("        release {")
        builder.appendLine("            isMinifyEnabled = false")
        builder.appendLine("            proguardFiles(getDefaultProguardFile(\"proguard-android-optimize.txt\"), \"proguard-rules.pro\")")
        builder.appendLine("        }")
        builder.appendLine("    }")
        builder.appendLine()
        builder.appendLine("    compileOptions {")
        builder.appendLine("        sourceCompatibility = JavaVersion.VERSION_17")
        builder.appendLine("        targetCompatibility = JavaVersion.VERSION_17")
        builder.appendLine("    }")
        builder.appendLine()
        builder.appendLine("    kotlinOptions {")
        builder.appendLine("        jvmTarget = \"17\"")
        builder.appendLine("    }")
        
        // Add Compose configuration if Compose dependencies are detected
        val hasCompose = manifest.dependencies.any { it.key.contains("compose") || it.value.contains("compose") }
        if (hasCompose) {
            builder.appendLine()
            builder.appendLine("    buildFeatures {")
            builder.appendLine("        compose = true")
            builder.appendLine("    }")
            builder.appendLine()
            builder.appendLine("    composeOptions {")
            builder.appendLine("        kotlinCompilerExtensionVersion = \"1.5.15\"")
            builder.appendLine("    }")
        }
        
        builder.appendLine("}")
    }
    
    private fun generateRepositories(repositories: RepositoryConfig, builder: StringBuilder) {
        if (repositories.mavenCentral) {
            builder.appendLine("    mavenCentral()")
        }
        if (repositories.google) {
            builder.appendLine("    google()")
        }
        if (repositories.gradlePluginPortal) {
            builder.appendLine("    gradlePluginPortal()")
        }
        repositories.custom.forEach { repo ->
            builder.appendLine("    maven { url = uri(\"$repo\") }")
        }
    }
    
    private fun generateDependencies(manifest: KpmManifest, lockfile: KpmLockfile, builder: StringBuilder) {
        // Main dependencies
        manifest.dependencies.forEach { (key, coordinates) ->
            val resolvedVersion = lockfile.dependencies[coordinates] ?: coordinates
            if (coordinates.contains("compose-bom") || coordinates.contains("-bom:")) {
                // Handle BOM (Bill of Materials) dependencies
                builder.appendLine("    implementation(platform(\"$resolvedVersion\"))")
            } else {
                builder.appendLine("    implementation(\"$resolvedVersion\")")
            }
        }
        
        // Test dependencies
        manifest.testDependencies.forEach { (key, coordinates) ->
            val resolvedVersion = lockfile.testDependencies[coordinates] ?: coordinates
            builder.appendLine("    testImplementation(\"$resolvedVersion\")")
        }
        
        // KAPT dependencies
        manifest.kaptDependencies.forEach { (key, coordinates) ->
            val resolvedVersion = lockfile.kaptDependencies[coordinates] ?: coordinates
            builder.appendLine("    kapt(\"$resolvedVersion\")")
        }
        
        // KSP dependencies
        manifest.kspDependencies.forEach { (key, coordinates) ->
            val resolvedVersion = lockfile.kspDependencies[coordinates] ?: coordinates
            builder.appendLine("    ksp(\"$resolvedVersion\")")
        }
        
        // Add standard Kotlin stdlib
        builder.appendLine("    implementation(\"org.jetbrains.kotlin:kotlin-stdlib:${manifest.project.kotlinVersion}\")")
    }
    
    private fun generateKotlinConfig(manifest: KpmManifest, builder: StringBuilder) {
        builder.appendLine("kotlin {")
        builder.appendLine("    jvmToolchain(17)")
        builder.appendLine("}")
        
        if (manifest.project.type == ProjectType.JVM_APPLICATION || manifest.project.type == ProjectType.KTOR_API) {
            builder.appendLine()
            builder.appendLine("application {")
            val mainClass = when (manifest.project.type) {
                ProjectType.KTOR_API -> "io.ktor.server.netty.EngineMain"
                else -> "MainKt"
            }
            builder.appendLine("    mainClass.set(\"$mainClass\")")
            builder.appendLine("}")
        }
    }
    
    fun generateLibsVersionsToml(manifest: KpmManifest): String {
        val builder = StringBuilder()
        
        // Extract versions from dependencies
        val versions = mutableMapOf<String, String>()
        val libraries = mutableMapOf<String, Triple<String, String, String>>()
        
        // Add core versions
        manifest.project.agpVersion?.let { versions["agp"] = it }
        versions["kotlin"] = manifest.project.kotlinVersion
        
        // Process dependencies
        (manifest.dependencies + manifest.testDependencies).forEach { (key, coordinates) ->
            val parts = coordinates.split(":")
            if (parts.size >= 3) {
                // Keep camelCase for Kotlin DSL compatibility (no hyphens)
                val versionKey = key.replaceFirstChar { it.lowercase() }
                versions[versionKey] = parts[2]
                libraries[versionKey] = Triple(parts[0], parts[1], versionKey)
            } else if (parts.size == 2) {
                // BOM member without version
                val libKey = key.replaceFirstChar { it.lowercase() }
                libraries[libKey] = Triple(parts[0], parts[1], "")
            }
        }
        
        // [versions] section
        builder.appendLine("[versions]")
        versions.forEach { (key, version) ->
            builder.appendLine("$key = \"$version\"")
        }
        builder.appendLine()
        
        // [libraries] section
        builder.appendLine("[libraries]")
        libraries.forEach { (key, info) ->
            val (group, name, versionRef) = info
            if (versionRef.isNotEmpty()) {
                builder.appendLine("$key = { group = \"$group\", name = \"$name\", version.ref = \"$versionRef\" }")
            } else {
                builder.appendLine("$key = { group = \"$group\", name = \"$name\" }")
            }
        }
        builder.appendLine()
        
        // [plugins] section
        builder.appendLine("[plugins]")
        manifest.project.agpVersion?.let {
            val pluginType = if (manifest.project.type == ProjectType.ANDROID_APP) "application" else "library"
            builder.appendLine("android-$pluginType = { id = \"com.android.$pluginType\", version.ref = \"agp\" }")
        }
        builder.appendLine("kotlin-android = { id = \"org.jetbrains.kotlin.android\", version.ref = \"kotlin\" }")
        builder.appendLine("kotlin-compose = { id = \"org.jetbrains.kotlin.plugin.compose\", version.ref = \"kotlin\" }")
        builder.appendLine()
        
        return builder.toString()
    }
    
    fun generateSettingsGradle(manifest: KpmManifest): String {
        val builder = StringBuilder()
        
        // For Android projects, use modern structure with version catalog
        if (manifest.project.type == ProjectType.ANDROID_APP || manifest.project.type == ProjectType.ANDROID_LIBRARY) {
            builder.appendLine("pluginManagement {")
            builder.appendLine("    repositories {")
            builder.appendLine("        google {")
            builder.appendLine("            content {")
            builder.appendLine("                includeGroupByRegex(\"com\\\\.android.*\")")
            builder.appendLine("                includeGroupByRegex(\"com\\\\.google.*\")")
            builder.appendLine("                includeGroupByRegex(\"androidx.*\")")
            builder.appendLine("            }")
            builder.appendLine("        }")
            builder.appendLine("        mavenCentral()")
            builder.appendLine("        gradlePluginPortal()")
            builder.appendLine("    }")
            builder.appendLine("}")
            builder.appendLine()
            builder.appendLine("dependencyResolutionManagement {")
            builder.appendLine("    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)")
            builder.appendLine("    repositories {")
            builder.appendLine("        google()")
            builder.appendLine("        mavenCentral()")
            builder.appendLine("    }")
            builder.appendLine("}")
            builder.appendLine("    // Version catalog is auto-detected from gradle/libs.versions.toml")
            builder.appendLine()
            builder.appendLine("rootProject.name = \"${manifest.project.name}\"")
            builder.appendLine("include(\":app\")")
        } else {
            // Non-Android projects use simpler structure
            builder.appendLine("rootProject.name = \"${manifest.project.name}\"")
            builder.appendLine()
            builder.appendLine("pluginManagement {")
            builder.appendLine("    repositories {")
            builder.appendLine("        gradlePluginPortal()")
            builder.appendLine("        google()")
            builder.appendLine("        mavenCentral()")
            builder.appendLine("    }")
            builder.appendLine("}")
        }
        
        return builder.toString()
    }
    
    fun generateGradleWrapper(projectDir: File, gradleVersion: String = "8.5") {
        try {
            // Copy bundled Gradle wrapper files from resources
            copyGradleWrapperFromResources(projectDir, gradleVersion)
        } catch (e: Exception) {
            // Fallback: try system gradle command
            try {
                val gradleProcess = ProcessBuilder("gradle", "wrapper", "--gradle-version", gradleVersion)
                    .directory(projectDir)
                    .redirectErrorStream(true)
                    .start()
                
                val exitCode = gradleProcess.waitFor()
                
                if (exitCode != 0) {
                    createGradleSetupInstructions(projectDir)
                }
            } catch (e2: Exception) {
                createGradleSetupInstructions(projectDir)
            }
        }
    }
    
    private fun copyGradleWrapperFromResources(projectDir: File, gradleVersion: String) {
        val classLoader = this::class.java.classLoader
        
        // Create gradle/wrapper directory
        val wrapperDir = File(projectDir, "gradle/wrapper")
        wrapperDir.mkdirs()
        
        // Generate gradle-wrapper.properties with specified version
        val propertiesContent = """
            distributionBase=GRADLE_USER_HOME
            distributionPath=wrapper/dists
            distributionUrl=https\://services.gradle.org/distributions/gradle-$gradleVersion-bin.zip
            networkTimeout=10000
            validateDistributionUrl=true
            zipStoreBase=GRADLE_USER_HOME
            zipStorePath=wrapper/dists
        """.trimIndent()
        File(wrapperDir, "gradle-wrapper.properties").writeText(propertiesContent)
        
        // Copy gradle-wrapper.jar
        val jarStream = classLoader.getResourceAsStream("gradle-wrapper/wrapper/gradle-wrapper.jar")
            ?: throw IllegalStateException("gradle-wrapper.jar resource not found")
        jarStream.use { input ->
            File(wrapperDir, "gradle-wrapper.jar").outputStream().use { output ->
                input.copyTo(output)
            }
        }
        
        // Copy gradlew script
        val gradlewStream = classLoader.getResourceAsStream("gradle-wrapper/gradlew")
            ?: throw IllegalStateException("gradlew resource not found")
        gradlewStream.use { input ->
            val gradlewFile = File(projectDir, "gradlew")
            gradlewFile.outputStream().use { output ->
                input.copyTo(output)
            }
            gradlewFile.setExecutable(true)
        }
        
        // Copy gradlew.bat script
        val gradlewBatStream = classLoader.getResourceAsStream("gradle-wrapper/gradlew.bat")
            ?: throw IllegalStateException("gradlew.bat resource not found")
        gradlewBatStream.use { input ->
            File(projectDir, "gradlew.bat").outputStream().use { output ->
                input.copyTo(output)
            }
        }
        
        // Verify files were created
        if (!File(projectDir, "gradlew").exists()) {
            throw IllegalStateException("Failed to create gradlew file")
        }
    }
    
    private fun createGradleSetupInstructions(projectDir: File) {
        val readmeFile = File(projectDir, "GRADLE_SETUP.md")
        readmeFile.writeText("""
            # Gradle Setup Required
            
            KPM couldn't automatically set up Gradle wrapper.
            
            To complete the project setup, run:
            
            ```bash
            gradle wrapper
            ```
            
            If you don't have Gradle installed, install it first:
            - macOS: `brew install gradle`
            - Linux: Use your package manager  
            - Windows: Download from https://gradle.org/install/
        """.trimIndent())
    }
}
