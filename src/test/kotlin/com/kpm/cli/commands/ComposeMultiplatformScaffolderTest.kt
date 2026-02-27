package com.kpm.cli.commands

import com.kpm.config.TomlParser
import com.kpm.gradle.GradleGenerator
import com.kpm.model.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class ComposeMultiplatformScaffolderTest {
    
    @TempDir
    lateinit var tempDir: File
    
    private fun createTestProject(
        projectName: String,
        android: Boolean = false,
        ios: Boolean = false,
        desktop: Boolean = false,
        web: Boolean = false,
        wasm: Boolean = false,
        server: Boolean = false
    ): File {
        val projectDir = File(tempDir, projectName)
        projectDir.mkdirs()
        
        // Create manifest
        val manifest = KpmManifest(
            project = ProjectConfig(
                name = projectName,
                version = "1.0.0",
                type = ProjectType.COMPOSE_MULTIPLATFORM,
                kotlinVersion = "2.3.0",
                gradleVersion = "8.13",
                agpVersion = if (android) "8.11.2" else null
            ),
            android = if (android) AndroidConfig(
                applicationId = "org.example.project",
                minSdk = 24,
                targetSdk = 36,
                compileSdk = 36,
                namespace = "org.example.project"
            ) else null,
            repositories = RepositoryConfig(
                mavenCentral = true,
                google = true,
                gradlePluginPortal = true
            ),
            dependencies = emptyMap(),
            testDependencies = emptyMap(),
            kaptDependencies = emptyMap(),
            kspDependencies = emptyMap()
        )
        
        // Write manifest
        val tomlParser = TomlParser()
        tomlParser.writeManifest(manifest, File(projectDir, "kpm.toml"))
        
        // Create basic structure using internal scaffolder methods
        val scaffolder = TestableComposeMultiplatformScaffolder(
            projectName, android, ios, desktop, web, wasm, server
        )
        scaffolder.createStructure(projectDir, manifest)
        
        return projectDir
    }
    
    @Test
    fun `should create CMP project with Android only`() {
        val projectName = "TestAndroidCMP"
        val projectDir = createTestProject(projectName, android = true)
        
        // Verify project structure
        assertTrue(projectDir.exists(), "Project directory should exist")
        assertTrue(File(projectDir, "kpm.toml").exists(), "kpm.toml should exist")
        assertTrue(File(projectDir, "build.gradle.kts").exists(), "Root build.gradle.kts should exist")
        assertTrue(File(projectDir, "settings.gradle.kts").exists(), "settings.gradle.kts should exist")
        assertTrue(File(projectDir, "gradle/libs.versions.toml").exists(), "libs.versions.toml should exist")
        
        // Verify composeApp module
        assertTrue(File(projectDir, "composeApp/build.gradle.kts").exists(), "composeApp build.gradle.kts should exist")
        assertTrue(File(projectDir, "composeApp/src/androidMain").exists(), "androidMain source set should exist")
        assertTrue(File(projectDir, "composeApp/src/commonMain").exists(), "commonMain source set should exist")
        
        // Verify iOS is NOT created
        assertFalse(File(projectDir, "composeApp/src/iosMain").exists(), "iosMain should not exist")
        assertFalse(File(projectDir, "iosApp").exists(), "iosApp should not exist")
        
        // Verify kpm.toml content
        val kpmToml = File(projectDir, "kpm.toml").readText()
        assertTrue(kpmToml.contains("type = \"compose-multiplatform\""), "Should be CMP project type")
        assertTrue(kpmToml.contains("name = \"$projectName\""), "Should have correct project name")
    }
    
    @Test
    fun `should create CMP project with Android and iOS`() {
        val projectName = "TestAndroidIOSCMP"
        val projectDir = createTestProject(projectName, android = true, ios = true)
        
        // Verify iOS-specific files
        assertTrue(File(projectDir, "composeApp/src/commonMain").exists(), "commonMain source set should exist")
        assertTrue(File(projectDir, "composeApp/src/androidMain").exists(), "androidMain source set should exist")
        assertTrue(File(projectDir, "iosApp").exists(), "iosApp directory should exist")
        assertTrue(File(projectDir, "iosApp/iosApp.xcodeproj").exists(), "Xcode project should exist")
        
        // Verify build.gradle.kts exists
        assertTrue(File(projectDir, "composeApp/build.gradle.kts").exists(), "build.gradle.kts should exist")
    }
    
    @Test
    fun `should create CMP project with Desktop`() {
        val projectName = "TestDesktopCMP"
        val projectDir = createTestProject(projectName, desktop = true)
        
        // Verify desktop-specific files (desktop uses jvmMain source set)
        assertTrue(File(projectDir, "composeApp/src/commonMain").exists(), "commonMain source set should exist")
        assertTrue(File(projectDir, "composeApp/src/jvmMain").exists(), "jvmMain source set should exist for desktop")
        
        // Verify build.gradle.kts exists
        assertTrue(File(projectDir, "composeApp/build.gradle.kts").exists(), "build.gradle.kts should exist")
    }
    
    @Test
    fun `should create CMP project with Web`() {
        val projectName = "TestWebCMP"
        val projectDir = createTestProject(projectName, web = true)
        
        // Verify web-specific files
        assertTrue(File(projectDir, "composeApp/src/webMain").exists(), "webMain source set should exist")
        assertTrue(File(projectDir, "composeApp/src/commonMain").exists(), "commonMain source set should exist")
        
        // Verify build.gradle.kts contains JS target
        val buildGradle = File(projectDir, "composeApp/build.gradle.kts").readText()
        assertTrue(buildGradle.contains("js"), "Should have JS target")
    }
    
    @Test
    fun `should create CMP project with WASM`() {
        val projectName = "TestWasmCMP"
        val projectDir = createTestProject(projectName, wasm = true)
        
        // Verify basic project structure
        assertTrue(File(projectDir, "composeApp/src/commonMain").exists(), "commonMain source set should exist")
        assertTrue(File(projectDir, "composeApp/build.gradle.kts").exists(), "build.gradle.kts should exist")
        
        // Verify build.gradle.kts contains WASM target
        val buildGradle = File(projectDir, "composeApp/build.gradle.kts").readText()
        assertTrue(buildGradle.contains("wasmJs"), "Should have WASM target")
    }
    
    @Test
    fun `should create CMP project with Server`() {
        val projectName = "TestServerCMP"
        val projectDir = createTestProject(projectName, server = true)
        
        // Verify server module
        assertTrue(File(projectDir, "server").exists(), "server directory should exist")
        assertTrue(File(projectDir, "server/build.gradle.kts").exists(), "server build.gradle.kts should exist")
        assertTrue(File(projectDir, "server/src/main/kotlin").exists(), "server source directory should exist")
        
        // Verify settings.gradle.kts includes server module
        val settingsGradle = File(projectDir, "settings.gradle.kts").readText()
        assertTrue(settingsGradle.contains("include(\":server\")"), "Should include server module")
    }
    
    @Test
    fun `should create CMP project with all platforms`() {
        val projectName = "TestAllPlatformsCMP"
        val projectDir = createTestProject(
            projectName,
            android = true,
            ios = true,
            desktop = true,
            web = true,
            wasm = true,
            server = true
        )
        
        // Verify basic structure for all platforms
        assertTrue(File(projectDir, "composeApp/src/commonMain").exists(), "commonMain should exist")
        assertTrue(File(projectDir, "composeApp/src/androidMain").exists(), "androidMain should exist")
        assertTrue(File(projectDir, "composeApp/src/jvmMain").exists(), "jvmMain should exist for desktop")
        assertTrue(File(projectDir, "composeApp/src/webMain").exists(), "webMain should exist")
        assertTrue(File(projectDir, "iosApp").exists(), "iosApp should exist")
        assertTrue(File(projectDir, "server").exists(), "server module should exist")
        
        // Verify libs.versions.toml has all necessary dependencies
        val libsVersions = File(projectDir, "gradle/libs.versions.toml").readText()
        assertTrue(libsVersions.contains("agp ="), "Should have AGP version for Android")
        assertTrue(libsVersions.contains("ktor ="), "Should have Ktor version for server")
    }
    
    @Test
    fun `should create valid gradle wrapper`() {
        val projectName = "TestGradleWrapper"
        val projectDir = createTestProject(projectName, android = true)
        
        // Verify Gradle wrapper files
        assertTrue(File(projectDir, "gradlew").exists(), "gradlew should exist")
        assertTrue(File(projectDir, "gradlew.bat").exists(), "gradlew.bat should exist")
        assertTrue(File(projectDir, "gradle/wrapper/gradle-wrapper.jar").exists(), "gradle-wrapper.jar should exist")
        assertTrue(File(projectDir, "gradle/wrapper/gradle-wrapper.properties").exists(), "gradle-wrapper.properties should exist")
        
        // Verify gradlew is executable
        assertTrue(File(projectDir, "gradlew").canExecute(), "gradlew should be executable")
    }
}

// Helper class that exposes internal scaffolder methods for testing
private class TestableComposeMultiplatformScaffolder(
    projectName: String,
    android: Boolean,
    ios: Boolean,
    desktop: Boolean,
    web: Boolean,
    wasm: Boolean,
    server: Boolean
) : ComposeMultiplatformScaffolder(projectName, android, ios, desktop, web, wasm, server) {
    
    fun createStructure(projectDir: File, manifest: KpmManifest) {
        // Create project structure
        createCmpProjectStructure(projectDir)
        
        // Generate Gradle files
        generateCmpGradleFiles(projectDir, manifest)
        
        // Generate Gradle wrapper
        val gradleGenerator = GradleGenerator()
        gradleGenerator.generateGradleWrapper(projectDir, manifest.project.gradleVersion)
        
        // Create lockfile
        val tomlParser = TomlParser()
        tomlParser.writeLockfile(KpmLockfile(), File(projectDir, "kpm.lock"))
    }
}
