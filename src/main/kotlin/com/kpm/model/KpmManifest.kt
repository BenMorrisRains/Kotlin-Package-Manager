package com.kpm.model

data class KpmManifest(
    val project: ProjectConfig,
    val android: AndroidConfig? = null,
    val repositories: RepositoryConfig = RepositoryConfig(),
    val dependencies: Map<String, String> = emptyMap(),
    val testDependencies: Map<String, String> = emptyMap(),
    val kaptDependencies: Map<String, String> = emptyMap(),
    val kspDependencies: Map<String, String> = emptyMap(),
    val plugins: Map<String, String> = emptyMap()
)

data class ProjectConfig(
    val name: String,
    val version: String,
    val type: ProjectType,
    val kotlinVersion: String = "2.0.0",
    val gradleVersion: String = "8.5",
    val agpVersion: String? = null
)

enum class ProjectType {
    ANDROID_APP,
    ANDROID_LIBRARY,
    JVM_APPLICATION,
    JVM_LIBRARY,
    MULTIPLATFORM_LIBRARY,
    COMPOSE_MULTIPLATFORM,
    KTOR_API
}

data class AndroidConfig(
    val applicationId: String? = null,
    val minSdk: Int = 24,
    val targetSdk: Int = 36,
    val compileSdk: Int = 36,
    val namespace: String? = null
)

data class RepositoryConfig(
    val mavenCentral: Boolean = true,
    val google: Boolean = false,
    val gradlePluginPortal: Boolean = false,
    val custom: List<String> = emptyList()
)
