package com.github.ulfendk.kmpsbom

import org.cyclonedx.model.Bom
import org.cyclonedx.model.Component
import org.cyclonedx.model.License
import org.cyclonedx.model.LicenseChoice
import org.cyclonedx.model.vulnerability.Vulnerability
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ViolationValidatorTest {
    
    @Test
    fun `should not fail when no violations`() {
        val project = ProjectBuilder.builder().build()
        val extension = KmpSbomExtension()
        extension.allowedLicenses = listOf("Apache-2.0", "MIT")
        extension.failOnViolation = "ALWAYS"
        
        val bom = Bom()
        val component = Component()
        component.group = "com.example"
        component.name = "test"
        component.version = "1.0.0"
        
        val licenseChoice = LicenseChoice()
        val license = License()
        license.id = "Apache-2.0"
        licenseChoice.addLicense(license)
        @Suppress("DEPRECATION")
        component.licenseChoice = licenseChoice
        
        bom.components = listOf(component)
        
        // Should not throw
        ViolationValidator.validateAndFailIfNeeded(bom, extension, project.logger)
    }
    
    @Test
    fun `should fail when license violation with failOnViolation=ALWAYS`() {
        val project = ProjectBuilder.builder().build()
        val extension = KmpSbomExtension()
        extension.allowedLicenses = listOf("MIT")
        extension.failOnViolation = "ALWAYS"
        
        val bom = Bom()
        val component = Component()
        component.group = "com.example"
        component.name = "test"
        component.version = "1.0.0"
        
        val licenseChoice = LicenseChoice()
        val license = License()
        license.id = "Apache-2.0"
        licenseChoice.addLicense(license)
        @Suppress("DEPRECATION")
        component.licenseChoice = licenseChoice
        
        bom.components = listOf(component)
        
        val exception = assertFailsWith<GradleException> {
            ViolationValidator.validateAndFailIfNeeded(bom, extension, project.logger)
        }
        
        assertTrue(exception.message!!.contains("license violation"))
    }
    
    @Test
    fun `should not fail when license violation with failOnViolation=NEVER`() {
        val project = ProjectBuilder.builder().build()
        val extension = KmpSbomExtension()
        extension.allowedLicenses = listOf("MIT")
        extension.failOnViolation = "NEVER"
        
        val bom = Bom()
        val component = Component()
        component.group = "com.example"
        component.name = "test"
        component.version = "1.0.0"
        
        val licenseChoice = LicenseChoice()
        val license = License()
        license.id = "Apache-2.0"
        licenseChoice.addLicense(license)
        @Suppress("DEPRECATION")
        component.licenseChoice = licenseChoice
        
        bom.components = listOf(component)
        
        // Should not throw
        ViolationValidator.validateAndFailIfNeeded(bom, extension, project.logger)
    }
    
    @Test
    fun `should fail when vulnerability violation with failOnViolation=ALWAYS`() {
        val project = ProjectBuilder.builder().build()
        val extension = KmpSbomExtension()
        extension.allowedVulnerabilitySeverity = "MEDIUM"
        extension.failOnViolation = "ALWAYS"
        
        val bom = Bom()
        val component = Component()
        component.group = "com.example"
        component.name = "test"
        component.version = "1.0.0"
        component.bomRef = "pkg:maven/com.example/test@1.0.0"
        bom.components = listOf(component)
        
        val vulnerability = Vulnerability()
        vulnerability.id = "CVE-2024-1234"
        vulnerability.description = "Test vulnerability"
        val rating = Vulnerability.Rating()
        rating.severity = Vulnerability.Rating.Severity.HIGH
        vulnerability.ratings = listOf(rating)
        
        val affect = Vulnerability.Affect()
        affect.ref = component.bomRef
        vulnerability.affects = listOf(affect)
        
        bom.vulnerabilities = listOf(vulnerability)
        
        val exception = assertFailsWith<GradleException> {
            ViolationValidator.validateAndFailIfNeeded(bom, extension, project.logger)
        }
        
        assertTrue(exception.message!!.contains("vulnerability violation"))
    }
    
    @Test
    fun `should not fail when vulnerability severity is within allowed level`() {
        val project = ProjectBuilder.builder().build()
        val extension = KmpSbomExtension()
        extension.allowedVulnerabilitySeverity = "HIGH"
        extension.failOnViolation = "ALWAYS"
        
        val bom = Bom()
        val component = Component()
        component.group = "com.example"
        component.name = "test"
        component.version = "1.0.0"
        component.bomRef = "pkg:maven/com.example/test@1.0.0"
        bom.components = listOf(component)
        
        val vulnerability = Vulnerability()
        vulnerability.id = "CVE-2024-1234"
        vulnerability.description = "Test vulnerability"
        val rating = Vulnerability.Rating()
        rating.severity = Vulnerability.Rating.Severity.MEDIUM
        vulnerability.ratings = listOf(rating)
        
        val affect = Vulnerability.Affect()
        affect.ref = component.bomRef
        vulnerability.affects = listOf(affect)
        
        bom.vulnerabilities = listOf(vulnerability)
        
        // Should not throw - MEDIUM is less severe than allowed HIGH
        ViolationValidator.validateAndFailIfNeeded(bom, extension, project.logger)
    }
    
    @Test
    fun `should detect Azure Pipelines environment`() {
        // This test would require mocking environment variables
        // For now, we'll just test the method exists
        val isAzure = ViolationValidator.isAzurePipelines()
        // We can't assert true or false without setting env vars
        assertFalse(isAzure) // In test environment, should be false
    }
    
    @Test
    fun `should detect pull request context`() {
        // This test would require mocking environment variables
        val isPr = ViolationValidator.isPullRequest()
        // In test environment, should be false
        assertFalse(isPr)
    }
    
    @Test
    fun `should handle component without license`() {
        val project = ProjectBuilder.builder().build()
        val extension = KmpSbomExtension()
        extension.allowedLicenses = listOf("MIT")
        extension.failOnViolation = "ALWAYS"
        
        val bom = Bom()
        val component = Component()
        component.group = "com.example"
        component.name = "test"
        component.version = "1.0.0"
        // No license set
        
        bom.components = listOf(component)
        
        val exception = assertFailsWith<GradleException> {
            ViolationValidator.validateAndFailIfNeeded(bom, extension, project.logger)
        }
        
        assertTrue(exception.message!!.contains("license violation"))
    }
    
    @Test
    fun `should allow multiple licenses if all are in allowed list`() {
        val project = ProjectBuilder.builder().build()
        val extension = KmpSbomExtension()
        extension.allowedLicenses = listOf("MIT", "Apache-2.0")
        extension.failOnViolation = "ALWAYS"
        
        val bom = Bom()
        val component = Component()
        component.group = "com.example"
        component.name = "test"
        component.version = "1.0.0"
        
        val licenseChoice = LicenseChoice()
        val license1 = License()
        license1.id = "MIT"
        licenseChoice.addLicense(license1)
        val license2 = License()
        license2.id = "Apache-2.0"
        licenseChoice.addLicense(license2)
        @Suppress("DEPRECATION")
        component.licenseChoice = licenseChoice
        
        bom.components = listOf(component)
        
        // Should not throw
        ViolationValidator.validateAndFailIfNeeded(bom, extension, project.logger)
    }
    
    @Test
    fun `should handle empty allowed licenses list`() {
        val project = ProjectBuilder.builder().build()
        val extension = KmpSbomExtension()
        extension.allowedLicenses = emptyList()
        extension.failOnViolation = "ALWAYS"
        
        val bom = Bom()
        val component = Component()
        component.group = "com.example"
        component.name = "test"
        component.version = "1.0.0"
        
        val licenseChoice = LicenseChoice()
        val license = License()
        license.id = "Apache-2.0"
        licenseChoice.addLicense(license)
        @Suppress("DEPRECATION")
        component.licenseChoice = licenseChoice
        
        bom.components = listOf(component)
        
        // Should not throw - empty list means no validation
        ViolationValidator.validateAndFailIfNeeded(bom, extension, project.logger)
    }
}
