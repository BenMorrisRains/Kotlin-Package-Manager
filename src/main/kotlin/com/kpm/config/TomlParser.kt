package com.kpm.config

import com.kpm.model.*
import java.io.File
import java.time.Instant

class TomlParser {
    
    fun parseManifest(file: File): KpmManifest {
        require(file.exists()) { "kpm.toml not found at ${file.absolutePath}" }
        
        val content = file.readText()
        return parseManifestFromString(content)
    }
    
    fun parseManifestFromString(content: String): KpmManifest {
        // Simple TOML-like parsing for now
        // In a real implementation, you'd use a proper TOML library
        val lines = content.lines().filter { it.isNotBlank() && !it.trim().startsWith("#") }
        
        var currentSection = ""
        val sections = mutableMapOf<String, MutableMap<String, String>>()
        
        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("[") && trimmed.endsWith("]") -> {
                    currentSection = trimmed.substring(1, trimmed.length - 1)
                    sections[currentSection] = mutableMapOf()
                }
                "=" in trimmed -> {
                    val (key, value) = trimmed.split("=", limit = 2)
                    val cleanKey = key.trim()
                    val cleanValue = value.trim().removeSurrounding("\"")
                    sections.getOrPut(currentSection) { mutableMapOf() }[cleanKey] = cleanValue
                }
            }
        }
        
        return buildManifest(sections)
    }
    
    private fun buildManifest(sections: Map<String, Map<String, String>>): KpmManifest {
        val projectSection = sections["project"] ?: error("Missing [project] section")
        
        val project = ProjectConfig(
            name = projectSection["name"] ?: error("Missing project name"),
            version = projectSection["version"] ?: error("Missing project version"),
            type = parseProjectType(projectSection["type"] ?: error("Missing project type")),
            kotlinVersion = projectSection["kotlin_version"] ?: "2.0.0"
        )
        
        val android = sections["android"]?.let { androidSection ->
            AndroidConfig(
                applicationId = androidSection["application_id"],
                minSdk = androidSection["min_sdk"]?.toIntOrNull() ?: 24,
                targetSdk = androidSection["target_sdk"]?.toIntOrNull() ?: 35,
                compileSdk = androidSection["compile_sdk"]?.toIntOrNull() ?: 35,
                namespace = androidSection["namespace"]
            )
        }
        
        val repositories = sections["repositories"]?.let { repoSection ->
            RepositoryConfig(
                mavenCentral = repoSection["maven_central"]?.toBoolean() ?: true,
                google = repoSection["google"]?.toBoolean() ?: false,
                gradlePluginPortal = repoSection["gradle_plugin_portal"]?.toBoolean() ?: false
            )
        } ?: RepositoryConfig()
        
        return KpmManifest(
            project = project,
            android = android,
            repositories = repositories,
            dependencies = sections["dependencies"] ?: emptyMap(),
            testDependencies = sections["test_dependencies"] ?: emptyMap(),
            kaptDependencies = sections["kapt_dependencies"] ?: emptyMap(),
            kspDependencies = sections["ksp_dependencies"] ?: emptyMap(),
            plugins = sections["plugins"] ?: emptyMap()
        )
    }
    
    private fun parseProjectType(type: String): ProjectType {
        return when (type.lowercase().replace("-", "_")) {
            "android_app", "android-app" -> ProjectType.ANDROID_APP
            "android_library", "android-library" -> ProjectType.ANDROID_LIBRARY
            "jvm_application", "jvm-application" -> ProjectType.JVM_APPLICATION
            "jvm_library", "jvm-library" -> ProjectType.JVM_LIBRARY
            "multiplatform_library", "multiplatform-library" -> ProjectType.MULTIPLATFORM_LIBRARY
            "ktor_api", "ktor-api" -> ProjectType.KTOR_API
            else -> error("Unknown project type: $type")
        }
    }
    
    fun writeManifest(manifest: KpmManifest, file: File) {
        val content = buildTomlContent(manifest)
        file.writeText(content)
    }
    
    private fun buildTomlContent(manifest: KpmManifest): String {
        val builder = StringBuilder()
        
        // Project section
        builder.appendLine("[project]")
        builder.appendLine("name = \"${manifest.project.name}\"")
        builder.appendLine("version = \"${manifest.project.version}\"")
        builder.appendLine("type = \"${manifest.project.type.name.lowercase().replace("_", "-")}\"")
        builder.appendLine("kotlin_version = \"${manifest.project.kotlinVersion}\"")
        builder.appendLine()
        
        // Android section
        manifest.android?.let { android ->
            builder.appendLine("[android]")
            android.applicationId?.let { builder.appendLine("application_id = \"$it\"") }
            builder.appendLine("min_sdk = ${android.minSdk}")
            builder.appendLine("target_sdk = ${android.targetSdk}")
            builder.appendLine("compile_sdk = ${android.compileSdk}")
            android.namespace?.let { builder.appendLine("namespace = \"$it\"") }
            builder.appendLine()
        }
        
        // Repositories section
        builder.appendLine("[repositories]")
        builder.appendLine("maven_central = ${manifest.repositories.mavenCentral}")
        builder.appendLine("google = ${manifest.repositories.google}")
        builder.appendLine("gradle_plugin_portal = ${manifest.repositories.gradlePluginPortal}")
        builder.appendLine()
        
        // Dependencies sections
        if (manifest.dependencies.isNotEmpty()) {
            builder.appendLine("[dependencies]")
            manifest.dependencies.forEach { (key, value) ->
                builder.appendLine("$key = \"$value\"")
            }
            builder.appendLine()
        }
        
        if (manifest.testDependencies.isNotEmpty()) {
            builder.appendLine("[test_dependencies]")
            manifest.testDependencies.forEach { (key, value) ->
                builder.appendLine("$key = \"$value\"")
            }
            builder.appendLine()
        }
        
        if (manifest.plugins.isNotEmpty()) {
            builder.appendLine("[plugins]")
            manifest.plugins.forEach { (key, value) ->
                builder.appendLine("$key = \"$value\"")
            }
            builder.appendLine()
        }
        
        return builder.toString()
    }
    
    fun parseLockfile(file: File): KpmLockfile {
        if (!file.exists()) {
            return KpmLockfile()
        }
        
        val content = file.readText()
        return parseLockfileFromString(content)
    }
    
    private fun parseLockfileFromString(content: String): KpmLockfile {
        val lines = content.lines().filter { it.isNotBlank() && !it.trim().startsWith("#") }
        
        var currentSection = ""
        val sections = mutableMapOf<String, MutableMap<String, String>>()
        
        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("[") && trimmed.endsWith("]") -> {
                    currentSection = trimmed.substring(1, trimmed.length - 1)
                    sections[currentSection] = mutableMapOf()
                }
                "=" in trimmed -> {
                    val (key, value) = trimmed.split("=", limit = 2)
                    val cleanKey = key.trim()
                    val cleanValue = value.trim().removeSurrounding("\"")
                    sections.getOrPut(currentSection) { mutableMapOf() }[cleanKey] = cleanValue
                }
            }
        }
        
        return KpmLockfile(
            dependencies = sections["dependencies"] ?: emptyMap(),
            testDependencies = sections["test_dependencies"] ?: emptyMap(),
            kaptDependencies = sections["kapt_dependencies"] ?: emptyMap(),
            kspDependencies = sections["ksp_dependencies"] ?: emptyMap(),
            metadata = LockfileMetadata(
                generatedAt = sections["metadata"]?.get("generated_at") ?: "",
                kpmVersion = sections["metadata"]?.get("kpm_version") ?: "0.1.0",
                kotlinVersion = sections["metadata"]?.get("kotlin_version") ?: ""
            )
        )
    }
    
    fun writeLockfile(lockfile: KpmLockfile, file: File) {
        val content = buildLockfileContent(lockfile)
        file.writeText(content)
    }
    
    private fun buildLockfileContent(lockfile: KpmLockfile): String {
        val builder = StringBuilder()
        
        // Metadata section
        builder.appendLine("[metadata]")
        builder.appendLine("generated_at = \"${lockfile.metadata.generatedAt}\"")
        builder.appendLine("kpm_version = \"${lockfile.metadata.kpmVersion}\"")
        builder.appendLine("kotlin_version = \"${lockfile.metadata.kotlinVersion}\"")
        builder.appendLine()
        
        // Dependencies sections
        if (lockfile.dependencies.isNotEmpty()) {
            builder.appendLine("[dependencies]")
            lockfile.dependencies.forEach { (key, value) ->
                builder.appendLine("\"$key\" = \"$value\"")
            }
            builder.appendLine()
        }
        
        if (lockfile.testDependencies.isNotEmpty()) {
            builder.appendLine("[test_dependencies]")
            lockfile.testDependencies.forEach { (key, value) ->
                builder.appendLine("\"$key\" = \"$value\"")
            }
            builder.appendLine()
        }
        
        if (lockfile.kaptDependencies.isNotEmpty()) {
            builder.appendLine("[kapt_dependencies]")
            lockfile.kaptDependencies.forEach { (key, value) ->
                builder.appendLine("\"$key\" = \"$value\"")
            }
            builder.appendLine()
        }
        
        if (lockfile.kspDependencies.isNotEmpty()) {
            builder.appendLine("[ksp_dependencies]")
            lockfile.kspDependencies.forEach { (key, value) ->
                builder.appendLine("\"$key\" = \"$value\"")
            }
            builder.appendLine()
        }
        
        return builder.toString()
    }
}
