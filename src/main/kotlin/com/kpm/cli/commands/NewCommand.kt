package com.kpm.cli.commands

import com.kpm.cli.Command
import com.kpm.cli.echo
import com.kpm.config.TomlParser
import com.kpm.config.GlobalConfigManager
import com.kpm.gradle.GradleGenerator
import com.kpm.model.*
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
        
        projectDir.mkdirs()
        
        // Create manifest with smart defaults
        val manifest = createSmartManifest(name, projectType)
        
        // Create project structure
        createProjectStructure(projectDir, projectType, name)
        
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
        gradleGenerator.generateGradleWrapper(projectDir)
        
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
    
    private fun createSmartManifest(name: String, type: ProjectType): KpmManifest {
        val globalConfig = GlobalConfigManager().getGlobalConfig()
        
        val project = ProjectConfig(
            name = name,
            version = "0.1.0",
            type = type,
            kotlinVersion = globalConfig.defaultKotlinVersion ?: "2.0.0"
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
        val globalConfig = GlobalConfigManager().getGlobalConfig()
        val buildOpts = globalConfig.buildOptimizations
        
        val gradleProperties = File(projectDir, "gradle.properties")
        gradleProperties.writeText("""
            android.useAndroidX=true
            android.enableJetifier=true
            
            # Memory optimization for Android builds
            org.gradle.jvmargs=-Xmx${buildOpts.maxHeapSize} -XX:MaxMetaspaceSize=512m -XX:+HeapDumpOnOutOfMemoryError
            org.gradle.parallel=${buildOpts.parallelBuilds}
            org.gradle.caching=${buildOpts.buildCache}
            org.gradle.configureondemand=${buildOpts.configureOnDemand}
            
            # Android build optimizations
            android.enableR8.fullMode=true
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
