import java.time.LocalDate

plugins {
    kotlin("jvm") version "2.0.0"
    application
}

group = "com.kpm"
version = "1.1.2"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
 
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
}

application {
    mainClass.set("com.kpm.cli.MainKt")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}

// Generate Version.kt with dynamic version from build.gradle.kts
tasks.register("generateVersion") {
    val versionFile = file("src/main/kotlin/com/kpm/cli/Version.kt")
    val projectVersion = version.toString()
    val buildDate = LocalDate.now().toString()
    
    doLast {
        versionFile.writeText("""
            package com.kpm.cli
            
            object Version {
                const val VERSION = "$projectVersion"
                const val BUILD_DATE = "$buildDate"
                
                fun getVersionString(): String {
                    return "KPM (Kotlin Package Manager) v${'$'}VERSION"
                }
                
                fun getFullVersionInfo(): String {
                    return ""${'"'}
                        KPM (Kotlin Package Manager) v${'$'}VERSION
                        Build Date: ${'$'}BUILD_DATE
                        
                        Repository: https://github.com/BenMorrisRains/Kotlin-Package-Manager
                        Documentation: https://github.com/BenMorrisRains/Kotlin-Package-Manager#readme
                    ""${'"'}.trimIndent()
                }
            }
            
        """.trimIndent())
    }
}

// Run generateVersion before compiling
tasks.named("compileKotlin") {
    dependsOn("generateVersion")
}

