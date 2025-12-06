package com.kpm.config

import java.io.File

data class GlobalKpmConfig(
    val defaultAuthor: String? = null,
    val defaultLicense: String? = null,
    val defaultKotlinVersion: String? = null,
    val defaultRepositories: Map<String, Boolean> = mapOf(
        "maven_central" to true,
        "google" to true,
        "gradle_plugin_portal" to false
    ),
    val androidSdkPath: String? = null,
    val buildOptimizations: BuildOptimizations = BuildOptimizations(),
    val globalDependencies: GlobalDependencies = GlobalDependencies(),
    val registryConfig: RegistryConfig = RegistryConfig()
)

data class BuildOptimizations(
    val parallelBuilds: Boolean = true,
    val buildCache: Boolean = true,
    val maxHeapSize: String = "4g",
    val configureOnDemand: Boolean = true
)

data class GlobalDependencies(
    val alwaysInclude: Map<String, String> = emptyMap(), // Dependencies to include in every project
    val defaultTestDependencies: Map<String, String> = mapOf(
        "junit" to "junit:junit:4.13.2",
        "kotlin-test" to "org.jetbrains.kotlin:kotlin-test"
    ),
    val excludePatterns: List<String> = emptyList() // Patterns to never include
)

data class RegistryConfig(
    val mavenCentralUrl: String = "https://repo1.maven.org/maven2/",
    val googleUrl: String = "https://dl.google.com/dl/android/maven2/",
    val customRegistries: Map<String, String> = emptyMap(), // name -> url
    val searchApiUrl: String = "https://search.maven.org/solrsearch/select",
    val timeout: Int = 30 // seconds
)

class GlobalConfigManager {
    
    private val configDir = File(System.getProperty("user.home"), ".kpm")
    private val configFile = File(configDir, "config.toml")
    
    fun getGlobalConfig(): GlobalKpmConfig {
        if (!configFile.exists()) {
            return GlobalKpmConfig() // Return defaults
        }
        
        return try {
            parseGlobalConfig(configFile.readText())
        } catch (e: Exception) {
            println("Warning: Failed to parse global config, using defaults: ${e.message}")
            GlobalKpmConfig()
        }
    }
    
    fun saveGlobalConfig(config: GlobalKpmConfig) {
        configDir.mkdirs()
        configFile.writeText(generateGlobalConfigToml(config))
    }
    
    fun initializeGlobalConfig() {
        if (configFile.exists()) {
            println("Global config already exists at: ${configFile.absolutePath}")
            return
        }
        
        configDir.mkdirs()
        val defaultConfig = GlobalKpmConfig(
            defaultAuthor = System.getProperty("user.name"),
            defaultLicense = "MIT",
            defaultKotlinVersion = "2.0.0"
        )
        
        saveGlobalConfig(defaultConfig)
        println("✅ Created global config at: ${configFile.absolutePath}")
    }
    
    private fun parseGlobalConfig(content: String): GlobalKpmConfig {
        val lines = content.lines()
        var defaultAuthor: String? = null
        var defaultLicense: String? = null
        var defaultKotlinVersion: String? = null
        var androidSdkPath: String? = null
        val repositories = mutableMapOf<String, Boolean>()
        var maxHeapSize = "4g"
        var parallelBuilds = true
        var buildCache = true
        var configureOnDemand = true
        val globalDeps = mutableMapOf<String, String>()
        val testDeps = mutableMapOf<String, String>()
        val customRegistries = mutableMapOf<String, String>()
        var mavenCentralUrl = "https://repo1.maven.org/maven2/"
        var googleUrl = "https://dl.google.com/dl/android/maven2/"
        var searchApiUrl = "https://search.maven.org/solrsearch/select"
        var timeout = 30
        
        var currentSection = ""
        
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            
            when {
                trimmed.startsWith("[") && trimmed.endsWith("]") -> {
                    currentSection = trimmed.substring(1, trimmed.length - 1)
                }
                trimmed.contains("=") -> {
                    val (key, value) = trimmed.split("=", limit = 2).map { it.trim() }
                    val cleanValue = value.removePrefix("\"").removeSuffix("\"")
                    
                    when (currentSection) {
                        "user" -> {
                            when (key) {
                                "default_author" -> defaultAuthor = cleanValue
                                "default_license" -> defaultLicense = cleanValue
                                "default_kotlin_version" -> defaultKotlinVersion = cleanValue
                            }
                        }
                        "android" -> {
                            when (key) {
                                "sdk_path" -> androidSdkPath = cleanValue
                            }
                        }
                        "repositories" -> {
                            repositories[key] = cleanValue.toBoolean()
                        }
                        "build" -> {
                            when (key) {
                                "max_heap_size" -> maxHeapSize = cleanValue
                                "parallel_builds" -> parallelBuilds = cleanValue.toBoolean()
                                "build_cache" -> buildCache = cleanValue.toBoolean()
                                "configure_on_demand" -> configureOnDemand = cleanValue.toBoolean()
                            }
                        }
                        "global_dependencies" -> {
                            globalDeps[key] = cleanValue
                        }
                        "default_test_dependencies" -> {
                            testDeps[key] = cleanValue
                        }
                        "registry" -> {
                            when (key) {
                                "maven_central_url" -> mavenCentralUrl = cleanValue
                                "google_url" -> googleUrl = cleanValue
                                "search_api_url" -> searchApiUrl = cleanValue
                                "timeout" -> timeout = cleanValue.toIntOrNull() ?: 30
                            }
                        }
                        "custom_registries" -> {
                            customRegistries[key] = cleanValue
                        }
                    }
                }
            }
        }
        
        return GlobalKpmConfig(
            defaultAuthor = defaultAuthor,
            defaultLicense = defaultLicense,
            defaultKotlinVersion = defaultKotlinVersion,
            defaultRepositories = repositories.ifEmpty { 
                mapOf("maven_central" to true, "google" to true, "gradle_plugin_portal" to false) 
            },
            androidSdkPath = androidSdkPath,
            buildOptimizations = BuildOptimizations(
                parallelBuilds = parallelBuilds,
                buildCache = buildCache,
                maxHeapSize = maxHeapSize,
                configureOnDemand = configureOnDemand
            ),
            globalDependencies = GlobalDependencies(
                alwaysInclude = globalDeps,
                defaultTestDependencies = testDeps.ifEmpty { 
                    mapOf("junit" to "junit:junit:4.13.2", "kotlin-test" to "org.jetbrains.kotlin:kotlin-test") 
                }
            ),
            registryConfig = RegistryConfig(
                mavenCentralUrl = mavenCentralUrl,
                googleUrl = googleUrl,
                customRegistries = customRegistries,
                searchApiUrl = searchApiUrl,
                timeout = timeout
            )
        )
    }
    
    private fun generateGlobalConfigToml(config: GlobalKpmConfig): String {
        return """
            # KPM Global Configuration
            # This file contains user-wide defaults and preferences
            
            [user]
            default_author = "${config.defaultAuthor ?: ""}"
            default_license = "${config.defaultLicense ?: "MIT"}"
            default_kotlin_version = "${config.defaultKotlinVersion ?: "2.0.0"}"
            
            [android]
            sdk_path = "${config.androidSdkPath ?: ""}"
            
            [repositories]
            maven_central = ${config.defaultRepositories["maven_central"] ?: true}
            google = ${config.defaultRepositories["google"] ?: true}
            gradle_plugin_portal = ${config.defaultRepositories["gradle_plugin_portal"] ?: false}
            
            [build]
            max_heap_size = "${config.buildOptimizations.maxHeapSize}"
            parallel_builds = ${config.buildOptimizations.parallelBuilds}
            build_cache = ${config.buildOptimizations.buildCache}
            configure_on_demand = ${config.buildOptimizations.configureOnDemand}
            
            [global_dependencies]
            # Dependencies to include in every new project
            ${config.globalDependencies.alwaysInclude.entries.joinToString("\n            ") { "${it.key} = \"${it.value}\"" }}
            
            [default_test_dependencies]
            # Default test dependencies for new projects
            ${config.globalDependencies.defaultTestDependencies.entries.joinToString("\n            ") { "${it.key} = \"${it.value}\"" }}
            
            [registry]
            maven_central_url = "${config.registryConfig.mavenCentralUrl}"
            google_url = "${config.registryConfig.googleUrl}"
            search_api_url = "${config.registryConfig.searchApiUrl}"
            timeout = ${config.registryConfig.timeout}
            
            ${if (config.registryConfig.customRegistries.isNotEmpty()) {
                "[custom_registries]\n            " + config.registryConfig.customRegistries.entries.joinToString("\n            ") { "${it.key} = \"${it.value}\"" }
            } else ""}
        """.trimIndent()
    }
}
