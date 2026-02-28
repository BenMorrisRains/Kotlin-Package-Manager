package com.kpm.cli.commands

import com.kpm.cli.ProgressIndicator
import com.kpm.config.TomlParser
import com.kpm.gradle.GradleGenerator
import com.kpm.model.*
import java.io.File

open class ComposeMultiplatformScaffolder(
    private val projectName: String,
    private val android: Boolean,
    private val ios: Boolean,
    private val desktop: Boolean,
    private val web: Boolean,
    private val wasm: Boolean,
    private val server: Boolean
) {
    
    fun create() {
        if (!android && !ios && !desktop && !web && !wasm) {
            println("Error: At least one platform must be specified (--android, --ios, --desktop, --web, or --wasm)")
            return
        }
        
        val currentDir = File(System.getProperty("user.dir"))
        val projectDir = File(currentDir, projectName)
        
        if (projectDir.exists() && projectDir.listFiles()?.isNotEmpty() == true) {
            println("Error: Directory $projectName already exists and is not empty")
            return
        }
        
        println("Creating Compose Multiplatform project: $projectName")
        println("Platforms: ${getPlatformsList()}")
        println("")
        
        val gradleVersion = promptForGradleVersion()
        val agpVersion = if (android) promptForAgpVersion() else null
        
        println("")
        
        projectDir.mkdirs()
        
        val manifest = createCmpManifest(gradleVersion, agpVersion)
        
        val progress = ProgressIndicator("Creating project structure...")
        progress.start()
        
        try {
            createCmpProjectStructure(projectDir)
            progress.succeed("Project structure created")
        } catch (e: Exception) {
            progress.fail("Failed to create project structure: ${e.message}")
            throw e
        }
        
        val tomlParser = TomlParser()
        tomlParser.writeManifest(manifest, File(projectDir, "kpm.toml"))
        
        val gradleProgress = ProgressIndicator("Generating Gradle build files...")
        gradleProgress.start()
        
        try {
            generateCmpGradleFiles(projectDir, manifest)
            gradleProgress.succeed("Gradle build files generated")
        } catch (e: Exception) {
            gradleProgress.fail("Failed to generate Gradle files: ${e.message}")
            throw e
        }
        
        val wrapperProgress = ProgressIndicator("Setting up Gradle wrapper...")
        wrapperProgress.start()
        try {
            val gradleGenerator = GradleGenerator()
            gradleGenerator.generateGradleWrapper(projectDir, manifest.project.gradleVersion)
            wrapperProgress.succeed("Gradle wrapper configured")
        } catch (e: Exception) {
            wrapperProgress.fail("Failed to setup Gradle wrapper")
            throw e
        }
        
        tomlParser.writeLockfile(KpmLockfile(), File(projectDir, "kpm.lock"))
        
        if (android) {
            handleAndroidSetup(projectDir)
        }
        
        println("✅ Created Compose Multiplatform project: $projectName")
        println("Project directory: ${projectDir.absolutePath}")
        showNextSteps()
    }
    
    private fun getPlatformsList(): String {
        val platforms = mutableListOf<String>()
        if (android) platforms.add("Android")
        if (ios) platforms.add("iOS")
        if (desktop) platforms.add("Desktop")
        if (web) platforms.add("Web")
        if (wasm) platforms.add("WebAssembly")
        if (server) platforms.add("Server")
        return platforms.joinToString(", ")
    }
    
    private fun promptForGradleVersion(): String {
        println("Select Gradle version:")
        println("  1) 8.13 (recommended for CMP 1.10.0)")
        println("  2) 8.11")
        println("  3) 8.10")
        println("  4) Custom version")
        println("")
        
        val choice = readLine()?.trim()?.takeIf { it.isNotEmpty() } ?: "1"
        
        return when (choice) {
            "1" -> "8.13"
            "2" -> "8.11"
            "3" -> "8.10"
            "4" -> {
                print("Enter Gradle version (e.g., 8.13): ")
                readlnOrNull()?.trim() ?: "8.13"
            }
            else -> {
                println("Invalid choice, using default: 8.13")
                "8.13"
            }
        }
    }
    
    private fun promptForAgpVersion(): String {
        println("Select Android Gradle Plugin (AGP) version:")
        println("  1) 8.11.2 (recommended)")
        println("  2) 8.7.3")
        println("  3) 8.6.1")
        println("  4) Custom version")
        println("")
        
        val choice = readLine()?.trim()?.takeIf { it.isNotEmpty() } ?: "1"
        
        return when (choice) {
            "1" -> "8.11.2"
            "2" -> "8.7.3"
            "3" -> "8.6.1"
            "4" -> {
                print("Enter AGP version (e.g., 8.11.2): ")
                readlnOrNull()?.trim() ?: "8.11.2"
            }
            else -> {
                println("Invalid choice, using default: 8.11.2")
                "8.11.2"
            }
        }
    }
    
    private fun createCmpManifest(gradleVersion: String, agpVersion: String?): KpmManifest {
        val project = ProjectConfig(
            name = projectName,
            version = "1.0.0",
            type = ProjectType.COMPOSE_MULTIPLATFORM,
            kotlinVersion = "2.3.0",
            gradleVersion = gradleVersion,
            agpVersion = agpVersion
        )
        
        val androidConfig = if (android) {
            AndroidConfig(
                applicationId = "org.example.project",
                namespace = "org.example.project",
                minSdk = 24,
                targetSdk = 36,
                compileSdk = 36
            )
        } else null
        
        val repositories = RepositoryConfig(
            mavenCentral = true,
            google = android,
            gradlePluginPortal = true
        )
        
        return KpmManifest(
            project = project,
            android = androidConfig,
            repositories = repositories,
            dependencies = emptyMap()
        )
    }
    
    protected fun createCmpProjectStructure(projectDir: File) {
        createRootFiles(projectDir)
        createComposeAppModule(projectDir)
        
        if (ios) {
            createIosApp(projectDir)
        }
        
        if (server) {
            createServerModule(projectDir)
        }
    }
    
    private fun createRootFiles(projectDir: File) {
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
            *.apk
            *.aab
            
            ### iOS ###
            iosApp/Pods/
            iosApp/*.xcworkspace
            iosApp/*.xcodeproj/xcuserdata/
            iosApp/*.xcodeproj/project.xcworkspace/xcuserdata/
        """.trimIndent())
        
        val gradleProperties = File(projectDir, "gradle.properties")
        val androidProps = if (android) {
            """
            
            #Android
            android.nonTransitiveRClass=true
            android.useAndroidX=true
            """.trimIndent()
        } else ""
        
        gradleProperties.writeText("""
            #Kotlin
            kotlin.code.style=official
            kotlin.daemon.jvmargs=-Xmx3072M
            
            #Gradle
            org.gradle.jvmargs=-Xmx4096M -Dfile.encoding=UTF-8
            org.gradle.configuration-cache=true
            org.gradle.caching=true$androidProps
        """.trimIndent())
    }
    
    private fun createComposeAppModule(projectDir: File) {
        val composeAppDir = File(projectDir, "composeApp")
        composeAppDir.mkdirs()
        
        File(composeAppDir, ".gitignore").writeText("/build")
        
        val srcDir = File(composeAppDir, "src")
        
        createCommonMain(srcDir)
        createCommonTest(srcDir)
        
        if (android) {
            createAndroidMain(srcDir)
        }
        
        if (ios) {
            createIosMain(srcDir)
        }
        
        if (desktop) {
            createJvmMain(srcDir)
        }
        
        if (web) {
            createWebMain(srcDir)
            createWebResources(composeAppDir)
        }
    }
    
    private fun createCommonMain(srcDir: File) {
        val commonMainDir = File(srcDir, "commonMain/kotlin/org/example/project")
        commonMainDir.mkdirs()
        
        val appKt = File(commonMainDir, "App.kt")
        appKt.writeText("""
            package org.example.project
            
            import androidx.compose.animation.AnimatedVisibility
            import androidx.compose.foundation.background
            import androidx.compose.foundation.layout.Column
            import androidx.compose.foundation.layout.fillMaxSize
            import androidx.compose.foundation.layout.fillMaxWidth
            import androidx.compose.foundation.layout.padding
            import androidx.compose.foundation.layout.safeContentPadding
            import androidx.compose.material3.Button
            import androidx.compose.material3.MaterialTheme
            import androidx.compose.material3.Text
            import androidx.compose.runtime.*
            import androidx.compose.ui.Alignment
            import androidx.compose.ui.Modifier
            import androidx.compose.ui.tooling.preview.Preview
            import androidx.compose.ui.unit.dp
            
            @Composable
            @Preview
            fun App() {
                MaterialTheme {
                    var showContent by remember { mutableStateOf(false) }
                    Column(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.background)
                            .safeContentPadding()
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Button(onClick = { showContent = !showContent }) {
                            Text("Click me!")
                        }
                        AnimatedVisibility(showContent) {
                            val greeting = remember { Greeting().greet() }
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = greeting,
                                    style = MaterialTheme.typography.headlineMedium
                                )
                            }
                        }
                    }
                }
            }
        """.trimIndent())
        
        val composeResourcesDir = File(srcDir, "commonMain/composeResources")
        composeResourcesDir.mkdirs()
        File(composeResourcesDir, "drawable").mkdirs()
        File(composeResourcesDir, "values").mkdirs()
        
        // Add Greeting class
        val greetingKt = File(commonMainDir, "Greeting.kt")
        greetingKt.writeText("""
            package org.example.project
            
            class Greeting {
                private val platform = getPlatform()
            
                fun greet(): String {
                    return "Hello, ${'$'}{platform.name}!"
                }
            }
        """.trimIndent())
        
        // Add Platform interface
        val platformKt = File(commonMainDir, "Platform.kt")
        platformKt.writeText("""
            package org.example.project
            
            interface Platform {
                val name: String
            }
            
            expect fun getPlatform(): Platform
        """.trimIndent())
    }
    
    private fun createCommonTest(srcDir: File) {
        val commonTestDir = File(srcDir, "commonTest/kotlin")
        commonTestDir.mkdirs()
    }
    
    private fun createAndroidMain(srcDir: File) {
        val androidMainDir = File(srcDir, "androidMain/kotlin/org/example/project")
        androidMainDir.mkdirs()
        
        val platformKt = File(androidMainDir, "Platform.android.kt")
        platformKt.writeText("""
            package org.example.project
            
            import android.os.Build
            
            actual fun getPlatform(): Platform {
                return object : Platform {
                    override val name: String = "Android ${'$'}{Build.VERSION.RELEASE}"
                }
            }
        """.trimIndent())
        
        val mainActivity = File(androidMainDir, "MainActivity.kt")
        mainActivity.writeText("""
            package org.example.project
            
            import android.os.Bundle
            import androidx.activity.ComponentActivity
            import androidx.activity.compose.setContent
            import androidx.activity.enableEdgeToEdge
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.tooling.preview.Preview
            
            class MainActivity : ComponentActivity() {
                override fun onCreate(savedInstanceState: Bundle?) {
                    enableEdgeToEdge()
                    super.onCreate(savedInstanceState)
            
                    setContent {
                        App()
                    }
                }
            }
            
            @Preview
            @Composable
            fun AppAndroidPreview() {
                App()
            }
        """.trimIndent())
        
        val androidManifest = File(srcDir, "androidMain/AndroidManifest.xml")
        androidManifest.parentFile.mkdirs()
        androidManifest.writeText("""
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
            
                <application
                    android:allowBackup="true"
                    android:label="$projectName"
                    android:supportsRtl="true"
                    android:theme="@android:style/Theme.Material.Light.NoActionBar">
                    <activity
                        android:exported="true"
                        android:name=".MainActivity">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
            
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                </application>
            
            </manifest>
        """.trimIndent())
        
        val resDir = File(srcDir, "androidMain/res")
        File(resDir, "values").mkdirs()
        val stringsXml = File(resDir, "values/strings.xml")
        stringsXml.writeText("""
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <string name="app_name">$projectName</string>
            </resources>
        """.trimIndent())
    }
    
    private fun createIosMain(srcDir: File) {
        val iosMainDir = File(srcDir, "iosMain/kotlin/org/example/project")
        iosMainDir.mkdirs()
        
        val mainViewControllerKt = File(iosMainDir, "MainViewController.kt")
        mainViewControllerKt.writeText("""
            package org.example.project
            
            import androidx.compose.ui.window.ComposeUIViewController
            
            fun MainViewController() = ComposeUIViewController { App() }
        """.trimIndent())
        
        val platformKt = File(iosMainDir, "Platform.ios.kt")
        platformKt.writeText("""
            package org.example.project
            
            import platform.UIKit.UIDevice
            
            actual fun getPlatform(): Platform {
                return object : Platform {
                    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
                }
            }
        """.trimIndent())
    }
    
    private fun createJvmMain(srcDir: File) {
        val jvmMainDir = File(srcDir, "jvmMain/kotlin/org/example/project")
        jvmMainDir.mkdirs()
        
        val mainKt = File(jvmMainDir, "main.kt")
        mainKt.writeText("""
            package org.example.project
            
            import androidx.compose.ui.window.Window
            import androidx.compose.ui.window.application
            
            fun main() = application {
                Window(
                    onCloseRequest = ::exitApplication,
                    title = "$projectName",
                ) {
                    App()
                }
            }
        """.trimIndent())
        
        val platformKt = File(jvmMainDir, "Platform.jvm.kt")
        platformKt.writeText("""
            package org.example.project
            
            actual fun getPlatform(): Platform {
                return object : Platform {
                    override val name: String = "Desktop (JVM)"
                }
            }
        """.trimIndent())
    }
    
    private fun createWebMain(srcDir: File) {
        val webMainDir = File(srcDir, "webMain/kotlin/org/example/project")
        webMainDir.mkdirs()
        
        val mainKt = File(webMainDir, "main.kt")
        mainKt.writeText("""
            package org.example.project
            
            import androidx.compose.ui.ExperimentalComposeUiApi
            import androidx.compose.ui.window.ComposeViewport
            
            @OptIn(ExperimentalComposeUiApi::class)
            fun main() {
                ComposeViewport(content = {
                        App()
                    })
            }
        """.trimIndent())
        
        val platformKt = File(webMainDir, "Platform.web.kt")
        platformKt.writeText("""
            package org.example.project
            
            actual fun getPlatform(): Platform {
                return object : Platform {
                    override val name: String = "Web (Kotlin/JS)"
                }
            }
        """.trimIndent())
    }
    
    private fun createWebResources(composeAppDir: File) {
        val webResourcesDir = File(composeAppDir, "src/webMain/resources")
        webResourcesDir.mkdirs()
        
        val indexHtml = File(webResourcesDir, "index.html")
        indexHtml.writeText("""
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>$projectName</title>
                <script src="skiko.js"></script>
            </head>
            <body>
                <div id="root"></div>
                <script src="composeApp.js"></script>
            </body>
            </html>
        """.trimIndent())
    }
    
    private fun createSharedModule(projectDir: File) {
        val sharedDir = File(projectDir, "shared")
        sharedDir.mkdirs()
        
        File(sharedDir, ".gitignore").writeText("/build")
        
        val srcDir = File(sharedDir, "src")
        
        val commonMainDir = File(srcDir, "commonMain/kotlin/org/example/project")
        commonMainDir.mkdirs()
        
        val greetingKt = File(commonMainDir, "Greeting.kt")
        greetingKt.writeText("""
            package org.example.project
            
            class Greeting {
                private val platform = getPlatform()
            
                fun greet(): String {
                    return "Hello, ${'$'}{platform.name}!"
                }
            }
        """.trimIndent())
        
        val platformKt = File(commonMainDir, "Platform.kt")
        platformKt.writeText("""
            package org.example.project
            
            interface Platform {
                val name: String
            }
            
            expect fun getPlatform(): Platform
        """.trimIndent())
        
        if (android) {
            val androidMainDir = File(srcDir, "androidMain/kotlin/org/example/project")
            androidMainDir.mkdirs()
            
            val androidPlatformKt = File(androidMainDir, "Platform.android.kt")
            androidPlatformKt.writeText("""
                package org.example.project
                
                class AndroidPlatform : Platform {
                    override val name: String = "Android ${'$'}{android.os.Build.VERSION.SDK_INT}"
                }
                
                actual fun getPlatform(): Platform = AndroidPlatform()
            """.trimIndent())
        }
        
        if (ios) {
            val iosMainDir = File(srcDir, "iosMain/kotlin/org/example/project")
            iosMainDir.mkdirs()
            
            val iosPlatformKt = File(iosMainDir, "Platform.ios.kt")
            iosPlatformKt.writeText("""
                package org.example.project
                
                import platform.UIKit.UIDevice
                
                class IOSPlatform: Platform {
                    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
                }
                
                actual fun getPlatform(): Platform = IOSPlatform()
            """.trimIndent())
        }
        
        if (desktop || server) {
            val jvmMainDir = File(srcDir, "jvmMain/kotlin/org/example/project")
            jvmMainDir.mkdirs()
            
            val jvmPlatformKt = File(jvmMainDir, "Platform.jvm.kt")
            jvmPlatformKt.writeText("""
                package org.example.project
                
                actual fun getPlatform(): Platform {
                    return object : Platform {
                        override val name: String = "JVM"
                    }
                }
            """.trimIndent())
        }
        
        if (web) {
            val jsMainDir = File(srcDir, "jsMain/kotlin/org/example/project")
            jsMainDir.mkdirs()
            
            val jsPlatformKt = File(jsMainDir, "Platform.js.kt")
            jsPlatformKt.writeText("""
                package org.example.project
                
                class JSPlatform: Platform {
                    override val name: String = "Web with Kotlin/JS"
                }
                
                actual fun getPlatform(): Platform = JSPlatform()
            """.trimIndent())
        }
        
        if (wasm) {
            val wasmJsMainDir = File(srcDir, "wasmJsMain/kotlin/org/example/project")
            wasmJsMainDir.mkdirs()
            
            val wasmPlatformKt = File(wasmJsMainDir, "Platform.wasmJs.kt")
            wasmPlatformKt.writeText("""
                package org.example.project
                
                class WasmPlatform: Platform {
                    override val name: String = "Web with Kotlin/Wasm"
                }
                
                actual fun getPlatform(): Platform = WasmPlatform()
            """.trimIndent())
        }
        
        val commonTestDir = File(srcDir, "commonTest/kotlin")
        commonTestDir.mkdirs()
    }
    
    private fun createIosApp(projectDir: File) {
        val iosAppDir = File(projectDir, "iosApp/iosApp")
        iosAppDir.mkdirs()
        
        val iOSAppSwift = File(iosAppDir, "iOSApp.swift")
        iOSAppSwift.writeText("""
            import SwiftUI
            
            @main
            struct iOSApp: App {
                var body: some Scene {
                    WindowGroup {
                        ContentView()
                    }
                }
            }
        """.trimIndent())
        
        val contentViewSwift = File(iosAppDir, "ContentView.swift")
        contentViewSwift.writeText("""
            import UIKit
            import SwiftUI
            import ComposeApp
            
            struct ComposeView: UIViewControllerRepresentable {
                func makeUIViewController(context: Context) -> UIViewController {
                    MainViewControllerKt.MainViewController()
                }
            
                func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
            }
            
            struct ContentView: View {
                var body: some View {
                    ComposeView()
                        .ignoresSafeArea()
                }
            }
        """.trimIndent())
        
        val infoPlist = File(iosAppDir, "Info.plist")
        infoPlist.writeText("""
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
                <key>CFBundleDevelopmentRegion</key>
                <string>${'$'}(DEVELOPMENT_LANGUAGE)</string>
                <key>CFBundleExecutable</key>
                <string>${'$'}(EXECUTABLE_NAME)</string>
                <key>CFBundleIdentifier</key>
                <string>${'$'}(PRODUCT_BUNDLE_IDENTIFIER)</string>
                <key>CFBundleInfoDictionaryVersion</key>
                <string>6.0</string>
                <key>CFBundleName</key>
                <string>${'$'}(PRODUCT_NAME)</string>
                <key>CFBundlePackageType</key>
                <string>${'$'}(PRODUCT_BUNDLE_PACKAGE_TYPE)</string>
                <key>CFBundleShortVersionString</key>
                <string>1.0</string>
                <key>CFBundleVersion</key>
                <string>1</string>
                <key>CADisableMinimumFrameDurationOnPhone</key>
                <true/>
                <key>UILaunchScreen</key>
                <dict/>
                <key>UISupportedInterfaceOrientations</key>
                <array>
                    <string>UIInterfaceOrientationPortrait</string>
                    <string>UIInterfaceOrientationLandscapeLeft</string>
                    <string>UIInterfaceOrientationLandscapeRight</string>
                </array>
                <key>UISupportedInterfaceOrientations~ipad</key>
                <array>
                    <string>UIInterfaceOrientationPortrait</string>
                    <string>UIInterfaceOrientationPortraitUpsideDown</string>
                    <string>UIInterfaceOrientationLandscapeLeft</string>
                    <string>UIInterfaceOrientationLandscapeRight</string>
                </array>
            </dict>
            </plist>
        """.trimIndent())
        
        val assetsDir = File(iosAppDir, "Assets.xcassets")
        assetsDir.mkdirs()
        
        // Create AppIcon.appiconset
        val appIconSetDir = File(assetsDir, "AppIcon.appiconset")
        appIconSetDir.mkdirs()
        
        val appIconContents = File(appIconSetDir, "Contents.json")
        appIconContents.writeText("""
            {
              "images" : [
                {
                  "idiom" : "iphone",
                  "scale" : "2x",
                  "size" : "20x20"
                },
                {
                  "idiom" : "iphone",
                  "scale" : "3x",
                  "size" : "20x20"
                },
                {
                  "idiom" : "iphone",
                  "scale" : "2x",
                  "size" : "29x29"
                },
                {
                  "idiom" : "iphone",
                  "scale" : "3x",
                  "size" : "29x29"
                },
                {
                  "idiom" : "iphone",
                  "scale" : "2x",
                  "size" : "40x40"
                },
                {
                  "idiom" : "iphone",
                  "scale" : "3x",
                  "size" : "40x40"
                },
                {
                  "idiom" : "iphone",
                  "scale" : "2x",
                  "size" : "60x60"
                },
                {
                  "idiom" : "iphone",
                  "scale" : "3x",
                  "size" : "60x60"
                },
                {
                  "idiom" : "ipad",
                  "scale" : "1x",
                  "size" : "20x20"
                },
                {
                  "idiom" : "ipad",
                  "scale" : "2x",
                  "size" : "20x20"
                },
                {
                  "idiom" : "ipad",
                  "scale" : "1x",
                  "size" : "29x29"
                },
                {
                  "idiom" : "ipad",
                  "scale" : "2x",
                  "size" : "29x29"
                },
                {
                  "idiom" : "ipad",
                  "scale" : "1x",
                  "size" : "40x40"
                },
                {
                  "idiom" : "ipad",
                  "scale" : "2x",
                  "size" : "40x40"
                },
                {
                  "idiom" : "ipad",
                  "scale" : "1x",
                  "size" : "76x76"
                },
                {
                  "idiom" : "ipad",
                  "scale" : "2x",
                  "size" : "76x76"
                },
                {
                  "idiom" : "ipad",
                  "scale" : "2x",
                  "size" : "83.5x83.5"
                },
                {
                  "idiom" : "ios-marketing",
                  "scale" : "1x",
                  "size" : "1024x1024"
                }
              ],
              "info" : {
                "author" : "xcode",
                "version" : 1
              }
            }
        """.trimIndent())
        
        // Create Assets.xcassets Contents.json
        val assetsContents = File(assetsDir, "Contents.json")
        assetsContents.writeText("""
            {
              "info" : {
                "author" : "xcode",
                "version" : 1
              }
            }
        """.trimIndent())
        
        val previewContentDir = File(iosAppDir, "Preview Content")
        previewContentDir.mkdirs()
        
        // Create Xcode project
        val xcodeProjectDir = File(projectDir, "iosApp/iosApp.xcodeproj")
        xcodeProjectDir.mkdirs()
        
        val projectPbxproj = File(xcodeProjectDir, "project.pbxproj")
        projectPbxproj.writeText(generateXcodeProject(projectName))
        
        // Create xcschemes directory and default scheme
        val xcschemesDir = File(xcodeProjectDir, "xcshareddata/xcschemes")
        xcschemesDir.mkdirs()
        
        val schemeFile = File(xcschemesDir, "iosApp.xcscheme")
        schemeFile.writeText(generateXcodeScheme())
        
        // Create Configuration directory with Config.xcconfig
        val configDir = File(projectDir, "iosApp/Configuration")
        configDir.mkdirs()
        
        val configXcconfig = File(configDir, "Config.xcconfig")
        configXcconfig.writeText("""
            TEAM_ID=
            BUNDLE_ID=org.example.project
            APP_NAME=$projectName
            
            KOTLIN_FRAMEWORK_BUILD_TYPE = Debug
            FRAMEWORK_SEARCH_PATHS=${'$'}(SRCROOT)/../composeApp/build/xcode-frameworks/${'$'}(CONFIGURATION)/${'$'}(SDK_NAME)
        """.trimIndent())
    }
    
    private fun generateXcodeProject(projectName: String): String {
        return """
// !${'$'}*UTF8*${'$'}!
{
	archiveVersion = 1;
	classes = {
	};
	objectVersion = 77;
	objects = {

/* Begin PBXFileReference section */
		2152FB032600AC8F00CF470E /* iosApp.app */ = {isa = PBXFileReference; explicitFileType = wrapper.application; includeInIndex = 0; path = iosApp.app; sourceTree = BUILT_PRODUCTS_DIR; };
/* End PBXFileReference section */

/* Begin PBXFileSystemSynchronizedBuildFileExceptionSet section */
		7555FFB92600AC8F00CF470E /* Exceptions for "iosApp" folder in "iosApp" target */ = {
			isa = PBXFileSystemSynchronizedBuildFileExceptionSet;
			membershipExceptions = (
				Info.plist,
			);
			target = 7555FF7A2600AC8F00CF470E /* iosApp */;
		};
/* End PBXFileSystemSynchronizedBuildFileExceptionSet section */

/* Begin PBXFileSystemSynchronizedRootGroup section */
		7555FF7D2600AC8F00CF470E /* iosApp */ = {
			isa = PBXFileSystemSynchronizedRootGroup;
			exceptions = (
				7555FFB92600AC8F00CF470E /* Exceptions for "iosApp" folder in "iosApp" target */,
			);
			path = iosApp;
			sourceTree = "<group>";
		};
		AB3632DC29227652001CCB65 /* Configuration */ = {
			isa = PBXFileSystemSynchronizedRootGroup;
			path = Configuration;
			sourceTree = "<group>";
		};
/* End PBXFileSystemSynchronizedRootGroup section */

/* Begin PBXFrameworksBuildPhase section */
		B92378962600AC8F00CF470E /* Frameworks */ = {
			isa = PBXFrameworksBuildPhase;
			buildActionMask = 2147483647;
			files = (
			);
			runOnlyForDeploymentPostprocessing = 0;
		};
/* End PBXFrameworksBuildPhase section */

/* Begin PBXGroup section */
		7555FF722600AC8F00CF470E = {
			isa = PBXGroup;
			children = (
				AB3632DC29227652001CCB65 /* Configuration */,
				7555FF7D2600AC8F00CF470E /* iosApp */,
				7555FF8C2600AC8F00CF470E /* Products */,
			);
			sourceTree = "<group>";
		};
		7555FF8C2600AC8F00CF470E /* Products */ = {
			isa = PBXGroup;
			children = (
				2152FB032600AC8F00CF470E /* iosApp.app */,
			);
			name = Products;
			sourceTree = "<group>";
		};
/* End PBXGroup section */

/* Begin PBXNativeTarget section */
		7555FF7A2600AC8F00CF470E /* iosApp */ = {
			isa = PBXNativeTarget;
			buildConfigurationList = 7555FFA52600AC8F00CF470E /* Build configuration list for PBXNativeTarget "iosApp" */;
			buildPhases = (
				F36B1CEB2AD836FC00CB74D5 /* Compile Kotlin Framework */,
				7555FF772600AC8F00CF470E /* Sources */,
				B92378962600AC8F00CF470E /* Frameworks */,
				7555FF8A2600AC8F00CF470E /* Resources */,
			);
			buildRules = (
			);
			dependencies = (
			);
			fileSystemSynchronizedGroups = (
				7555FF7D2600AC8F00CF470E /* iosApp */,
			);
			name = iosApp;
			packageProductDependencies = (
			);
			productName = iosApp;
			productReference = 2152FB032600AC8F00CF470E /* iosApp.app */;
			productType = "com.apple.product-type.application";
		};
/* End PBXNativeTarget section */

/* Begin PBXProject section */
		7555FF732600AC8F00CF470E /* Project object */ = {
			isa = PBXProject;
			attributes = {
				BuildIndependentTargetsInParallel = 1;
				LastSwiftUpdateCheck = 1500;
				LastUpgradeCheck = 1500;
			};
			buildConfigurationList = 7555FF762600AC8F00CF470E /* Build configuration list for PBXProject "iosApp" */;
			developmentRegion = en;
			hasScannedForEncodings = 0;
			knownRegions = (
				en,
				Base,
			);
			mainGroup = 7555FF722600AC8F00CF470E;
			minimizedProjectReferenceProxies = 1;
			preferredProjectObjectVersion = 77;
			productRefGroup = 7555FF8C2600AC8F00CF470E /* Products */;
			projectDirPath = "";
			projectRoot = "";
			targets = (
				7555FF7A2600AC8F00CF470E /* iosApp */,
			);
		};
/* End PBXProject section */

/* Begin PBXResourcesBuildPhase section */
		7555FF8A2600AC8F00CF470E /* Resources */ = {
			isa = PBXResourcesBuildPhase;
			buildActionMask = 2147483647;
			files = (
			);
			runOnlyForDeploymentPostprocessing = 0;
		};
/* End PBXResourcesBuildPhase section */

/* Begin PBXShellScriptBuildPhase section */
		F36B1CEB2AD836FC00CB74D5 /* Compile Kotlin Framework */ = {
			isa = PBXShellScriptBuildPhase;
			alwaysOutOfDate = 1;
			buildActionMask = 2147483647;
			files = (
			);
			inputFileListPaths = (
			);
			inputPaths = (
			);
			name = "Compile Kotlin Framework";
			outputFileListPaths = (
			);
			outputPaths = (
			);
			runOnlyForDeploymentPostprocessing = 0;
			shellPath = /bin/sh;
			shellScript = "if [ \"YES\" = \"${'$'}OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED\" ]; then\n  echo \"Skipping Gradle build task invocation due to OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED environment variable set to \\\"YES\\\"\"\n  exit 0\nfi\ncd \"${'$'}SRCROOT/..\"\nif [ ! -x ./gradlew ]; then\n  chmod +x ./gradlew\nfi\n./gradlew :composeApp:embedAndSignAppleFrameworkForXcode\n";
		};
/* End PBXShellScriptBuildPhase section */

/* Begin PBXSourcesBuildPhase section */
		7555FF772600AC8F00CF470E /* Sources */ = {
			isa = PBXSourcesBuildPhase;
			buildActionMask = 2147483647;
			files = (
			);
			runOnlyForDeploymentPostprocessing = 0;
		};
/* End PBXSourcesBuildPhase section */

/* Begin XCBuildConfiguration section */
		7555FFA32600AC8F00CF470E /* Debug */ = {
			isa = XCBuildConfiguration;
			baseConfigurationReferenceAnchor = AB3632DC29227652001CCB65 /* Configuration */;
			baseConfigurationReferenceRelativePath = Config.xcconfig;
			buildSettings = {
				ALWAYS_SEARCH_USER_PATHS = NO;
				ASSETCATALOG_COMPILER_GENERATE_SWIFT_ASSET_SYMBOL_EXTENSIONS = YES;
				CLANG_ANALYZER_NONNULL = YES;
				SDKROOT = iphoneos;
				SUPPORTED_PLATFORMS = "iphoneos iphonesimulator";
				SWIFT_EMIT_LOC_STRINGS = NO;
				CLANG_ANALYZER_NUMBER_OBJECT_CONVERSION = YES_AGGRESSIVE;
				CLANG_CXX_LANGUAGE_STANDARD = "gnu++20";
				CLANG_ENABLE_MODULES = YES;
				CLANG_ENABLE_OBJC_ARC = YES;
				CLANG_ENABLE_OBJC_WEAK = YES;
				CLANG_WARN_BLOCK_CAPTURE_AUTORELEASING = YES;
				CLANG_WARN_BOOL_CONVERSION = YES;
				CLANG_WARN_COMMA = YES;
				CLANG_WARN_CONSTANT_CONVERSION = YES;
				CLANG_WARN_DEPRECATED_OBJC_IMPLEMENTATIONS = YES;
				CLANG_WARN_DIRECT_OBJC_ISA_USAGE = YES_ERROR;
				CLANG_WARN_DOCUMENTATION_COMMENTS = YES;
				CLANG_WARN_EMPTY_BODY = YES;
				CLANG_WARN_ENUM_CONVERSION = YES;
				CLANG_WARN_INFINITE_RECURSION = YES;
				CLANG_WARN_INT_CONVERSION = YES;
				CLANG_WARN_NON_LITERAL_NULL_CONVERSION = YES;
				CLANG_WARN_OBJC_IMPLICIT_RETAIN_SELF = YES;
				CLANG_WARN_OBJC_LITERAL_CONVERSION = YES;
				CLANG_WARN_OBJC_ROOT_CLASS = YES_ERROR;
				CLANG_WARN_QUOTED_INCLUDE_IN_FRAMEWORK_HEADER = YES;
				CLANG_WARN_RANGE_LOOP_ANALYSIS = YES;
				CLANG_WARN_STRICT_PROTOTYPES = YES;
				CLANG_WARN_SUSPICIOUS_MOVE = YES;
				CLANG_WARN_UNGUARDED_AVAILABILITY = YES_AGGRESSIVE;
				CLANG_WARN_UNREACHABLE_CODE = YES;
				CLANG_WARN__DUPLICATE_METHOD_MATCH = YES;
				COPY_PHASE_STRIP = NO;
				DEBUG_INFORMATION_FORMAT = "dwarf-with-dsym";
				ENABLE_STRICT_OBJC_MSGSEND = YES;
				ENABLE_TESTABILITY = YES;
				ENABLE_USER_SCRIPT_SANDBOXING = NO;
				GCC_C_LANGUAGE_STANDARD = gnu17;
				GCC_DYNAMIC_NO_PIC = NO;
				GCC_NO_COMMON_BLOCKS = YES;
				GCC_OPTIMIZATION_LEVEL = 0;
				GCC_PREPROCESSOR_DEFINITIONS = (
					"DEBUG=1",
					"${'$'}(inherited)",
				);
				GCC_WARN_64_TO_32_BIT_CONVERSION = YES;
				GCC_WARN_ABOUT_RETURN_TYPE = YES_ERROR;
				GCC_WARN_UNDECLARED_SELECTOR = YES;
				GCC_WARN_UNINITIALIZED_AUTOS = YES_AGGRESSIVE;
				GCC_WARN_UNUSED_FUNCTION = YES;
				GCC_WARN_UNUSED_VARIABLE = YES;
				IPHONEOS_DEPLOYMENT_TARGET = 15.0;
				LOCALIZATION_PREFERS_STRING_CATALOGS = YES;
				MTL_ENABLE_DEBUG_INFO = INCLUDE_SOURCE;
				MTL_FAST_MATH = YES;
				ONLY_ACTIVE_ARCH = YES;
				SWIFT_ACTIVE_COMPILATION_CONDITIONS = "DEBUG ${'$'}(inherited)";
				SWIFT_OPTIMIZATION_LEVEL = "-Onone";
			};
			name = Debug;
		};
		7555FFA42600AC8F00CF470E /* Release */ = {
			isa = XCBuildConfiguration;
			baseConfigurationReferenceAnchor = AB3632DC29227652001CCB65 /* Configuration */;
			baseConfigurationReferenceRelativePath = Config.xcconfig;
			buildSettings = {
				ALWAYS_SEARCH_USER_PATHS = NO;
				ASSETCATALOG_COMPILER_GENERATE_SWIFT_ASSET_SYMBOL_EXTENSIONS = YES;
				CLANG_ANALYZER_NONNULL = YES;
				SDKROOT = iphoneos;
				SUPPORTED_PLATFORMS = "iphoneos iphonesimulator";
				SWIFT_EMIT_LOC_STRINGS = NO;
				CLANG_ANALYZER_NUMBER_OBJECT_CONVERSION = YES_AGGRESSIVE;
				CLANG_CXX_LANGUAGE_STANDARD = "gnu++20";
				CLANG_ENABLE_MODULES = YES;
				CLANG_ENABLE_OBJC_ARC = YES;
				CLANG_ENABLE_OBJC_WEAK = YES;
				CLANG_WARN_BLOCK_CAPTURE_AUTORELEASING = YES;
				CLANG_WARN_BOOL_CONVERSION = YES;
				CLANG_WARN_COMMA = YES;
				CLANG_WARN_CONSTANT_CONVERSION = YES;
				CLANG_WARN_DEPRECATED_OBJC_IMPLEMENTATIONS = YES;
				CLANG_WARN_DIRECT_OBJC_ISA_USAGE = YES_ERROR;
				CLANG_WARN_DOCUMENTATION_COMMENTS = YES;
				CLANG_WARN_EMPTY_BODY = YES;
				CLANG_WARN_ENUM_CONVERSION = YES;
				CLANG_WARN_INFINITE_RECURSION = YES;
				CLANG_WARN_INT_CONVERSION = YES;
				CLANG_WARN_NON_LITERAL_NULL_CONVERSION = YES;
				CLANG_WARN_OBJC_IMPLICIT_RETAIN_SELF = YES;
				CLANG_WARN_OBJC_LITERAL_CONVERSION = YES;
				CLANG_WARN_OBJC_ROOT_CLASS = YES_ERROR;
				CLANG_WARN_QUOTED_INCLUDE_IN_FRAMEWORK_HEADER = YES;
				CLANG_WARN_RANGE_LOOP_ANALYSIS = YES;
				CLANG_WARN_STRICT_PROTOTYPES = YES;
				CLANG_WARN_SUSPICIOUS_MOVE = YES;
				CLANG_WARN_UNGUARDED_AVAILABILITY = YES_AGGRESSIVE;
				CLANG_WARN_UNREACHABLE_CODE = YES;
				CLANG_WARN__DUPLICATE_METHOD_MATCH = YES;
				COPY_PHASE_STRIP = NO;
				DEBUG_INFORMATION_FORMAT = "dwarf-with-dsym";
				ENABLE_NS_ASSERTIONS = NO;
				ENABLE_STRICT_OBJC_MSGSEND = YES;
				ENABLE_USER_SCRIPT_SANDBOXING = NO;
				GCC_C_LANGUAGE_STANDARD = gnu17;
				GCC_NO_COMMON_BLOCKS = YES;
				GCC_WARN_64_TO_32_BIT_CONVERSION = YES;
				GCC_WARN_ABOUT_RETURN_TYPE = YES_ERROR;
				GCC_WARN_UNDECLARED_SELECTOR = YES;
				GCC_WARN_UNINITIALIZED_AUTOS = YES_AGGRESSIVE;
				GCC_WARN_UNUSED_FUNCTION = YES;
				GCC_WARN_UNUSED_VARIABLE = YES;
				IPHONEOS_DEPLOYMENT_TARGET = 15.0;
				LOCALIZATION_PREFERS_STRING_CATALOGS = YES;
				MTL_ENABLE_DEBUG_INFO = NO;
				MTL_FAST_MATH = YES;
				SWIFT_COMPILATION_MODE = wholemodule;
			};
			name = Release;
		};
		7555FFA62600AC8F00CF470E /* Debug */ = {
			isa = XCBuildConfiguration;
			buildSettings = {
				ASSETCATALOG_COMPILER_APPICON_NAME = AppIcon;
				ASSETCATALOG_COMPILER_GENERATE_SWIFT_ASSET_SYMBOL_EXTENSIONS = YES;
				CODE_SIGN_IDENTITY = "Apple Development";
				CODE_SIGN_STYLE = Automatic;
				DEAD_CODE_STRIPPING = YES;
				DEVELOPMENT_ASSET_PATHS = "\"iosApp/Preview Content\"";
				ENABLE_HARDENED_RUNTIME = YES;
				ENABLE_PREVIEWS = YES;
				INFOPLIST_FILE = iosApp/Info.plist;
				IPHONEOS_DEPLOYMENT_TARGET = 15.0;
				LD_RUNPATH_SEARCH_PATHS = (
					"${'$'}(inherited)",
					"@executable_path/Frameworks",
				);
				PRODUCT_BUNDLE_IDENTIFIER = "${'$'}(BUNDLE_ID)";
				PRODUCT_NAME = "${'$'}(APP_NAME)";
				SWIFT_VERSION = 5.0;
				TARGETED_DEVICE_FAMILY = "1,2";
			};
			name = Debug;
		};
		7555FFA72600AC8F00CF470E /* Release */ = {
			isa = XCBuildConfiguration;
			buildSettings = {
				ASSETCATALOG_COMPILER_APPICON_NAME = AppIcon;
				ASSETCATALOG_COMPILER_GENERATE_SWIFT_ASSET_SYMBOL_EXTENSIONS = YES;
				CODE_SIGN_IDENTITY = "Apple Development";
				CODE_SIGN_STYLE = Automatic;
				DEAD_CODE_STRIPPING = YES;
				DEVELOPMENT_ASSET_PATHS = "\"iosApp/Preview Content\"";
				ENABLE_HARDENED_RUNTIME = YES;
				ENABLE_PREVIEWS = YES;
				INFOPLIST_FILE = iosApp/Info.plist;
				IPHONEOS_DEPLOYMENT_TARGET = 15.0;
				LD_RUNPATH_SEARCH_PATHS = (
					"${'$'}(inherited)",
					"@executable_path/Frameworks",
				);
				PRODUCT_BUNDLE_IDENTIFIER = "${'$'}(BUNDLE_ID)";
				PRODUCT_NAME = "${'$'}(APP_NAME)";
				SWIFT_VERSION = 5.0;
				TARGETED_DEVICE_FAMILY = "1,2";
			};
			name = Release;
		};
/* End XCBuildConfiguration section */

/* Begin XCConfigurationList section */
		7555FF762600AC8F00CF470E /* Build configuration list for PBXProject "iosApp" */ = {
			isa = XCConfigurationList;
			buildConfigurations = (
				7555FFA32600AC8F00CF470E /* Debug */,
				7555FFA42600AC8F00CF470E /* Release */,
			);
			defaultConfigurationIsVisible = 0;
			defaultConfigurationName = Release;
		};
		7555FFA52600AC8F00CF470E /* Build configuration list for PBXNativeTarget "iosApp" */ = {
			isa = XCConfigurationList;
			buildConfigurations = (
				7555FFA62600AC8F00CF470E /* Debug */,
				7555FFA72600AC8F00CF470E /* Release */,
			);
			defaultConfigurationIsVisible = 0;
			defaultConfigurationName = Release;
		};
/* End XCConfigurationList section */
	};
	rootObject = 7555FF732600AC8F00CF470E /* Project object */;
}
        """.trimIndent()
    }
    
    private fun generateXcodeScheme(): String {
        return """
<?xml version="1.0" encoding="UTF-8"?>
<Scheme
   LastUpgradeVersion = "1500"
   version = "1.7">
   <BuildAction
      parallelizeBuildables = "YES"
      buildImplicitDependencies = "YES">
      <BuildActionEntries>
         <BuildActionEntry
            buildForTesting = "YES"
            buildForRunning = "YES"
            buildForProfiling = "YES"
            buildForArchiving = "YES"
            buildForAnalyzing = "YES">
            <BuildableReference
               BuildableIdentifier = "primary"
               BlueprintIdentifier = "7555FF7A2600AC8F00CF470E"
               BuildableName = "iosApp.app"
               BlueprintName = "iosApp"
               ReferencedContainer = "container:iosApp.xcodeproj">
            </BuildableReference>
         </BuildActionEntry>
      </BuildActionEntries>
   </BuildAction>
   <TestAction
      buildConfiguration = "Debug"
      selectedDebuggerIdentifier = "Xcode.DebuggerFoundation.Debugger.LLDB"
      selectedLauncherIdentifier = "Xcode.DebuggerFoundation.Launcher.LLDB"
      shouldUseLaunchSchemeArgsEnv = "YES"
      shouldAutocreateTestPlan = "YES">
   </TestAction>
   <LaunchAction
      buildConfiguration = "Debug"
      selectedDebuggerIdentifier = "Xcode.DebuggerFoundation.Debugger.LLDB"
      selectedLauncherIdentifier = "Xcode.DebuggerFoundation.Launcher.LLDB"
      launchStyle = "0"
      useCustomWorkingDirectory = "NO"
      ignoresPersistentStateOnLaunch = "NO"
      debugDocumentVersioning = "YES"
      debugServiceExtension = "internal"
      allowLocationSimulation = "YES">
      <BuildableProductRunnable
         runnableDebuggingMode = "0">
         <BuildableReference
            BuildableIdentifier = "primary"
            BlueprintIdentifier = "7555FF7A2600AC8F00CF470E"
            BuildableName = "iosApp.app"
            BlueprintName = "iosApp"
            ReferencedContainer = "container:iosApp.xcodeproj">
         </BuildableReference>
      </BuildableProductRunnable>
   </LaunchAction>
   <ProfileAction
      buildConfiguration = "Release"
      shouldUseLaunchSchemeArgsEnv = "YES"
      savedToolIdentifier = ""
      useCustomWorkingDirectory = "NO"
      debugDocumentVersioning = "YES">
      <BuildableProductRunnable
         runnableDebuggingMode = "0">
         <BuildableReference
            BuildableIdentifier = "primary"
            BlueprintIdentifier = "7555FF7A2600AC8F00CF470E"
            BuildableName = "iosApp.app"
            BlueprintName = "iosApp"
            ReferencedContainer = "container:iosApp.xcodeproj">
         </BuildableReference>
      </BuildableProductRunnable>
   </ProfileAction>
   <AnalyzeAction
      buildConfiguration = "Debug">
   </AnalyzeAction>
   <ArchiveAction
      buildConfiguration = "Release"
      revealArchiveInOrganizer = "YES">
   </ArchiveAction>
</Scheme>
        """.trimIndent()
    }
    
    private fun createServerModule(projectDir: File) {
        val serverDir = File(projectDir, "server")
        serverDir.mkdirs()
        
        File(serverDir, ".gitignore").writeText("/build")
        
        val srcDir = File(serverDir, "src")
        val mainDir = File(srcDir, "main/kotlin/org/example/project")
        mainDir.mkdirs()
        
        val applicationKt = File(mainDir, "Application.kt")
        applicationKt.writeText("""
            package org.example.project
            
            import io.ktor.server.application.*
            import io.ktor.server.engine.*
            import io.ktor.server.netty.*
            import io.ktor.server.response.*
            import io.ktor.server.routing.*
            
            fun main() {
                embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
                    .start(wait = true)
            }
            
            fun Application.module() {
                routing {
                    get("/") {
                        call.respondText("Hello from $projectName Server!")
                    }
                    
                    get("/health") {
                        call.respondText("OK")
                    }
                }
            }
        """.trimIndent())
        
        val testDir = File(srcDir, "test/kotlin")
        testDir.mkdirs()
    }
    
    protected fun generateCmpGradleFiles(projectDir: File, manifest: KpmManifest) {
        generateRootBuildGradle(projectDir)
        generateSettingsGradle(projectDir)
        generateLibsVersionsToml(projectDir, manifest)
        generateComposeAppBuildGradle(projectDir)
        
        if (server) {
            generateServerBuildGradle(projectDir)
        }
    }
    
    private fun generateRootBuildGradle(projectDir: File) {
        val buildGradle = File(projectDir, "build.gradle.kts")
        
        val androidPlugin = if (android) {
            "    alias(libs.plugins.androidApplication) apply false\n" +
            "    alias(libs.plugins.androidLibrary) apply false\n"
        } else ""
        
        val ktorPlugin = if (server) {
            "    alias(libs.plugins.ktor) apply false\n"
        } else ""
        
        buildGradle.writeText("""
            plugins {
                // this is necessary to avoid the plugins to be loaded multiple times
                // in each subproject's classloader
            ${androidPlugin}    alias(libs.plugins.composeHotReload) apply false
                alias(libs.plugins.composeMultiplatform) apply false
                alias(libs.plugins.composeCompiler) apply false
                alias(libs.plugins.kotlinJvm) apply false
                alias(libs.plugins.kotlinMultiplatform) apply false
            ${ktorPlugin}}
        """.trimIndent())
    }
    
    private fun generateSettingsGradle(projectDir: File) {
        val settingsGradle = File(projectDir, "settings.gradle.kts")
        
        settingsGradle.writeText("""
            rootProject.name = "$projectName"
            enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
            
            pluginManagement {
                repositories {
                    google {
                        mavenContent {
                            includeGroupAndSubgroups("androidx")
                            includeGroupAndSubgroups("com.android")
                            includeGroupAndSubgroups("com.google")
                        }
                    }
                    mavenCentral()
                    gradlePluginPortal()
                }
            }
            
            dependencyResolutionManagement {
                repositories {
                    google {
                        mavenContent {
                            includeGroupAndSubgroups("androidx")
                            includeGroupAndSubgroups("com.android")
                            includeGroupAndSubgroups("com.google")
                        }
                    }
                    mavenCentral()
                }
            }
            
            plugins {
                id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
            }
            
            include(":composeApp")${if (server) "\ninclude(\":server\")" else ""}
        """.trimIndent())
    }
    
    private fun generateLibsVersionsToml(projectDir: File, manifest: KpmManifest) {
        val gradleDir = File(projectDir, "gradle")
        gradleDir.mkdirs()
        
        val libsVersionsToml = File(gradleDir, "libs.versions.toml")
        
        val androidVersions = if (android) {
            """agp = "${manifest.project.agpVersion ?: "8.11.2"}"
android-compileSdk = "36"
android-minSdk = "24"
android-targetSdk = "36"
androidx-activity = "1.12.2"
androidx-appcompat = "1.7.1"
androidx-core = "1.17.0"
androidx-espresso = "3.7.0"
androidx-lifecycle = "2.9.6"
androidx-testExt = "1.3.0"
"""
        } else ""
        
        val androidLibraries = if (android) {
            """androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "androidx-core" }
androidx-testExt-junit = { module = "androidx.test.ext:junit", version.ref = "androidx-testExt" }
androidx-espresso-core = { module = "androidx.test.espresso:espresso-core", version.ref = "androidx-espresso" }
androidx-appcompat = { module = "androidx.appcompat:appcompat", version.ref = "androidx-appcompat" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "androidx-activity" }
compose-uiTooling = { module = "org.jetbrains.compose.ui:ui-tooling", version.ref = "composeMultiplatform" }
"""
        } else ""
        
        val androidPlugins = if (android) {
            """androidApplication = { id = "com.android.application", version.ref = "agp" }
androidLibrary = { id = "com.android.library", version.ref = "agp" }
"""
        } else ""
        
        val desktopLibraries = if (desktop) {
            """kotlinx-coroutinesSwing = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-swing", version.ref = "kotlinx-coroutines" }
"""
        } else ""
        
        val ktorVersion = if (server) {
            "ktor = \"3.3.3\"\nlogback = \"1.5.24\"\n"
        } else ""
        
        libsVersionsToml.writeText("""
[versions]
${androidVersions}composeHotReload = "1.0.0"
composeMultiplatform = "1.10.0"
junit = "4.13.2"
kotlin = "${manifest.project.kotlinVersion}"
kotlinx-coroutines = "1.10.2"
${ktorVersion}material3 = "1.10.0-alpha05"

[libraries]
kotlin-test = { module = "org.jetbrains.kotlin:kotlin-test", version.ref = "kotlin" }
kotlin-testJunit = { module = "org.jetbrains.kotlin:kotlin-test-junit", version.ref = "kotlin" }
junit = { module = "junit:junit", version.ref = "junit" }
${androidLibraries}androidx-lifecycle-viewmodelCompose = { module = "org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "androidx-lifecycle" }
androidx-lifecycle-runtimeCompose = { module = "org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose", version.ref = "androidx-lifecycle" }
compose-runtime = { module = "org.jetbrains.compose.runtime:runtime", version.ref = "composeMultiplatform" }
compose-foundation = { module = "org.jetbrains.compose.foundation:foundation", version.ref = "composeMultiplatform" }
compose-material3 = { module = "org.jetbrains.compose.material3:material3", version.ref = "material3" }
compose-ui = { module = "org.jetbrains.compose.ui:ui", version.ref = "composeMultiplatform" }
compose-components-resources = { module = "org.jetbrains.compose.components:components-resources", version.ref = "composeMultiplatform" }
compose-uiToolingPreview = { module = "org.jetbrains.compose.ui:ui-tooling-preview", version.ref = "composeMultiplatform" }
${desktopLibraries}${if (server) "logback = { module = \"ch.qos.logback:logback-classic\", version.ref = \"logback\" }\nktor-serverCore = { module = \"io.ktor:ktor-server-core-jvm\", version.ref = \"ktor\" }\nktor-serverNetty = { module = \"io.ktor:ktor-server-netty-jvm\", version.ref = \"ktor\" }\nktor-serverTestHost = { module = \"io.ktor:ktor-server-test-host-jvm\", version.ref = \"ktor\" }\n" else ""}
[plugins]
${androidPlugins}composeHotReload = { id = "org.jetbrains.compose.hot-reload", version.ref = "composeHotReload" }
composeMultiplatform = { id = "org.jetbrains.compose", version.ref = "composeMultiplatform" }
composeCompiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlinJvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlinMultiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }${if (server) "\nktor = { id = \"io.ktor.plugin\", version.ref = \"ktor\" }" else ""}
        """.trimIndent())
    }
    
    private fun generateComposeAppBuildGradle(projectDir: File) {
        val composeAppDir = File(projectDir, "composeApp")
        val buildGradle = File(composeAppDir, "build.gradle.kts")
        
        val androidImports = if (android) {
            "import org.jetbrains.kotlin.gradle.dsl.JvmTarget\n"
        } else ""
        
        val wasmImport = if (wasm) {
            "import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl\n"
        } else ""
        
        val desktopImport = if (desktop) {
            "import org.jetbrains.compose.desktop.application.dsl.TargetFormat\n"
        } else ""
        
        val androidPlugin = if (android) {
            "alias(libs.plugins.androidApplication)"
        } else ""
        
        val androidTarget = if (android) {
            """

    androidTarget {
        @OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
"""
        } else ""
        
        val iosTargets = if (ios) {
            """

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }
"""
        } else ""
        
        val jvmTarget = if (desktop) {
            """

    jvm()
"""
        } else ""
        
        val jsTarget = if (web) {
            """

    js {
        browser()
        binaries.executable()
    }
"""
        } else ""
        
        val wasmTarget = if (wasm) {
            """

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }
"""
        } else ""
        
        val androidMainDeps = if (android) {
            """
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
        }
"""
        } else ""
        
        val jvmMainDeps = if (desktop) {
            """
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
        }
"""
        } else ""
        
        val androidBlock = if (android) {
            """
android {
    namespace = "org.example.project"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "org.example.project"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(libs.compose.uiTooling)
}
"""
        } else ""
        
        val desktopBlock = if (desktop) {
            """
compose.desktop {
    application {
        mainClass = "org.example.project.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "org.example.project"
            packageVersion = "1.0.0"
        }
    }
}
"""
        } else ""
        
        val imports = listOfNotNull(
            desktopImport.takeIf { it.isNotEmpty() },
            wasmImport.takeIf { it.isNotEmpty() },
            androidImports.takeIf { it.isNotEmpty() }
        ).joinToString("")
        
        val pluginsList = listOfNotNull(
            "alias(libs.plugins.kotlinMultiplatform)",
            androidPlugin.takeIf { it.isNotEmpty() },
            "alias(libs.plugins.composeMultiplatform)",
            "alias(libs.plugins.composeCompiler)",
            "alias(libs.plugins.composeHotReload)"
        ).joinToString("\n    ")
        
        buildGradle.writeText("""
${imports}plugins {
    $pluginsList
}

kotlin {$androidTarget$iosTargets$jvmTarget$jsTarget$wasmTarget
    sourceSets {$androidMainDeps
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }$jvmMainDeps
    }
}
$androidBlock$desktopBlock
        """.trimIndent())
    }
    
    private fun generateSharedBuildGradle(projectDir: File) {
        val sharedDir = File(projectDir, "shared")
        val buildGradle = File(sharedDir, "build.gradle.kts")
        
        val androidImports = if (android) {
            "import org.jetbrains.kotlin.gradle.dsl.JvmTarget\n"
        } else ""
        
        val wasmImport = if (wasm) {
            "import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl\n"
        } else ""
        
        val androidPlugin = if (android) {
            "    alias(libs.plugins.androidLibrary)\n"
        } else ""
        
        val androidTarget = if (android) {
            """
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    """
        } else ""
        
        val iosTargets = if (ios) {
            """
    iosArm64()
    iosSimulatorArm64()
    """
        } else ""
        
        val jvmTarget = if (desktop || server) {
            """
    jvm()
    """
        } else ""
        
        val jsTarget = if (web) {
            """
    js {
        browser()
    }
    """
        } else ""
        
        val wasmTarget = if (wasm) {
            """
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }
    """
        } else ""
        
        val androidBlock = if (android) {
            """
android {
    namespace = "org.example.project.shared"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}
"""
        } else ""
        
        buildGradle.writeText("""
            ${wasmImport}${androidImports}
            plugins {
                alias(libs.plugins.kotlinMultiplatform)
            ${androidPlugin}}
            
            kotlin {$androidTarget$iosTargets$jvmTarget$jsTarget$wasmTarget
                sourceSets {
                    commonMain.dependencies {
                        // put your Multiplatform dependencies here
                    }
                    commonTest.dependencies {
                        implementation(libs.kotlin.test)
                    }
                }
            }
            $androidBlock
        """.trimIndent())
    }
    
    private fun generateServerBuildGradle(projectDir: File) {
        val serverDir = File(projectDir, "server")
        val buildGradle = File(serverDir, "build.gradle.kts")
        
        buildGradle.writeText("""
            plugins {
                alias(libs.plugins.kotlinJvm)
                alias(libs.plugins.ktor)
                application
            }
            
            group = "org.example.project"
            version = "1.0.0"
            application {
                mainClass.set("org.example.project.ApplicationKt")
                
                val isDevelopment: Boolean = project.ext.has("development")
                applicationDefaultJvmArgs = listOf("-Dio.ktor.development=${'$'}isDevelopment")
            }
            
            dependencies {
                implementation(projects.composeApp)
                implementation(libs.logback)
                implementation(libs.ktor.serverCore)
                implementation(libs.ktor.serverNetty)
                testImplementation(libs.ktor.serverTestHost)
                testImplementation(libs.kotlin.testJunit)
            }
        """.trimIndent())
    }
    
    private fun handleAndroidSetup(projectDir: File) {
        val androidSdkPath = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
        if (androidSdkPath != null) {
            println("✅ Found Android SDK at: $androidSdkPath")
            val localProperties = File(projectDir, "local.properties")
            localProperties.writeText("sdk.dir=$androidSdkPath")
        } else {
            println("⚠️  Android SDK not found!")
            println("Set ANDROID_HOME environment variable or install Android Studio")
        }
    }
    
    private fun showNextSteps() {
        println("")
        println("Next steps:")
        println("   cd $projectName")
        println("   kpm build")
        
        if (android) {
            println("   # For Android: Open in Android Studio or run ./gradlew assembleDebug")
        }
        
        if (ios) {
            println("   # For iOS: Open iosApp/iosApp.xcodeproj in Xcode")
        }
        
        if (desktop) {
            println("   # For Desktop: ./gradlew :composeApp:run")
        }
        
        if (server) {
            println("   # For Server: ./gradlew :server:run")
        }
        
        println("")
    }
}
