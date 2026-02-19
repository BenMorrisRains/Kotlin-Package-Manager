package com.kpm.cli

import com.kpm.maven.MavenSearchApi
import com.kpm.model.Dependency

/**
 * Shared utility for resolving dependencies from shorthand names or full coordinates.
 * Used by both AddCommand and ConfigCommand to avoid code duplication.
 */
object DependencyResolver {
    
    /**
     * Resolves a dependency from shorthand (e.g., "timber") or full coordinates (e.g., "group:artifact:version").
     * Returns null if the user cancels or the dependency cannot be found.
     */
    fun resolveDependencyInteractive(input: String): Dependency? {
        return if (":" in input) {
            // Full coordinates provided
            val dep = try {
                Dependency.parse(input)
            } catch (e: Exception) {
                echo("Error: Invalid dependency format. Use group:artifact:version", err = true)
                return null
            }
            
            if (confirmDependency(dep)) dep else null
        } else {
            // Shorthand provided - resolve interactively
            resolveShorthandInteractive(input)
        }
    }
    
    /**
     * Resolves a shorthand dependency name with interactive confirmation and retry.
     */
    private fun resolveShorthandInteractive(shorthand: String): Dependency? {
        var searchTerm = shorthand
        
        while (true) {
            val dep = try {
                resolveShorthand(searchTerm)
            } catch (e: Exception) {
                // Check if user requested new search from selection menu
                if (e.message == "User requested new search") {
                    print("Enter new search term: ")
                    val newTerm = readLine()?.trim()
                    if (newTerm.isNullOrBlank()) {
                        echo("No search term provided. Aborting.")
                        return null
                    }
                    searchTerm = newTerm
                    continue
                }
                
                // Check if it was an invalid selection
                if (e.message == "Invalid selection") {
                    // Retry the same search
                    continue
                }
                
                // Artifact not found
                echo("")
                echo("Could not find artifact '$searchTerm'.")
                print("Would you like to search with a different term? (y/n): ")
                
                val retry = readLine()?.trim()?.lowercase()
                if (retry == "y" || retry == "yes") {
                    print("Enter search term: ")
                    val newTerm = readLine()?.trim()
                    if (newTerm.isNullOrBlank()) {
                        echo("No search term provided. Aborting.")
                        return null
                    }
                    searchTerm = newTerm
                    continue
                } else {
                    return null
                }
            }
            
            // User already selected from the list, so just return the dependency
            // No need for additional confirmation
            return dep
        }
    }
    
    /**
     * Resolves a shorthand dependency name to full coordinates.
     * Throws exception if not found.
     */
    private fun resolveShorthand(shorthand: String): Dependency {
        val mavenApi = MavenSearchApi()
        
        // First check well-known artifacts
        val wellKnown = mavenApi.getWellKnownArtifact(shorthand)
        if (wellKnown != null) {
            echo("Found well-known artifact: ${wellKnown.groupId}:${wellKnown.artifactId}")
            echo("")
            return Dependency(
                group = wellKnown.groupId,
                artifact = wellKnown.artifactId,
                version = wellKnown.latestVersion
            )
        }
        
        // Try to search Maven Central for the artifact
        echo("Searching Maven Central for '$shorthand'...")
        val searchResults = mavenApi.searchArtifact(shorthand)
        
        if (searchResults.isNotEmpty()) {
            // Paginate through results, 5 at a time
            var currentPage = 0
            val pageSize = 5
            val totalPages = (searchResults.size + pageSize - 1) / pageSize
            
            while (true) {
                val startIndex = currentPage * pageSize
                val endIndex = minOf(startIndex + pageSize, searchResults.size)
                val pageResults = searchResults.subList(startIndex, endIndex)
                
                echo("")
                echo("Found ${searchResults.size} results. Showing ${startIndex + 1}-${endIndex} (Page ${currentPage + 1}/$totalPages):")
                pageResults.forEachIndexed { index, artifact ->
                    echo("  ${index + 1}) ${artifact.groupId}:${artifact.artifactId}:${artifact.latestVersion}")
                }
                echo("")
                
                val options = mutableListOf("Enter selection (1-${pageResults.size})")
                if (currentPage < totalPages - 1) options.add("'n' for next page")
                if (currentPage > 0) options.add("'p' for previous page")
                options.add("'s' to search again")
                
                print("${options.joinToString(", ")}: ")
                val selection = readLine()?.trim()?.lowercase()
                
                when (selection) {
                    "s", "search" -> {
                        throw IllegalArgumentException("User requested new search")
                    }
                    "n", "next" -> {
                        if (currentPage < totalPages - 1) {
                            currentPage++
                            continue
                        } else {
                            echo("Already on last page.")
                            continue
                        }
                    }
                    "p", "prev", "previous" -> {
                        if (currentPage > 0) {
                            currentPage--
                            continue
                        } else {
                            echo("Already on first page.")
                            continue
                        }
                    }
                    else -> {
                        val selectedIndex = selection?.toIntOrNull()?.minus(1)
                        if (selectedIndex != null && selectedIndex in pageResults.indices) {
                            val selected = pageResults[selectedIndex]
                            return Dependency(
                                group = selected.groupId,
                                artifact = selected.artifactId,
                                version = selected.latestVersion
                            )
                        } else {
                            echo("Invalid selection.")
                            continue
                        }
                    }
                }
            }
        }
        
        // Fallback to common shorthand mappings
        val commonDependencies = getCommonDependencyMappings()
        
        val coordinates = commonDependencies[shorthand.lowercase()]
        if (coordinates != null) {
            echo("📚 Using built-in mapping for '$shorthand'")
            return Dependency.parse(coordinates)
        }
        
        throw IllegalArgumentException("Could not find artifact '$shorthand' in Maven Central or built-in mappings")
    }
    
    /**
     * Confirms a dependency with the user.
     */
    private fun confirmDependency(dep: Dependency): Boolean {
        echo("")
        echo("Found dependency: ${dep.coordinates}")
        print("Add this dependency? (y/n): ")
        
        val response = readLine()?.trim()?.lowercase()
        return when (response) {
            "y", "yes" -> true
            "n", "no" -> {
                echo("Dependency not added.")
                false
            }
            else -> {
                echo("Invalid response. Dependency not added.")
                false
            }
        }
    }
    
    /**
     * Common dependency shorthand mappings.
     */
    private fun getCommonDependencyMappings(): Map<String, String> {
        return mapOf(
            "junit" to "junit:junit:4.13.2",
            "mockito" to "org.mockito:mockito-core:5.7.0",
            "gson" to "com.google.code.gson:gson:2.10.1",
            "retrofit" to "com.squareup.retrofit2:retrofit:2.9.0",
            "okhttp" to "com.squareup.okhttp3:okhttp:4.12.0",
            "picasso" to "com.squareup.picasso:picasso:2.8",
            "glide" to "com.github.bumptech.glide:glide:4.16.0",
            "coroutines" to "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3",
            "serialization" to "org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0",
            "ktor-client" to "io.ktor:ktor-client-core:2.3.6",
            "ktor-server" to "io.ktor:ktor-server-core:2.3.6",
            "compose-bom" to "androidx.compose:compose-bom:2024.10.00",
            "compose-ui" to "androidx.compose.ui:ui:1.5.4",
            "compose-material3" to "androidx.compose.material3:material3:1.1.2",
            "hilt" to "com.google.dagger:hilt-android:2.48",
            "room" to "androidx.room:room-runtime:2.6.1",
            "navigation" to "androidx.navigation:navigation-compose:2.7.5",
            "timber" to "com.jakewharton.timber:timber:5.0.1",
            "lottie" to "com.airbnb.android:lottie:6.1.0",
            "material" to "com.google.android.material:material:1.11.0",
            "appcompat" to "androidx.appcompat:appcompat:1.6.1",
            "coreKtx" to "androidx.core:core-ktx:1.12.0",
            "constraintlayout" to "androidx.constraintlayout:constraintlayout:2.1.4",
            "recyclerview" to "androidx.recyclerview:recyclerview:1.3.2",
            "cardview" to "androidx.cardview:cardview:1.0.0",
            "viewpager2" to "androidx.viewpager2:viewpager2:1.0.0",
            "fragment" to "androidx.fragment:fragment-ktx:1.6.2",
            "activity" to "androidx.activity:activity-ktx:1.8.2",
            "lifecycle" to "androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2",
            "workmanager" to "androidx.work:work-runtime-ktx:2.9.0",
            "datastore" to "androidx.datastore:datastore-preferences:1.0.0",
            "paging" to "androidx.paging:paging-runtime:3.2.1",
            "camera" to "androidx.camera:camera-camera2:1.3.1",
            "biometric" to "androidx.biometric:biometric:1.1.0",
            "compose-activity" to "androidx.activity:activity-compose:1.8.2",
            "compose-viewmodel" to "androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2",
            "compose-navigation" to "androidx.navigation:navigation-compose:2.7.6",
            "ktor-netty" to "io.ktor:ktor-server-netty:2.3.6",
            "exposed" to "org.jetbrains.exposed:exposed-core:0.45.0",
            "koin" to "io.insert-koin:koin-android:3.5.0",
            "coil" to "io.coil-kt:coil:2.5.0",
            "leakcanary" to "com.squareup.leakcanary:leakcanary-android:2.12"
        )
    }
}
