package com.kpm.cli.commands

import com.kpm.cli.Command
import com.kpm.cli.echo
import com.kpm.config.GlobalConfigManager
import com.kpm.maven.MavenSearchApi

class ConfigCommand : Command("config", "Manage global KPM configuration") {
    
    private val action by argument("action", "Action: init, list, get, set")
    
    override fun run() {
        val configManager = GlobalConfigManager()
        
        when (action.lowercase()) {
            "init" -> {
                configManager.initializeGlobalConfig()
            }
            "list", "show" -> {
                showGlobalConfig(configManager)
            }
            "get" -> {
                // For get/set, we'll need to access the raw arguments
                val allArgs = arguments.mapNotNull { it.value }
                if (allArgs.size < 2) {
                    echo("Error: Key is required for 'get' action", err = true)
                    showUsage()
                    return
                }
                getConfigValue(configManager, allArgs[1])
            }
            "set" -> {
                val allArgs = arguments.mapNotNull { it.value }
                if (allArgs.size < 3) {
                    echo("Error: Both key and value are required for 'set' action", err = true)
                    showUsage()
                    return
                }
                setConfigValue(configManager, allArgs[1], allArgs[2])
            }
            "add-global" -> {
                val allArgs = arguments.mapNotNull { it.value }
                if (allArgs.size < 2) {
                    echo("Error: Dependency name is required", err = true)
                    echo("Usage: kpm config add-global <name> [version]", err = true)
                    echo("Examples:", err = true)
                    echo("  kpm config add-global timber                    # Latest version", err = true)
                    echo("  kpm config add-global timber 5.0.1             # Specific version", err = true)
                    echo("  kpm config add-global timber com.jakewharton.timber:timber:5.0.1  # Full coordinate", err = true)
                    return
                }
                val dependencyName = allArgs[1]
                val versionOrCoordinate = if (allArgs.size >= 3) allArgs[2] else dependencyName // Use name as version for resolution
                addGlobalDependency(configManager, dependencyName, versionOrCoordinate)
            }
            "remove-global" -> {
                val allArgs = arguments.mapNotNull { it.value }
                if (allArgs.size < 2) {
                    echo("Error: Dependency name is required", err = true)
                    return
                }
                removeGlobalDependency(configManager, allArgs[1])
            }
            "registry" -> {
                val allArgs = arguments.mapNotNull { it.value }
                if (allArgs.size < 3) {
                    echo("Error: Registry name and URL are required", err = true)
                    return
                }
                setCustomRegistry(configManager, allArgs[1], allArgs[2])
            }
            else -> {
                echo("Error: Unknown action '$action'. Use: init, list, get, set, add-global, remove-global, registry", err = true)
                showUsage()
            }
        }
    }
    
    private fun showGlobalConfig(configManager: GlobalConfigManager) {
        val config = configManager.getGlobalConfig()
        
        echo("Global KPM Configuration:")
        echo("")
        echo("User Settings:")
        echo("   default_author = \"${config.defaultAuthor ?: ""}\"")
        echo("   default_license = \"${config.defaultLicense ?: "MIT"}\"")
        echo("   default_kotlin_version = \"${config.defaultKotlinVersion ?: "2.0.0"}\"")
        echo("")
        echo("Android Settings:")
        echo("   sdk_path = \"${config.androidSdkPath ?: ""}\"")
        echo("")
        echo("Repository Settings:")
        config.defaultRepositories.forEach { (repo, enabled) ->
            echo("   $repo = $enabled")
        }
        echo("")
        echo("Build Optimizations:")
        echo("   max_heap_size = \"${config.buildOptimizations.maxHeapSize}\"")
        echo("   parallel_builds = ${config.buildOptimizations.parallelBuilds}")
        echo("   build_cache = ${config.buildOptimizations.buildCache}")
        echo("   configure_on_demand = ${config.buildOptimizations.configureOnDemand}")
        echo("")
        echo("Global Dependencies:")
        if (config.globalDependencies.alwaysInclude.isEmpty()) {
            echo("   (none)")
        } else {
            config.globalDependencies.alwaysInclude.forEach { (name, version) ->
                echo("   $name = \"$version\"")
            }
        }
        echo("")
        echo("Default Test Dependencies:")
        config.globalDependencies.defaultTestDependencies.forEach { (name, version) ->
            echo("   $name = \"$version\"")
        }
        echo("")
        echo("Registry Configuration:")
        echo("   maven_central_url = \"${config.registryConfig.mavenCentralUrl}\"")
        echo("   google_url = \"${config.registryConfig.googleUrl}\"")
        echo("   search_api_url = \"${config.registryConfig.searchApiUrl}\"")
        echo("   timeout = ${config.registryConfig.timeout}")
        if (config.registryConfig.customRegistries.isNotEmpty()) {
            echo("")
            echo("Custom Registries:")
            config.registryConfig.customRegistries.forEach { (name, url) ->
                echo("   $name = \"$url\"")
            }
        }
    }
    
    private fun getConfigValue(configManager: GlobalConfigManager, key: String) {
        val config = configManager.getGlobalConfig()
        
        val value = when (key) {
            "default_author", "user.default_author" -> config.defaultAuthor
            "default_license", "user.default_license" -> config.defaultLicense
            "default_kotlin_version", "user.default_kotlin_version" -> config.defaultKotlinVersion
            "android_sdk_path", "android.sdk_path" -> config.androidSdkPath
            "max_heap_size", "build.max_heap_size" -> config.buildOptimizations.maxHeapSize
            "parallel_builds", "build.parallel_builds" -> config.buildOptimizations.parallelBuilds.toString()
            "build_cache", "build.build_cache" -> config.buildOptimizations.buildCache.toString()
            "configure_on_demand", "build.configure_on_demand" -> config.buildOptimizations.configureOnDemand.toString()
            else -> {
                echo("Error: Unknown configuration key '$key'", err = true)
                return
            }
        }
        
        echo("$key = \"${value ?: ""}\"")
    }
    
    private fun setConfigValue(configManager: GlobalConfigManager, key: String, value: String) {
        val currentConfig = configManager.getGlobalConfig()
        
        val updatedConfig = when (key) {
            "default_author", "user.default_author" -> 
                currentConfig.copy(defaultAuthor = value)
            "default_license", "user.default_license" -> 
                currentConfig.copy(defaultLicense = value)
            "default_kotlin_version", "user.default_kotlin_version" -> 
                currentConfig.copy(defaultKotlinVersion = value)
            "android_sdk_path", "android.sdk_path" -> 
                currentConfig.copy(androidSdkPath = value)
            "max_heap_size", "build.max_heap_size" -> 
                currentConfig.copy(buildOptimizations = currentConfig.buildOptimizations.copy(maxHeapSize = value))
            "parallel_builds", "build.parallel_builds" -> 
                currentConfig.copy(buildOptimizations = currentConfig.buildOptimizations.copy(parallelBuilds = value.toBoolean()))
            "build_cache", "build.build_cache" -> 
                currentConfig.copy(buildOptimizations = currentConfig.buildOptimizations.copy(buildCache = value.toBoolean()))
            "configure_on_demand", "build.configure_on_demand" -> 
                currentConfig.copy(buildOptimizations = currentConfig.buildOptimizations.copy(configureOnDemand = value.toBoolean()))
            else -> {
                echo("Error: Unknown configuration key '$key'", err = true)
                return
            }
        }
        
        configManager.saveGlobalConfig(updatedConfig)
        echo("✅ Set $key = \"$value\"")
    }
    
    private fun addGlobalDependency(configManager: GlobalConfigManager, name: String, versionOrCoordinate: String) {
        val currentConfig = configManager.getGlobalConfig()
        
        // Check if it's a full coordinate (group:artifact:version) or npm-style name
        val (finalName, finalCoordinate) = if (versionOrCoordinate.contains(":")) {
            // Full coordinate provided (e.g., "timber com.jakewharton.timber:timber:5.0.1")
            name to versionOrCoordinate
        } else {
            // npm-style: try to resolve the name (e.g., "timber 5.0.1" or just "timber")
            val resolvedCoordinate = resolveNpmStyleDependency(name, versionOrCoordinate)
            if (resolvedCoordinate != null) {
                name to resolvedCoordinate
            } else {
                echo("❌ Could not resolve dependency: $name", err = true)
                echo("Try using full coordinates: kpm config add-global $name group:artifact:version")
                return
            }
        }
        
        val updatedDependencies = currentConfig.globalDependencies.copy(
            alwaysInclude = currentConfig.globalDependencies.alwaysInclude + (finalName to finalCoordinate)
        )
        val updatedConfig = currentConfig.copy(globalDependencies = updatedDependencies)
        configManager.saveGlobalConfig(updatedConfig)
        echo("✅ Added global dependency: $finalName = \"$finalCoordinate\"")
        echo("This dependency will be included in all new projects")
        echo("")
        echo("💡 To add this dependency to existing projects, run:")
        echo("   kpm sync-global")
    }
    
    private fun resolveNpmStyleDependency(name: String, version: String?): String? {
        // First try the known popular libraries map (like AddCommand does)
        val popularLibraries = mapOf(
            "picasso" to "com.squareup.picasso:picasso",
            "glide" to "com.github.bumptech.glide:glide", 
            "retrofit" to "com.squareup.retrofit2:retrofit",
            "okhttp" to "com.squareup.okhttp3:okhttp",
            "gson" to "com.google.code.gson:gson",
            "hilt" to "com.google.dagger:hilt-android",
            "room" to "androidx.room:room-runtime",
            "coroutines" to "org.jetbrains.kotlinx:kotlinx-coroutines-android",
            "timber" to "com.jakewharton.timber:timber",
            "junit" to "junit:junit",
            "mockito" to "org.mockito:mockito-core",
            "lottie" to "com.airbnb.android:lottie",
            "material" to "com.google.android.material:material",
            "appcompat" to "androidx.appcompat:appcompat",
            "coreKtx" to "androidx.core:core-ktx",
            "constraintlayout" to "androidx.constraintlayout:constraintlayout",
            "recyclerview" to "androidx.recyclerview:recyclerview",
            "cardview" to "androidx.cardview:cardview",
            "viewpager2" to "androidx.viewpager2:viewpager2",
            "fragment" to "androidx.fragment:fragment-ktx",
            "activity" to "androidx.activity:activity-ktx",
            "lifecycle" to "androidx.lifecycle:lifecycle-viewmodel-ktx",
            "navigation" to "androidx.navigation:navigation-fragment-ktx",
            "workmanager" to "androidx.work:work-runtime-ktx",
            "datastore" to "androidx.datastore:datastore-preferences",
            "paging" to "androidx.paging:paging-runtime",
            "camera" to "androidx.camera:camera-camera2",
            "biometric" to "androidx.biometric:biometric",
            "compose-bom" to "androidx.compose:compose-bom",
            "compose-ui" to "androidx.compose.ui:ui",
            "compose-material3" to "androidx.compose.material3:material3",
            "compose-activity" to "androidx.activity:activity-compose",
            "compose-viewmodel" to "androidx.lifecycle:lifecycle-viewmodel-compose",
            "compose-navigation" to "androidx.navigation:navigation-compose",
            "ktor-server" to "io.ktor:ktor-server-core",
            "ktor-netty" to "io.ktor:ktor-server-netty",
            "ktor-client" to "io.ktor:ktor-client-core",
            "exposed" to "org.jetbrains.exposed:exposed-core",
            "koin" to "io.insert-koin:koin-android",
            "coil" to "io.coil-kt:coil",
            "leakcanary" to "com.squareup.leakcanary:leakcanary-android"
        )
        
        val baseCoordinate = popularLibraries[name.lowercase()]
        if (baseCoordinate != null) {
            // If version is provided and not empty, use it; otherwise get latest
            return if (!version.isNullOrEmpty() && version != name) {
                "$baseCoordinate:$version"
            } else {
                // Get latest version from Maven Central
                try {
                    val searchApi = MavenSearchApi()
                    val parts = baseCoordinate.split(":")
                    val latestVersion = searchApi.getLatestVersion(parts[0], parts[1])
                    if (latestVersion != null) {
                        "$baseCoordinate:$latestVersion"
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    echo("⚠️  Could not fetch latest version for $name, using known coordinate")
                    null
                }
            }
        }
        
        // If not in popular libraries, try searching Maven Central
        try {
            val searchApi = MavenSearchApi()
            val results = searchApi.searchArtifact(name)
            if (results.isNotEmpty()) {
                val result = results.first()
                return "${result.groupId}:${result.artifactId}:${result.latestVersion}"
            }
        } catch (e: Exception) {
            // Search failed, return null
        }
        
        return null
    }
    
    private fun removeGlobalDependency(configManager: GlobalConfigManager, name: String) {
        val currentConfig = configManager.getGlobalConfig()
        
        if (!currentConfig.globalDependencies.alwaysInclude.containsKey(name)) {
            echo("❌ Global dependency '$name' not found", err = true)
            return
        }
        
        val updatedDependencies = currentConfig.globalDependencies.copy(
            alwaysInclude = currentConfig.globalDependencies.alwaysInclude - name
        )
        val updatedConfig = currentConfig.copy(globalDependencies = updatedDependencies)
        configManager.saveGlobalConfig(updatedConfig)
        echo("✅ Removed global dependency: $name")
        echo("")
        echo("💡 To sync existing projects with this change, run:")
        echo("   kpm sync-global")
        echo("")
        echo("Or run it in each project directory to remove '$name' from local projects.")
    }
    
    private fun setCustomRegistry(configManager: GlobalConfigManager, name: String, url: String) {
        val currentConfig = configManager.getGlobalConfig()
        val updatedRegistry = currentConfig.registryConfig.copy(
            customRegistries = currentConfig.registryConfig.customRegistries + (name to url)
        )
        val updatedConfig = currentConfig.copy(registryConfig = updatedRegistry)
        configManager.saveGlobalConfig(updatedConfig)
        echo("✅ Added custom registry: $name = \"$url\"")
    }
    
    private fun showUsage() {
        echo("")
        echo("Usage:")
        echo("  kpm config init                           # Initialize global config")
        echo("  kpm config list                           # Show all configuration")
        echo("  kpm config get <key>                      # Get a configuration value")
        echo("  kpm config set <key> <value>              # Set a configuration value")
        echo("  kpm config add-global <name> [version]    # Add global dependency (like npm -g)")
        echo("  kpm config remove-global <name>           # Remove global dependency")
        echo("  kpm config registry <name> <url>          # Add custom registry")
        echo("")
        echo("Examples:")
        echo("  kpm config set default_author \"John Doe\"")
        echo("  kpm config set android_sdk_path \"/path/to/android/sdk\"")
        echo("  kpm config set max_heap_size \"8g\"")
        echo("  kpm config add-global timber              # Latest version (npm-style)")
        echo("  kpm config add-global retrofit 2.9.0     # Specific version")
        echo("  kpm config add-global custom com.example:custom:1.0.0  # Full coordinate")
        echo("  kpm config registry corporate https://nexus.company.com/repository/maven-public/")
        echo("  kpm config get default_kotlin_version")
    }
}
