package com.kpm.cli

object Version {
    const val VERSION = "1.0.6"
    const val BUILD_DATE = "2024-12-05"
    
    fun getVersionString(): String {
        return "KPM (Kotlin Package Manager) v$VERSION"
    }
    
    fun getFullVersionInfo(): String {
        return """
            KPM (Kotlin Package Manager) v$VERSION
            Build Date: $BUILD_DATE
            
            Repository: https://github.com/BenMorrisRains/Kotlin-Package-Manager
            Documentation: https://github.com/BenMorrisRains/Kotlin-Package-Manager#readme
        """.trimIndent()
    }
}
