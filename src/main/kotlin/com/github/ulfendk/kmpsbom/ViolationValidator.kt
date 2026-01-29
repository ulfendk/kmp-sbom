package com.github.ulfendk.kmpsbom

import org.cyclonedx.model.Bom
import org.cyclonedx.model.Component
import org.cyclonedx.model.vulnerability.Vulnerability
import org.gradle.api.GradleException
import org.gradle.api.logging.Logger

/**
 * Validates SBOM for license and vulnerability violations
 */
object ViolationValidator {
    
    /**
     * Severity levels in order from most severe to least severe
     */
    private val SEVERITY_LEVELS = listOf("CRITICAL", "HIGH", "MEDIUM", "LOW", "NONE")
    
    /**
     * Checks if running in Azure Pipelines
     */
    fun isAzurePipelines(): Boolean {
        return System.getenv("TF_BUILD") == "True" || System.getenv("AGENT_ID") != null
    }
    
    /**
     * Checks if running in a pull request context in Azure Pipelines
     */
    fun isPullRequest(): Boolean {
        val buildReason = System.getenv("BUILD_REASON")
        return buildReason == "PullRequest"
    }
    
    /**
     * Validates the BOM and throws exception if violations are found and conditions are met
     */
    fun validateAndFailIfNeeded(
        bom: Bom,
        extension: KmpSbomExtension,
        logger: Logger
    ) {
        val violations = collectViolations(bom, extension, logger)
        
        if (violations.isEmpty()) {
            logger.lifecycle("✓ No license or vulnerability violations found")
            return
        }
        
        // Log all violations
        logViolations(violations, logger)
        
        // Determine if we should fail the build
        val shouldFail = shouldFailBuild(extension, logger)
        
        if (shouldFail) {
            throw GradleException(buildViolationMessage(violations))
        } else {
            logger.warn("License or vulnerability violations found but build will not fail (failOnViolation=${extension.failOnViolation})")
        }
    }
    
    /**
     * Collects all license and vulnerability violations
     */
    private fun collectViolations(
        bom: Bom,
        extension: KmpSbomExtension,
        logger: Logger
    ): List<Violation> {
        val violations = mutableListOf<Violation>()
        
        // Check license violations
        if (extension.allowedLicenses.isNotEmpty()) {
            violations.addAll(checkLicenseViolations(bom, extension, logger))
        }
        
        // Check vulnerability violations
        if (extension.allowedVulnerabilitySeverity != "NONE") {
            violations.addAll(checkVulnerabilityViolations(bom, extension, logger))
        }
        
        return violations
    }
    
    /**
     * Checks for license violations
     */
    private fun checkLicenseViolations(
        bom: Bom,
        extension: KmpSbomExtension,
        logger: Logger
    ): List<Violation> {
        val violations = mutableListOf<Violation>()
        val allowedLicenses = extension.allowedLicenses.map { it.uppercase() }
        
        bom.components?.forEach { component ->
            val componentStr = formatComponent(component)
            
            @Suppress("DEPRECATION")
            val licenses = component.licenseChoice?.licenses?.map { it.id?.uppercase() ?: it.name?.uppercase() ?: "UNKNOWN" }
                ?: emptyList()
            
            if (licenses.isEmpty()) {
                violations.add(
                    Violation(
                        type = ViolationType.LICENSE,
                        component = componentStr,
                        details = "No license information available"
                    )
                )
            } else {
                licenses.forEach { license ->
                    if (!allowedLicenses.contains(license)) {
                        violations.add(
                            Violation(
                                type = ViolationType.LICENSE,
                                component = componentStr,
                                details = "License '$license' is not in allowed list: ${extension.allowedLicenses}"
                            )
                        )
                    }
                }
            }
        }
        
        logger.debug("Found ${violations.size} license violations")
        return violations
    }
    
    /**
     * Checks for vulnerability violations
     */
    private fun checkVulnerabilityViolations(
        bom: Bom,
        extension: KmpSbomExtension,
        logger: Logger
    ): List<Violation> {
        val violations = mutableListOf<Violation>()
        val maxAllowedLevel = extension.allowedVulnerabilitySeverity.uppercase()
        
        if (!SEVERITY_LEVELS.contains(maxAllowedLevel)) {
            logger.warn("Invalid allowedVulnerabilitySeverity: $maxAllowedLevel. Must be one of: ${SEVERITY_LEVELS.joinToString()}")
            return violations
        }
        
        val maxAllowedIndex = SEVERITY_LEVELS.indexOf(maxAllowedLevel)
        
        bom.vulnerabilities?.forEach { vulnerability ->
            val severity = extractSeverity(vulnerability)
            val severityIndex = SEVERITY_LEVELS.indexOf(severity)
            
            // Lower index means more severe
            if (severityIndex >= 0 && severityIndex < maxAllowedIndex) {
                val componentRef = vulnerability.affects?.firstOrNull()?.ref ?: "Unknown"
                val component = findComponentByRef(bom, componentRef)
                val componentStr = component?.let { formatComponent(it) } ?: componentRef
                
                violations.add(
                    Violation(
                        type = ViolationType.VULNERABILITY,
                        component = componentStr,
                        details = "${vulnerability.id} (Severity: $severity) - ${vulnerability.description ?: "No description"}"
                    )
                )
            }
        }
        
        logger.debug("Found ${violations.size} vulnerability violations")
        return violations
    }
    
    /**
     * Extracts severity from vulnerability
     */
    private fun extractSeverity(vulnerability: Vulnerability): String {
        // Try to get severity from ratings
        val rating = vulnerability.ratings?.firstOrNull()
        if (rating != null) {
            val severity = rating.severity?.toString()?.uppercase()
            if (severity != null && SEVERITY_LEVELS.contains(severity)) {
                return severity
            }
        }
        
        // Default to HIGH if not specified
        return "HIGH"
    }
    
    /**
     * Finds a component by its BOM reference
     */
    private fun findComponentByRef(bom: Bom, ref: String): Component? {
        return bom.components?.find { it.bomRef == ref }
    }
    
    /**
     * Formats a component as a string with null-safe handling
     */
    private fun formatComponent(component: Component): String {
        val group = component.group ?: "unknown"
        val name = component.name ?: "unknown"
        val version = component.version ?: "unknown"
        return "$group:$name:$version"
    }
    
    /**
     * Determines if build should fail based on configuration
     */
    private fun shouldFailBuild(extension: KmpSbomExtension, logger: Logger): Boolean {
        return when (extension.failOnViolation.uppercase()) {
            "ALWAYS" -> {
                logger.info("failOnViolation=ALWAYS: Build will fail")
                true
            }
            "PULL_REQUEST" -> {
                val isAzure = isAzurePipelines()
                val isPr = isPullRequest()
                logger.info("failOnViolation=PULL_REQUEST: Azure Pipelines=$isAzure, Pull Request=$isPr")
                isAzure && isPr
            }
            "NEVER" -> {
                logger.info("failOnViolation=NEVER: Build will not fail")
                false
            }
            else -> {
                logger.warn("Invalid failOnViolation value: ${extension.failOnViolation}. Must be ALWAYS, PULL_REQUEST, or NEVER. Defaulting to NEVER.")
                false
            }
        }
    }
    
    /**
     * Logs violations in a readable format
     */
    private fun logViolations(violations: List<Violation>, logger: Logger) {
        val licenseViolations = violations.filter { it.type == ViolationType.LICENSE }
        val vulnerabilityViolations = violations.filter { it.type == ViolationType.VULNERABILITY }
        
        if (licenseViolations.isNotEmpty()) {
            logger.error("╔════════════════════════════════════════════════════════════════════════════")
            logger.error("║ LICENSE VIOLATIONS (${licenseViolations.size})")
            logger.error("╠════════════════════════════════════════════════════════════════════════════")
            licenseViolations.forEach { violation ->
                logger.error("║ Component: ${violation.component}")
                logger.error("║   → ${violation.details}")
                logger.error("╟────────────────────────────────────────────────────────────────────────────")
            }
            logger.error("╚════════════════════════════════════════════════════════════════════════════")
        }
        
        if (vulnerabilityViolations.isNotEmpty()) {
            logger.error("╔════════════════════════════════════════════════════════════════════════════")
            logger.error("║ VULNERABILITY VIOLATIONS (${vulnerabilityViolations.size})")
            logger.error("╠════════════════════════════════════════════════════════════════════════════")
            vulnerabilityViolations.forEach { violation ->
                logger.error("║ Component: ${violation.component}")
                logger.error("║   → ${violation.details}")
                logger.error("╟────────────────────────────────────────────────────────────────────────────")
            }
            logger.error("╚════════════════════════════════════════════════════════════════════════════")
        }
    }
    
    /**
     * Builds error message for GradleException
     */
    private fun buildViolationMessage(violations: List<Violation>): String {
        val licenseCount = violations.count { it.type == ViolationType.LICENSE }
        val vulnerabilityCount = violations.count { it.type == ViolationType.VULNERABILITY }
        
        return buildString {
            appendLine("Build failed due to license or vulnerability violations:")
            if (licenseCount > 0) {
                appendLine("  - $licenseCount license violation(s)")
            }
            if (vulnerabilityCount > 0) {
                appendLine("  - $vulnerabilityCount vulnerability violation(s)")
            }
            appendLine("See build log above for details.")
        }
    }
}

/**
 * Represents a license or vulnerability violation
 */
data class Violation(
    val type: ViolationType,
    val component: String,
    val details: String
)

/**
 * Type of violation
 */
enum class ViolationType {
    LICENSE,
    VULNERABILITY
}
