package com.github.ulfendk.kmpsbom

/**
 * Extension for configuring KMP SBOM generation
 */
open class KmpSbomExtension {
    /**
     * Enable vulnerability scanning using OSS Index
     */
    var enableVulnerabilityScanning: Boolean = true
    
    /**
     * Include license information in SBOM
     */
    var includeLicenses: Boolean = true
    
    /**
     * SBOM format version (CycloneDX)
     */
    var sbomVersion: String = "1.5"
    
    /**
     * Organization name for SBOM metadata
     */
    var organizationName: String = ""
    
    /**
     * Organization URL for SBOM metadata
     */
    var organizationUrl: String = ""
    
    /**
     * Path to Package.resolved file for Swift Package Manager dependencies
     * (optional, only used for iOS targets)
     */
    var packageResolvedPath: String? = null
    
    /**
     * Android module path for aggregate SBOM generation
     * (e.g., ":androidApp" or ":app")
     */
    var androidAppModule: String? = null
    
    /**
     * iOS framework module path for aggregate SBOM generation
     * (e.g., ":iosApp" or ":shared")
     */
    var iosFrameworkModule: String? = null
    
    /**
     * Include debug dependencies in aggregate SBOM
     */
    var includeDebugDependencies: Boolean = false
    
    /**
     * Include release dependencies in aggregate SBOM
     */
    var includeReleaseDependencies: Boolean = true
    
    /**
     * Include test dependencies in aggregate SBOM
     */
    var includeTestDependencies: Boolean = false
}
