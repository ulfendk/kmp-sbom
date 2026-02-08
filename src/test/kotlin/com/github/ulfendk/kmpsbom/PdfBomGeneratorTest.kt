package com.github.ulfendk.kmpsbom

import org.cyclonedx.model.Bom
import org.cyclonedx.model.Component
import org.cyclonedx.model.Hash
import org.cyclonedx.model.License
import org.cyclonedx.model.LicenseChoice
import org.cyclonedx.model.Metadata
import org.cyclonedx.model.OrganizationalContact
import org.junit.Test
import java.io.File
import kotlin.test.assertTrue

class PdfBomGeneratorTest {
    
    @Test
    fun `generates PDF from markdown content`() {
        val markdown = """
            # Software Bill of Materials (SBOM)
            
            ## Metadata
            
            - **Serial Number**: `urn:uuid:test-123`
            - **Version**: 1
            
            ## Components
            
            ### Library (1)
            
            #### com.example:test-lib @ 1.0.0
            
            - **PURL**: `pkg:maven/com.example/test-lib@1.0.0`
            - **License**: Apache-2.0
        """.trimIndent()
        
        val outputFile = File.createTempFile("sbom-test-", ".pdf")
        try {
            PdfBomGenerator.generateFromMarkdown(markdown, outputFile)
            
            // Verify file was created and has content
            assertTrue(outputFile.exists(), "PDF file should be created")
            assertTrue(outputFile.length() > 0, "PDF file should not be empty")
            
            // Basic PDF validation - check for PDF header
            val headerBytes = outputFile.readBytes()
            assertTrue(
                headerBytes.size >= 4 &&
                headerBytes[0] == 0x25.toByte() &&
                headerBytes[1] == 0x50.toByte() &&
                headerBytes[2] == 0x44.toByte() &&
                headerBytes[3] == 0x46.toByte(),
                "File should have PDF header (%PDF)"
            )
        } finally {
            outputFile.delete()
        }
    }
    
    @Test
    fun `generates PDF from BOM with all metadata`() {
        val bom = Bom()
        bom.serialNumber = "urn:uuid:test-full-123"
        bom.version = 1
        
        val metadata = Metadata()
        metadata.timestamp = java.util.Date()
        
        val org = OrganizationalContact()
        org.name = "Test Organization"
        metadata.authors = mutableListOf(org)
        
        val component = Component()
        component.type = Component.Type.APPLICATION
        component.name = "test-app"
        component.version = "2.0.0"
        component.description = "Test application for PDF generation"
        metadata.component = component
        
        bom.metadata = metadata
        
        val library = Component()
        library.type = Component.Type.LIBRARY
        library.group = "com.example"
        library.name = "library1"
        library.version = "1.0.0"
        library.purl = "pkg:maven/com.example/library1@1.0.0"
        
        val licenseChoice = LicenseChoice()
        val license = License()
        license.id = "Apache-2.0"
        licenseChoice.addLicense(license)
        library.licenseChoice = licenseChoice
        
        val hash = Hash(Hash.Algorithm.SHA_256, "abc123def456")
        library.hashes = listOf(hash)
        
        bom.components = listOf(library)
        
        val markdown = MarkdownBomGenerator.generate(bom)
        val outputFile = File.createTempFile("sbom-full-test-", ".pdf")
        
        try {
            PdfBomGenerator.generateFromMarkdown(markdown, outputFile)
            
            // Verify file was created and has content
            assertTrue(outputFile.exists(), "PDF file should be created")
            assertTrue(outputFile.length() > 1000, "PDF file should have substantial content")
            
            // Basic PDF validation
            val headerBytes = outputFile.readBytes()
            assertTrue(
                headerBytes.size >= 4 &&
                headerBytes[0] == 0x25.toByte() &&
                headerBytes[1] == 0x50.toByte() &&
                headerBytes[2] == 0x44.toByte() &&
                headerBytes[3] == 0x46.toByte(),
                "File should have PDF header (%PDF)"
            )
        } finally {
            outputFile.delete()
        }
    }
}
