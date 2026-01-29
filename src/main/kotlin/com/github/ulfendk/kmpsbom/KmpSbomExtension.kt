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
}
