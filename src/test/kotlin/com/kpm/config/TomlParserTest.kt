package com.kpm.config

import com.kpm.model.ProjectType
import org.junit.jupiter.api.Test

class TomlParserTest {
    
    @Test
    fun `should parse basic manifest`() {
        val tomlContent = """
            [project]
            name = "test-app"
            version = "0.1.0"
            type = "android-app"
            kotlin_version = "2.0.0"
            
            [android]
            application_id = "com.example.testapp"
            min_sdk = 24
            target_sdk = 35
            compile_sdk = 35
            
            [dependencies]
            coreKtx = "androidx.core:core-ktx:1.13.+"
        """.trimIndent()
        
        val parser = TomlParser()
        val manifest = parser.parseManifestFromString(tomlContent)
        
        assert(manifest.project.name == "test-app")
        assert(manifest.project.version == "0.1.0")
        assert(manifest.project.type == ProjectType.ANDROID_APP)
        assert(manifest.project.kotlinVersion == "2.0.0")
        
        assert(manifest.android != null)
        assert(manifest.android?.applicationId == "com.example.testapp")
        assert(manifest.android?.minSdk == 24)
        
        assert(manifest.dependencies.size == 1)
        assert(manifest.dependencies["coreKtx"] == "androidx.core:core-ktx:1.13.+")
    }
}
