package com.kpm.maven

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class MavenArtifact(
    val groupId: String,
    val artifactId: String,
    val latestVersion: String,
    val description: String? = null
)

class MavenSearchApi {
    
    private val baseUrl = "https://search.maven.org/solrsearch/select"
    
    fun searchArtifact(query: String): List<MavenArtifact> {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "$baseUrl?q=$encodedQuery&rows=10&wt=json"
            
            val response = makeHttpRequest(searchUrl)
            parseSearchResponse(response)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    fun getLatestVersion(groupId: String, artifactId: String): String? {
        return try {
            val query = URLEncoder.encode("g:$groupId AND a:$artifactId", "UTF-8")
            val searchUrl = "$baseUrl?q=$query&rows=1&wt=json"
            
            val response = makeHttpRequest(searchUrl)
            val artifacts = parseSearchResponse(response)
            artifacts.firstOrNull()?.latestVersion
        } catch (e: Exception) {
            null
        }
    }
    
    fun findPopularArtifact(artifactName: String): MavenArtifact? {
        // First try exact artifact name match
        val exactMatch = searchArtifact("a:$artifactName")
        if (exactMatch.isNotEmpty()) {
            return exactMatch.first()
        }
        
        // Then try fuzzy search
        val fuzzyResults = searchArtifact(artifactName)
        return fuzzyResults.firstOrNull { artifact ->
            artifact.artifactId.equals(artifactName, ignoreCase = true) ||
            artifact.artifactId.contains(artifactName, ignoreCase = true)
        }
    }
    
    private fun makeHttpRequest(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", "KPM/1.0")
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        
        return if (connection.responseCode == 200) {
            BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                reader.readText()
            }
        } else {
            throw Exception("HTTP ${connection.responseCode}")
        }
    }
    
    private fun parseSearchResponse(jsonResponse: String): List<MavenArtifact> {
        val artifacts = mutableListOf<MavenArtifact>()
        
        try {
            // Simple JSON parsing without external libraries
            val docsStart = jsonResponse.indexOf("\"docs\":[")
            if (docsStart == -1) return emptyList()
            
            val docsSection = jsonResponse.substring(docsStart + 8)
            val docsEnd = docsSection.indexOf("]")
            val docsContent = docsSection.substring(0, docsEnd)
            
            // Split by artifacts (each starts with {)
            val artifactBlocks = docsContent.split("},{").map { 
                if (!it.startsWith("{")) "{$it" else it
            }.map { 
                if (!it.endsWith("}")) "$it}" else it
            }
            
            for (block in artifactBlocks) {
                if (block.trim().isEmpty() || block == "{}") continue
                
                val groupId = extractJsonValue(block, "g") ?: continue
                val artifactId = extractJsonValue(block, "a") ?: continue
                val latestVersion = extractJsonValue(block, "latestVersion") ?: continue
                
                artifacts.add(MavenArtifact(
                    groupId = groupId,
                    artifactId = artifactId,
                    latestVersion = latestVersion
                ))
            }
        } catch (e: Exception) {
            // Fallback: return empty list if parsing fails
        }
        
        return artifacts
    }
    
    private fun extractJsonValue(json: String, key: String): String? {
        val pattern = "\"$key\":\"([^\"]*)\""
        val regex = Regex(pattern)
        val match = regex.find(json)
        return match?.groupValues?.get(1)
    }
    
    // Common artifact mappings for popular libraries
    fun getWellKnownArtifact(name: String): MavenArtifact? {
        val wellKnown = mapOf(
            "picasso" to MavenArtifact("com.squareup.picasso", "picasso", "2.8"),
            "glide" to MavenArtifact("com.github.bumptech.glide", "glide", "4.16.0"),
            "retrofit" to MavenArtifact("com.squareup.retrofit2", "retrofit", "2.9.0"),
            "okhttp" to MavenArtifact("com.squareup.okhttp3", "okhttp", "4.12.0"),
            "gson" to MavenArtifact("com.google.code.gson", "gson", "2.10.1"),
            "jackson" to MavenArtifact("com.fasterxml.jackson.core", "jackson-core", "2.16.0"),
            "rxjava" to MavenArtifact("io.reactivex.rxjava3", "rxjava", "3.1.8"),
            "dagger" to MavenArtifact("com.google.dagger", "dagger", "2.48.1"),
            "hilt" to MavenArtifact("com.google.dagger", "hilt-android", "2.48.1"),
            "room" to MavenArtifact("androidx.room", "room-runtime", "2.6.1"),
            "lifecycle" to MavenArtifact("androidx.lifecycle", "lifecycle-runtime-ktx", "2.7.0"),
            "navigation" to MavenArtifact("androidx.navigation", "navigation-fragment-ktx", "2.7.6"),
            "viewmodel" to MavenArtifact("androidx.lifecycle", "lifecycle-viewmodel-ktx", "2.7.0"),
            "livedata" to MavenArtifact("androidx.lifecycle", "lifecycle-livedata-ktx", "2.7.0"),
            "coroutines" to MavenArtifact("org.jetbrains.kotlinx", "kotlinx-coroutines-android", "1.7.3"),
            "serialization" to MavenArtifact("org.jetbrains.kotlinx", "kotlinx-serialization-json", "1.6.2"),
            "ktor-client" to MavenArtifact("io.ktor", "ktor-client-android", "2.3.6"),
            "coil" to MavenArtifact("io.coil-kt", "coil", "2.5.0"),
            "timber" to MavenArtifact("com.jakewharton.timber", "timber", "5.0.1"),
            "leakcanary" to MavenArtifact("com.squareup.leakcanary", "leakcanary-android", "2.12"),
            "espresso" to MavenArtifact("androidx.test.espresso", "espresso-core", "3.5.1"),
            "mockito" to MavenArtifact("org.mockito", "mockito-core", "5.7.0"),
            "robolectric" to MavenArtifact("org.robolectric", "robolectric", "4.11.1")
        )
        
        return wellKnown[name.lowercase()]
    }
}
