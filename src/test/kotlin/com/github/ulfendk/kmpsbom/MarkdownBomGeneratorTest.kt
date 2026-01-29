package com.github.ulfendk.kmpsbom

import org.cyclonedx.model.Bom
import org.cyclonedx.model.Component
import org.cyclonedx.model.Hash
import org.cyclonedx.model.License
import org.cyclonedx.model.LicenseChoice
import org.cyclonedx.model.Metadata
import org.cyclonedx.model.OrganizationalContact
import org.junit.Test
import kotlin.test.assertTrue

class MarkdownBomGeneratorTest {
    
    @Test
    fun `generates basic markdown from empty BOM`() {
        val bom = Bom()
        bom.serialNumber = "urn:uuid:test-123"
        bom.version = 1
        
        val markdown = MarkdownBomGenerator.generate(bom)
        
        assertTrue(markdown.contains("# Software Bill of Materials (SBOM)"))
        assertTrue(markdown.contains("## Metadata"))
        assertTrue(markdown.contains("urn:uuid:test-123"))
        assertTrue(markdown.contains("## Components"))
    }
    
    @Test
    fun `generates markdown with metadata`() {
        val bom = Bom()
        val metadata = Metadata()
        metadata.timestamp = java.util.Date()
        
        val org = OrganizationalContact()
        org.name = "Test Organization"
        metadata.authors = mutableListOf(org)
        
        val component = Component()
        component.type = Component.Type.APPLICATION
        component.name = "test-app"
        component.version = "1.0.0"
        component.description = "Test application"
        metadata.component = component
        
        bom.metadata = metadata
        
        val markdown = MarkdownBomGenerator.generate(bom)
        
        assertTrue(markdown.contains("Test Organization"))
        assertTrue(markdown.contains("test-app"))
        assertTrue(markdown.contains("1.0.0"))
        assertTrue(markdown.contains("Test application"))
    }
    
    @Test
    fun `generates markdown with components`() {
        val bom = Bom()
        
        val component1 = Component()
        component1.type = Component.Type.LIBRARY
        component1.group = "com.example"
        component1.name = "library1"
        component1.version = "1.0.0"
        component1.purl = "pkg:maven/com.example/library1@1.0.0"
        
        val licenseChoice = LicenseChoice()
        val license = License()
        license.id = "Apache-2.0"
        licenseChoice.addLicense(license)
        component1.licenseChoice = licenseChoice
        
        val hash = Hash(Hash.Algorithm.SHA_256, "abc123")
        component1.hashes = listOf(hash)
        
        bom.components = listOf(component1)
        
        val markdown = MarkdownBomGenerator.generate(bom)
        
        assertTrue(markdown.contains("com.example:library1 @ 1.0.0"))
        assertTrue(markdown.contains("pkg:maven/com.example/library1@1.0.0"))
        assertTrue(markdown.contains("Apache-2.0"))
        assertTrue(markdown.contains("SHA_256") || markdown.contains("SHA-256"))
        assertTrue(markdown.contains("abc123"))
    }
    
    @Test
    fun `groups components by type`() {
        val bom = Bom()
        
        val library = Component()
        library.type = Component.Type.LIBRARY
        library.name = "library"
        library.version = "1.0.0"
        
        val framework = Component()
        framework.type = Component.Type.FRAMEWORK
        framework.name = "framework"
        framework.version = "2.0.0"
        
        bom.components = listOf(library, framework)
        
        val markdown = MarkdownBomGenerator.generate(bom)
        
        assertTrue(markdown.contains("### Library"))
        assertTrue(markdown.contains("### Framework"))
    }
    
    @Test
    fun `sorts components alphabetically within type`() {
        val bom = Bom()
        
        val component1 = Component()
        component1.type = Component.Type.LIBRARY
        component1.group = "com.zzz"
        component1.name = "zebra"
        component1.version = "1.0.0"
        
        val component2 = Component()
        component2.type = Component.Type.LIBRARY
        component2.group = "com.aaa"
        component2.name = "apple"
        component2.version = "1.0.0"
        
        bom.components = listOf(component1, component2)
        
        val markdown = MarkdownBomGenerator.generate(bom)
        
        // apple should appear before zebra
        val appleIndex = markdown.indexOf("com.aaa:apple")
        val zebraIndex = markdown.indexOf("com.zzz:zebra")
        assertTrue(appleIndex < zebraIndex, "Components should be sorted alphabetically")
    }
}
