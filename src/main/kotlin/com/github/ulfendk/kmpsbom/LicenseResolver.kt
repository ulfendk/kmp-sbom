package com.github.ulfendk.kmpsbom

import okhttp3.OkHttpClient
import okhttp3.Request
import org.gradle.api.logging.Logger
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Resolves license information for dependencies from multiple sources:
 * 1. Local Gradle cache (POM files)
 * 2. Maven Central repository
 * 3. Google Maven repository
 * 4. Swift Package Manager (for Swift dependencies)
 */
class LicenseResolver(private val logger: Logger, private val gradleUserHomeDir: File) {
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    
    /**
     * Resolve license information for a dependency
     */
    fun resolve(dep: DependencyInfo): LicenseInfo? {
        // Handle Swift packages differently
        if (dep.isSwiftPackage) {
            return resolveSwiftPackageLicense(dep)
        }
        
        // Try local cache first
        val localLicense = resolveFromLocalCache(dep)
        if (localLicense != null) {
            logger.debug("Found license in local cache for ${dep.id}")
            return localLicense
        }
        
        // Try Maven Central
        val mavenCentralLicense = resolveFromMavenCentral(dep)
        if (mavenCentralLicense != null) {
            logger.info("Found license in Maven Central for ${dep.id}")
            return mavenCentralLicense
        }
        
        // Try Google Maven
        val googleMavenLicense = resolveFromGoogleMaven(dep)
        if (googleMavenLicense != null) {
            logger.info("Found license in Google Maven for ${dep.id}")
            return googleMavenLicense
        }
        
        logger.info("Unable to resolve license for ${dep.id} from any source")
        return null
    }
    
    /**
     * Resolve license from local Gradle cache
     */
    private fun resolveFromLocalCache(dep: DependencyInfo): LicenseInfo? {
        val pomFile = findPomFileInCache(dep)
        if (pomFile != null && pomFile.exists()) {
            logger.debug("Found POM file for ${dep.id}: ${pomFile.absolutePath}")
            return PomLicenseParser.parse(pomFile)
        }
        return null
    }
    
    /**
     * Resolve license from Maven Central repository
     */
    private fun resolveFromMavenCentral(dep: DependencyInfo): LicenseInfo? {
        val pomUrl = buildMavenCentralUrl(dep)
        logger.debug("Trying to fetch POM from Maven Central: $pomUrl")
        return fetchAndParsePom(pomUrl)
    }
    
    /**
     * Resolve license from Google Maven repository
     */
    private fun resolveFromGoogleMaven(dep: DependencyInfo): LicenseInfo? {
        val pomUrl = buildGoogleMavenUrl(dep)
        logger.debug("Trying to fetch POM from Google Maven: $pomUrl")
        return fetchAndParsePom(pomUrl)
    }
    
    /**
     * Resolve license for Swift packages
     */
    private fun resolveSwiftPackageLicense(dep: DependencyInfo): LicenseInfo? {
        // For Swift packages, we can try to fetch LICENSE file from the repository
        // The repository URL is stored in the group field from SwiftPackageInfo
        logger.debug("Attempting to resolve license for Swift package: ${dep.id}")
        
        // Extract the repository URL from the dependency
        // For now, we'll return null as Swift package license resolution
        // would require fetching from GitHub/GitLab APIs or raw repository files
        // This can be enhanced in the future
        
        // Try common GitHub patterns
        val repoUrl = dep.group
        if (repoUrl.contains("github.com", ignoreCase = true)) {
            return tryFetchLicenseFromGitHub(repoUrl, dep)
        }
        
        logger.debug("Swift package license resolution not yet implemented for non-GitHub repositories")
        return null
    }
    
    /**
     * Try to fetch license information from GitHub repository
     */
    private fun tryFetchLicenseFromGitHub(repoUrl: String, dep: DependencyInfo): LicenseInfo? {
        try {
            // Convert git URL to GitHub API URL
            // Example: https://github.com/firebase/firebase-ios-sdk.git -> 
            //          https://api.github.com/repos/firebase/firebase-ios-sdk/license
            
            val cleanUrl = repoUrl.removeSuffix(".git")
                .replace("https://github.com/", "")
                .replace("http://github.com/", "")
                .replace("www.github.com/", "github.com/")
                .removePrefix("github.com/")
            
            val parts = cleanUrl.split("/")
            if (parts.size >= 2) {
                val owner = parts[0]
                val repo = parts[1]
                
                val licenseApiUrl = "https://api.github.com/repos/$owner/$repo/license"
                logger.debug("Trying to fetch license from GitHub API: $licenseApiUrl")
                
                val request = Request.Builder()
                    .url(licenseApiUrl)
                    .addHeader("Accept", "application/vnd.github.v3+json")
                    .build()
                
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (body != null) {
                            return parseGitHubLicenseResponse(body)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.debug("Failed to fetch license from GitHub for ${dep.id}: ${e.message}")
        }
        
        return null
    }
    
    /**
     * Parse GitHub license API response
     */
    private fun parseGitHubLicenseResponse(jsonResponse: String): LicenseInfo? {
        try {
            // Parse JSON response to extract SPDX ID
            // Example response: {"license": {"spdx_id": "Apache-2.0", "name": "Apache License 2.0"}}
            val regex = """"spdx_id"\s*:\s*"([^"]+)"""".toRegex()
            val match = regex.find(jsonResponse)
            if (match != null) {
                val spdxId = match.groupValues[1]
                if (spdxId != "NOASSERTION" && spdxId.isNotBlank()) {
                    return LicenseInfo(id = spdxId, name = spdxId, url = null)
                }
            }
        } catch (e: Exception) {
            logger.debug("Failed to parse GitHub license response: ${e.message}")
        }
        return null
    }
    
    /**
     * Find POM file in local Gradle cache
     */
    private fun findPomFileInCache(dep: DependencyInfo): File? {
        val pomDir = File(gradleUserHomeDir, "caches/modules-2/files-2.1/${dep.group}/${dep.name}/${dep.version}")
        
        if (!pomDir.exists() || !pomDir.isDirectory) {
            logger.debug("POM directory not found for ${dep.id}: ${pomDir.absolutePath}")
            return null
        }
        
        // Search for POM files in subdirectories (Gradle stores files in hash subdirectories)
        // First check the directory itself
        pomDir.listFiles()?.forEach { file ->
            if (file.isFile && file.extension == "pom") {
                return file
            }
        }
        
        // Then check one level deeper (hash subdirectories)
        pomDir.listFiles()?.forEach { subDir ->
            if (subDir.isDirectory) {
                subDir.listFiles()?.forEach { file ->
                    if (file.isFile && file.extension == "pom") {
                        return file
                    }
                }
            }
        }
        
        logger.debug("No POM file found in ${pomDir.absolutePath} or its subdirectories")
        return null
    }
    
    /**
     * Build Maven Central POM URL
     */
    private fun buildMavenCentralUrl(dep: DependencyInfo): String {
        val groupPath = dep.group.replace('.', '/')
        return "https://repo1.maven.org/maven2/$groupPath/${dep.name}/${dep.version}/${dep.name}-${dep.version}.pom"
    }
    
    /**
     * Build Google Maven POM URL
     */
    private fun buildGoogleMavenUrl(dep: DependencyInfo): String {
        val groupPath = dep.group.replace('.', '/')
        return "https://dl.google.com/dl/android/maven2/$groupPath/${dep.name}/${dep.version}/${dep.name}-${dep.version}.pom"
    }
    
    /**
     * Fetch and parse POM from URL
     */
    private fun fetchAndParsePom(url: String): LicenseInfo? {
        try {
            val request = Request.Builder()
                .url(url)
                .build()
            
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null) {
                        // Create a temporary file to parse the POM
                        val tempFile = File.createTempFile("pom", ".xml")
                        try {
                            tempFile.writeText(body)
                            return PomLicenseParser.parse(tempFile)
                        } finally {
                            tempFile.delete()
                        }
                    }
                } else {
                    logger.debug("Failed to fetch POM from $url: HTTP ${response.code}")
                }
            }
        } catch (e: IOException) {
            logger.debug("Network error fetching POM from $url: ${e.message}")
        } catch (e: Exception) {
            logger.debug("Error fetching POM from $url: ${e.message}")
        }
        
        return null
    }
}
