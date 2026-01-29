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
    
    /**
     * List of allowed SPDX license identifiers.
     * If not empty, build will fail when dependencies have licenses not in this list.
     * Example: ["Apache-2.0", "MIT", "BSD-3-Clause"]
     */
    var allowedLicenses: List<String> = emptyList()
    
    /**
     * Maximum allowed vulnerability severity level.
     * Vulnerabilities above this level will cause build failure.
     * Valid values: CRITICAL, HIGH, MEDIUM, LOW, NONE
     * Default: NONE (no severity causes failure)
     */
    var allowedVulnerabilitySeverity: String = "NONE"
    
    /**
     * When to fail the build on license or vulnerability violations.
     * - ALWAYS: Always fail on violations
     * - PULL_REQUEST: Only fail when running in a pull request (Azure Pipelines)
     * - NEVER: Never fail, only report violations
     * Default: NEVER
     */
    var failOnViolation: String = "NEVER"
}
