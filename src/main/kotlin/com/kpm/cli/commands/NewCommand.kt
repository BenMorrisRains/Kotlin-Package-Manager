package com.kpm.cli.commands

import com.kpm.cli.Command
import com.kpm.cli.echo
import com.kpm.model.*
import com.kpm.config.TomlParser
import com.kpm.gradle.GradleGenerator
import com.kpm.config.GlobalConfigManager
import com.kpm.cli.ProgressIndicator
import com.kpm.android.AndroidSdkManager
import java.io.File

class NewCommand : Command("new", "Create a new project with smart defaults") {
    
    private val projectName by argument("name", "Project name")
    private val android by option("android", "Create Android app").flag()
    private val compose by option("compose", "Add Compose UI (Android only)").flag()
    private val ktor by option("ktor", "Create Ktor API server").flag()
    private val library by option("library", "Create library instead of application").flag()
    
    override fun run() {
        val name = projectName
        
        // Determine project type based on flags
        val projectType = when {
            android && library -> ProjectType.ANDROID_LIBRARY
            android -> ProjectType.ANDROID_APP
            ktor -> ProjectType.KTOR_API
            library -> ProjectType.JVM_LIBRARY
            else -> ProjectType.JVM_APPLICATION
        }
        
        val currentDir = File(System.getProperty("user.dir"))
        val projectDir = File(currentDir, name)
        
        if (projectDir.exists() && projectDir.listFiles()?.isNotEmpty() == true) {
            echo("Error: Directory $name already exists and is not empty", err = true)
            return
        }
        
        echo("Creating ${getProjectTypeDescription(projectType)} project: $name")
        echo("")
        
        // Prompt for Gradle version
        val gradleVersion = promptForGradleVersion()
        
        // Prompt for AGP version if Android project
        val agpVersion = if (projectType == ProjectType.ANDROID_APP || projectType == ProjectType.ANDROID_LIBRARY) {
            promptForAgpVersion()
        } else null
        
        echo("")
        
        projectDir.mkdirs()
        
        // Create manifest with smart defaults
        val manifest = createSmartManifest(name, projectType, gradleVersion, agpVersion)
        
        val progress = ProgressIndicator("Creating project structure...")
        progress.start()
        
        try {
            // Create project structure
            createProjectStructure(projectDir, projectType, name)
            progress.succeed("Project structure created")
        } catch (e: Exception) {
            progress.fail("Failed to create project structure")
            throw e
        }
        
        // Generate kpm.toml
        val tomlParser = TomlParser()
        tomlParser.writeManifest(manifest, File(projectDir, "kpm.toml"))
        
        // Generate Gradle files
        val gradleGenerator = GradleGenerator()
        val gradleProgress = ProgressIndicator("Generating Gradle build files...")
        gradleProgress.start()
        
        try {
            if (projectType == ProjectType.ANDROID_APP || projectType == ProjectType.ANDROID_LIBRARY) {
                // Modern multi-module structure for Android
                val buildGradle = gradleGenerator.generateBuildGradle(manifest, KpmLockfile(), projectDir)
                File(projectDir, "build.gradle.kts").writeText(buildGradle)
                
                // Generate app module build.gradle.kts
                val appBuildGradle = gradleGenerator.generateAppBuildGradle(manifest)
                File(projectDir, "app/build.gradle.kts").writeText(appBuildGradle)
                
                // Generate gradle/libs.versions.toml for version catalog
                val libsVersionsToml = gradleGenerator.generateLibsVersionsToml(manifest)
                val gradleDir = File(projectDir, "gradle")
                gradleDir.mkdirs()
                File(gradleDir, "libs.versions.toml").writeText(libsVersionsToml)
                
                // Create proguard-rules.pro
                File(projectDir, "app/proguard-rules.pro").writeText("""
                    # Add project specific ProGuard rules here.
                    # You can control the set of applied configuration files using the
                    # proguardFiles setting in build.gradle.
                    #
                    # For more details, see
                    #   http://developer.android.com/guide/developing/tools/proguard.html
                """.trimIndent())
            } else {
                // Traditional single-module structure for non-Android
                val buildGradle = gradleGenerator.generateBuildGradle(manifest, KpmLockfile(), projectDir)
                File(projectDir, "build.gradle.kts").writeText(buildGradle)
            }
            
            val settingsGradle = gradleGenerator.generateSettingsGradle(manifest)
            File(projectDir, "settings.gradle.kts").writeText(settingsGradle)
            
            gradleProgress.succeed("Gradle build files generated")
        } catch (e: Exception) {
            gradleProgress.fail("Failed to generate Gradle files")
            throw e
        }
        
        // Generate Gradle wrapper
        val wrapperProgress = ProgressIndicator("Setting up Gradle wrapper...")
        wrapperProgress.start()
        try {
            gradleGenerator.generateGradleWrapper(projectDir, manifest.project.gradleVersion)
            wrapperProgress.succeed("Gradle wrapper configured")
        } catch (e: Exception) {
            wrapperProgress.fail("Failed to setup Gradle wrapper")
            throw e
        }
        
        // Create empty lockfile
        tomlParser.writeLockfile(KpmLockfile(), File(projectDir, "kpm.lock"))
        
        // Handle Android SDK setup for Android projects
        if (projectType == ProjectType.ANDROID_APP || projectType == ProjectType.ANDROID_LIBRARY) {
            handleAndroidSdkSetup(projectDir)
        }
        
        // Add Compose dependencies if requested
        if (compose && (projectType == ProjectType.ANDROID_APP || projectType == ProjectType.ANDROID_LIBRARY)) {
            addComposeDependencies(projectDir, tomlParser)
        }
        
        echo("✅ Created ${getProjectTypeDescription(projectType)} project: $name")
        echo("Project directory: ${projectDir.absolutePath}")
        
        showNextSteps(projectType, compose)
    }
    
    private fun getProjectTypeDescription(type: ProjectType): String {
        return when (type) {
            ProjectType.ANDROID_APP -> "Android app"
            ProjectType.ANDROID_LIBRARY -> "Android library"
            ProjectType.JVM_APPLICATION -> "JVM application"
            ProjectType.JVM_LIBRARY -> "JVM library"
            ProjectType.KTOR_API -> "Ktor API server"
            ProjectType.MULTIPLATFORM_LIBRARY -> "Multiplatform library"
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
    
    private fun createSmartManifest(name: String, type: ProjectType, gradleVersion: String, agpVersion: String?): KpmManifest {
        val globalConfig = GlobalConfigManager().getGlobalConfig()
        
        // For Android projects with Compose, ensure Kotlin 2.0+ for compatibility
        val kotlinVersion = when {
            type == ProjectType.ANDROID_APP || type == ProjectType.ANDROID_LIBRARY -> {
                globalConfig.defaultKotlinVersion?.takeIf { it >= "2.0.0" } ?: "2.1.0"
            }
            else -> globalConfig.defaultKotlinVersion ?: "2.1.0"
        }
        
        val project = ProjectConfig(
            name = name,
            version = "0.1.0",
            type = type,
            kotlinVersion = kotlinVersion,
            gradleVersion = gradleVersion,
            agpVersion = agpVersion
        )
        
        val android = if (type == ProjectType.ANDROID_APP || type == ProjectType.ANDROID_LIBRARY) {
            AndroidConfig(
                applicationId = "com.example.${name.lowercase().replace("-", "").replace("_", "")}",
                namespace = "com.example.${name.lowercase().replace("-", "").replace("_", "")}"
            )
        } else null
        
        val repositories = when (type) {
            ProjectType.ANDROID_APP, ProjectType.ANDROID_LIBRARY -> RepositoryConfig(
                mavenCentral = globalConfig.defaultRepositories["maven_central"] ?: true,
                google = globalConfig.defaultRepositories["google"] ?: true,
                gradlePluginPortal = globalConfig.defaultRepositories["gradle_plugin_portal"] ?: false
            )
            else -> RepositoryConfig(
                mavenCentral = globalConfig.defaultRepositories["maven_central"] ?: true,
                google = globalConfig.defaultRepositories["google"] ?: false,
                gradlePluginPortal = globalConfig.defaultRepositories["gradle_plugin_portal"] ?: false
            )
        }
        
        val defaultDependencies = getSmartDefaultDependencies(type)
        
        // Add global dependencies from config
        val globalDeps = globalConfig.globalDependencies.alwaysInclude
        val allDependencies = defaultDependencies + globalDeps
        
        return KpmManifest(
            project = project,
            android = android,
            repositories = repositories,
            dependencies = allDependencies
        )
    }
    
    private fun getSmartDefaultDependencies(type: ProjectType): Map<String, String> {
        return when (type) {
            ProjectType.ANDROID_APP -> mapOf(
                "coreKtx" to "androidx.core:core-ktx:1.10.1",
                "appcompat" to "androidx.appcompat:appcompat:1.6.1",
                "material" to "com.google.android.material:material:1.11.0"
            )
            ProjectType.ANDROID_LIBRARY -> mapOf(
                "coreKtx" to "androidx.core:core-ktx:1.10.1"
            )
            ProjectType.KTOR_API -> mapOf(
                "ktorServerCore" to "io.ktor:ktor-server-core:2.3.6",
                "ktorServerNetty" to "io.ktor:ktor-server-netty:2.3.6",
                "ktorServerContentNegotiation" to "io.ktor:ktor-server-content-negotiation:2.3.6",
                "ktorSerialization" to "io.ktor:ktor-serialization-kotlinx-json:2.3.6",
                "logback" to "ch.qos.logback:logback-classic:1.4.11"
            )
            ProjectType.JVM_APPLICATION -> mapOf(
                "coroutines" to "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3"
            )
            else -> emptyMap()
        }
    }
    
    private fun createProjectStructure(projectDir: File, type: ProjectType, name: String) {
        when (type) {
            ProjectType.ANDROID_APP, ProjectType.ANDROID_LIBRARY -> {
                // Modern multi-module structure: root/app/src/main
                val appDir = File(projectDir, "app")
                val srcDir = File(appDir, "src")
                val mainDir = File(srcDir, "main")
                val testDir = File(srcDir, "test")
                
                File(mainDir, "kotlin").mkdirs()
                File(mainDir, "res/layout").mkdirs()
                File(mainDir, "res/values").mkdirs()
                File(testDir, "kotlin").mkdirs()
                
                // Create app/.gitignore
                File(appDir, ".gitignore").writeText("/build")
                
                // Create AndroidManifest.xml in app module
                val androidManifest = File(mainDir, "AndroidManifest.xml")
                val packageName = "com.example.${name.lowercase().replace("-", "").replace("_", "")}"
                val themeStyle = if (compose) "@android:style/Theme.Material.Light.NoActionBar" else "@style/Theme.Material3.DayNight"
                androidManifest.writeText("""
                    <?xml version="1.0" encoding="utf-8"?>
                    <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        package="$packageName">
                        <application
                            android:allowBackup="true"
                            android:label="@string/app_name"
                            android:theme="$themeStyle">
                            <activity
                                android:name=".MainActivity"
                                android:exported="true"
                                android:theme="$themeStyle">
                                <intent-filter>
                                    <action android:name="android.intent.action.MAIN" />
                                    <category android:name="android.intent.category.LAUNCHER" />
                                </intent-filter>
                            </activity>
                        </application>
                    </manifest>
                """.trimIndent())
                
                // Create string resources
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
                    val packagePath = "com/example/${name.lowercase().replace("-", "").replace("_", "")}"
                    val kotlinDir = File(mainDir, "kotlin/$packagePath")
                    kotlinDir.mkdirs()
                    
                    val mainActivity = File(kotlinDir, "MainActivity.kt")
                    
                    if (compose) {
                        // Create Compose-enabled MainActivity
                        mainActivity.writeText("""
                            package $packageName
                            
                            import android.os.Bundle
                            import androidx.activity.ComponentActivity
                            import androidx.activity.compose.setContent
                            import androidx.compose.foundation.layout.*
                            import androidx.compose.material3.*
                            import androidx.compose.runtime.*
                            import androidx.compose.ui.Alignment
                            import androidx.compose.ui.Modifier
                            import androidx.compose.ui.tooling.preview.Preview
                            import androidx.compose.ui.unit.dp
                            
                            class MainActivity : ComponentActivity() {
                                override fun onCreate(savedInstanceState: Bundle?) {
                                    super.onCreate(savedInstanceState)
                                    setContent {
                                        ${name}Theme {
                                            Surface(
                                                modifier = Modifier.fillMaxSize(),
                                                color = MaterialTheme.colorScheme.background
                                            ) {
                                                MainScreen()
                                            }
                                        }
                                    }
                                }
                            }
                            
                            @Composable
                            fun MainScreen() {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "Welcome to $name!",
                                        style = MaterialTheme.typography.headlineMedium
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = { /* TODO: Add your action here */ }
                                    ) {
                                        Text("Get Started")
                                    }
                                }
                            }
                            
                            @Composable
                            fun ${name}Theme(content: @Composable () -> Unit) {
                                MaterialTheme(
                                    colorScheme = dynamicColorScheme(),
                                    content = content
                                )
                            }
                            
                            @Composable
                            private fun dynamicColorScheme(): ColorScheme {
                                return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                    androidx.compose.material3.dynamicLightColorScheme(
                                        androidx.compose.ui.platform.LocalContext.current
                                    )
                                } else {
                                    lightColorScheme()
                                }
                            }
                            
                            @Preview(showBackground = true)
                            @Composable
                            fun MainScreenPreview() {
                                ${name}Theme {
                                    MainScreen()
                                }
                            }
                        """.trimIndent())
                    } else {
                        // Create regular Activity
                        mainActivity.writeText("""
                            package $packageName
                            
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
            }
            ProjectType.KTOR_API -> {
                // Traditional single-module structure for non-Android
                val srcDir = File(projectDir, "src")
                val mainDir = File(srcDir, "main")
                val testDir = File(srcDir, "test")
                
                File(mainDir, "kotlin").mkdirs()
                File(testDir, "kotlin").mkdirs()
                
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
                                call.respondText("Hello, $name API!")
                            }
                            
                            get("/health") {
                                call.respondText("OK")
                            }
                        }
                    }
                """.trimIndent())
            }
            else -> {
                // Traditional single-module structure for non-Android
                val srcDir = File(projectDir, "src")
                val mainDir = File(srcDir, "main")
                val testDir = File(srcDir, "test")
                
                File(mainDir, "kotlin").mkdirs()
                File(testDir, "kotlin").mkdirs()
                
                if (type == ProjectType.JVM_APPLICATION) {
                    val mainKt = File(mainDir, "kotlin/Main.kt")
                    mainKt.writeText("""
                        fun main() {
                            println("Hello from $name!")
                        }
                    """.trimIndent())
                }
            }
        }
        
        // Create .gitignore
        createGitignore(projectDir, type)
        
        // Create gradle.properties for Android projects
        if (type == ProjectType.ANDROID_APP || type == ProjectType.ANDROID_LIBRARY) {
            createAndroidGradleProperties(projectDir)
        }
    }
    
    private fun createGitignore(projectDir: File, type: ProjectType) {
        val gitignore = File(projectDir, ".gitignore")
        val androidSection = if (type == ProjectType.ANDROID_APP || type == ProjectType.ANDROID_LIBRARY) {
            """
            
            ### Android ###
            local.properties
            *.apk
            *.aab
            """
        } else ""
        
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
            kpm.lock$androidSection
        """.trimIndent())
    }
    
    private fun handleAndroidSdkSetup(projectDir: File) {
        val androidSdkManager = AndroidSdkManager()
        val sdkInfo = androidSdkManager.detectAndroidSdk()
        
        if (sdkInfo != null) {
            echo("✅ Found Android SDK at: ${sdkInfo.path}")
            androidSdkManager.setupAndroidSdkEnvironment(projectDir, sdkInfo)
        } else {
            echo("Android SDK not found!")
            echo("Install Android Studio or run: kpm android setup")
        }
    }
    
    private fun addComposeDependencies(projectDir: File, tomlParser: TomlParser) {
        echo("Adding Compose UI dependencies...")
        
        val manifestFile = File(projectDir, "kpm.toml")
        val manifest = tomlParser.parseManifest(manifestFile)
        
        val composeDependencies = mapOf(
            "composeBom" to "androidx.compose:compose-bom:2024.12.01"
        )
        
        val composePlatformDependencies = mapOf(
            "composeUi" to "androidx.compose.ui:ui",
            "composeUiToolingPreview" to "androidx.compose.ui:ui-tooling-preview", 
            "composeMaterial3" to "androidx.compose.material3:material3",
            "composeFoundation" to "androidx.compose.foundation:foundation",
            "composeRuntime" to "androidx.compose.runtime:runtime",
            "activityCompose" to "androidx.activity:activity-compose:1.8.2"
        )
        
        val updatedManifest = manifest.copy(
            dependencies = manifest.dependencies + composeDependencies + composePlatformDependencies
        )
        
        tomlParser.writeManifest(updatedManifest, manifestFile)
        
        // Regenerate Gradle files
        val gradleGenerator = GradleGenerator()
        val buildGradle = gradleGenerator.generateBuildGradle(updatedManifest, KpmLockfile(), projectDir)
        File(projectDir, "build.gradle.kts").writeText(buildGradle)
        
        echo("✅ Compose dependencies added")
    }
    
    private fun createAndroidGradleProperties(projectDir: File) {
        val gradleProperties = File(projectDir, "gradle.properties")
        gradleProperties.writeText("""
            # Project-wide Gradle settings.
            # IDE (e.g. Android Studio) users:
            # Gradle settings configured through the IDE *will override*
            # any settings specified in this file.
            # For more details on how to configure your build environment visit
            # http://www.gradle.org/docs/current/userguide/build_environment.html
            # Specifies the JVM arguments used for the daemon process.
            # The setting is particularly useful for tweaking memory settings.
            org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
            # When configured, Gradle will run in incubating parallel mode.
            # This option should only be used with decoupled projects. For more details, visit
            # https://developer.android.com/r/tools/gradle-multi-project-decoupled-projects
            # org.gradle.parallel=true
            # AndroidX package structure to make it clearer which packages are bundled with the
            # Android operating system, and which are packaged with your app's APK
            # https://developer.android.com/topic/libraries/support-library/androidx-rn
            android.useAndroidX=true
            # Kotlin code style for this project: "official" or "obsolete":
            kotlin.code.style=official
            # Enables namespacing of each library's R class so that its R class includes only the
            # resources declared in the library itself and none from the library's dependencies,
            # thereby reducing the size of the R class for that library
            android.nonTransitiveRClass=true
        """.trimIndent())
    }
    
    private fun showNextSteps(type: ProjectType, compose: Boolean) {
        echo("")
        echo("Next steps:")
        echo("   cd ${projectName}")
        
        when (type) {
            ProjectType.ANDROID_APP -> {
                if (compose) {
                    echo("   # Your Android app with Compose is ready!")
                    echo("   kpm build")
                } else {
                    echo("   kpm add androidx.compose:compose-bom:2024.10.00  # Add Compose UI")
                    echo("   kpm build")
                }
            }
            ProjectType.KTOR_API -> {
                echo("   kpm build")
                echo("   kpm run  # Starts server on http://localhost:8080")
            }
            ProjectType.JVM_APPLICATION -> {
                echo("   kpm add junit --test  # Add testing")
                echo("   kpm build")
                echo("   kpm run")
            }
            else -> {
                echo("   kpm build")
            }
        }
        echo("")
    }
}
