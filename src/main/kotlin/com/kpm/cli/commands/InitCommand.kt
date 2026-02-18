package com.kpm.cli.commands

import com.kpm.cli.Command
import com.kpm.cli.echo
import com.kpm.config.TomlParser
import com.kpm.gradle.GradleGenerator
import com.kpm.model.*
import com.kpm.android.AndroidSdkManager
import java.io.File

class InitCommand : Command("init", "Initialize a new KPM project") {
    
    private val projectName by option("name", "Project name")
    private val projectType by option("type", "Project type")
    private val packageName by option("package", "Package name (for Android projects)")
    private val setupAndroidSdk by option("setup-android-sdk", "Automatically setup Android SDK").flag()
    private val androidSdkPath by option("android-sdk-path", "Custom Android SDK path")
    
    override fun run() {
        val name = projectName ?: run {
            echo("Project name is required. Use --name <name>", err = true)
            return
        }
        val type = projectType ?: "jvm-application"
        
        val currentDir = File(System.getProperty("user.dir"))
        val projectDir = File(currentDir, name)
        
        if (projectDir.exists() && projectDir.listFiles()?.isNotEmpty() == true) {
            echo("Error: Directory $name already exists and is not empty", err = true)
            return
        }
        
        projectDir.mkdirs()
        
        val parsedProjectType = parseProjectType(type)
        
        // Prompt for Gradle version
        echo("")
        val gradleVersion = promptForGradleVersion()
        
        // Prompt for AGP version if Android project
        val agpVersion = if (parsedProjectType == ProjectType.ANDROID_APP || parsedProjectType == ProjectType.ANDROID_LIBRARY) {
            promptForAgpVersion()
        } else null
        
        echo("")
        val manifest = createManifest(name, parsedProjectType, packageName, gradleVersion, agpVersion)
        
        // Create project structure
        createProjectStructure(projectDir, parsedProjectType)
        
        // Generate kpm.toml
        val tomlParser = TomlParser()
        tomlParser.writeManifest(manifest, File(projectDir, "kpm.toml"))
        
        // Generate Gradle files
        val gradleGenerator = GradleGenerator()
        val buildGradle = gradleGenerator.generateBuildGradle(manifest, KpmLockfile(), projectDir)
        File(projectDir, "build.gradle.kts").writeText(buildGradle)
        
        val settingsGradle = gradleGenerator.generateSettingsGradle(manifest)
        File(projectDir, "settings.gradle.kts").writeText(settingsGradle)
        
        // Generate Gradle wrapper
        gradleGenerator.generateGradleWrapper(projectDir, manifest.project.gradleVersion)
        
        // Create empty lockfile
        tomlParser.writeLockfile(KpmLockfile(), File(projectDir, "kpm.lock"))
        
        // Handle Android SDK setup for Android projects
        if (parsedProjectType == ProjectType.ANDROID_APP || parsedProjectType == ProjectType.ANDROID_LIBRARY) {
            handleAndroidSdkSetup(projectDir)
        }
        
        echo("✅ Created new ${parsedProjectType.name.lowercase().replace("_", " ")} project: $name")
        echo("📁 Project directory: ${projectDir.absolutePath}")
        
        if (parsedProjectType == ProjectType.ANDROID_APP || parsedProjectType == ProjectType.ANDROID_LIBRARY) {
            echo("🚀 Next steps:")
            echo("   cd $name")
            echo("   kpm add androidx.compose:compose-bom:2024.10.00  # Add Compose (optional)")
            echo("   kpm build")
        } else {
            echo("🚀 Next steps:")
            echo("   cd $name")
            echo("   kpm add junit --test  # Add test dependencies (optional)")
            echo("   kpm build")
        }
    }
    
    private fun parseProjectType(typeStr: String): ProjectType {
        return when (typeStr.lowercase().replace("-", "_")) {
            "android_app", "android-app" -> ProjectType.ANDROID_APP
            "android_library", "android-library" -> ProjectType.ANDROID_LIBRARY
            "jvm_application", "jvm-application" -> ProjectType.JVM_APPLICATION
            "jvm_library", "jvm-library" -> ProjectType.JVM_LIBRARY
            "multiplatform_library", "multiplatform-library" -> ProjectType.MULTIPLATFORM_LIBRARY
            "ktor_api", "ktor-api" -> ProjectType.KTOR_API
            else -> {
                echo("Unknown project type: $typeStr. Using jvm-application", err = true)
                ProjectType.JVM_APPLICATION
            }
        }
    }
    
    private fun promptForGradleVersion(): String {
        echo("Select Gradle version:")
        echo("  1) 8.9 (stable, recommended for AGP 8.6+)")
        echo("  2) 8.10 (latest stable)")
        echo("  3) 8.11")
        echo("  4) 8.7")
        echo("  5) 8.6")
        echo("  6) 8.5")
        echo("  7) Custom version")
        echo("")
        
        val choice = readLine()?.trim()?.takeIf { it.isNotEmpty() } ?: "1"
        
        return when (choice) {
            "1" -> "8.9"
            "2" -> "8.10"
            "3" -> "8.11"
            "4" -> "8.7"
            "5" -> "8.6"
            "6" -> "8.5"
            "7" -> {
                echo("Note: Valid Gradle versions are typically 7.x - 8.x")
                print("Enter Gradle version (e.g., 8.5): ")
                val version = readlnOrNull()?.trim() ?: "8.5"
                echo("Using Gradle version: $version")
                version
            }
            else -> {
                echo("Invalid choice, using default: 8.9")
                "8.9"
            }
        }
    }
    
    private fun promptForAgpVersion(): String {
        echo("Select Android Gradle Plugin (AGP) version:")
        echo("  1) 8.6.1 (stable, recommended for Gradle 8.5)")
        echo("  2) 8.7.3 (requires Gradle 8.9+)")
        echo("  3) 8.5.2 (compatible with Gradle 8.2+)")
        echo("  4) 8.4.2 (compatible with Gradle 8.0+)")
        echo("  5) 8.3.2 (compatible with Gradle 8.0+)")
        echo("  6) 8.2.2 (compatible with Gradle 8.0+)")
        echo("  7) Custom version")
        echo("")
        
        val choice = readLine()?.trim()?.takeIf { it.isNotEmpty() } ?: "1"
        
        return when (choice) {
            "1" -> "8.6.1"
            "2" -> "8.7.3"
            "3" -> "8.5.2"
            "4" -> "8.4.2"
            "5" -> "8.3.2"
            "6" -> "8.2.2"
            "7" -> {
                echo("Note: Latest stable AGP versions are in the 8.x range (e.g., 8.7.3)")
                echo("AGP 9.x and higher are not yet released")
                print("Enter AGP version (e.g., 8.7.3): ")
                val version = readlnOrNull()?.trim() ?: "8.7.3"
                echo("Using AGP version: $version")
                version
            }
            else -> {
                echo("Invalid choice, using default: 8.7.3")
                "8.7.3"
            }
        }
    }
    
    private fun createManifest(name: String, type: ProjectType, packageName: String?, gradleVersion: String, agpVersion: String?): KpmManifest {
        val project = ProjectConfig(
            name = name,
            version = "0.1.0",
            type = type,
            kotlinVersion = "2.0.0",
            gradleVersion = gradleVersion,
            agpVersion = agpVersion
        )
        
        val android = if (type == ProjectType.ANDROID_APP || type == ProjectType.ANDROID_LIBRARY) {
            AndroidConfig(
                applicationId = packageName ?: "com.example.$name",
                namespace = packageName ?: "com.example.$name"
            )
        } else null
        
        val repositories = when (type) {
            ProjectType.ANDROID_APP, ProjectType.ANDROID_LIBRARY -> 
                RepositoryConfig(mavenCentral = true, google = true)
            else -> 
                RepositoryConfig(mavenCentral = true)
        }
        
        val defaultDependencies = getDefaultDependencies(type)
        
        return KpmManifest(
            project = project,
            android = android,
            repositories = repositories,
            dependencies = defaultDependencies
        )
    }
    
    private fun getDefaultDependencies(type: ProjectType): Map<String, String> {
        return when (type) {
            ProjectType.ANDROID_APP -> mapOf(
                "coreKtx" to "androidx.core:core-ktx:1.10.1"
            )
            ProjectType.ANDROID_LIBRARY -> mapOf(
                "coreKtx" to "androidx.core:core-ktx:1.12.0"
            )
            ProjectType.KTOR_API -> mapOf(
                "ktorServerCore" to "io.ktor:ktor-server-core:2.3.6",
                "ktorServerNetty" to "io.ktor:ktor-server-netty:2.3.6",
                "ktorServerContentNegotiation" to "io.ktor:ktor-server-content-negotiation:2.3.6",
                "ktorSerialization" to "io.ktor:ktor-serialization-kotlinx-json:2.3.6",
                "logback" to "ch.qos.logback:logback-classic:1.4.11"
            )
            else -> emptyMap()
        }
    }
    
    private fun createProjectStructure(projectDir: File, type: ProjectType) {
        // Create source directories
        val srcDir = File(projectDir, "src")
        val mainDir = File(srcDir, "main")
        val testDir = File(srcDir, "test")
        
        when (type) {
            ProjectType.ANDROID_APP, ProjectType.ANDROID_LIBRARY -> {
                File(mainDir, "kotlin").mkdirs()
                File(mainDir, "res/layout").mkdirs()
                File(mainDir, "res/values").mkdirs()
                File(testDir, "kotlin").mkdirs()
                
                // Create AndroidManifest.xml
                val androidManifest = File(mainDir, "AndroidManifest.xml")
                val appPackageName = packageName ?: "com.example.${name.lowercase().replace("-", "").replace("_", "")}"
                androidManifest.writeText("""
                    <?xml version="1.0" encoding="utf-8"?>
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="$appPackageName">
                        <application
                            android:allowBackup="true"
                            android:label="@string/app_name"
                            android:theme="@style/Theme.Material3.DayNight">
                            <activity
                                android:name=".MainActivity"
                                android:exported="true">
                                <intent-filter>
                                    <action android:name="android.intent.action.MAIN" />
                                    <category android:name="android.intent.category.LAUNCHER" />
                                </intent-filter>
                            </activity>
                        </application>
                    </manifest>
                """.trimIndent())
                
                // Create basic string resources
                val valuesDir = File(mainDir, "res/values")
                valuesDir.mkdirs()
                val stringsXml = File(valuesDir, "strings.xml")
                stringsXml.writeText("""
                    <?xml version="1.0" encoding="utf-8"?>
                    <resources>
                        <string name="app_name">$name</string>
                    </resources>
                """.trimIndent())
                
                // Create MainActivity for Android apps
                if (type == ProjectType.ANDROID_APP) {
                    val packagePath = appPackageName.replace(".", "/")
                    val kotlinDir = File(mainDir, "kotlin/$packagePath")
                    kotlinDir.mkdirs()
                    
                    val mainActivity = File(kotlinDir, "MainActivity.kt")
                    mainActivity.writeText("""
                        package $appPackageName
                        
                        import android.app.Activity
                        import android.os.Bundle
                        
                        class MainActivity : Activity() {
                            override fun onCreate(savedInstanceState: Bundle?) {
                                super.onCreate(savedInstanceState)
                                // TODO: Set up your UI here
                                // For Compose UI, add: setContent { YourComposeContent() }
                            }
                        }
                    """.trimIndent())
                }
            }
            else -> {
                File(mainDir, "kotlin").mkdirs()
                File(testDir, "kotlin").mkdirs()
                
                // Create a simple Main.kt for applications
                if (type == ProjectType.JVM_APPLICATION) {
                    val mainKt = File(mainDir, "kotlin/Main.kt")
                    mainKt.writeText("""
                        fun main() {
                            println("Hello, KPM!")
                        }
                    """.trimIndent())
                }
                
                if (type == ProjectType.KTOR_API) {
                    val applicationKt = File(mainDir, "kotlin/Application.kt")
                    applicationKt.writeText("""
                        import io.ktor.server.application.*
                        import io.ktor.server.engine.*
                        import io.ktor.server.netty.*
                        import io.ktor.server.response.*
                        import io.ktor.server.routing.*
                        import io.ktor.serialization.kotlinx.json.*
                        import io.ktor.server.plugins.contentnegotiation.*
                        
                        fun main() {
                            embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
                                .start(wait = true)
                        }
                        
                        fun Application.module() {
                            install(ContentNegotiation) {
                                json()
                            }
                            
                            routing {
                                get("/") {
                                    call.respondText("Hello, KPM Ktor API!")
                                }
                                
                                get("/health") {
                                    call.respondText("OK")
                                }
                            }
                        }
                    """.trimIndent())
                }
            }
        }
        
        // Create .gitignore
        val gitignore = File(projectDir, ".gitignore")
        gitignore.writeText("""
            .gradle
            build/
            !gradle/wrapper/gradle-wrapper.jar
            !**/src/main/**/build/
            !**/src/test/**/build/
            
            ### IntelliJ IDEA ###
            .idea/
            *.iws
            *.iml
            *.ipr
            out/
            !**/src/main/**/out/
            !**/src/test/**/out/
            
            ### VS Code ###
            .vscode/
            
            ### Mac ###
            .DS_Store
            
            ### KPM ###
            kpm.lock
            
            ### Android ###
            local.properties
        """.trimIndent())
        
        // Create gradle.properties for Android projects
        if (type == ProjectType.ANDROID_APP || type == ProjectType.ANDROID_LIBRARY) {
            val gradleProperties = File(projectDir, "gradle.properties")
            gradleProperties.writeText("""
                # Project-wide Gradle settings.
                # IDE (e.g. Android Studio) users:
                # Gradle settings configured through the IDE *will override*
                # any settings specified in this file.
                
                # AndroidX package structure to make it clearer which packages are bundled with the
                # Android operating system, and which are packaged with your app's APK
                android.useAndroidX=true
                android.enableJetifier=true
                
                # Kotlin code style for this project: "official" or "obsolete":
                kotlin.code.style=official
                
                # Enables namespacing of each library's R class so that its R class includes only the
                # resources declared in the library itself and none from the library's dependencies,
                # thereby reducing the size of the R class for that library
                android.nonTransitiveRClass=true
                
                # Suppress compile SDK warning
                android.suppressUnsupportedCompileSdk=35
                
                # Memory optimization for Android builds
                org.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=512m -XX:+HeapDumpOnOutOfMemoryError
                org.gradle.parallel=true
                org.gradle.caching=true
                org.gradle.configureondemand=true
                
                # Android build optimizations
                android.enableR8.fullMode=true
            """.trimIndent())
        }
    }
    
    private fun handleAndroidSdkSetup(projectDir: File) {
        val androidSdkManager = AndroidSdkManager()
        
        // Check if custom SDK path was provided
        val customSdkPath = androidSdkPath
        if (customSdkPath != null) {
            val sdkDir = File(customSdkPath)
            if (sdkDir.exists()) {
                echo("🔧 Using custom Android SDK: $customSdkPath")
                val localProperties = File(projectDir, "local.properties")
                localProperties.writeText("sdk.dir=$customSdkPath")
                return
            } else {
                echo("⚠️  Custom SDK path not found: $customSdkPath", err = true)
            }
        }
        
        // Try to detect existing Android SDK
        val sdkInfo = androidSdkManager.detectAndroidSdk()
        
        if (sdkInfo != null) {
            echo("✅ Found Android SDK at: ${sdkInfo.path}")
            echo("📱 Build Tools: ${sdkInfo.buildTools.take(3).joinToString(", ")}")
            echo("📱 Platforms: ${sdkInfo.platforms.take(3).joinToString(", ")}")
            
            // Setup local.properties
            androidSdkManager.setupAndroidSdkEnvironment(projectDir, sdkInfo)
            echo("✅ Android SDK configured for project")
            
        } else if (setupAndroidSdk) {
            echo("📥 Android SDK not found. Setting up Android SDK...")
            
            // Create SDK directory
            val sdkDir = File(System.getProperty("user.home"), "Android/Sdk")
            sdkDir.mkdirs()
            
            // Download command line tools
            if (androidSdkManager.downloadAndroidCommandLineTools(sdkDir)) {
                // Install essential components
                val components = listOf(
                    "platform-tools",
                    "platforms;android-35",
                    "build-tools;35.0.0"
                )
                
                if (androidSdkManager.installAndroidSdkComponents(sdkDir.absolutePath, components)) {
                    echo("✅ Android SDK setup completed")
                    val localProperties = File(projectDir, "local.properties")
                    localProperties.writeText("sdk.dir=${sdkDir.absolutePath}")
                } else {
                    echo("❌ Failed to install Android SDK components", err = true)
                    showAndroidSdkInstructions()
                }
            } else {
                echo("❌ Failed to download Android SDK", err = true)
                showAndroidSdkInstructions()
            }
            
        } else {
            echo("⚠️  Android SDK not found!")
            showAndroidSdkInstructions()
        }
    }
    
    private fun showAndroidSdkInstructions() {
        echo("")
        echo("📱 To set up Android SDK:")
        echo("   Option 1: Install Android Studio (recommended)")
        echo("     - Download from: https://developer.android.com/studio")
        echo("     - Android Studio includes the SDK automatically")
        echo("")
        echo("   Option 2: Use KPM to auto-setup SDK")
        echo("     - Re-run: kpm init --setup-android-sdk --name YourProject --type android-app")
        echo("")
        echo("   Option 3: Manual SDK setup")
        echo("     - Download SDK from: https://developer.android.com/studio#cmdline-tools")
        echo("     - Set ANDROID_HOME environment variable")
        echo("     - Or use: kpm init --android-sdk-path /path/to/sdk")
        echo("")
    }
}
