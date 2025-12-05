package com.kpm.model

data class KpmLockfile(
    val dependencies: Map<String, String> = emptyMap(),
    val testDependencies: Map<String, String> = emptyMap(),
    val kaptDependencies: Map<String, String> = emptyMap(),
    val kspDependencies: Map<String, String> = emptyMap(),
    val metadata: LockfileMetadata = LockfileMetadata()
)

data class LockfileMetadata(
    val generatedAt: String = "",
    val kpmVersion: String = "0.1.0",
    val kotlinVersion: String = ""
)

data class Dependency(
    val group: String,
    val artifact: String,
    val version: String,
    val scope: DependencyScope = DependencyScope.IMPLEMENTATION
) {
    val coordinates: String
        get() = "$group:$artifact:$version"
        
    companion object {
        fun parse(coordinates: String): Dependency {
            val parts = coordinates.split(":")
            require(parts.size >= 3) { "Invalid dependency coordinates: $coordinates" }
            return Dependency(
                group = parts[0],
                artifact = parts[1],
                version = parts.drop(2).joinToString(":")
            )
        }
    }
}

enum class DependencyScope {
    IMPLEMENTATION,
    TEST_IMPLEMENTATION,
    KAPT,
    KSP,
    API,
    COMPILE_ONLY,
    RUNTIME_ONLY
}
