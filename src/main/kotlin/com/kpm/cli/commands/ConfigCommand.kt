package com.kpm.cli.commands

import com.kpm.cli.Command
import com.kpm.cli.DependencyResolver
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
        
        // Use shared DependencyResolver for consistent behavior with kpm add
        val input = if (versionOrCoordinate.contains(":")) {
            // Full coordinate provided
            versionOrCoordinate
        } else {
            // Just the name provided - use it as shorthand
            name
        }
        
        val dep = DependencyResolver.resolveDependencyInteractive(input)
        if (dep == null) {
            echo("Global dependency not added.")
            return
        }
        
        val updatedDependencies = currentConfig.globalDependencies.copy(
            alwaysInclude = currentConfig.globalDependencies.alwaysInclude + (dep.artifact to dep.coordinates)
        )
        val updatedConfig = currentConfig.copy(globalDependencies = updatedDependencies)
        configManager.saveGlobalConfig(updatedConfig)
        echo("✅ Added global dependency: ${dep.artifact} = \"${dep.coordinates}\"")
        echo("This dependency will be included in all new projects")
        echo("")
        echo("💡 To add this dependency to existing projects, run:")
        echo("   kpm sync-global")
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
