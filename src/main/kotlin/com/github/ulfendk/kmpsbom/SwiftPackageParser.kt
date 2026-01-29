package com.github.ulfendk.kmpsbom

import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.File

/**
 * Parser for Swift Package Manager Package.resolved files
 */
object SwiftPackageParser {
    
    /**
     * Parse Package.resolved file and extract Swift dependencies
     * 
     * @param packageResolvedFile The Package.resolved file to parse
     * @return List of Swift package dependencies
     */
    fun parse(packageResolvedFile: File): List<SwiftPackageInfo> {
        if (!packageResolvedFile.exists() || !packageResolvedFile.isFile) {
            return emptyList()
        }
        
        val packages = mutableListOf<SwiftPackageInfo>()
        
        try {
            val gson = Gson()
            val jsonContent = packageResolvedFile.readText()
            val jsonObject = gson.fromJson(jsonContent, JsonObject::class.java)
            
            // Package.resolved can have different formats depending on Swift version
            // Try to handle both version 1 and version 2 formats
            when {
                jsonObject.has("pins") -> {
                    // Version 1 format
                    val pins = jsonObject.getAsJsonArray("pins")
                    pins?.forEach { pinElement ->
                        val pin = pinElement.asJsonObject
                        val swiftPackage = parsePackageFromPin(pin)
                        if (swiftPackage != null) {
                            packages.add(swiftPackage)
                        }
                    }
                }
                jsonObject.has("object") -> {
                    // Version 2 format - has a wrapper object
                    val objectWrapper = jsonObject.getAsJsonObject("object")
                    val pins = objectWrapper?.getAsJsonArray("pins")
                    pins?.forEach { pinElement ->
                        val pin = pinElement.asJsonObject
                        val swiftPackage = parsePackageFromPin(pin)
                        if (swiftPackage != null) {
                            packages.add(swiftPackage)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Log error but don't fail - just return empty list
            println("Warning: Failed to parse Package.resolved: ${e.message}")
        }
        
        return packages
    }
    
    private fun parsePackageFromPin(pin: JsonObject): SwiftPackageInfo? {
        try {
            // Extract identity/package name
            val identity = pin.get("identity")?.asString 
                ?: pin.get("package")?.asString 
                ?: return null
            
            // Extract location/repository URL
            val location = pin.get("location")?.asString
                ?: pin.get("repositoryURL")?.asString
                ?: return null
            
            // Extract state information (version and revision)
            val state = pin.getAsJsonObject("state")
            if (state == null) {
                return null
            }
            
            val version = state.get("version")?.asString ?: "unknown"
            val revision = state.get("revision")?.asString
            
            return SwiftPackageInfo(
                identity = identity,
                location = location,
                version = version,
                revision = revision,
                kind = pin.get("kind")?.asString ?: "remoteSourceControl"
            )
        } catch (e: Exception) {
            println("Warning: Failed to parse Swift package pin: ${e.message}")
            return null
        }
    }
}

/**
 * Information about a Swift package dependency
 */
data class SwiftPackageInfo(
    val identity: String,
    val location: String,
    val version: String,
    val revision: String?,
    val kind: String
) {
    /**
     * Convert Swift package info to a DependencyInfo object
     */
    fun toDependencyInfo(): DependencyInfo {
        // Extract organization/author from GitHub URL if possible
        val group = extractGroupFromUrl(location)
        
        return DependencyInfo(
            group = group,
            name = identity,
            version = version,
            id = "$group:$identity:$version",
            file = null // Swift packages don't have local file references like Maven artifacts
        )
    }
    
    private fun extractGroupFromUrl(url: String): String {
        // Try to extract organization from GitHub/GitLab URLs
        // Examples:
        // https://github.com/google/app-check.git -> google
        // https://www.github.com/firebase/firebase-ios-sdk.git -> firebase
        
        return try {
            val cleanUrl = url.removeSuffix(".git")
            val parts = cleanUrl.split("/")
            
            // Find the index of "github.com", "gitlab.com", etc.
            val hostIndex = parts.indexOfFirst { 
                it.contains("github", ignoreCase = true) || 
                it.contains("gitlab", ignoreCase = true) 
            }
            
            if (hostIndex >= 0 && hostIndex + 1 < parts.size) {
                // Organization is the part after the host
                parts[hostIndex + 1]
            } else {
                "swift"
            }
        } catch (e: Exception) {
            "swift"
        }
    }
}
